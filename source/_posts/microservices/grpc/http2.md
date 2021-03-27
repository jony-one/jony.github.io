---
title: http2 RFC 阅读
date: 2021-03-20 20:11:59
categories: 
	- [gRPC]
tags:
  - microservices
  - grpc
  - http2
  - h2c
author: Jony
---

# HTTP RFC 文档阅读


## 术语

1. 发起连接请求：主要讨论 HTTP2 如何初始化的
1. 帧与流(stream)：主要讨论 HTTP2 帧的结构以及如何组成多路复用的流
1. 帧与错误定义：主要讨论 HTTP2 帧的细节和错误类型
1. HTTP 映射：主要讨论如何使用 帧 和 流来表示 HTTP 语义

1. 客户端(client):即发起HTTP/2连接的端点(endpoint). 客户端发出HTTP请求，并接收HTTP应答。
1. 连接(connection): 两个端点之间的传输层连接
1. 连接错误(connection error):影响HTTP/2连接的错误
1. 端点(endpoint): 连接的客户端或服务端
1. 帧(frame): HTTP/2连接中的最小通讯单元，由帧头与任意长度的字节流组成，字节流的具体结构由帧类型决定。
1. 节点(peer): 一类特殊的端点，主要用来代指在讨论中与主要端点对应的远端端点
1. 接收端: 接收帧的端点
1. 发送端: 发送帧的端点
1. 服务器: 接收HTTP/2连接的端点。服务器接收HTTP请求，并发送HTTP应答
1. 流: HTTP/2中的双向帧传输流
1. 流错误:发生在单独的HTTP/2流中的错误


## H2C

### 建立连接


1. 客户端/服务端必须向服务端先发送一个连接 preface，然后可以立即发送 HTTP2 帧
	- 连接 preface 中可以选择包含 SETTINGS 帧
	- 连接 preface 的第一个帧必须是 SETTINGS

### 交换 HTTP 帧

帧格式如下：
+-----------------------------------------------+
|                 Length (24)                   |
+---------------+---------------+---------------+
|   Type (8)    |   Flags (8)   |
+-+-------------+---------------+-------------------------------+
|R|                 Stream Identifier (31)                      |
+=+=============================================================+
|                   Frame Payload (0...)                      ...
+---------------------------------------------------------------+

所有帧必须以 9 字节的报文头开始：

- Length：载荷长度，无符号类型。***不包含报文头***
- Type：帧类型
- Flag：为 Type 保留的 bool 标识
- R：1 位的保留字段，无实意义
- Stream Identifier：无符号整型的流标示符

### Header 压缩和解压

- 允许一键多值

#### 压缩

传输过程：先将报文头列表转化为一个区块，然后将区块分割成一个或多个序列，即区块分片。将分片作为 HEADERS 帧、PUSH_PROMISE 帧、CONTINUATION 帧

Cookie 帧通过 HTTP mapping 特殊处理

#### 解压缩与重组

报文接收端将分片拼接起来以重组报头区块, 然后解压区块得到原始的报头列表.
一个完整地报头区块可以由下面任意一种结构组成:

- 一个设置了 END_HEADERS 标记的 HEADERS 或 PUSH_PROMISE 帧.
- 一个 END_HEADERS 标记置空的 HEADERS 或 PUSH_PROMISE 帧, 后接一个或多个 CONTINUATION 帧, 并且最后一个 CONTINUATION 帧 END_HEADERS 标记.


### 流与多路复用

流的生命周期：
                         +--------+
                 send PP |        | recv PP
                ,--------|  idle  |--------.
               /         |        |         \
              v          +--------+          v
       +----------+          |           +----------+
       |          |          | send H /  |          |
,------| reserved |          | recv H    | reserved |------.
|      | (local)  |          |           | (remote) |      |
|      +----------+          v           +----------+      |
|          |             +--------+             |          |
|          |     recv ES |        | send ES     |          |
|   send H |     ,-------|  open  |-------.     | recv H   |
|          |    /        |        |        \    |          |
|          v   v         +--------+         v   v          |
|      +----------+          |           +----------+      |
|      |   half   |          |           |   half   |      |
|      |  closed  |          | send R /  |  closed  |      |
|      | (remote) |          | recv R    | (local)  |      |
|      +----------+          |           +----------+      |
|           |                |                 |           |
|           | send ES /      |       recv ES / |           |
|           | send R /       v        send R / |           |
|           | recv R     +--------+   recv R   |           |
| send R /  `----------->|        |<-----------'  send R / |
| recv R                 | closed |               recv R   |
`----------------------->|        |<----------------------'
                         +--------+

   send:   发送这个frame的终端
   recv:   接受这个frame的终端

   H:  HEADERS帧 (隐含CONTINUATION帧)
   PP: PUSH_PROMISE帧 (隐含CONTINUATION帧)
   ES: END_STREAM标记
   R:  RST_STREAM帧

流是服务器与客户端之间用于帧交换的一个独立双向序列

- 一个HTTP/2连接可以包含多个并发的流, 各个端点从多个流中交换frame
- 流可以被客户端或服务器单方面建立、使用或共享
- 流也可以被任意一方关闭，可以处于半关闭
- rames在一个流上的**发送顺序**很重要. 接收方将按照他们的接收顺序处理这些frame. 特别是 HEADERS 和 DATA frame 的顺序, 在协议的语义上显得尤为重要.
- 流用一个整数(流标识符)标记. 端点初始化流的时候就为其分配了标识符.


```
[:authority: 127.0.0.1:8080,
 :path: /HelloService/sayFuchGrp,
 :method: POST,
 :scheme: http,
 content-type: application/grpc,
 te: trailers,
 user-agent: grpc-java-netty/1.36.0-SNAPSHOT,
 grpc-accept-encoding: gzip]
```









# 参考文档

[Hypertext Transfer Protocol Version 2 (HTTP/2)](https://tools.ietf.org/html/rfc7540)
[rfc7540-translation-zh_cn](https://github.com/abbshr/rfc7540-translation-zh_cn)
[超文本传输协议版本 2 ](https://github.com/fex-team/http2-spec/blob/master/HTTP2%E4%B8%AD%E8%8B%B1%E5%AF%B9%E7%85%A7%E7%89%88(06-29).md)
[解开 HTTP/2 的面纱：HTTP/2 是如何建立连接的](https://halfrost.com/http2_begin/)
[HTTP/2 简介](https://developers.google.com/web/fundamentals/performance/http2?hl=zh-cn)
[http2-spec](https://github.com/httpwg/http2-spec/wiki/Implementations)