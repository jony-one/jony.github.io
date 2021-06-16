---
title: Cilium 源码阅读：cilium-agent eBPF
date: 2021-06-16 12:44:19
categories: 
	- [eBPF]
tags:
  - ebpf
  - cilium
author: Jony
---



# Cilium 源码阅读： 功能解析

# 关于 eBPF 代码解析

本次代码基于 cilium v1.8 代码版本解析。


```c
__section("from-network")
__section("from-netdev")
__section("from-host")
__section("to-netdev")
__section("to-host")

__section("from-container")
__section("to-container")

__section("from-overlay")
__section("to-overlay")

__section("connect4")
__section("post_bind4")
__section("sendmsg4")
__section("recvmsg4")
__section("getpeername4")

__section("post_bind6")
__section("connect6")
__section("sendmsg6")
__section("recvmsg6")
__section("getpeername6")

__section("from-netdev")
__section("sk_msg")
__section("sockops")
```
每个 section 代表一个单独的程序，即使多个 section 放在一个文件中。

思考：eBPF 工作在什么地方？被加载到哪去？不得不提的具体工作内容？

据目前了解的 eBPF 程序的加载方式有三种：tc、iproute2、bpfload

前两种都是网络用到的 eBPF 。所以就从 tc、iproute2 下手。既然主要程序是用来解决进流量的过滤，上层展示是用 label 来管理流量进出权限。但是底层依然使用的是 IP 权限。

`docker start demo1` -> `request cilium-ipam` -> `request cilium-cni` -> `load demo1-bpf`  -> `pin demo1-bpf-map`       
制定策略 禁止 demo2 访问 demo1
`docker start demo2 request demo1` -> ↑       ->         ↑            ->     ↑             ->      ↑             ->  `request demo1-server`
                                      ↓ 
                                      `async policy` -> `demo2-ip to demo1-bpf-map` 
                                                                                                   ↓
                                   `demo2 request timeout`      <-   `package drop`       <-    `demo1 ingress`


运行命令:

```bash
ip addr

8: veth9613a25@if7: <BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500 qdisc noqueue master docker0 state UP mode DEFAULT group default
    link/ether 1a:f0:78:0d:bc:5f brd ff:ff:ff:ff:ff:ff link-netnsid 0
10: vethb8a8bca@if9: <BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500 qdisc noqueue master docker0 state UP mode DEFAULT group default
    link/ether 12:03:76:f8:3b:a9 brd ff:ff:ff:ff:ff:ff link-netnsid 1
11: cilium_net@cilium_host: <BROADCAST,MULTICAST,NOARP,UP,LOWER_UP> mtu 1500 qdisc noqueue state UP mode DEFAULT group default qlen 1000
    link/ether 4e:5a:8c:58:f4:2e brd ff:ff:ff:ff:ff:ff
12: cilium_host@cilium_net: <BROADCAST,MULTICAST,NOARP,UP,LOWER_UP> mtu 1500 qdisc noqueue state UP mode DEFAULT group default qlen 1000
    link/ether 72:97:c5:3e:c8:f1 brd ff:ff:ff:ff:ff:ff
13: cilium_vxlan: <BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500 qdisc noqueue state UNKNOWN mode DEFAULT group default qlen 1000
    link/ether 36:c1:8d:86:b1:3b brd ff:ff:ff:ff:ff:ff
15: lxc_health@if14: <BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500 qdisc noqueue state UP mode DEFAULT group default
    link/ether 5e:82:28:6a:db:97 brd ff:ff:ff:ff:ff:ff link-netns cilium-health
21: lxcfd196ec6df51@if20: <BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500 qdisc noqueue state UP mode DEFAULT group default
    link/ether 2e:07:92:04:03:d5 brd ff:ff:ff:ff:ff:ff link-netnsid 3
```

`lxcfd196ec6df51@if20` 应该就是当前 demo1 的网卡了。通过  `ip link |grep xdp` 发现并没有 `XDP` 运行。可能是运行在虚拟机里的原因。

