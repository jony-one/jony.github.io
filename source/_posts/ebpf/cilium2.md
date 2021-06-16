---
title: Cilium 源码阅读：cilium-docker
date: 2021-06-14 19:44:19
categories: 
	- [eBPF]
tags:
  - ebpf
  - cilium
author: Jony
---



# Cilium 源码阅读：cilium-docker 工作流程



# cilium-docker


实现网络和IPAM API的Docker插件。该插件处理来自本地Docker运行时的请求，以提供容器的网络连接和IP地址管理的请求。连接到 "cilium "类型的Docker网络。

运行：

`docker network create --driver cilium --ipam-driver cilium cilium-net`

当请求代理创建网络时，远程进程将收到一个发送到URL `/NetworkDriver.CreateNetwork` 的POST表单


```bash
POST /NetworkDriver.CreateNetwork HTTP/1.1
Host: 
User-Agent: Go-http-client/1.1
Content-Length: 269
Accept: application/vnd.docker.plugins.v1.2+json

{
	"NetworkID": "501239d1d45a2eafb1874ad440e92397f6a154acea93af636aec4a57d9547b25", // NetworkID值由LibNetwork生成，代表一个唯一的网络。
	"Options": { // Options值是LibNetwork给代理的任意映射。
		"com.docker.network.enable_ipv6": false, 
		"com.docker.network.generic": {}
	},
	"IPv4Data": [{ // IPv4Data和IPv6Data是用户配置并由IPAM驱动管理的ip地址数据。网络驱动程序需要支持IPAM驱动程序提供的ip寻址数据。
		"AddressSpace": "CiliumLocal", // AddressSpace:一个唯一的字符串表示IP地址的隔离空间
		"Gateway": "10.11.72.123/32", // 可选，IPAM驱动程序可以为池所代表的子网提供CIDR格式的网关IP地址。网络驱动程序可以利用这些信息实现网络管道功能。
		"Pool": "0.0.0.0/0" // 地址池:以CIDR格式表示的地址/掩码。因为IPAM驱动程序负责分配容器ip地址，所以网络驱动程序可以利用这些信息实现网络管道功能。
	}],
	"IPv6Data": []
}
```

看到请求进来的时候已经分配好了 IP ，这个 IP 是从哪分配的呢？

libnetwork 创建网络工作流程：
- 先请求 IPAM drive 获取 ippool，cilium：
```json
{
	"PoolID":"CiliumPoolv4",
	"Pool":"0.0.0.0/0",
	"Data":[{"com.docker.network.gateway":"10.11.72.123/32"}]
}
```
- 返回后首先确定len(ipV4Data)不为0
- 调用config, err := parseNetworkOptions(id, option)确认配置不和当前的networks的配置矛盾
- 调用err = config.processIPAM(id, ipV4Data, ipV6Data)
- 调用err = d.createNetwork(config)进行具体的网络创建
- 最后调用return d.storeUpdate(config)

思考：如果都是用第三方驱动的话那么主要的逻辑应该是查看：网络的配置是否与当前具有的配置矛盾、保存或者更新配置等待下一次比较、分配IP用于校验。也就是使用了第三方插件也只剩下 if...else... 逻辑。在 cilium 里面 ipv4 可变的只有 `com.docker.network.gateway` 的内容可以通过配置改变，那么 IPv4 通信规则在 Cilium 已经没那么重要了。IPv4 可以大幅度定制地址空间、网关。所以 Cilium 主要面向的 IP 地址是 IPv6。

思考鉴于代码。直接看代码：
```golang
func Init(dc driverapi.DriverCallback, config map[string]interface{}) error {
	newPluginHandler := func(name string, client *plugins.Client) {
		// negotiate driver capability with client
		d := newDriver(name, client)
		c, err := d.(*driver).getCapabilities()
		if err = dc.RegisterDriver(name, d, *c); err != nil {
	}
    ...
	handleFunc(driverapi.NetworkPluginEndpointType, newPluginHandler)

	return nil
}
```

newDriver 是新建一个驱动类，getCapabilities 就是调用驱动的 getCapabilities 接口，对应接口应该是：`NetworkDriver.GetCapabilities` 。应该是在初始化的情况才被调用，之上面的命令并没触发。RegisterDriver：在发现网络驱动程序时注册它。看代码就是做一个驱动绑定，可能会使用多个 driver。
```golang
func (r *DrvRegistry) RegisterDriver(ntype string, driver driverapi.Driver, capability driverapi.Capability) error {
	dData := &driverData{driver, capability}
	r.drivers[ntype] = dData
}
```

