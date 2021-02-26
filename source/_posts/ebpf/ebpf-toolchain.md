---
title: 3. eBPF 工具链
date: 2021-02-24 09:59:45
categories: 
	- [eBPF]
tags:
  - ebpf
author: Jony

---

# 工具链

---

本节介绍 BPF 相关的用户态工具、内省设施（introspection facilities）和内核控制选项。 注意，围绕 BPF 的工具和基础设施还在快速发展当中，因此本文提供的内容可能只覆 盖了其中一部分。

### Ubuntu

```bash
$ sudo apt-get install -y make gcc libssl-dev bc libelf-dev libcap-dev \
  clang gcc-multilib llvm libncurses5-dev git pkg-config libmnl-dev bison flex \
  graphviz
```

# 2.2 LLVM


写作本文时，LLVM 是唯一提供 BPF 后端的编译器套件。gcc 目前还不支持。

主流的发行版在对 LLVM 打包的时候就默认启用了 BPF 后端，因此，在大部分发行版上安 装 clang 和 llvm 就可以将 C 代码编译为 BPF 对象文件了。

典型的工作流是：

1. 用 C 编写 BPF 程序
1. 用 LLVM 将 C 程序编译成对象文件（ELF）
1. 用户空间 BPF ELF 加载器（例如 iproute2）解析对象文件
1. 加载器通过 bpf() 系统调用将解析后的对象文件注入内核
1. 内核验证 BPF 指令，然后对其执行即时编译（JIT），返回程序的一个新文件描述符
1. 利用文件描述符 attach 到内核子系统（例如网络子系统）

某些子系统还支持将 BPF 程序 offload 到硬件（例如网卡）。


## 2.2.1 BPF Target（目标平台）

```bash
$ llc --version
LLVM (http://llvm.org/):
LLVM version 3.8.1
Optimized build.
Default target: x86_64-unknown-linux-gnu
Host CPU: skylake

Registered Targets:
  [...]
  bpf        - BPF (host endian)
  bpfeb      - BPF (big endian)
  bpfel      - BPF (little endian)
  [...]
```

**默认情况下，bpf target 使用编译时所在的 CPU 的大小端格式** ，即，如果 CPU 是小 端，BPF 程序就会用小端表示；如果 CPU 是大端，BPF 程序就是大端。这也和 BPF 的运 行时行为相匹配，这样的行为比较通用，而且大小端格式一致可以避免一些因为格式导致的 架构劣势。

BPF 程序可以在大端节点上编译，在小端节点上运行，或者相反，因此对于 **交叉编译** ， 引入了两个新目标 `bpfeb` 和 `bpfel`。注意前端也需要以相应的大小端方式运行。

在不存在大小端混用的场景下，建议使用 bpf target。例如，在 x86_64 平台上（小端 ），指定 bpf 和 bpfel 会产生相同的结果，因此触发编译的脚本不需要感知到大小端 。

下面是一个最小的完整 XDP 程序，实现丢弃包的功能（xdp-example.c）：

```c
#include <linux/bpf.h>

#ifndef __section
# define __section(NAME)                  \
   __attribute__((section(NAME), used))
#endif

__section("prog")
int xdp_drop(struct xdp_md *ctx)
{
    return XDP_DROP;
}

char __license[] __section("license") = "GPL";
```

用下面的命令编译并加载到内核：

```bash
$ clang -O2 -Wall -target bpf -c xdp-example.c -o xdp-example.o
$ ip link set dev em1 xdp obj xdp-example.o
```

	# 注意
	以上命令将一个 XDP 程序 attach 到一个网络设备，需要是 Linux 4.11 内核中支持 XDP 的设备，或者 4.12+ 版本的内核。

LLVM（>= 3.9） 使用正式的 BPF 机器值（machine value），即 EM_BPF（十进制 247 ，十六进制 0xf7），来生成对象文件。在这个例子中，程序是用 bpf target 在 x86_64 平台上编译的，因此下面显示的大小端标识是 LSB (和 MSB 相反)：

```bash
$ file xdp-example.o
xdp-example.o: ELF 64-bit LSB relocatable, *unknown arch 0xf7* version 1 (SYSV), not stripped
```
`readelf -a xdp-example.o` 能够打印 ELF 文件的更详细信息，有时在检查生成的 section header、relocation entries 和符号表时会比较有用。

---

# 2.2.2 调试信息（[DWARF](http://arthurchiao.art/blog/cilium-bpf-xdp-reference-guide-zh/#bpf_instruction)、BTF）

