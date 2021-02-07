---
title: io_uring 是什么?
date: 2021-02-07 11:57:26
categories: ["Lord of the io_uring"]
tags:
  - io_uring

---

io_uring 是一个新的 Linux 异步 I/O API，由 Facebook 的 Jens Axboe 创建。它的目的是提供一个不受当前[select(2)](http://man7.org/linux/man-pages/man2/select.2.html)、[poll(2)](https://man7.org/linux/man-pages/man2/poll.2.html)、[poll(2)](https://man7.org/linux/man-pages/man2/poll.2.html)或[aio(7)](http://man7.org/linux/man-pages/man7/aio.7.html)系列系统调用限制的API，我们在上一节讨论过。鉴于异步编程模型的用户首先是出于性能的考虑而选择它，因此，拥有一个性能开销非常低的API是有意义的。我们将在后面的章节中看到io_uring是如何实现这一点的。

# io_uring 接口

io_uring这个名字的由来是由于该接口使用环形缓冲区作为内核与用户空间通信的主要接口。虽然涉及到系统调用，但它们被保持在最低限度。并且可以使用轮询模式来尽可能地减少系统调用的需要。

> ## 参阅
> - [提交队列轮询教程](https://unixism.net/loti/tutorial/sq_poll.html#sq-poll)与示例程序。

### 心智模型

为了使用 `io_uring` 构建异步处理I/O的程序，需要构建的心智模型，但是相当简单。

- 有两个环形缓冲区，一个用于提交请求(提交队列或SQ)，另一个用于通知您这些请求已完成(完成队列或CQ)。
- 这些环形缓冲区在内核和用户空间之间共享。您可以使用 `[io_uring_setup()](https://unixism.net/loti/ref-iouring/io_uring_setup.html#c.io_uring_setup)` 设置这些缓冲区，然后通过2个[mmap(2)](http://man7.org/linux/man-pages/man2/mmap.2.html)调用将它们映射到用户空间。
- 你告诉 `io_uring` 你需要做什么(读或写文件，接受客户端连接，等等)，你把它描述为提交队列条目(SQE)的一部分，并把它添加到提交环缓冲区的尾部。
- 然后你通过 `[io_uring_enter()](https://unixism.net/loti/ref-iouring/io_uring_enter.html#c.io_uring_enter)` 系统调用告诉内核你已经在提交队列环形缓冲区中添加了一个SQE。你也可以在进行系统调用之前添加多个SQE。
- 另外，`[io_uring_enter()](https://unixism.net/loti/ref-iouring/io_uring_enter.html#c.io_uring_enter)` 也可以在返回之前等待内核处理一些请求，这样你就知道可以从完成队列读取结果了。
- 内核处理提交的请求，并将完成队列事件（CQE）添加到完成队列环形缓冲区的尾部。
- 您从完成队列环形缓冲区的头部读取CQE。每个SQE对应一个CQE，它包含该特定请求的状态。
- 您可以根据需要继续添加SQE和获取CQE。
- 有一种[轮询模式可用](https://unixism.net/loti/tutorial/sq_poll.html#sq-poll)，内核在该模式下轮询提交队列中的新条目。这避免了每次提交条目进行处理时调用[io_uring_enter()](https://unixism.net/loti/ref-iouring/io_uring_enter.html#c.io_uring_enter)的系统调用开销。

> ### 参考
> - [io_uring 底层接口](https://unixism.net/loti/low_level.html#low-level)

# io_uring 性能

由于内核和用户空间之间共享环形缓冲区，因此io_uring可以是零拷贝系统。当涉及在内核和用户空间之间传输数据的系统调用时，需要复制字节。但是，由于io_uring中的大部分通信是通过内核和用户空间之间共享的缓冲区进行的，因此完全避免了这种巨大的性能开销。虽然系统调用(我们习惯于大量调用)可能看起来不是很大的开销，但在高性能应用程序中，大量调用将开始变得重要。此外，系统调用也不像以前那么便宜了。再加上操作系统为应对[Specter和Meltdown](https://meltdownattack.com/)而采取的工作方法，我们谈论的不是微不足道的开销。因此，在高性能应用程序中，尽可能避免系统调用确实是一个很棒的想法。

在使用同步编程接口时，甚至在Linux下使用异步编程接口时，每个请求的提交都至少涉及一个系统调用。在 `io_uring` 中，您可以添加几个请求，只需添加多个sqe，每个sqe描述您想要的I/O操作，并对io_uring_enter进行一次调用。对于初学者来说，这就是一场胜利。但它会变得更好。

你可以让内核在你将SQEs添加到提交队列中时，进行轮询并提取它们进行处理，这样可以让你不用调用io_uring_enter()来告诉内核提取SQEs。对于高性能的应用，这意味着更少的系统调用开销。更多细节请参见[提交队列轮询教程](https://unixism.net/loti/tutorial/sq_poll.html#sq-poll)。

通过巧妙地使用共享环形缓冲区，`io_uring` 的性能其实是受内存限制的，因为在轮询模式下，我们可以完全不使用系统调用。重要的是要记住，性能基准测试是一个相对的过程，需要有某种共同的参考点。根据[io_uring的论文](https://kernel.dk/io_uring.pdf)，在一台参考机器上，在轮询模式下，io_uring管理着1.7M 4k IOPS的时钟，而[aio(7)](http://man7.org/linux/man-pages/man7/aio.7.html)管理着608k。虽然远超过一倍，但这并不是一个公平的比较，因为 [aio(7)](http://man7.org/linux/man-pages/man7/aio.7.html) 并不具备轮询模式。但即使禁用轮询模式，io_uring也达到了1.2M IOPS，接近于 [aio(7)](http://man7.org/linux/man-pages/man7/aio.7.html) 的两倍。

为了检查 `io_uring` 接口的原始吞吐量，有一个no-op请求类型。有了这个类型，在参考机器上，`io_uring` 实现了每秒20M的消息量。更多细节请参见 [io_uring_prep_nop()](https://unixism.net/loti/ref-liburing/submission.html#c.io_uring_prep_nop)。

# 使用底层API的 Demo

编写一个小程序来读取文件并将它们打印到控制台，就像Unix的 `cat` 实用程序一样，这可能是一个很好的起点，可以让你熟悉 `io_uring` API。请看下一章中的一个例子。


# 只使用 liburing

虽然熟悉低级别的 `io_uring` API肯定是件好事，但在实际的、严肃的程序中，你可能想使用liburing提供的更高级别的接口。像QEMU这样的程序已经在使用它了。如果 `liburing` 从来没有存在过，你就会在低级的IO接口上构建一些抽象层， `liburing` 为你做到了这一点，它也是一个经过深思熟虑的接口。简而言之，你可能应该花一些精力去了解底层的 `io_uring` 接口是如何工作的，但默认情况下，你应该在你的程序中真正使用 `liburing` 。

