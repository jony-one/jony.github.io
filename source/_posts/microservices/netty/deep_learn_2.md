---
title: Netty 学习 - 深入理解 1
date: 2021-03-25 23:17:12
categories: 
	- [微服务]
tags:
  - microservices
  - Netty 
author: Jony

---

# Netty 深入理解

先考虑几个问题：

1. Netty 启动的时候会做些什么操作
2. Netty 初始化的时候需要做些什么操作
3. 流量怎么进入 Netty 的，Netty 对其怎么做出处理的
4. 流量在Netty 内部如何流转的
5. Netty 如何做到高并发的
6. Netty 的线程模型是怎么样的
7. Netty 十万个为什么

# Netty 深入理解

## Netty 怎么接入请求的，怎么处理这些请求的

先简单理解下 reactor 线程模型，即有个线程在不停的轮询绑定的端口直到有新的连接进来，然后封装成一个程序可以处理的对象扔给后面的线程组去处理。也就说监听线程和处理线程进行了一个解耦并不是传统的同一个线程。


而在 Netty 中的接口轮询的线程成为 Boss 线程，处理线程称为 Worker 线程。不管是 Boss 线程还是 Worker 线程，所做的事情均分为以下三个步骤：
1. 轮询注册在 Selector 的 IO 事件
2. 处理 IO 事件
3. 执行异步 task

对于 boss 线程，第一步轮询处理的基本都是 accept 事件，表示有新的丽娜姐，而 worker 线程轮询处理的基本都是 read/write 事件，表示网络的读写事件。

服务端启动过程是在用户线程中开启，第一次添加异步任务的时候启动 boss 线程被启动，netty 将处理新连接的过程封装成一个 Channel，对于的 pipeline 会按顺序处理新建立的连接。
所以我们关注三个点：1.启动 boss 线程，监听新的连接，2. 将连接封装成 Channel ， 3. pipeline 按顺序处理新的连接


回顾上一页的代码，在
```java
public abstract class SingleThreadEventLoop extends SingleThreadEventExecutor implements EventLoop {
    public ChannelFuture register(final ChannelPromise promise) {
        ObjectUtil.checkNotNull(promise, "promise");
        promise.channel().unsafe().register(this, promise);
        return promise;
    }
}    
```    











## 参考文档
[Netty 实战](https://jeff-duan.github.io/downloads/resource/netty/Netty%20in%20Action-%E4%B8%AD%E6%96%87%E7%89%88.pdf)
[源码之下无秘密](https://segmentfault.com/a/1190000007282628)
[Related articles](https://netty.io/wiki/related-articles.html)