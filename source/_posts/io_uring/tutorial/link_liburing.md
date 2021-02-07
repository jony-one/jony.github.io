---
title: 请求链
date: 2021-02-07 17:15:34
categories: ["Lord of the io_uring"]
tags:
  - io_uring

---

在`io_uring`中，完成没有按照提交的问题所在的顺序到达。这在[底层io_uring接口](https://unixism.net/loti/low_level.html#low-level)一章中讨论过。如果您想要强制某些操作按顺序进行，该怎么办?这可以通过将请求链实现。这里的示例向您展示了如何实现这一点。

```c
#include <stdio.h>
#include <string.h>
#include <fcntl.h>
#include "liburing.h"

#define FILE_NAME   "/tmp/io_uring_test.txt"
#define STR         "Hello, io_uring!"
char buff[32];

int link_operations(struct io_uring *ring) {
    struct io_uring_sqe *sqe;
    struct io_uring_cqe *cqe;

    int fd = open(FILE_NAME, O_RDWR|O_TRUNC|O_CREAT, 0644);
    if (fd < 0 ) {
        perror("open");
        return 1;
    }

    sqe = io_uring_get_sqe(ring);
    if (!sqe) {
        fprintf(stderr, "Could not get SQE.\n");
        return 1;
    }

    io_uring_prep_write(sqe, fd, STR, strlen(STR), 0 );
    sqe->flags |= IOSQE_IO_LINK;

    sqe = io_uring_get_sqe(ring);
    if (!sqe) {
        fprintf(stderr, "Could not get SQE.\n");
        return 1;
    }

    io_uring_prep_read(sqe, fd, buff, strlen(STR),0);
    sqe->flags |= IOSQE_IO_LINK;

    sqe = io_uring_get_sqe(ring);
    if (!sqe) {
        fprintf(stderr, "Could not get SQE.\n");
        return 1;
    }

    io_uring_prep_close(sqe, fd);

    io_uring_submit(ring);

    for (int i = 0; i < 3; i++) {
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
    printf("Buffer contents: %s\n", buff);
}

int main() {
    struct io_uring ring;

    int ret = io_uring_queue_init(8, &ring, 0);
    if (ret) {
        fprintf(stderr, "Unable to setup io_uring: %s\n", strerror(-ret));
        return 1;
    }
    link_operations(&ring);
    io_uring_queue_exit(&ring);
    return 0;
}
```

这是一个相当简单的程序。我们打开一个空文件，向它写入一个字符串，从文件中读取字符串，然后关闭它。由于 `io_uring` 并不能保证提交的操作会按顺序执行，这可能会给我们的程序带来问题。因为它是一个空文件，在程序的每一次运行中都会被截断，如果如果在读取之前没有完成写操作，那么将没有任何东西可以读。另外，如果关闭操作在读取或写入操作或这两个操作之前完成，这些操作也可能失败。为此，本程序用 `IOSQE_IO_LINK` 标志来链接操作。这样可以保证操作串行地执行。

这个程序理解起来相当简单。在 `link_operations()` 函数中，我们调用 [io_uring_prep_write()](https://unixism.net/loti/ref-liburing/submission.html#c.io_uring_prep_write)，但是在它上面设置 `IOSQE_IO_LINK`标志，这样下一个操作就会和这个操作链接起来。接下来，我们调用 [io_uring_prep_read()](https://unixism.net/loti/ref-liburing/submission.html#c.io_uring_prep_read)，现在它已经链接到了之前的写操作。我们还在此操作上设置了 `IOSQE_IO_LINK` 标志，这样我们用 [io_uring_prep_close()](https://unixism.net/loti/ref-liburing/submission.html#c.io_uring_prep_close)设置的后续关闭操作就会与这个操作链接起来。这样就会使 `io_uring` 接连执行写、读和关闭操作。

# 请求链中的故障

当涉及到链操作时，一个操作的失败会导致所有后续的链接操作失败，并出现错误 "Operation cancelled."。一般情况下，如果你在内核5.6以上版本上运行这个程序，应该会有这个输出。

	→  cmake-build-debug ./link
	Result of the operation: 16
	Result of the operation: 16
	Result of the operation: 0
	Buffer contents: Hello, io_uring!

如果我们切换它们的open()语句

```c
int fd = open(FILE_NAME, O_RDWR|O_TRUNC|O_CREAT, 0644);
```

以只写模式打开文件:

```c
int fd = open(FILE_NAME, O_WRONLY|O_TRUNC|O_CREAT, 0644);
```

我们的写操作应该会通过，但是我们的读操作会失败，因为文件现在是以只写模式打开的。由于后续的close操作链接到read操作。现在这个有缺陷的程序的输出将是。

	→  cmake-build-debug ./link
	Error in async operation: Bad file descriptor
	Result of the operation: -9
	Error in async operation: Operation canceled
	Result of the operation: -125

你看到的第一个错误("Bad file descriptor")是来自于失败的读取操作。你看到的下一个错误("Operation cancelled")是io_uring取消了链接关闭操作。

	# 注意
	请注意，你需要内核5.6或更高版本的内核才能工作，因为在早期版本中不支持读、写和关闭操作。


# 源代码

这个和其他例子的源代码可以在 [Github](https://github.com/shuveb/loti-examples) 上找到。	