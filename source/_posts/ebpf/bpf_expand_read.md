---
title: eBPF 扩展阅读-BPF Map
date: 2021-02-25 16:26:47
categories: 
	- [eBPF]
tags:
  - ebpf
author: Jony
---

# eBPF 扩展阅读 - BPF Maps

通过向程序传递消息而引起程序行为被调用，这种方式在软件工程中使用广泛。BPF 的功能之一就是内核中运行的代码和加载这些代码的程序可以通过**消息传递**的方式实现实时通信。

BPF Map 以键/值保存在内核中，可以被任何 BPF 程序访问。**用户空间** 使用文件描述符的方式访问 BPF Map。BPF Map 中可以保存任何类型的数据。本质是是保存的**二进制数据**，所以内核并不关心 BPF Map  保存的内容。

## 1. 创建 BPF Maps

创建 BPF Maps 最直接的方法是使用 bpf 系统调用。

```c
int bpf(int cmd, union bpf_attr *attr, unsigned int size );
```

cmd是eBPF支持的cmd，分为三类： 操作注入的代码、操作用于通信的map、前两个操作的混合。
创建一个新的 Map ：

**第一个参数**：BPF_MAP_CREATE 表示创建一个新的 Map 。调用后将返回与创建 Map 相关的文件描述符。
**第二个参数**是 Map 的设置。如下：

```c
union bpf_attr {
	struct 
	{
		__u32 map_type;
		__u32 key_size;
		__u32 value_size;
		__u32 max_entries;
		__u32 map_flags;
	};
}
```
**第三个参数**是设置属性的大小。

实际操作创建一个 Key 和 Value 为无符号整数的哈希表 Map：

```c
union bpf_attr my_map {
	.map_type = BPF_MAP_TYPE_HASH,
	.key_size = sizeof(int),
	.value_size = sizeof(int),
	.max_entries = 100,
	.map_flags = BPF_F_NO_PREALLOC,
};

int fd = bpf(BPF_MAP_CREATE, &my_map, sizeof(my_map));
```
如果系统调用失败，内核将会返回 `-1`,失败有三种原因，可以通过 `errno` 来区分你：
- 属性无效：内核将 `errno` 变量设置为 `EINVAL`
- 无权操作：内核将 `errno` 变量设置为 `EPERM`
- 内存不足：内核将 `errno` 变量设置为 `ENOMEM`

---

当然内核也包含了一些约定和帮助函数，用于生成和使用 BPF Map。调用这些约定比直接执行系统调用更为常用，因为约定的方式更具有可读性且更易遵循。但是这些约定底层仍然是通过 bpf 系统调用来创建 `Map`。比如 `bpf_create_map` 就封装了上述的编写的代码，如下：

```c
int fd;
fd = bpf_create_map(BPF_MAP_TYPE_HASH, sizeof(int), sizeof(int), 100, BPF_F_NO_PREALLOC);
```

---

当然如果事先知道将要使用的 Map 类型的话，我们也可以预先定义 Map。让程序更易于理解：

```c
struct bpf_map_def SEC("maps") my_map = {
	.map_type = BPF_MAP_TYPE_HASH,
	.key_size = sizeof(int),
	.value_size = sizeof(int),
	.max_entries = 100,
	.map_flags = BPF_F_NO_PREALLOC,
};
```

这种方式使用 `section` 属性来定义 Map，本示例中为 `SEC("maps")`。这个`宏`告诉内核该结构是 BPF Map ，并告诉内核创建相应的 Map。使用这种方式的创建 Map 如果需要使用与之想关联的文件描述符可以使用如下方式：

```c
int fd = map_data[0].fd;
```

因为内核使用 `map_data	` 全局变量来保存 BPF 程序 Map 信息。这个变量是数组结构，存储的内容是按照程序中指定 Map 的顺序进行排序。
初始化完成后，就可以使用它们在**内核和用户空间**之间传递消息。

## 使用 Map















## 参考文档
(BPF系统调用API v14)[https://www.cnblogs.com/zhangzl2013/p/4008838.html]
(Linux内核功能eBPF入门学习)[https://blog.csdn.net/eydwyz/article/details/107983479]