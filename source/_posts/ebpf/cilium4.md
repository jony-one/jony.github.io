---
title: Cilium 源码阅读：如何制定和执行 L3 策略 续
date: 2021-06-15 19:44:19
categories: 
	- [eBPF]
tags:
  - ebpf
  - cilium
author: Jony
---



# Cilium 源码阅读： 如何制定和执行 L3 策略 续


debug 了那么久大概知道了整个过程，但是不知道详细操纵。所以比较关心 regenerateBPF 生成了什么样的文件，
注释上写到 
`//regenerateBPF 重写所有标头并更新所有BPF Map以反映指定的端点。 ReloadDatapath强制数据路径程序被重新加载。`

跟踪观察后发现会从 Temp 生成文件，文件可以从 `/run/cilium/state/templates/` 目录下找到：


```bash
$ sudo tree /run/cilium/state/templates
/run/cilium/state/templates
├── 25df1a420976f38f9816d470bf855db02aae2781
│   ├── bpf_host.o
│   └── ep_config.h
└── 9c049fd667d887a78a9fa5569ec628f318b57cde
    ├── bpf_lxc.o
    └── ep_config.h
```

然后 regenerateBPF 的操作就是为每个 docker 实例单独生成一个 BPF 程序加载，加入当前实例关联的 ID 是 2665：

```bash
/run/cilium/state/2665
├── bpf_lxc.o
├── ep_config.h
```
具体看下各个文件内容：/run/cilium/state/2665/ep_config.h

