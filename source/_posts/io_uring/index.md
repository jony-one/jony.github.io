---
title: 欢迎来到 Lord of the io_uring
date: 2021-02-05 15:19:03
categories: ["Lord of the io_uring"]
tags:
  - io_uring
	
---
io_uring是在Linux下进行异步I/O编程的一种强大的新方法。`io_uring` 消除了上一代I/O子系统的各种限制，拥有巨大的前景。关于io_uring带来的更多细节，请参阅“[什么是 `io_uring`]() ?”

这本IO指南是由Shuveb Hussain创建的，他也是专注于Linux的博客unixism.net的作者。

# Contributing 

本指南的源代码库在Github上。如果你想投稿，请发送我拉请求。如果你在文档或包含的源代码示例中发现任何错误，请在GitHub上提出问题。示例程序的存储库是分开维护的。请看下面的细节。

# 源代码案例

本指南中所有示例程序的源代码都可以在 Github 上找到。如果您在示例中发现了 bug，请在 Github 上提出问题。我想让这些例子简单明了。出于这个原因，我很多不会合并添加特性的拉请求。欢迎提出修复 bug 的请求。

# 文档

引言

- Linux 下的异步编程
- 什么是 io_uring？
- `io_uring` 的底层接口 

教程

- liburing 例子
- cat 使用 liburing 实现
- cp 使用 liburing 实现
- web 服务器使用 liburing 实现
- 探测支持的功能
- 连接请求
- 固定的缓冲区
- 提交队列轮询
- 注册一个eventfd

liburing 参考资料

- liburing 参考资料
- SQE: 提交队列条目
- CQE: 完成队列事件
- 支持的功能
- 安装和卸载
- 提交
- 完成
- 高级使用

io_uring 参考资料

- io_uring 参考资料
- io_uring_setup
- io_uring_enter
- io_uring_register