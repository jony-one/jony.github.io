---
title: 4. C BPF 编程规范
date: 2021-02-24 15:04:22
categories: 
	- [eBPF]
tags:
  - ebpf
author: Jony
toc: true

---

#  C BPF 代码注意事项

**用 C 语言编写 BPF 程序不同于用 C 语言做应用开发，有一些陷阱需要注意。本节列出了 二者的一些不同之处。**

## 1. 所有函数都需要内联（inlined）、不支持函数调用（对于老版本 LLVM）、不支持共享库调用

BPF 不支持共享库（Shared libraries）。但是，可以将 **常规** 的库代码（library code）放到头文件中，然后在主程序中 include 这些头文件，例如 Cilium 就大量使用了这种方式 （可以查看 bpf/lib/ 文件夹）。另外，也可以 include 其他的一些头文件，例如内核 或其他库中的头文件，复用其中的静态内联函数（static inline functions）或宏/定义（ macros / definitions）。

内核 4.16+ 和 LLVM 6.0+ 之后已经支持 BPF-to-BPF 函数调用。对于任意给定的程序片段 ，在此之前的版本只能将全部代码编译和内联成一个扁平的 BPF 指令序列（a flat sequence of BPF instructions）。在这种情况下，最佳实践就是为每个库函数都使用一个 像 \_\_inline 一样的注解（annotation ），下面的例子中会看到。推荐使用 always\_inline，因为编译器可能会对只注解为 inline 的长函数仍然做 uninline 操 作。

如果是后者，LLVM 会在 ELF 文件中生成一个重定位项（relocation entry），BPF ELF 加载器（例如 iproute2）无法解析这个重定位项，因此会产生一条错误，因为对加载器 来说只有 BPF maps 是合法的、能够处理的重定位项。

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

## 2. 多个程序可以放在同一 C 文件中的不同 section

**BPF C 程序大量使用 section annotations** 。一个 C 文件典型情况下会分为 3 个或更多个 section。BPF ELF 加载器利用这些名字来 **提取** 和 **准备** 相关的信息，以通过 bpf()系 统调用加载程序和 maps。例如，查找创建 map 所需的元数据和 BPF 程序的 license 信息时，iproute2 会分别使用 maps 和 license 作为默认的 section 名字。注意在程序 创建时 license section 也会加载到内核，如果程序使用的是兼容 GPL 的协议，这些信 息就可以启用那些 GPL-only 的辅助函数，例如 bpf_ktime_get_ns() 和 bpf_probe_read() 。

其余的 section 名字都是和特定的 BPF 程序代码相关的，例如，下面经过修改之后的代码 包含两个程序 section：ingress 和 egress。这个非常简单的示例展示了不同 section （这里是 ingress 和 egress）之间可以共享 BPF map 和常规的静态内联辅助函数（ 例如 account_data()）。

--- 

## 示例程序

这里将原来的 xdp-example.c 修改为 tc-example.c，然后用 tc 命令加载，attach 到 一个 netdevice 的 ingress 或 egress hook。该程序对传输的字节进行计数，存储在一 个名为 acc_map 的 BPF map 中，这个 map 有两个槽（slot），分别用于 ingress hook 和 egress hook 的流量统计。

