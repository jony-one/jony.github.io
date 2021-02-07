---
title: liburing 实现 cat 命令
date: 2021-02-07 15:57:55
categories: ["Lord of the io_uring"]
tags:
  - io_uring

---


# cat 使用 liburing 实现

我们看到，用io_uring构建一个读取文件的程序这么简单的东西，可能并不那么简单。事实证明，与读取同步 i/o 文件的程序相比，它的代码更多。但是，如果你分析使用[底层 io _ uring 接口的 cat 实用程序](https://unixism.net/loti/low_level.html#low-level)的代码，你会发现大部分代码都是重复代码，可以很容易地隐藏在一个单独的文件中，它应该不会出现在应用逻辑。无论如何，无论如何，我们学习底层的io_uring细节是为了更好地理解它的工作原理。现在，让我们看看如何使用 liburing 实现功能类似的程序。

``` c
#include <stdio.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/ioctl.h>
#include <liburing.h>
#include <stdlib.h>

#define QUEUE_DEPTH 1
#define BLOCK_SZ    1024

struct file_info {
    off_t file_sz;
    struct iovec iovecs[];      /* Referred by readv/writev */
};

/*
* 返回传入打开文件描述符的文件的大小。 
* 正确处理常规文件和块设备。
* */

off_t get_file_size(int fd) {
    struct stat st;
    // 判断文件是否存在
    if(fstat(fd, &st) < 0) {
        perror("fstat");
        return -1;
    }

    if (S_ISBLK(st.st_mode)) {
        unsigned long long bytes;
        // 获取文件字节大小64位
        if (ioctl(fd, BLKGETSIZE64, &bytes) != 0) {
            perror("ioctl");
            return -1;
        }
        return bytes;
    } else if (S_ISREG(st.st_mode))
        return st.st_size;

    return -1;
}

/*
 * 输出一串长度为len的字符到stdout。
 * 我们在这里使用缓冲输出是有效的，因为我们需要逐字符输出。
 * */
void output_to_console(char *buf, int len) {
    while (len--) {
        fputc(*buf++, stdout);
    }
}

/*
 * 等待完成可用，从readv操作中获取数据并打印到控制台。
 * */

int get_completion_and_print(struct io_uring *ring) {
    struct io_uring_cqe *cqe;
    // 等待完成队列
    int ret = io_uring_wait_cqe(ring, &cqe);
    if (ret < 0) {
        perror("io_uring_wait_cqe");
        return 1;
    }
    // 如果完成队列的事件代码小于0
    if (cqe->res < 0) {
        fprintf(stderr, "Async readv failed.\n");
        return 1;
    }
    // 获取完成队列的数据返回
    struct file_info *fi = io_uring_cqe_get_data(cqe);
    // 文件字节大小初一数据块大小，按照 1024 字节切割
    int blocks = (int) fi->file_sz / BLOCK_SZ;
    if (fi->file_sz % BLOCK_SZ) blocks++;
    for (int i = 0; i < blocks; i ++)
        output_to_console(fi->iovecs[i].iov_base, fi->iovecs[i].iov_len);

    io_uring_cqe_seen(ring, cqe);
    return 0;
}

/*
 * 通过liburing提交readv请求
 * */
int submit_read_request(char *file_path, struct io_uring *ring) {
	// 打开文件
    int file_fd = open(file_path, O_RDONLY);
    if (file_fd < 0) {
        perror("open");
        return 1;
    }
    // 获取文件大小
    off_t file_sz = get_file_size(file_fd);
    // 文件剩余字节数
    off_t bytes_remaining = file_sz;
    // 偏移值
    off_t offset = 0;
    // 当前块大小
    int current_block = 0;
    // 计算文件可以切割多少快
    int blocks = (int) file_sz / BLOCK_SZ;
    if (file_sz % BLOCK_SZ) blocks++;
    // 分配文件大小信息
    struct file_info *fi = malloc(sizeof(*fi) + (sizeof(struct iovec) * blocks));
    // 分配空间
    char *buff = malloc(file_sz);
    if (!buff) {
        fprintf(stderr, "Unable to allocate memory.\n");
        return 1;
    }

    /*
     * 对于我们需要读取的文件的每个块，
     * 我们分配一个iovec结构，它被索引到iovecs数组中。 
     * 此数组作为提交的一部分传入。 如果您不明白这一点，
     * 那么您需要查找readv()和writev()系统调用是如何工作的。
     * */
    while (bytes_remaining) {
        off_t bytes_to_read = bytes_remaining;
        // 每次最多读 1024 字节
        if (bytes_to_read > BLOCK_SZ)
            bytes_to_read = BLOCK_SZ;
        // 偏移值
        offset += bytes_to_read;

        fi->iovecs[current_block].iov_len = bytes_to_read;
        void *buf;
        // posix_memalign:https://blog.csdn.net/wallwind/article/details/7461701
        // 动态分配内存，1024 字节对齐
        if( posix_memalign(&buf, BLOCK_SZ, BLOCK_SZ)) {
            perror("posix_memalign");
            return 1;
        }
        // 数据的基地地址
        fi->iovecs[current_block].iov_base = buf;
        // 当前块大小加一
        current_block++;
        // 剩余字节数减一
        bytes_remaining -= bytes_to_read;
    }
    fi->file_sz = file_sz;

    /* 获取一个 SQE */
    struct io_uring_sqe *sqe = io_uring_get_sqe(ring);
    /* 设置读操作n */
    io_uring_prep_readv(sqe, file_fd, fi->iovecs, blocks, 0);
    /* 设置用户数据 */
    io_uring_sqe_set_data(sqe, fi);
    /* 最后提交请求 */
    io_uring_submit(ring);

    return 0;
}

int main(int argc, char *argv[]) {
    struct io_uring ring;

    if (argc < 2) {
        fprintf(stderr, "Usage: %s [file name] <[file name] ...>\n",
                argv[0]);
        return 1;
    }

    /* Initialize io_uring */
    io_uring_queue_init(QUEUE_DEPTH, &ring, 0);

    for (int i = 1; i < argc; i++) {
        int ret = submit_read_request(argv[i], &ring);
        if (ret) {
            fprintf(stderr, "Error reading file: %s\n", argv[i]);
            return 1;
        }
        get_completion_and_print(&ring);
    }

    /* Call the clean-up function. */
    io_uring_queue_exit(&ring);
    return 0;
}
```

重复的代码被提取后。让我们快速浏览一下。我们像这样初始化 io_uring:
```c
io_uring_queue_init(QUEUE_DEPTH, &ring, 0);
```
在方法 submit_read_request()中，我们得到一个 SQE，准备执行 readv 操作并提交它。

```c
/* 获取一个 SQE */
struct io_uring_sqe *sqe = io_uring_get_sqe(ring);
/* 设置一个 readv 操作 */
io_uring_prep_readv(sqe, file_fd, fi->iovecs, blocks, 0);
/* 设置 user_data */
io_uring_sqe_set_data(sqe, fi);
/* 最后提交请求 */
io_uring_submit(ring);
```

我们等待一个完成事件，然后得到我们在提交端设置的用户数据，如下所示:

```c
struct io_uring_cqe *cqe;
int ret = io_uring_wait_cqe(ring, &cqe);
struct file_info *fi = io_uring_cqe_get_data(cqe);
```

当然，与使用原始界面相比，使用这个界面要简单得多。


# 参考
- [io_uring_queue_init()](https://unixism.net/loti/ref-liburing/setup_teardown.html#c.io_uring_queue_init)
- [io_uring_get_sqe()](https://unixism.net/loti/ref-liburing/submission.html#c.io_uring_get_sqe)
- [io_uring_prep_readv()](https://unixism.net/loti/ref-liburing/submission.html#c.io_uring_prep_readv)
- [io_uring_sqe_set_data()](https://unixism.net/loti/ref-liburing/submission.html#c.io_uring_sqe_set_data)
- [io_uring_submit()](https://unixism.net/loti/ref-liburing/submission.html#c.io_uring_submit)
- [io_uring_wait_cqe()](https://unixism.net/loti/ref-liburing/completion.html#c.io_uring_wait_cqe)
- [io_uring_cqe_get_data()](https://unixism.net/loti/ref-liburing/completion.html#c.io_uring_cqe_get_data)
- [io_uring_queue_exit()](https://unixism.net/loti/ref-liburing/setup_teardown.html#c.io_uring_queue_exit)