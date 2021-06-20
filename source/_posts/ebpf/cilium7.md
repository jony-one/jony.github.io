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



# Cilium 源码阅读： 功能解析

查看了一下 cilium-docker 的功能主要以 IPAM 为主
sudo nohup /home/vagrant/go/bin/dlv attach 651 --headless=true --listen=:9526  --api-version=2 --accept-multiclient --log &
sudo nohup /home/vagrant/go/bin/dlv attach 18376 --headless=true --listen=:9527  --api-version=2 --accept-multiclient --log &
sudo nohup /home/vagrant/go/bin/dlv attach 5049 --headless=true --listen=:9528  --api-version=2 --accept-multiclient --log &

docker network create --driver cilium --ipam-driver cilium cilium-net
docker run -d --name app1 --net cilium-net -l "id=app1" cilium/demo-httpd
docker run --rm -ti --net cilium-net -l "id=app2" cilium/demo-client curl -m 20 http://app1

export HTTP_PROXY=http://192.168.0.90:58591; export HTTPS_PROXY=http://192.168.0.90:58591; export ALL_PROXY=socks5://192.168.0.90:51837

http:///var/run/cilium/cilium.sock//v1/ipam?family=ipv4&owner=docker-ipam
Accept:application/json
Content-Type:application/json
Expiration:false


curl -X POST --unix-socket /var/run/cilium/cilium.sock \
  'http:///v1/ipam?family=ipv4&owner=docker-ipam' \
  -H 'Accept: application/json' \
  -H 'Content-Type: application/json' \
  -H 'Expiration: false' 



curl -XPOST --unix-socket /var/run/cilium/cilium.sock/  http://localhost/v1/ipam?family=ipv4&owner=docker-ipam

HTTP/1.1 201 Created
Content-Type: application/json
Date: Sun, 13 Jun 2021 08:41:33 GMT
Content-Length: 259

{"address":{"ipv4":"10.11.215.121"},"host-addressing":{"ipv4":{"alloc-range":"10.11.0.0/16","enabled":true,"ip":"10.11.168.111"},"ipv6":{"alloc-range":"f00d::a0f:0:0:0/96","enabled":true,"ip":"f00d::a0f:0:0:56a9"}},"ipv4":{"cidrs":null,"ip":"10.11.215.121"}}
el":"None","NAT46":"Disabled","PolicyAuditMode":"Disabled","PolicyVerdictNotification":"Enabled","TraceNotification":"Enabled"}},"status":{"controllers":[{"configuration":{"error-retry":true,"error-retry-base":"2s"},"name":"endpoint-922-regeneration-recovery","status":{"last-failure-timestamp":"0001-01-01T00:00:00.000Z","last-success-timestamp":"0001-01-01T00:00:00.000Z"},"uuid":"00b7c499-cc22-11eb-8a22-080027e4875d"},{"configuration":{"error-retry":true,"interval":"5m0s"},"name":"resolve-identity-922","status":{"last-failure-timestamp":"0001-01-01T00:00:00.000Z","last-success-timestamp":"2021-06-13T08:33:17.807Z","success-count":1},"uuid":"00b9ae9e-cc22-11eb-8a22-080027e4875d"},{"configuration":{"error-retry":true,"interval":"5m0s"},"name":"sync-IPv4-identity-mapping (922)","status":{"last-failure-timestamp":"0001-01-01T00:00:00.000Z","last-success-timestamp":"2021-06-13T08:33:17.806Z","success-count":1},"uuid":"00b939f9-cc22-11eb-8a22-080027e4875d"},{"configuration":{"error-retry":true,"interval":"1m0s"},"name":"sync-policymap-922","status":{"last-failure-timestamp":"0001-01-01T00:00:00.000Z","last-success-timestamp":"2021-06-13T08:33:18.387Z","success-count":1},"uuid":"011253cb-cc22-11eb-8a22-080027e4875d"}],"external-identifiers":{"docker-endpoint-id":"74515cf5064ba31007dabcf724e6fa3d2914241f01da8ba888227ab3a0283304","docker-network-id":"f1e88970d55e632a6f130bcacb4a9e328a3489d88c231b191fcff94e053d30ed","pod-name":"/"},"health":{"bpf":"OK","connected":true,"overallHealth":"OK","policy":"OK"},"identity":{"id":5,"labels":["reserved:init"],"labelsSHA256":"200a5c3596eeb6d318ecd6d810acfd1fd5408e498501fd8a7ed212d3adab62e3"},"labels":{"realized":{},"security-relevant":["reserved:init"]},"log":[{"code":"OK","message":"Successfully regenerated endpoint program (Reason: updated security labels)","state":"ready","timestamp":"2021-06-13T08:33:18Z"}],"networking":{"addressing":[{"ipv4":"10.11.125.251"}],"host-mac":"3a:e1:e1:cb:18:23","interface-index":19,"interface-name":"lxcc502f5f6cac7","mac":"92:59:a2:63:f0:9c"},"policy":{"proxy-statistics":[],"realized":{"allowed-egress-identities":[],"allowed-ingress-identities":[],"build":1,"cidr-policy":{"egress":[],"ingress":[]},"id":5,"l4":{"egress":[],"ingress":[]},"policy-enabled":"both","policy-revision":1},"spec":{"allowed-egress-identities":[],"allowed-ingress-identities":[],"build":1,"cidr-policy":{"egress":[],"ingress":[]},"id":5,"l4":{"egress":[],"ingress":[]},"policy-enabled":"both","policy-revision":1}},"realized":{"label-configuration":{},"options":{"Conntrack":"Enabled","ConntrackAccounting":"Enabled","ConntrackLocal":"Disabled","Debug":"Disabled","DebugLB":"Disabled","DebugPolicy":"Disabled","DropNotification":"Enabled","MonitorAggregationLevel":"None","NAT46":"Disabled","PolicyAuditMode":"Disabled","PolicyVerdictNotification":"Enabled","TraceNotification":"Enabled"}},"state":"ready"}}