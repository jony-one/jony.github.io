---
title: 用 ebpf 监控 URL 
date: 2021-07-05 14:06:19
categories: 
	- [eBPF]
tags:
  - ebpf
  - cilium
author: Jony
---

# 用 ebpf 监控 URL 

了解 ebpf 监控 URL 之前，我们需要先知道如何将 map 读写分离，所谓的读写分离规则就是，腿在这人走了。

之前的所有程序都是通过加载器加载之后返回一个 `bpf_object` 然后通过 `bpf_object` 去查找 `bpf_map_fd` 。在程序内部读取
但是程序内部读取数据高度耦合，显然不符合现在的程序设计，所以需要通过解耦也就是将 `map` 提取出来之后，任何程序都可以读取。
这里就需要知道一个概念 `pin`。 `pin` 的意思就是固定。

回头看看最初的理论设计 [[译] Cilium：BPF 和 XDP 参考指南（2019）](http://arthurchiao.art/blog/cilium-bpf-xdp-reference-guide-zh/#bpf_arch)

# 复习理论

## BPF 寄存器和调用规定

BPF 有几个部分组成：
1. 11 个 64 位寄存器（包含 32 位的）
2. 一个程序计数器 PC
3. 一个 512 字节的 BPF 栈空间

寄存器名字 `r0-r10` 。默认运行模式 64位，32 位访问模式通过 ALU 访问。
各寄存器作用：
- r10: 唯一的只读寄存器，用于存放 BPF `栈空间`的`栈帧指针`地址
- r0: 存放`辅助函数`的`返回值`
- r1-r5: 存放调用辅助函数时传递的`参数`
- r6-r9:  由被调用方（callee）保存，在函数返回之后调用方（caller）可以读取 **`（没说明白，没用到）`**



## BPF 指令格式

每个指定由 64 位 比特编码：
`op:8, dst_reg:4, src_reg:4, off:16, imm:32`

-  dst_reg、src_reg ：提供了寄存器操作数
- off：用于表示一个相对偏移量（offset）
- imm：存储一个常量/立即值
- op:将要执行的操作 可以继续拆分 **从 MSB(Min) 到 LSB(Large)** :`code:4, source:1 和 class:3`
  - class 是指令类型，类别由如下：
    - BPF_LD, BPF_LDX：加载操作
    - BPF_ST, BPF_STX：存储操作
    - BPF_ALU, BPF_ALU64：逻辑运算操作
    - BPF_JMP：跳转操作
  - code 指特定类型的指令中的某种特定操作码
  - source 可以告诉我们源操作数是一个寄存器还是一个立即数

## 辅助函数

辅助函数（Helper functions）使得 BPF 能够通过一组内核定义的函数调用来从内核中查询数据，或者将数据推送到内核。
所有的辅助函数都共享同一个通用的、和系统调用类似的函数签名。签名定义如下：
  u64 fn(u64 r1, u64 r2, u64 r3, u64 r4, u64 r5)

内核将辅助函数抽象成 BPF_CALL_0() 到 BPF_CALL_5() 几个宏，形式和相应类型的系 统调用类似。

```c
BPF_CALL_4(bpf_map_update_elem, struct bpf_map *, map, void *, key,
           void *, value, u64, flags)
{
    WARN_ON_ONCE(!rcu_read_lock_held());
    return map->ops->map_update_elem(map, key, value, flags);
}

const struct bpf_func_proto bpf_map_update_elem_proto = {
    .func           = bpf_map_update_elem,
    .gpl_only       = false,
    .ret_type       = RET_INTEGER,
    .arg1_type      = ARG_CONST_MAP_PTR,
    .arg2_type      = ARG_PTR_TO_MAP_KEY,
    .arg3_type      = ARG_PTR_TO_MAP_VALUE,
    .arg4_type      = ARG_ANYTHING,
};
```
所有的 BPF 辅助函数都是核心内核的一 部分，无法通过内核模块（kernel module）来扩展或添加。


## Maps

map 是驻留在内核空间中的高效键值仓库。map 还可以从用户空间通过文件描述符访问，可以在任意 BPF 程序以及用 户空间应用之间共享。

**所以看见没有这里就是我们想要的一个概念** 

`单个 BPF 程序目前最多可直接访问 64 个不同 map。` 


## Object Pinning (Pin 住对象)

BPF map 和程序作为内核资源只能通过文件描述符访问，其背后是内核中的匿名 inode。 使用过文件描述符的访问的优缺点如下：

优点：
- 用户空间应用能够使用大部分文件描述符相关的 API，传递给 Unix socket 的文 件描述符是透明工作的等等

缺点：
- 文件描述符受限于进程的生命周期，使得 map 共享之类的操作非常笨重。

问题场景： iproute2，其中的 tc 或 XDP 在准备 环境、加载程序到内核之后因为某些原因导致程序退出。在这种情况下，从用户空间也无法访问这些 map 了。

解决方法：内核实现了一个最小内核空间 BPF 文件系统，BPF map 和 BPF 程序 都可以钉到（pin）这个文件系统内，这个过程称为 object pinning
具体实施：BPF 系统调用进行了扩展添加了两个新命令，分别用于钉住`（BPF_OBJ_PIN）`一个 对象和获取`（BPF_OBJ_GET）`一个 pinned objects
使用技巧：例如，tc 之类的工具可以利用这个基础设施在 ingress 和 egress 之间共享 map。
其他信息：BPF 相关的文件系统不是单例模式（singleton），它支持多挂载实例、硬链接、软连接等 等。
默认路径：被 pin 的对象的文件路径：`/sys/fs/bpf/tc/globals/ 、/sys/fs/bpf/xdp/globals/ 、/sys/fs/bpf/ip/globals/`

# 限制

**限制**：不支持共享库（Shared libraries）。
解决方案：可以将常规的库代码（library code）放 到头文件中，然后在主程序中 include 这些头文件

**限制**：多个程序可以放在同一 C 文件中的不同 section



**限制**：不允许全局变量
解决方案：可以使用 BPF_MAP_TYPE_PERCPU_ARRAY 类型的 BPF map

**限制**：不支持常量字符串或数组,BPF C 程序中不允许定义 const 字符串或其他数组，重定位项会被加载器拒绝
解决方案：将来 LLVM 可能会检测这种情况，提前将错误抛给用户。用户自行解决

**限制**：BPF 程序除了调用 BPF 辅助函数之外无法执行任何函数调用
解决方案：常规的库代码必须 实现为内联函数
其他方案：LLVM 提供了一些可以用于特定大小的内置函数 ，这些函数永远都会被内联，如下

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

**限制** ： BPF 最大栈空间 512 字节
解决方案：以通过一个只有一条记录的 BPF_MAP_TYPE_PERCPU_ARRAY map 来绕过这限制，增大 scratch buffer 空间。


**限制**：未验证的引用访问包数据，某些网络相关的 BPF 辅助函数，可能会修改包。校验器无法跟踪这类改动，因此它会将所有之前对包数据的引用都视为过期
解决方案：为避免程序被校验器拒绝，在访问数据之外需要先**更新**相应的引用



# 技巧
技巧：LLVM 将 \_\_sync_fetch_and_add() 作为一个内置函数映射到 BPF 原子加指令，即 BPF_STX | BPF_XADD | BPF_W（for word sizes）

调试程序： `trace_printk` 输出会写到 trace pipe，用 tc exec bpf dbg 命令可以获取这些打印的消息。

调试程序：还推荐使用 `skb_event_output()` 或 `xdp_event_output()` 辅助函数。
为什么？
因为这两个函数接受从 BPF 程序 传递自定义的结构体类型参数，然后将参数以及可选的包数据（packet sample）放到 perf event ring buffer。




# 尾调用

尾调用能够从一个程序调到另一个程序，提供了在运行时（runtime）原子地改变程序行 为的灵活性。为了选择要跳转到哪个程序，
尾调用使用了 程序数组 `map（ BPF_MAP_TYPE_PROG_ARRAY）`，将 map 及其索引（index）传递给将要跳转到的程序。
**跳转动作一旦完成，就没有办法返回到原来的程序**
**如果给定的 map 索引中没有程序（无 法跳转），执行会继续在原来的程序中执行**




看了那么多就操作 Map Pin 操作，Map pin 操作相当于持久化 BPF Map。

接着上面一篇的文章：

`pin_map_read.c` 将读取 Map 的代码功能单独领出来，本以为 open_bpf_map_file 是 libbpf 封装的库函数，运行之后发现不对，就直接 copy 了一份过来
```c

/* SPDX-License-Identifier: GPL-2.0 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <getopt.h>

#include <locale.h>
#include <unistd.h>
#include <time.h>

#include <bpf/bpf.h>

#include <net/if.h>
#include <linux/if_link.h> 


#include "tc_http_common.h"


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


int open_bpf_map_file(const char *pin_dir,
          const char *mapname,
          struct bpf_map_info *info)
{
  char filename[4096];
  int err, len, fd;
  __u32 info_len = sizeof(*info);

  len = snprintf(filename, 4096, "%s/%s", pin_dir, mapname);
  if (len < 0) {
    fprintf(stderr, "ERR: constructing full mapname path\n");
    return -1;
  }

  fd = bpf_obj_get(filename);
  if (fd < 0) {
    fprintf(stderr,
      "WARN: Failed to open bpf map file:%s err(%d):%s\n",
      filename, errno, strerror(errno));
    return fd;
  }

  if (info) {
    err = bpf_obj_get_info_by_fd(fd, info, &info_len);
    if (err) {
      fprintf(stderr, "ERR: %s() can't get info - %s\n",
        __func__,  strerror(errno));
      return err;
    }
  }

  return fd;
}

int main(){

    struct bpf_map_info info = { 0 };
    int map_fd;

    const char *map_path = "/sys/fs/bpf/eth0";
    const char *map_name = "request_map";


    map_fd = open_bpf_map_file(map_path, map_name, &info);
    if (map_fd < 0) {
        printf("open map fail\n");
    goto out;
  }

    while (1) {
        find_map_value(map_fd, GET);
        find_map_value(map_fd, PUT);
        find_map_value(map_fd, POST);
        find_map_value(map_fd, DELETE);
        printf("\n\n\n\n");
    sleep(2);
  }
out:
    return  0;
}
```
大概流程就是：
1. 给出 map 文件的绝对路径
2. 根据文件名称获取文件标识符，也就是打开了文件
3. 获取文件的基本信息
4. 读取文件信息

将加载 BPF 程序单独令出来，如下:
```c
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <getopt.h>
#include <sys/resource.h>

 #include <unistd.h>

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
    char *filename = "pin_map.bpf.o";

    // 网卡名称
    int eth_index = 2;

    // 错误码
    int err;

    // bpf map

    char *map_name = "request_map";
    // struct bpf_map *map;
    // int map_fd = -1;

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


    // 定住 map
    printf("pin map\n");
    char pin_dir[4096];
    char map_filename[4096];
    int len;
    char *pin_basedir = "/sys/fs/bpf";
    char *subdir = "eth0";
    // 创建文件夹
    len = snprintf(pin_dir, 4096, "%s/%s", pin_basedir, subdir);
    // 创建文件
    len = snprintf(map_filename, 4096, "%s/%s/%s",pin_basedir, subdir, map_name);
    // 检查 map 是否存在过
    if (access(map_filename, F_OK ) != -1 ) {
        err = bpf_object__unpin_maps(bpf_fd,pin_dir);
        if (err) {
      fprintf(stderr, "ERR: UNpinning maps in %s\n", pin_dir);
      goto out;
    }
    }
    // pin bpf 程序中的所有 map
    err = bpf_object__pin_maps(bpf_fd,pin_dir);
    if (err) {
      fprintf(stderr, "ERR: Pin maps in %s\n", pin_dir);
      goto out;
  }
    printf("BPF OK\n");
    return 0;
out:
    bpf_object__close(bpf_fd);
    return 0;
}
```
这个代码和之前的代码差不多。多加了两行代码，通过 `snprintf` 创建文件，通过 `bpf_object__pin_maps` 将所有 map 固定住，成为一个虚拟文件。这个时候就算 BPF 程序退出了
也不会影响到其他应用对 Map 的读取。


BPF 程序和之前的一样，复制过来就好了。
然后先运行加载程序，在运行读取程序就可以看见输出：
```bash
GET 24
PUT 0
POST 11
DELETE 2
```

实现了，对 map 文件的解耦，也就是说通过 loader 加载程序之后，可以通过任何语言调用 BPF 辅助函数对数据的查看。

# 实现URL 识别

上面的代码对  URL 识别有什么帮助，很明显就是解耦，将内核数据复制到用户空间处理，用户空间的程序肯定不止 C 所以相当于对外面开放了 API　。只要对上面的程序稍微修改就可以完成对　URL 的识别。


# ~~ 思路错误，开发得地方不应是在 ingress 或者 egress  ，在这个地方做侵入式得监控，严重影响性能，应该做擅长得决断操作  ~~

#  下一篇分析 监控 sk_buff 流量 


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


cc -g -Wall -I.output -I../../libbpf/include/uapi -I../../vmlinux/ -c pin_map_read.c -o .output/pin_map_read.o && cc -g -Wall .output/pin_map_read.o /home/vagrant/libbpf-bootstrap/examples/c/.output/libbpf.a -lelf -lz -o pin_map_read

tc filter add dev eth0 ingress bpf da obj sec_xdp.bpf.o sec ingress

tc filter add dev eth0 ingress bpf da obj sec_xdp.bpf.o


sudo tc filter del dev eth0 ingress
sudo ip link set dev eth0 xdp off
sudo tc qdisc del dev eth0 clsact



sudo cat /sys/kernel/debug/tracing/trace_pipe









