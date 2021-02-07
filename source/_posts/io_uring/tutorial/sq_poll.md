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


# 源代码

这个和其他例子的源代码可以在 [Github](https://github.com/shuveb/loti-examples) 上找到。