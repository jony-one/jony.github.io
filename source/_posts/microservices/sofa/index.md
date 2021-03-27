---
title: SOFA RPC 深入学习 1
date: 2021-03-24 21:33:01
categories: 
	- [微服务]
tags:
  - microservices
  - 微服务
  - 无服务
  - sofa
author: Jony
---


# 由浅入深的体验和学习

## 简单介绍


SOFARPC 是蚂蚁金服开源的一款基于 Java 实现的 RPC 服务框架，为应用之间提供远程服务调用能。高可用、容错性强。提供负载均衡、流量转发、链路追踪、链路数据透传，故障剔除等功能。
还支持不同的协议，目前包括：bolt、RESTful、dubbo、H2C 协议通信。

## 基本架构

![SOFA 架构](/jony-one.github.io/images/sofa/overview.png)

1. Service 的应用程序启动的时候，应当向注册中心注册 RPC 服务
2. Reference 启动时向注册中心请求元数据，并订阅元数据推送
3. invoke 当 Reference 获取到元数据后可以向 Service 发起调用

所以我们这里需要三大组件：Reference（客户端）、Service（服务端）、Registry（注册中心）











