---
title: Cilium 源码阅读：如何制定和执行 L3 策略 续续
date: 2021-06-20 00:44:19
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

| 名称                   |         作用域       |       作用                              |
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

结构如下：

```golang
type PolicyKey struct {
	Identity         uint32 `align:"sec_label"`  // => 36895
	DestPort         uint16 `align:"dport"` // In network byte-order   => 80
	Nexthdr          uint8  `align:"protocol"`   // TCP 
	TrafficDirection uint8  `align:"egress"`     // =>  0
}

type PolicyEntry struct {
	ProxyPort uint16 `align:"proxy_port"` // In network byte-order
	Pad0      uint16 `align:"pad0"`
	Pad1      uint16 `align:"pad1"`
	Pad2      uint16 `align:"pad2"`
	Packets   uint64 `align:"packets"`
	Bytes     uint64 `align:"bytes"`
}
```

解释到这了：来源实例的 ID 一开始只是假设，但是后来确实坐实了。比如这里的 36895 通过查看 ipcache 就可以找到对应的实例：

```bash
cilium map get  cilium_ipcache
Key                             Value             State   Error
f00d::a0f:0:0:2fd8/128          4 0 0.0.0.0       sync    
10.0.2.15/32                    1 0 0.0.0.0       sync    
fe80::3cac:d1ff:fe3d:fe30/128   1 0 0.0.0.0       sync    
10.11.90.17/32                  36895 0 0.0.0.0   sync  

docker run -d --name app1 --net cilium-net -l "id=app1" cilium/demo-httpd   10.11.71.148
{10.11.255.104  32327f7b4cf9  f00d::a0f:0:0:36a    32327f7b4cf9  6e:b0:7d:4e:6b:e5   cilium0@if19}
docker run -d --name app2 --net cilium-net -l "id=app2" nginx   
{10.11.90.17  a94c744974c4  f00d::a0f:0:0:36a    a94c744974c4  1a:6d:c6:85:08:4d  cilium0@if49}
```

而对应的实例就是这个 IP。
也就是说 app2 实例来访问 app1 实例的时候，eBPF 从二层截取到数据流，获取到来源IP，目标端口号，然后从 ipCache 中获取标识信息，
判断是否允许访问目标端口。

既然是多重判断那么就需要找出  ipcache 的结构体。还要找出如何将信息添加到 ipcache ，底层的逻辑结构推断出来了，那么 ipcache 属于辅助填充条件
找出的思路，就是想上反推，思考的地方有两点，就是在添加策略的时候，将条件推到 ipcahe 是同步的还是异步的，首先可以确定的一点就是推送到 ipcache 
是先决条件，应该是在实例生成的时候推送到  ipcache 。所以应该冲添加实例的入口下手进行程序逻辑判断。
那就回到 createEndpoint 方法，但是找了半天也没有找到更新 ipcache 的接口调用，索性就直接冲调用接口底层打了一个端点：

```golang
pkg/maps/ipcache/ipcache.go
func NewKey(ip net.IP, mask net.IPMask) Key {  
	result := Key{}

	ones, _ := mask.Size()   // break
	...
	return result
}
```

猜测是异步的，没想到异步的这么彻底，也就是说可以完整的用在生产环境。通过订阅 consul 的某个键来完成。也就是说在某个阶段将 IP 传送到 consul 中。

```json
{
	"key": "cilium/state/ip/v1/default/10.11.227.54",
	"value": {
		"IP": "10.11.227.54",
		"Mask": null,
		"HostIP": "10.0.2.15",
		"ID": 5,
		"Key": 0,
		"Metadata": "cilium-global:default:runtime1:3459"
	}
}
```

处理上面这端数据的调用链如下：

```bash
pkg/ipcache/kvstore.go#Watch  => IPIdentityCache.Upsert // 监听 kvstore.EventTypeCreate 创建事件
	pkg/ipcache/ipcache.go#Upsert =>  listener.OnIPIdentityCacheChange
	    pkg/datapath/ipcache/listener.go#OnIPIdentityCacheChange  => l.bpfMap.Update
```