```c
/*
 * CILIUM_BASE64_IjEuOC4xMCBmOThmOGI0ODkgMjAyMS0wNi0xNFQyMDoyMjo1NiswODowMCBnbyB2ZXJzaW9uIGdvMS4xNC4zIGxpbnV4L2FtZDY0Ig==:eyJJRCI6MjY2NSwiQ29udGFpbmVyTmFtZSI6ImFwcDEiLCJkb2NrZXJJRCI6IjMyMzI3ZjdiNGNmOTA0MGFmODBhZmU5NjA3ZThiN2Y0MzA1NzAxZDc1ZTU4NWFlMjE2YzM1ZjliNDgxNDA1YjIiLCJEb2NrZXJOZXR3b3JrSUQiOiJmMWU4ODk3MGQ1NWU2MzJhNmYxMzBiY2FjYjRhOWUzMjhhMzQ4OWQ4OGMyMzFiMTkxZmNmZjk0ZTA1M2QzMGVkIiwiRG9ja2VyRW5kcG9pbnRJRCI6ImU4YTNlNDZmNTZiOTY0ZDBkMDUxMjBhZWYwMzQ3MDEyNjRiNDgzZWQwYTZiOGRkYjdiMzE1ZTEwM2IyOGE2OGYiLCJEYXRhcGF0aE1hcElEIjowLCJJZk5hbWUiOiJseGM4MDc2MzNiZmVkZWEiLCJJZkluZGV4IjoxOSwiT3BMYWJlbHMiOnsiQ3VzdG9tIjp7fSwiT3JjaGVzdHJhdGlvbklkZW50aXR5Ijp7ImlkIjp7ImtleSI6ImlkIiwidmFsdWUiOiJhcHAxIiwic291cmNlIjoiY29udGFpbmVyIn0sImlkLnNlcnZpY2UxIjp7ImtleSI6ImlkLnNlcnZpY2UxIiwic291cmNlIjoiY29udGFpbmVyIn19LCJEaXNhYmxlZCI6e30sIk9yY2hlc3RyYXRpb25JbmZvIjp7fX0sIkxYQ01BQyI6IjZlOmIwOjdkOjRlOjZiOmU1IiwiSVB2NiI6IiIsIklQdjQiOiIxMC4xMS4yNTUuMTA0IiwiTm9kZU1BQyI6IjQyOjIzOmRjOjRkOjc5OmIxIiwiU2VjTGFiZWwiOnsiaWQiOjEyMDIyLCJsYWJlbHMiOnsiaWQiOnsia2V5IjoiaWQiLCJ2YWx1ZSI6ImFwcDEiLCJzb3VyY2UiOiJjb250YWluZXIifSwiaWQuc2VydmljZTEiOnsia2V5IjoiaWQuc2VydmljZTEiLCJzb3VyY2UiOiJjb250YWluZXIifX0sImxhYmVsc1NIQTI1NiI6IiJ9LCJPcHRpb25zIjp7Im1hcCI6eyJDb25udHJhY2siOjEsIkNvbm50cmFja0FjY291bnRpbmciOjEsIkNvbm50cmFja0xvY2FsIjowLCJEZWJ1ZyI6MCwiRGVidWdMQiI6MCwiRGVidWdQb2xpY3kiOjAsIkRyb3BOb3RpZmljYXRpb24iOjEsIk1vbml0b3JBZ2dyZWdhdGlvbkxldmVsIjowLCJOQVQ0NiI6MCwiUG9saWN5QXVkaXRNb2RlIjowLCJQb2xpY3lWZXJkaWN0Tm90aWZpY2F0aW9uIjoxLCJUcmFjZU5vdGlmaWNhdGlvbiI6MX19LCJETlNSdWxlcyI6bnVsbCwiRE5TSGlzdG9yeSI6W10sIkROU1pvbWJpZXMiOnt9LCJLOHNQb2ROYW1lIjoiIiwiSzhzTmFtZXNwYWNlIjoiIiwiRGF0YXBhdGhDb25maWd1cmF0aW9uIjp7fX0=
 * 
 * Container ID: 32327f7b4cf9040af80afe9607e8b7f4305701d75e585ae216c35f9b481405b2
 * IPv6 address: 
 * IPv4 address: 10.11.255.104
 * Identity: 12022
 * PolicyMap: cilium_policy_02665
 * NodeMAC: 42:23:dc:4d:79:b1
 */

/*
 * Labels:
 * - {id app1 container}
 * - {id.service1  container}
 */

#include "lib/utils.h"

DEFINE_U32(LXC_IPV4, 0x68ff0b0a); /* 1761544970 */
#define LXC_IPV4 fetch_u32(LXC_IPV4)
DEFINE_U32(LXC_ID, 0x00000a69); /* 2665 */
#define LXC_ID fetch_u32(LXC_ID)
DEFINE_MAC(NODE_MAC, 0x42, 0x23, 0xdc, 0x4d, 0x79, 0xb1);
#define NODE_MAC fetch_mac(NODE_MAC)
DEFINE_U32(SECLABEL, 0x00002ef6); /* 12022 */
#define SECLABEL fetch_u32(SECLABEL)
DEFINE_U32(SECLABEL_NB, 0xf62e0000);  /* 4130209792 */
#define SECLABEL_NB fetch_u32(SECLABEL_NB)
DEFINE_U32(POLICY_VERDICT_LOG_FILTER, 0x00000000);  /* 0 */
#define POLICY_VERDICT_LOG_FILTER fetch_u32(POLICY_VERDICT_LOG_FILTER)
#define POLICY_MAP cilium_policy_02665
#define CALLS_MAP cilium_calls_02665
#define FORCE_LOCAL_POLICY_EVAL_AT_SOURCE 1
#define ENABLE_ROUTING 1
#define ENABLE_ARP_RESPONDER 1
#define ENABLE_HOST_REDIRECT 1
#define CT_MAP_TCP4 cilium_ct4_global
#define CT_MAP_ANY4 cilium_ct_any4_global
#define CT_MAP_TCP6 cilium_ct6_global
#define CT_MAP_ANY6 cilium_ct_any6_global
#define CT_MAP_SIZE_TCP 524288
#define CT_MAP_SIZE_ANY 262144
#define LOCAL_DELIVERY_METRICS 1
#define CONNTRACK 1
#define CONNTRACK_ACCOUNTING 1
#undef CONNTRACK_LOCAL
#undef DEBUG
#undef LB_DEBUG
#undef POLICY_DEBUG
#define DROP_NOTIFY 1
#undef MONITOR_AGGREGATION
#undef ENABLE_NAT46
#undef POLICY_AUDIT_MODE
#define POLICY_VERDICT_NOTIFY 1
#define TRACE_NOTIFY 1
#define IPCACHE6_PREFIXES 128,0,
#define IPCACHE4_PREFIXES 32,0,
```

