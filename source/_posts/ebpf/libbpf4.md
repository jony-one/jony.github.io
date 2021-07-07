---
title: 用ebpf编写网络监控
date: 2021-07-07 14:58:19
categories: 
	- [eBPF]
tags:
  - ebpf
  - cilium
author: Jony
---

# 用ebpf 编写网络监控

网络流量将空应该在 raw_tracepoint 监控。
参考文档：[bpf: revolutionize bpf tracing](https://lwn.net/Articles/801992/)、[bpf, tracing: introduce bpf raw tracepoints](https://lwn.net/Articles/748352/)

SO  网络流量监控并不一定要在入口和出口才可以去监控，这里将介绍一个早就已经存在得技术，但是不常见得东东，`kfree_skb` 释放 `skb_buff` ，
方法名就可以看出用处。

开始之前来复习下如何 trace 内核方法，之前都没有关注过，一直认为不会与内核打交道，看了程序得世界里不会有这种假设了，程序员要死于学习。
分析下现有得程序吧。代码流程应该与前面得类似

猜测中
加载器
1. 指定 BPF 程序
2. 打开 BPF 程序
3. 读取 section ，按照 section 读取 program
4. attach 到函数入口
5. 获取所有 map_fd 
6. pin 住 map

BPF 程序
1. 找到入参
2. 分析入参
3. 函数返回值是否参与决断，根据情况返回返回值

看看简单得如下程序：

minimal.bpf.c
```c
#include <linux/bpf.h>
#include <bpf/bpf_helpers.h>

char LICENSE[] SEC("license") = "Dual BSD/GPL";

int my_pid = 0;

SEC("tp/syscalls/sys_enter_write") // 指定是 tracepoint:syscalls:sys_enter_write
int handle_tp(void *ctx)  // 因为不需要分析参数所以这里就直接使用了 void * 任何参数类型
{
  int pid = bpf_get_current_pid_tgid() >> 32;

  if (pid != my_pid)
    return 0;

  bpf_printk("BPF triggered from PID %d.\n", pid);

  return 0;
}
```

大致调用编译得方式如下：

1. 因为程序依赖 libbpf 所以先将 libbpf 编译为库文件
```c
mkdir -p .output

mkdir -p .output/libbpf

make -C /home/vagrant/libbpf-bootstrap/libbpf/src BUILD_STATIC_ONLY=1                    \
            OBJDIR=/home/vagrant/libbpf-bootstrap/examples/c/.output//libbpf DESTDIR=/home/vagrant/libbpf-bootstrap/examples/c/.output/                     \
            INCLUDEDIR= LIBDIR= UAPIDIR=                              \
            install
```








# 常用命令合集

clang -g -O2 -target bpf -D__TARGET_ARCH_x86 -I.output -I../../libbpf/include/uapi -I../../vmlinux/ \
-idirafter /usr/local/include \
-idirafter /usr/lib/llvm-11/lib/clang/11.1.0/include \
-idirafter /usr/include/x86_64-linux-gnu \
-idirafter /usr/include \
-c url_map.bpf.c \
-o url_map.bpf.o


cc -g -Wall -I.output -I../../libbpf/include/uapi -I../../vmlinux/ -c url_map_read.c -o .output/url_map_read.o && cc -g -Wall .output/url_map_read.o /home/vagrant/libbpf-bootstrap/examples/c/.output/libbpf.a -lelf -lz -o url_map_read

cc -g -Wall -I.output -I../../libbpf/include/uapi -I../../vmlinux/ -c url_map.c -o .output/url_map.o && cc -g -Wall .output/url_map.o /home/vagrant/libbpf-bootstrap/examples/c/.output/libbpf.a -lelf -lz -o url_map

tc filter add dev eth0 ingress bpf da obj url_map.bpf.o sec ingress
tc filter add dev eth0 ingress bpf da obj url_map.bpf.o

sudo tc filter del dev eth0 ingress
sudo ip link set dev eth0 xdp off
sudo tc qdisc del dev eth0 clsact

sudo tc qdisc add dev eth0 clsact
sudo tc qdisc del dev eth0 ingress
sudo ip link set dev eth0 xdp off && sudo tc qdisc del dev eth0 clsact && sudo ./url_map


sudo cat /sys/kernel/debug/tracing/trace_pipe



git pull && clang -g -O2 -target bpf -D__TARGET_ARCH_x86 -I.output -I../../libbpf/include/uapi -I../../vmlinux/ -idirafter /usr/local/include -idirafter /usr/lib/llvm-11/lib/clang/11.1.0/include -idirafter /usr/include/x86_64-linux-gnu -idirafter /usr/include -c url_map.bpf.c -o url_map.bpf.o && sudo ip link set dev eth0 xdp off && sudo tc qdisc del dev eth0 clsact && sudo ./url_map


git pull && cc -g -Wall -I.output -I../../libbpf/include/uapi -I../../vmlinux/ -c url_map_read.c -o .output/url_map_read.o && cc -g -Wall .output/url_map_read.o /home/vagrant/libbpf-bootstrap/examples/c/.output/libbpf.a -lelf -lz -o url_map_read && sudo ./url_map_read
