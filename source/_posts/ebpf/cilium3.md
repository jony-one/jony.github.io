---
title: Cilium 解析阅读：如何制定和执行 L3 策略
date: 2021-05-20 19:44:19
categories: 
	- [eBPF]
tags:
  - ebpf
  - cilium
author: Jony
---



# Cilium 源码阅读：如何制定和执行 L3 策略

原文：[http://arthurchiao.art/blog/cilium-code-agent-start/#0-overview](http://arthurchiao.art/blog/cilium-code-agent-start/#0-overview)


**我认为理解 eBPF 代码还比较简单，多看看内核代码就行了，但配置和编写 eBPF 就要难多了。**

Cilium 提供了 CNI 和 kube-proxy replacement 功能，相比 iptable 性能要好很多当前代码复杂度也上升了。

本文做了一个官方 Demo  顺便解析一下代码以把整个过程透明化：

Demo 参考：[https://docs.cilium.io/en/v1.8/gettingstarted/docker/](https://docs.cilium.io/en/v1.8/gettingstarted/docker/)

看下 `cilium policy import l3_l4_policy.json` 得处理流程：


命令会触发 CMD 得 import 方法最终会生成新的请求发送到 cilium_agent，调用链如下：
```bash
cmd.loadPolicy   # cilium/cmd/policy_import.go
client.PolicyPut 
    c.Policy.PutPolicy
        a.transport.Submit
```

经过上面的调用链就会将请求通过 unix 协议发送出去。