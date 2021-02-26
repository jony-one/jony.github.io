---
title: 6. Program_Types
date: 2021-02-24 19:38:33
categories: 
	- [eBPF]
tags:
  - ebpf
author: Jony
---
	
	XDP 负责宿主机到宿主机之间的负载均衡，进入的流量会被统一拦截和转发
	TC 负责宿主机到容器之间的流量转发，进入、发出的流量会被统一拦截和转发

# 术语
per-packet cost ：每包成本，处理一个网络数据包的成本
hairpinning：发卡模式。[hairpin](https://docs.fortinet.com/document/fortigate/5.4.0/cookbook/105831) 
skb: [sock_buffer](https://blog.csdn.net/shanshanpt/article/details/21024465)
Spectre:[幽灵漏洞](https://zh.wikipedia.org/wiki/%E5%B9%BD%E7%81%B5%E6%BC%8F%E6%B4%9E)
CPUMAP:[BPF CPUMAP映射类型](https://xdp-project.net/areas/cpumap.html)
BPF Flow Dissector：[flow dissector](https://www.kernel.org/doc/html/v5.1/networking/bpf_flow_dissector.html) 
[offload](https://en.wikipedia.org/wiki/TCP_offload_engine)
tc层：[Traffic Control Layer](https://www.nsnam.org/docs/models/html/traffic-control-layer.html)，2 层之上3层之下，2.5层
qdisc:[qdisc](https://tonydeng.github.io/sdn-handbook/linux/tc.html)

# 3 程序类型

写作本文时，一共有 18 种不同的 BPF 程序类型，本节接下来进一步介绍其中两种和 网络相关的类型，即 XDP BPF 程序和 tc BPF 程序。这两种类型的程序在 LLVM、 iproute2 和其他工具中使用的例子已经在前一节“工具链”中介绍过了。本节将关注其架 构、概念和使用案例。

---

# 3.1 XDP

XDP（eXpress Data Path）提供了一个内核态、高性能、可编程 BPF 包处理框架。这个框架在软件中最早可以处理包的位置 **（即网卡驱动收到包的时刻）** 运行 BPF 程序。
	
XDP hook 位于网络驱动的快速路径上，XDP 程序直接从接收缓冲区（receive ring）中将 包拿下来，无需执行任何耗时的操作，例如分配 skb 然后将包推送到网络协议栈，或者 将包推送给 **[`GRO 引擎`](https://jaminzhang.github.io/hardware/NIC-offload-Introduction/)** 等等。因此，只要有 CPU 资源，XDP BPF 程序就能够在最早的位置执行处理。


- XDP 可以**复用所有上游开发的内核网络驱动、用户空间工具，以及其他一些可用的内核 基础设施**，例如 BPF 辅助函数在调用自身时可以使用系统路由表、socket 等等。

- 因为驻留在内核空间，因此 XDP 在**访问硬件时与内核其他部分有相同的安全模型**。

- **无需跨内核/用户空间边界**，因为正在被处理的包已经在内核中，因此可以灵活地将 其转发到内核内的其他实体，例如容器的命名空间或内核网络栈自身。Meltdown 和 Spectre 漏洞尤其与此相关（Spectre 论文中一个例子就是用 ebpf 实现的，译者注 ）。

- 将包从 XDP 送到内核中非常简单，可以**复用内核**中这个健壮、高效、使用广泛的 TCP/IP 协议栈，而**不是像一些用户态框架**一样需要自己维护一个独立的 TCP/IP 协 议栈。

- 基于 BPF 可以**实现内核的完全可编程**，保持 ABI 的稳定，保持内核的系统调用 ABI `“永远不会破坏用户空间的兼容性”` 的保证。而且，与内核 模块（modules）方式相比，它还更加安全，这来源于 **BPF 校验器**，它能保证内核操作 的稳定性。

- XDP 轻松地支持在`运行时（runtime）`原子地`创建（spawn）`新程序，而**`不会导致任何网络流量中断`**，甚至不需要重启内核/系统。

- XDP 允许对负载进行灵活的结构化，然后集成到内核。例 如，它可以工作在`“不停轮询”`或`“中断驱动”`模 式。不需要显式地将专门 CPU 分配给 XDP。没有特殊的硬件需求，它也不依赖 `hugepage（大页）`。

- XDP 不需要任何第三方内核模块或许可（licensing）。它是一个长期的架构型解决方案，是 Linux 内核的一个核心组件，而且是由内核社 区开发的。
- 主流发行版中，4.8+ 的内核已经内置并启用了 XDP，并支持主流的 10G 及更高速网络 驱动。


作为一个在驱动中运行 BPF 的框架，XDP 还保证了包是线性放置并且可以匹配到单 个 DMA 页面，这个页面对 BPF 程序来说是可读和可写的。XDP 还提供了额外的 256 字 节 headroom 给 BPF 程序，后者可以利用 **[`bpf_xdp_adjust_head()` ](https://man7.org/linux/man-pages/man7/bpf-helpers.7.html)辅助函数**  实现自定义 **`封装头`** ，或者通过 **[`bpf_xdp_adjust_meta()`](https://man7.org/linux/man-pages/man7/bpf-helpers.7.html) 在包前面添加自定义元数据**。

下一节会深入介绍 XDP 操作码，BPF 程序会根据返回的操作码来指导驱动接下来应该对这个包做什么，而且它还使得我们可以原子地替换运行在 XDP 层的程序。XDP 在设计上就是定位于高性能场景的。BPF 允许以“直接包访问”的 方式访问包中的数据，这意味着程序直接将数据的指针放到了寄存器中，然后将内容加载到寄存器，相应地再将内容从寄存器写到包中。

数据包在 XDP 中的表示形式是 xdp_buff，这也是传递给 BPF 程序的结构体（BPF 上下 文）：

```c
struct xdp_buff {
    void *data;
    void *data_end;
    void *data_meta;
    void *data_hard_start;
    struct xdp_rxq_info *rxq;
};
```

- **`data`**: 指向页面（page）中包数据的起始位置
- **`data_end`**:  执行包数据 的结尾位置
- **`data_meta`**: 开始时指向与 data 相同的位置，`bpf_xdp_adjust_meta()` 能够将其朝着 `data_hard_start` 移动
- **`data_hard_start`**:XDP 支持 headroom，因此 data_hard_start 指向页面中最大可能的 headroom 开始位置
	- 即，当对包进行封包（加 header）时，data 会逐渐向 data_hard_start 靠近，这是通过 `bpf_xdp_adjust_head()` 实现的，该辅助函数还支 持解拆包（去 header）。
- **`rxq`**:指向某些额外的、和每个接收队列相关的元数据：
	- 这些元数据是在缓冲区设置时确定的（并不是在 XDP 运行时）。
```c
			struct xdp_rxq_info {
			    struct net_device *dev;
			    u32 queue_index;
			    u32 reg_state;
			} ____cacheline_aligned;		
```
	

---




**`注：`** data_meta 开始时指向与 data 相同的位置，bpf_xdp_adjust_meta() 能够将其朝着 `data_hard_start`移动，这样可以给自定义元数据提供空间，这个空间对内核网络栈是不可见的，但对 tc BPF 程序可见，因为 **tc 需要将它从 XDP 转移到 skb**。 反之亦然，这个辅助函数也可以将 data_meta 移动到离 data_hard_start 比较远的位 置，这样就可以达到删除或缩小这个自定义空间的目的。 **data_meta 还可以单纯用于在尾调用时`传递状态`**，和 tc BPF 程序中用 skb->cb[] 控制块（control block）类似。


这样，我们就可以得到这样的结论，对于 struct xdp_buff 中数据包的指针，有： `data_hard_start <= data_meta <= data < data_end`.

BPF 程序可以从 netdevice 自身获取 queue_index 以及其他信息，例如 ifindex。

---

# BPF 程序返回码

XDP BPF 程序执行结束后会返回一个判决结果（verdict），告诉驱动接下来如何处理这个 包。在系统头文件 linux/bpf.h 中列出了所有的判决类型。

```c
enum xdp_action {
    XDP_ABORTED = 0,
    XDP_DROP,
    XDP_PASS,
    XDP_TX,
    XDP_REDIRECT,
};
```
- XDP_DROP 表示立即在驱动层将包丢弃。这样可以节省很多资源，对于 DDoS mitigation 或通用目的防火墙程序来说这尤其有用。

- XDP_PASS 表示允许将这个包送到内核网络栈。同时，当前正在处理这个包的 CPU 会 分配一个 skb，做一些初始化，然后将其送到 GRO 引擎。这是没有 XDP 时默 认的包处理行为是一样的。

- XDP_TX 是 BPF 程序的一个高效选项，能够在收到包的网卡上直接将包再发送出去。对于实现防火墙+负载均衡的程序来说这非常有用，因为这些部署了 BPF 的节点可以作为一 个 [hairpin](https://docs.fortinet.com/document/fortigate/5.4.0/cookbook/105831) （发卡模式，从同一个设备进去再出来）模式的负载均衡器集群，将`收到的包`在 XDP BPF 程序中`重写`之后直接推送回去。

- XDP_REDIRECT 与 XDP_TX 类似，但是**通过另一个网卡**将包发出去。另外， `XDP_REDIRECT` 还可以将包重定向到一个 BPF cpumap，即，当前执行 XDP 程序的 CPU 可以将这个包交给某个远端 CPU，由后者（某个远端 CPU）将这个包送到更上层的内核栈，当前 CPU 则继续在这个网卡执行接收和处理包的任务。这和 `XDP_PASS` 类似。但当前 CPU 不用去做将包送到内核协议栈的准备工作（分配 skb，初始化等等），因为这部分开销还是很大的。

- XDP_ABORTED 表示程序产生异常，其行为和 XDP_DROP，但 XDP_ABORTED 会经过 trace_xdp_exception tracepoint，因此可以通过 tracing 工具来监控这种非正常行为。

---

# XDP 使用案例

本节列出了 XDP 的几种主要使用案例。这里列出的并不全，而且考虑到 XDP 和 BPF 的可 编程性和效率，人们能容易地将它们适配到其他领域。

- **DDoS 防御、防火墙**
	XDP BPF 的一个基本特性就是用 `XDP_DROP` 命令驱动将包丢弃，由于这个丢弃的位置 非常早 **（网卡驱动收到包的时刻）** ，因此这种方式可以实现高效的网络策略，平均到每个包的开销非常小（ per-packet cost）。这对于那些需要处理任何形式的 DDoS 攻击的场景来说是非常理 想的，而且由于其通用性，使得它能够在 BPF 内实现任何形式的防火墙策略，开销几乎为零， 例如，作为 standalone 设备（例如通过 XDP_TX 清洗流量）；或者广泛部署在节点上，保护节点的安全（通过 `XDP_PASS` 或 `cpumap XDP_REDIRECT` 允许“好流量”经过）。

	Offloaded XDP 将本来就已经很小的 per-packet cost （每包成本）全部下放到网卡以 线速（line-rate）进行处理，从而使这一点更上一层楼。

- **转发和负载均衡**
	XDP 的另一个主要使用场景是包转发和负载均衡，这是通过 `XDP_TX` 或 `XDP_REDIRECT` 动作实现的。

	XDP 层运行的 BPF 程序能够 **任意修改（mangle）** 数据包，即使是 BPF 辅助函数都能增加或减少包的 `headroom`，这样就可以在将包再次发送出去之前，对包进行任何的封包/拆包。

	利用 XDP_TX 能够实现 hairpinned（发卡）模式的负载均衡器，这种均衡器能够 在接收到包的网卡再次将包发送出去，而 `XDP_REDIRECT` 动作能够将包转发到另一个网卡然后发送出去。

	`XDP_REDIRECT` 返回码还可以和 [BPF cpumap](https://lwn.net/Articles/736336/) 一起使用，对那些目标是本机协议栈、 将由 non-XDP 的远端（remote）CPU 处理的包进行负载均衡。

- **栈前（Pre-stack）过滤/处理**：进入协议栈前处理

	除了策略执行，XDP 还可以用于加固内核的网络栈，这是通过 XDP_DROP 实现的。 这意味着，XDP 能够在可能的最早位置丢弃那些与本节点不相关的包，这个过程发生在 内核网络栈看到这些包之前。例如假如我们已经知道某台节点只接受 TCP 流量，那任 何 UDP、SCTP 或其他四层流量都可以在发现后立即丢弃。

	这种方式的好处是包不需要再经过各种实体（例如 GRO 引擎、内核的 [flow dissector](https://www.kernel.org/doc/html/v5.1/networking/bpf_flow_dissector.html) 以及其他的模块），就可以判断出是否应该丢弃，因此减少了内核的 受攻击面。正是由于 XDP 的早期处理阶段，这有效地对内核网络栈“假装”这些包根本就没被网络设备看到。

	另外，如果内核接收路径上某个潜在 bug 导致 [ping of death](https://zh.wikipedia.org/wiki/%E6%AD%BB%E4%BA%A1%E4%B9%8BPing) 之类的场景，那我们能够利用 XDP 立即丢弃这些包，而不用重启内核或任何服务。而且由于能够原子地替换 程序，这种方式甚至都不会导致宿主机的任何流量中断。

	栈前处理的另一个场景是：在内核**分配 skb 之前**，XDP BPF 程序可以对包进行**`任意修改`**，而且对内核“假装”这个包从网络设备收上来之后就是这样的。对于某些自定义包 修改（mangling）和封装协议的场景来说比较有用，在这些场景下，包在进入 GRO 聚合之前会被修改和解封装，否则 GRO 将无法识别自定义的协议，进而无法执行任何形式的聚合。

	XDP 还能够在包的前面 push 元数据（非包内容的数据）。这些元数据对常规的内核栈是不可见的，但能被 GRO 聚合（匹配元数据），稍后可以和 tc ingress BPF 程序一起处理，tc BPF 中携带了 skb 的某些上下文，例如，设置了某些 skb 字段。

- **流抽样（Flow sampling）和监控**

	XDP 还可以用于包监控、抽样或其他的一些网络分析，例如作为流量路径中间节点 的一部分；或运行在终端节点上，和前面提到的场景相结合。对于复杂的包分析，XDP 提供了设施来高效地将网络包（截断的或者是完整的 payload）或自定义元数据 push 到 perf 提供的一个快速、无锁、per-CPU 内存映射缓冲区，或者是一 个用户空间应用。

	这还可以用于流分析和监控，对每个流的初始数据进行分析，一旦确定是正常流量，这个流随 后的流量就会跳过这个监控。感谢 BPF 带来的灵活性，这使得我们可以实现任何形式 的自定义监控或采用。

---

# XDP 工作模式

XDP 有三种工作模式，默认是 native（原生）模式，当讨论 XDP 时通常隐含的都是指这 种模式。

- **Native XDP**
	默认模式，在这种模式中，XDP BPF 程序直接运行在网络驱动的早期接收路径上（ early receive path）。大部分广泛使用的 10G 及更高速的网卡都已经支持这种模式 。

- **Offloaded XDP**
	在这种模式中，XDP BPF 程序直接 offload 到网卡，而不是在主机的 CPU 上执行。 因此，本来就已经很低的 per-packet 开销完全从主机下放到网卡，能够比运行在 native XDP 模式取得更高的性能。这种 offload 通常由**智能网卡(SmartNIC)**实现，这些网卡有多 线程、多核流处理器（flow processors），一个位于内核中的 JIT 编译器（ in-kernel JIT compiler）将 BPF 翻译成网卡的原生指令。
	
	支持 offloaded XDP 模式的驱动通常也支持 native XDP 模式，因为 BPF 辅助函数可 能目前还只支持后者。

- **Generic XDP**

	对于还没有实现 native 或 offloaded XDP 的驱动，内核提供了一个 generic XDP 选 项，这种模式不需要任何驱动改动，因为相应的 XDP 代码运行在网络栈很后面的一个 位置（a much later point）。

	这种设置主要面向的是用内核的 XDP API 来编写和测试程序的开发者，并且无法达到 前面两种模式能达到的性能。对于在生产环境使用 XDP，推荐要么选择 native 要么选择 offloaded 模式。

# 驱动支持
由于 BPF 和 XDP 的特性和驱动支持还在快速发展和变化，因此这里的列表只统计到了 4.17 内核支持的 native 和 offloaded XDP 驱动。
略...


---

# tc (traffic control)

除了 XDP 等类型的程序之外，BPF 还可以用于内核数据路径的 tc (traffic control，流量控制)层。

**tc 和 XDP BPF 程序的不同。从高层看，tc BPF 程序和 XDP BPF 程序有三点主要不同：**

- 	BPF 的输入上下文（input context）是一个 sk_buff 而不是 xdp_buff。当内核 **协议栈** 收到一个包时（说明包通过了 XDP 层），它会分配一个缓冲区，解析包，并存储包 的元数据。表示这个包的结构体就是 **`sk_buff`**。这个结构体会暴露给 BPF 输入上下文， 因此 tc `ingress` 层的 BPF 程序就可以利用这些（由协议栈提取的）包的元数据。这些元数据很有用，但在包达到 tc 的 hook 点之前，协议栈执行的**缓冲区分配**、**元数据提取**和其他处理等过程也是有`开销`的。从定义来看，xdp_buff 不需要访问这些元数据，因为 XDP hook 在进入协议栈**之前**就会被调用。这是 XDP 和 tc hook 性能差距的重要原因之一 。
	
	因此，attach 到 tc BPF hook 的 BPF 程序可以读取 skb 的 mark、pkt_type、 protocol、priority、queue_mapping、napi_id、cb[]、hash、tc_classid 、tc_index、vlan 元数据和 **XDP 层传过来的自定义元数据** 以及其他信息。 tc BPF 的 BPF 上下文中使用了 **[`struct \_\_sk_buff`](https://elixir.bootlin.com/linux/latest/source/include/linux/skbuff.h#L714)**，这个结构体中的所有成员字段都定义在 **[`linux/bpf.h`](https://elixir.bootlin.com/linux/latest/source/include/linux/bpf.h)**系统头文件。

	通常来说，**[`sk_buff`](https://elixir.bootlin.com/linux/latest/C/ident/sk_buff)** 和 **[`xdp_buff`](https://elixir.bootlin.com/linux/latest/C/ident/xdp_buff)** 完全不同，二者各有优缺点。例如，sk_buff 的优点就是修改与其关联的元数据（its associated metadata）非常方便，但它包含了大量协议相关的特定信息（例如 GSO 相关的状态），这使得无法仅仅通过重写包数据来**`切换协议`**。这是因为**协议栈是基于元数据处理包的，而不是每次都去读包的内容**。因此，BPF 辅助函数需要**额外的转换**，并且还要正确处理 sk_buff 内部信息。xdp_buff 没有这些问题，因为它所处的阶段非常早，此时内核还没有分配 sk_buff，因此很容易实现各种类型的数据包重写（packet rewrite）。 但是，xdp_buff 的缺点是在它这个阶段进行任意修改的时候，无法利用到 `sk_buff` 元数据。解决这个问题的方式是从 XDP BPF **传递自定义的元数据**到 tc BPF。这样，根据使用场景的不同，可以同时利用这两者 BPF 程序，以达到互补的效果。

	 注：XDP 可以修改数据包来切换协议。比如 TCP 转 UDP ，修改数据包的协议头部分即可。可以理解 XDP 可以工作在三层，tc 工作在三层半

- 	tc BPF 程序在数据路径上的 **`ingress`** 和 **`egress`** 点都可以触发；而 **XDP BPF 程序 只能在 ingress 点触发**。
	内核两个 hook 点：
   	1. ingress hook sch_handle_ingress()：由 **[`\_\_netif_receive_skb_core()`](https://elixir.bootlin.com/linux/latest/source/net/core/dev.c#L5111)** 触发
   	2. egress hook sch_handle_egress()：由 **[`\_\_dev_queue_xmit()`](https://elixir.bootlin.com/linux/latest/source/net/core/dev.c#L4049)** 触发

   	\_\_netif_receive_skb_core() 和 \_\_dev_queue_xmit() 是 data path 的主要接收和 发送函数，不考虑 XDP 的话（XDP 可能会拦截或修改，导致不经过这两个 hook 点）， 每个网络进入或离开系统的网络包都会经过这两个点，从而使得 tc BPF 程序具备完全可观测性。

-	tc BPF 程序不需要驱动做任何改动，因为它们运行在网络栈通用层中的 hook 点。因此，它们可以 attach 到任何类型的网络设备上。
	
	## ingress
	这提供了很好的灵活性，但跟运行在原生 XDP 层的程序相比，性能要差一些。然而，tc BPF 程序仍然是内核的通用 data path 做完 GRO 之后、且处理任何协议**之前**最早的 处理点。传统的 iptables 防火墙也是在这里处理的，例如 iptables PREROUTING 或 nftables ingress hook 或其他数据包包处理过程。 

	## egress
	类似的，对于 **egress**，tc BPF 程序在将包交给驱动之前的**最晚**的地方（latest point）执 行，这个地方在传统 iptables 防火墙 hook 之后（例如 iptables POSTROUTING）， 但在内核 GSO 引擎之前。

	唯一需要驱动做改动的场景是：将 tc BPF 程序 `offload` 到网卡。形式通常和 XDP offload 类似，只是特性列表不同，因为二者的 BPF 输入上下文、辅助函数和返回码（ verdict）不同。

## [`cls_bpf`](https://elixir.bootlin.com/linux/latest/source/net/sched/cls_bpf.c) 分类器

*注：[eBPF Offload to Hardware:
`cls_bpf` and XDP](https://netdevconf.info/1.2/slides/oct7/10_nic_viljoen_eBPF_Offload_to_Hardware__cls_bpf_and_XDP_finalised.pdf)*

运行在 tc 层的 BPF 程序是从 `cls_bpf` 分类器开始运行的。虽然 **tc 术语将 `BPF连接点`描述为`“分类器”`**，但这个词其实有点误导，因为它没有充分的描述了`cls_bpf`可以 做的事情。attachment point 是一个完全可编程的包处理器，不仅能够读取 skb 元数据和包数据，还可以任意修改这两者，并通过动作判决终止TC处理。因此，`cls_bpf` 可以认为是一个**管理和执行 tc BPF 程序的独立实体**。

`cls_bpf` 可以持有一个或多个 tc BPF 程序。Cilium 在部署 `cls_bpf` 程序时 ，对于一个给定的 hook 点只会附着一个程序，并且用的是 **直接操作（direct-action）** 模式。 典型情况下，在传统 tc 方案中，`分类器（classifier ）`和`操作模块（action modules）` 之间是分开的，每个分类器可以 attach 多个 action，当匹配到这个分类器时这些 action 就会执行。
在现代世界，在软件 data path 中使用 tc 做复杂包处理时这种模型**扩展性不好**。 考虑到附着到 `cls_bpf` 的 tc BPF 程序 是完全独立的，因此它们有效地将解析和 action 过程融合到了单个单元（unit）中。得益于 `cls_bpf` 的 `direct-action` 模式，它只需要返回 tc action 判决结果，然后立即 终止处理流水线。这使得能够在网络 data path 中实现可扩展可编程的包处理，避免动作的线性迭代。`cls_bpf` 是 tc 层中唯一支持这种快速路径（fast-path）的一个分类器模块。

和 XDP BPF 程序类似，tc BPF 程序能在运行时（runtime）通过 `cls_bpf` 原子地更新， 而不会导致**任何网络流量中断**，也不用重启服务。

`cls_bpf` 可以附着的 tc ingress 和 egress hook 点都是由一个名为 `sch_clsact` 的 伪 qdisc 管理的，它是 ingress qdisc 的一个超集（superset），可以无缝替换后 者，因为它既可以管理 ingress tc hook 又可以管理 egress tc hook。对于 `\_\_dev_queue_xmit()` 内的 tc egress hook，需要注意的是这个 hook 并不是在内核的 qdisc root lock 下执行的。因此，ingress 和 egress hook 都是在快速路径中以无锁（ lockless）方式执行的。不管是 ingress 还是 egress，抢占（preemption ）都被关闭， 并且执行发生在 `[RCU](https://zhuanlan.zhihu.com/p/30583695)` 读的一侧。

通常在 egress 的场景下，有很多类型的 qdisc 会 attach 到 netdevice，例如 `sch_mq`, `sch_fq`, `sch_fq_codel` 或者 `sch_htb`，其中某些是**有分类的 qdiscs**，这些 qdisc 包含**多个子类别**。 因此需要一个对包进行分类的机制，决定将包 **分离（demux）** 到哪里。这个机制是 由调用 `tcf_classify()` 实现的，这个函数会进一步调用 `tc 分类器`（如果提供了）。

在 这种场景下， `cls_bpf` 也可以被 attach 和使用。这种操作通常发生在 qdisc root lock 下面，因此会面临锁竞争的问题。 `sch_clsact` qdisc 的 egress hook 点位于**更前面**，没有落入这个锁的范围内，因此完全**独立于**常规 egress qdisc 而执行。 

因此对于 sch_htb 这种场景，`sch_clsact` qdisc 可以将繁重的数据包分类工作可通过 tc BPF 程序，在 qdisc root lock 之外执行，在这些 tc BPF 程序中设置 `skb->mark` 或 `skb->priority` ，因此随后 `sch_htb` 只需要一个简单的映射，而没有原来在 root lock 下面昂贵的包分类开销，还减少了锁竞争。

在 `sch_clsact` 结合 `cls_bpf` 场景下支持 Offloaded tc BPF 程序， 在这种场景下，原来加载到**智能网卡驱动**的 BPF 程序被 JIT，在网卡原生执行。 只有工作在 direct-action 模式的 `cls_bpf` 程序支持 offload。 `cls_bpf` 只支持 offload 单个程序，不支持**同时** offload 多个程序。另外，**只有 ingress hook 支持 offloading BPF 程序**。

一个 `cls_bpf` 实例内部可以持有多个 tc BPF 程序。如果由多个程序， `TC_ACT_UNSPEC` 程序返回码就是让继续执行列表中的下一个程序。但这种方式的缺点是： **每个程序都需要解析一遍数据包，性能会下降**。

# BPF 程序返回码

tc ingress 和 egress hook 共享相同的返回码（动作判决），定义在 linux/pkt_cls.h 系统头文件：

```c
#define TC_ACT_UNSPEC         (-1)
#define TC_ACT_OK               0
#define TC_ACT_SHOT             2
#define TC_ACT_STOLEN           4
#define TC_ACT_REDIRECT         7
```

系统头文件中还有一些 `TC_ACT_*` 动作判决，也用在了这两个 hook 中。但是，这些判决 和上面列出的那几个共享相同的语义。这意味着，从 tc BPF 的角度看， `TC_ACT_OK` 和 `TC_ACT_RECLASSIFY` 有相同的语义， `TC_ACT_STOLEN`, `TC_ACT_QUEUED` and `TC_ACT_TRAP` 返回码也是类似的情况。因此， 对于这些情况，我们只描述 `TC_ACT_OK` 和 `TC_ACT_STOLEN` 操作码。

## `TC_ACT_UNSPEC` 和 `TC_ACT_OK`

`TC_ACT_UNSPEC` 表示“未指定的动作”（unspecified action），在三种情况下会用到：

1. attach 了一个 offloaded tc BPF 程序，tc ingress hook 正在运行，被 offload 的 程序的 `cls_bpf` 表示会返回 `TC_ACT_UNSPEC`
2. 为了在 `cls_bpf` 多程序的情况下，继续下一个 tc BPF 程序。这种情况可以和第一种情况中提到的 offloaded tc BPF 程序一起使用，将继续运行在不是 offloaded 下的 tc BPF 程序，并返回 `TC_ACT_UNSPEC`
3. `TC_ACT_UNSPEC` 还用于单个程序从场景，只是通知内核继续执行 `skb` 处理，但不要带 来任何副作用（without additional side-effects）。

TC_ACT_UNSPEC 在某些方面和 `TC_ACT_OK` 非常类似，因为二者都是将 `skb` 向下一个 处理阶段传递，在 `ingress` 的情况下是传递给内核协议栈的更上层，在 egress 的情况下 是传递给网络设备驱动。**唯一的不同是 `TC_ACT_OK` `基于 tc BPF 程序`设置的 classid 来设置 skb->tc_index**，而 `TC_ACT_UNSPEC` 是通过 tc BPF 程序之外的 BPF 上下文中的 `skb->tc_classid` 设置。


## `TC_ACT_SHOT` 和 `TC_ACT_STOLEN`

这两个返回码指示内核将包丢弃。这两个返回码很相似，只有少数几个区别：

- `TC_ACT_SHOT` 提示内核 `skb` 是通过 `kfree_skb()` 释放的，并**返回 `NET_XMIT_DROP` 给调用方**，作为立即反馈
- `TC_ACT_STOLEN` 通过 consume_skb() 释放 `skb`，**返回 `NET_XMIT_SUCCESS` 给上层`假装`这个包已经被正确发送了**


## TC_ACT_REDIRECT

这个返回码加上 bpf_redirect() 辅助函数，允许重定向一个 `skb` 到同一个或另一个设备的 `ingress` 或 `egress` 路径。能够将包注入另一个设备的 `ingress` 或 `egress` 路径使 得基于 BPF 的包转发具备了完全的灵活性。对目标网络设备没有额外的要求，**只要本身是一个网络设备就行了**，在目标设备上不需要运行 `cls_bpf` 实例或其他限制。


---

# 文档连接
[Linux BPF 3.2、BPF and XDP Reference Guide](https://www.dazhuanlan.com/2019/12/10/5dee76b007da0/)
[[译] Cilium：BPF 和 XDP 参考指南（2019）](http://arthurchiao.art/blog/cilium-bpf-xdp-reference-guide-zh/#bpf_instruction)
[BPF and XDP Reference Guide](https://docs.cilium.io/en/stable/bpf/)
[BPF 辅助函数](https://man7.org/linux/man-pages/man7/bpf-helpers.7.html)
[BPF man 文档](https://man7.org/linux/man-pages/man2/bpf.2.html)
[Using Hairpinning in a Network](https://docs.fortinet.com/document/fortigate/5.4.0/cookbook/105831)
[BPF cpumap](https://lwn.net/Articles/736336/)
[Top-level XDP project management](https://xdp-project.net/)