```bash
$ tc filter show dev lxcfd196ec6df51@if20 ingress
filter protocol all pref 1 bpf
filter protocol all pref 1 bpf handle 0x1 bpf_lxc.o:[from-container] direct-action
```
tc 的命令显示具有 eBPF 程序加载。加载 `bpf_lxc.o:[from-container]` 还是 da 模式 ：

`from-container` 代码查看：

```c
__section("from-container")
int handle_xgress(struct __ctx_buff *ctx)
{
  __u16 proto;
  int ret;

  bpf_clear_meta(ctx);

  send_trace_notify(ctx, TRACE_FROM_LXC, SECLABEL, 0, 0, 0, 0,
        TRACE_PAYLOAD_LEN);

  if (!validate_ethertype(ctx, &proto)) {
    ret = DROP_UNSUPPORTED_L2;
    goto out;
  }

  switch (proto) {
  case bpf_htons(ETH_P_IPV6):
    invoke_tailcall_if(__or(__and(is_defined(ENABLE_IPV4), is_defined(ENABLE_IPV6)),
          is_defined(DEBUG)),
           CILIUM_CALL_IPV6_FROM_LXC, tail_handle_ipv6);
    break;
  case bpf_htons(ETH_P_IP):
    invoke_tailcall_if(__or(__and(is_defined(ENABLE_IPV4), is_defined(ENABLE_IPV6)),
          is_defined(DEBUG)),
           CILIUM_CALL_IPV4_FROM_LXC, tail_handle_ipv4);
    break;
  case bpf_htons(ETH_P_ARP):
    ret = CTX_ACT_OK;
    break;
  default:
    ret = DROP_UNKNOWN_L3;
  }

out:
  if (IS_ERR(ret))
    return send_drop_notify(ctx, SECLABEL, 0, 0, ret, CTX_ACT_DROP,
          METRIC_EGRESS);
  return ret;
}
```

问题还是挺多的，比如入参为什么不是 `__skb_buff ` 这个问题。所以带着问题搜索了一下，然并暖。直接复习下 `Cilium 架构` 和 `eBPF 编程指南` 吧。

## eBPF 下微服务网络安全

eBPF 适用于监控、安全和网络领域：

Cilium 项目大量使用了 eBPF ，为基于容器的系统提供了路由和网络流量的过滤。可以在不修改内核的前提下动态的生成和应用规则。

```bash
      _______d:app1__________
      /    x         x
  :80/     |:22       \
    id:app2          id:app3
```

上面就应用了 L3/L4 策略允许 app2 通过 80端口访问 app1，不允许 app3 访问 app1
```json
[{
  "labels": [{
    "key": "name",
    "value": "l3-rule"
  }],
  "endpointSelector": {
    "matchLabels": {
      "id": "app1"
    }
  },
  "ingress": [{
    "fromEndpoints": [{
      "matchLabels": {
        "id": "app2"
      }
    }],
    "toPorts": [{
      "ports": [{
        "port": "80",
        "protocol": "TCP"
      }]
    }]
  }]
}]
```

