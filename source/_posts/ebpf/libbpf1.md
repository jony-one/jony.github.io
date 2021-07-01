---
title: libbpf 学习使用
date: 2021-06-14 19:44:19
categories: 
	- [eBPF]
tags:
  - ebpf
  - cilium
author: Jony
---

# BPF CO-RE
参考:[https://blog.gmem.cc/ebpf](https://blog.gmem.cc/ebpf)

BPF CO-RE 与 Java 初始目的一致，一次编译导出运行。
产生的原因就是因为内核版本直接存在的差异可能导致某些字段、结构顺序的改变，导致再这个内核上可以运行，但是到另一个内核出现运行是失败的情况。

初始 BCC 解决方案：
BCC 是很好的一款 eBPF 编程工具，再使用 BCC 时 BPF 内核程序的 c 代码被嵌入到前端语言中。而且 BCC 只会在目标机器上编译源码运行不会产生编译好的输出文件。BCC 的确当
1. Clang/LLVM 再编译时很耗资源，如果再程序启动时编译 BPF 代码，对生产环境不友好
2. 目标机器可能不包含指定的内核文件
3. 运行时可能会有编译错误
3. 读写 BPF Map 时需要编写面向对象的 C 代码。

## CO-BE 原理：
将必要的功能数据片整合到一起，降低可以移植 BPF 程序的难度。
1. BTF类型信息：允许捕获关于内核、BPF程序的类型/代码的关键信息
2. Clang为BPF程序C代码提供了express the intent和记录relocation信息的手段
3. BPF loader（libbpf）根据内核的BTF和BPF程序，调整编译后的BPF代码，使其适合在目标内核上运行
4. 对于BPF CO-RE不可知的内核，提供了一些高级的BPF特性，满足高级场景

## BTF
即BPF Type Format，类似于DWARF调试信息，但是没有那么generic和verbose。BTF能够用来增强BPF verifier的能力，能够允许BPF代码直接访问内核内存。
对于CO-RE来说，更重要的是，内核通过 /sys/kernel/btf/vmlinux暴露了权威的、自描述的BTF信息。执行下面的命令，你可以得到一个可编译的C头文件：

`bpftool btf dump file /sys/kernel/btf/vmlinux format c`


## 编译支持
为了启用CO-RE，并且让BPF loader（libbpf）来为正在运行的（目标）内核调整BPF程序，Clang被扩展，增加了一些built-ins。

这些built-ins会发出（emit）BTF relocations，BTF relocations是BPF程序需要读取什么信息的高层描述。假设程序需要访问task_struct->pid，Clang会将其记录：
需要访问pid_t类型的、名为pid、位于task_struct结构中的字段。这样，即使字段顺序调整，甚至pid字段被放入一个内嵌的匿名结构体/联合体中，BPF程序仍然能够正确访问到pid字段。
提高了内核之间的兼容性。

能不捕获（进而重定位）的信息不单单是字段偏移量，还包括字段是否存在、字段的size。甚至对于位域（bitfield）字段，也能够捕获足够多的信息，让对它的访问能够被重定位。

## BPF loader
BPF loader在加载程序时，会利用前述的（构建机的）内核BTF信息、Clang重定位信息，并读取当前内核的BTF信息，对BPF程序（ELF object文件）进行裁减（custom tailored） —— 解析和匹配所有类型、字段，更新字段偏移量，以及其它可重定位数据 —— 确保程序在当前内核上能够正确运行。

描述：CO-RE 通过编译成类似中间代码的 BTF 文件，在 BPF Loader 加载的时候根据当前内核信息对程序进行裁剪。也就是说想要弄懂 libbpf 底层可能需要一些编译原理的实操能力。

# 内核
要支持CO-RE，内核不需要更多的改变（除了开启CONFIG_DEBUG_INFO_BTF）。被BPF loader（libbpf）处理过的BPF程序，对于内核来说，和在本机编译的BPF程序是完全等价的。

## 可移植性演示：

假设我们期望读取task_struct结构体的pid字段。使用BCC时，你可以直接访问：
pid_t pid = task->pid;
BCC会自动将其重写为对 bpf_probe_read()的调用。

1. 
如果添加了 **[BTF_PROG_TYPE_TRACING](https://patchwork.ozlabs.org/project/netdev/list/?series=139747&state=*)** 程序
使用CO-RE的时候，由于没有BCC这种代码重写机制，为了打成同样效果，你可能需要：
libbpf + BPF_PROG_TYPE_TRACING：如果编写的是这类程序，你可以直接写：
`pid_t pid = task->pid;`
而不需要bpf_probe_read()调用。要实现可移植性，则需要将上述代码包围到 \_\_builtin\_preserve\_access\_index中：
`pid_t pid = __builtin_preserve_access_index(({ task->pid; }));`

**`但是`**
**[BTF_PROG_TYPE_TRACING](https://patchwork.ozlabs.org/project/netdev/list/?series=139747&state=*)** 程序 过于超前，对于低版本的内核
还是需要显式地调用 `bpf_probe_read()` 
现在，使用CO-RE+libbpf，我们有两种方式来实现访问pid字段的值。一种是直接使用 `bpf_core_read()`替换 `bpf_probe_read()`:

```c
pid_t pid;
bpf_core_read(&pid, sizeof(pid), &task->pid);
```

原因：bpf_core_read()是一个简单的宏，它会将所有的参数直接传递给bpf_probe_read()，**但也会使Clang通过__builtin_preserve_access_index()记录第三个参数(&task->pid)的字段的偏移量。**
所以最终翻译之后的调用代码：`bpf_probe_read(&pid, **sizeof**(pid), __builtin_preserve_access_index(&task->pid));`

但是 进行pointer chasing时，使用bpf_probe_read()/bpf_core_read()会变得痛苦。 幸运的是，CO-RE提供了*`助手宏`*，使用：
```c
u64 inode = BPF_CORE_READ(task, mm, exe_file, f_inode, i_ino);
 
// 或者
u64 inode;
BPF_CORE_READ_INTO(&inode, task, mm, exe_file, f_inode, i_ino);
```

类似的，和 bpf_probe_read_str()对应的CO-RE函数是 bpf_core_read_str()，以及助手宏 BPF_CORE_READ_STR_INTO()。

当然可以避免上面的情况，可以通过检查字段是否在目标内核存在，可以使用 bpf_core_field_exists()宏：

`pid_t pid = bpf_core_field_exists(task->pid) ? BPF_CORE_READ(task, pid) : -1;`

此外，可以通过bpf_core_field_size()宏捕获任意字段的大小，以此来保证不同内核版本间的字段大小没有发生变化。

`u32 comm_sz = bpf_core_field_size(task->comm);`

位域字段的读取，可以使用：

```c
struct tcp_sock *s = ...;
 
// 读取s->is_cwnd_limited对应的位域字段
bool is_cwnd_limited = BPF_CORE_READ_BITFIELD(s, is_cwnd_limited);
 
// 或者
u64 is_cwnd_limited;
BPF_CORE_READ_BITFIELD_PROBED(s, is_cwnd_limited, &is_cwnd_limited);
```

# 内核版本和配置差异 根据用户配置修改行为 这两种一般遇不到，先跳过

# 总结

libbpf 通过以下几种方式帮组用户实现 CO-RE 的：

- `vmlinux.h` 消除了对内核头文件的依赖
- 字段重定位(字段偏移，存在性，大小等)使得可以从内核中抽取数据；
- libbpf提供的Kconfig外部变量允许BPF程序适应各种内核版本以及特定配置的更改；
- 当上述都不适合时，app提供了只读的配置和struct flavors，作为解决任何应用程序必须处理的复杂场景的最终大锤。


# libbpf-bootstrap
libbpf-bootstrap 提供了 使用 libbpf 抽象出来的开发 bpf 的手脚架工具。
libbpf-bootstrap 目前有两个演示 BPF 应用程序可用：minimal 和bootstrap. minimal就是这样——编译、加载和运行一个简单的 BPF 等价物的最小 BPF 应用程序printf("Hello, World!")。作为最小的一个，它也没有对 Linux 内核的最新性强加太多要求，并且应该在相当旧的内核版本上运行良好。

此外，bootstrap演示了 BPF 全局变量的使用（Linux 5.5+）和BPF 环形缓冲区的使用（Linux 5.8+）。这些特性都不是构建有用的 BPF 应用程序所必需的，但它们带来了巨大的可用性改进，并且是构建现代 BPF 应用程序的方式，因此我在基本bootstrap示例中添加了使用它们的示例。

libbpf-bootstrap 与 libbpf（作为 Git 子模块）和 bpftool（仅适用于 x86-64 架构）捆绑在一起，以避免依赖 Linux 发行版中可用的任何特定（并且可能已过时）版本。您的系统还应安装zlib(libz-dev或zlib-devel包) 和libelf (libelf-dev或elfutils-libelf-devel包) 。这些是libbpf正确编译和运行它所必需的依赖项。


# Libbpf-bootstrap 概述
```bash
$ tree
.
├── libbpf
│   ├── ...
│   ... 
├── LICENSE
├── README.md
├── src
│   ├── bootstrap.bpf.c
│   ├── bootstrap.c
│   ├── bootstrap.h
│   ├── Makefile
│   ├── minimal.bpf.c
│   ├── minimal.c
│   ├── vmlinux_508.h
│   └── vmlinux.h -> vmlinux_508.h
└── tools
    ├── bpftool
    └── gen_vmlinux_h.sh

16 directories, 85 files
```
tools/包含bpftool二进制文件，用于构建 BPF 代码的BPF 骨架。与 libbpf 类似，它被捆绑以避免依赖于系统范围的 bpftool 可用性及其版本是否足够最新。

bpftool 可用于生成您自己的vmlinux.h包含所有 Linux 内核类型定义的头文件。
BPF CO-RE，vmlinux.h不必完全匹配您的内核配置和版本。
**但是**，如果您确实需要生成自定义vmlinux.h，请随时检查 tools/gen_vmlinux_h.sh 脚本以了解如何完成。

Makefile 定义了必要的构建规则来编译所有提供的（和你自定义的）BPF 应用程序。它遵循一个简单的文件命名约定：

- `<app>.bpf.c` 文件是包含要在内核上下文中执行的逻辑的 BPF C 代码；
- `<app>.c` 是用户空间的 C 代码，它在应用程序的整个生命周期中加载 BPF 代码并与之交互；
- `<app>.h` 是可选具有通用类型定义的头文件，由应用程序的 BPF 和用户空间代码共享。

minimal.c并minimal.bpf.c形成minimalBPF 演示应用程序。
bootstrap.c、bootstrap.bpf.c和bootstrap.h是bootstrap-bpf应用程序。很简单。

# minimal 应用程序

minimal是一个很好的例子。这不是构建生产就绪应用程序和工具的最佳方法，但对于本地实验来说已经足够了。
BPF 代码：

minimum.bpf.c
```c
// SPDX-License-Identifier: GPL-2.0 OR BSD-3-Clause
/* Copyright (c) 2020 Facebook */
#include <linux/bpf.h>
#include <bpf/bpf_helpers.h>

char LICENSE[] SEC("license") = "Dual BSD/GPL";

int my_pid = 0;

SEC("tp/syscalls/sys_enter_write")
int handle_tp(void *ctx)
{
  int pid = bpf_get_current_pid_tgid() >> 32;

  if (pid != my_pid)
    return 0;

  bpf_printk("BPF triggered from PID %d.\n", pid);

  return 0;
}
```
`#include <linux/bpf.h>` 引入了一些基本的BPF 相关类型和使用内核端 BPF API 所需要的长了（例如，BPF 辅助函数标志）。 
`#include <bpf/bpf_helpers.h>` 是 libbpf 最常用的宏、常量和 BPF 辅助函数，基本现有的 BPF 应用程序都会使用他们。
`bpf_get_current_pid_tgid()` 就是上面 BPF 辅助函数的一个 Demo。

`LICENSE` 变量定义了 BPF 代码的许可。

`int my_pid = 0;` 是全局变量，BPF 代码可以读取和修改这个变量，就像用户空间的代码修改全局变量一样。而且 Linux 5.5 及以上版本可以从用户空间
程序直接读取。也就是说  **`它还可以用于在内核 BPF 代码和用户空间控制代码之间来回传递数据`**

`SEC("tp/syscalls/sys_enter_write") int handle_tp(void *ctx) { ... }` 定义将加载内核中的 BPF 程序。它的 `SEC` 定义了应该创建什么类型的 BPF 程序
以及告诉 libbpf 应该将该程序 attach 到内核中的那个位置。

当前定义的是一个 BPF 跟踪程序，每次 `write()` 从任何用户空间应用程序调用系统调用时都会调用该程序。

现在让我们看看 `handle_tp` BPF 程序在做什么：

```c
  int pid = bpf_get_current_pid_tgid() >> 32;

  if (pid != my_pid)
    return 0;
```

这部分获取以bpf_get_current_pid_tgid()的返回值的高 32 位编码的 PID（或内部内核术语中的“TGID”）。然后它检查触发 `write()`系统调用的minimal进程是否是我们的进程。
但是按照这种方式一般查看不到效果，所以输出到控制台最清楚。

` bpf_printk("BPF triggered from PID %d.\n", pid);`

这是 BPF 等价 `printf("Hello, world!\n")` 它将格式化的字符串发送到位于 的特殊文件中 `/sys/kernel/debug/tracing/trace_pipe` 可以通过 cat 命令从控制台查看其内容（确保您sudo在 root 下使用或运行）：

```bash
$ sudo cat /sys/kernel/debug/tracing/trace_pipe
  <...>-3840345 [010] d... 3220701.101143: bpf_trace_printk: BPF triggered from PID 3840345.
  <...>-3840345 [010] d... 3220702.101265: bpf_trace_printk: BPF triggered from PID 3840345.
```

# include: vmlinux.h、libbpf 和 应用 headers 文件

```c
#include "vmlinux.h"
#include <bpf/bpf_helpers.h>
#include <bpf/bpf_tracing.h>
#include <bpf/bpf_core_read.h>
#include "bootstrap.h"
```

这里使用 `vmlinux.h` 与普通引入 `linux/bpf.h` 不同，`vmlinux.h` 将内核中所有类型抖包含在一个文件汇总。可以使用 `libbpf-bootstrap` 生产，也可以使用 `bpftool` 生成。

**`vmlinux.h它不能与其他系统范围的内核头文件结合使用，因为您将不可避免地遇到类型重新定义和冲突`**

**`bpf_tracing.h和bpf_core_read.h，它们为编写基于BPF CO-RE的追踪BPF应用程序提供了一些额外的宏文件`**

# libbpf  BPF Map  操作

定义一个名为 `exec_start` 的 BPF Map： ` struct { ... }exec_start SEC(".maps");`
类型为 `BPF_MAP_TYPE_HASH` （HashMap）：`__uint(type, BPF_MAP_TYPE_HASH);`
容量最大为：8192 个条目 `__uint(max_entries, 8192);`
Key 为 `pid_t` 类型： `__type(key, pid_t);`  (`pid_t` 为 64 位无符号数字)
Value 为 `u64` 类型： `__type(value, u64);` 用于存储纳秒级时间戳
```c
struct {
        __uint(type, BPF_MAP_TYPE_HASH);
        __uint(max_entries, 8192);
        __type(key, pid_t);
        __type(value, u64);
} exec_start SEC(".maps");
```

更新或着添加一个记录：
```c
        pid_t pid;
        u64 ts;

        /* remember time exec() was executed for this PID */
        pid = bpf_get_current_pid_tgid() >> 32;
        ts = bpf_ktime_get_ns();
        bpf_map_update_elem(&exec_start, &pid, &ts, BPF_ANY);
```

获取一个记录并删除:
```c
  pid_t pid;
  u64 *start_ts;
  ...
  start_ts = bpf_map_lookup_elem(&exec_start, &pid);
  if (start_ts)
    duration_ns = bpf_ktime_get_ns() - *start_ts;
  ...
  bpf_map_delete_elem(&exec_start, &pid);
```

# 用户态只读 BPF 变量

libbpf 可以i当以只读变量

`const volatile unsigned long long min_duration_ns = 0;`

关键点就在于前面的 `const volatile` ，将变量标记为 BPF 代码和用户空间代码的只读。
所以在 BPF 验证就知道了 min_duration_ns 的具体数值。而且保证了 clang 在优化代码时这个不会优化掉这个变量名。

**`只读`** 变量在用户空间被初始化于 BPF 中初始化有点不一样，选哦在加载之前 `完成初始化` 。而且在初始化的时候打开
BPF 和 加载 BPF 需要区分开不能再使用单个方法了，代码如下：

```c
  /* 加载和验证 BPF 应用 */
  skel = bootstrap_bpf__open();
  if (!skel) {
    fprintf(stderr, "Failed to open and load BPF skeleton\n");
    return 1;
  }

  /* 用最小持续时间参数参数化BPF代码 */
  skel->rodata->min_duration_ns = env.min_duration_ms * 1000000ULL;

  /* 加载验证BPF程序 */
  err = bootstrap_bpf__load(skel);
  if (err) {
    fprintf(stderr, "Failed to load and verify BPF skeleton\n");
    goto cleanup;
  }
```

**`只读变量是框架中rodata部分的一部分(而不是data或bss)：skel->rodata->min_uration_ns`**


# 用户态侧 认识 `*.skel.h`

`*.skel.h` 是调用 `make` 命令后生成的 libbpf-bootstrap BPF 骨架。是由 bpftool 根据 `*.bpf.c` 文件自动生成的。
并且已经将 BPF 程序编译成目标代码嵌入到了 `*.skel.h` 中。简化了用户程序部署，不在需要额外的二进制文件，只需要包含这个 `*.skel.h` 头文件就可以了。

查看 `minimal.skel.h` 文件：
```c
#ifndef __MINIMAL_BPF_SKEL_H__
#define __MINIMAL_BPF_SKEL_H__

#include <stdlib.h>
#include <bpf/libbpf.h>

struct minimal_bpf {
  struct bpf_object_skeleton *skeleton;
  struct bpf_object *obj;    // 传递给 libbpf API 函数。
  struct {
    struct bpf_map *bss;
  } maps;
  struct {
    struct bpf_program *handle_tp;   // 提供对 BPF Map 和 BPF 代码的直接方法
  } progs;
  struct {
    struct bpf_link *handle_tp;
  } links;
  struct minimal_bpf__bss {
    int my_pid;   // 全局变量
  } *bss;
};

static inline void minimal_bpf__destroy(struct minimal_bpf *obj) { ... }
static inline struct minimal_bpf *minimal_bpf__open_opts(const struct bpf_object_open_opts *opts) { ... }
static inline struct minimal_bpf *minimal_bpf__open(void) { ... }
static inline int minimal_bpf__load(struct minimal_bpf *obj) { ... }
static inline struct minimal_bpf *minimal_bpf__open_and_load(void) { ... }
static inline int minimal_bpf__attach(struct minimal_bpf *obj) { ... }
static inline void minimal_bpf__detach(struct minimal_bpf *obj) { ... }

#endif /* __MINIMAL_BPF_SKEL_H__ */
```



`main` 函数执行过程：
1. 首先声明一个 `minimal_bpf` 结构
2. 设置 `libbpf_set_print` 日志打印回掉函数。
3. 释放内核对 BPF 程序内存的限制
4. 调用 `minimal_bpf__open` 打开 BPF 程序的方法，将返回值赋值给 `minimal_bpf` 结构
5. 这里可以修改和设置全局变量
6. 调用 `minimal_bpf__load` 加载并验证 BPF 程序
7. 调用 `minimal_bpf__attach` 将 BPF 程序附加到 `SEC` 指定的 tracepoints、kprobes  位置

大概代码如下：

```c
int main(int argc, char **argv)
{
        struct minimal_bpf *skel;
        libbpf_set_print(libbpf_print_fn);
        bump_memlock_rlimit();
        skel = minimal_bpf__open();
        /* 设置全局变量 */
        skel->bss->my_pid = getpid();
        minimal_bpf__load(skel);
        minimal_bpf__attach(skel);
        minimal_bpf__destroy(skel);
        return -err;
}        
```

`main` 函数比较简单，主要的还是看 `*.skel.h` 文件。接下来看下：Makefile

`.output` 下文件：
```bash
.output$ tree
.
├── bpf
│      ├── bpf_core_read.h
│      ├── bpf_endian.h
│      ├── bpf.h
│      ├── bpf_helper_defs.h
│      ├── bpf_helpers.h
│      ├── bpf_tracing.h
│      ├── btf.h
│      ├── libbpf_common.h
│      ├── libbpf.h
│      ├── libbpf_legacy.h
│      ├── skel_internal.h
│      └── xsk.h
├── libbpf
│      ├── libbpf.a
│      ├── libbpf.pc
│      └── staticobjs
│          ├── bpf.o
│          ├── bpf_prog_linfo.o
│          ├── btf_dump.o
│          ├── btf.o
│          ├── gen_loader.o
│          ├── hashmap.o
│          ├── libbpf_errno.o
│          ├── libbpf.o
│          ├── libbpf_probes.o
│          ├── linker.o
│          ├── netlink.o
│          ├── nlattr.o
│          ├── ringbuf.o
│          ├── str_error.o
│          ├── strset.o
│          └── xsk.o
├── libbpf.a
├── minimal.bpf.o
├── minimal.o
├── minimal.skel.h
└── pkgconfig
    └── libbpf.pc
```
查看 Makefile：

```bash
# 定义所有中间文件写到 .outpt 文件夹下
INCLUDES := -I$(OUTPUT)
# 定义使用 -g 调试信息编译
CFLAGS := -g -Wall
# 定义目标架构
ARCH := $(shell uname -m | sed 's/x86_64/x86/')

APPS = minimal bootstrap

# 将 libbpf 构建为静态库，将 API 头文件安装到 .output 下。可以使用共享库跳过该步骤
$(LIBBPF_OBJ): $(wildcard $(LIBBPF_SRC)/*.[ch] $(LIBBPF_SRC)/Makefile) | $(OUTPUT)/libbpf
        $(call msg,LIB,$@)
        $(Q)$(MAKE) -C $(LIBBPF_SRC) BUILD_STATIC_ONLY=1                      \
                    OBJDIR=$(dir $@)/libbpf DESTDIR=$(dir $@)                 \
                    INCLUDEDIR= LIBDIR= UAPIDIR=                              \
                    install
# 编译 bpf 文件，-D__TARGET_ARCH_$(ARCH) 用户处理低级 struct pt_regs 宏定义
$(OUTPUT)/%.bpf.o: %.bpf.c $(LIBBPF_OBJ) $(wildcard %.h) $(VMLINUX) | $(OUTPUT)
        $(call msg,BPF,$@)
        $(Q)$(CLANG) -g -O2 -target bpf -D__TARGET_ARCH_$(ARCH) $(INCLUDES) $(CLANG_BPF_SYS_INCLUDES) -c $(filter %.c,$^) -o $@
        $(Q)$(LLVM_STRIP) -g $@

# 使用 bpftool 工具生成 skel.h 头文件
$(OUTPUT)/%.skel.h: $(OUTPUT)/%.bpf.o | $(OUTPUT)
        $(call msg,GEN-SKEL,$@)
        $(Q)$(BPFTOOL) gen skeleton $< > $@ 

```



执行 make minimal 后将 Makefile 命令拆开看就清楚了。

```bash
# 创建 .output下 文件夹
mkdir -p .output

mkdir -p .output/libbpf

make -C /home/vagrant/ebpf/libbpf-bootstrap/libbpf/src BUILD_STATIC_ONLY=1                    \
            OBJDIR=/home/vagrant/ebpf/libbpf-bootstrap/examples/c/.output//libbpf DESTDIR=/home/vagrant/ebpf/libbpf-bootstrap/examples/c/.output/                     \
            INCLUDEDIR= LIBDIR= UAPIDIR=                              \
            install

make[1]: Entering directory '/home/vagrant/ebpf/libbpf-bootstrap/libbpf/src'

mkdir -p /home/vagrant/ebpf/libbpf-bootstrap/examples/c/.output//libbpf/staticobjs

# 将 libbpf 下的文件编译为目标文件
cc -I. -I../include -I../include/uapi -g -O2 -Werror -Wall -D_LARGEFILE64_SOURCE -D_FILE_OFFSET_BITS=64   -c bpf.c -o /home/vagrant/ebpf/libbpf-bootstrap/examples/c/.output//libbpf/staticobjs/bpf.o
cc -I. -I../include -I../include/uapi -g -O2 -Werror -Wall -D_LARGEFILE64_SOURCE -D_FILE_OFFSET_BITS=64   -c btf.c -o /home/vagrant/ebpf/libbpf-bootstrap/examples/c/.output//libbpf/staticobjs/btf.o
cc -I. -I../include -I../include/uapi -g -O2 -Werror -Wall -D_LARGEFILE64_SOURCE -D_FILE_OFFSET_BITS=64   -c libbpf.c -o /home/vagrant/ebpf/libbpf-bootstrap/examples/c/.output//libbpf/staticobjs/libbpf.o
cc -I. -I../include -I../include/uapi -g -O2 -Werror -Wall -D_LARGEFILE64_SOURCE -D_FILE_OFFSET_BITS=64   -c libbpf_errno.c -o /home/vagrant/ebpf/libbpf-bootstrap/examples/c/.output//libbpf/staticobjs/libbpf_errno.o
cc -I. -I../include -I../include/uapi -g -O2 -Werror -Wall -D_LARGEFILE64_SOURCE -D_FILE_OFFSET_BITS=64   -c netlink.c -o /home/vagrant/ebpf/libbpf-bootstrap/examples/c/.output//libbpf/staticobjs/netlink.o
cc -I. -I../include -I../include/uapi -g -O2 -Werror -Wall -D_LARGEFILE64_SOURCE -D_FILE_OFFSET_BITS=64   -c nlattr.c -o /home/vagrant/ebpf/libbpf-bootstrap/examples/c/.output//libbpf/staticobjs/nlattr.o
cc -I. -I../include -I../include/uapi -g -O2 -Werror -Wall -D_LARGEFILE64_SOURCE -D_FILE_OFFSET_BITS=64   -c str_error.c -o /home/vagrant/ebpf/libbpf-bootstrap/examples/c/.output//libbpf/staticobjs/str_error.o
cc -I. -I../include -I../include/uapi -g -O2 -Werror -Wall -D_LARGEFILE64_SOURCE -D_FILE_OFFSET_BITS=64   -c libbpf_probes.c -o /home/vagrant/ebpf/libbpf-bootstrap/examples/c/.output//libbpf/staticobjs/libbpf_probes.o
cc -I. -I../include -I../include/uapi -g -O2 -Werror -Wall -D_LARGEFILE64_SOURCE -D_FILE_OFFSET_BITS=64   -c bpf_prog_linfo.c -o /home/vagrant/ebpf/libbpf-bootstrap/examples/c/.output//libbpf/staticobjs/bpf_prog_linfo.o
cc -I. -I../include -I../include/uapi -g -O2 -Werror -Wall -D_LARGEFILE64_SOURCE -D_FILE_OFFSET_BITS=64   -c xsk.c -o /home/vagrant/ebpf/libbpf-bootstrap/examples/c/.output//libbpf/staticobjs/xsk.o
cc -I. -I../include -I../include/uapi -g -O2 -Werror -Wall -D_LARGEFILE64_SOURCE -D_FILE_OFFSET_BITS=64   -c btf_dump.c -o /home/vagrant/ebpf/libbpf-bootstrap/examples/c/.output//libbpf/staticobjs/btf_dump.o
cc -I. -I../include -I../include/uapi -g -O2 -Werror -Wall -D_LARGEFILE64_SOURCE -D_FILE_OFFSET_BITS=64   -c hashmap.c -o /home/vagrant/ebpf/libbpf-bootstrap/examples/c/.output//libbpf/staticobjs/hashmap.o
cc -I. -I../include -I../include/uapi -g -O2 -Werror -Wall -D_LARGEFILE64_SOURCE -D_FILE_OFFSET_BITS=64   -c ringbuf.c -o /home/vagrant/ebpf/libbpf-bootstrap/examples/c/.output//libbpf/staticobjs/ringbuf.o
cc -I. -I../include -I../include/uapi -g -O2 -Werror -Wall -D_LARGEFILE64_SOURCE -D_FILE_OFFSET_BITS=64   -c strset.c -o /home/vagrant/ebpf/libbpf-bootstrap/examples/c/.output//libbpf/staticobjs/strset.o
cc -I. -I../include -I../include/uapi -g -O2 -Werror -Wall -D_LARGEFILE64_SOURCE -D_FILE_OFFSET_BITS=64   -c linker.c -o /home/vagrant/ebpf/libbpf-bootstrap/examples/c/.output//libbpf/staticobjs/linker.o
cc -I. -I../include -I../include/uapi -g -O2 -Werror -Wall -D_LARGEFILE64_SOURCE -D_FILE_OFFSET_BITS=64   -c gen_loader.c -o /home/vagrant/ebpf/libbpf-bootstrap/examples/c/.output//libbpf/staticobjs/gen_loader.o

# 创建静态库文件，将目标文件压缩到一个文件中并添加所欲
ar rcs /home/vagrant/ebpf/libbpf-bootstrap/examples/c/.output//libbpf/libbpf.a /home/vagrant/ebpf/libbpf-bootstrap/examples/c/.output//libbpf/staticobjs/bpf.o /home/vagrant/ebpf/libbpf-bootstrap/examples/c/.output//libbpf/staticobjs/btf.o /home/vagrant/ebpf/libbpf-bootstrap/examples/c/.output//libbpf/staticobjs/libbpf.o /home/vagrant/ebpf/libbpf-bootstrap/examples/c/.output//libbpf/staticobjs/libbpf_errno.o /home/vagrant/ebpf/libbpf-bootstrap/examples/c/.output//libbpf/staticobjs/netlink.o /home/vagrant/ebpf/libbpf-bootstrap/examples/c/.output//libbpf/staticobjs/nlattr.o /home/vagrant/ebpf/libbpf-bootstrap/examples/c/.output//libbpf/staticobjs/str_error.o /home/vagrant/ebpf/libbpf-bootstrap/examples/c/.output//libbpf/staticobjs/libbpf_probes.o /home/vagrant/ebpf/libbpf-bootstrap/examples/c/.output//libbpf/staticobjs/bpf_prog_linfo.o /home/vagrant/ebpf/libbpf-bootstrap/examples/c/.output//libbpf/staticobjs/xsk.o /home/vagrant/ebpf/libbpf-bootstrap/examples/c/.output//libbpf/staticobjs/btf_dump.o /home/vagrant/ebpf/libbpf-bootstrap/examples/c/.output//libbpf/staticobjs/hashmap.o /home/vagrant/ebpf/libbpf-bootstrap/examples/c/.output//libbpf/staticobjs/ringbuf.o /home/vagrant/ebpf/libbpf-bootstrap/examples/c/.output//libbpf/staticobjs/strset.o /home/vagrant/ebpf/libbpf-bootstrap/examples/c/.output//libbpf/staticobjs/linker.o /home/vagrant/ebpf/libbpf-bootstrap/examples/c/.output//libbpf/staticobjs/gen_loader.o

# cat libbpf.pc.template 
# # SPDX-License-Identifier: (LGPL-2.1 OR BSD-2-Clause)
# 
# prefix=@PREFIX@
# libdir=@LIBDIR@
# includedir=${prefix}/include
# 
# Name: libbpf
# Description: BPF library
# Version: @VERSION@
# Libs: -L${libdir} -lbpf
# Requires.private: libelf zlib
# Cflags: -I${includedir}

sed -e "s|@PREFIX@|/usr|" \
        -e "s|@LIBDIR@|$\{prefix\}/lib64|" \
        -e "s|@VERSION@|0.5.0|" \
        < libbpf.pc.template > /home/vagrant/ebpf/libbpf-bootstrap/examples/c/.output//libbpf/libbpf.pc

# 创建文件夹
if [ ! -d '/home/vagrant/ebpf/libbpf-bootstrap/examples/c/.output//bpf' ]; then 
  install -d -m 755 '/home/vagrant/ebpf/libbpf-bootstrap/examples/c/.output//bpf'; 
fi;
# 安装所有头文件
install -m 644 bpf.h libbpf.h btf.h libbpf_common.h libbpf_legacy.h xsk.h bpf_helpers.h bpf_helper_defs.h bpf_tracing.h bpf_endian.h bpf_core_read.h skel_internal.h '/home/vagrant/ebpf/libbpf-bootstrap/examples/c/.output//bpf'

# 创建文件夹
if [ ! -d '/home/vagrant/ebpf/libbpf-bootstrap/examples/c/.output//pkgconfig' ]; then 
  install -d -m 755 '/home/vagrant/ebpf/libbpf-bootstrap/examples/c/.output//pkgconfig'; 
fi;

# 安装库文件
install -m 644 /home/vagrant/ebpf/libbpf-bootstrap/examples/c/.output//libbpf/libbpf.pc '/home/vagrant/ebpf/libbpf-bootstrap/examples/c/.output//pkgconfig'

# 创建文件夹
if [ ! -d '/home/vagrant/ebpf/libbpf-bootstrap/examples/c/.output/' ]; then 
  install -d -m 755 '/home/vagrant/ebpf/libbpf-bootstrap/examples/c/.output/'; 
fi;

# 将静态库添加到文件夹下
cp -fR /home/vagrant/ebpf/libbpf-bootstrap/examples/c/.output//libbpf/libbpf.a  '/home/vagrant/ebpf/libbpf-bootstrap/examples/c/.output/'

make[1]: Leaving directory '/home/vagrant/ebpf/libbpf-bootstrap/libbpf/src'

# 编译 bpf  程序
clang -g -O2 -target bpf -D__TARGET_ARCH_x86 -I.output -I../../libbpf/include/uapi -I../../vmlinux/ \
-idirafter /usr/local/include \
-idirafter /usr/lib/llvm-11/lib/clang/11.1.0/include \
-idirafter /usr/include/x86_64-linux-gnu \
-idirafter /usr/include \
-c minimal.bpf.c \
-o .output/minimal.bpf.o

# 删除所有调试部分
llvm-strip -g .output/minimal.bpf.o # strip useless DWARF info
# 通过 BPF 文件提取为头文件，这个可以大概研究下
/home/vagrant/ebpf/libbpf-bootstrap/tools/bpftool gen skeleton .output/minimal.bpf.o > .output/minimal.skel.h

libbpf: elf: skipping unrecognized data section(5) .rodata.str1.1

# 编译用户程序
cc -g -Wall -I.output -I../../libbpf/include/uapi -I../../vmlinux/ -c minimal.c -o .output/minimal.o
# 连接目标文件组成可执行文件
cc -g -Wall .output/minimal.o /home/vagrant/ebpf/libbpf-bootstrap/examples/c/.output/libbpf.a -lelf -lz -o minimal

```


# `bootstrap.*` 演示 BPF Map 使用

内核态定义 Map 结构：

```c
struct {
        __uint(type, BPF_MAP_TYPE_HASH);
        __uint(max_entries, 8192);
        __type(key, pid_t);
        __type(value, u64);
} exec_start SEC(".maps");

struct {
        __uint(type, BPF_MAP_TYPE_RINGBUF);
        __uint(max_entries, 256 * 1024);
} rb SEC(".maps");
```

解释：
map 名称：`exec_start`，map 类型：`BPF_MAP_TYPE_HASH`，最大条目：`8192`，key 类型：`pid_t`，value 类型：`u64`
map 名称：`rb`，map 类型：`BPF_MAP_TYPE_RINGBUF`，最大条目：`8192`


#NOTE
访问全局变量：只读变量是 skel 中rodata部分的一部分

# 问题：分别什么时候使用 bss、rodata、links 访问全局变量
答：
  skel->rodata 用于只读变量；
  skel->bss 用于可变的零初始化变量；
  skel->data 用于非零初始化的可变变量。

# 问题：SEC 范围怎么找
# 辅助函数的帮助文档阅读
# 是否还存在尾调用