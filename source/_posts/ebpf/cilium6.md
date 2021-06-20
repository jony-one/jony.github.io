---
title: Cilium 功能阅读：如何制定和执行 L3 策略 续续续
date: 2021-06-14 19:44:19
categories: 
	- [eBPF]
tags:
  - ebpf
  - cilium
author: Jony
---



# Cilium 源码阅读： 如何制定和执行 L3 策略 续续续 解读 eBPF 执行

## High-level

**api**
  Cilium & Hubble API 定义
**bpf**
  BPF数据路径代码
**bpftool**
  命令行收集代理和系统信息，用于bug报告
**cilium**
  Cilium CLI客户端
**contrib, tools**
  用于开发的其他工具和资源
**daemon**
  在每个节点上运行的cilium代理
**examples**
  各种示例资源和清单。通常需要修改才能使用。
**hubble-relay**
  哈勃中继服务器
**install**
  Helm 所有组件的部署清单  
**pkg**
  所有组件之间共享的通用Go包
**operator**
  负责集中任务的操作员，这些任务不需要在每个节点上执行。
**plugins**
  与Kubernetes和Docker集成的插件
**test**
  端到端集成测试在端到端测试框架中运行。  


## Cilium

**api/v1/openapi.yaml**
  Cilium的API规范。用于代码生成。
