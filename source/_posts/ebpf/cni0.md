---
title: CNI 理解
date: 2021-06-03 10:57:19
categories: 
	- [eBPF]
tags:
  - ebpf
  - CNI
author: Jony
---


# CNI

CNI Container Network Interface 的缩写。就是标准的、通用接口。应用于 Kubernetes、docker、contained。还有其他网络解决方案。

CNI 的主要工作是从容器管理系统处获取运行信息。包括 network namespace的路径、容器 ID 和 network interface name。再从容器网络的配置文件中加载网络配置信息，再将这些信息传递给对应的插件。由插件进行具体的网络配置工作。

上面的描述总结来说 CNI 用于管理网络插口类似于网管的工作。但是不会介入通信管理。不支持通信。抽象来说像给容器安装网络插口、插上网线、分配IP。