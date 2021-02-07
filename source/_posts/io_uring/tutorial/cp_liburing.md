---
title: liburing 实现 cp 命令
date: 2021-02-07 16:15:31
categories: ["Lord of the io_uring"]
tags:
  - io_uring

---


# liburing 实现 cp 命令

在前面的小节中，我们看到了如何使用liburing提供的高级接口来构建相当于Unix cat的工具。
然而，在这两个例子中，我们队列没有同时处理超过一个请求。 io_uring的目标之一是能够通过
让用户同时排队几个操作来减少系统调用的数量，这样内核就可以一下子接收并处理这些操作，
而不需要程序为每个I/O请求进行一次或多次系统调用。

为此，在本部分中，我们构建一个复制文件的复制程序。它试图通过队
列深度允许的尽可能多的请求进来，这样尽量提高效率。让我们看看一些代码。
这在很大程度上是基于 [fio 包](https://github.com/axboe/fio/blob/master/t/io_uring.c) 中的一个程序。

```c
#include <stdio.h>
#include <fcntl.h>
#include <string.h>
#include <stdlib.h>
#include <unistd.h>
#include <assert.h>
#include <errno.h>
#include <sys/stat.h>
#include <sys/ioctl.h>
#include <liburing.h>

#define QD  2
#define BS (16 * 1024)

static int infd, outfd;

struct io_data {
    int read;
    off_t first_offset, offset;
    size_t first_len;
    struct iovec iov;
};

static int setup_context(unsigned entries, struct io_uring *ring) {
    int ret;

    ret = io_uring_queue_init(entries, ring, 0);
    if( ret < 0) {
        fprintf(stderr, "queue_init: %s\n", strerror(-ret));
        return -1;
    }

    return 0;
}

static int get_file_size(int fd, off_t *size) {
    struct stat st;

    if (fstat(fd, &st) < 0 )
        return -1;
    if(S_ISREG(st.st_mode)) {
        *size = st.st_size;
        return 0;
    } else if (S_ISBLK(st.st_mode)) {
        unsigned long long bytes;

        if (ioctl(fd, BLKGETSIZE64, &bytes) != 0)
            return -1;

        *size = bytes;
        return 0;
    }
    return -1;
}

static void queue_prepped(struct io_uring *ring, struct io_data *data) {
    struct io_uring_sqe *sqe;

    sqe = io_uring_get_sqe(ring);
    assert(sqe);

    if (data->read)
        io_uring_prep_readv(sqe, infd, &data->iov, 1, data->offset);
    else
        io_uring_prep_writev(sqe, outfd, &data->iov, 1, data->offset);

    io_uring_sqe_set_data(sqe, data);
}

static int queue_read(struct io_uring *ring, off_t size, off_t offset) {
    struct io_uring_sqe *sqe;
    struct io_data *data;

    data = malloc(size + sizeof(*data));
    if (!data)
        return 1;

    sqe = io_uring_get_sqe(ring);
    if (!sqe) {
        free(data);
        return 1;
    }

    data->read = 1;
    data->offset = data->first_offset = offset;

    data->iov.iov_base = data + 1;
    data->iov.iov_len = size;
    data->first_len = size;

    io_uring_prep_readv(sqe, infd, &data->iov, 1, offset);
    io_uring_sqe_set_data(sqe, data);
    return 0;
}

static void queue_write(struct io_uring *ring, struct io_data *data) {
    data->read = 0;
    data->offset = data->first_offset;

    data->iov.iov_base = data + 1;
    data->iov.iov_len = data->first_len;

    queue_prepped(ring, data);
    io_uring_submit(ring);
}

int copy_file(struct io_uring *ring, off_t insize) {
    unsigned long reads, writes;
    struct io_uring_cqe *cqe;
    off_t write_left, offset;
    int ret;

    write_left = insize;
    writes = reads = offset = 0;

    while (insize || write_left) {
        int had_reads, got_comp;

        /* 尽可能多地排队读取 */
        had_reads = reads;
        while (insize) {
            off_t this_size = insize;

            if (reads + writes >= QD)
                break;
            if (this_size > BS)
                this_size = BS;
            else if (!this_size)
                break;

            if (queue_read(ring, this_size, offset))
                break;

            insize -= this_size;
            offset += this_size;
            reads++;
        }

        if (had_reads != reads) {
            ret = io_uring_submit(ring);
            if (ret < 0) {
                fprintf(stderr, "io_uring_submit: %s\n", strerror(-ret));
                break;
            }
        }

        /* 此时队列已满。让我们至少找到一个 completion */
        got_comp = 0;
        while (write_left) {
            struct io_data *data;

            if (!got_comp) {
                ret = io_uring_wait_cqe(ring, &cqe);
                got_comp = 1;
            } else {
                ret = io_uring_peek_cqe(ring, &cqe);
                if (ret == -EAGAIN) {
                    cqe = NULL;
                    ret = 0;
                }
            }
            if (ret < 0) {
                fprintf(stderr, "io_uring_peek_cqe: %s\n",
                        strerror(-ret));
                return 1;
            }
            if (!cqe)
                break;

            data = io_uring_cqe_get_data(cqe);
            if (cqe->res < 0) {
                if (cqe->res == -EAGAIN) {
                    queue_prepped(ring, data);
                    io_uring_cqe_seen(ring, cqe);
                    continue;
                }
                fprintf(stderr, "cqe failed: %s\n",
                        strerror(-cqe->res));
                return 1;
            } else if (cqe->res != data->iov.iov_len) {
                /*短时间读/写；调整并重新排队 */
                data->iov.iov_base += cqe->res;
                data->iov.iov_len -= cqe->res;
                queue_prepped(ring, data);
                io_uring_cqe_seen(ring, cqe);
                continue;
            }

            /*
             * 全都做完了。如果写了，就没别的事可做了。如果读取，则将相应的写入排队。
             * */

            if (data->read) {
                queue_write(ring, data);
                write_left -= data->first_len;
                reads--;
                writes++;
            } else {
                free(data);
                writes--;
            }
            io_uring_cqe_seen(ring, cqe);
        }
    }

    return 0;
}

int main(int argc, char *argv[]) {
    struct io_uring ring;
    off_t insize;
    int ret;

    if (argc < 3) {
        printf("Usage: %s <infile> <outfile>\n", argv[0]);
        return 1;
    }

    infd = open(argv[1], O_RDONLY);
    if (infd < 0) {
        perror("open infile");
        return 1;
    }

    outfd = open(argv[2], O_WRONLY | O_CREAT | O_TRUNC, 0644);
    if (outfd < 0) {
        perror("open outfile");
        return 1;
    }

    if (setup_context(QD, &ring))
        return 1;

    if (get_file_size(infd, &insize))
        return 1;

    ret = copy_file(&ring, insize);

    close(infd);
    close(outfd);
    io_uring_queue_exit(&ring);
    return ret;
}
```

# 程序结构

这个复制程序像大多数其他程序一样，将第一个参数指向的文件复制到第二个参数指向的文件中。
该程序的核心是 **`copy_ file()`** 函数。在这里，我们设置了一个外层的 `while` 循环，而这个循环又包含了另外2个嵌套在同一层次的 `while` 循环。虽然外层的 while 循环是为了确保源文件的所有字节都被复制，但是第一个嵌套 `while` 循环的任务是创建尽可能多的 [readv(2)](http://man7.org/linux/man-pages/man2/readv.2.html) 类型请求。事实上，
它排队的数量是队列深度允许的数量。

一旦队列满了，我们就进入第二个嵌套的 `while` 循环。这个循环收集完成队列条目，
并提交写入目标文件的请求，现在数据已经被读取。有几个变量跟踪状态，可能会有点混乱。但异步文件复制程序能有多难呢？:)

基于 [unixism.net/2020/04/io-uring-by-example-part-2-queuing-multiple-requests/](unixism.net/2020/04/io-uring-by-example-part-2-queuing-multiple-requests/)

# 源码
此示例和其他示例的源代码可在[Github](https://github.com/shuveb/loti-examples)上使用。