```bash
$ docker ps -l
CONTAINER ID        IMAGE               COMMAND              CREATED             STATUS              PORTS               NAMES
32327f7b4cf9        cilium/demo-httpd   "httpd-foreground"   16 minutes ago      Up 15 minutes                           app1
```

看到这大概就明白了，但是上面 BASE64 隐藏了什么内容：

```json
{
  "ID": 2665,
  "ContainerName": "app1",
  "dockerID": "32327f7b4cf9040af80afe9607e8b7f4305701d75e585ae216c35f9b481405b2",
  "DockerNetworkID": "f1e88970d55e632a6f130bcacb4a9e328a3489d88c231b191fcff94e053d30ed",
  "DockerEndpointID": "e8a3e46f56b964d0d05120aef034701264b483ed0a6b8ddb7b315e103b28a68f",
  "DatapathMapID": 0,
  "IfName": "lxc807633bfedea",
  "IfIndex": 19,
  "OpLabels": {
    "Custom": {},
    "OrchestrationIdentity": {
      "id": {
        "key": "id",
        "value": "app1",
        "source": "container"
      },
      "id.service1": {
        "key": "id.service1",
        "source": "container"
      }
    },
    "Disabled": {},
    "OrchestrationInfo": {}
  },
  "LXCMAC": "6e:b0:7d:4e:6b:e5",
  "IPv6": "",
  "IPv4": "10.11.255.104",
  "NodeMAC": "42:23:dc:4d:79:b1",
  "SecLabel": {
    "id": 12022,
    "labels": {
      "id": {
        "key": "id",
        "value": "app1",
        "source": "container"
      },
      "id.service1": {
        "key": "id.service1",
        "source": "container"
      }
    },
    "labelsSHA256": ""
  },
  "Options": {
    "map": {
      "Conntrack": 1,
      "ConntrackAccounting": 1,
      "ConntrackLocal": 0,
      "Debug": 0,
      "DebugLB": 0,
      "DebugPolicy": 0,
      "DropNotification": 1,
      "MonitorAggregationLevel": 0,
      "NAT46": 0,
      "PolicyAuditMode": 0,
      "PolicyVerdictNotification": 1,
      "TraceNotification": 1
    }
  },
  "DNSRules": null,
  "DNSHistory": [],
  "DNSZombies": {},
  "K8sPodName": "",
  "K8sNamespace": "",
  "DatapathConfiguration": {}
}
```

又明白了一点，藏了实例的基本信息。注释的头部有很多的信息：

```bash
 * Container ID: 32327f7b4cf9040af80afe9607e8b7f4305701d75e585ae216c35f9b481405b2
 * IPv6 address:  //IPv6 地址
 * IPv4 address: 10.11.255.104   // IPv4 地址
 * Identity: 12022  // 当前实例身份标识
 * PolicyMap: cilium_policy_02665  // 当前实例对应的策略 Map
 * NodeMAC: 42:23:dc:4d:79:b1  // 当前实例的 Mac 地址
```

知道了 Policy 对应的 Map  知道了 BPF 程序是 Docker 实例一一对应的那么久可以分析一下  BPF 程序做了些啥，直接分析 bpf_host.c 和  bpf_lxc.c 文件吧。

大致看了下 bpf 目录下的文件：

