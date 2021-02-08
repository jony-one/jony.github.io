---
title: 支持的功能
date: 2021-02-08 13:40:27
categories: ["Lord of the io_uring"]
tags:
  - io_uring
author: Jony
---

允许您检查支持的操作和功能的函数。

_struct_ ***io_uring_probe***
```c
struct io_uring_probe {
    __u8 last_op;   /* last opcode supported */
    __u8 ops_len;   /* length of ops[] array below */
    __u16 resv;
    __u32 resv2[3];
    struct io_uring_probe_op ops[0];
};
```

struct io_uring_probe_op
```c
struct io_uring_probe_op {
__u8 op;
__u8 resv;
__u16 flags;        /* IO_URING_OP_* flags */
__u32 resv2;
};
```

*struct* [io_uring_probe](https://unixism.net/loti/ref-liburing/supported_caps.html#c.io_uring_probe) \****io_uring_get_probe_ring***(*struct* [io_uring](https://unixism.net/loti/ref-liburing/setup_teardown.html#c.io_uring) \**ring*)

> **参数**

> - ring: uring 结构通过 [io_uring_queue_init()](https://unixism.net/loti/ref-liburing/setup_teardown.html#c.io_uring_queue_init) 设置

> **返回值**：成功时，返回指向 [io_uring_probe](https://unixism.net/loti/ref-liburing/supported_caps.html#c.io_uring_probe)的指针，该指针用于探测正在运行的内核的`io_uring`子系统的能力。[io_uring_probe](https://unixism.net/loti/ref-liburing/supported_caps.html#c.io_uring_probe)包含了支持的操作列表。如果失败，则返回NULL。

	> # 注意
	> 这个函数分配内存来保存io_uring_probe结构。一旦你完成了任务，你在安全的时刻需要释放它。

struct [io_uring_probe](https://unixism.net/loti/ref-liburing/supported_caps.html#c.io_uring_probe) ***\*io_uring_get_probe***(void)

> 返回指向 [io_uring_probe](https://unixism.net/loti/ref-liburing/supported_caps.html#c.io_uring_probe) 的指针，该指针用于探测运行中内核的io_uring子系统的能力。[io_uring_probe](https://unixism.net/loti/ref-liburing/supported_caps.html#c.io_uring_probe) 包含支持的操作列表。

> 这个函数和 [io_uring_get_probe_ring()](https://unixism.net/loti/ref-liburing/supported_caps.html#c.io_uring_get_probe_ring) 差不多，除了不需要你设置一个 ring 或者有一个 ring 的引用。它设置了一个临时的 ring ，这样它就可以为你获取支持的操作的细节。然后它在返回之前销毁了这个 ring 。

> # 参考
> 在运行中的内核中打印支持的io_uring操作的[示例程序](https://unixism.net/loti/tutorial/probe_liburing.html#probing-liburing)。

***

int **io_uring_opcode_supported**(struct [io_uring_probe](https://unixism.net/loti/ref-liburing/supported_caps.html#c.io_uring_probe) \*p, int op)
> 用于确定内核是否支持io_uring操作的函数。如果不支持该操作，则返回0，如果支持则返回非零值。请看一下支持的操作示例程序来了解这个函数的作用。
>
> **参数**
>  p: 指向 io_uring_probe 结构体
> op: 要检查支持的操作。一个IO_URING_OP_*宏。
> **返回值**
> 如果不支持，则为0，否则为1。

***

int **io_uring_register_probe**(struct [io_uring](https://unixism.net/loti/ref-liburing/setup_teardown.html#c.io_uring) \*ring, struct [io_uring_probe](https://unixism.net/loti/ref-liburing/supported_caps.html#c.io_uring_probe) \*p, unsigned nr)

> 让你获得 `io_uring` 功能的底层接口。
> **参数**
> ring: **[io_uring_queue_init()](https://unixism.net/loti/ref-liburing/setup_teardown.html#c.io_uring_queue_init)** 设置的 **[io_uring](https://unixism.net/loti/ref-liburing/setup_teardown.html#c.io_uring)** 结构
>    p: 指向 `io_uring_probe` 结构的指针
>   nr: 指向的`p`数组结构的数目
> **返回值**: 成功时返回0，失败时返回 `-errono` 。你可以使用 [strerror(3)](http://man7.org/linux/man-pages/man3/strerror.3.html) 来获得一个可读的失败原因版本。
