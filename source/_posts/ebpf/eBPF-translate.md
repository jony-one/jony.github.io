---
title: 2. eBPF 翻译文档合集
date: 2021-02-22 10:01:25
categories: 
	- [eBPF]
tags:
  - ebpf
author: Jony

---

	注意：本文档部分针对的是希望深入了解BPF和XDP的开发人员和用户。尽管阅读本参考指南可能有助于拓宽你对Cilium的理解，但使用Cilium并不是必须的。请参考入门指南和概念以获得更高级别的介绍。

BPF是Linux内核中高度灵活和高效的类似虚拟机的构造，允许以安全的方式在各种挂起点执行字节码。它被用于许多Linux内核子系统，最突出的是networking、tracing和安全(例如沙箱)。

尽管BPF自1992年以来就存在，但本文介绍了扩展的Berkeley Packet Filter (eBPF)版本，该版本首次出现在内核3.18中，并呈现了最近被称为“classic"BPF (cBPF)的原始版本。cBPF以tcpdump使用的包过滤语言而闻名。现在，Linux内核只运行eBPF，并且在程序执行之前将加载的cBPF字节码透明地转换为内核中的eBPF表示。除非明确指出eBPF和cBPF之间的区别，否则本文档一般将使用BPF这个术语。

尽管 Berkeley Packet Filter的名称暗示了包过滤的特定用途，但是指令集是通用的，并且非常灵活，除了网络之外，BPF还有很多用例。有关使用BPF的项目列表，请参阅进一步阅读。