调用命令会调用创建 CreateNetwork 接口，但是在调用之前会调用 IPAM 驱动程序接口，可以参考[libnetwork/controller.go](https://github.com/moby/libnetwork/blob/64b7a4574d1426139437d20e81c0b6d391130ec8/controller.go#L709) 整个接口逻辑点，整个流程还是比较清晰简单的。

大概逻辑：
docker 命令执行后：先注册 IPAM 驱动和 network 驱动，然后docker 调用，NewNetwork 接口创建网络，创建网络会优先加载 IPAM 驱动获取基本网络管理IPv4 和 IPv6 分别请求，Gateway 是必填项，如果在请求 IPAM 的 RequestPool 接口没有返回时，会再次请求 IPAM RequestAddress 接口，然后在调用创建  网络驱动 CreateNetwork 接口创建网络。



# 创建实例使用网络插件
接下来看看：创建实例时如果获取 IPAddress、Mac。

`docker run -d --name app1 --net cilium-net -l "id=app1" cilium/demo-httpd`

```bash
POST /IpamDriver.RequestAddress HTTP/1.1
Host: 
User-Agent: Go-http-client/1.1
Content-Length: 54
Accept: application/vnd.docker.plugins.v1.2+json

{"PoolID":"CiliumPoolv4","Address":"","Options":null}

```

请求 IPAM 分配一个 IPv4 地址，通过 [docker 源代码](https://github.com/moby/libnetwork/blob/64b7a4574d1426139437d20e81c0b6d391130ec8/endpoint.go#L1088) 可以看到创建 Endpoint 的整个过程。

IPAM 请求将转为：

cilium-docker 

```bash
curl -X POST --unix-socket /var/run/cilium/cilium.sock \
  'http:///var/run/cilium/cilium.sock/v1/ipam?family=ipv4&owner=docker-ipam' \
  -H 'Accept: application/json' \
  -H 'Content-Type: application/json' \
  -H 'Expiration: false' 
```


先请求 IP  地址，然后请求 创建 Endpoint 接口，请求参数如下

```bash
POST /NetworkDriver.CreateEndpoint HTTP/1.1
Host: 
User-Agent: Go-http-client/1.1
Content-Length: 348
Accept: application/vnd.docker.plugins.v1.2+json

{
	"NetworkID": "f1e88970d55e632a6f130bcacb4a9e328a3489d88c231b191fcff94e053d30ed",
	"EndpointID": "f33feeba0d0ba23ba6abb659f2306f6f393919279842cbf2ed0050641ded5664",
	"Interface": {
		"Address": "10.11.197.102/32",
		"AddressIPv6": "",
		"MacAddress": ""
	},
	"Options": {
		"com.docker.network.endpoint.exposedports": [{
			"Proto": 6,
			"Port": 80
		}],
		"com.docker.network.portmap": []
	}
}
```

如果远程driver已经提供了一个非空的Interface那么必须回复一个空的Interface值，因为 如果Libnetwork提供一个非空的值，接收了一个非空值将会被视为错误。所以创建  Endpoint IP 地址就是上面的这样，IP 已经由 IPAM 分配。


实践操作，这里 Endpoint 并没有 mac 地址，所以 mac 地址是在哪里分配的？
经过上面的调用最终请求还是进入了 cilium-agent 程序的 IPAM 模块，这里将 IP 具体分配出来。所以如果要使其拥有 Mac 地址只需要在 Cilium-agent IPAM 模块稍微修改即可。

这边不是很核心的重点就分析到这。

参考：
[https://segmentfault.com/a/1190000017000822](https://segmentfault.com/a/1190000017000822)
[DOCKER源码分析6 网络部分执行流分析](https://guanjunjian.github.io/2017/10/13/study-6-docker-6-libnetwork-excuting-flow/)
[Docker驱动规范](https://github.com/moby/libnetwork/blob/master/docs/remote.md)
[Docker驱动规范中文](http://ninjadq.com/2015/09/29/3rd-party-net-plugin-in-docker)
[Docker远程驱动接口](https://github.com/moby/libnetwork/blob/master/drivers/remote/driver.go)











