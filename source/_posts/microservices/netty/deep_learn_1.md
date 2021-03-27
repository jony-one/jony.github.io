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


## Netty 启动

简单了解 Netty 之后，Netty 是如何将这些组件组织起来并完成指定的工作的。即：学习 Bootstrap 和 ServerBootstrap
\             \<interface\>
\				Closeable
\                  |
\			AbstractBootstrap
\           /            \\
\    ServerBootstrap   Bootstrap

## 服务端 ServerBootStrap
```java
public final class EchoServer {

    static final int PORT = Integer.parseInt(System.getProperty("port", "8007"));

    public static void main(String[] args) throws Exception {
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup(); // 1. 创建 EventLoopGroup
        final EchoServerHandler serverHandler = new EchoServerHandler();  // 2.创建 ServerBootstrap
        try {
            ServerBootstrap b = new ServerBootstrap();  
            b.group(bossGroup, workerGroup)
             .channel(NioServerSocketChannel.class)   // 3. 指定所使用得 NIO 传输 Channel 
             .option(ChannelOption.SO_BACKLOG, 100)
             .handler(new LoggingHandler(LogLevel.INFO)) // 4. 使用指定得端口设置套接字地址
             .childHandler(new ChannelInitializer<SocketChannel>() {  // 5. 添加一个 EchoServerHandler 到自 Channel 得 ChannelPipeline
                 @Override
                 public void initChannel(SocketChannel ch) throws Exception {
                     ChannelPipeline p = ch.pipeline();
                     p.addLast(new EchoServerOutboundHandler()); // 添加 OutBoundHandler
                     p.addLast(serverHandler);   // 添加 ServerHandler
                 }
             });
            ChannelFuture f = b.bind(PORT).sync();   // 6. 异步绑定服务器，调用 sync 方法阻塞等待知道绑定完成
            f.channel().closeFuture().sync();      // 7. 获取 Channel 得 CloseFuture 并且阻塞档期线程直到它完成
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();  // 8. 关闭 EventLoopGroup 资源
        }
    }
}
```

在 4 处设置了了一个 InetSocketAddres。服务器将绑定到这个地址以监听新的连接请求。
在 5 处，使用了 ChannelInitializer ，一个新的子 Channel 将会被创建，而 ChannelInitializer 将会把 EchoServerHandler 添加到 pipeline 中，这个 ChannelHandler 将会收到有关入站得消息得通知。

整个逻辑步骤回顾一下：

- EchoServerHandler 实现了业务逻辑
- main() 方法引导了服务器
	- 创建一个 ServerBootStrap 得实例以引导进和绑定服务器
	- 创建并分配一个 NioEventLoopGroup 实例以进行事件得处理，如接收新连接以及读写数据
	- 指定服务器绑定得本地 InetSocketAddress
	- 使用一个 EchoServerHandler 得实例初始化每一个新的 Channel
	- 调用 ServerBootstrap.bind 方法绑定服务器

直接剖析下 ServerBootstrap 干了啥：
- EventLoopGroup 将会有单独文章解析，简单来说就是一个轮询，不停得探测 IO 事件并处理 IO 事件，执行其任务
- ServerBootstrap 是一个服务端启动类
- group(bossGroup, workerGroup)，分配两个线程，一个不停得accept外面得连接，然后丢给第二个。第二个用于处理连接
- channel(NioServerSocketChannel.class) 标识服务都启动得是 NIO 相关得 Channel
- handler 标识服务器启动时，需要经过哪些流程
- childHandler 标识一条新的连接进来之后，该怎么处理
- b.bind(PORT).sync() 真正得启动过程，并绑定端口
- f.channel().closeFuture().sync() 等待服务器关闭
- bossGroup.shutdownGracefully();workerGroup.shutdownGracefully(); 关闭两组线程


## 深入细节

ServerBootstrap 一系列的参数配置是将需要得参数保存到 filed。那最终得启动入口在 bind 这个方法里执行，

```java
    public ChannelFuture bind(SocketAddress localAddress) {
        validate();  // 验证必要得参数
        return doBind(ObjectUtil.checkNotNull(localAddress, "localAddress")); // 真正绑定
    }

	private ChannelFuture doBind(final SocketAddress localAddress) {
	    //...
	    final ChannelFuture regFuture = initAndRegister();
	    //...
	    final Channel channel = regFuture.channel();
	    //...
	    doBind0(regFuture, channel, localAddress, promise);
	    //...
	    return promise;
	}  
```

