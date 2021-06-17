---
title: Cilium 源码阅读：如何制定和执行 L3 策略 续续
date: 2021-06-14 19:44:19
categories: 
	- [eBPF]
tags:
  - ebpf
  - cilium
author: Jony
---



# Cilium 源码阅读：如何制定和执行 L3 策略


## macVlan

所有的资料显示 ipvlan 都会与 macvlan 放在一起比较研究。也就是表示 macvlan 出生早于 ipvlan ，那么 ipvlan 应该弥补了很多 macvlan 的不足和不好解决的问题。

macvlan 就是允许在一个物理网卡上配置多个 mac 地址，这个有点反标准，一般一个网卡只有一个 mac 地址
而且是全球唯一的。但是现在虚拟机技术越来越牛逼，所以一些原始的标准只能作为部分标准择优而选。多个 macvlan 就是多个 interface 所以前面那么多说的接口都是网卡的意思。每个 interface 都有可以自己的 IP。

macvlan 最大的有点就是性能好，效率高，但是这里思考到一个问题就是 BGP？多网口肯定需要 BGP 的支持。

macvlan 可以在一个 host 的网络接口上虚拟出多个网络接口也可以有自己的 MAC 和 IP 地址

macvlan 模式

- 可以在一个实体网卡上设定多个 mac 地址
- 设定的 mac 地址称为子接口（`sub interface`）；实体网卡称为父接口（`parent interface`）
- `parent interface` 可以是一个物理接口 （eth0）、802.1q、eth0.10、bonding 接口
- 所有 interface 都可以设定 IP
- `sub interface` 无法直接与 `parent interface` 通信
- vm 或者 container 要与 host 通信，还要额外建一个 sub interface 给 host
- sub interface 通常以 mac0@eth0 的形式来命名

MACVlan 工作模式
- Bridge： 属于同一个 parent interface 的 macvlan 接口挂到同一个 bridge 上，可以互通（二层工作）
- VPEA（Virtual Ethernet Port Aggregator）：所有接口的流量都需要到外部交换器走一圈才能到达其他接口（交换机）
- Private：接口只接收发送给自己 MAC 地址的报文 （交换机）
- Passthru：父接口和响应的 MacVlan 接口捆绑在一起，这种模式每个父接口只能和一个 MacVlan 接口捆绑。并且 MacVlan 虚拟网卡接口基础 父接口的 Mac 地址。


## ipvlan 

ipvlan 也是从一个主机接口虚拟出多个虚拟网络接口。一个重要的区别就是所有的虚拟接口都有相同的 macv 地址，而拥有不同的 ip 地址。

ipvlan 工作模式

L2：与 macvlan 的 bridge 模式工作原理很类似，父接口作为交换机来转发子接口的数据。同一个网路的子接口可以通过父接口来转
发数据。（理解：桥接模式需要中转站，这个站点就是宿主机本身，有网络划分。如果流量要出去也只能走中转站）
L3：Ipvlan 类似路由器功能，在各个虚拟网络和主机网络之间进行不同网络报文的路由转发工作。只要父接口相同，及时各个容器或者
虚拟机不在同一个网络，也可以互相 ping 通对方。因为 ipvlan 可以在中间做转发。（理解：同上但是无网络划分）

同理：需要了解 IProuter2 ，读懂了应该会系统的了解整个虚拟网络。

所以 cilium_net、cilium_host、cilium_vxlan 


```bash
$ export V=lxc807633bfedea &&  tc filter show dev $V ingress && echo "========" &&tc filter show dev $V egress 
filter protocol all pref 1 bpf 
filter protocol all pref 1 bpf handle 0x1 bpf_lxc.o:[from-container] direct-action 
========

$ export V=lxc_health &&  tc filter show dev $V ingress && echo "========" &&tc filter show dev $V egress 
filter protocol all pref 1 bpf 
filter protocol all pref 1 bpf handle 0x1 bpf_lxc.o:[from-container] direct-action 
========

$ export V=cilium_vxlan &&  tc filter show dev $V ingress && echo "========" &&tc filter show dev $V egress 
filter protocol all pref 1 bpf 
filter protocol all pref 1 bpf handle 0x1 bpf_overlay.o:[from-overlay] direct-action 
========
filter protocol all pref 1 bpf 
filter protocol all pref 1 bpf handle 0x1 bpf_overlay.o:[to-overlay] direct-action 

$ export V=cilium_host &&  tc filter show dev $V ingress && echo "========" &&tc filter show dev $V egress 
filter protocol all pref 1 bpf 
filter protocol all pref 1 bpf handle 0x1 bpf_host.o:[to-host] direct-action 
========
filter protocol all pref 1 bpf 
filter protocol all pref 1 bpf handle 0x1 bpf_host.o:[from-host] direct-action 

$ export V=cilium_net &&  tc filter show dev $V ingress && echo "========" &&tc filter show dev $V egress 
filter protocol all pref 1 bpf 
filter protocol all pref 1 bpf handle 0x1 bpf_host_cilium_net.o:[to-host] direct-action 
========

$ export V=docker0 &&  tc filter show dev $V ingress && echo "========" &&tc filter show dev $V egress 
========
```

所以这三者应该是中转的组件，逐个剖析每个函数的作用：几千行代码。

Map 共有

| 名称   | 作用域  |
| Connection Tracking  | node or endpoint  |
| NAT | node |
| Neighbor Table   | node
| Endpoints | node |
| IP cache   | node |
| Load Balancer  | node |
| Policy | endpoint |
| Proxy Map  | node |
| Tunnel | node |
| IPv4 Fragmentation   | node |
| Session Affinity   | node |
| IP Masq  | node |
| Service Source Ranges  | node |



