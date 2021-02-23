---
title: eBPF 相关文档阅读笔记
date: 2021-02-20 16:37:29
categories: 
	- [eBPF]
tags:
  - ebpf
author: Jony

---

BPF 是 **Linux 内核中** 一个高度灵活与高效的**类虚拟机（`virtual machine-like`）** 组件，它以一种安全的方式在许多 hook 点执行字节码（bytecode ）。很多 **内核子系统** 都已经使用了 BPF，比如常见的**网络（networking）**、**跟踪（tracing）**与**安全 （security ，例如沙盒）**。
**BPF（cBPF）** 1992 年就出现了，但本文介绍的是**扩展的 BPF（extended Berkeley Packet Filter，eBPF**）。eBPF 最早出现在 3.18 内核中，此后原来的 BPF 就被称为 **“经典” BPF（classic BPF, cBPF）**，cBPF 现在基本已经废弃了。


# 1.1 指令集
---

BPF 是一个通用目的 RISC 指令集，其最初的设计目标是：用 C 语言的一个子集编 写程序，然后用一个编译器后端（例如 LLVM）将其 **编译** 成 BPF 指令，然后内核再通 过一个位于内核中的（in-kernel）**即时编译器（JIT Compiler）** 将 BPF 指令映射成处 理器的 **原生指令（opcode ）** ，以取得在内核中的最佳执行性能。
将这些指令下放到内核中可以带来如下好处：
- **无需在内核/用户空间切换就可以实现内核的可编程。**
- **可编程 datapath 具有很大的灵活性，因此程序能在编译时将不需要的特性禁用掉， 从而极大地优化程序的性能。**
- 对于网络场景（例如 tc 和 XDP），BPF 程序可以在 **无需重启内核、系统服务或容器的 情况下实现原子更新，并且不会导致网络中断**。
- BPF 给用户空间 **提供了一个稳定的 ABI**，而且**不依赖**任何第三方内核模块。
- BPF 程序与内核协同工作，复用已有的内核基础设施和工具以及内核提供的安全保证。

	# 注意

	**BPF 程序在内核中的执行总是事件驱动的**

---	

BPF 组成部分：
- 11 个64位寄存器
	寄存器从 **`r0-r10`** 。默认运行 64 位，兼容 32 位。 
	- `r10` 是唯一的只读寄存器。
	- `r1 - r5` 存储 BPF 调用内核辅助函数是传递的参数
	- `r6 - r9` 由 **被调用方（callee）保存**，在函数返回之后**调用方**可以读取
	- `r0`  存放被调用的 **辅助函数** 的返回值。注：辅助函数概念参考下面解释
- 一个程序计数器（Program Counter，PC）
- 一个 512 字节大小的 BPF 栈空间

	# 注意
	- 每个 BPF 程序的最大指令数限制在 4096 条以内。
	- BPF 校验器禁止 程序中有循环
	- BPF 中有尾调用的概念，允许一 个 BPF 程序调用另一个 BPF 程序。限制 32 层。
	- BUG：可以使用尾调用实现循环概念

---

# 指令格式

BPF 指令格式（instruction format）建模为两操作数指令（two operand instructions）， 这种格式可以在 JIT 阶段将 BPF 指令映射（mapping）为原生指令。
- 指令集长度固定为 64 bit 编码
- 目前已经实现 87 条指令，并且可扩展

指令格式：**`op:8, dst_reg:4, src_reg:4, off:16, imm:32`**
- **`op`**：指定将要执行的操作。操作可以基于寄存器，也可以基于立即数。
	### op 指令分类（[MSB](https://zh.wikipedia.org/wiki/%E6%9C%80%E9%AB%98%E6%9C%89%E6%95%88%E4%BD%8D)（高比特） 到 [LSB](https://zh.wikipedia.org/wiki/%E6%9C%80%E4%BD%8E%E6%9C%89%E6%95%88%E4%BD%8D)（最低比特））：
	- `class`：指令类型
	- `code`：指定类型的指令中的某种特定操作码
	- `source`：告诉源操作数是一个寄存器还是一个立即数

	### 指令类别包括：
	- BPF_LD, BPF_LDX：加载操作（load operations）
	- BPF_ST, BPF_STX：存储操作（store operations）
	- BPF_ALU, BPF_ALU64：逻辑运算操作（ALU operations）
	- BPF_JMP：跳转操作（jump operations）
- **`dst_reg`** 和 **`src_reg`**：提供了一个寄存器操作数的额外信息
- **`off`**: 有符号类型。编码信息定义在内核头文件中 `linux/bpf.h` 和 `linux/bpf_common.h` 中
	- 某些指令中，off 用于表示一个相对偏移量（offset）
- **`imm`**: 有符号类型。编码信息定义在内核头文件中 `linux/bpf.h` 和 `linux/bpf_common.h` 中
	- 某些指令中，imm 存储一个常量值或者立即值

	所有的 BPF 操作，例如加载程序到内核，或者创建 BPF map，都是通过核心的 bpf() 系 统调用完成的。它还用于管理 map 表项（查找/更新/删除），以及通过 pinning（钉住 ）将程序和 map 持久化到 BPF 文件系统。

# 辅助函数、BPF Maps、Object Pinning

问题，Process 通过文件描述符访问 BFP Maps ，是否需要经过系统调用，还是 Direct Memory，





# 参考文档

[官方文档如何与BPF子系统交互](https://www.kernel.org/doc/html/latest/bpf/bpf_devel_QA.html)
[bpf-helpers](https://man7.org/linux/man-pages/man7/bpf-helpers.7.html)
[bpf常见的设计问题](https://www.kernel.org/doc/html/latest/bpf/bpf_design_QA.html#questions-and-answers)
[[译] Cilium：BPF 和 XDP 参考指南（2019）](http://arthurchiao.art/blog/cilium-bpf-xdp-reference-guide-zh/#bpf_arch)
[bpf 内核源代码](https://git.kernel.org/pub/scm/linux/kernel/git/bpf/bpf.git)