这里我们专注于两个细节：initAndRegister 和 doBind0，其中 init 负责初始化，register 负责注册，

```java
final ChannelFuture initAndRegister() {
    Channel channel = null;
    // ...
    channel = channelFactory.newChannel();
    //...
    init(channel);
    //...
    ChannelFuture regFuture = config().group().register(channel);
    //...
    return regFuture;
}
```

initAndRegister 主要专注于几个事情：
1. 新建一个 channel 对象
2. 初始化这个 channel
3. 将这个 channe 注册到某个对象

在哪具体看下每个步骤得细节：

### 1. 新建一个 channel 对象

`channelFactory.newChannel();` 最终会调用到 `ReflectiveChannelFactory.newChannel()`   方法，可以在初始化 ChannelFactory 得时候可以看出来默认设置了 `ReflectiveChannelFactory` 。 代码略有删减

```java
public class ReflectiveChannelFactory<T extends Channel> implements ChannelFactory<T> {
	private final Constructor<? extends T> constructor;
	public ReflectiveChannelFactory(Class<? extends T> clazz) {
		 this.constructor = clazz.getConstructor();
	}
	...
	    @Override
    public T newChannel() {
        try {
            return constructor.newInstance();
        } catch (Throwable t) {
            throw new ChannelException("Unable to create Channel from class " + constructor.getDeclaringClass(), t);
        }
    }
    ...
}
```

我们可以注意到，方法最终是通过反射来创建一个对象，而这个 class 就是我们在 ServerBootstrap 中传入得 `NioServerSocketChannel.class` 。

剩下来得重心放到 `NioServerSocketChannel.class` 得创建上：

```java
public class NioServerSocketChannel extends AbstractNioMessageChannel
                             implements io.netty.channel.socket.ServerSocketChannel {
    private static final ChannelMetadata METADATA = new ChannelMetadata(false, 16);
    private static final SelectorProvider DEFAULT_SELECTOR_PROVIDER = SelectorProvider.provider();

    private static ServerSocketChannel newSocket(SelectorProvider provider) {
        return provider.openServerSocketChannel();
    }
    private final ServerSocketChannelConfig config;

    public NioServerSocketChannel() {
        this(newSocket(DEFAULT_SELECTOR_PROVIDER));
    }

	...        
    public NioServerSocketChannel(ServerSocketChannel channel) {
        super(null, channel, SelectionKey.OP_ACCEPT);
        config = new NioServerSocketChannelConfig(this, javaChannel().socket());
    }    
}                        
```

可以看见通过 SelectorProvider.openServerSocketChannel 创建一条服务端得 Channel，然后通过创建一个 `NioServerSocketChannelConfig` 其顶层接口为 `ChannelConfig` ，所以 ChannelConfig 也可以是 Netty 里面得一大核心模块，暂时忽略。继续深入父类构造器，最终将看到如下两个抽象类的构造：

```java
public abstract class AbstractChannel extends DefaultAttributeMap implements Channel {
	protected AbstractChannel(Channel parent) {
	    this.parent = parent;
	    id = newId();
	    unsafe = newUnsafe();
	    pipeline = newChannelPipeline();
	}
}
public abstract class AbstractNioChannel extends AbstractChannel {
	protected AbstractNioChannel(Channel parent, SelectableChannel ch, int readInterestOp) {
	    super(parent);
	    this.ch = ch;
	    this.readInterestOp = readInterestOp;
	    //...
	    ch.configureBlocking(false);
	    //...
	}
}
```

这里简单得将前面创建出来得 ServerSocketChannel 保存到成员遍历，然后调用 ch.configureBlocking(false) 设置该 Channel 为非阻塞模式。
`readInterestOp`  就是前面每层传入的 SelectionKey.OP_ACCEPT 。然后看下顶层的设计，新建的三大组件：

`newId` 为每个 Channel 赋值一个唯一的 ID。`newUnsafe` ，在官方的解释中是实际的传输，并且必须从 I/O 线程调用并且最好不允许用户代码调用。最后一个 `newChannelPipeline` 最终调用：

```java
    protected DefaultChannelPipeline(Channel channel) {
        this.channel = ObjectUtil.checkNotNull(channel, "channel");
        succeededFuture = new SucceededChannelFuture(channel, null);
        voidPromise =  new VoidChannelPromise(channel, true);

        tail = new TailContext(this);
        head = new HeadContext(this);

        head.next = tail;
        tail.prev = head;
    }
```

