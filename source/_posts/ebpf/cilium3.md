---
title: Cilium 解析阅读：如何制定和执行 L3 策略
date: 2021-05-20 19:44:19
categories: 
	- [eBPF]
tags:
  - ebpf
  - cilium
author: Jony
---



# Cilium 源码阅读：如何制定和执行 L3 策略

#NOTE： 在 docker 部署下 debug

**我认为理解 eBPF 代码还比较简单，多看看内核代码就行了，但配置和编写 eBPF 就要难多了。**

Cilium 提供了 CNI 和 kube-proxy replacement 功能，相比 iptable 性能要好很多当前代码复杂度也上升了。

本文做了一个官方 Demo  顺便解析一下代码以把整个过程透明化：

Demo 参考：[https://docs.cilium.io/en/v1.8/gettingstarted/docker/](https://docs.cilium.io/en/v1.8/gettingstarted/docker/)

看下 `cilium policy import l3_l4_policy.json` 得处理流程：

```json
[{
	"labels": [{
		"key": "name",
		"value": "l3-rule"
	}],
	"endpointSelector": {
		"matchLabels": {
			"id": "app1"
		}
	},
	"ingress": [{
		"fromEndpoints": [{
			"matchLabels": {
				"id": "app2"
			}
		}],
		"toPorts": [{
			"ports": [{
				"port": "80",
				"protocol": "TCP"
			}]
		}]
	}]
}]
```

命令会触发 CMD 得 import 方法最终会生成新的请求发送到 cilium_agent，调用链如下：
```bash
cmd.loadPolicy   # cilium/cmd/policy_import.go
client.PolicyPut 
    c.Policy.PutPolicy
        a.transport.Submit
```

经过上面的调用链就会将请求通过 unix 协议发送出去。

cilium-agent 接收到请求后，开始处理策略。之前大概浏览过 v0.10 的代码，重新生成了 BPF 程序。所有猜测了一下处理策略的过程应该和之前的一致：

- 解析策略内容
- 根据策略内容查询 docker 的IP
- 重新生成 BPF 程序
- 重新加载 BPF 程序
- 后面应该就是扫尾的动作，比如保存策略、保存 BPF 程序，添加新的监控

`但是总觉得不太合理。应该设计出 BPF 与 cilium 共享 map ，然后往 map 里面塞东西`



上面的流程只是猜测，具体的流量可能有很多处理，但是觉得大致思路是这样，所以按照这个思路去读代码。

cilium-agent 接收到添加策略请求后会调用如下路径方法：daemon/cmd/policy.go#policyAdd

看下具体代码：

```golang
// 注释：腾讯翻译->Policy Add将一段规则添加到守护进程拥有的策略存储库中。策略规则的最终更改将传播到所有本地管理的端点。
// 将规则添加到存储库后，返回存储库的策略修订号，如果无法导入更新的策略，则返回错误。
func (d *Daemon) policyAdd(sourceRules policyAPI.Rules, opts *policy.AddOptions, resChan chan interface{}) {
	

	

	allEndpoints := d.endpointManager.GetPolicyEndpoints()
	addedRules, newRev := d.policy.AddListLocked(sourceRules)

	addedRules.UpdateRulesEndpointsCaches(endpointsToBumpRevision, endpointsToRegen, &policySelectionWG)

	repr, err := monitorAPI.PolicyUpdateRepr(len(sourceRules), labels, newRev)
	if err == nil {
		d.SendNotification(monitorAPI.AgentNotifyPolicyUpdated, repr)
	}

	if option.Config.SelectiveRegeneration {
		r := &PolicyReactionEvent{
			wg:                &policySelectionWG,
			epsToBumpRevision: endpointsToBumpRevision,
			endpointsToRegen:  endpointsToRegen,
			newRev:            newRev,
			upsertIdentities:  newlyAllocatedIdentities,
		}
		_, err := d.policy.RuleReactionQueue.Enqueue(ev)	
	}else {
		d.TriggerPolicyUpdates(false, "policy rules added")
		ipcache.UpsertGeneratedIdentities(newlyAllocatedIdentities)
	}
}
```


步骤有点多，关键还有异步处理，切换上下文处理，但是不需要关注其返回结果倒是好点。先来点线索和基本条件。

`app1.ip :10.11.152.158`
`consul.ip:172.17.0.3`
`registry.ip:172.17.0.2`

看注释貌似没有支持 BPF 的部分，这个意思就是这边就负责解析保存到库里，然后下发到各个节点处理的意思。所以还要找到处理的地方。


首先看下 `GetPolicyEndpoints`  cilium 内置了一个 EndpointManager 管理器，用来管理每个 docker 实例，docker 的基本信息都有。这里将他们全部获取出来当然包括策略。这里获取出来的目的就是新旧策略对比进行切换。

AddListLocked 也有注释：AddListLocked在存储库已锁定的情况下将规则插入到策略存储库中，预计整个规则列表已被清理。
看了下代码应该就是将规则全部保存到一个地方。至于什么地方用可能会同步到 etcd 等第三方存储。顺便把 版本号生成了，也就是说与前端完成了交互。剩下的就进入自动化过程了。



UpdateRulesEndpointsCache更新r中指定规则是否选择EPS中的端点的每个规则中的缓存。
如果有任何规则与端点匹配，则将其添加到提供的IDSet，并从提供的EndpointSet中删除。当给定端点完成处理时，将向该端点发送所提供的WaitGroup信号。
大致的意思就是更新缓存中的规则，感觉也不是重点，主要作用就是将 规则与本地匹配进行一个不匹配清理的动作。什么情况下会清理：1. 当前值为空 2. 当前值指向的 docker 已经不在 alive 的状态。


PolicyUpdateRepr 将策略简化成一个字符串的形式。然后通过 SendNotification 将其发送给 hubble 。监控用的好像也不是什么核心流程。

接下来分别触发的是 RuleReactionQueue 和 TriggerPolicyUpdates
RuleReactionQueue 是一个异步处理的过程，会有一个上下文切换。最终触发的是 reactToRuleUpdates

reactToRuleUpdates 的作用注释：
ReactToRuleUpdate执行以下操作：
*重新生成epsToRegen中的所有端点。
*将所有端点(不在epsToRegen中，但在所有Eps中)的策略修订提升为修订版本。
*等待重新生成完成*根据需要将CIDR标识更新或删除到ipcache。

也还是与 ipcache 有关系，但是  epsToRegen 是什么操作呢。
```golang 
func reactToRuleUpdates(epsToBumpRevision, epsToRegen *policy.EndpointSet, rev uint64, upsertIdentities map[string]*identity.Identity, releasePrefixes []*net.IPNet) {
	epsToBumpRevision.ForEachGo(&enqueueWaitGroup, func(epp policy.Endpoint) {
		if epp == nil {
			return
		}
		epp.PolicyRevisionBumpEvent(rev)
	})
	epsToRegen.ForEachGo(&enqueueWaitGroup, func(ep policy.Endpoint) {
		ep.RegenerateIfAlive(regenMetadata)
	}
	if upsertIdentities != nil {
		ipcache.UpsertGeneratedIdentities(upsertIdentities)
	}
```

epsToRegen 就是 Endpoint 的一个 Set 集合。RegenerateIfAlive 在导入上诉策略并没有触发，UpsertGeneratedIdentities 是用来更新 k8s 缓存，目前并没有用到，所以没有具体执行。

思考：如果这里只是把 Policy 保存到本地内存，那么执行策略从如何触发的？是共享 Map 了么？那么为什么没有看见直接操作 map，是不是异步触发了？

一直到执行创建一个实例命令执行，有了新的线索：
`docker run -d --name app1 --net cilium-net -l "id=app1" cilium/demo-httpd`

触发了 regenerateBPF 方法的执行，而上游也是触发了策略的更新，异步来完成 regenerateBPF 。

然后技术回到上面的方法：

```golang
func (e *Endpoint) RegenerateIfAlive(regenMetadata *regeneration.ExternalRegenerationMetadata) <-chan bool {
	return e.Regenerate(regenMetadata)
}
```




