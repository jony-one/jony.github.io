---
title: gRPC 学习 - HTTP2
date: 2021-03-08 13:16:51
categories: 
	- [gRPC]
tags:
  - microservices
author: Jony
---


# gRPC 学习

## 名词解释
- RPC: `Remote procedure call` 直译就是**远程过程调用**。基本流程如下：
![RPC](/jony.github.io/images/grpc/rpc-process.png)

## 简介

RPC 协议分为两类：
- 通讯处协议，一般和业务无关。职责就是将业务数据打包发送。例如：HSF、Dubbo、gRPC 等
- 应用层协议。约定业务数据和二进制串的转换规则。例如：Hessian、Protobuf、JSON

	HTTP 调用实际上依然是 RPC。

gRPC 是 google 开源的高性能跨语言的 RPC 方案，并且采用的是 HTTP2 作为通信层协议。gRPC 的设计目标是在任何环境下运行，默认使用 protobuf 作为接口描述语言（IDL interface description language）及底层消息通信格式

支持可插拔的负载均衡、跟踪、运行状况检查和身份验证。

它不仅支持数据中心内部和跨数据中心的服务调用，它也适合于分布式计算的最后一公里，将设备、移动应用和浏览器连接到后端服务。

### Protobuf 

Protobuf 是谷歌推出的一宗轻便高效的结构和的数据存储格式，把结构和的数据序列化。常用以存储数据、作为网络通信的数据载体。具有多种编程语言的 API、跨平台和可扩展的特性。比 JSON 、XML 更小解析速度更快、更易于上手。

gRPC 应该是基于 HTTP2 通信协议 + Protobuf 序列化工具的组合

只是对 Netty 基于 HTTP2 的应用，具体的 Framer 、Stream 参考 HTTP2 相关文档即可。

## HTTP 1.1 与 HTTP2

### HTTP 1.1 存在的问题

1. 线头阻塞：每个  TPC 连接同时只能处理一个请求 - 响应。如果上一个响应没有完成，后续的请求 - 响应都会受阻。
1. Header 内容多，每次请求 Header 内容都不会变化，但是每次必传
1. 明文传输


### HTTP2 优势

1. 二进制分帧传输，帧时数据传输的最小单位，以二进制传输代替原本的明文传输。
2. 多路复用：每个请求当作一个流，多个请求就是多个流，请求响应数据分成多个帧，不同流的帧交错地发送给对方，这就是 HTTP2 的多路复用

*TCP 连接上向对方不断发送帧，每帧的 stream identifier 标明这一帧属于哪个流，然后对方更加 stream identifier 拼接每个流所组成一整块数据。*

所以 HTTP2 对于同一个域名只需要创建一个连接，而不是多个连接。也就是说不在需要连接池。因为流的概念实现了单链接上多i请求 - 响应并行，解决了线头阻塞的问题，减少了TCP 连接梳理和TCP连接慢启动造成的问题。


### 帧格式

所有帧都是一个固定的 9 字节头部（payload）跟一个指定长度的负载（payload）：
	+-----------------------------------------------+
	|                 Length (24)                   |
	+---------------+---------------+---------------+
	|   Type (8)    |   Flags (8)   |
	+-+-------------+---------------+-------------------------------+
	|R|                 Stream Identifier (31)                      |
	+=+=============================================================+
	|                   Frame Payload (0...)                      ...
	+---------------------------------------------------------------+

- Lenght：frame 的长度，用 24 位无符号整数标识
- Type ： Frame 的类型，用 8 bits 标识。帧类型决定了帧主体的格式和语义
- Flags： 帧类型相关而预留的不二标识。标识对于不同的帧类型赋予了不同的语义
- R：保留的比特位。无实际意义
- Stream Identifier 用作流控制，用 31 位无符号整数标识。客户端建立的 SID 必须为奇数，服务端建立的 SID 必须为偶数
- Frame Payload 是主体内容，由帧类型决定
	- HEADERS：报头帧（type=0x1）
	- DATA：数据帧（type = 0x0）
	- PRIORITY：优先级帧（type = 0x2）
	- RST_STREAM:流终止帧（type=0x3）
	- SETTINGS:设置帧（type=0x4）
	- PUSH_PROMISE：推送帧（type=0x5）
	- PING：PING 帧（typ=0x6）
	- GOAWAY：GOWAY帧（type=0x7）
	- WINDOW_UPDATE：窗口更新帧（type=0x8）
	- CONTINUATION:延续帧（type=0x9）

# 参考文档
[gRPC 是什么](http://www.iigrowing.cn/grpc_shi_shen_me.html)
[HTTP/2 协议规范](https://blog.csdn.net/u010129119/article/details/79361949#1-%E7%AE%80%E4%BB%8B)
[HTTP2 详解](https://www.jianshu.com/p/e57ca4fec26f)
[永顺 专栏](https://segmentfault.com/u/yongshun/articles)