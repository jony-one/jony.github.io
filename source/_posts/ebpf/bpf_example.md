---
title: 7. eBPF 应用案例
date: 2021-02-25 13:14:19
categories: 
	- [eBPF]
tags:
  - ebpf
author: Jony
---


# tc BPF FAQ

本节列出一些经常被问的、与 tc BPF 程序有关的问题。

- **用 act_bpf 作为 tc action module 怎么样，现在用的还多吗？**
	
	不多。虽然对于 tc BPF 程序来说 cls_bpf 和 act_bpf 有相同的功能 ，但前者更加灵活，因为它是后者的一个超集（superset）。tc 的工作原理是将 tc actions attach 到 tc 分类器。要想实现与 cls_bpf 一样的灵活性，act_bpf 需要 被 attach 到 cls_matchall 分类器。如名字所示，为了将包传递给 attached tc action 去处理，这个分类器会匹配每一个包。相比于工作在 direct-action 模式的 cls_bpf，act_bpf 这种方式会导致较低的包处理性能。如果 act_bpf 用在 cls_bpf or cls_matchall 之外的其他分类器，那性能会更差，这是由 tc 分类器的 操作特性（nature of operation of tc classifiers）决定的。同时，如果分类器 A 未 匹配，那包会传给分类器 B，B 会重新解析这个包以及重复后面的流量，因此这是一个线 性过程，在最坏的情况下需要遍历 N 个分类器才能匹配和（在匹配的分类器上）执行 act_bpf。因此，act_bpf 从未大规模使用过。另外，和 cls_bpf 相比， act_bpf 也没有提供 tc offload 接口。

- **是否推荐在使用 cls_bpf 时选择 direct-action 之外的其他模式?**
	
	不推荐。原因和上面的问题类似，选择其他模式无法应对更加复杂的处理情况。tc BPF 程序本身已经能以一种高效的方式做任何处理，因此除了 direct-action 这个模式 之外，不需要其他的任何东西了。

- **offloaded cls_bpf 和 offloaded XDP 有性能差异吗？**

	没有。二者都是由内核内的同一个编译器 JIT 的，这个编译器负责 offload 到智能网 卡以及，并且对二者的加载机制是非常相似的。因此，要在 NIC 上原生执行，BPF 程 序会被翻译成相同的目标指令。

	tc BPF 和 XDP BPF 这两种程序类型有不同的特性集合，因此根据使用场景的不同，你 可以选择 tc BPF 或者是 XDP BPF，例如，二者的在 offload 场景下的辅助函数可能 会有差异。

---

# tc BPF 使用案例

本节列出了 tc BPF 程序的主要使用案例。但要注意，这里列出的并不是全部案例，而且考 虑到 tc BPF 的可编程性和效率，人们很容易对它进行定制化（tailor）然后集成到编排系 统，用来解决特定的问题。XDP 的一些案例可能有重叠，但 tc BPF 和 XDP BPF 大部分情 况下都是互补的，可以单独使用，也可以同时使用，就看哪种情况更适合解决给定的问题了 。

- **为容器落实策略（Policy enforcement）**

	tc BPF 程序适合用来给容器实现安全策略、自定义防火墙或类似的安全工具。在传统方式中，容器隔离是通过网络命名空间时实现的，veth pair 的一端连接到宿主机的初始命 名空间，另一端连接到容器的命名空间。因为 veth pair 的一端移动到了容器的命名空间，而另一端还留在宿主机上（默认命名空间），容器所有的网络流量都需要经过主机端的 veth 设备，因此可以在这个 veth 设备的 tc ingress 和 egress hook 点 attach tc BPF 程序。目标地址是容器的网络流量会经过主机端的 veth 的 tc egress hook，而从容器出来的网络流量会经过主机端的 veth 的 tc ingress hook。

	对于像 veth 这样的虚拟设备，XDP 在这种场景下是不合适的，因为内核在这里只操作 skb，而通用 XDP 有几个限制，导致无法操作克隆的 skb。而克隆 skb 在 TCP/IP 协议栈中用的非常多，目的是持有（hold）准备重传的数据片（data segments），而通 用 XDP hook 在这种情况下回被直接绕过。另外，generic XDP 需要顺序化（linearize ）整个 skb 导致严重的性能下降。相比之下， tc BPF 非常灵活，因为设计中它就是工作在接 收 skb 格式的输入上下文中，因此没有 generic XDP 遇到的那些问题。

