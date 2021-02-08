---
title: 注册一个eventfd
date: 2021-02-08 09:39:20
categories: ["Lord of the io_uring"]
tags:
  - io_uring

---

关于 [eventfd(2)](http://man7.org/linux/man-pages/man2/eventfd.2.html) 系统调用的细节不在讨论范围内。你可能需要查看 [eventfd(2)](http://man7.org/linux/man-pages/man2/eventfd.2.html) man 页面来了解该系统调用的描述。 [eventfd(2)](http://man7.org/linux/man-pages/man2/eventfd.2.html) 是一个 Linux 特有的同步机制。

`io_uring` 能够在事件完成时在eventfd实例上发布事件。该功能允许使用 [poll(2)](http://man7.org/linux/man-pages/man2/poll.2.html) 或 [epoll(7)](http://man7.org/linux/man-pages/man7/epoll.7.html) 复用I/O的进程将 `io_uring` 注册的eventfd实例文件描述符添加到兴趣列表中，以便 [poll(2)](http://man7.org/linux/man-pages/man2/poll.2.html) 或 [epoll(7)](http://man7.org/linux/man-pages/man7/epoll.7.html) 在完成时通过 `io_uring` 通知它们。这允许这样的程序忙于处理它们现有的事件循环，而不是在调用 [io_uring_wait_cqe()](http://man7.org/linux/man-pages/man7/epoll.7.html) 时被阻塞。

```c
#include <sys/eventfd.h>
#include <unistd.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <pthread.h>
#include <liburing.h>
#include <fcntl.h>

#define BUFF_SZ   512

char buff[BUFF_SZ + 1];
struct io_uring ring;

void error_exit(char *message) {
    perror(message);
    exit(EXIT_FAILURE);
}

void *listener_thread(void *data) {
    struct io_uring_cqe *cqe;
    int efd = (int) data;
    eventfd_t v;
    printf("%s: Waiting for completion event...\n", __FUNCTION__);

    int ret = eventfd_read(efd, &v);
    if (ret < 0) error_exit("eventfd_read");

    printf("%s: Got completion event.\n", __FUNCTION__);

    ret = io_uring_wait_cqe(&ring, &cqe);
    if (ret < 0) {
        fprintf(stderr, "Error waiting for completion: %s\n",
                strerror(-ret));
        return NULL;
    }
    /* 现在我们有了CQE，让我们来处理它 */
    if (cqe->res < 0) {
        fprintf(stderr, "Error in async operation: %s\n", strerror(-cqe->res));
    }
    printf("Result of the operation: %d\n", cqe->res);
    io_uring_cqe_seen(&ring, cqe);

    printf("Contents read from file:\n%s\n", buff);
    return NULL;
}

int setup_io_uring(int efd) {
    int ret = io_uring_queue_init(8, &ring, 0);
    if (ret) {
        fprintf(stderr, "Unable to setup io_uring: %s\n", strerror(-ret));
        return 1;
    }
    io_uring_register_eventfd(&ring, efd);
    return 0;
}

int read_file_with_io_uring() {
    struct io_uring_sqe *sqe;

    sqe = io_uring_get_sqe(&ring);
    if (!sqe) {
        fprintf(stderr, "Could not get SQE.\n");
        return 1;
    }

    int fd = open("/etc/passwd", O_RDONLY);
    io_uring_prep_read(sqe, fd, buff, BUFF_SZ, 0);
    io_uring_submit(&ring);

    return 0;
}

int main() {
    pthread_t t;
    int efd;

    /* 创建一个eventfd实例 */
    efd = eventfd(0, 0);
    if (efd < 0)
        error_exit("eventfd");

    /* 创建监听线程 */
    pthread_create(&t, NULL, listener_thread, (void *)efd);

    sleep(2);

    /* 设置 io_uring 实例和注册eventfd */
    setup_io_uring(efd);

    /* 初始化读 io_uring */
    read_file_with_io_uring();

    /* 等待监听线程完成 */
    pthread_join(t, NULL);

    /* 所有完成，清空和退出 */
    io_uring_queue_exit(&ring);
    return EXIT_SUCCESS;
}
```

# 它是如何工作的

在主线程中，我们创建了一个[eventfd(2)](http://man7.org/linux/man-pages/man2/eventfd.2.html)实例。然后创建一个线程，将 `eventfd` 文件描述符传递给它。在线程中，我们打印一条消息并立即从 `eventfd` 文件描述符中读取。这将导致线程阻塞，因为在 `eventfd` 实例上还没有发布任何事件。

当子线程在读取 `eventfd` 文件描述符时阻塞，我们在父线程中休眠2秒以清楚地感知这个序列。接下来，在 `setup_io_uring()` 中，我们创建一个 `io_uring` 实例，并向它注册 `eventfd` 文件描述符。这将导致 `io_uring` 在每一次完成事件时，都会在这个 `eventfd` 上 post 一个事件。

然后从main调用 `read_file_with_io_uring()` 。在这里，我们提交一个读取文件的请求。这将导致io_uring在注册的eventfd实例上发布一个事件。这现在应该会导致 `[read(2)](http://man7.org/linux/man-pages/man2/read.2.html)` 调用，在该调用中 `listener_thread()` 被阻塞，以解除阻塞并继续执行。在这个线程中，我们获取完成并打印出数据。

	# 注意
	请注意，`eventfd_read()` 是glibc提供的一个库函数。它本质上是在eventfd上调用read。


# 源代码

这个和其他例子的源代码可以在 [Github](https://github.com/shuveb/loti-examples) 上找到。