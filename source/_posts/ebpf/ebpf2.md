---
title: 7. eBPF 应用案例
date: 2021-05-17 13:14:19
categories: 
	- [eBPF]
tags:
  - ebpf
author: Jony
---


# 实践

在 `ebpf` 实践中遇到了一些问题。
首先是在虚拟机上支持不了 `xdpdrv` 让人很头大，只能通过 `xdpgeneric`

另外在加载 `tc` 层的时候由于没有指定 `classifier` 直接报错，让我排查了一天都不知道什么问题。现场还原：
在编辑完 `c` 代码以后编译成二进制文件，然后使用下面的命令加载到 `tc` 层：

`sudo tc filter add dev ens33 ingress bpf da obj tc-example.o`

然后报错：

```bash
Program section 'classifier' not found in ELF file!
Error fetching program/map!
Unable to load program
```

命名修改后：

`sudo tc filter add dev ens33 ingress bpf da obj tc-example.o  sec ingress`

没有任何提示，查看一下是否加载成功

```bash
$:tc filter show dev ens33 ingress
filter protocol all pref 49151 bpf chain 0 
filter protocol all pref 49151 bpf chain 0 handle 0x1 tc-example.o:[ingress] direct-action not_in_hw id 21 tag c5f7825e5dac396f 
```



在做 JIT 反编译的时候遇到如下问题：

```base
sudo bpftool prog dump jited id 20
Error: No libbfd support

```

此问题未解决，没有怎么查找资料，不影响主流程

使用 clang 单独编译一个文件参考：[运行第一个 bpf 程序](https://blog.csdn.net/Longyu_wlz/article/details/109900096)

了解 libbfd 是啥？
libbfd 二进制文件描述器，在安装 binutils 工具后选择性安装。利用 libbfd 可以获取 elf 的 section 以及 symbol 信息。
[bpf库](https://blog.csdn.net/zxremail/article/details/5192400)

运行加载器遇到问题:libbpf: BTF is required, but is missing or corrupted.
搜索出来的结果都指向了内核bug 行为。
[内核 patch](https://yhbt.net/lore/all/20210319205909.1748642-1-andrii@kernel.org/T/)
通过内核 patch 觉得好像是 map 声明出现了问题，内校验器拒绝。所以map 只要从
```c
struct {
	__uint(type, BPF_MAP_TYPE_ARRAY);
	__type(key, __u32);
	__type(value, long);
	__uint(max_entries, 256);
} my_map SEC(".maps");
```
改为:

```c
struct bpf_map_def SEC("maps") my_map = {
	__uint(type, BPF_MAP_TYPE_ARRAY);
	__type(key, u32);
	__type(value, long);
	__uint(max_entries, 256);
};
```
就会报另一个错:`libbpf: object file doesn't contain bpf program`

思路一时想不起来，一开始看上面的定义发现好像使用来定义一个map的形式，而导致报上面的错。然后翻了一下资料使用 map 的方式。就改成了如下的形式：
```c
struct bpf_map_def SEC("maps") my_map =  {
	.type = BPF_MAP_TYPE_ARRAY,
	.key_size = sizeof(__u32),
	.value_size = sizeof(long),
	.max_entries = 256,
};
```
然后就可以正常运行了， 很 Nice .
程序就是内核代码下的 `sockex1_kern.c` 的代码，只要修改一下 map 的定义方式就可以，该程序用于统计字节数。




