---
title: 固定缓冲区
date: 2021-02-07 17:43:24
categories: ["Lord of the io_uring"]
tags:
  - io_uring
---

使用固定缓冲区的思想是这样的:您提供一组用`iovec`结构体数组描述的缓冲区，并使用 `[io_uring_register_buffers()](https://unixism.net/loti/ref-liburing/advanced_usage.html#c.io_uring_register_buffers)` 将它们注册到内核。这将导致内核将这些缓冲区映射到内存中，从而避免将来在用户空间中来回复制。然后可以使用像[io_uring_prep_write_fixed()](https://unixism.net/loti/ref-liburing/submission.html#c.io_uring_prep_write_fixed)和[io_uring_prep_read_fixed()](https://unixism.net/loti/ref-liburing/submission.html#c.io_uring_prep_read_fixed)这样的“固定缓冲区”函数来指定要使用的缓冲区的索引。

```c
#include <stdio.h>
#include <string.h>
#include <fcntl.h>
#include <stdlib.h>
#include "liburing.h"

#define BUF_SIZE    512
#define FILE_NAME   "/tmp/io_uring_test.txt"
#define STR1        "What is this life if, full of care,\n"
#define STR2        "We have no time to stand and stare."

int fixed_buffers(struct io_uring *ring) {
    struct iovec iov[4];
    struct io_uring_sqe *sqe;
    struct io_uring_cqe *cqe;

    int fd = open(FILE_NAME, O_RDWR|O_TRUNC|O_CREAT, 0644);
    if (fd < 0 ) {
        perror("open");
        return 1;
    }

    for (int i = 0; i < 4; i++) {
        iov[i].iov_base = malloc(BUF_SIZE);
        iov[i].iov_len = BUF_SIZE;
        memset(iov[i].iov_base, 0, BUF_SIZE);
    }

    int ret = io_uring_register_buffers(ring, iov, 4);
    if(ret) {
        fprintf(stderr, "Error registering buffers: %s", strerror(-ret));
        return 1;
    }

    sqe = io_uring_get_sqe(ring);
    if (!sqe) {
        fprintf(stderr, "Could not get SQE.\n");
        return 1;
    }

    int str1_sz = strlen(STR1);
    int str2_sz = strlen(STR2);
    strncpy(iov[0].iov_base, STR1, str1_sz);
    strncpy(iov[1].iov_base, STR2, str2_sz);
    io_uring_prep_write_fixed(sqe, fd, iov[0].iov_base, str1_sz, 0, 0);

    sqe = io_uring_get_sqe(ring);
    if (!sqe) {
        fprintf(stderr, "Could not get SQE.\n");
        return 1;
    }
    io_uring_prep_write_fixed(sqe, fd, iov[1].iov_base, str2_sz, str1_sz, 1);

    io_uring_submit(ring);

    for(int i = 0; i < 2; i ++) {
        int ret = io_uring_wait_cqe(ring, &cqe);
        if (ret < 0) {
            fprintf(stderr, "Error waiting for completion: %s\n",
                    strerror(-ret));
            return 1;
        }
        /* Now that we have the CQE, let's process the data */
        if (cqe->res < 0) {
            fprintf(stderr, "Error in async operation: %s\n", strerror(-cqe->res));
        }
        printf("Result of the operation: %d\n", cqe->res);
        io_uring_cqe_seen(ring, cqe);
    }

    sqe = io_uring_get_sqe(ring);
    if (!sqe) {
        fprintf(stderr, "Could not get SQE.\n");
        return 1;
    }

    io_uring_prep_read_fixed(sqe, fd, iov[2].iov_base, str1_sz, 0, 2);

    sqe = io_uring_get_sqe(ring);
    if (!sqe) {
        fprintf(stderr, "Could not get SQE.\n");
        return 1;
    }

    io_uring_prep_read_fixed(sqe, fd, iov[3].iov_base, str2_sz, str1_sz, 3);

    io_uring_submit(ring);
    for(int i = 0; i < 2; i ++) {
        int ret = io_uring_wait_cqe(ring, &cqe);
        if (ret < 0) {
            fprintf(stderr, "Error waiting for completion: %s\n",
                    strerror(-ret));
            return 1;
        }
        /* Now that we have the CQE, let's process the data */
        if (cqe->res < 0) {
            fprintf(stderr, "Error in async operation: %s\n", strerror(-cqe->res));
        }
        printf("Result of the operation: %d\n", cqe->res);
        io_uring_cqe_seen(ring, cqe);
    }
    printf("Contents read from file:\n");
    printf("%s%s", iov[2].iov_base, iov[3].iov_base);
}

int main() {
    struct io_uring ring;

    int ret = io_uring_queue_init(8, &ring, 0);
    if (ret) {
        fprintf(stderr, "Unable to setup io_uring: %s\n", strerror(-ret));
        return 1;
    }
    fixed_buffers(&ring);
    io_uring_queue_exit(&ring);
    return 0;
}
```

# 它是如何工作的

我们通过malloc(3)分配4个缓冲区，然后用io_uring_register_buffers()函数向内核注册它们。iovec结构通过持有一个基地址和分配的缓冲区大小来描述每个数组。我们使用一个4个元素长的iovec结构数组来保存我们需要的4个数组的详细信息。

这个程序只是简单的演示了如何使用固定缓冲区，除此之外并没有更多有用的东西。使用两个固定的写操作(io_uring_prep_write_fixed())将两个字符串写入写入到一个使用索引为0和1的缓冲区的文件。 之后，我们使用两个固定的读操作(io_uring_prep_read_fixed())读取文件，这次使用的是2和3的缓冲区。然后我们打印这些读取的结果。

你可以看到这个程序的输出如下所示:

	Result of the operation: 36
	Result of the operation: 35
	Result of the operation: 36
	Result of the operation: 35
	Contents read from file:
	What is this life if, full of care,
	We have no time to stand and stare.

# 源代码

这个和其他例子的源代码可以在 [Github](https://github.com/shuveb/loti-examples) 上找到。