```language
__section("sockops")

__section("from-netdev")
__section("from-host")
__section("to-netdev")
__section("to-host")

__section("from-container")
__section("to-container")

__section("from-network")
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

4: docker0: <BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500 qdisc noqueue state UP group default 
    link/ether 02:42:62:0a:04:14 brd ff:ff:ff:ff:ff:ff
    inet 172.17.0.1/16 brd 172.17.255.255 scope global docker0
       valid_lft forever preferred_lft forever
    inet6 fe80::42:62ff:fe0a:414/64 scope link 
       valid_lft forever preferred_lft forever
6: veth6bf49a1@if5: <BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500 qdisc noqueue master docker0 state UP group default 
    link/ether 82:e0:f5:1a:8d:ca brd ff:ff:ff:ff:ff:ff link-netnsid 0
    inet6 fe80::80e0:f5ff:fe1a:8dca/64 scope link 
       valid_lft forever preferred_lft forever
8: veth20f4c3f@if7: <BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500 qdisc noqueue master docker0 state UP group default 
    link/ether ee:48:81:af:5e:7b brd ff:ff:ff:ff:ff:ff link-netnsid 1
    inet6 fe80::ec48:81ff:feaf:5e7b/64 scope link 
       valid_lft forever preferred_lft forever
9: cilium_net@cilium_host: <BROADCAST,MULTICAST,NOARP,UP,LOWER_UP> mtu 1500 qdisc noqueue state UP group default qlen 1000
    link/ether 02:36:d7:87:c5:9b brd ff:ff:ff:ff:ff:ff
    inet6 fe80::36:d7ff:fe87:c59b/64 scope link 
       valid_lft forever preferred_lft forever
10: cilium_host@cilium_net: <BROADCAST,MULTICAST,NOARP,UP,LOWER_UP> mtu 1500 qdisc noqueue state UP group default qlen 1000
    link/ether 3e:ac:d1:3d:fe:30 brd ff:ff:ff:ff:ff:ff
    inet 10.11.53.10/32 scope link cilium_host
       valid_lft forever preferred_lft forever
    inet6 fd01::b/128 scope global 
       valid_lft forever preferred_lft forever
    inet6 fe80::3cac:d1ff:fe3d:fe30/64 scope link 
       valid_lft forever preferred_lft forever
11: cilium_vxlan: <BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500 qdisc noqueue state UNKNOWN group default qlen 1000
    link/ether 16:96:5a:55:b7:43 brd ff:ff:ff:ff:ff:ff
    inet6 fe80::1496:5aff:fe55:b743/64 scope link 
       valid_lft forever preferred_lft forever
13: lxc_health@if12: <BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500 qdisc noqueue state UP group default 
    link/ether fe:17:fd:39:83:39 brd ff:ff:ff:ff:ff:ff link-netns cilium-health
    inet6 fe80::fc17:fdff:fe39:8339/64 scope link 
       valid_lft forever preferred_lft forever
19: lxc807633bfedea@if18: <BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500 qdisc noqueue state UP group default 
    link/ether 42:23:dc:4d:79:b1 brd ff:ff:ff:ff:ff:ff link-netnsid 3
    inet6 fe80::4023:dcff:fe4d:79b1/64 scope link 
       valid_lft forever preferred_lft forever
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

  总结：气氛都轰到这了，说实话这段没看懂。猜测一下吧，就是根据上面的提到的概念做了一个具体的实现和功能划分，具体实现分为
  cilium_net、cilium_host、cilium_vxlan 三个虚拟接口，虚拟接口就是 ipvlan 相关概念了，然后脑抽的去学习了一下 ipvlan。下面继续笔记。
  预过滤就是 XDP 层提到的功能更，Endpoint 策略就是属于流量治理了，一个治理 L3、L4 流量，还有一个就是将流量转发到用户空间去处理 L7 流量。
  Service 目测是做了一个负载均衡，与 k8s 的 Service 好像是一样的，当流量进来了以后查找自己所代理的实例然后将流量转发过去，这里没有说明流量
  是有状态的还是无状态的。
  socket 就是短路径的概念了，不参与连接的建立、销毁。建立完成之后监听数据的发送和转发。


太长了 400行了，去续续看 ipvlan










