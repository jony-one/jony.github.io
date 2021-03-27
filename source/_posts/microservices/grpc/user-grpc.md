---
title: gRPC 学习 - 简单使用
date: 2021-03-10 10:36:01
categories: 
	- [gRPC]
tags:
  - microservices
author: Jony
---



## gRPC 简单使用 

### Protobuf

Protobuf 是谷歌推出的一宗轻便高效的结构和的数据存储格式，把结构和的数据序列化。常用以存储数据、作为网络通信的数据载体。具有多种编程语言的 API、跨平台和可扩展的特性。比 JSON 、XML 更小解析速度更快、更易于上手。

### 命令使用 

写一个 Proto 文件
```proto
syntax = "proto3";

option java_package = "com.example.grpc";
option java_multiple_files = true;
option java_outer_classname = "HelloWorldProto";

message Greeting {
    string name = 1;
}

message HelloResp {
    string reply = 1;
}

service HelloWorld {
    rpc sayFuchGrpc (Greeting) returns (HelloResp);
}
```

生成 protobuf 类，但是并没有生成 RPC 调用关系，所以还需要继续使用一个命令

`protoc --java_out=./ --proto_path=./ helloworld.proto`

生成 RPC 类，需要使用 [protoc-gen-grpc-java](https://repo1.maven.org/maven2/io/grpc/protoc-gen-grpc-java/) ，可以下载适合自己得版本，这里使用得是：`protoc-gen-grpc-java-1.36.0-windows-x86_64.exe`
使用命令：

`protoc --plugin=protoc-gen-grpc-java="D:/Software/protobuf-3.12.4/protoc-gen-grpc-java-1.36.0-windows-x86_64.exe" --grpc-java_out=./src hello_world.proto`

将生成：*HelloWorldGrpc.java* 文件

服务端编写代码：
```java
import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.io.IOException;

public class GreeterServer {

    public static void main(String[] args) {
        int port = 50051;
        Server server = null;
        try {
            server = ServerBuilder.forPort(port)
                    .addService(new GreeterImpl())
                    .build()
                    .start();
            server.awaitTermination();
        } catch (IOException e) {
            e.printStackTrace();
            server.shutdown();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            System.out.println("server shutdown");
        }
    }
}

```

客户端编写代码:

```java
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

public class GreeterClient {
    public static void main(String[] args) {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("127.0.0.1",50051).usePlaintext().build();
        HelloServiceGrpc.HelloServiceBlockingStub stub  = HelloServiceGrpc.newBlockingStub(channel);
        HelloResponse response = stub.sayFuchGrp(HelloRequest.newBuilder().setName("123456 up shan play triger").build());
        System.out.println(response.toString());
        channel.shutdown();
    }
}
```

成功输出：
	reply: "123456 up shan play triger:hahahhhaa"


## 原理剖析

Client 与 Server ，均通过 Netty Channel 作为数据通信，序列化、反序列化使用 Protobuf，每个请求都将被封装成 HTTP2 得 Stream，在生命周期内，Channel 得应用应该属于长连接，并不需要重复去创建，直至关闭 Channel。目的就是为了**连接复用**提高效率。

*默认情况下 ServerProvider 使用 Netty 作为服务提供者,即使用 NettyServerBuilder*

跟踪代码将会看到 `io.grpc.netty.NettyServer` 

1. 创建 `ServerBootstrap` ,设定 `BossGroup` 和 `WorkerGroup` 线程池
1. 添加 `ChannelFactory` ,使用的是 `new ReflectiveChannelFactory<>(epollServerChannelType())`
1. 注册 `childHandler` ，用来处理客户端连接中的请求帧
1. bind 到指定的 `port` ，即内部初始化 `ServerSocketChannel` 等，开始侦听和接受客户端连接
1. `BossGroup` 中的线程用于 accept 客户端连接，并转发给 `WorkerGroup` 中的线程，如果没有指定就会默认 static 共享对下，线程池大小默认为 1
1. `workerGroup` 中的特定线程用于初始化客户端连接，初始化 `pipeline` 和 `handler` ，并将其注册到 `worker` 线程的 `selector` 上。每个 `worker` 线程独占一个 `selector`。默认大小 coreSize*2
1. `selector` 上发送读写时间后，获取时间所属的连接句柄，然后指定 `handler（inbound）` ，同时拆分 `package` 。`handler` 执行完毕后，数据写入有 `outbound handler` 处理通过连接发出。
1. `channelType`：默认为 `NioServerSocketChannel`。
1. `followControlWindow`：流量控制的窗口大小，单位：字节，默认值为 1M HTTP2 中的 `Flow Control` 特性
1. `maxConcurrentCallPerConnection`：每个 connection 允许的最大并发请求书，默认值为 Integer.MAX_VALUE；标识此连接的未响应的 streams 个数的上限
1. `maxMessageSize` ：每次调用允许发送的最大数据量，默认为 100M
1. `maxHeaderListSize`：每次调用允许发送的 header 的最大条数据，默认 8192
1. `protocolNegotiator`：协议协商工具类，用于支持 SSL、TLS 工具类。默认情况下：PlaintextProtocolNegotiator 使用的仍然是 HTTP 协议


***注意：每个 `worker` 线程上的数据请求时是队列形式***
***Channel 是 NIO 的基本构造，代表的一个实体，可以看作传入或者传出的数据载体。因此可以被打开或者关闭、连接或者断开***


## addService 的作用

addService 使用的是代理模式，每个 `gRPC` 生成的 Service 都有 `bindService` 方法。`gRPC`  通过硬编码的方式遍历这个 `service` 列表，将每个方法的调用过程都与`“被代理实例”`绑定。`bingService` 方法的最终目的是创建一个 `ServerServiceDefinition` 对象，内部创建了一个 `Map` ，`KEY` 就是 `Service`  方法的全名 `{package}.{service}.{method}`，value 就是这个方法的 gRPC 封装类。
``` java
...
    private final HelloServiceImplBase serviceImpl;
    private final int methodId;

    MethodHandlers(HelloServiceImplBase serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }
...
    @Override
    @SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_SAY_FUCH_GRP:
          serviceImpl.sayFuchGrp((HelloRequest) request,
              (io.grpc.stub.StreamObserver<HelloResponse>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }
    ...

    @Override public final io.grpc.ServerServiceDefinition bindService() {
      return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
          .addMethod(
            getSayFuchGrpMethod(),
            io.grpc.stub.ServerCalls.asyncUnaryCall(
              new MethodHandlers<
                HelloRequest,
                HelloResponse>(
                  this, METHODID_SAY_FUCH_GRP)))
          .build();
    }
```    

`addService` 方法可以添加多个 `Service`，代表 Netty Server 可以添加多个 `Service`。`addService` 会把 service 保存在内部的一个 map 中，key 为 serviceName ，value 就是上述 bindService 生成的对象。

既然对象都已经生成存储到 Map 中，那么 `gRPC` 服务端是如何解析 RPC 过程的？client 在调用的收会将调用的 `service` 名词 + `method` 信息保存在一个 `GRPC 保留` 的 header 中，那么服务端在接收到 stream （PS：h2c 中每个 stream 相当于一个请求）之后，通过获取这个特定 Header 信息，就可以知道这个 stream 要调用的相应的方法，然后就使用到来上诉的 service ，然后找个相应的方法，直接代理调用。然后返回执行结果。

## Client 使用

Client 使用 `ManagedChannelBuilder` 的 `provider`机制来决定创建哪种类型的客户端的 `Channel`（`NettyChannelBuilder` 和 `OkHttpChannelBuilder`）。

通常情况下 Channel 是可以复用的，所以为了提高 Client 端的并发能力，我们可以创建连接池即多个 `ManagedChannel`，每次请求时选择其中一个 Channel 即可。

`ManagedChannel` 是客户端最核心的 class，代表这逻辑上的 `channel`，底层持有一个物理的 `transport`，并负责维护当检测到其处于 `terminated` 的时候会重新创建。

每个 Client SDK 都生成了 2 中 stub：BlockingStub 和 FutureStub； BlockingStub 内部仍然使用的是 Future 机制，只是内部封装了**阻塞等待**的过程：

```java
public static <ReqT, RespT> RespT blockingUnaryCall(
      Channel channel, MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, ReqT req) {
    ThreadlessExecutor executor = new ThreadlessExecutor();
    boolean interrupt = false;
    ClientCall<ReqT, RespT> call = channel.newCall(method,
        callOptions.withOption(ClientCalls.STUB_TYPE_OPTION, StubType.BLOCKING)
            .withExecutor(executor));
    try {
      ListenableFuture<RespT> responseFuture = futureUnaryCall(call, req);
		...
      return getUnchecked(responseFuture);
  }


  private static <V> V getUnchecked(Future<V> future) {
    try {
      return future.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw Status.CANCELLED
          .withDescription("Thread interrupted")
          .withCause(e)
          .asRuntimeException();
    } catch (ExecutionException e) {
      throw toStatusRuntimeException(e.getCause());
    }
  }
```

目前每次发起请求时都是通过 `channel` 创建新的 `Stub`，这会导致创建大量的 `Stub` 对象，当然 `Stub` 对象也是可以复用的，直到 Stub 状态异常。每个 Stub 都配置 deadline 时间，那么如果此 Stub 被使用的时长超过这个阈值，将会抛出 ***io.grpc.StatusRuntimeException: DEADLINE_EXCEEDED*** 异常。

```java
  public static io.grpc.Channel resetChannel(io.grpc.ManagedChannel channel){
    if (channel.isShutdown() || channel.isTerminated()){
      return ManagedChannelBuilder.forAddress(HOST, PORT).usePlaintext(true).build();
    }
    return channel;
  }

    public static void main(String[] args) {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("127.0.0.1",50051).usePlaintext().build();
        resetChannel(channel)
        HelloServiceGrpc.HelloServiceBlockingStub stub  = HelloServiceGrpc.newBlockingStub(channel);
        HelloResponse response = stub.sayFuchGrp(HelloRequest.newBuilder().setName("123456 up shan play triger").build());
        System.out.println(response.toString());
        channel.shutdown();
    }
```

对于批量调用的场景，建议使用 FutureStub，对于普通的业务类型 RPC，我们使用 BlockingStub。每个 RPC 方法的调用一开始都会创建一个 **ClientCall**，其内部封装了调用的方法、配置等。此后将会创建 Stream 对象，每个 Stream 对象都持有唯一的 streamId，它是 Transport 用于区分 Response 的凭证。最终所有调用的参数都封装在 Stream 中。在大多数的 RPC 调用中，请求参数报文都是分多次发送，所以 ClientCall 在创建时就已经绑定线程，所以数据发送总是哦他难过一个线程进行的所以不会有乱序的现象。同样在等待 Response 时，底层的 Netty 将会对 Response 报文进行解包，并根据 streamId **分拣** Response，同时唤醒相应的 ClientCalls 阻塞。

**注意：如果出现网络异常将会重置 Channel、StreamId**






# 参考文档
[gRPC 是什么](http://www.iigrowing.cn/grpc_shi_shen_me.html)
[HTTP/2 协议规范](https://blog.csdn.net/u010129119/article/details/79361949#1-%E7%AE%80%E4%BB%8B)
[HTTP2 详解](https://www.jianshu.com/p/e57ca4fec26f)
[永顺 专栏](https://segmentfault.com/u/yongshun/articles)


 