对于 Pipeline 的阅读将由单独的文章。这里见官方释意：处理和拦截 Channel 的出入站事件的 ChannelHandler 列表。
所以总结一下 ServerBootstrap 启动的操作基本就是：

- Channel
- ChannelConfig
- ChannelId
- Unsafe
- Pipeline
- ChannelHandler

基本上都是一些核心组件的创建，但是实际上配置还没有开始，所以继续

----


### 初始化这个 channel

```java
    @Override
    void init(Channel channel) {
        setChannelOptions(channel, newOptionsArray(), logger);
        setAttributes(channel, attrs0().entrySet().toArray(EMPTY_ATTRIBUTE_ARRAY));

        ChannelPipeline p = channel.pipeline();

        final EventLoopGroup currentChildGroup = childGroup;
        final ChannelHandler currentChildHandler = childHandler;
        final Entry<ChannelOption<?>, Object>[] currentChildOptions;
        synchronized (childOptions) {
            currentChildOptions = childOptions.entrySet().toArray(EMPTY_OPTION_ARRAY);
        }
        final Entry<AttributeKey<?>, Object>[] currentChildAttrs = childAttrs.entrySet().toArray(EMPTY_ATTRIBUTE_ARRAY);

        p.addLast(new ChannelInitializer<Channel>() {
            @Override
            public void initChannel(final Channel ch) {
                final ChannelPipeline pipeline = ch.pipeline();
                ChannelHandler handler = config.handler();
                if (handler != null) {
                    pipeline.addLast(handler);
                }

                ch.eventLoop().execute(new Runnable() {
                    @Override
                    public void run() {
                        pipeline.addLast(new ServerBootstrapAcceptor(
                                ch, currentChildGroup, currentChildHandler, currentChildOptions, currentChildAttrs));
                    }
                });
            }
        });
    }
```

这个方法相对比较简单， `setChannelOptions` 、 `setAttributes` 这两方法只是简单的设置 KV 操作，但是具体设置什么 KV 这里还看不出来，所以只能先分析别的代码。加入新的连接处理器：`p.addLast(new ChannelInitializer<Channel>() {...`
最后一步就是想 ServerChannel 加入了一个 ServerBootstrapAcceptor 。这是一个接入器，专门用于接受请求，把新的请求扔给某个 EventLoop。

### 将这个 channe 注册到某个对象

先看方法调用：

```java
ChannelFuture regFuture = config().group().(SingleThreadEventLoop)register(channel);

public abstract class SingleThreadEventLoop extends SingleThreadEventExecutor implements EventLoop {
    @Override
    public ChannelFuture register(final ChannelPromise promise) {
        ObjectUtil.checkNotNull(promise, "promise");
        promise.channel().unsafe().register(this, promise);
        return promise;
    }
}

    protected abstract class AbstractUnsafe implements Unsafe {
    	....
        @Override
        public final void register(EventLoop eventLoop, final ChannelPromise promise) {
        	register0(promise);
        }    	
    }


        private void register0(ChannelPromise promise) {
            try {
				...
                boolean firstRegistration = neverRegistered;
                doRegister();
                neverRegistered = false;
                registered = true;
                pipeline.invokeHandlerAddedIfNeeded();

                safeSetSuccess(promise);
                pipeline.fireChannelRegistered();
                if (isActive()) {
                    if (firstRegistration) {
                        pipeline.fireChannelActive();
                    } else if (config().isAutoRead()) {
                        beginRead();
                    }
                }
            } catch (Throwable t) {
                closeForcibly();
                closeFuture.setClosed();
                safeSetFailure(promise, t);
            }
        }    

```

`doRegister` 的操作：将 Channel 在 EventLoop 注册后作为注册过程的一部分被调用。
invokeHandlerAddedIfNeeded 就是调用其 pipeline 中注册的一个 ChannelHandler 的 handlerAdded 方法，fireChannelRegistered 的作用就是调用其 channelRegistered 方法。
然后继续调用 isActive 方法，isActive 方法中的逻辑：

```java
public class NioServerSocketChannel extends AbstractNioMessageChannel
                             implements io.netty.channel.socket.ServerSocketChannel {
    @Override
    public boolean isActive() {
        return isOpen() && javaChannel().socket().isBound();
    }
}
    public boolean isBound() {
        return bound || oldImpl;
    }    
```

这里的 isBound 被调用的时候我们考虑下 bound 是在哪做的操作呢，让我们回到最初的方法。当用户使用 bind 方法的里面会有一个 doBound0 方法。

