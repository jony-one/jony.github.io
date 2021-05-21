---
title: Cilium 源码阅读：Agent  启动过程
date: 2021-05-20 19:44:19
categories: 
	- [eBPF]
tags:
  - ebpf
  - cilium
author: Jony
---



# Cilium 源码阅读：Agent  启动过程

原文：[http://arthurchiao.art/blog/cilium-code-agent-start/#0-overview](http://arthurchiao.art/blog/cilium-code-agent-start/#0-overview)



```bash
runDaemon                                                                    //    daemon/cmd/daemon_main.go
  |-enableIPForwarding                                                       
  |-k8s.Init                                                                 // -> pkg/k8s/init.go
  |-NewDaemon                                                                // -> daemon/cmd/daemon.go
  |  |-d := Daemon{}
  |  |-d.initMaps                                                            //    daemon/cmd/datapath.go
  |  |-d.svc.RestoreServices                                                 // -> pkg/service/service.go
  |  |  |-restoreBackendsLocked
  |  |  |-restoreServicesLocked
  |  |-d.k8sWatcher.RunK8sServiceHandler                                     //    pkg/k8s/watchers/watcher.go
  |  |  |-k8sServiceHandler                                                  //    pkg/k8s/watchers/watcher.go
  |  |    |-eventHandler                                                     //    pkg/k8s/watchers/watcher.go
  |  |-k8s.RegisterCRDs
  |  |-d.bootstrapIPAM                                                       // -> daemon/cmd/ipam.go
  |  |-restoredEndpoints := d.restoreOldEndpoints                            // -> daemon/cmd/state.go
  |  |  |-ioutil.ReadDir                                                   
  |  |  |-endpoint.FilterEPDir // filter over endpoint directories
  |  |  |-for ep := range possibleEPs
  |  |      validateEndpoint(ep)
  |  |        |-allocateIPsLocked
  |  |-k8s.Client().AnnotateNode                                           
  |  |-d.bootstrapClusterMesh                                              
  |  |-d.init                                                                //    daemon/cmd/daemon.go
  |  |  |-os.MkdirAll(globalsDir)
  |  |  |-d.createNodeConfigHeaderfile
  |  |  |-d.Datapath().Loader().Reinitialize
  |  |-monitoragent.NewAgent
  |  |-d.syncEndpointsAndHostIPs                                             // -> daemon/cmd/datapath.go
  |  |  |-insert special identities to lxcmap, ipcache
  |  |-UpdateController("sync-endpoints-and-host-ips")
  |  |-loader.RestoreTemplates                                               // -> pkg/datapath/loader/cache.go
  |  |  |-os.RemoveAll()
  |  |-ipcache.InitIPIdentityWatcher                                         // -> pkg/ipcache/kvstore.go
  |     |-watcher = NewIPIdentityWatcher
  |     |-watcher.Watch
  |        |-IPIdentityCache.Upsert/Delete
  |-gc.Enable                                                                // -> pkg/maps/ctmap/gc/gc.go
  |   |-for { runGC() } // conntrack & nat gc
  |-initKVStore
  |  |-UpdateController("kvstore-locks-gc", RunLocksGC)
  |  |-kvstore.Setup
  |-initRestore(restoredEndpoints)
  |  |-regenerateRestoredEndpoints(restoredEndpoints)                        // daemon/cmd/state.go
  |  |-UpdateController("sync-lb-maps-with-k8s-services")
  |-initHealth
  |-startStatusCollector
  |  |-status.NewCollector(probes)                                           // pkg/status
  |-startAgentHealthHTTPService
  |-SendNotification
  |  |-monitorAgent.SendEvent(AgentNotifyStart)
  |-srv.Serve()  // start Cilium agent API server
  |-k8s.Client().MarkNodeReady()
  |-launchHubble()
```

