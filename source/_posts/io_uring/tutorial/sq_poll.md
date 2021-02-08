---
title: 轮询提交队列
date: 2021-02-07 17:54:16
categories: ["Lord of the io_uring"]
tags:
  - io_uring
---

减少系统调用的次数是 `io_uring` 的一个主要目的。为此， `io_uring` 允许你提交I/O请求，而不需要进行一次系统调用。这是通过 `io_uring` 支持的一个特殊的提交队列轮询功能实现的。在这种模式下，当你的程序设置了轮询模式后， `io_uring` 就会启动一个特殊的内核线程，轮询共享的提交队列，查看你的程序可能添加的条目。这样一来，你只需要向共享队列提交条目，内核线程就会看到它，并获取提交队列条目，而不需要你的程序进行 [io_uring_enter()](https://unixism.net/loti/ref-iouring/io_uring_enter.html#c.io_uring_enter) 系统调用，这通常由`liburing` 来处理。这是在用户空间和内核之间共享队列的一个好处。

如何使用这种模式？这个想法很简单。你通过在 `io_uring_params` 结构的 `flags` 成员中设置 `IORING_SETUP_SQPOLL` 标志来告诉 `io_uring` 你想使用这个模式。如果和你的进程一起启动的内核线程在一段时间内没有看到任何提交，它就会退出，你的程序需要再调用一次 [io_uring_enter()](https://unixism.net/loti/ref-iouring/io_uring_enter.html#c.io_uring_enter)系统调用来唤醒它。这个时间段可以通过 :c:struct\`io_uring_params\` 结构的 `sq_thread_idle` 成员来配置。但是，如果不断收到提交，内核轮询器线程应该永远不会休眠


	 # 注意：
	 当使用 liburing 时，你永远不会直接调用 [io_uring_enter()](https://unixism.net/loti/ref-iouring/io_uring_enter.html#c.io_uring_enter) 系统调用。这通常由liburing的 [io_uring_submit()](https://unixism.net/loti/ref-liburing/submission.html#c.io_uring_submit) 函数来处理。它能自动判断你是否使用轮询模式，并处理你的程序何时需要调用 [io_uring_enter()](https://unixism.net/loti/ref-iouring/io_uring_enter.html#c.io_uring_enter)，而不需要你判断。

	 # 注意
	 内核的轮询线程会占用大量的CPU。你需要小心使用这个功能。设置一个非常大的sq_thread_idle值会导致内核线程在你的程序没有提交时继续消耗CPU。如果你真的期望处理大量的I/O，那么使用这个特性是个好主意。而即使你这样做，也最好将轮询线程的空闲值设置为最多几秒钟。

但是，如果您需要使用此功能，则还需要将其与 [io_uring_register_files()](https://unixism.net/loti/ref-liburing/advanced_usage.html#c.io_uring_register_files)结合使用。使用它，您可以预先告诉内核有关文件描述符数组的信息。这只是您在启动I/O之前打开的常规文件描述符的数组。在提交期间，您需要在SQE的 `flags` 字段中设置 `IOSQE_FIXED_FILE` 标志，并传递您之前设置的文件描述符数组中的文件描述符的索引，而不是像通常那样将文件描述符传递给[io_uring_prep_read()](https://unixism.net/loti/ref-liburing/submission.html#c.io_uring_prep_read)或 [io_uring_prep_write()](https://unixism.net/loti/ref-liburing/submission.html#c.io_uring_prep_write) 等调用。

```c
#include <unistd.h>
#include <stdlib.h>
#include <fcntl.h>
#include <liburing.h>
#include <string.h>

#define BUF_SIZE    512
#define FILE_NAME1  "/tmp/io_uring_sq_test.txt"
#define STR1        "What is this life if, full of care,\n"
#define STR2        "We have no time to stand and stare."

void print_sq_poll_kernel_thread_status() {

    if (system("ps --ppid 2 | grep io_uring-sq" ) == 0)
        printf("Kernel thread io_uring-sq found running...\n");
    else
        printf("Kernel thread io_uring-sq is not running.\n");
}

int start_sq_polling_ops(struct io_uring *ring) {
    int fds[2];
    char buff1[BUF_SIZE];
    char buff2[BUF_SIZE];
    char buff3[BUF_SIZE];
    char buff4[BUF_SIZE];
    struct io_uring_sqe *sqe;
    struct io_uring_cqe *cqe;
    int str1_sz = strlen(STR1);
    int str2_sz = strlen(STR2);

    fds[0] = open(FILE_NAME1, O_RDWR | O_TRUNC | O_CREAT, 0644);
    if (fds[0] < 0 ) {
        perror("open");
        return 1;
    }

    memset(buff1, 0, BUF_SIZE);
    memset(buff2, 0, BUF_SIZE);
    memset(buff3, 0, BUF_SIZE);
    memset(buff4, 0, BUF_SIZE);
    strncpy(buff1, STR1, str1_sz);
    strncpy(buff2, STR2, str2_sz);

    int ret = io_uring_register_files(ring, fds, 1);
    if(ret) {
        fprintf(stderr, "Error registering buffers: %s", strerror(-ret));
        return 1;
    }

    sqe = io_uring_get_sqe(ring);
    if (!sqe) {
        fprintf(stderr, "Could not get SQE.\n");
        return 1;
    }
    io_uring_prep_write(sqe, 0, buff1, str1_sz, 0);
    sqe->flags |= IOSQE_FIXED_FILE;

    sqe = io_uring_get_sqe(ring);
    if (!sqe) {
        fprintf(stderr, "Could not get SQE.\n");
        return 1;
    }
    io_uring_prep_write(sqe, 0, buff2, str2_sz, str1_sz);
    sqe->flags |= IOSQE_FIXED_FILE;

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

    print_sq_poll_kernel_thread_status();

    sqe = io_uring_get_sqe(ring);
    if (!sqe) {
        fprintf(stderr, "Could not get SQE.\n");
        return 1;
    }
    io_uring_prep_read(sqe, 0, buff3, str1_sz, 0);
    sqe->flags |= IOSQE_FIXED_FILE;

    sqe = io_uring_get_sqe(ring);
    if (!sqe) {
        fprintf(stderr, "Could not get SQE.\n");
        return 1;
    }
    io_uring_prep_read(sqe, 0, buff4, str2_sz, str1_sz);
    sqe->flags |= IOSQE_FIXED_FILE;

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
    printf("%s%s", buff3, buff4);
}

int main() {
    struct io_uring ring;
    struct io_uring_params params;

    if (geteuid()) {
        fprintf(stderr, "You need root privileges to run this program.\n");
        return 1;
    }

    print_sq_poll_kernel_thread_status();

    memset(&params, 0, sizeof(params));
    params.flags |= IORING_SETUP_SQPOLL;
    params.sq_thread_idle = 2000;

    int ret = io_uring_queue_init_params(8, &ring, &params);
    if (ret) {
        fprintf(stderr, "Unable to setup io_uring: %s\n", strerror(-ret));
        return 1;
    }
    start_sq_polling_ops(&ring);
    io_uring_queue_exit(&ring);
    return 0;
}
```

# 它是如何工作的

这个示例程序很像我们之前看到的固定缓冲区示例。虽然我们使用 **[io_uring_prep_read_fixed()](https://unixism.net/loti/ref-liburing/submission.html#c.io_uring_prep_read_fixed)** 和 **[io_uring_prep_write_fixed()](https://unixism.net/loti/ref-liburing/submission.html#c.io_uring_prep_write_fixed)** 等专门函数来处理固定缓冲区，但我们使用了 **[io_uring_prep_read()](https://unixism.net/loti/ref-liburing/submission.html#c.io_uring_prep_read)**、**[io_uring_prep_readv()](https://unixism.net/loti/ref-liburing/submission.html#c.io_uring_prep_readv)**、**[io_uring_prep_write()](https://unixism.net/loti/ref-liburing/submission.html#c.io_uring_prep_write)**或**[io_uring_prep_writev()](https://unixism.net/loti/ref-liburing/submission.html#c.io_uring_prep_writev)**等常规函数。然而，在使用描述提交的SQE中，设置 ``IOSQE_FIXED_FILE`` 标志时，在文件描述符数组中使用文件描述符的索引，而不是在调用**[io_uring_prep_readv()](https://unixism.net/loti/ref-liburing/submission.html#c.io_uring_prep_readv)**和 **[io_uring_prep_writev()](https://unixism.net/loti/ref-liburing/submission.html#c.io_uring_prep_writev)** 等调用中使用文件描述符本身。

当程序启动时，在我们设置 `io_uring` 实例之前，我们打印执行提交队列轮询的内核线程的运行状态。此线程的名称为 `io_uring-sq` 。函数 `print_sq_poll_kernel_thread_status()` 负责打印此状态。当然，如果有任何其他进程使用提交队列轮询，您将看到该内核线程确实在运行。所有内核线程的父线是 `kthreadd` 内核线程，这个线程是在 `init`之后马上启动的，众所周知，它的进程ID为1。因此，`kthreadd` 的PID为2，我们可以利用这一事实作为一种简单的优化来只过滤内核线程。

为了初始化`io_uring`，我们使用 **[io_uring_queue_init_params()](https://unixism.net/loti/ref-liburing/setup_teardown.html#c.io_uring_queue_init_params)** 而不是常用的 **[io_uring_queue_init()](https://unixism.net/loti/ref-liburing/setup_teardown.html#c.io_uring_queue_init)** ，因为这需要一个指向 **io_uring_params** 结构的指针作为参数。正是在这个参数中，我们指定了 `IORING_SETUP_SQPOLL` 作为 `flags` 字段的一部分，并将 `sq_thread_idle` 设置为2000，这是提交队列轮询器内核线程的空闲时间。如果在这许多毫秒内没有提交，线程就会退出，需要通过 `liburing` 在内部进行 **[io_uring_enter()](https://unixism.net/loti/ref-iouring/io_uring_enter.html#c.io_uring_enter)** 系统调用，让内核线程再次启动。

由于提交队列轮询只能与固定文件结合使用，因此我们首先要注册处理的唯一文件描述符。如果要处理更多的文件，这时你要用 **[io_uring_register_files()](https://unixism.net/loti/ref-liburing/advanced_usage.html#c.io_uring_register_files)** 函数打开并注册它们。对于每次提交，您需要使用 `io_sqe_set_flag()` 辅助函数设置 `IOSQE_FIXED_FILE` 标志，并将注册文件数组中打开文件的索引(而不是实际文件描述符本身)提供给 **[io_uring_prep_read()](https://unixism.net/loti/ref-liburing/submission.html#c.io_uring_prep_read)** 或 **[io_uring_prep_write()](https://unixism.net/loti/ref-liburing/submission.html#c.io_uring_prep_write)** 等函数。

在这个例子中，我们有4个缓冲区。前2个缓冲区被2个写操作用来向文件中各写一行。之后，我们用第3个和第4个缓冲区再进行2次读操作，读取这2行写入的内容并打印出来。写操作结束后，我们打印 `io_uring-sq` 内核线程的状态，现在我们应该发现它正在运行。

	➜  sudo ./sq_poll
	[sudo] password for shuveb:
	Kernel thread io_uring-sq is not running.
	Result of the operation: 36
	Result of the operation: 35
	   1750 ?        00:00:00 io_uring-sq
	Kernel thread io_uring-sq found running...
	Result of the operation: 36
	Result of the operation: 35
	Contents read from file:
	What is this life if, full of care,
	We have no time to stand and stare.%    



# 通过内核验证轮询

但是，您需要调用 **[io_uring_submit()](https://unixism.net/loti/ref-liburing/submission.html#c.io_uring_submit)**。我们在前面的示例中看到，这会导致发出 **[io_uring_enter()](https://unixism.net/loti/ref-iouring/io_uring_enter.html#c.io_uring_enter)** 系统调用。但是，在已经设置了 `IORING_SETUP_SQPOLL` 标志的情况下不是这样。 `liburing` 完全隐藏了这一点，同时保持了程序的持续接口。但是，我们能证实这一点吗?当然，我们可以通过使用eBPF的 `bpftrace` 程序来窥探系统。这里，我们将在 `io_uring` 设置的内核中使用跟踪点来证明，当我们设置 `IORING_SETUP_SQPOLL` 并提交I/O请求时，尽管我们调用了 **[io_uring_submit()](https://unixism.net/loti/ref-iouring/io_uring_enter.html#c.io_uring_enter)** 函数，但我们的程序不会执行 **[io_uring_enter()](https://unixism.net/loti/ref-iouring/io_uring_enter.html#c.io_uring_enter)** 系统调用。如前所述，对于高吞吐量的程序，我们的想法是尽可能地避免系统调用。

在下面的程序中，我们添加一个 tracepoint 到 `io_uring` 的 `io_uring_submit_sqe` 。每当一个SQE被提交给内核时，这个tracepoint就会被触发。每次这个tracepoint被触发，我们使用 `bpftrace` 来打印命令的名称和它的PID。首先，让我们在一个终端上运行 `bpftrace` 命令，同时在另一个终端上运行[固定缓冲区](https://unixism.net/loti/tutorial/fixed_buffers.html#fixed-buffers)的例子。下面是我机器上的输出示例。你可以看到 `fixed_buffers` 是提交SQE的那个。

	➜  sudo bpftrace -e 'tracepoint:io_uring:io_uring_submit_sqe {printf("%s(%d)\n", comm, pid);}'
	Attaching 1 probe...
	fixed_buffers(30336)
	fixed_buffers(30336)
	fixed_buffers(30336)
	fixed_buffers(30336)

让我们重复前面的练习，但现在运行当前的例子。你可以看到，SQE的提交是通过 `io_uring_sq` 内核线程进行的。因此我们避免了系统调用。

	➜  sudo bpftrace -e 'tracepoint:io_uring:io_uring_submit_sqe {printf("%s(%d)\n", comm, pid);}'
	io_uring-sq(30429)
	io_uring-sq(30429)
	io_uring-sq(30429)
	io_uring-sq(30429)


# 源代码

这个和其他例子的源代码可以在 [Github](https://github.com/shuveb/loti-examples) 上找到。