```java

public abstract class AbstractBootstrap<B extends AbstractBootstrap<B, C>, C extends Channel> implements Cloneable {
    private static void doBind0(
            final ChannelFuture regFuture, final Channel channel,
            final SocketAddress localAddress, final ChannelPromise promise) {
        channel.eventLoop().execute(new Runnable() {
            @Override
            public void run() {
                if (regFuture.isSuccess()) {
                    channel.bind(localAddress, promise).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
                } else {
                    promise.setFailure(regFuture.cause());
                }
            }
        });
    }
}    
```
在触发 channelRegistered 方法之前会调用这个方法，使得用户有机会在 channelRegistered 实现中设置管道。我们点击 bind 方法往下跟踪代码，经过层层调用最总讲来到 `AbstractChannel` 的 doBind 方法，这里的 doBind 实现将会很多种不同的实现。我们可以进入最常用的 
```java
public class NioServerSocketChannel extends AbstractNioMessageChannel
                             implements io.netty.channel.socket.ServerSocketChannel {
    @SuppressJava6Requirement(reason = "Usage guarded by java version check")
    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        if (PlatformDependent.javaVersion() >= 7) {
            javaChannel().bind(localAddress, config.getBacklog());
        } else {
            javaChannel().socket().bind(localAddress, config.getBacklog());
        }
    }
}    
```
最后还是调用了 java 的 Socket 实现的 bind 方法，进行了真正的绑定。

这里我们还有一个感兴趣的事件就是当 Bound 激活以后再 `AbstractChannel` 的 `beginRead` 事件，官方注释：当 Channel 已经注册且 autoRead 已经设置，那么就可以开始读取，处理入站数据。问题来了，那 autoRead 在哪设置的，`beginRead` 有哪些操作。

#### autoRead 设置

跟踪 isAutoRead 方法最终将会看到 `DefaultChannelConfig` 类种的实现： `autoRead = 1` 在默认的情况在就是为 1 ，所以主要的还是看 isBound ，什么情况下不为 `1` 可以在 ServerBootstrapAdapter 看出，当有异常信息时会设置为 0 ，并且 Delay 1秒后重新设置为 `1` 官方解释，为了防止打开文件描述过多导致的死循环。
也就是在主线程抛出异常的情况下，进行一个捕获然后重新启动程序
```java

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            final ChannelConfig config = ctx.channel().config();
            if (config.isAutoRead()) {
                // stop accept new connections for 1 second to allow the channel to recover
                // See https://github.com/netty/netty/issues/1328
                config.setAutoRead(false);
                ctx.channel().eventLoop().schedule(enableAutoReadTask, 1, TimeUnit.SECONDS);
            }
            // still let the exceptionCaught event flow through the pipeline to give the user
            // a chance to do something with it
            ctx.fireExceptionCaught(cause);
        }
```

#### beginRead 操作

```java
public abstract class AbstractNioChannel extends AbstractChannel {
    @Override
    protected void doRegister() throws Exception {
        boolean selected = false;
        for (;;) {
            try {
                selectionKey = javaChannel().register(eventLoop().unwrappedSelector(), 0, this);
                return;
            } catch (CancelledKeyException e) {
            	...
            }
        }
    }

    @Override
    protected void doBeginRead() throws Exception {
        final SelectionKey selectionKey = this.selectionKey;
        if (!selectionKey.isValid()) {
            return;
        }

        readPending = true;

        final int interestOps = selectionKey.interestOps();
        if ((interestOps & readInterestOp) == 0) {
            selectionKey.interestOps(interestOps | readInterestOp);
        }
    }    
}    
``` 

这里的this.selectionKey就是我们在前面register步骤返回的对象，前面我们在register的时候，注册测ops是0。`readInterestOp` 就是前面 newChannel 的时候传入的 SelectionKey.OP_ACCEPT 。

所以基本数 Netty Server 启动的时候流程分为几大块：
1. 设置启动类参数，最重要的就是设置 Channel
2. 创建 Server 对应的 Channel，创建各大组件
3. 配置 option attr，register 
4. 调用 java 底层 socket 绑定事件





## 参考文档
[Netty 实战](https://jeff-duan.github.io/downloads/resource/netty/Netty%20in%20Action-%E4%B8%AD%E6%96%87%E7%89%88.pdf)
[源码之下无秘密](https://segmentfault.com/a/1190000007282628)
[Related articles](https://netty.io/wiki/related-articles.html)