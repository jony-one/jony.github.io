---
title: 7. eBPF 应用案例
date: 2021-05-10 13:14:19
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

