- **转发和负载均衡**

	转发和负载均衡的使用场景和 XDP 很类似，只是目标更多的是在东西向容器流量而不是 南北向（虽然两者都可以用于东西向或南北向场景）。XDP 只能在 ingress 方向使用， tc BPF 程序还可以在 egress 方向使用，例如，可以在初始命名空间内（宿主机上的 veth 设备上），通过 BPF 对容器的 egress 流量同时做地址转化（NAT）和负载均衡， 整个过程对容器是透明的。由于在内核网络栈的实现中，egress 流量已经是 sk_buff 形式的了，因此很适合 tc BPF 对其进行重写（rewrite）和重定向（redirect）。 使用 bpf_redirect() 辅助函数，BPF 就可以接管转发逻辑，将包推送到另一个网络设 备的 ingress 或 egress 路径上。因此，有了 tc BPF 程序实现的转发网格（ forwarding fabric），网桥设备都可以不用了。

- **流抽样（Flow sampling）、监控**

	和 XDP 类似，可以通过高性能无锁 per-CPU 内存映射 perf 环形缓冲区（ring buffer ）实现流抽样（flow sampling）和监控，在这种场景下，BPF 程序能够将自定义数据、 全部或截断的包内容或者二者同时推送到一个用户空间应用。在 tc BPF 程序中这是通过 bpf_skb_event_output() BPF 辅助函数实现的，它和 bpf_xdp_event_output() 有相 同的函数签名和语义。

	考虑到 tc BPF 程序可以同时 attach 到 ingress 和 egress，而 XDP 只能 attach 到 ingress，另外，这两个 hook 都在（通用）网络栈的更低层，这使得可以监控每台节点 的所有双向网络流量。这和 tcpdump 和 Wireshark 使用的 cBPF 比较相关，但是，不 需要克隆 skb，而且因为其可编程性而更加灵活，例如。BPF 能够在内核中完成聚合 ，而不用将所有数据推送到用户空间；也可以对每个放到 ring buffer 的包添加自定义 的 annotations。Cilium 大量使用了后者，对被 drop 的包进一步 annotate，关联到 容器标签以及 drop 的原因（例如因为违反了安全策略），提供了更丰富的信息。

- **包调度器预处理**（Packet scheduler pre-processing）
	sch_clsact’s egress hook 被 sch_handle_egress() 调用，在获得内核的 qdisc root lock 之前执行，因此 tc BPF 程序可以在包被发送到一个真实的 full blown qdis （例如 sch_htb）之前，用来执行包分类和 mangling 等所有这些高开销工作。 这种 sch_clsact 和后面的发送阶段的真实 qdisc（例如 sch_htb） 之间的交互， 能够减少发送时的锁竞争，因为 sch_clsact 的 egress hook 是在无锁的上下文中执行的。

同时使用 tc BPF 和 XDP BPF 程序的一个具体例子是 Cilium。Cilium 是一个开源软件， 透明地对（K8S 这样的容器编排平台中的）容器之间的网络连接进行安全保护，工作在 L2/L3/L4/L7。Cilium 的核心基于 BPF，用来实现安全策略、负载均衡和监控。

---


XDP BPF 在生产环境使用的一个例子是 Facebook 的 SHIV 和 Droplet 基础设施，实现了 它们的 L4 负载均衡和 DDoS 测量。从基于 netfilter 的 IPV（IP Virtual Server）迁移到 XDP BPF 使它们的生产基础设施获得了 10x 的性能提升。这方面 的工作最早在 netdev 2.1 大会上做了分享：
- [演讲 Slides](https://www.netdevconf.org/2.1/slides/apr6/zhou-netdev-xdp-2017.pdf)
- [演讲视频](https://youtu.be/YEU2ClcGqts)

另一个例子是 Cloudflare 将 XDP 集成到它们的 DDoS 防御流水线中，替换了原来基于 cBPF 加 iptables 的 xt_bpf 模块所做的签名匹配（signature matching）。 基于 iptables 的版本在发生攻击时有严重的性能问题，因此它们考虑了基于用户态、 bypass 内核的一个方案，但这种方案也有自己的一些缺点，并且需要不停轮询（busy poll ）网卡，并且在将某些包重新注入内核协议栈时代价非常高。迁移到 eBPF/XDP 之后，两种 方案的优点都可以利用到，直接在内核中实现了高性能、可编程的包处理过程：
- [演讲 Slides](https://www.netdevconf.org/2.1/slides/apr6/bertin_Netdev-XDP.pdf)
- [演讲视频](https://youtu.be/7OuOukmuivg)
