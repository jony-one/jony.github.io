---
title: Cilium 功能阅读：Code 概览
date: 2021-06-14 19:44:19
categories: 
	- [eBPF]
tags:
  - ebpf
  - cilium
author: Jony
---



# Cilium 源码阅读： 功能解析

## High-level

**api**
  Cilium & Hubble API 定义
**bpf**
  BPF数据路径代码
**bpftool**
  命令行收集代理和系统信息，用于bug报告
**cilium**
  Cilium CLI客户端
**contrib, tools**
  用于开发的其他工具和资源
**daemon**
  在每个节点上运行的cilium代理
**examples**
  各种示例资源和清单。通常需要修改才能使用。
**hubble-relay**
  哈勃中继服务器
**install**
  Helm 所有组件的部署清单  
**pkg**
  所有组件之间共享的通用Go包
**operator**
  负责集中任务的操作员，这些任务不需要在每个节点上执行。
**plugins**
  与Kubernetes和Docker集成的插件
**test**
  端到端集成测试在端到端测试框架中运行。  


## Cilium

**api/v1/openapi.yaml**
  Cilium的API规范。用于代码生成。
**api/v1/models/**
  从代表所有API资源的openapi.yaml生成的Go代码
**bpf**
  BPF数据路径代码  
**cilium**
  Cilium CLI客户端
**cilium-health**
  Cilium集群连接的CLI客户端
**daemon**
  cilium-agent的具体代码    
**plugins/cilium-cni**
  与Kubernetes集成的CNI插件
**plugins/cilium-docker**
  Docker集成插件

# 重要的通用包  

**pkg/allocator**
  安全身份分配
**pkg/bpf**
  抽象层，与BPF运行时进行交互  
**pkg/client**
  访问Cilium API的Go客户端
**pkg/clustermesh**
  多集群实现包括控制平面和全局服务
**pkg/controller**
  任何需要重试或基于间隔的调用的后台操作的基本控制器实现。
**pkg/datapath**
  用于数据通路交互的抽象层
**pkg/default**
  所有默认值
**pkg/elf**
  用于BPF加载器的ELF抽象库
**pkg/endpoint**
  对Cilium endpoint 的抽象，代表所有的工作负载。
**pkg/endpointmanager**
  管理所有 Endpoint
**pkg/envoy**
  Envoy 集成代理
**pkg/fqdn**
  FQDN 代理 和 FQDN 策略实现
**pkg/health**
  网络连接健康检查
**pkg/identity**
    代表工作负载的安全身份
**pkg/ipam**
  IP 地址管理
**pkg/ipcache**
  全局缓存将IP映射到端点和安全标识
**pkg/k8s**
  与Kubernetes的所有交互
**pkg/kafka**
  Kafka协议代理和策略实现
**pkg/kvstore**
  带etcd和consul后端的键值存储抽象层
**pkg/labels**
  基本元数据类型，用于描述工作负载标识规范和策略匹配的所有标签/元数据要求。
**pkg/loadbalancer**
  用于负载平衡功能的控制平面
**pkg/maps**
  BPF map 表述
**pkg/metrics**
  Prometheus 指标实现
**pkg/monitor**
  BPF数据路径监视抽象
**pkg/node**
  网络节点的表示
**pkg/option**
  所有可用配置选项
**pkg/policy**
  策略实施规范与实施
**pkg/proxy**
  第7层代理抽象
**pkg/service**
  负载均衡 service 的表示
**pkg/trigger**
  实现触发器功能以实现事件驱动功能








  