若是要 debug，clang 可以生成下面这样的汇编器输出：

```bash
$ clang -O2 -S -Wall -target bpf -c xdp-example.c -o xdp-example.S
$ cat xdp-example.S
    .text
    .section    prog,"ax",@progbits
    .globl      xdp_drop
    .p2align    3
xdp_drop:                             # @xdp_drop
# BB#0:
    r0 = 1
    exit

    .section    license,"aw",@progbits
    .globl    __license               # @__license
__license:
    .asciz    "GPL"
```

LLVM 从 6.0 开始，还包括了汇编解析器（assembler parser）的支持。你可以直接使用 BPF 汇编指令编程，然后使用 llvm-mc 将其汇编成一个目标文件。例如，你可以将前面 的 xdp-example.S 重新变回对象文件：

```bash
$ llvm-mc -triple bpf -filetype=obj -o xdp-example.o xdp-example.S
```

# [DWARF](http://arthurchiao.art/blog/cilium-bpf-xdp-reference-guide-zh/#bpf_instruction) 格式和 llvm-objdump

另外，较新版本（>= 4.0）的 LLVM 还可以将调试信息以 [dwarf](http://arthurchiao.art/blog/cilium-bpf-xdp-reference-guide-zh/#bpf_instruction) 格式存储到对象文件中。 只要在编译时加上 -g：

```bash
$ clang -O2 -g -Wall -target bpf -c xdp-example.c -o xdp-example.o
$ llvm-objdump -S -no-show-raw-insn xdp-example.o

xdp-example.o:        file format ELF64-BPF

Disassembly of section prog:
xdp_drop:
; {
    0:        r0 = 1
; return XDP_DROP;
    1:        exit
```
llvm-objdump 工具能够用编译的 C 源码对汇编输出添加注解（annotate ）。这里 的例子过于简单，没有几行 C 代码；但注意上面的 0 和 1 行号，这些行号直接对 应到内核的校验器日志（见下面的输出）。这意味着假如 BPF 程序被校验器拒绝了， llvm-objdump能帮助你将 BPF 指令关联到原始的 C 代码，对于分析来说非常有用。

```bash
$ ip link set dev em1 xdp obj xdp-example.o verb

Prog section 'prog' loaded (5)!
 - Type:         6
 - Instructions: 2 (0 over limit)
 - License:      GPL

Verifier analysis:

0: (b7) r0 = 1
1: (95) exit
processed 2 insns
```

从上面的校验器分析可以看出，llvm-objdump 的输出和内核中的 BPF 汇编是相同的。

去掉 -no-show-raw-insn 选项还可以以十六进制格式在每行汇编代码前面打印原始的 struct bpf_insn：

```bash
$ llvm-objdump -S xdp-example.o

xdp-example.o:        file format ELF64-BPF

Disassembly of section prog:
xdp_drop:
; {
   0:       b7 00 00 00 01 00 00 00     r0 = 1
; return foo();
   1:       95 00 00 00 00 00 00 00     exit
```

### LLVM IR

对于 LLVM IR 调试，BPF 的编译过程可以分为两个步骤：首先生成一个二进制 LLVM IR 临 时文件 xdp-example.bc，然后将其传递给 `llc`：

```bash
$ clang -O2 -Wall -target bpf -emit-llvm -c xdp-example.c -o xdp-example.bc
$ llc xdp-example.bc -march=bpf -filetype=obj -o xdp-example.o
```

生成的 LLVM IR 还可以 dump 成人类可读的格式：
``` bash
$ clang -O2 -Wall -emit-llvm -S -c xdp-example.c -o -
```

## BTF

LLVM 能将调试信息（例如对程序使用的数据的描述）attach 到 BPF 对象文件。默认情况 下使用 [DWARF](http://arthurchiao.art/blog/cilium-bpf-xdp-reference-guide-zh/#bpf_instruction) 格式。

BPF 使用了一个高度简化的版本，称为 **BTF** (BPF Type Format)。生成的 [DWARF](http://arthurchiao.art/blog/cilium-bpf-xdp-reference-guide-zh/#bpf_instruction) 可以 转换成 BTF 格式，然后通过 BPF 对象加载器加载到内核。内核验证 BTF 数据的正确性， 并跟踪 BTF 数据中包含的数据类型。

这样的话，就可以用键和值对 BPF map 打一些注解（annotation）存储到 BTF 数据中，这 样下次 dump map 时，除了 map 内的数据外还会打印出相关的类型信息。这对内省（ introspection）、调试和格式良好的打印都很有帮助。注意，BTF 是一种通用的调试数据 格式，因此任何从 [DWARF](http://arthurchiao.art/blog/cilium-bpf-xdp-reference-guide-zh/#bpf_instruction) 转换成的 BTF 数据都可以被加载（例如，内核 vmlinux [DWARF](http://arthurchiao.art/blog/cilium-bpf-xdp-reference-guide-zh/#bpf_instruction) 数 据可以转换成 BTF 然后加载）。后者对于未来 BPF 的跟踪尤其有用。

将 [DWARF](http://arthurchiao.art/blog/cilium-bpf-xdp-reference-guide-zh/#bpf_instruction) 格式的调试信息转换成 BTF 格式需要用到 `elfutils` (>= 0.173) 工具。 如果没有这个工具，那需要在 `llc` 编译时打开 `-mattr=[dwarf](http://arthurchiao.art/blog/cilium-bpf-xdp-reference-guide-zh/#bpf_instruction)ris` 选项：

```bash
$ llc -march=bpf -mattr=help |& grep [dwarf](http://arthurchiao.art/blog/cilium-bpf-xdp-reference-guide-zh/#bpf_instruction)ris
[dwarf](http://arthurchiao.art/blog/cilium-bpf-xdp-reference-guide-zh/#bpf_instruction)ris - Disable MCAsmInfo [Dwarf](http://arthurchiao.art/blog/cilium-bpf-xdp-reference-guide-zh/#bpf_instruction)UsesRelocationsAcrossSections.
[...]
```

使用 -mattr=[dwarf](http://arthurchiao.art/blog/cilium-bpf-xdp-reference-guide-zh/#bpf_instruction)ris 是因为 [dwarf](http://arthurchiao.art/blog/cilium-bpf-xdp-reference-guide-zh/#bpf_instruction)ris ([dwarf](http://arthurchiao.art/blog/cilium-bpf-xdp-reference-guide-zh/#bpf_instruction) relocation in section) 选项禁 用了 [DWARF](http://arthurchiao.art/blog/cilium-bpf-xdp-reference-guide-zh/#bpf_instruction) 和 ELF 的符号表之间的 [DWARF](http://arthurchiao.art/blog/cilium-bpf-xdp-reference-guide-zh/#bpf_instruction) cross-section 重定位，因为 libdw 不支持 BPF 重定位。不打开这个选项的话，pahole 这类工具将无法正确地从对象中 dump 结构。

elfutils (>= 0.173) 实现了合适的 BPF 重定位，因此没有打开 -mattr=[dwarf](http://arthurchiao.art/blog/cilium-bpf-xdp-reference-guide-zh/#bpf_instruction)ris 选 项也能正常工作。它可以从对象文件中的 [DWARF](http://arthurchiao.art/blog/cilium-bpf-xdp-reference-guide-zh/#bpf_instruction) 或 BTF 信息 dump 结构。目前 pahole 使用 LLVM 生成的 [DWARF](http://arthurchiao.art/blog/cilium-bpf-xdp-reference-guide-zh/#bpf_instruction) 信息，但未来它可能会使用 BTF 信息。


## pahole (注：pahole 一种代码审计工具)

将 [DWARF](http://arthurchiao.art/blog/cilium-bpf-xdp-reference-guide-zh/#bpf_instruction) 转换成 BTF 格式需要使用较新的 pahole 版本（>= 1.12），然后指定 -J 选项。 检查所用的 pahole 版本是否支持 BTF（注意，pahole 会用到 llvm-objcopy，因此 也要检查后者是否已安装）：

```bash
$ pahole --help | grep BTF
-J, --btf_encode           Encode as BTF
```

生成调试信息还需要前端的支持，在 clang 编译时指定 -g 选项，生成源码级别的调 试信息。注意，不管 llc 是否指定了 [dwarf](http://arthurchiao.art/blog/cilium-bpf-xdp-reference-guide-zh/#bpf_instruction)ris 选项，-g 都是需要指定的。生成目 标文件的完整示例：

```bash
$ clang -O2 -g -Wall -target bpf -emit-llvm -c xdp-example.c -o xdp-example.bc
$ llc xdp-example.bc -march=bpf -mattr=[dwarf](http://arthurchiao.art/blog/cilium-bpf-xdp-reference-guide-zh/#bpf_instruction)ris -filetype=obj -o xdp-example.o
```

或者，只使用 clang 这一个工具来编译带调试信息的 BPF 程序（同样，如果有合适的 elfutils 版本，[dwarf](http://arthurchiao.art/blog/cilium-bpf-xdp-reference-guide-zh/#bpf_instruction)ris 选项可以省略）：

```bash
$ clang -target bpf -O2 -g -c -Xclang -target-feature -Xclang +[dwarf](http://arthurchiao.art/blog/cilium-bpf-xdp-reference-guide-zh/#bpf_instruction)ris -c xdp-example.c -o xdp-example.o
```

基于 [DWARF](http://arthurchiao.art/blog/cilium-bpf-xdp-reference-guide-zh/#bpf_instruction) 信息 dump BPF 程序的数据结构：

```bash
$ pahole xdp-example.o
struct xdp_md {
        __u32                      data;                 /*     0     4 */
        __u32                      data_end;             /*     4     4 */
        __u32                      data_meta;            /*     8     4 */

        /* size: 12, cachelines: 1, members: 3 */
        /* last cacheline: 12 bytes */
};
```

在对象文件中，[DWARF](http://arthurchiao.art/blog/cilium-bpf-xdp-reference-guide-zh/#bpf_instruction) 数据将仍然伴随着新加入的 BTF 数据一起保留。完整的 clang 和 pahole 示例：

```bash
$ clang -target bpf -O2 -Wall -g -c -Xclang -target-feature -Xclang +[dwarf](http://arthurchiao.art/blog/cilium-bpf-xdp-reference-guide-zh/#bpf_instruction)ris -c xdp-example.c -o xdp-example.o
$ pahole -J xdp-example.o
```

## readelf

通过 readelf 工具可以看到多了一个 .BTF section：

```bash
$ readelf -a xdp-example.o
[...]
  [18] .BTF              PROGBITS         0000000000000000  00000671
[...]
```
BPF 加载器（例如 iproute2）会检测和加载 BTF section，因此给 BPF map 注释（ annotate）类型信息。


### 2.2.3 BPF 指令集

	根据不同的 CPU 生成不同的汇编指令，用于编译和执行

LLVM 默认用 BPF 基础指令集（base instruction set）来生成代码，以确保这些生成的对
象文件也能够被稍老的 LTS 内核（例如 4.9+）加载。

但是，LLVM 提供了一个 BPF 后端选项 `-mcpu`，可以指定不同版本的 BPF 指令集，即
BPF 基础指令集之上的指令集扩展（instruction set extensions），以生成更高效和体积
更小的代码。

可用的 `-mcpu` 类型：

```shell
$ llc -march bpf -mcpu=help
Available CPUs for this target:

  generic - Select the generic processor.
  probe   - Select the probe processor.
  v1      - Select the v1 processor.
  v2      - Select the v2 processor.
[...]
```

* `generic` processor 是默认的 processor，也是 BPF `v1` 基础指令集。
* `v1` 和 `v2` processor 通常在交叉编译 BPF 的环境下比较有用，即编译 BPF 的平台
  和最终执行 BPF 的平台不同（因此 BPF 内核特性可能也会不同）。

**推荐使用 `-mcpu=probe` ，这也是 Cilium 内部在使用的类型**。使用这种类型时，
LLVM BPF 后端会向内核询问可用的 BPF 指令集扩展，如果找到可用的，就会使用相应的指
令集来编译 BPF 程序。

使用 `llc` 和 `-mcpu=probe` 的完整示例：

```shell
$ clang -O2 -Wall -target bpf -emit-llvm -c xdp-example.c -o xdp-example.bc
$ llc xdp-example.bc -march=bpf -mcpu=probe -filetype=obj -o xdp-example.o
```

<a name="ch_2.2.4"></a>

### 2.2.4 指令和寄存器位宽（64/32 位）

通常来说，LLVM IR 生成是架构无关的。但使用 `clang` 编译时是否指定 `-target bpf`
是有几点小区别的，取决于不同的平台架构（`x86_64`、`arm64` 或其他），`-target` 的
默认配置可能不同。

引用内核文档 `Documentation/bpf/bpf_devel_QA.txt`：

* BPF 程序可以嵌套 include 头文件，只要头文件中都是文件作用域的内联汇编代码（
  file scope inline assembly codes）。大部分情况下默认 target 都可以处理这种情况，
  但如果 BPF 后端汇编器无法理解这些汇编代码，那 `bpf` target 会失败。

* 如果编译时没有指定 `-g`，那额外的 elf sections（例如 `.eh_frame`
  和 `.rela.eh_frame`）可能会以默认 target 格式出现在对象文件中，但不会是 `bpf`
  target。

* 默认 target 可能会将一个 C `switch` 声明转换为一个 `switch` 表的查找和跳转操作。
  由于 switch 表位于全局的只读 section，因此 BPF 程序的加载会失败。 `bpf` target
  不支持 switch 表优化。clang 的 `-fno-jump-tables` 选项可以禁止生成 switch 表。

* 如果 clang 指定了 `-target bpf`，那指针或 `long`/`unsigned long` 类型将永远
  是 64 位的，不管底层的 clang 可执行文件或默认的 target（或内核）是否是 32
  位。但如果使用的是 native clang target，那 clang 就会根据底层的架构约定（
  architecture's conventions）来编译这些类型，这意味着对于 32 位的架构，BPF 上下
  文中的指针或 `long`/`unsigned long` 类型会是 32 位的，但此时的 BPF LLVM 后端仍
  然工作在 64 位模式。

`native` target 主要用于跟踪（tracing）内核中的 `struct pt_regs`，这个结构体对
CPU 寄存器进行映射，或者是跟踪其他一些能感知 CPU 寄存器位宽（CPU's register
width）的内核结构体。除此之外的其他场景，例如网络场景，都建议使用 `clang -target
bpf`。

另外，LLVM 从 7.0 开始支持 32 位子寄存器和 BPF ALU32 指令。另外，新加入了一个代
码生成属性 `alu32`。当指定这个参数时，LLVM 会尝试尽可能地使用 32 位子寄存器，例
如当涉及到 32 位操作时。32 位子寄存器及相应的 ALU 指令组成了 ALU32 指令。例如，
对于下面的示例代码：

```shell
$ cat 32-bit-example.c
void cal(unsigned int *a, unsigned int *b, unsigned int *c)
{
  unsigned int sum = *a + *b;
  *c = sum;
}
```

使用默认的代码生成选项，产生的汇编代码如下：

```
$ clang -target bpf -emit-llvm -S 32-bit-example.c
$ llc -march=bpf 32-bit-example.ll
$ cat 32-bit-example.s
cal:
  r1 = *(u32 *)(r1 + 0)
  r2 = *(u32 *)(r2 + 0)
  r2 += r1
  *(u32 *)(r3 + 0) = r2
  exit
```

可以看到默认使用的是 `r` 系列寄存器，这些都是 64 位寄存器，这意味着其中的加法都
是 64 位加法。现在，如果指定 `-mattr=+alu32` 强制要求使用 32 位，生成的汇编代码
如下：

```
$ llc -march=bpf -mattr=+alu32 32-bit-example.ll
$ cat 32-bit-example.s
cal:
  w1 = *(u32 *)(r1 + 0)
  w2 = *(u32 *)(r2 + 0)
  w2 += w1
  *(u32 *)(r3 + 0) = w2
  exit
```

可以看到这次使用的是 `w` 系列寄存器，这些是 32 位子寄存器。

使用 32 位子寄存器可能会减小（最终生成的代码中）**类型扩展指令**（type extension
instruction）的数量。另外，它对 32 位架构的内核 eBPF JIT 编译器也有所帮助，因为
原来这些编译器都是用 32 位模拟 64 位 eBPF 寄存器，其中使用了很多 32 位指令来操作
高 32 bit。即使写 32 位子寄存器的操作仍然需要对高 32 位清零，但只要确保从 32 位
子寄存器的读操作只会读取低 32 位，那只要 JIT 编译器已经知道某个寄存器的定义只有
子寄存器读操作，那对高 32 位的操作指令就可以避免。





# 文档连接
[Linux BPF 3.2、BPF and XDP Reference Guide](https://www.dazhuanlan.com/2019/12/10/5dee76b007da0/)
[[译] Cilium：BPF 和 XDP 参考指南（2019）](http://arthurchiao.art/blog/cilium-bpf-xdp-reference-guide-zh/#bpf_instruction)
[BPF and XDP Reference Guide](https://docs.cilium.io/en/stable/bpf/)
[BPF 辅助函数](https://man7.org/linux/man-pages/man7/bpf-helpers.7.html)
[BPF man 文档](https://man7.org/linux/man-pages/man2/bpf.2.html)