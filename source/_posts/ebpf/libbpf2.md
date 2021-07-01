---
title: 理解 libbpf 译
date: 2021-06-28 19:44:19
categories: 
	- [eBPF]
tags:
  - ebpf
  - cilium
author: Jony
---

# libbpf CO-RE 原理

libbpf CO-RE 原理将软件栈所有层次 -- 内核、用户空间 BPF Loader库、编译器 进行了抽象将必要的功能、数据段整合到了一起，来降低编写可移植性 BPF 程序的难度。

1. 内核层：将 BPF 程序编译成 BTF 字节码允许捕获关于内核、BPF 程序的类型、代码等关键信息
2. Clang为BPF程序C代码提供了express the intent和记录relocation信息的手段
3. BPF Loader 根据内核版本对 BTF 进行剪裁，让其适合于当前内核版本运行
4. 整合头文件 vlinux.h ，将所需要的大多数都文件加载到期，方便程序编译

实战：bcc `http-parse-simple` 转  libbpf

调研 bcc `http-parse-simple` 功能：

eBPF应用程序，它解析HTTP包并提取(并在屏幕上打印)GET/POST请求中包含的URL。

使用方式：
  $ sudo python http-parse-complete.py 
  GET /pipermail/iovisor-dev/ HTTP/1.1
  HTTP/1.1 200 OK
  GET /favicon.ico HTTP/1.1
  HTTP/1.1 404 Not Found
  GET /pipermail/iovisor-dev/2016-January/thread.html HTTP/1.1
  HTTP/1.1 200 OK
  GET /pipermail/iovisor-dev/2016-January/000046.html HTTP/1.1
  HTTP/1.1 200 OK

实现方式
一、利用 BPF 代码，将数据包存储到 Map
二、用户空间执行额外的处理

首先使用 BPF 过滤包含 HTTP、GET、POST 等字符串 IP 和 TCP 包，以及属于同一会话的后续包，具有相同元组数据的 `ip.src,ip.dst,port.src,port.dst`

程序以：`PROG_TYPE_SOCKET_FILTER` 方式加载，嵌入到 socket 上面，绑定到 eth0

匹配的报文转发到用户空间，其他的全部删除掉

用户空间将过滤出来的数据包属于同一会话的进行重新组装。


# XDP 区别
基于 libbpf 写 XDP、Socket、tc 相关接口与写普通的 tracepoints 方式还不太一样，tracepoints 被 libbpf 封装的比较好，但是
XDP 就需要了解一下底层的方法和系统调用
大致步骤如下：
1. 编译内核 BPF 程序
2. 加载文件
3. 将 BPF attach 到指定设备

http_xdp.bpf.c

```c
/* SPDX-License-Identifier: GPL-2.0 */
#include "vmlinux.h"
#include <bpf/bpf_helpers.h>


SEC("xdp")
int xdp_prog_simple(void* ctx)
{
    bpf_printk("monitor xdp entry\n");
    return XDP_PASS;
}

char _license[] SEC("license") = "GPL";
```
执行编译命令:
```bash
clang -g -O2 -target bpf -D__TARGET_ARCH_x86 -I.output -I../../libbpf/include/uapi -I../../vmlinux/ \
-idirafter /usr/local/include \
-idirafter /usr/lib/llvm-11/lib/clang/11.1.0/include \
-idirafter /usr/include/x86_64-linux-gnu \
-idirafter /usr/include \
-c http_xdp.bpf.c \
-o .output/http_xdp.bpf.o
```


用户空间代码

```c
// SPDX-License-Identifier: (LGPL-2.1 OR BSD-2-Clause)
/* Copyright (c) 2020 Facebook */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <getopt.h>

#include <bpf/bpf.h>
#include <bpf/libbpf.h>

#include <net/if.h>
#include <linux/if_link.h>

int load_bpf_file(char* filename){
  int first_prog_fd = -1;

  struct bpf_object *obj;
  int err;

  err = bpf_prog_file(filename,BPF_PROG_TYPE_XDP,&obj,&first_prog_fd);

  if (err)
  {
    fprintf(stderr, "ERR: loading BPF-OBJ file(%s) (%d): %s\n",
      filename, err, strerror(-err));
  }
  

  return first_prog_fd;
}

int xdp_attach(int ifindex, __u32 xdp_flags, int prog_fd){
  int err;
  // 设置 link fd
  err = bpf_set_link_xdp_fd(ifindex,prog_fd,xdp_flags);
  
  if (err < 0 && !(xdp_flags & XDP_FLAGS_UPDATE_IF_NOEXIST)) {
    
    __u32 old_flags = xdp_flags;

    xdp_flags &= ~XDP_FLAGS_MODES;
    xdp_flags |= (old_flags & XDP_FLAGS_SKB_MODE) ? XDP_FLAGS_DRV_MODE : XDP_FLAGS_SKB_MODE;
    err = bpf_set_link_xdp_fd(ifindex, -1, xdp_flags);
    if (!err)
      err = bpf_set_link_xdp_fd(ifindex, prog_fd, old_flags);

  }

  if (err < 0) {
    fprintf(stderr, "ERR: "
      "ifindex(%d) link set xdp fd failed (%d): %s\n",
      ifindex, -err, strerror(-err));

    switch (-err) {
    case EBUSY:
      fprintf(stderr, "EBUSY\n");
      break;
    case EEXIST:
      fprintf(stderr, "EEXIST\n");
      break;
    case EOPNOTSUPP:
      fprintf(stderr, "EOPNOTSUPP\n");
      break;
    default:
      fprintf(stderr, "default\n");
      break;
    }
    return -1;
  }



  return 0;
}

int main(){
    int err;

  struct bpf_prog_info info = {};
  __u32 info_len = sizeof(info);
  
  // 指定 BPF 编译文件 和 设备
  char filename[256] = "http_xdp.bpf.o";
  int dev_index = 2; // eth0
  __u32 xdp_flags = XDP_FLAGS_UPDATE_IF_NOEXIST | XDP_FLAGS_DRV_MODE;

  // 加载文件
  int prog_fd = load_bpf_file(filename);
  if (prog_fd <= 0)
  {
    printf("Load BPF %s Faile.\n",filename);
    return -1;
  }
  
  // attach 文件
  err = xdp_attach(dev_index, xdp_flags, prog_fd);
  // 退出 detach

  printf("Successfully started! Please run `sudo cat /sys/kernel/debug/tracing/trace_pipe` "
         "to see output of the BPF programs.\n");


    for (;;)
  {
    sleep(1);
  }
  
  return -err;
}
```

执行编译命令：
  
```c
cc -g -Wall -I.output -I../../libbpf/include/uapi -I../../vmlinux/ -c http_xdp.c -o http_xdp.o
cc -g -Wall ./http_xdp.o /home/vagrant/libbpf-bootstrap/examples/c/.output/libbpf.a -lelf -lz -o http_xdp
```

然后运行 `http_xdp` 就可以了，通过另一个命令行可以看到 xdp 被正确加载并运行。


