---
title: eBPF 基本了解和入门
date: 2021-02-08 21:37:21
categories: 
	- [eBPF]
	- 导航
tags:
  - ebpf
author: Jony

---


eBPF 的 `**e**` 就是 extended 的缩写。所以 eBPF 就是，BPF 字节码过滤器的增强版。

学习建议方式
- 初学会用 `bcc` 工具
- 中级会用 `bpftrace` 开发
- 高级开发  `bcc` 工具，并贡献

Ubuntu 安装 bcc ：
```shell
sudo apt-get install bpfcc-tools linux-headers-$(uname -r)
```
其他系统安装 bcc 参考：[https://github.com/iovisor/bcc/blob/master/INSTALL.md#ubuntu---binary](https://github.com/iovisor/bcc/blob/master/INSTALL.md#ubuntu---binary)

手动抄一个 bcc 的 Hello World

```python
#!/usr/bin/python
# Copyright (c) PLUMgrid, Inc.
# Licensed under the Apache License, Version 2.0 (the "License")

# run in project examples directory with:
# sudo ./hello_world.py"
# see trace_fields.py for a longer example

from bcc import BPF

# This may not work for 4.17 on x64, you need replace kprobe__sys_clone with kprobe____x64_sys_clone
BPF(text='int kprobe__sys_clone(void *ctx) { bpf_trace_printk("Hello, World!\\n"); return 0; }').trace_print()
```
成功运行，输出结果：
```
b' IPDL Background-5703    [003] ....  5087.572783: 0: Hello, World!'
b'     Web Content-13466   [003] ....  5087.634894: 0: Hello, World!'
b' gnome-session-b-1181    [000] ....  5128.625994: 0: Hello, World!'
b'           Timer-5685    [000] ....  5140.509352: 0: Hello, World!'
b' IPDL Background-5703    [003] ....  5140.966663: 0: Hello, World!'
b' Privileged Cont-5797    [001] ....  5140.968354: 0: Hello, World!'
```

在抄一个最关心的 network：[http-parse-simple.py](https://github.com/iovisor/bcc/blob/master/examples/networking/http_filter/http-parse-simple.py)


# 参考文档

[官方文档如何与BPF子系统交互](https://www.kernel.org/doc/html/latest/bpf/bpf_devel_QA.html)
[bpf-helpers](https://man7.org/linux/man-pages/man7/bpf-helpers.7.html)
[bpf常见的设计问题](https://www.kernel.org/doc/html/latest/bpf/bpf_design_QA.html#questions-and-answers)