那么问题来了什么地方丢过来的，而且这份 ID 也分配了，Metadata 也分配了，global 看着很熟悉，所以就回头看下 createEndpoint 吧。

调用链如下：

```bash
daemon/cmd/endpoint.go#createEndpoint => ep.UpdateLabels
	pkg/endpoint/endpoint.go#UpdateLabels  => e.runIdentityResolver
	    pkg/endpoint/endpoint.go#runIdentityResolver =>  e.identityLabelsChanged
	         pkg/endpoint/endpoint.go#identityLabelsChanged  =>   e.SetIdentity
	               pkg/endpoint/policy.go#SetIdentity   =>    e.runIPIdentitySync
	                      pkg/endpoint/policy.go#runIPIdentitySync   =>   ipcache.UpsertIPToKVStore
```

现在两边应该就能串起来了，在创建实例的时候，首先调用 requestAddres 接口，然后调用 createEndpoint 接口，createEndpoint 会创建 Endpoint 
虚拟网络等信息，并且设置全局唯一 ID。形成一个实例信息发送到 consul 中。然后 watch 监听到 consul 有新的值就会启动 ipcache 的流程将基本信息
存储到 ipcache 中。

那 ipcache 具体存储什么呢？

```golang
type Key struct {
	Prefixlen uint32 `align:"lpm_key"`   //  64
	Pad1      uint16 `align:"pad1"`
	Pad2      uint8  `align:"pad2"`
	Family    uint8  `align:"family"`    // 1
	// represents both IPv6 and IPv4 (in the lowest four bytes)
	IP types.IPv6 `align:"$union0"`    // 10.11.41.206
}
type RemoteEndpointInfo struct {
	SecurityIdentity uint32     `align:"sec_label"`    // 62820
	TunnelEndpoint   types.IPv4 `align:"tunnel_endpoint"`
	Key              uint8      `align:"key"`   // 
}
```

这就是存储的具体内容。



下面就需要具体分析 ipcache、lxc、filter 等代码了。


~~~~
结构定义如下：

```golang
// 与 bpf/lib/common.h endpoint_key 结构体同步
type EndpointKey struct {
	IP     types.IPv6 `align:"$union0"`
	Family uint8      `align:"family"`  // 地址类型 IPv4 和 IPv6
	Key    uint8      `align:"key"`     // 默认=0
	Pad2   uint16     `align:"pad5"`
}
// 与 <bpf/lib/common.h> endpoint_info 结构体同步
type EndpointInfo struct {
	IfIndex uint32 `align:"ifindex"`
	Unused  uint16 `align:"unused"`
	LxcID   uint16 `align:"lxc_id"`
	Flags   uint32 `align:"flags"`
	// go alignment
	_       uint32
	MAC     MAC        `align:"mac"`
	NodeMAC MAC        `align:"node_mac"`
	Pad     pad4uint32 `align:"pad"`
}
```


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


0 = {uint8} 110 6e
1 = {uint8} 176
2 = {uint8} 125
3 = {uint8} 78
4 = {uint8} 107
5 = {uint8} 229



demo Mac     6e:b0:7d:4e:6b:e5        10.11.255.104
nginx Mac    1a:6d:c6:85:08:4d        
enp0s3 Mac： 52:54:00:12:35:02
docker0 Mac：02:42:ac:11:00:03

docker run -d --name app1 --net cilium-net -l "id=app1" cilium/demo-httpd   10.11.71.148
{10.11.255.104  32327f7b4cf9  f00d::a0f:0:0:36a    32327f7b4cf9  6e:b0:7d:4e:6b:e5   cilium0@if19}
docker run -d --name app2 --net cilium-net -l "id=app2" nginx   
{10.11.90.17  a94c744974c4  f00d::a0f:0:0:36a    a94c744974c4  1a:6d:c6:85:08:4d  cilium0@if49}
~~~~