```c
#include <linux/bpf.h> // 标准 C  文件头
#include <linux/pkt_cls.h>
#include <stdint.h>
#include <iproute2/bpf_elf.h> // 特定 iproute2 头文件，有 struct bpf_elf_map 定义

#ifndef __section
#define __section(NAME) __attribute__((section(NAME), used))
#endif

#ifndef __inline
#define __inline inline __attribute__((always_inline))
#endif

#ifndef lock_xadd
#define lock_xadd(ptr,val) ((void) __sync_fetch_and_add(ptr, val))
#endif

#ifndef BPF_FUNC
#define BPF_FUNC(NAME, ...) (*NAME)(__VA_ARGS__) = (void *)BPF_FUNC_##NAME
#endif

// 关联到上面的宏定义，宏展开后 BPF_FUNC_map_lookup_elem，该函数在 iap/linux/bpf.h 中有定义
static void &BPF_FUNC(map_lookup_elem, void *map, const void *key);

// maps 放在 section 为了让加载器找到
// struct bpf_elf_map 是特定用于 iproute2 的
struct bpf_elf_map acc_map __section("maps") = { // 每条记录定义一个该 map
        .type           = BPF_MAP_TYPE_ARRAY,
        .size_key       = sizeof(uint32_t),
        .size_value     = sizeof(uint32_t),
        .pinning        = PIN_GLOBAL_NS,// map 固定为BPF伪文件系统。路径 /sys/fs/bpf/tc/globals/acc_map
        .max_elem       = 2,
};

static __inline int account_data(struct __sk_buff *skb, uint32_t dir)
{
        uint32_t *bytes;
        // map 传递给辅助函数
        bytes = map_lookup_elem(&acc_map, &dir);
        if (bytes){
                // 这里有锁，原子操作
                lock_xadd(bytes, skb->len);
        }
        return TC_ACT_OK;
}

__section("ingress")
int tc_ingress(struct __sk_buff *skb)
{
        return account_data(skb, 0);
}

__section("egress")
int tc_egress(struct __sk_buff *skb)
{
        return account_data(skb, 1);
}

char __license[] __section("license") = "GPL";

```

# 程序说明

这个例子还展示了其他一些很有用的东西，在开发过程中要注意。

首先，include 了内核头文件、标准 C 头文件和一个特定的 iproute2 头文件 iproute2/bpf_elf.h，后者定义了struct bpf_elf_map。iproute2 有一个通用的 BPF ELF 加载器，因此 struct bpf_elf_map的定义对于 XDP 和 tc 类型的程序是完全一样的 。

其次，程序中每条 struct bpf_elf_map 记录（entry）定义一个 map，这个记录包含了生成一 个（ingress 和 egress 程序需要用到的）map 所需的全部信息（例如 key/value 大 小）。这个结构体的定义必须放在 maps section，这样加载器才能找到它。可以用这个 结构体声明很多名字不同的变量，但这些声明前面必须加上 \_\_section("maps") 注解。

结构体 struct bpf_elf_map 是特定于 iproute2 的。不同的 BPF ELF 加载器有不同 的格式，例如，内核源码树中的 libbpf（主要是 perf 在用）就有一个不同的规范 （结构体定义）。iproute2 保证 struct bpf_elf_map 的后向兼容性。Cilium 采用的 是 iproute2 模型。

另外，这个例子还展示了 BPF 辅助函数是如何映射到 C 代码以及如何被使用的。这里首先定义了 一个宏 BPF_FUNC，接受一个函数名 NAME 以及其他的任意参数。然后用这个宏声明了一 个 NAME 为 map_lookup_elem 的函数，经过宏展开后会变成 BPF_FUNC_map_lookup_elem 枚举值，后者以辅助函数的形式定义在 uapi/linux/bpf.h 。当随后这个程序被加载到内核时，校验器会检查传入的参数是否是期望的类型，如果是， 就将辅助函数调用重新指向（re-points）某个真正的函数调用。另外， map_lookup_elem() 还展示了 map 是如何传递给 BPF 辅助函数的。这里，maps section 中的 &acc_map 作为第一个参数传递给 map_lookup_elem()。

