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

也就是说 BPF attach 进入了上面的这么多点。所以要搞清楚每个 attach 点在什么地方会被触发执行，BPF 本来就是基于事件驱动的。














