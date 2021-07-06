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


上面的程序运行目前只能监控到 ingress 流量，也就是说只能监控到入口流量，不能监控到出口流量，所以为了监控到出口流量，需要重新编写新的一套工具在 tc 层做流量监控。

一开始对 tc 有点误解，认为 filter > class > qdisc ，但是通过文章 [《Linux 流量控制工具 TC 详解》](https://blog.csdn.net/wuruixn/article/details/8210760) 
了解知道，方向恰好反过来。 qdisc > class > filter 

主要入口流量分为分类队列和无类队列规定。所以 tc 层最早接入流量的应该是在 qdisc 层。有 qdisc 规定才会衍生出 class、filter。如果无 qdisc 就不能创建 class、filter 也就是不能
使用 tc 加载 bpf 程序。

编写 BPF 程序如下：

```c
// SPDX-License-Identifier: GPL-2.0 OR BSD-3-Clause
/* Copyright (c) 2020 Facebook */

#include <linux/bpf.h>
#include <bpf/bpf_helpers.h>
#include <bpf/bpf_endian.h>
#include <linux/ip.h>
#include <linux/in.h>
#include <linux/tcp.h>
#include <linux/if_ether.h>
#include <linux/pkt_cls.h>

struct eth_hdr
{
    unsigned char h_dest[ETH_ALEN];
    unsigned char h_source[ETH_ALEN];
    unsigned short h_proto;
};

SEC("ingress")
int tc_ingress(struct __sk_buff *skb)
{
    if (skb->protocol == bpf_htons(ETH_P_IP))
    {
        if (skb->len > 120)
        {
            // 获取数据位置
            void *data = (void *)(long)skb->data;
            void *data_end = (void *)(long)skb->data_end;
            
            // 获取 网卡 header
            struct eth_hdr *eth = data;
            // 获取 ip header
            struct iphdr *iph = data + sizeof(*eth);
            // 获取 tcp header
            struct tcphdr *tcp = data + sizeof(*eth) + sizeof(*iph);

            if (data + sizeof(*eth) > data_end)
            {
                return TC_ACT_OK;
            }
            // 只关心 IPv4 
            if (!!eth && eth->h_proto == bpf_htons(ETH_P_IP))
            {

                if (data + sizeof(*eth) + sizeof(*iph) > data_end)
                {
                    return TC_ACT_OK;
                }

                // 只过滤 TCP 
                if (iph->protocol != IPPROTO_TCP)
                {
                    return TC_ACT_OK;
                }

                // 判断数据是否充足
                if (data + sizeof(*eth) + sizeof(*iph) + sizeof(*tcp) + 27 > data_end)
                {
                    return TC_ACT_OK;
                }
                // 开始读取报文信息
                char *body = data + sizeof(*eth) + sizeof(*iph) + sizeof(*tcp);
                // HTTP
                if (*(body + 0) == 'H' && *(body + 1) == 'T' && *(body + 2) == 'T' && *(body + 3) == 'P')
                {
                    bpf_printk("HTTP\n");
                    goto out;
                }
                // GET 
                if (*(body + 0) == 'G' && *(body + 1) == 'E' && *(body + 2) == 'T')
                {
                    bpf_printk("GET\n");
                    goto out;
                }
                // POST
                if (*(body + 0) == 'P' && *(body + 1) == 'O' && *(body + 2) == 'S' && *(body + 3) == 'T')
                {
                    bpf_printk("POST\n");
                    goto out;
                }
                // PUT
                if (*(body + 0) == 'P' && *(body + 1) == 'U' && *(body + 2) == 'T')
                {
                    bpf_printk("PUT\n");
                    goto out;
                }
                // DELETE
                if (*(body + 0) == 'D' && *(body + 1) == 'E' && *(body + 2) == 'L' 
                && *(body + 3) == 'E' && *(body + 4) == 'T' && *(body + 5) == 'E')
                {
                    bpf_printk("DELETe\n");
                    goto out;
                }
            }
        }
    }
out:
    return TC_ACT_OK;
}

SEC("egress")
int tc_egress(struct __sk_buff *sk)
{
    return TC_ACT_OK;
}

char _license[] SEC("license") = "GPL";
```

通过以下编译命令：

```bash
clang -O2 -target bpf -D__TARGET_ARCH_x86 -I.output -I../../libbpf/include/uapi -I../../vmlinux/ -idirafter /usr/local/include -idirafter /usr/lib/llvm-11/lib/clang/11.1.0/include -idirafter /usr/include/x86_64-linux-gnu -idirafter /usr/include -c tc_http.bpf.c -o tc_http.bpf.o
```

通过命令加载：

```bash
sudo tc filter add dev eth0 ingress bpf da obj tc_http.bpf.o sec ingress
```

通过以下命令查看输出：

```bash
sudo cat /sys/kernel/debug/tracing/trace_pipe
          <idle>-0       [001] ..s. 90146.929668: 0: GET
          <idle>-0       [001] ..s. 90209.748481: 0: GET
          <idle>-0       [001] ..s. 90209.971206: 0: GET
          <idle>-0       [001] ..s. 90210.146610: 0: GET
```

如果不是用 libbpf 的话那么可以用命令行来加载，但是 libbpf 将整个过程简化了很多不需要记住这么多命令就可以直接加载到 tc 触发运行。所以需要编写用户空间代码。

先了解下如何通过指定 section 来加载 ebpf 程序：

`sec_xdp.bpf.c`
```c
#include <linux/bpf.h>
#include <bpf/bpf_helpers.h>


SEC("xdp_pass")
int xdp_pass_func(struct xdp_md *ctx)
{
    bpf_printk("XDP_PASS --------->\n");
    return XDP_PASS;
}

SEC("xpd_drop")
int xdp_drop_func(struct xdp_md *ctx)
{
    bpf_printk("XDP_DROP --------->\n");
    return XDP_PASS;
}

char _license[] SEC("license") = "GPL";
```

`sec_xdp.c` 加载程序
```c

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <getopt.h>

#include <bpf/bpf.h>
#include <bpf/libbpf.h>

#include <net/if.h>
#include <linux/if_link.h> 

#include "common_defines.h"

int xdp_link_attach(int ifindex, __u32 xdp_flags, int prog_fd)
{
  int err;

  err = bpf_set_link_xdp_fd(ifindex, prog_fd, xdp_flags);
  if (err == -EEXIST && !(xdp_flags & XDP_FLAGS_UPDATE_IF_NOEXIST)) {

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
    case EEXIST:
      fprintf(stderr, "Hint: XDP already loaded on device"
        " use --force to swap/replace\n");
      break;
    case EOPNOTSUPP:
      fprintf(stderr, "Hint: Native-XDP not supported"
        " use --skb-mode or --auto-mode\n");
      break;
    default:
      break;
    }
    return -1;
  }

  return 0;
}

int main()
{
    const char *filename = "sec_xdp.bpf.o";
    const char *progsec = "xdp_pass";
    int err;
    int prog_fd;
    int first_prog_fd = -1;
    int ifindex = 0;
    struct bpf_object *bpf_obj;
    struct bpf_program *bpf_prog;

    int xdp_flags = XDP_FLAGS_UPDATE_IF_NOEXIST | XDP_FLAGS_SKB_MODE;

    // define xdp attr
    struct bpf_prog_load_attr prog_load_attr = {
    .prog_type = BPF_PROG_TYPE_XDP,
    .ifindex   = ifindex,
  };
    prog_load_attr.file = filename;
    printf("start load file %s \n",prog_load_attr.file);


    // load xdp file
    err = bpf_prog_load_xattr(&prog_load_attr, &bpf_obj, &first_prog_fd);
    if(err < 0){
        fprintf(stderr, "ERR: loading BPF-OBJ file(%s) (%d): %s\n",
      filename, err, strerror(-err));
        goto out;
    }
    // find section 
    bpf_prog = bpf_object__find_program_by_title(bpf_obj, progsec);
    if (!bpf_prog) 
    {
        fprintf(stderr, "ERR: finding progsec: %s\n", progsec);
        goto out;
    }
  // 读取指令文件
    prog_fd = bpf_program__fd(bpf_prog);
    if (prog_fd <= 0) {
    fprintf(stderr, "ERR: bpf_program__fd failed\n");
    goto out;
  }    
    // attach xdp file
    err = xdp_link_attach(2, xdp_flags, prog_fd);
    
    if (err)
  {
        goto out;
    }

out:
    return 0;
}
```

通过运行 `sudo ./sec_xdp` 就可以观察到 xdp 程序已经按照 section 去加载了。

```bash
$ sudo ./sec_xdp                   
start load file sec_xdp.bpf.o 
libbpf: elf: skipping unrecognized data section(4) .rodata.str1.1
$ ip addr|grep xdp
2: eth0: <BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500 xdpgeneric/id:285 qdisc fq_codel state UP group default qlen 1000
$ sudo cat /sys/kernel/debug/tracing/trace_pipe
          <idle>-0       [001] ..s. 273632.203008: 0: XDP_PASS --------->
          <idle>-0       [001] ..s. 273632.203025: 0: XDP_PASS --------->
```
**`有个 ifindex 参考值，不知道是什么意思，要查`**



接下来看下如何通过 libbpf 将程序加载到 tc 层：


公共部分 `tc_http_common.h`

```c
#ifndef __TC_HTTP_COMMON
static const __u32 HTTP = 0;
static const __u32 GET = 1;
static const __u32 POST = 2;
static const __u32 PUT = 3;
static const __u32 DELETE = 4;


struct datarec {
    __u64 rx_packets;
};
#endif
```

内核处理部分： `tc_http.bpf.c`

```c

#include <linux/bpf.h>
#include <bpf/bpf_helpers.h>
#include <bpf/bpf_endian.h>
#include <linux/ip.h>
#include <linux/in.h>
#include <linux/tcp.h>
#include <linux/if_ether.h>
#include <linux/pkt_cls.h>

#include "tc_http_common.h"

struct eth_hdr
{
    unsigned char h_dest[ETH_ALEN];
    unsigned char h_source[ETH_ALEN];
    unsigned short h_proto;
};

struct bpf_map_def SEC("maps") request_map = 
{
    .type = BPF_MAP_TYPE_ARRAY,
    .key_size = sizeof(__u32),
    .value_size = sizeof(struct datarec),
    .max_entries = 64435,
};

#ifndef lock_xadd
#define lock_xadd(ptr, val) ((void) __sync_fetch_and_add(ptr, val))
#endif

SEC("classifier/read")
int tc_ingress(struct __sk_buff *skb)
{

    struct datarec *rec;    
    int err;    
    if (skb->protocol == bpf_htons(ETH_P_IP))
    {
        if (skb->len > 120)
        {
            // 获取数据位置
            void *data = (void *)(long)skb->data;
            void *data_end = (void *)(long)skb->data_end;
            
            // 获取 网卡 header
            struct eth_hdr *eth = data;
            // 获取 ip header
            struct iphdr *iph = data + sizeof(*eth);
            // 获取 tcp header
            struct tcphdr *tcp = data + sizeof(*eth) + sizeof(*iph);

            if (data + sizeof(*eth) > data_end)
            {
                return TC_ACT_OK;
            }
            // 只关心 IPv4 
            if (!!eth && eth->h_proto == bpf_htons(ETH_P_IP))
            {

                if (data + sizeof(*eth) + sizeof(*iph) > data_end)
                {
                    return TC_ACT_OK;
                }

                // 只过滤 TCP 
                if (iph->protocol != IPPROTO_TCP)
                {
                    return TC_ACT_OK;
                }

                // 判断数据是否充足
                if (data + sizeof(*eth) + sizeof(*iph) + sizeof(*tcp) + 27 > data_end)
                {
                    return TC_ACT_OK;
                }
                // 开始读取报文信息
                char *body = data + sizeof(*eth) + sizeof(*iph) + sizeof(*tcp);
                // HTTP
                if (*(body + 0) == 'H' && *(body + 1) == 'T' && *(body + 2) == 'T' && *(body + 3) == 'P')
                {
                    rec = bpf_map_lookup_elem(&request_map,&HTTP);
                    if(rec == NULL){
                        goto out;
                    }
                    lock_xadd(&rec->rx_packets, 1);
                    bpf_printk("HTTP NOT NULL  %lld\n",rec->rx_packets);
                    goto out;
                }
                // GET 
                if (*(body + 0) == 'G' && *(body + 1) == 'E' && *(body + 2) == 'T')
                {
                    rec = bpf_map_lookup_elem(&request_map,&GET);
                    if(rec == NULL){
                        goto out;
                    }
                    lock_xadd(&rec->rx_packets, 1);
                    bpf_printk("GET NOT NULL  %lld\n",rec->rx_packets);
                    goto out;
                }
                // POST
                if (*(body + 0) == 'P' && *(body + 1) == 'O' && *(body + 2) == 'S' && *(body + 3) == 'T')
                {
                    rec = bpf_map_lookup_elem(&request_map,&POST);
                    if(rec == NULL){
                        goto out;
                    }
                    lock_xadd(&rec->rx_packets, 1);
                    bpf_printk("POST NOT NULL  %lld\n",rec->rx_packets);
                    goto out;
                }
                // PUT
                if (*(body + 0) == 'P' && *(body + 1) == 'U' && *(body + 2) == 'T')
                {
                    rec = bpf_map_lookup_elem(&request_map,&PUT);
                    if(rec == NULL){
                        goto out;
                    }
                    lock_xadd(&rec->rx_packets, 1);
                    bpf_printk("PUT NOT NULL  %lld\n",rec->rx_packets);
                    goto out;
                }
                // DELETE
                if (*(body + 0) == 'D' && *(body + 1) == 'E' && *(body + 2) == 'L' 
                && *(body + 3) == 'E' && *(body + 4) == 'T' && *(body + 5) == 'E')
                {
                    rec = bpf_map_lookup_elem(&request_map,&DELETE);
                    if(rec == NULL){
                        goto out;
                    }
                    lock_xadd(&rec->rx_packets, 1);
                    bpf_printk("DELETE NOT NULL  %lld\n",rec->rx_packets);
                    goto out;
                }
            }
        }
    }
out:
    return TC_ACT_OK;
}

char _license[] SEC("license") = "GPL";
```

用户空间处理函数：`tc_http.c`
```c
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <getopt.h>
#include <sys/resource.h>

#include <bpf/bpf.h>
#include <bpf/libbpf.h>

#include <net/if.h>
#include <linux/if_link.h>

#include "tc_http_common.h"

int load_bpf_file(char *filename)
{
    return 0;
}

void find_map_value(int map_fd, __u32 key)
{
    // 查下值
    struct datarec rec;
    int err;
    err = bpf_map_lookup_elem(map_fd, &key, &rec);
    if (err == 0)
    {
        if (key == GET)
        {
            printf("GET %lld \n", rec.rx_packets);
        }

        if (key == PUT)
        {
            printf("PUT %lld \n", rec.rx_packets);
        }
        if (key == POST)
        {
            printf("POST %lld \n", rec.rx_packets);
        }
        if (key == DELETE)
        {
            printf("DELETE %lld \n", rec.rx_packets);
        }
    }
    else
    {
        fprintf(stderr, "bpf_map_lookup_elem error %d \n", err);
    }
}

int main()
{

    DECLARE_LIBBPF_OPTS(bpf_tc_hook, hook, .attach_point = BPF_TC_INGRESS);
    DECLARE_LIBBPF_OPTS(bpf_tc_opts, attach_pre);
    DECLARE_LIBBPF_OPTS(bpf_tc_opts, attach_post);

    // BPF 程序名称
    char *filename = "tc_http.bpf.o";

    // 网卡名称
    int eth_index = 2;

    // 错误码
    int err;

    // bpf map

    char *map_name = "request_map";
    struct bpf_map *map;
    int map_fd = -1;

    // open bpf file
    struct bpf_object *bpf_fd = bpf_object__open(filename);

    err = libbpf_get_error(bpf_fd);
    if (err)
    {
        fprintf(stderr, "Couldn't open file: %s\n", filename);
        goto out;
    }

    // load bpf
    err = bpf_object__load(bpf_fd);

    if (err)
    {
        fprintf(stderr, "BPF Load Faile\n");
        goto out;
    }

    // read tc_ingress section
    attach_pre.prog_fd = bpf_program__fd(bpf_object__find_program_by_name(bpf_fd, "tc_ingress"));
    if (attach_pre.prog_fd < 0)
    {
        fprintf(stderr, "Find BPF Section tc_ingress Fail\n");
        goto out;
    }

    hook.ifindex = eth_index;
    // 创建 hook 点  设置触发点
    err = bpf_tc_hook_create(&hook);
    if (err)
    {
        fprintf(stderr, "Create Hook Fail\n");
        goto out;
    }

    // attach hook
    err = bpf_tc_attach(&hook, &attach_pre);
    if (err)
    {
        fprintf(stderr, "Attach Hook Fail\n");
        goto out;
    }

    printf("Successfully started! Please run `sudo cat /sys/kernel/debug/tracing/trace_pipe` "
           "to see output of the BPF programs.\n");

    // find map
    printf("find Map \n");
    map = bpf_object__find_map_by_name(bpf_fd, map_name);
    map_fd = bpf_map__fd(map);
    if (map_fd < 0)
    {
        printf("map read fail\n");
        goto out;
    }
    printf("start epoll\n");
    while (1)
    {
        find_map_value(map_fd, GET);
        find_map_value(map_fd, PUT);
        find_map_value(map_fd, POST);
        find_map_value(map_fd, DELETE);
        printf("\n\n\n\n");
        sleep(2);
    }

out:
    bpf_object__close(bpf_fd);
    return 0;
}
```


到这里基本上已经完成了，libbpf 统计 HTTP 请求部分。也就是说基本可以读取到 L7 的数据了。


** 下一章看如何监控 URL **


编写代码和测试时常用命令：

clang -g -O2 -target bpf -D__TARGET_ARCH_x86 -I.output -I../../libbpf/include/uapi -I../../vmlinux/ \
-idirafter /usr/local/include \
-idirafter /usr/lib/llvm-11/lib/clang/11.1.0/include \
-idirafter /usr/include/x86_64-linux-gnu \
-idirafter /usr/include \
-c sec_xdp.bpf.c \
-o sec_xdp.bpf.o

cc -Wall -I../libbpf/src/build/usr/include/ -g -I../headers/ -L../libbpf/src/ -o xdp_loader ../common/common_params.o ../common/common_user_bpf_xdp.o  xdp_loader.c -l:libbpf.a -lelf 

cc -g -Wall -I.output -I../../libbpf/include/uapi -I../../vmlinux/ -c sec_xdp.c -o .output/sec_xdp.o && cc -g -Wall .output/sec_xdp.o /home/vagrant/libbpf-bootstrap/examples/c/.output/libbpf.a -lelf -lz -o sec_xdp

tc filter add dev eth0 ingress bpf da obj sec_xdp.bpf.o sec ingress

tc filter add dev eth0 ingress bpf da obj sec_xdp.bpf.o


sudo tc filter del dev eth0 ingress
sudo ip link set dev eth0 xdp off
sudo tc qdisc del dev eth0 clsact



sudo cat /sys/kernel/debug/tracing/trace_pipe
