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

| 名称                  |         作用域       |       作用                              |
| Connection Tracking   | node or endpoint    |       连接跟踪表                         | 
| NAT                   |  node               |        NAT映射表                        |
| Neighbor Table        | node                |           |
| Endpoints             | node                |        本地端点Map                      |
| IP cache              | node                |     管理IP/CIDR<->身份的IPCache映射      |
| Load Balancer         | node                |          负载平衡配置                    |
| Policy                | endpoint            |       管理与策略相关的BPF映射             |
| Proxy Map             | node                |           代理配置                       |
| Tunnel                | node                |        隧道端点Map                       |
| IPv4 Fragmentation    | node                |           |
| Session Affinity      | node                |           |
| IP Masq               | node                |      ip-masq-agent CIDRs               |
| Service Source Ranges | node                |           |

继续了解代码：bpf 如何执行策略的


调用 regenerateBPF 方法的入口在这，所以需要向上反推，什么情况下才会触发 `RegenerateIfAlive` 方法，看方法名的意思是如果存在存活节点才执行，
思考：如果没有存活节点会不会触发？如果删除节点会不会触发？epsToRegen 内容是什么怎么的来的？

```golang
  epsToRegen.ForEachGo(&enqueueWaitGroup, func(ep policy.Endpoint) {
    if ep != nil {
      switch e := ep.(type) {
      case *endpoint.Endpoint:
        // Do not wait for the returned channel as we want this to be
        // ASync 不要等待返回的通道，因为我们希望这是异步的
        e.RegenerateIfAlive(regenMetadata)
      default:
        log.Errorf("BUG: endpoint not type of *endpoint.Endpoint, received '%s' instead", e)
      }
    }
  })
```

debug 从 policyAdd 跳转至 PolicyReactionEvent 发现 endpointsToRegen 在 policyAdd 的时候为空，跳转之后就不为空了，说明 RegenerateIfAlive 不是从 
policyAdd 跳转过来的。那么就将目标转到 putPolicy 上。

入参如下：
```json
[
  {
    "endpointSelector": {
      "matchLabels": {
        "any:id": "app1"
      }
    },
    "ingress": [
      {
        "fromEndpoints": [
          {
            "matchLabels": {
              "any:id": "app2"
            }
          }
        ],
        "toPorts": [
          {
            "ports": [
              {
                "port": "80",
                "protocol": "TCP"
              }
            ]
          }
        ]
      }
    ],
    "labels": [
      {
        "key": "name",
        "value": "l3-rule",
        "source": ""
      }
    ]
  }
]
```

~~规则整合完整后就会触发 UpdateRulesEndpointsCaches 就需要更新 Endpoint 筛选出来，放到 Set 中，然后从原有的 Set 中删除。目的就是将需要~~
~~更新的 Endpoint 和 不需要更新的 Endpoint 区分开来，但是每次更新都需要修改 Revision。 ~~
~~~~
~~匹配完成后会触发 PolicyReactionEvent#reactToRuleUpdates 方法。将需要修改的 Endpoint 重新生成。进入 RegenerateIfAlive  方法：~~
~~~~
~~1. 判断当前 Endpoint 是否处于 Alive~~
~~2. 切换上下文至 EndpointRegenerationEvent 执行~~
~~3. 开始处理 Regeneration 事件~~
~~4. 这里为了防止出现死锁，所以在 Endpoint 重新生成 BPF 时对 Endpoint 加锁。~~
~~5. 获取当前 Endpoint State 文件夹路径，一般时 `/var/run/cilium/state/{EnpointId}`~~
~~6. 创建一个临时目录，tmpDir~~
~~7. 如果对应的 Map 不存在就创建已给 Map -> 子步骤需要详解~~
~~8. 过滤成一组基于SelectorCache的具体 Map 条目。这些条目随后可以被引入数据路径。~~
~~9. 跟新 BPF Map  IPcache  ，查看下 ipcache 用来干啥的~~

一直 debug 到核心位置，发现注释上面已经写了：
```language
Allow pushes an entry into the PolicyMap to allow traffic in the given  `trafficDirection` for identity `id` with destination port `dport` over  protocol `proto`.

 It is assumed that `dport` and `proxyPort` are in host byte-order.

 Allow向PolicyMap推送一个条目，允许身份为`id`，目的端口为`dport`，协议为`proto`的流量进入给定的`trafficDirection`。

 假设`dport`和`proxyPort`是按主机字节顺序排列的。
 ## trafficDirection 流量方向
```

只能说这个设计有点出乎意料，上层程序对数据的处理到精简化，给到底层 eBPF 基础架构。绕了很大一圈。

原理简单描述： 将现有的实例信息转至为一个 **实例 -> ID** 存储到 ipcache 中。策略存储更为简单。
PoliyMap 存储 
**key: ptr(id,dport,proto,trafficDirect)**
**value:proxyPort**
解释下key 的内容：`id -> 来源实例的 ID 也就是通过 ipcache 获取的id`，`dport:目标端口`,`proto:通信协议`








0 = ReservedIdentityHealth (4) -> 
1 = ReservedEKSKubeDNS (103) -> 
2 = ReservedIdentityInit (5) -> 
3 = ReservedCiliumOperator (105) -> 
4 = github.com/cilium/cilium/pkg/datapath/loader.templateSecurityID (2) -> 
5 = ReservedCoreDNS (104) -> 
6 = ReservedCiliumKVStore (101) -> 
7 = ReservedIdentityUnmanaged (3) -> 
8 = ReservedIdentityRemoteNode (6) -> 
9 = ReservedKubeDNS (102) -> 
10 = ReservedEKSCoreDNS (106) -> 
11 = ReservedCiliumEtcdOperator (107) -> 
12 = ReservedIdentityHost (1) -> 
13 = ReservedETCDOperator (100) -> 






docker run -d --name app1 --net cilium-net -l "id=app1" cilium/demo-httpd   10.11.71.148
{10.11.71.148  1f9f6dbe2f191722432ee407ed40a46082494fee9bf5929ad653e04a17f2e3ad  f00d::a0f:0:0:bd64    1f9f6dbe2f19  d6:a4:50:4a:84:a8   cilium0@if19}
docker run -d --name app2 --net cilium-net -l "id=app2" nginx   
{10.11.237.236  a5b9160b399224d0b329fe1885bc07a4449b4273c0fa5ff462c7dc319e2990e7  f00d::a0f:0:0:bd64    a5b9160b3992  06:46:c4:a2:66:b6  cilium0@if49}
docker run --rm -ti --net cilium-net -l "id=app2" cilium/demo-client curl -m 20 http://app1
docker run --rm -ti --net cilium-net -l "id=app3" cilium/demo-client curl -m 20 http://app1