Cilium 具体是如何完成上面的工作的？
![架构图](https://img2020.cnblogs.com/blog/1334952/202004/1334952-20200418113727982-621490951.png)

Cilium 为每个主机运行一个 agent，将网络策略定义转换成 BPF 程序。这些程序会被加载到内核中。这里也就解释了为什么每次注入新的策略都会触发 BPF 重新生成的问题。但是没有解决根本的问题，为什么每次策略修改都需要重新生成 BPF 程序，难道不能优化么？
当 BPF 程序被加载到容器的虚拟以太网设备上后，每次发送和接收的报文都会应用这些规则。

Cilium 使用 Hook 列表：
- XDP： XDP Hoot 最早可以在网络驱动中使用，在报文接收时触发 BPF 程序。可以修改包地址和端口
- Ingress/Egress 流控: 与 XDP 类似，也是附加到网络驱动程序上触发。但是在网络栈完成初始化的报文之后运行。该 Hook  在协议栈的 L3 层之前运行，但可以访问与报文相关的大多数元数据。
- socket 操作：socket Hook 附加到一个特定的 cgroup 上，根据 TCP 事件运行。Cilium 将 BPF Socket 操作程序附加到 cgroup 上，监听 TCP 状态变更，特别是对 ESTABLISHED 状态变更。当一个
  socket 状态变化为 ESTABLISHED 时，如果 TCP socket 的连接的远程位于本节点，就附加 socket send/recv 程序。
- socket 发送接收：单台已给 TCP socket 执行发送操作时会运行 socket send/recv hook。 这个时候 hook 会检查消息或者丢弃消息。将消息发送至 TCP 层或者直接重定向到另一个套接字。Cilium 使用它来加速数据路径重定向。

  总结一下：就是 XDP 层工作在 sk_buff 形成之前，可以对原始数据包进行操作并且只有进这个单向可以操作。Ingress/Egress 是在 sk_buff 上操作，例如：Ingress 是在 sk_buff 形成之后进入三层之前进行操作，但是不可以在修改重定向数据包。前面两个都是在驱动层进行操作，但是 XDP 操作更提前所以看起来效率更高。二者可以互补。
  Cilium 对 Socket 的支持则是放在 cgroup 上，监听 TCP 的状态，只要 TCP 进入 ESTABLISHED 状态，就会附加 BPF 程序，也就是说不会管理 socket 状态、连接建立、连接销毁操作。只会在
  socket 形成连接之后判断数据包发往的方向是否在本机上，如果是就会直接转发到对应的 socket，如果不是就按照正常的流程。


所以 Cilium 定义了三个虚拟网卡接口，cilium_net、cilium_host、cilium_vxlan ，根据上面的 Hook 与这三个虚拟网卡接口结婚，可以创建下面的网络对象：

- 预过滤（prefilter）：prefilter 会运行一个 XDP 程序，过滤网络上的流量。根据目的地选择丢弃报文、允许网络协议栈处理报文等操作。可扩展过滤规则
- Endpoint 策略：Endpoint Policy对象实现Cilium端点强制。使用映射查找与标识和策略相关的数据包，该层可以很好地扩展到许多端点。根据策略，
  该层可以丢弃数据包、转发到本地端点、转发到服务对象或转发到L7策略对象以获得进一步的L7规则。这是Cilium数据路径中的主要对象，负责将数据包映射到标识并强制执行L3和L4策略。
- Service: Service对象对该对象接收到的每个包执行目的地IP和可选目的地端口上的映射查找。如果找到匹配的表项，报文将被转发到配置
  的L3/L4端点之一。Service块可以用于在使用TC入口钩子的任何接口上实现独立的负载均衡器，也可以集成到端点策略对象中。
- socket layer Enforcement：socket layer Enforcement会使用两个钩子，socket 操作钩子和socket 发送/接收钩子来监控并附加到所有与Cilium管理的endpoint相关的TCP socket，
  包括L7代理。socket操作钩子会识别要加速的候选套接字，这些候选套接字包括所有的本地节点连接(endpoint到endpoint)以及所有到Cilium代理的连接。这些标识的连接将会包含所有
  由socket 发送/接收钩子处理的消息，并且使用sockmap快速重定向进行加速。快速重定向保证Cilium中实现的所有策略对于关联的socket/endpoint映射均有效，并假设
  它们会直接向对端socket发送消息。sockmap send/recv钩子确保消息不会被上面提到的任何对象处理。
- L7策略：L7策略对象将代理的流量重定向到一个Cilium用户空间代理实例中。Cilium使用一个Envoy作为它的用户空间代理。Envoy要么转发流量，要么会根据配置的L7策略生成拒绝消息。

  