Cilium 在其数据路径中大量使用BPF，有关更多信息，请参阅概念。本章的目标是提供一个BPF参考指南，以了解BPF，它的网络特定用途，包括加载BPF程序与tc((traffic control)和XDP (eXpress Data Path)，并协助开发Cilium的BPF模板。

BPF不仅仅定义了自己的指令集，还提供了更进一步的框架。包括：maps(高效的key/value存储)、helper functions(调用内核函数)、tail calls(调用其他的BPF程序)、security hardening primitives(安全性加强原语)、一个伪文件系统为了pinning目标(maps、programs)、还允许BPF的offloaded(例如，offload到一个网卡)。	

LLVM提供了一个BPF后端，这样就可以使用像clang这样的工具将C编译成BPF对象文件，然后可以将其加载到内核中。BPF与Linux内核紧密相连，允许在不牺牲本机内核性能的情况下进行完全的编程。

最后，使用BPF的内核子系统也是BPF基础设施的一部分。本文档中讨论的两个主要子系统是tc和XDP, BPF程序可以附加到这两个子系统中。XDP BPF程序是在最早的网络驱动阶段附加的，在数据包接收时触发BPF程序运行。根据定义，这可以获得最佳的包处理性能，因为包在软件中甚至不能在更早的时候被处理。然而，由于这种处理发生在网络堆栈的早期，堆栈还没有从包中提取元数据。另一方面，tc BPF程序稍后在内核堆栈中执行，因此它们可以访问更多的元数据和核心内核功能。除了tc和XDP程序之外，还有许多其他使用BPF的内核子系统，如trace (kprobes, uprobes, tracepoint等)。

下面的小节将进一步详细介绍BPF体系结构的各个方面。

# 1.1 Instruction Set

---

BPF是一个通用的RISC指令集，最初的设计目的是可以使用一个C语言的子集写程序并且可以通过一个编译器后端(例如LLVM)编译成BPF指令，这样内核就可以稍后通过内核JIT编译器将它们映射到本机操作码优化内核内部的执行性能。

将这些指令推入内核的优点包括:

- 使内核可编程，而不必跨越内核/用户空间边界。例如，networking相关的BPF程序，如Cilium，可以实现灵活的 container policies, load balancing and other means，而无需将包移动到用户空间并返回到内核。当需要时，BPF程序和内核/用户空间之间的状态仍然可以通过映射共享。

- 考虑到可编程数据路径的灵活性，还可以通过编译去掉不需要的特性来大大优化程序的性能。例如，如果一个container不需要IPv4，那么BPF程序可以构建为只处理IPv6，以便在快速通道中节省资源。

- 假设networking应用(例如tc和XDP)， BPF程序可以自动更新，而无需重新启动内核、系统服务或容器，也无需中断数据传输。此外，任何程序状态也可以通过BPF maps在整个更新过程中维护。
- BPF为用户空间提供了一个稳定的ABI，不需要任何第三方内核模块。BPF是到处发布的Linux内核的核心部分，它保证了现有的BPF程序可以在新的内核版本中继续运行。这种保证与内核为用户空间应用程序的系统调用提供的保证相同。此外，BPF程序可以跨不同的体系结构进行移植。
- BPF程序与内核协同工作，它们利用现有的内核基础设施(例如drivers, netdevices, tunnels, protocol stack, sockets)和工具(例如iproute2)以及内核提供的安全保证。和内核模块不同,BPF程序验证通过内核校验以确保他们不能崩溃内核,总能终止,等。XDP项目,例如,重用现有的内核驱动程序和操作提供DMA缓冲区包含包数据，不暴露他们或整个驱动到用户空间。此外，XDP程序重用现有的堆栈，而不是绕过它。BPF可以被看作是一种通用的“粘合代码"，用于编写用于解决特定用例的程序。

在内核中执行BPF程序总是由事件驱动的!例如,一个网络设备具有BPF程序附在其入口路径，收到数据包将触发程序的执行一次；内核地址有kprobes附带一个BPF程序，一旦地址的代码被执行,然后调用kprobes回调函数随后触发执行BPF程序。

BPF由11个64位寄存器(包含32位子寄存器)、PC、512字节的堆栈空间组成。寄存器命名为 r0 - r10。操作模式默认为64位，32位子寄存器只能通过特殊的ALU(算术逻辑单元)操作访问。32位以下的子寄存器在写入时从0扩展到64位。

寄存器r10是唯一的只读寄存器，它包含堆栈指针地址，以便访问BPF堆栈空间。其余的r0 - r9寄存器是一般用途和读写性质。

BPF程序可以调用预定义的helper function，这是由核心内核(而不是模块)定义的。BPF调用规范定义如下:

	- r0 存放被调用的辅助函数的返回值
	- r1 - r5 存放 BPF 调用内核辅助函数时传递的参数
	- r6 - r9 由被调用方（callee）保存，在函数返回之后调用方（caller）可以读取

BPF调用规范足够通用，可以直接映射到x86_64、arm64和其他ABIs，因此所有 BPF 寄存器可以一一映射到硬件 CPU 寄存器，这样JIT只需发出一个调用指令，而不需要额外的移动来放置函数参数。这种调用规范的建模是为了涵盖常见的调用情况，而不会造成性能损失。当前不支持带有6个或更多参数的调用。内核中专门用于BPF (BPF_CALL_0()到BPF_CALL_5()函数的helper函数是专门根据这种规范设计的。

寄存器r0也是包含BPF程序退出值的寄存器。退出值的语义由程序的类型定义。此外，当将执行返回给内核时，退出值作为32位值传递。

寄存器r1 - r5是暂存寄存器，这意味着BPF程序需要将它们临时转储（spill）到BPF堆栈中，或者将它们移动到被调用方保存的寄存器中，如果这些参数要跨多个辅助函数调用重用的话。spilling意味着寄存器中的变量被移动到BPF堆栈中。将变量从BPF堆栈移到寄存器的反向操作称为filling。spilling/filling的原因是寄存器数量有限。

在执行BPF程序时，寄存器r1最初包含程序的context。context是程序的输入参数(类似于典型C程序的argc/argv对)。BPF仅限于在单个context中工作。context由程序类型定义，例如，网络程序可以将网络数据包(skb)的内核表示形式作为输入参数。

BPF的一般操作是64位，遵循64位体系结构的自然模型，以便执行指针算术运算，传递指针，也向helper functions传递64位值，并允许64位原子操作。

每个程序的最大指令限制限制在4096 BPF指令，这意味着任何程序都会很快终止。虽然指令集包含前向跳转和后向跳转，但是内核中的BPF验证器将禁止循环，保证程序总是能终止。由于BPF程序在内核中运行，verifier的工作是确保这些程序能够安全运行，而不影响系统的稳定性。这意味着从指令集的观点来看，循环是可以实现的，但是verifier会限制它。然而，也有一个tail call的概念，允许一个BPF程序跳转到另一个BPF程序。这也带来了32个调用的嵌套上限。tail call通常用于将程序逻辑的一部分解耦，例如，分解成阶段。

指令格式建模为两个操作数指令，这有助于在JIT阶段将BPF指令映射到本机指令。指令集是固定大小的，这意味着每条指令都有64位编码。目前，已经实现了87条指令，编码还允许在需要时使用进一步的指令扩展集合。在big-endian机上一条64位指令的指令编码定义为从最有效位(MSB)到最低有效位(LSB)的位序列，**`“op:8, dst_reg:4, src_reg:4, off:16, imm:32"`**。off和imm是有符号类型。编码是内核头文件的一部分，在linux/bpf.h头文件中定义。其中还包括linux/bpf_common.h。

“op"定义要执行的实际操作。大多数用于op的编码都被cBPF重用。操作可以基于寄存器或直接操作数。op本身的编码提供了使用哪种模式的信息
	- BPF_X分别表示基于寄存器的操作
	- BPF_K分别表示基于立即的操作

在后一种情况下，目标操作数总是一个寄存器。dst_reg和src_reg都提供了关于用于操作的寄存器操作数(例如r0 - r9)的附加信息。"off"在一些指令中用于提供相对偏移量，例如，用于处理BPF可用的堆栈或其他缓冲区(例如map value、包数据等)，或跳转指令中的跳转目标。imm包含一个常量/即时值。


可用的"op"指令可以分为不同的指令类。这些类也编码在op字段中。op字段分为(从MSB到LSB)`code:4`, `source:1` 和 `class:3`
	- class 是指令类型
	- code 指特定类型的指令中的某种特定操作码（operational code）
	- source 可以告诉我们源操作数（source operand）是一个寄存器还是一个立即数

可能的指令类包括:

- `BPF_LD`, `BPF_LDX`：**加载操作（load operations）**
	- `BPF_LD` 用于加载 **double word 长度的特殊指令**（占两个指令长度，源于 imm:32 的限制），或byte / half-word / word 长度的包数据（packet data ）后者主要是为了保持cBPF转换为BPF的效率，因为他们已经优化了JIT代码。对于 native BPF 来说，这些包加载指令在今天已经 用的很少了。
	- `BPF_LDX` 用于从内存中加载 byte / half-word / word / double-word，这里的内存包括栈内存、map value data、packet data 等等。

- `BPF_ST`, `BPF_STX`：**存储操作（store operations）**
	- `BPF_STX` 与 `BPF_LDX` 相对，将某个寄存器中的值存储到内存中，同样，这里的 内存也可以是栈内存、map value、packet data 等等。BPF_STX 类包含一些 word 和 double-word 相关的原子加操作，例如，可以用于计数器。
	- `BPF_ST类` 与 `BPF_STX` 指令类似，提供了将源操作数（source operand）作为立即值（immediate value）存储到内中。

- BPF_ALU, BPF_ALU64：逻辑运算操作（ALU operations），BPF_ALU操作是32位模式，BPF_ALU64是64位模式。这两个ALU类的源操作数具有基于寄存器或基于即时操作数的模式。支持`add (+), sub (-), and (&), or (|), left shift (<<), right shift (>>), xor (^), mul (*), div (/), mod (%), neg (~) `操作。
	- "mov ( := )"作为一种特殊的ALU操作添加到这两个类的操作模式中。
	- BPF_ALU64包含符号右移位
	- BPF_ALU还包含在给定源寄存器中half-word/word/double-word的字节序转换指令

- BPF_JMP：跳转操作（jump operations）
	- 跳跃可以是无条件的，也可以是有条件的。
		- 无条件跳转只是将程序计数器向前移动，相对于当前指令执行的下一条指令是off + 1，其中off是指令中编码的常量偏移量。因为off是有符号的，所以跳转也可以向后执行，只要它不创建循环并且在程序范围内。
		- 条件跳转对基于寄存器和基于立即的源操作数都进行操作。如果跳转操作中的条件是true，则执行相对跳转到off + 1，否则执行下一条指令(0 + 1)。
	- 与cBPF相比，这种fall-through跳转逻辑是不同的，并且允许更好的分支预测，因为它更自然地适合CPU分支预测逻辑。可用条件有 `jeq (==), jne (!=), jgt (>), jge (>=), jsgt (signed >), jsge (signed >=), jlt (<), jle (<=), jslt (signed <), jsle (signed <=) and jset (jump if DST & SRC)` 。除此之外,有三个特殊的跳转操作在这个类:exit 指令,将带通滤波器程序并返回当前值在r0返回代码；call指令,这将发出一个函数调用到一个可用的BPF helper function；和一个隐藏的tail call指令,进入一个不同的BPF程序。

**Linux 内核中内置了一个 BPF 解释器**，该解释器能够执行由 BPF 指令组成的程序。即使是 cBPF 程序，也可以在内核中透明地转换成 eBPF 程序，除非该架构仍然内置了 cBPF JIT，还没有迁移到 eBPF JIT。

目前下列架构都内置了内核 eBPF JIT 编译器：x86_64、arm64、ppc64、s390x 、mips64、sparc64 和 arm。

所有的 BPF 操作，例如加载程序到内核，或者创建 BPF map，都是通过核心的 bpf() 系统调用完成的。它还用于管理 map 表项（查找/更新/删除），以及通过 pinning（钉住 ）将程序和 map 持久化到 BPF 文件系统。

----

# 1.2 Helper Functions

简单来说就是BPF程序调用kernel的函数。

辅助函数（Helper functions）使得 BPF 能够通过内核定义的一系列函数调用（function call）从内核中查询数据，或者将数据推送到内核。不同类型的 BPF 程序能够使用的 辅助函数可能是不同的，例如，与连接到 tc 层的 BPF 程序相比，连接到 socket 的 BPF程序只能够调用前者可以调用的辅助函数的一个子集。另外一个例子是，**轻量级隧道**（lightweight tunneling ）的 **封装和解封装（Encapsulation and decapsulation）** 辅助函数，只能被 **更低级 tc 层（lower tc layers）** 使用；而推送通知到 用户态所使用的事件输出辅助函数，既可以被 tc 程序使用也可以被 XDP 程序使用。

所有的辅助函数都共享同一个通用的、和系统调用类似的函数签名。签名定义如下：
	u64 fn(u64 r1, u64 r2, u64 r3, u64 r4, u64 r5)
前一节介绍的调用规范适用于所有的 BPF 辅助函数。

内核将辅助函数抽象为与系统调用类似的宏BPF_CALL_0()到BPF_CALL_5()。下面的示例是从一个辅助函数中提取的，该函数通过调用相应的回调来更新map elements:

```c
BPF_CALL_4(bpf_map_update_elem, struct bpf_map *, map, void *, key,
           void *, value, u64, flags)
{
    WARN_ON_ONCE(!rcu_read_lock_held());
    return map->ops->map_update_elem(map, key, value, flags);
}

const struct bpf_func_proto bpf_map_update_elem_proto = {
    .func           = bpf_map_update_elem,
    .gpl_only       = false,
    .ret_type       = RET_INTEGER,
    .arg1_type      = ARG_CONST_MAP_PTR,
    .arg2_type      = ARG_PTR_TO_MAP_KEY,
    .arg3_type      = ARG_PTR_TO_MAP_VALUE,
    .arg4_type      = ARG_ANYTHING,
};
```
在 eBPF 中，JIT 编译器会以一种透明和高效的方式编译新加入的辅助函数，这意味着 JIT 编译器只需要发射（emit）一条调用指令（call instruction），因为寄存器映射的方式使得 BPF 排列参数的方式（assignments）已经和底层架构的调用规范相匹配了。这使得基于辅助函数扩展核心内核（core kernel）非常方便。所有的 BPF 辅助函数都是核心内核的一部分，无法通过内核模块（kernel module）来扩展或添加。

前面提到的函数签名还允许校验器执行类型检测（type check）。例如 struct bpf_func_proto 用于存放`校验器`必需知道的所有关于该辅助函数的信息，这样校验器才可以确保辅助函数期望的类型和 BPF 程序寄存器中的当前内容是匹配的。

辅助方法的参数类型范围很广，可以时任意类型，也可以限制为特定类型（例如 BPF 栈缓冲区（stack buffer）的 pointer/size 参数对），辅助函数可以从这个位置读取数据或向其写入数据。 对于这种情况，校验器还可以执行额外的检查，例如，缓冲区是否已经初始化过了。

当前可用的 BPF 辅助函数已经有几十个，并且数量还在不断增加，例如，写作本文时，tc BPF 程序可以使用38 种不同的 BPF 辅助函数。对于一个给定的 BPF 程序类型，内核的 struct bpf_verifier_ops 包含了 get_func_proto 回调函数，这个函数提供了从某个 特定的enum bpf_func_id 到一个可用的辅助函数的映射。

---

# 1.3 Maps
![Maps](/jony.github.io/images/bpf_map.png)
map 是驻留在内核空间中的高效键值仓库（key/value store）。map 中的数据可以被 BPF 程序访问，如果想在多个 BPF 程序调用（invoke）之间保存状态，可以将状态信息放到 map。map 还可以从用户空间通过文件描述符访问，可以在任意 BPF 程序以及用户空间应用之间共享。

共享 map 的 BPF 程序不要求是相同的程序类型，例如 tracing 程序可以和网络程序共享 map。单个 BPF 程序目前最多可直接访问 64 个不同 map。

map 的实现由核心内核（core kernel）提供。有 per-CPU 及 non-per-CPU 的通用 map，这些 map 可以读/写任意数据，也有一些和辅助函数一起使用的非通用 map。

当前可用的 通用 map 有：

	- BPF_MAP_TYPE_HASH
	- BPF_MAP_TYPE_ARRAY
	- BPF_MAP_TYPE_PERCPU_HASH
	- BPF_MAP_TYPE_PERCPU_ARRAY
	- BPF_MAP_TYPE_LRU_HASH
	- BPF_MAP_TYPE_LRU_PERCPU_HASH
	- BPF_MAP_TYPE_LPM_TRIE
以上 map 都使用相同的一组 BPF 辅助函数来执行查找、更新或删除操作，但各自实现了不 同的后端，这些后端各有不同的语义和性能特点。

当前内核中的 非通用 map 有：

	- BPF_MAP_TYPE_PROG_ARRAY
	- BPF_MAP_TYPE_PERF_EVENT_ARRAY
	- BPF_MAP_TYPE_CGROUP_ARRAY
	- BPF_MAP_TYPE_STACK_TRACE
	- BPF_MAP_TYPE_ARRAY_OF_MAPS
	- BPF_MAP_TYPE_HASH_OF_MAPS

例如，BPF_MAP_TYPE_PROG_ARRAY 是一个数组 map，用于保存其他的 BPF 程序 。BPF_MAP_TYPE_ARRAY_OF_MAPS 和 BPF_MAP_TYPE_HASH_OF_MAPS 都用于保存其他 map 的指针，这样整个 map 就可以在运行时实现原子替换。这些类型的 map 都针对 特定的问题，不适合单单通过一个 BPF 辅助函数实现，因为它们需要在各次 BPF 程序调用 （invoke）之间时保持额外的（非数据）状态。

--- 

# 1.4 Object Pinning
![Object Pinning](/jony.github.io/images/bpf_fs.png)
BPF map和程序作为内核资源只能通过由内核中的匿名节点支持的文件描述符访问。有优点，但也伴随着一些缺点:

**用户空间应用程序可以使用大多数与文件描述符相关的api, Unix域套接字传递的文件描述符可以透明地工作**，等等，但是同时，**文件描述符被限制在进程的生命周期内，这使得像map共享这样的选项执行起来相当麻烦**。

因此，这给某些特定的场景带来了很多复杂性，例如 iproute2，其中的 tc 或 XDP 在准备 环境、加载程序到内核之后最终会**退出**。在这种情况下，从用户空间也无法访问这些 map 了，而本来这些 map 其实是很有用的，例如，在 data path 的 ingress 和 egress 位置共 享的 map（可以统计包数、字节数、PPS 等信息）。另外，第三方应用可能希望在 BPF 程 序运行时监控或更新 map。

为了解决这个问题，内核实现了一个最小内核空间 BPF 文件系统，BPF map 和 BPF 程序 都可以固定到这个文件系统上，这个过程称为 object pinning。相应地，BPF 系统调用进行了扩展，添加了两个新命令，分别用于固定（BPF_OBJ_PIN）或者获取（BPF_OBJ_GET）以前的固定住的对象（pinned objects）。

---

# 1.5 Tail Calls
![Tail Calls](/jony.github.io/images/bpf_tailcall.png)

BPF 相关的另一个概念是尾调用（tail calls）。尾调用的机制是：一个 BPF 程序可以调 用另一个 BPF 程序，并且调用完成后不用返回到原来的程序。和普通函数调用相比，这种 调用方式开销最小，因为它是用长跳转（long jump）实现的，复用了原来的栈帧 （stack frame）。

BPF 程序都是独立验证的，因此要传递状态，要么使用 per-CPU map 作为 暂存(scratch) 缓冲区 ，要么如果是 tc 程序的话，还可以使用 skb 的某些字段（例如 cb[]）。

相同类型的程序才可以尾调用，而且它们还要与 JIT 编译器相匹配，因此要么是 JIT 编译执行，要么是解释器执行（invoke interpreted programs），但只能选其一，不能混合在一起。。

尾调用执行涉及两个步骤：

	1. 设置一个称为“程序数组”（program array）的特殊 map（BPF_MAP_TYPE_PROG_ARRAY ），这个 map 可以从用户空间通过操作 key/value ，value就是tail calle调用的BPF 程序的文件描述符
	2. 调用辅助函数 bpf_tail_call()。将上下文、对程序数组的引用和要查找的 key 传递给该辅助函数。内核将这个辅助函数调用内联（ inline）到一个特殊的 BPF 指令内。目前，这样的程序数组在用户空间侧是只写模式。

内核根据传入的文件描述符查找相关的 BPF 程序，原子性地替换给定的 map slot（槽） 处的程序指针。如果没有找到给定的 key 对应的 value，内核会跳过（fall through）这一步 ，继续执行 bpf_tail_call() 后面的旧程序。尾调用是一个强大的功能，例如，可以通 过尾调用结构化地解析网络头（network headers）。还可以在运行时（runtime）原子地 添加或替换功能，即，动态地改变 BPF 程序的执行行为。

---

# 1.6 BPF to BPF Calls

![BPF to BPF Calls](/jony.github.io/images/bpf_call.png)
除了 BPF 辅助函数和 BPF 尾调用之外，BPF 核心基础设施最近刚加入了一个新特性：BPF 到 BPF 调用（BPF to BPF calls）。在这个特性引入内核之前，典型的 BPF C 程序必须 将所有需要复用的代码进行特殊处理，例如，在头文件中声明为 always_inline。当 LLVM 编译和生成 BPF 对象文件时，所有这些函数将被内联，因此会在生成的对象文件中重复多次，导致代码尺寸膨胀：
```c
#include <linux/bpf.h>

#ifndef __section
# define __section(NAME)                  \
   __attribute__((section(NAME), used))
#endif

#ifndef __inline
# define __inline                         \
   inline __attribute__((always_inline))
#endif

static __inline int foo(void)
{
    return XDP_DROP;
}

__section("prog")
int xdp_drop(struct xdp_md *ctx)
{
    return foo();
}

char __license[] __section("license") = "GPL";
```
之所以要这样做是因为 BPF 程序的加载器、校验器、解释器和 JIT 中都缺少对函数调用的 支持。从 Linux 4.16 和 LLVM 6.0 开始，这个限制得到了解决，BPF 程序不再需 要到处使用 always_inline 声明了。因此，上面的代码可以更自然地重写为：
```c
#include <linux/bpf.h>

#ifndef __section
# define __section(NAME)                  \
   __attribute__((section(NAME), used))
#endif

static int foo(void)
{
    return XDP_DROP;
}

__section("prog")
int xdp_drop(struct xdp_md *ctx)
{
    return foo();
}

char __license[] __section("license") = "GPL";
```
BPF 到 BPF 调用是一个重要的性能优化，极大减小了生成的 BPF 代码大小，因此对 CPU 指令缓存更友好。
**BPF 辅助函数的调用约定也适用于 BPF 函数间调用，这意味着r1到r5是用来向被调用方传递参数的，`结果在r0中返回`。**
r1 - r5 是 暂存寄存器(scratch registers)，r6 - r9 像往常一样是保留寄存器。最大嵌套调用深度是 8。调用方可以传递指针（例如，指向调用方的栈帧的指针） 给被调用方，但反过来不行。

~~当前，BPF 函数间调用和 BPF 尾调用是不兼容的，因为后者需要复用当前的栈设置（ stack setup），而前者会增加一个额外的栈帧，因此不符合尾调用期望的布局。~~

BPF JIT 编译器为每个函数体发出单独的镜像，随后在最后的 JIT 处理（final JIT pass）中再修改镜像中函数调用的地址 。事实证明，这种方式需要对 JIT 做最少的改动，因为它们可以将BPF到BPF的调用当作传统的BPF辅助调用。

直到kernel5.9，BPF 函数间调用和 BPF 尾调用是不兼容的。利用尾调用的BPF程序不能获得减少程序映像大小和更快加载时间的好处。Linux Kernel 5.10 终于允许用户两全其美，并增加了将BPF子程序与尾调用相结合的能力。

不过，这种改进也有一些限制。混合使用这两种特性可能会导致内核堆栈溢出。为了了解可能发生的情况，请看下面的图片，说明了bpf2bpf调用和尾部调用的组合：
![bpf_tailcall_subprograms.png](/jony.github.io/images/bpf_tailcall_subprograms.png)

尾调用，在实际跳转到目标程序之前，只会释放其当前的栈帧。在上面的例子中我们可以看到，如果从子函数内部发生尾部调用，当程序执行到func2时，函数（func1）的栈帧将存在于栈中。一旦最后一个函数（func3）函数终止，之前所有的栈帧将被释放，控制权将回到BPF程序的调用者手中。

内核引入了额外的逻辑来检测这种功能组合。对整个调用链的堆栈大小有一个限制，每个子程序的最大为256字节（注意，如果验证器检测到bpf2bpf调用，那么主函数也会被当作一个子函数）。总的来说，在这种限制下，bpf程序的调用链最多可以消耗8KB的栈空间。这个限制来自于每个栈帧256字节乘以尾部调用次数限制(32)。如果没有这个限制，BPF程序将在512字节的堆栈大小上运行，产生的尾部调用的最大计数总计为16KB，这将在某些架构上溢出堆栈。

还要提到的一点是，目前仅在x86-64架构上支持此特性组合。

---

# 1.7 JIT

![bpf_jit.png](/jony.github.io/images/bpf_jit.png)

64 位的 x86_64、arm64、ppc64、s390x、mips64、sparc64 和 32 位的 arm 、x86_32 架构都内置了 in-kernel eBPF JIT 编译器，它们的功能都是一样的，可 以用如下方式打开：

```bash
$ echo 1 > /proc/sys/net/core/bpf_jit_enable
```

32 位的 mips、ppc 和 sparc 架构目前内置的是一个 cBPF JIT 编译器。这些只有 cBPF JIT 编译器的架构，以及那些甚至完全没有 BPF JIT 编译器的架构，需要通过内核中的解释器（in-kernel interpreter）执行 eBPF 程序。

要判断哪些平台支持 eBPF JIT，可以在内核源文件中 grep HAVE_EBPF_JIT：

```bash
$ git grep HAVE_EBPF_JIT arch/
arch/arm/Kconfig:       select HAVE_EBPF_JIT   if !CPU_ENDIAN_BE32
arch/arm64/Kconfig:     select HAVE_EBPF_JIT
arch/powerpc/Kconfig:   select HAVE_EBPF_JIT   if PPC64
arch/mips/Kconfig:      select HAVE_EBPF_JIT   if (64BIT && !CPU_MICROMIPS)
arch/s390/Kconfig:      select HAVE_EBPF_JIT   if PACK_STACK && HAVE_MARCH_Z196_FEATURES
arch/sparc/Kconfig:     select HAVE_EBPF_JIT   if SPARC64
arch/x86/Kconfig:       select HAVE_EBPF_JIT   if X86_64
```
JIT 编译器可以极大加速 BPF 程序的执行，因为与解释器相比，它们可以降低每个指令的 开销（reduce the per instruction cost）。通常，指令可以 1:1 映射到底层架构的原生 指令。另外，这也会减少生成的可执行镜像的大小，因此对 CPU 的指令缓存更友好。特别 地，对于 CISC 指令集（例如 x86），JIT 做了很多特殊优化，目的是为给定的指令产生 可能的最短操作码（emitting the shortest possible opcodes），以降低程序翻译过程所 需的空间。

---

# 1.8 Hardening

为了避免代码被损坏，BPF 会在程序的生命周期内，在内核中将 BPF 解释器解释后的整个镜像（struct bpf_prog）和 JIT 编译之后的镜像（struct bpf_binary_header）锁定为只读的（read-only）。在这些位置发生的任何数据损坏（例 如由于某些内核 bug 导致的）会触发通用的保护机制，因此会造成内核崩溃（crash）而不是允许损坏静默地发生。

查看哪些平台支持将镜像内存（image memory）设置为只读的，可以通过下面的搜索：

```bash
$ git grep ARCH_HAS_SET_MEMORY | grep select
arch/arm/Kconfig:    select ARCH_HAS_SET_MEMORY
arch/arm64/Kconfig:  select ARCH_HAS_SET_MEMORY
arch/s390/Kconfig:   select ARCH_HAS_SET_MEMORY
arch/x86/Kconfig:    select ARCH_HAS_SET_MEMORY
```

`CONFIG_ARCH_HAS_SET_MEMORY` 选项是不可配置的，因此平台要么内置支持，要么不支持 。那些目前还不支持的架构未来可能也会支持。

对于 `x86_64` JIT 编译器，如果设置了 `CONFIG_RETPOLINE`，尾调用的间接跳转就会用 retpoline 实现。写作本文时，在大部分现代 Linux 发行版上 这个配置都是打开的。

将 `/proc/sys/net/core/bpf_jit_harden` 设置为 `1` 会为非特权用户的 JIT 编译做一些额外的强化工作。这些额外强化会稍微降低程序 的性能，但在有非受信用户在系统上进行操作的情况下，能够有效地减小（潜在的）受攻击 面。但与完全切换到解释器相比，这些性能损失还是比较小的。

当前，启用 hardening 会在 JIT 编译时 **模糊（blind）** BPF 程序中用户提供的所有 32 位和 64 位常量，以防御 JIT spraying（喷射）攻击，这些攻击会将原生操作码（native opcodes）作为立即数（immediate values）注入到内核。这种攻击有效是因为： **立即数** 驻留在可执行内核内存（executable kernel memory）中，因此某些内核 bug 可能会触 发一个跳转动作，如果跳转到立即数的开始位置，就会把它们当做原生指令开始执行。

模糊 JIT 常量通过对真实指令进行随机化实现 。在这种方式中，通过对指令进行重写，将原来基于 **立即数** 的操作转换成基于寄存器的操作。指令重写将加载值的过程分解为两部分：

	1.加载一个模糊后的（blinded）立即数 rnd ^ imm 到寄存器
	2.将寄存器和 rnd 进行异或操作（xor）

这样原始的 imm 立即数就驻留在寄存器中，可以用于真实的操作了。这里介绍的只是加载操作的模糊过程，实际上所有的通用操作都被模糊了。

下面是强化关闭的情况下，某个程序的 JIT 编译结果：

```c
$ echo 0 > /proc/sys/net/core/bpf_jit_harden

  ffffffffa034f5e9 + <x>:
  [...]
  39:   mov    $0xa8909090,%eax
  3e:   mov    $0xa8909090,%eax
  43:   mov    $0xa8ff3148,%eax
  48:   mov    $0xa89081b4,%eax
  4d:   mov    $0xa8900bb0,%eax
  52:   mov    $0xa810e0c1,%eax
  57:   mov    $0xa8908eb4,%eax
  5c:   mov    $0xa89020b0,%eax
  [...]
 ```

强化打开之后，以上程序被某个非特权用户通过 BPF 加载的结果（这里已经进行了常量模糊）：

```c
$ echo 1 > /proc/sys/net/core/bpf_jit_harden

  ffffffffa034f1e5 + <x>:
  [...]
  39:   mov    $0xe1192563,%r10d
  3f:   xor    $0x4989b5f3,%r10d
  46:   mov    %r10d,%eax
  49:   mov    $0xb8296d93,%r10d
  4f:   xor    $0x10b9fd03,%r10d
  56:   mov    %r10d,%eax
  59:   mov    $0x8c381146,%r10d
  5f:   xor    $0x24c7200e,%r10d
  66:   mov    %r10d,%eax
  69:   mov    $0xeb2a830e,%r10d
  6f:   xor    $0x43ba02ba,%r10d
  76:   mov    %r10d,%eax
  79:   mov    $0xd9730af,%r10d
  7f:   xor    $0xa5073b1f,%r10d
  86:   mov    %r10d,%eax
  89:   mov    $0x9a45662b,%r10d
  8f:   xor    $0x325586ea,%r10d
  96:   mov    %r10d,%eax
  [...]
```

两个程序在语义上是一样的，但在第二种方式中，原来的立即数在反汇编之后的程序中不再 可见。

同时，强化还会禁止任何 JIT 内核符合（kallsyms）暴露给特权用户，JIT 镜像地址不再 出现在 /proc/kallsyms 中。

另外，Linux 内核提供了 CONFIG_BPF_JIT_ALWAYS_ON 选项，打开这个开关后 BPF 解释器将会从内核中完全移除，永远启用 JIT 编译器。此功能部分是为防御 Spectre v2 攻击开发的，如果应用在一个基于虚拟机的环境，客户机内核（guest kernel）将不会复用 内核的 BPF 解释器，因此可以避免某些相关的攻击。如果是基于容器的环境，这个配置是可选的，如果 JIT 功能打开了，解释器仍然可能会在编译时被去掉，以降低内核的复杂度 。因此，对于主流架构（例如 x86_64 和 arm64）上的 JIT 通常都建议打开这个开关 。

另外，内核提供了一个配置项 /proc/sys/kernel/unprivileged_bpf_disabled 来禁止非 特权用户使用 bpf(2) 系统调用，可以通过 sysctl 命令修改。 比较特殊的一点是，这个配置项特意设计为“一次性开关”（one-time kill switch）， 这意味着一旦将它设为 1，就没有办法再改为 0 了，除非重启内核。一旦设置为 1 之后，只有初始命名空间中有 CAP_SYS_ADMIN 特权的进程才可以调用 bpf(2) 系统调用 。 Cilium 启动后也会将这个配置项设为 1：

```bash
$ echo 1 > /proc/sys/kernel/unprivileged_bpf_disabled
```

---

# 1.9 Offloads

![bpf_offload.png](/jony.github.io/images/bpf_offload.png)

BPF 网络程序，尤其是 tc 和 XDP BPF 程序在内核中都有一个 offload 到硬件的接口，这 样就可以直接在网卡上执行 BPF 程序。

当前，Netronome 公司的 nfp 驱动支持通过 JIT 编译器 offload BPF，它会将 BPF 指令 翻译成网卡实现的指令集。另外，它还支持将 BPF maps offload 到网卡，因此 offloaded BPF 程序可以执行 map 查找、更新和删除操作。






# 文档连接
[Linux BPF 3.2、BPF and XDP Reference Guide](https://www.dazhuanlan.com/2019/12/10/5dee76b007da0/)
[[译] Cilium：BPF 和 XDP 参考指南（2019）](http://arthurchiao.art/blog/cilium-bpf-xdp-reference-guide-zh/#bpf_instruction)
[BPF and XDP Reference Guide](https://docs.cilium.io/en/stable/bpf/)
[BPF 辅助函数](https://man7.org/linux/man-pages/man7/bpf-helpers.7.html)
[BPF man 文档](https://man7.org/linux/man-pages/man2/bpf.2.html)