**api/v1/models/**
  从代表所有API资源的openapi.yaml生成的Go代码
**bpf**
  BPF数据路径代码  
**cilium**
  Cilium CLI客户端
**cilium-health**
  Cilium集群连接的CLI客户端
**daemon**
  cilium-agent的具体代码    
**plugins/cilium-cni**
  与Kubernetes集成的CNI插件
**plugins/cilium-docker**
  Docker集成插件

# 重要的通用包  

**pkg/allocator**
  安全身份分配
**pkg/bpf**
  抽象层，与BPF运行时进行交互  
**pkg/client**
  访问Cilium API的Go客户端
**pkg/clustermesh**
  多集群实现包括控制平面和全局服务
**pkg/controller**
  任何需要重试或基于间隔的调用的后台操作的基本控制器实现。
**pkg/datapath**
  用于数据通路交互的抽象层
**pkg/default**
  所有默认值
**pkg/elf**
  用于BPF加载器的ELF抽象库
**pkg/endpoint**
  对Cilium endpoint 的抽象，代表所有的工作负载。
**pkg/endpointmanager**
  管理所有 Endpoint
**pkg/envoy**
  Envoy 集成代理
**pkg/fqdn**
  FQDN 代理 和 FQDN 策略实现
**pkg/health**
  网络连接健康检查
**pkg/identity**
    代表工作负载的安全身份
**pkg/ipam**
  IP 地址管理
**pkg/ipcache**
  全局缓存将IP映射到端点和安全标识
**pkg/k8s**
  与Kubernetes的所有交互
**pkg/kafka**
  Kafka协议代理和策略实现
**pkg/kvstore**
  带etcd和consul后端的键值存储抽象层
**pkg/labels**
  基本元数据类型，用于描述工作负载标识规范和策略匹配的所有标签/元数据要求。
**pkg/loadbalancer**
  用于负载平衡功能的控制平面
**pkg/maps**
  BPF map 表述
**pkg/metrics**
  Prometheus 指标实现
**pkg/monitor**
  BPF数据路径监视抽象
**pkg/node**
  网络节点的表示
**pkg/option**
  所有可用配置选项
**pkg/policy**
  策略实施规范与实施
**pkg/proxy**
  第7层代理抽象
**pkg/service**
  负载均衡 service 的表示
**pkg/trigger**
  实现触发器功能以实现事件驱动功能



一般代码都  attach 到内核中，所以直接看前一页的内容就基本知道了 哪些  section。

bpf 下主要有：bpf_host.c、bpf_lxc.c、bpf_network.c、bpf_overlay.c、bpf_sock.c、bp_xdp.c 几个文件

从架构图上看在底层起到拦截作用的应该是 bpf_lxc.c 看下面代码执行：

```c
__section("from-container")
int handle_xgress(struct __ctx_buff *ctx)
{

  __u16 proto;
  int ret;

...
  switch (proto) {
...
  case bpf_htons(ETH_P_IP):
    invoke_tailcall_if(__or(__and(is_defined(ENABLE_IPV4), 
           is_defined(ENABLE_IPV6)),is_defined(DEBUG)),
           CILIUM_CALL_IPV4_FROM_LXC, tail_handle_ipv4);
    break;
    ...
}
  return ret;
}

declare_tailcall_if(__or(__and(is_defined(ENABLE_IPV4), is_defined(ENABLE_IPV6)),
       is_defined(DEBUG)), CILIUM_CALL_IPV4_FROM_LXC)
int tail_handle_ipv4(struct __ctx_buff *ctx)
{
  __u32 dstID = 0;
  int ret = handle_ipv4_from_lxc(ctx, &dstID);
...
  return ret;
}

static __always_inline int handle_ipv4_from_lxc(struct __ctx_buff *ctx,
            __u32 *dstID)
{
  ...
    struct remote_endpoint_info *info;

    info = lookup_ip4_remote_endpoint(orig_dip);
    if (info != NULL && info->sec_label) {
      *dstID = info->sec_label;
      tunnel_endpoint = info->tunnel_endpoint;
      encrypt_key = get_min_encrypt_key(info->key);
    } else {
      *dstID = WORLD_ID;
    }

  verdict = policy_can_egress4(ctx, &tuple, SECLABEL, *dstID,
             &policy_match_type, &audited);

}  
// lookup_ip4_remote_endpoint ----->>>>>> ipcache_lookup4

static __always_inline __maybe_unused struct remote_endpoint_info *
ipcache_lookup4(struct bpf_elf_map *map, __be32 addr, __u32 prefix)
{
  struct ipcache_key key = {
    .lpm_key = { IPCACHE_PREFIX_LEN(prefix), {} },
    .family = ENDPOINT_KEY_IPV4,
    .ip4 = addr,
  };
  key.ip4 &= GET_PREFIX(prefix);
  return map_lookup_elem(map, &key);
}


static __always_inline int
__policy_can_access(const void *map, struct __ctx_buff *ctx, __u32 localID,
        __u32 remoteID, __u16 dport, __u8 proto, int dir,
        bool is_untracked_fragment, __u8 *match_type)
{
  struct policy_entry *policy;
  struct policy_key key = {
    .sec_label = remoteID,
    .dport = dport,
    .protocol = proto,
    .egress = !dir,
    .pad = 0,
  };
  // Start with L3/L4 lookup. 
  // L4-only lookup.
  // If L4 policy check misses, fall back to L3.
  // Final fallback if allow-all policy is in place.
}

```

禁止访问的流程就是这么多了，还是用 c 语言写出的代码比较精简，易懂。
流程与之前分析的差不多。流量进入了之后根据目前的网络协议发起网络尾调用，先通过 lookup_ip4_remote_endpoint 查出来源IP的唯一标识，这里查的就是 ipcache。如果没查到
就直接判断失败，也就是不允许访问。（PS：这里感觉有bug，或者设计的问题，因为自己访问自己都访问不通）。如果查出了之后会调用 policy_can_egress4 查看策略是否允许访问。
1. 先从已经跟踪的 ct 进行 L3L4 联合查找、没找到就只从 L4 查找，
2. 如果不是已经跟中的 ct 则进行 L3 层 查找
3. 如果都找不到最后就会在是否允许所有流量中进行查找。

c 这边处理的相对要简单。所以重点应该还是上层的架构设计，繁杂适合多样性。


后面可能需要分析 ct 的概念和监控的概念。

完结

  