由于程序中定义的数组 map （array map）是全局的，因此计数时需要使用原子操作，这里 是使用了 lock_xadd()。LLVM 将 \_\_sync_fetch_and_add() 作为一个内置函数映射到 BPF 原子加指令，即 BPF_STX | [BPF_XADD](http://lists.infradead.org/pipermail/linux-arm-kernel/2015-November/384376.html) | BPF_W（for word sizes）。

另外，struct bpf_elf_map 中的 .pinning 字段初始化为 PIN_GLOBAL_NS，这意味 着 tc 会将这个 map 作为一个节点（node）钉（pin）到 BPF 伪文件系统。默认情况下， 这个变量 acc_map 将被钉到 /sys/fs/bpf/tc/globals/acc_map。

- 如果指定的是 **`PIN_GLOBAL_NS`**，那 map 会被放到 `/sys/fs/bpf/tc/globals/`。 **globals** 是一个跨对象文件的全局命名空间。
- 如果指定的是 **`PIN_OBJECT_NS`**，tc 将会为对象文件创建一个它的**`本地目录`**（local to the object file）。例如，只要指定了 PIN_OBJECT_NS，不同的 C 文件都可以像上面一样定义各自的 acc_map。在这种情况下，这个 map 会在不同 BPF 程序之间共享。
- **`PIN_NONE`** 表示 map 不会作为节点（node）钉（pin）到 BPF 文件系统，因此当 tc 退 出时这个 map 就无法从用户空间访问了。同时，这还意味着独立的 tc 命令会创建出独 立的 map 实例，因此后执行的 tc 命令无法用这个 map 名字找到之前被钉住的 map。 在路径 /sys/fs/bpf/tc/globals/acc_map 中，map 名是 acc_map。

因此，在加载 ingress 程序时，tc 会先查找这个 map 在 BPF 文件系统中是否存在，不 存在就创建一个。创建成功后，map 会被钉（pin）到 BPF 文件系统，因此当 egress 程序通过 tc 加载之后，它就会发现这个 map 存在了，接下来会复用这个 map 而不是再创建一个新的。在 map 存在的情况下，加载器还会确保 map 的属性（properties）是匹配的， 例如 key/value 大小等等。

就像 tc 可以从同一 map 获取数据一样，第三方应用也可以用 bpf 系统调用中的 BPF_OBJ_GET 命令创建一个指向某个 map 实例的新文件描述符，然后用这个描述 符来查看/更新/删除 map 中的数据。

通过 clang 编译和 iproute2 加载：

```bash
$ clang -O2 -Wall -target bpf -c tc-example.c -o tc-example.o

$ tc qdisc add dev ens32 clsact
$ tc filter add dev ens32 ingress bpf da obj tc-example.o sec ingress
$ tc filter add dev ens32 egress bpf da obj tc-example.o sec egress

$ tc filter show dev ens32 ingress
filter protocol all pref 49152 bpf
filter protocol all pref 49152 bpf handle 0x1 tc-example.o:[ingress] direct-action id 1 tag c5f7825e5dac396f

$ tc filter show dev ens32 egress
filter protocol all pref 49152 bpf
filter protocol all pref 49152 bpf handle 0x1 tc-example.o:[egress] direct-action id 2 tag b2fd5adc0f262714

$ mount | grep bpf
sysfs on /sys/fs/bpf type sysfs (rw,nosuid,nodev,noexec,relatime,seclabel)
bpf on /sys/fs/bpf type bpf (rw,relatime,mode=0700)

$ tree /sys/fs/bpf/
/sys/fs/bpf/
+-- ip -> /sys/fs/bpf/tc/
+-- tc
|   +-- globals
|       +-- acc_map
+-- xdp -> /sys/fs/bpf/tc/

4 directories, 1 file
```

以上步骤指向完成后，当包经过 ens32 设备时，BPF map 中的计数器就会递增。

---

# 3. 不允许全局变量

由于在第1点已经提到的原因，BPF不能像普通C程序中经常使用的那样具有全局变量。
但是，我们有间接的方式实现全局变量的效果：BPF 程序可以使用一个 **`BPF_MAP_TYPE_PERCPU_ARRAY`** 类型的、只有一个槽（slot）的、可以存放任意类型数据（ arbitrary value size）的 BPF map。这可以实现全局变量的效果原因是，**BPF 程序在执行期间不会被内核抢占**，因此可以用单个 map entry 作为一个暂存缓冲区(scratch buffer)使用，存储临时数据，例如扩展 BPF 栈的限制（512 字节）。这种方式在尾调用中也是可以工作的，因为尾调用执行期间也不会被抢占。

另外，如果要在不同次 BPF 程序执行之间保持状态，使用常规的 BPF map 就可以了。

---

# 4. 不支持常量字符串或数组（const strings or arrays）

BPF C 程序中不允许定义 const 字符串或其他数组，原因和第 1 点及第 3 点一样，即 ，ELF 文件中生成的重定位项（relocation entries）会被加载器拒绝，因为不符合加 载器的 ABI（加载器也无法修复这些重定位项，因为这需要对已经编译好的 BPF 序列进行 大范围的重写）。

将来 LLVM 可能会检测这种情况，提前将错误抛给用户。现在可以用下面的辅助函数来作为 短期解决方式（work around）：

```c
static void BPF_FUNC(trace_printk, const char *fmt, int fmt_size, ...);

#ifndef printk
# define printk(fmt, ...)                                      \
    ({                                                         \
        char ____fmt[] = fmt;                                  \
        trace_printk(____fmt, sizeof(____fmt), ##__VA_ARGS__); \
    })
#endif
```

有了上面的定义，程序就可以自然地使用这个宏，例如 `printk("skb len:%u\n", skb->len);`。 输出会写到 trace pipe，用 tc exec bpf dbg 命令可以获取这些打印的消息。

不过，使用 trace_printk() 辅助函数也有一些不足，因此不建议在生产环境使用。每次 调用这个辅助函数时，常量字符串（例如 "skb len:%u\n"）都需要加载到 BPF 栈，但这 个辅助函数最多只能接受 5 个参数，因此使用这个函数输出信息时只能传递三个参数。

因此，虽然这个辅助函数对快速调试很有用，但（对于**网络程序**）还是推荐使用 **`skb_event_output()`** 或 **`xdp_event_output()`** 辅助函数。这两个函数接受从 BPF 程序 传递自定义的结构体类型参数，然后将参数以及可选的包数据（packet sample）放到 perf event ring buffer。这些函数通过一个无锁的、内存映射的、 per-CPU 的 **`perf ring buffer`** 传递数据，因此要远快于 `trace_printk()`。例如，Cilium monitor 利用这些辅助函数实现了一个调试框架，以及 在发现违反网络策略时发出通知等功能。

---

# 5. 使用 LLVM 内置的函数做内存操作

因为 BPF 程序除了调用 BPF 辅助函数之外无法执行任何函数调用，因此常规的库代码必须 实现为内联函数。另外，LLVM 也提供了一些可以用于特定大小（这里是 n）的内置函数 ，这些函数永远都会被内联：

```c
#ifndef memset
# define memset(dest, chr, n)   __builtin_memset((dest), (chr), (n))
#endif

#ifndef memcpy
# define memcpy(dest, src, n)   __builtin_memcpy((dest), (src), (n))
#endif

#ifndef memmove
# define memmove(dest, src, n)  __builtin_memmove((dest), (src), (n))
#endif
```
LLVM 后端中的某个问题会导致内置的 memcmp() 有某些边界场景下无法内联，因此在这 个问题解决之前不推荐使用这个函数。

# 6. （目前还）不支持循环

内核中的 BPF 校验器除了对其他的控制流进行图验证（graph validation）之外，还会对 所有程序路径执行深度优先搜索（depth first search），确保其中不存在循环。这样做的 目的是确保程序永远会结束。

但可以使用 `#pragma unroll` 指令实现**常量的、不超过一定上限的`循环`**。下面是一个例子 ：

```c
#pragma unroll
    for (i = 0; i < IPV6_MAX_HEADERS; i++) {
        switch (nh) {
        case NEXTHDR_NONE:
            return DROP_INVALID_EXTHDR;
        case NEXTHDR_FRAGMENT:
            return DROP_FRAG_NOSUPPORT;
        case NEXTHDR_HOP:
        case NEXTHDR_ROUTING:
        case NEXTHDR_AUTH:
        case NEXTHDR_DEST:
            if (skb_load_bytes(skb, l3_off + len, &opthdr, sizeof(opthdr)) < 0)
                return DROP_INVALID;

            nh = opthdr.nexthdr;
            if (nh == NEXTHDR_AUTH)
                len += ipv6_authlen(&opthdr);
            else
                len += ipv6_optlen(&opthdr);
            break;
        default:
            *nexthdr = nh;
            return len;
        }
    }
```

另外一种实现循环的方式是：用一个 BPF_MAP_TYPE_PERCPU_ARRAY map 作为本地 scratch space（暂存存储空间），然后用尾调用的方式调用函数自身。虽然这种方式更加动态，但目前 最大只支持 32 层嵌套调用。

将来 BPF 可能会提供一些更加原生、但有一定限制的循环。

---

## 7. 尾调用的用途
尾调用能够从一个程序调到另一个程序，提供了在运行时（runtime）原子地改变程序行 为的灵活性。为了选择要跳转到哪个程序，尾调用使用了程序数组 map（ `BPF_MAP_TYPE_PROG_ARRAY`），将 map 及其索引（index）传递给将要跳转到的程序。跳 转动作一旦完成，就没有办法返回到原来的程序；但如果给定的 map 索引中没有程序（无 法跳转），执行会继续在原来的程序中执行。

例如，可以用尾调用实现解析器的不同阶段，可以在运行时（runtime）更新这些阶段的新 解析特性。

尾调用的另一个用处是事件通知，例如，Cilium 可以在运行时（runtime）开启或关闭**丢包**的通知（packet drop notifications），其中对 skb_event_output() 的调用就是发生在被尾调用的程序中。因此，在常规情况下，执行的永远是从上到下的路径（ fall-through path），当某个程序被加入到相关的 map 索引之后，程序就会解析元数据， 触发向用户空间守护进程（user space daemon）发送事件通知。

程序数组 map 非常灵活， map 中每个索引对应的程序可以实现各自的动作（actions）。 例如，连接到 tc 或 XDP 的 root 程序执行初始的、跳转到程序数组 map 中索引为 0 的程序，然后执行流量抽样（traffic sampling），然后跳转到索引为 1 的程序，在那个 程序中应用防火墙策略，然后就可以决定是丢包还是将其送到索引为 2 的程序中继续处理，在后者中，可能可能会被 mangle 然后再次通过某个接口发送出去。在程序数据 map 之中是可以随意跳转的。当达到尾调用的最大调用深度时，内核最终会执行 fall-through path。

一个使用尾调用的最小程序示例：

```c
[...]

#ifndef __stringify
# define __stringify(X)   #X
#endif

#ifndef __section
# define __section(NAME) __attribute__((section(NAME), used))
#endif

#ifndef __section_tail
# define __section_tail(ID, KEY) __section(__stringify(ID) "/" __stringify(KEY))
#endif

#ifndef BPF_FUNC
# define BPF_FUNC(NAME, ...) (*NAME)(__VA_ARGS__) = (void *)BPF_FUNC_##NAME
#endif

#define BPF_JMP_MAP_ID   1

static void BPF_FUNC(tail_call, struct __sk_buff *skb, void *map, uint32_t index);

struct bpf_elf_map jmp_map __section("maps") = {
    .type           = BPF_MAP_TYPE_PROG_ARRAY,
    .id             = BPF_JMP_MAP_ID,
    .size_key       = sizeof(uint32_t),
    .size_value     = sizeof(uint32_t),
    .pinning        = PIN_GLOBAL_NS,
    .max_elem       = 1,
};

__section_tail(JMP_MAP_ID, 0)
int looper(struct __sk_buff *skb)
{
    printk("skb cb: %u\n", skb->cb[0]++);
    tail_call(skb, &jmp_map, 0);
    return TC_ACT_OK;
}

__section("prog")
int entry(struct __sk_buff *skb)
{
    skb->cb[0] = 0;
    tail_call(skb, &jmp_map, 0);
    return TC_ACT_OK;
}

char __license[] __section("license") = "GPL";
```

加载这个示例程序时，tc 会创建其中的程序数组（jmp_map 变量），并将其固定到 BPF 文件系统中全局命名空间下名为的 jump_map 位置。而且，iproute2 中的 BPF ELF 加载器也会识别出标记为 \_\_section_tail() 的 section。 jmp_map 的 id 字段会 跟__section_tail() 中的 id 字段（这里初始化为常量 `JMP_MAP_ID`）做匹配，因此程 序能加载到用户指定的索引（位置），在上面的例子中这个索引是 0。然后，所有的尾调用 section 将会被 iproute2 加载器处理，关联到 map 中。这个机制并不是 tc 特有的， iproute2 支持的其他 BPF 程序类型（例如 XDP、lwt）也适用。


生成的 elf 包含 section headers，描述 map id 和 map 内的条目：

```bash
$ llvm-objdump -S --no-show-raw-insn prog_array.o | less
prog_array.o:   file format ELF64-BPF

Disassembly of section 1/0:
looper:
       0:       r6 = r1
       1:       r2 = *(u32 *)(r6 + 48)
       2:       r1 = r2
       3:       r1 += 1
       4:       *(u32 *)(r6 + 48) = r1
       5:       r1 = 0 ll
       7:       call -1
       8:       r1 = r6
       9:       r2 = 0 ll
      11:       r3 = 0
      12:       call 12
      13:       r0 = 0
      14:       exit
Disassembly of section prog:
entry:
       0:       r2 = 0
       1:       *(u32 *)(r1 + 48) = r2
       2:       r2 = 0 ll
       4:       r3 = 0
       5:       call 12
       6:       r0 = 0
       7:       exi
```

在这个例子中，section 1/0 表示 looper() 函数位于 map 1 中，在 map 1 内的 位置是 0。

被固定住的 map 可以被用户空间应用（例如 Cilium daemon）读取，也可以被 tc 本身读取，因为 tc 可能会用新的程序替换原来的程序，此时可能需要读取 map 内容。 更新是原子的。

**tc 执行尾调用 map 更新（tail call map updates）的例子：**
```bash
$ tc exec bpf graft m:globals/jmp_map key 0 obj new.o sec foo
```

如果 iproute2 需要更新被固定的程序数组，可以使用 graft 命令。上面的 例子中指向的是 globals/jmp_map，那 tc 将会用一个新程序更新位于 index/key 为 0 的 map， 这个新程序位于对象文件 new.o 中的 foo section。

---

## 8. BPF 最大栈空间 512 字节

BPF 程序的最大栈空间是 512 字节，在使用 C 语言实现 BPF 程序时需要考虑到这一点。 但正如在第 3 点中提到的，可以通过一个只有一条记录（single entry）的 BPF_MAP_TYPE_PERCPU_ARRAY map 来绕过这限制，增大 （暂存空间）scratch buffer 空间。

---

## 9. 尝试使用 BPF 内联汇编

LLVM 6.0 以后支持 BPF 内联汇编，在某些场景下可能会用到。下面这个玩具示例程序（ 没有实际意义）展示了一个 64 位原子加操作。
由于文档不足，要获取更多信息和例子，目前可能只能参考 LLVM 源码中的 `lib/Target/BPF/BPFInstrInfo.td` 以及 `test/CodeGen/BPF/`。测试代码：

```c
#include <linux/bpf.h>

#ifndef __section
# define __section(NAME)                  \
   __attribute__((section(NAME), used))
#endif

__section("prog")
int xdp_test(struct xdp_md *ctx)
{
    __u64 a = 2, b = 3, *c = &a;
    /* just a toy xadd example to show the syntax */
    asm volatile("lock *(u64 *)(%0+0) += %1" : "=r"(c) : "r"(b), "0"(c));
    return a;
}

char __license[] __section("license") = "GPL";
```

上面的程序会被编译成下面的 BPF 指令序列：

```bash
Verifier analysis:

0: (b7) r1 = 2
1: (7b) *(u64 *)(r10 -8) = r1
2: (b7) r1 = 3
3: (bf) r2 = r10
4: (07) r2 += -8
5: (db) lock *(u64 *)(r2 +0) += r1
6: (79) r0 = *(u64 *)(r10 -8)
7: (95) exit
processed 8 insns (limit 131072), stack depth 8
```

---

## 10. 用 #pragma pack 禁止结构体填充（struct padding）

现代编译器默认会对数据结构进行 **内存对齐（align）**，以实现更加高效的访问。结构 体成员会被对齐到数倍于其自身大小的内存位置，不足的部分会进行填充（padding），因 此结构体最终的大小可能会比预想中大。
```c
struct called_info {
    u64 start;  // 8-byte
    u64 end;    // 8-byte
    u32 sector; // 4-byte
}; // size of 20-byte ?

printf("size of %d-byte\n", sizeof(struct called_info)); // size of 24-byte

// 实际编译后的 struct called_info
// 0x0(0)                   0x8(8)
//  ↓________________________↓
//  |        start (8)       |
//  |________________________|
//  |         end  (8)       |
//  |________________________|
//  |  sector(4) |  PADDING  | <= 地址 8 位对齐
//  |____________|___________|     会填充 4-byte.
```

内核中的 BPF 校验器会检查栈边界（stack boundary），BPF 程序不会访问栈边界外的空 间，或者是未初始化的栈空间。如果**将结构体中`填充`出来的内存区域作为一个 map 值进行访问**，那调用 bpf_prog_load() 时就会报 invalid indirect read from stack 错误。

示例代码：

```c
struct called_info {
    u64 start;
    u64 end;
    u32 sector;
};

struct bpf_map_def SEC("maps") called_info_map = {
    .type = BPF_MAP_TYPE_HASH,
    .key_size = sizeof(long),
    .value_size = sizeof(struct called_info),
    .max_entries = 4096,
};

SEC("kprobe/submit_bio")
int submit_bio_entry(struct pt_regs *ctx)
{
    char fmt[] = "submit_bio(bio=0x%lx) called: %llu\n";
    u64 start_time = bpf_ktime_get_ns();
    long bio_ptr = PT_REGS_PARM1(ctx);
    struct called_info called_info = {
            .start = start_time,
            .end = 0,
            .bi_sector = 0
    };

    bpf_map_update_elem(&called_info_map, &bio_ptr, &called_info, BPF_ANY);
    bpf_trace_printk(fmt, sizeof(fmt), bio_ptr, start_time);
    return 0;
}

// On bpf_load_program
bpf_load_program() err=13
0: (bf) r6 = r1
...
19: (b7) r1 = 0
20: (7b) *(u64 *)(r10 -72) = r1
21: (7b) *(u64 *)(r10 -80) = r7
22: (63) *(u32 *)(r10 -64) = r1
...
30: (85) call bpf_map_update_elem#2
invalid indirect read from stack off -80+20 size 24
```

在 bpf_prog_load() 中会调用 BPF 校验器的 bpf_check() 函数，后者会调用 check_func_arg() -> check_stack_boundary() 来检查栈边界。从上面的错误可以看出 ，struct called_info 被编译成 24 字节，错误信息提示从 +20 位置读取数据是“非 法的间接读取”（invalid indirect read）。从我们更前面给出的内存布局图中可以看到， 地址 0x14(20) 是填充（PADDING ）开始的地方。这里再次画出内存布局图以方便对比：

	// Actual compiled composition of struct called_info
	// 0x10(16)    0x14(20)    0x18(24)
	//  ↓____________↓___________↓
	//  |  sector(4) |  PADDING  | <= address aligned to 8
	//  |____________|___________|     with 4-byte PADDING.

`check_stack_boundary()` 会遍历每一个从开始指针出发的 `access_size (24)` 字节， 确保它们位于栈边界内部，并且栈内的所有元素都初始化了。因此填充的部分是不允许使用 的，所以报了 “invalid indirect read from stack” 错误。要避免这种错误，需要将结 构体中的填充去掉。这是通过 `#pragma pack(n)` 原语实现的：

```c
#pragma pack(4)
struct called_info {
    u64 start;  // 8-byte
    u64 end;    // 8-byte
    u32 sector; // 4-byte
}; // size of 20-byte ?

printf("size of %d-byte\n", sizeof(struct called_info)); // size of 20-byte

// Actual compiled composition of packed struct called_info
// 0x0(0)                   0x8(8)
//  ↓________________________↓
//  |        start (8)       |
//  |________________________|
//  |         end  (8)       |
//  |________________________|
//  |  sector(4) |             <= address aligned to 4
//  |____________|                 with no PADDING.
```

在 struct called_info 前面加上 #pragma pack(4) 之后，编译器会以 4 字节为单位 进行对齐。上面的图可以看到，这个结构体现在已经变成 20 字节大小，没有填充了。

但是，去掉填充也是有弊端的。例如，编译器产生的代码没有原来优化的好。去掉填充之后 ，处理器访问结构体时触发的是非对齐访问（unaligned access），可能会导致性能下降。 并且，某些架构上的校验器可能会直接拒绝非对齐访问。

不过，我们也有一种方式可以避免产生自动填充：手动填充。我们简单地在结构体中加入一 个 `u32 pad `成员来显式填充，这样既**避免了自动填充**的问题，又**解决了非对齐访问**的问题。

```c
struct called_info {
    u64 start;  // 8-byte
    u64 end;    // 8-byte
    u32 sector; // 4-byte
    u32 pad;    // 4-byte
}; // size of 24-byte ?

printf("size of %d-byte\n", sizeof(struct called_info)); // size of 24-byte

// Actual compiled composition of struct called_info with explicit padding
// 0x0(0)                   0x8(8)
//  ↓________________________↓
//  |        start (8)       |
//  |________________________|
//  |         end  (8)       |
//  |________________________|
//  |  sector(4) |  pad (4)  | <= address aligned to 8
//  |____________|___________|     with explicit PADDING.
```

---

## 11. 通过未验证的引用（invalidated references）访问包数据

某些网络相关的 BPF 辅助函数，例如 `bpf_skb_store_bytes`，可能会修改包的大小。校验器无法跟踪这类改动，因此它会将所有之前对包数据的引用都视为过期的（未验证的） 。因此，为避免程序被校验器拒绝，在访问数据之外需要先更新相应的引用。
来看下面的例子：
```c
struct iphdr *ip4 = (struct iphdr *) skb->data + ETH_HLEN;

skb_store_bytes(skb, l3_off + offsetof(struct iphdr, saddr), &new_saddr, 4, 0);

if (ip4->protocol == IPPROTO_TCP) {
    // do something
}
```

校验器会拒绝这段代码，因为它认为在 `skb_store_bytes` 执行之后，引用 `ip4->protocol` 是未验证的（invalidated）:

```c
R1=pkt_end(id=0,off=0,imm=0) R2=pkt(id=0,off=34,r=34,imm=0) R3=inv0
R6=ctx(id=0,off=0,imm=0) R7=inv(id=0,umax_value=4294967295,var_off=(0x0; 0xffffffff))
R8=inv4294967162 R9=pkt(id=0,off=0,r=34,imm=0) R10=fp0,call_-1
...
18: (85) call bpf_skb_store_bytes#9
19: (7b) *(u64 *)(r10 -56) = r7
R0=inv(id=0) R6=ctx(id=0,off=0,imm=0) R7=inv(id=0,umax_value=2,var_off=(0x0; 0x3))
R8=inv4294967162 R9=inv(id=0) R10=fp0,call_-1 fp-48=mmmm???? fp-56=mmmmmmmm
21: (61) r1 = *(u32 *)(r9 +23)
R9 invalid mem access 'inv'
```

要解决这个问题，必须更新（重新计算） ip4 的地址：

```c
struct iphdr *ip4 = (struct iphdr *) skb->data + ETH_HLEN;

skb_store_bytes(skb, l3_off + offsetof(struct iphdr, saddr), &new_saddr, 4, 0);

ip4 = (struct iphdr *) skb->data + ETH_HLEN;

if (ip4->protocol == IPPROTO_TCP) {
    // do something
}
```



# 文档连接
[Linux BPF 3.2、BPF and XDP Reference Guide](https://www.dazhuanlan.com/2019/12/10/5dee76b007da0/)
[[译] Cilium：BPF 和 XDP 参考指南（2019）](http://arthurchiao.art/blog/cilium-bpf-xdp-reference-guide-zh/#bpf_instruction)
[BPF and XDP Reference Guide](https://docs.cilium.io/en/stable/bpf/)
[BPF 辅助函数](https://man7.org/linux/man-pages/man7/bpf-helpers.7.html)
[BPF man 文档](https://man7.org/linux/man-pages/man2/bpf.2.html)