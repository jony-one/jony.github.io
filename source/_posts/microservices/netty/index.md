---
title: Netty 学习 - 初体验
date: 2021-03-04 13:17:12
categories: 
	- [微服务]
tags:
  - microservices
  - Netty
author: Jony

---

# Netty 


Netty Java 网络编程的代表，很多流行的网络框架都出自 Netty 之手。

## Netty 主要抽象概念

Channel、Eventloop 和 ChannelFuture 主要 Netty 网络抽象的一个代表

- Channel 相当于 Socket
- EventLoop 相当于逻辑层、线程、并发处理
- ChannelFuture 异步回掉通知机制

## Channel 

Channel 的主要操作有 bind、connect、read、write 与底层提供的网络编程原语相同。Netty 的 Channel 主要为了降低 Socket 类的复杂性。并且做了很多预定义类：

- EmbeddedChannel
- LocalServerChannel
- NioDatagramChannel
- NioSctpChannel
- NioSocketChannel

## EventLoop 

EventLoop 定义了 Netty 的核心抽象，用于处理连接的生命周期中所发生的事件。 Channel 、EventLoop、Thread 和 EventLoopGroup 之间的关系：

- 一个 EventLoopGroup 包含一个或者多个 EventLoop
- 一个 EventLoop 在它的生命周期内只和一个 Thread 绑定
- 所有由 EventLoop 处理的 I/O 事件都将在它专有的 Thread 上被处理
- 一个 Channel 在它的生命周期内只注册于一个 EventLoop
- 一个 EventLoop 可能会被分配给一个或多个 Channel

## ChannelFuture 

因为 Netty 中所有的 I/O 操作都是异步的。即操作不会立即得到响应，需要一种在某个时间点或者事件发生之后获取结果的方法。所以 Netty 提供了 ChannelFuture 接口，其中 addListener 方法注册一个 ChannelFutureListener 类，在每个事件发生后进行回掉通知。


# 主力 ChannelHandler 和 ChannelPipeline

## ChannelHandler

应用开发者角度看 Netty 的主要组件时 ChannelHandler ，因为它充当了整个出站和入站处理逻辑的容器。ChannelHandler 可专门用于几乎任何类型的动作，
例如数据格式转换、异常处理等。通常一个应用程序有一个至多个 ChannelHandler。

- 编码器和解码就是 ChannelHandler
	- 入栈：将字节转换为一种对象，ByteToMessageDecoder。将对象转为字节，MessageToByteEncoder
	- 对于入栈事件，都会调用 channelRead 方法读取字节，再由这个方法调用 decode 进行解码操作，然后将解码的字节转发给 ChannelPipeline 的下一个 ChannelInboundHandler
	- 对于出栈事件，则相反。将消息转为字节，并将它们转发给下一个 ChannelOutboundHandler
- ChannelInboundHandlerAdapter 自己扩展几个消息处理器，即处理已经被解码的消息
	- 扩展自基类 SimpleChannelInboundHandler<T> 就是要处理消息的 Java 类型
	- 这种类型 ChannelHandler 种最重要的方法时 channelRead0（ChannelHandlerContext, T） 

## ChannelPipeline 

ChannelPipeline 提供了 ChannelHandler 链的容器，定义了用于该链上传播事件流的 API 。


## ChannelHandlerContext

ChannelHandlerContext 代表 ChannelHandler 和 ChannelPipeline 之间的关联，当 ChannelHandler 添加到 ChannelPipeline 中是，都会创建ChannelHandlerContext。

ChannelHandler 与 ChannelPipeline 的关系就如同流水线与操作员的关系，当资源（ChannelHandler）在流水线（ChannelPipeline）上流转时，每个操作员（ChannelHandler）会判断当前流程是否需要自己处理，当需要自己处理时，会触发指定的一个至多个动作对资源进行处理，处理完成之后继续在流水线上流转直至尾端出栈或者入栈。ChannelHandler 与 ChannelPipeline 通过 ChannelHandlerContext 解耦绑定。

--------


# 生命周期

## Channel 生命周期

状态|描述
--|:--:
ChannelUnregistered| Channel 已经被创建，还没有注册
ChannelRegistered| Channel 已经被注册到 EventLoop
ChannelActive | Channel处于活动状态（已经连接到远程节点）。可以接收和发送数据
ChannelInactive | Channel 没有连接到远程节点

生命周期轮转： ChannelRegistered -> ChannelActive -> ChannelInactive -> ChannelUnregistered




## ChannelHandler 生命周期

状态|描述
--|:--:
handlerAdded | 当添加到 ChannelPipeline 中时被触发
handlerRemoved | 从 ChannelPipeline 中移除时触发
exceptionCaught | 处理过程中在 ChannelPipeline 中由错误产生时触发

ChannelPromise 与 ChannelFuture 用于在操作完成时触发，有两种情况成功和失败。
其他子类：ChannelInboundHandler 和 ChannelOutboundHandler 只是在上述组件上面的扩展


## ChannelPipeline 生命周期

事件|描述
--|:--:
addFirst、addBefore、addAfter、addLast | 将 ChannelHandler 添加到 ChannelPipeline 中
remove | 将 ChannelHandler 从 ChannelPipeline 中移除
replace | 将 ChannelPipeline 中的 ChannelHandler 替换为另一个
get | 通过类型或者名称返回 ChannelHandler
context | 返回和 ChannelHandler 绑定的 ChannelHandlerContext
names | 返回 ChannelPipeline 中所有 ChannelHandler 的名称


ChannelPipeline 是一个 Channel 的入栈和出栈事件的 ChannelHandler 实例链。每一个新创建的 Channel 都会被分配一个新的 ChannelPipeline 且是永久性的无法切换和分离。
ChannelPipeline 保存了与 Channel 相关联的 ChannelHandler 并且可以根据需要动态的修改 ChannelHandler 。具有丰富的 API 可以被调用。以响应入站出战事件。


## ChannelHandlerContext 生命周期

ChannelHandlerContext 的主要功能是管理它所关联的 ChannelHandler 和在同一个 ChannelPipeline 中的其他 ChannelHandler 之间的交互
- ChannelHandlerContext 和 ChannelHandler 之间的关联永远不会改变的，所以缓存对他的引用是安全的


# 总结

Netty 总体还是围绕这三大组件来操作：Channel、EventLoop、ChannelFuture。Channel 代表是事件源。EventLoop 代表执行线程，ChannelFuture 获取执行结果。
入站和出站事件围绕具体化的三大家族：ChannelHandler、ChannelPipeline、ChannelHandlerContext 来做具体的操作，ChannelHandler 代表了对数据的具体操作，ChannelPipeline 代表了操作链，ChanneHandlerContext 是在 Channel 创建的时候被创建出来贯穿了整个操作链。


# Demo 案例测试

## 客户端

EchoClientOutboundHandler.java
```java
@ChannelHandler.Sharable
public class EchoClientOutboundHandler extends ChannelOutboundHandlerAdapter {

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        System.out.println("write事件触发了我");
        super.write(ctx, msg, promise);
    }

    @Override
    public void flush(ChannelHandlerContext ctx) throws Exception {
        System.out.println("flush事件触发了我");
        super.flush(ctx);
    }
}

```

EchoClientHandler.java
```java
public class EchoClientHandler extends SimpleChannelInboundHandler<ByteBuf> {

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        ctx.writeAndFlush(Unpooled.copiedBuffer("Netty Test!!!!!!!", CharsetUtil.UTF_8));
    }


    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        System.out.println("Client Received:" + msg.toString(CharsetUtil.UTF_8));
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
       ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}

```

EchoClient.java
```java
public final class EchoClient {
    static final String HOST = System.getProperty("host", "127.0.0.1");
    static final int PORT = Integer.parseInt(System.getProperty("port", "8007"));

    public static void main(String[] args) throws Exception {
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap b = new Bootstrap();
            b.group(group)
             .channel(NioSocketChannel.class)
             .option(ChannelOption.TCP_NODELAY, true)
             .handler(new ChannelInitializer<SocketChannel>() {
                 @Override
                 public void initChannel(SocketChannel ch) throws Exception {
                     ChannelPipeline p = ch.pipeline();
                     //p.addLast(new LoggingHandler(LogLevel.INFO));
                     p.addLast(new EchoClientOutboundHandler());
                     p.addLast(new EchoClientHandler());
                 }
             });
            ChannelFuture f = b.connect(HOST, PORT).sync();
            f.channel().closeFuture().sync();
        } finally { group.shutdownGracefully();
        }
    }
}

```

## 服务端

EchoServerOutboundHandler.java
```java
@ChannelHandler.Sharable
public class EchoServerOutboundHandler extends ChannelOutboundHandlerAdapter {

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        System.out.println("write 事件触发了我");
        super.write(ctx, msg, promise);
    }

    @Override
    public void flush(ChannelHandlerContext ctx) throws Exception {
        System.out.println("flush 事件触发了我");
        super.flush(ctx);
    }
}

```


EchoServerHandler.java
```java
@Sharable
public class EchoServerHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ByteBuf byteBuf = (ByteBuf) msg;
        System.out.println("Server receive:" + byteBuf.toString(CharsetUtil.UTF_8));
        ctx.write(msg);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}

```

EchoServer.java
```java
public final class EchoServer {

    static final int PORT = Integer.parseInt(System.getProperty("port", "8007"));

    public static void main(String[] args) throws Exception {
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        final EchoServerHandler serverHandler = new EchoServerHandler();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
             .channel(NioServerSocketChannel.class)
             .option(ChannelOption.SO_BACKLOG, 100)
             .handler(new LoggingHandler(LogLevel.INFO))
             .childHandler(new ChannelInitializer<SocketChannel>() {
                 @Override
                 public void initChannel(SocketChannel ch) throws Exception {
                     ChannelPipeline p = ch.pipeline();
                     p.addLast(new EchoServerOutboundHandler());
                     p.addLast(serverHandler);
                 }
             });
            ChannelFuture f = b.bind(PORT).sync();
            f.channel().closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}

```

-----------

先运行服务端在运行客户端就可以体验到 ChannelHandler 出站入站的事件

--------

Client 输出
```
write事件触发了我
flush事件触发了我
Client Received:Netty Test!!!!!!!
flush事件触发了我
flush事件触发了我
```

-------

Server 输出
```
Server receive:Netty Test!!!!!!!
write 事件触发了我
write 事件触发了我
flush 事件触发了我
```

## 参考文档
[Netty 实战](https://jeff-duan.github.io/downloads/resource/netty/Netty%20in%20Action-%E4%B8%AD%E6%96%87%E7%89%88.pdf)
