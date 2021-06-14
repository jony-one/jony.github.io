---
title: Cilium 源码阅读：Agent 功能查看
date: 2021-05-21 19:44:19
categories: 
	- [eBPF]
tags:
  - ebpf
  - cilium
author: Jony
---



# Cilium 源码阅读： 功能解析


Cilium 是开源软件，用于透明的提供和保护使用 Kubernetes、Docker 等 Linux 容器管理平台部署的应用程序之间的网络和 API 连接。

Cilium 基于 BPF 内核技术，再 Linux 内部动态插入强大的**安全性**、**可见性**和**网络控制逻辑**。处理提供传统的玩了国际安全性之外，BPF 的灵活性还可以再 API 和进程级别上实现安全性，以保护哦让那个和或容器内的同步新，由于 BPF 再 Linux 内核中运行一次可以应用和更新 Cilium 安全策略，而无须对应用程序代码或者容器配置进行任何**更改**。


Cilium 组件：

- Cilium Agent (Daemon): 用户空间守护程序，通过插件与容器运行时和编排系统（如Kubernetes）交互，以便为在本地服务器上运行的容器设置网络和安全性。提供用于配置网络安全策略，提取网络可见性数据等的API。

- Cilium CLI Client: 用于与本地Cilium Agent通信的简单CLI客户端，例如，用于配置网络安全性或可见性策略。

- Linux Kernel BPF: 集成Linux内核的功能，用于接受内核中在各种钩子/跟踪点运行的已编译字节码。Cilium编译BPF程序，并让内核在网络堆栈的关键点运行它们，以便可以查看和控制进出所有容器中的所有网络流量。

- CNI 容器平台网络插件: 每个容器平台（例如，Docker，Kubernetes）都有自己的插件模型，用于外部网络平台集成。对于Docker，每个Linux节点都运行一个进程（cilium-docker）来处理每个Docker libnetwork调用，并将数据/请求传递给主要的Cilium Agent。

# 术语

- Labels： 标签是一种通用，灵活且高度可扩展的方式，用于处理大量资源，因为它们允许任意分组和创建集合。 每当需要描述，寻址或选择某些内容时，都会根据标签完成：
  - Endpoint 在被从容器运行时，编排系统，或者其他源派生时分配标签。
  - Network Policy 选择 Endpoint 对，这些端点对被容许基于标签相互通讯。策略本身也由标签标示。

- Endpoint： Cilium通过分配IP地址使应用程序容器在网络上可用。多个应用程序容器可以共享相同的IP地址; 这个模型的一个典型例子是Kubernetes Pod。 共享公共地址的所有应用程序容器在Cilium所指的端点中组合在一起。



# 问题

Cilium 提供了IPAM 的功能，是否保持一致性的问题
Cilium 提供了基于 eBPF 的离包伪装，这个怎么实现的。



从 Config 数据结构看支持哪些功能


```golang
type DaemonConfig struct {
  CreationTime        time.Time
  BpfDir              string     // BPF 模板文件目录
  LibDir              string     // Cilium 库文件目录
  RunDir              string     // Cilium 运行时目录
  NAT46Prefix         *net.IPNet // NAT46 IPv6 前缀
  Devices             []string   // bpf_host 设备
  DirectRoutingDevice string     // Direct routing device (used only by NodePort BPF)
  LBDevInheritIPAddr  string     // Device which IP addr used by bpf_host devices
  DevicePreFilter     string     // Prefilter device
  ModePreFilter       string     // Prefilter mode
  XDPDevice           string     // XDP device
  XDPMode             string     // XDP mode, values: { xdpdrv | xdpgeneric | none }
  HostV4Addr          net.IP     // Host v4 address of the snooping device
  HostV6Addr          net.IP     // Host v6 address of the snooping device
  EncryptInterface    []string   // Set of network facing interface to encrypt over
  EncryptNode         bool       // Set to true for encrypting node IP traffic

  Ipvlan IpvlanConfig // Ipvlan 相关配置

  DatapathMode string // Datapath 数据路径模式
  Tunnel       string // Tunnel 模式

  DryMode bool // Do not create BPF maps, devices, ..

  // RestoreStat 可以恢复以前运行得 daemons 进程的状态.
  RestoreState bool

  // EnableHostIPRestore 可以根据以前Cilium运行留下的状态恢复主机IP。
  EnableHostIPRestore bool

  KeepConfig bool // 启动时保持现有端点的配置。

  // AllowLocalhost 定义了什么时候允许本地堆栈到本地端点。
  // values: { auto | always | policy }
  AllowLocalhost string

  // StateDir 存储端点运行状态的目录。
  StateDir string

  // Options 可在运行时改变
  Opts *IntOptions

  // Mutex for serializing configuration updates to the daemon.
  ConfigPatchMutex lock.RWMutex

  // Monitor 包含节点监视器的配置。
  Monitor *models.MonitorStatus

  // AgentHealthPort 是代理健康状态API的TCP端口。.
  AgentHealthPort int

  // AgentLabels 包含额外的标签，以便在监控事件中识别这个代理。
  AgentLabels []string

  // IPv6ClusterAllocCIDR  如果不是由协调系统进行分配，用于分配IPv6节点CIDR的基本CIDR发射点发生
  IPv6ClusterAllocCIDR string

  // IPv6ClusterAllocCIDRBase is derived from IPv6ClusterAllocCIDR and
  // contains the CIDR without the mask, e.g. "fdfd::1/64" -> "fdfd::"
  // IPv6ClusterAllocCIDRBase衍生自IPv6ClusterAllocCIDR，包含不含掩码的CIDR。
  // This variable should never be written to, it is initialized via
  // DaemonConfig.Validate()
  IPv6ClusterAllocCIDRBase string

  // K8sRequireIPv4PodCIDR requires the k8s node resource to specify the
  // IPv4 PodCIDR. Cilium will block bootstrapping until the information
  // is available.
  // K8sRequireIPv4PodCIDR要求k8s节点资源指定IPv4 PodCIDR。Cilium将阻止启动，直到信息可用。
  K8sRequireIPv4PodCIDR bool

  // K8sRequireIPv6PodCIDR requires the k8s node resource to specify the
  // IPv6 PodCIDR. Cilium will block bootstrapping until the information
  // is available.
  // K8sRequireIPv6PodCIDR要求k8s节点资源指定IPv6 PodCIDR。Cilium将阻止启动，直到信息可用。
  K8sRequireIPv6PodCIDR bool

  // K8sServiceCacheSize is the service cache size for cilium k8s package.
  // K8sServiceCacheSize是cilium k8s软件包的服务缓存大小。
  K8sServiceCacheSize uint

  // K8sForceJSONPatch when set, uses JSON Patch to update CNP and CEP
  // status in kube-apiserver.
  // K8sForceJSONPatch被设置后，使用JSON补丁来更新kube-apiserver中的CNP和CEP状态。
  K8sForceJSONPatch bool

  // MTU is the maximum transmission unit of the underlying network
  //  MTU是基础网络的最大传输单位
  MTU int

  // ClusterName is the name of the cluster
  // ClusterName是集群的名称
  ClusterName string

  // ClusterID is the unique identifier of the cluster
  // ClusterID是集群的唯一标识符。
  ClusterID int

  // ClusterMeshConfig is the path to the clustermesh configuration directory
  // ClusterMeshConfig是集群网格配置目录的路径。
  ClusterMeshConfig string

  // CTMapEntriesGlobalTCP is the maximum number of conntrack entries
  // allowed in each TCP CT table for IPv4/IPv6.
  // CTMapEntriesGlobalTCP是指IPv4/IPv6的每个TCP CT表中允许的最大串联条目数量。
  CTMapEntriesGlobalTCP int

  // CTMapEntriesGlobalAny is the maximum number of conntrack entries
  // allowed in each non-TCP CT table for IPv4/IPv6.
  // CTMapEntriesGlobalAny是IPv4/IPv6的每个非TCP CT表中允许的最大的conntrack条目数量。
  CTMapEntriesGlobalAny int

  // CTMapEntriesTimeout* values configured by the user.
  // 用户配置的CTMapEntriesTimeout*值。
  CTMapEntriesTimeoutTCP    time.Duration
  CTMapEntriesTimeoutAny    time.Duration
  CTMapEntriesTimeoutSVCTCP time.Duration
  CTMapEntriesTimeoutSVCAny time.Duration
  CTMapEntriesTimeoutSYN    time.Duration
  CTMapEntriesTimeoutFIN    time.Duration

  // EnableMonitor enables the monitor unix domain socket server
  // EnableMonitor 启用监控unix域套接字服务器。
  EnableMonitor bool

  // MonitorAggregationInterval configures the interval between monitor
  // messages when monitor aggregation is enabled.
  // MonitorAggregationInterval配置了在启用监控聚合时监控信息的间隔。
  MonitorAggregationInterval time.Duration

  // MonitorAggregationFlags determines which TCP flags that the monitor
  // aggregation ensures reports are generated for when monitor-aggragation
  // is enabled. Network byte-order.
  // MonitorAggregationFlags决定了在启用监控聚合时，监控聚合确保产生报告的TCP标志。网络的字节顺序。
  MonitorAggregationFlags uint16

  // BPFMapsDynamicSizeRatio is ratio of total system memory to use for
  // dynamic sizing of the CT, NAT, Neighbor and SockRevNAT BPF maps.
  // BPFMapsDynamicSizeRatio是用于CT、NAT、Neighbor和SockRevNAT BPF Map 动态大小的总系统内存的比率。
  BPFMapsDynamicSizeRatio float64

  // NATMapEntriesGlobal is the maximum number of NAT mappings allowed
  // in the BPF NAT table
  // NATMapEntriesGlobal是BPF NAT表中允许的NAT映射的最大数量
  NATMapEntriesGlobal int

  // NeighMapEntriesGlobal is the maximum number of neighbor mappings
  // allowed in the BPF neigh table
  // NeighMapEntriesGlobal 是BPF NAT表中允许的NAT映射的最大数量
  NeighMapEntriesGlobal int

  // PolicyMapEntries is the maximum number of peer identities that an
  // endpoint may allow traffic to exchange traffic with.
  // PolicyMapEntries是一个端点可能允许流量交换的对等身份的最大数量。
  PolicyMapEntries int

  // SockRevNatEntries is the maximum number of sock rev nat mappings
  // allowed in the BPF rev nat table
  // SockRevNatEntries是BPF rev nat表中允许的sock rev nat映射的最大数量。
  SockRevNatEntries int

  // DisableCiliumEndpointCRD disables the use of CiliumEndpoint CRD
  // DisableCiliumEndpointCRD禁止使用CiliumEndpoint CRD。
  DisableCiliumEndpointCRD bool

  // MaxControllerInterval is the maximum value for a controller's
  // RunInterval. Zero means unlimited.
  // MaxControllerInterval是一个控制器的RunInterval的最大值。零意味着无限制。
  MaxControllerInterval int

  // UseSingleClusterRoute specifies whether to use a single cluster route
  // instead of per-node routes.
  // UseSingleClusterRoute指定是否使用单个集群路由而不是每个节点路由。
  UseSingleClusterRoute bool

  // HTTPNormalizePath switches on Envoy HTTP path normalization options, which currently
  // includes RFC 3986 path normalization, Envoy merge slashes option, and unescaping and
  // redirecting for paths that contain escaped slashes. These are necessary to keep path based
  // access control functional, and should not interfere with normal operation. Set this to
  // false only with caution.
  // HTTPNormalizePath开启了Envoy HTTP路径规范化选项，目前包括RFC 3986路径规范化、Envoy合并斜线选项，
  // 以及对含有转义斜线的路径进行取消转义和重定向。这些是保持基于路径的访问控制功能所必需的，
  // 不应该影响正常的操作。只有在谨慎的情况下才将其设置为false。
  HTTPNormalizePath bool

  // HTTP403Message is the error message to return when a HTTP 403 is returned
  // by the proxy, if L7 policy is configured.
  // HTTP403Message是当代理返回HTTP403时，如果配置了L7策略，则返回错误信息。
  HTTP403Message string

  // HTTPRequestTimeout is the time in seconds after which Envoy responds with an
  // error code on a request that has not yet completed. This needs to be longer
  // than the HTTPIdleTimeout
  // HTTPRequestTimeout是指在一个尚未完成的请求中，
  // Envoy响应错误代码的时间，以秒计。这需要比HTTPIdleTimeout长。
  HTTPRequestTimeout int

  // HTTPIdleTimeout is the time in seconds of a HTTP stream having no traffic after
  // which Envoy responds with an error code. This needs to be shorter than the
  // HTTPRequestTimeout
  // HTTPIdleTimeout是指在HTTP流没有流量的情况下，Envoy回应错误代码的时间（秒）。这需要比HTTPRequestTimeout短。
  HTTPIdleTimeout int

  // HTTPMaxGRPCTimeout is the upper limit to which "grpc-timeout" headers in GRPC
  // requests are honored by Envoy. If 0 there is no limit. GRPC requests are not
  // bound by the HTTPRequestTimeout, but ARE affected by the idle timeout!
  // HTTPMaxGRPCTimeout是GRPC请求中 "grpc-timeout "头信息被Envoy认可的上限。如果是0，
  // 则没有限制。GRPC请求不受HTTPRequestTimeout的约束，但会受到空闲超时的影响。
  HTTPMaxGRPCTimeout int

  // HTTPRetryCount is the upper limit on how many times Envoy retries failed requests.
  // HTTPRetryCount是Envoy重试失败请求次数的上限。
  HTTPRetryCount int

  // HTTPRetryTimeout is the time in seconds before an uncompleted request is retried.
  // HTTPRetryTimeout是指在未完成的请求被重试之前的时间（秒）。
  HTTPRetryTimeout int

  // ProxyConnectTimeout is the time in seconds after which Envoy considers a TCP
  // connection attempt to have timed out.
  // ProxyConnectTimeout是Envoy认为TCP连接尝试超时的时间，以秒为单位。
  ProxyConnectTimeout int

  // ProxyPrometheusPort specifies the port to serve Envoy metrics on.
  // ProxyPrometheusPort指定了为Envoy度量服务的端口。
  ProxyPrometheusPort int

  // EnvoyLogPath specifies where to store the Envoy proxy logs when Envoy
  // runs in the same container as Cilium.
  // EnvoyLogPath指定了当Envoy与Cilium在同一容器中运行时，Envoy代理日志的存储位置。
  EnvoyLogPath string

  // EnableSockOps specifies whether to enable sockops (socket lookup).
  // EnableSockOps指定是否启用sockops（socket lookup）。
  SockopsEnable bool

  // PrependIptablesChains is the name of the option to enable prepending
  // iptables chains instead of appending
  // PrependIptablesChains是选项的名称，用于启用预置iptables链，而不是附加的链。
  PrependIptablesChains bool

  // IPTablesLockTimeout defines the "-w" iptables option when the
  // iptables CLI is directly invoked from the Cilium agent.
  // IPTablesLockTimeout定义了从Cilium代理直接调用iptables CLI时的"-w "iptables选项。
  IPTablesLockTimeout time.Duration

  // IPTablesRandomFully defines the "--random-fully" iptables option when the
  // iptables CLI is directly invoked from the Cilium agent.
  // IPTablesRandomFully定义了当从Cilium代理直接调用iptables CLI时的"--random-fully" iptables选项。
  IPTablesRandomFully bool

  // K8sNamespace is the name of the namespace in which Cilium is
  // deployed in when running in Kubernetes mode
  // K8sNamespace是指在Kubernetes模式下运行时，Cilium被部署在其中的名称空间。
  K8sNamespace string

  // JoinCluster is 'true' if the agent should join a Cilium cluster via kvstore
  // registration
  // 如果代理应该通过kvstore注册加入Cilium集群，JoinCluster为 "true"。
  JoinCluster bool

  // EnableIPv4 is true when IPv4 is enabled
  // 当EnableIPv4为真，IPv4被启用时。
  EnableIPv4 bool

  // EnableIPv6 is true when IPv6 is enabled
  // EnableIPv6为真 启用了IPv6
  EnableIPv6 bool

  // EnableIPv6NDP is true when NDP is enabled for IPv6
  // 为IPv6启用NDP时，EnableIPv6NDP为True
  EnableIPv6NDP bool

  // IPv6MCastDevice is the name of device that joins IPv6's solicitation multicast group
  // IPv6MCastDevice是加入IPv6的请求多播组的设备的名称
  IPv6MCastDevice string

  // EnableL7Proxy is the option to enable L7 proxy
  // EnableL7Proxy是启用L7代理的选项。
  EnableL7Proxy bool

  // EnableIPSec is true when IPSec is enabled
  // 启用IPSec时，EnableIPSec为true
  EnableIPSec bool

  // IPSec key file for stored keys
  // 存储密钥的IPSec密钥文件
  IPSecKeyFile string

  // EnableWireguard enables Wireguard encryption
  // EnableWireguard启用Wireguard加密
  EnableWireguard bool

  // MonitorQueueSize is the size of the monitor event queue
  // Monitor QueueSize是监视器事件队列的大小
  MonitorQueueSize int

  // CLI options
  // Path to BPF filesystem
  BPFRoot                       string
  //Path to Cgroup2 filesystem
  CGroupRoot                    string
  //Enable debugging of the BPF compilation process. 
  BPFCompileDebug               string
  // Extra CFLAGS for BPF compilation
  CompilerFlags                 []string
  // Configuration file (default "$HOME/ciliumd.yaml")
  ConfigFile                    string
  // Configuration directory that contains a file for each option
  ConfigDir                     string
  // Enable debugging mode
  Debug                         bool
  // List of enabled verbose debug groups
  DebugVerbose                  []string
  // Disable connection tracking
  DisableConntrack              bool
  // Use per endpoint routes instead of routing via cilium_host
  EnableHostReachableServices   bool
  // 
  EnableHostServicesTCP         bool
  EnableHostServicesUDP         bool
  EnableHostServicesPeer        bool
  // Enable policy enforcement
  EnablePolicy                  string
  // Enable tracing while determining policy (debugging)
  EnableTracing                 bool
  // Path to a separate Envoy log file, if any
  EnvoyLog                      string
  // Do not perform Envoy binary version check on startup
  DisableEnvoyVersionCheck      bool
  // Key-value for the fixed identity mapping which allows to use reserved label for fixed identities
  FixedIdentityMapping          map[string]string
  FixedIdentityMappingValidator func(val string) (string, error) `json:"-"`
  // Per-node IPv4 endpoint prefix, e.g. 10.16.0.0/16
  IPv4Range                     string
  // Per-node IPv6 endpoint prefix, e.g. fd02:1:1::/96
  IPv6Range                     string
  // Kubernetes IPv4 services CIDR if not inside cluster prefix
  IPv4ServiceRange              string
  // Kubernetes IPv6 services CIDR if not inside cluster prefix
  IPv6ServiceRange              string
  // "Per-node IPv4 endpoint prefix, e.g. 10.16.0.0/16
  K8sAPIServer                  string
  // Absolute path of the kubernetes kubeconfig file
  K8sKubeConfigPath             string
  K8sClientBurst                int
  K8sClientQPSLimit             float64
  // Timeout for synchronizing k8s resources before exiting
  K8sSyncTimeout                time.Duration
  // Timeout for listing allocator state before exiting
  AllocatorListTimeout          time.Duration
  // K8s endpoint watcher will watch for these k8s endpoints
  K8sWatcherEndpointSelector    string
  // Key-value store type
  KVStore                       string
  // Key-value store options
  KVStoreOpt                    map[string]string
  // Valid label prefixes file path
  LabelPrefixFile               string
  // List of label prefixes used to determine identity of an endpoint
  Labels                        []string
  // Logging endpoints to use for example syslog
  LogDriver                     []string
  // Log driver options for cilium-agent,configmap example for syslog driver: {"syslog.level":"info","syslog.facility":"local5","syslog.tag":"cilium-agent"}
  LogOpt                        map[string]string
  // Enable periodic logging of system load
  Logstash                      bool
  // Enable periodic logging of system load
  LogSystemLoadConfig           bool
  // IPv6 prefix to map IPv4 addresses to
  NAT46Range                    string

  // Masquerade specifies whether or not to masquerade packets from endpoints
  // leaving the host.
  // Masquerade 指定是否伪装来自离开主机的端点的数据包。
  EnableIPv4Masquerade   bool
  // Masquerade IPv6 traffic from endpoints leaving the host
  EnableIPv6Masquerade   bool
  // Masquerade packets from endpoints leaving the host with BPF instead of iptables
  EnableBPFMasquerade    bool
  // Enable BPF clock source probing for more efficient tick retrieval
  EnableBPFClockProbe    bool
  // Enable BPF ip-masq-agent
  EnableIPMasqAgent      bool
  // Enable egress gateway
  EnableEgressGateway    bool
  // ip-masq-agent configuration file path
  IPMasqAgentConfigPath  string
  // Install base iptables rules for cilium to mainly interact with kube-proxy (and masquerading)
  InstallIptRules        bool
  // Level of monitor aggregation for traces from the datapath
  MonitorAggregation     string
  // Enable BPF map pre-allocation
  PreAllocateMaps        bool
  // Invalid IPv6 node address
  IPv6NodeAddr           string
  // Invalid IPv4 node address
  IPv4NodeAddr           string
  // Regular expression matching compatible Istio sidecar istio-proxy container image names
  SidecarIstioProxyImage string
  // Sets daemon's socket path to listen for connections
  SocketPath             string
  // Length of payload to capture when tracing
  TracePayloadlen        int
  // Print version information
  Version                string
  // Enable serving the pprof debugging API
  PProf                  bool
  // Port that the pprof listens on
  PProfPort              int
  // IP:Port on which to serve prometheus metrics (pass ":Port" to bind on all interfaces, "" is off)
  PrometheusServeAddr    string
  // The minimum time, in seconds, to use DNS data for toFQDNs policies. (default %d )
  ToFQDNsMinTTL          int

  // DNSMaxIPsPerRestoredRule defines the maximum number of IPs to maintain
  // for each FQDN selector in endpoint's restored DNS rules
  // DNSMaxIPsPerRestoredRule定义了端点恢复的DNS规则中每个FQDN选择器要保持的最大IP数
  DNSMaxIPsPerRestoredRule int

  // ToFQDNsProxyPort is the user-configured global, shared, DNS listen port used
  // by the DNS Proxy. Both UDP and TCP are handled on the same port. When it
  // is 0 a random port will be assigned, and can be obtained from
  // DefaultDNSProxy below.
  // ToFQDNsProxyPort是用户配置的全局、共享的DNS监听端口，由DNS代理使用。
  // UDP和TCP都在同一个端口上处理。当它为0时，将分配一个随机端口，可以从下面的DefaultDNSProxy获得。
  ToFQDNsProxyPort int

  // ToFQDNsMaxIPsPerHost defines the maximum number of IPs to maintain
  // for each FQDN name in an endpoint's FQDN cache
  // ToFQDNsMaxIPsPerHost定义了在端点的FQDN缓存中为每个FQDN名称维护的最大IP数量。
  ToFQDNsMaxIPsPerHost int

  // ToFQDNsMaxIPsPerHost defines the maximum number of IPs to retain for
  // expired DNS lookups with still-active connections
  // ToFQDNsMaxIPsPerHost定义了为过期的DNS查询保留的最大IP数量，并有仍然有效的连接。
  ToFQDNsMaxDeferredConnectionDeletes int

  // ToFQDNsIdleConnectionGracePeriod Time during which idle but
  // previously active connections with expired DNS lookups are
  // still considered alive
  // ToFQDNsIdleConnectionGracePeriod 在这段时间内，空闲但先前活跃的连接与过期的DNS查询仍被认为是活的。
  ToFQDNsIdleConnectionGracePeriod time.Duration

  // FQDNRejectResponse is the dns-proxy response for invalid dns-proxy request
  // FQDNRejectResponse是对无效dns-proxy请求的dns-proxy响应。
  FQDNRejectResponse string

  // FQDNProxyResponseMaxDelay The maximum time the DNS proxy holds an allowed
  // DNS response before sending it along. Responses are sent as soon as the
  // datapath is updated with the new IP information.
  // FQDNProxyResponseMaxDelay DNS代理在发送允许的DNS响应之前保持的最大时间。一旦数据通路更新了新的IP信息，就立即发送响应。
  FQDNProxyResponseMaxDelay time.Duration

  // Path to a file with DNS cache data to preload on startup
  // 在启动时预加载DNS缓存数据的文件的路径
  ToFQDNsPreCache string

  // ToFQDNsEnableDNSCompression allows the DNS proxy to compress responses to
  // endpoints that are larger than 512 Bytes or the EDNS0 option, if present.
  // ToFQDNsEnableDNSCompression允许DNS代理压缩对大于512字节或EDNS0选项（如果存在）的端点的响应。
  ToFQDNsEnableDNSCompression bool

  // HostDevice will be device used by Cilium to connect to the outside world.
  // HostDevice将是Cilium用来与外部世界连接的设备。
  HostDevice string

  // EnableXTSocketFallback allows disabling of kernel's ip_early_demux
  // sysctl option if `xt_socket` kernel module is not available.
  // EnableXTSocketFallback允许在`xt_socket`内核模块不可用的情况下禁用内核的ip_early_demux sysctl选项。
  EnableXTSocketFallback bool

  // EnableBPFTProxy enables implementing proxy redirection via BPF
  // mechanisms rather than iptables rules.
  // EnableBPFTProxy可以通过BPF机制而不是iptables规则实现代理重定向。
  EnableBPFTProxy bool

  // EnableAutoDirectRouting enables installation of direct routes to
  // other nodes when available
  // EnableAutoDirectRouting可以在可用时安装到其他节点的直接路由。
  EnableAutoDirectRouting bool

  // EnableLocalNodeRoute controls installation of the route which points
  // the allocation prefix of the local node.
  // EnableLocalNodeRoute控制路由的安装，它指向本地节点的分配前缀。
  EnableLocalNodeRoute bool

  // EnableHealthChecking enables health checking between nodes and
  // health endpoints
  // EnableHealthChecking可以在节点和健康端点之间进行健康检查。
  EnableHealthChecking bool

  // EnableEndpointHealthChecking enables health checking between virtual
  // health endpoints
  // EnableEndpointHealthChecking启用虚拟健康端点之间的健康检查
  EnableEndpointHealthChecking bool

  // EnableHealthCheckNodePort enables health checking of NodePort by
  // cilium
  // EnableHealthCheckNodePort可以通过cilium对NodePort进行健康检查。
  EnableHealthCheckNodePort bool

  // KVstoreKeepAliveInterval is the interval in which the lease is being
  // renewed. This must be set to a value lesser than the LeaseTTL ideally
  // by a factor of 3.
  // KVstoreKeepAliveInterval是续租的时间间隔。这必须被设置为一个小于LeaseTTL的值，最好是3的系数。
  KVstoreKeepAliveInterval time.Duration

  // KVstoreLeaseTTL is the time-to-live for kvstore lease.
  // KVstoreLeaseTTL是kvstore租赁的生存时间。
  KVstoreLeaseTTL time.Duration

  // KVstorePeriodicSync is the time interval in which periodic
  // synchronization with the kvstore occurs
  // KVstorePeriodicSync是与kvstore进行定期同步的时间间隔。
  KVstorePeriodicSync time.Duration

  // KVstoreConnectivityTimeout is the timeout when performing kvstore operations
  // KVstoreConnectivityTimeout是执行kvstore操作时的超时。
  KVstoreConnectivityTimeout time.Duration

  // IPAllocationTimeout is the timeout when allocating CIDRs
  // IPAllocationTimeout是分配CIDR时的超时。
  IPAllocationTimeout time.Duration

  // IdentityChangeGracePeriod is the grace period that needs to pass
  // before an endpoint that has changed its identity will start using
  // that new identity. During the grace period, the new identity has
  // already been allocated and other nodes in the cluster have a chance
  // to whitelist the new upcoming identity of the endpoint.
  // 身份变更宽限期（IdentityChangeGracePeriod）是指在一个改变了身份的端点开始使用
  // 新身份之前需要经过的宽限期。在宽限期内，新的身份已经被分配，集群中的
  // 其他节点有机会将该端点即将出现的新身份列入白名单。
  IdentityChangeGracePeriod time.Duration

  // PolicyQueueSize is the size of the queues for the policy repository.
  // A larger queue means that more events related to policy can be buffered.
  // PolicyQueueSize是策略库的队列大小。较大的队列意味着可以缓冲更多与政策有关的事件。
  PolicyQueueSize int

  // EndpointQueueSize is the size of the EventQueue per-endpoint. A larger
  // queue means that more events can be buffered per-endpoint. This is useful
  // in the case where a cluster might be under high load for endpoint-related
  // events, specifically those which cause many regenerations.
  // EndpointQueueSize是每个端点的EventQueue的大小。一个更大的队列意味
  // 着每个端点可以缓冲更多的事件。这在集群可能处于端点相关事件的高负荷情况下很有用，特别是那些导致许多再生的事件。
  EndpointQueueSize int

  // EndpointGCInterval is interval to attempt garbage collection of
  // endpoints that are no longer alive and healthy.
  // EndpointGCInterval是间隔时间，用于尝试对不再存活和健康的端点进行垃圾收集。
  EndpointGCInterval time.Duration

  // SelectiveRegeneration, when true, enables the functionality to only
  // regenerate endpoints which are selected by the policy rules that have
  // been changed (added, deleted, or updated). If false, then all endpoints
  // are regenerated upon every policy change regardless of the scope of the
  // policy change.
  // 选择性再生（SelectiveRegeneration），当为真时，使该
  // 功能只再生被策略规则选中的、已被改变（添加、删除或更新）的端点。如果是假的，
  // 那么所有的端点都会在每次策略改变时被重新生成，不管策略改变的范围如何。
  SelectiveRegeneration bool

  // ConntrackGCInterval is the connection tracking garbage collection
  // interval
  // ConntrackGCInterval是连接跟踪的垃圾收集时间间隔。
  ConntrackGCInterval time.Duration

  // K8sEventHandover enables use of the kvstore to optimize Kubernetes
  // event handling by listening for k8s events in the operator and
  // mirroring it into the kvstore for reduced overhead in large
  // clusters.
  // K8sEventHandover能够使用kvstore来优化Kubernetes的事件处理，
  // 通过监听运营商的k8s事件并将其镜像到kvstore中，以减少大型集群的开销。
  K8sEventHandover bool

  // MetricsConfig is the configuration set in metrics
  // MetricsConfig是在metrics中设置的配置
  MetricsConfig metrics.Configuration

  // LoopbackIPv4 is the address to use for service loopback SNAT
  // LoopbackIPv4是用于服务环回SNAT的地址
  LoopbackIPv4 string

  // LocalRouterIPv4 is the link-local IPv4 address used for Cilium's router device
  // LocalRouterIPv4是用于Cilium路由器设备的链接本地IPv4地址。
  LocalRouterIPv4 string

  // LocalRouterIPv6 is the link-local IPv6 address used for Cilium's router device
  // LocalRouterIPv6是用于Cilium的路由器设备的链接本地IPv6地址。
  LocalRouterIPv6 string

  // EndpointInterfaceNamePrefix is the prefix name of the interface
  // names shared by all endpoints
  // EndpointInterfaceNamePrefix是所有端点共享的接口名称的前缀名称。
  EndpointInterfaceNamePrefix string

  // ForceLocalPolicyEvalAtSource forces a policy decision at the source
  // endpoint for all local communication
  // ForceLocalPolicyEvalAtSource强制所有本地通信在源端点进行策略决定。
  ForceLocalPolicyEvalAtSource bool

  // SkipCRDCreation disables creation of the CustomResourceDefinition
  // on daemon startup
  // 跳过CRDCreation，禁止在守护程序启动时创建CustomResourceDefinition。
  SkipCRDCreation bool

  // EnableEndpointRoutes enables use of per endpoint routes
  // enableendpointrroutes允许使用每个端点路由
  EnableEndpointRoutes bool

  // Specifies wheather to annotate the kubernetes nodes or not
  // 指定是否注解kubernetes节点
  AnnotateK8sNode bool

  // RunMonitorAgent indicates whether to run the monitor agent
  // “RunMonitorAgent”表示是否运行monitor代理
  RunMonitorAgent bool

  // ReadCNIConfiguration reads the CNI configuration file and extracts
  // Cilium relevant information. This can be used to pass per node
  // configuration to Cilium.
  // ReadCNIConfiguration读取CNI配置文件并提取纤毛相关信息。这可用于将每个节点的配置传递给cilium。
  ReadCNIConfiguration string

  // WriteCNIConfigurationWhenReady writes the CNI configuration to the
  // specified location once the agent is ready to serve requests. This
  // allows to keep a Kubernetes node NotReady until Cilium is up and
  // running and able to schedule endpoints.
  // 代理准备好处理请求后，WriteCNIConfigurationWhenReady将CNI配置写入指定位置。
  // 这允许在cilium启动并运行并能够调度端点之前保持Kubernetes节点NotReady。
  WriteCNIConfigurationWhenReady string

  // EnableNodePort enables k8s NodePort service implementation in BPF
  // EnableNodePort支持在BPF中实现K8S NodePort服务
  EnableNodePort bool

  // EnableSVCSourceRangeCheck enables check of loadBalancerSourceRanges
  // EnableSVCSourceRangeCheck启用loadBalancerSourceRanges检查
  EnableSVCSourceRangeCheck bool

  // EnableHealthDatapath enables IPIP health probes data path
  // EnableHealthDatapath启用IPIP运行状况探测器数据路径
  EnableHealthDatapath bool

  // EnableHostPort enables k8s Pod's hostPort mapping through BPF
  // EnableHostPort通过BPF启用K8S Pod的主机端口映射
  EnableHostPort bool

  // EnableHostLegacyRouting enables the old routing path via stack.
  // EnableHostLegacyRouting通过堆栈启用旧路由路径。
  EnableHostLegacyRouting bool

  // NodePortMode indicates in which mode NodePort implementation should run
  // ("snat", "dsr" or "hybrid")
  // NodePortMode指示NodePort实现应该在哪种模式下运行(“snat”、“dsr”或“Mixed”)
  NodePortMode string

  // NodePortAlg indicates which backend selection algorithm is used
  // ("random" or "maglev")
  // NodePortAlg表示使用哪种后端选择算法(“随机”或“maglev”)
  NodePortAlg string

  // LoadBalancerDSRDispatch indicates the method for pushing packets to
  // backends under DSR ("opt" or "ipip")
  // LoadBalancerDSRDispatch表示DSR下推包到后台的方式(opt或ipip)
  LoadBalancerDSRDispatch string

  // LoadBalancerDSRL4Xlate indicates the method for L4 DNAT translation
  // under IPIP dispatch, that is, whether the inner packet will be
  // translated to the frontend or backend port.
  // LoadBalancerDSRL4Xlate表示IPIP调度下的L4 DNAT转换方式，即内部报文是转换到前端端口还是后端端口。
  LoadBalancerDSRL4Xlate string

  // LoadBalancerRSSv4CIDR defines the outer source IPv4 prefix for DSR/IPIP
  // LoadBalancerRSSv4CIDR定义了DSR/IPIP的外源IPv4前缀
  LoadBalancerRSSv4CIDR string
  LoadBalancerRSSv4     net.IPNet

  // LoadBalancerRSSv4CIDR defines the outer source IPv6 prefix for DSR/IPIP
  // LoadBalancerRSSv4CIDR定义了DSR/IPIP的外部源IPv6前缀
  LoadBalancerRSSv6CIDR string
  LoadBalancerRSSv6     net.IPNet

  // LoadBalancerPMTUDiscovery indicates whether LB should reply with ICMP
  // frag needed messages to client (when needed)
  // LoadBalancerPMTUDiscovery指示LB是否应该用ICMP回复
  LoadBalancerPMTUDiscovery bool

  // Maglev backend table size (M) per service. Must be prime number.
  // 每个服务的 maglev 后端表大小（M）。必须是质数。
  MaglevTableSize int

  // MaglevHashSeed contains the cluster-wide seed for the hash(es).
  // MaglevHashSeed包含 hash 的群集范围种子。
  MaglevHashSeed string

  // NodePortAcceleration indicates whether NodePort should be accelerated
  // via XDP ("none", "generic" or "native")
  // NodePortAcceleration表示NodePort是否应该通过XDP加速(“None”、“Generic”或“Native”)
  NodePortAcceleration string

  // NodePortHairpin indicates whether the setup is a one-legged LB
  // NodePortHairpin指示设置是否为单腿LB
  NodePortHairpin bool

  // NodePortBindProtection rejects bind requests to NodePort service ports
  // NodePortBindProction拒绝对NodePort服务端口的绑定请求
  NodePortBindProtection bool

  // EnableAutoProtectNodePortRange enables appending NodePort range to
  // net.ipv4.ip_local_reserved_ports if it overlaps with ephemeral port
  // range (net.ipv4.ip_local_port_range)
  // EnableAutoProtectNodePortRange 可以在节点端口范围与临时端口范围（net.ipv4.ip_local_reserved_ports）
  // 重叠时将其追加到 net.ipv4.ip_local_port_range。
  EnableAutoProtectNodePortRange bool

  // KubeProxyReplacement controls how to enable kube-proxy replacement
  // features in BPF datapath
  // KubeProxyReplacement控制如何在BPF数据路径中启用kube-proxy替换功能
  KubeProxyReplacement string

  // EnableBandwidthManager enables EDT-based pacing
  // EnableBandwidthManager 启用基于EDT的节奏。
  EnableBandwidthManager bool

  // EnableRecorder enables the datapath pcap recorder
  // EnableRecorder 启用数据路径 pcap 记录器
  EnableRecorder bool

  // KubeProxyReplacementHealthzBindAddr is the KubeProxyReplacement healthz server bind addr
  // KubeProxyReplacementHealthzBindAddr是KubeProxyReplacement healthz服务器的绑定地址。
  KubeProxyReplacementHealthzBindAddr string

  // EnableExternalIPs enables implementation of k8s services with externalIPs in datapath
  // EnableExternalIPs可以在数据通路中用外部IPs实现k8s服务。
  EnableExternalIPs bool

  // EnableHostFirewall enables network policies for the host
  // EnableHostFirewall 启用主机的网络策略。
  EnableHostFirewall bool

  // EnableLocalRedirectPolicy enables redirect policies to redirect traffic within nodes
  // EnableLocalRedirectPolicy使重定向策略能够在节点内重定向流量
  EnableLocalRedirectPolicy bool

  // K8sEnableEndpointSlice enables k8s endpoint slice feature that is used
  // in kubernetes.
  // K8sEnableEndpointSlice 启用kubernetes中使用的k8s端点分片功能。
  K8sEnableK8sEndpointSlice bool

  // NodePortMin is the minimum port address for the NodePort range
  // NodePortMin是NodePort范围的最小端口地址。
  NodePortMin int

  // NodePortMax is the maximum port address for the NodePort range
  // NodePortMax是NodePort范围的最大端口地址。
  NodePortMax int

  // EnableSessionAffinity enables a support for service sessionAffinity
  EnableSessionAffinity bool

  // Selection of BPF main clock source (ktime vs jiffies)
  // BPF主时钟源的选择（ktime与jiffies）。
  ClockSource BPFClockSource

  // EnableIdentityMark enables setting the mark field with the identity for
  // local traffic. This may be disabled if chaining modes and Cilium use
  // conflicting marks.
  // EnableIdentityMark可以为本地流量设置带有身份的标记字段。
  // 如果连锁模式和Cilium使用冲突的标记，这可能被禁用。
  EnableIdentityMark bool

  // KernelHz is the HZ rate the kernel is operating in
  // KernelHz是内核运行的HZ率。
  KernelHz int

  // excludeLocalAddresses excludes certain addresses to be recognized as
  // a local address
  // excludeLocalAddresses 排除某些地址被识别为本地地址。
  excludeLocalAddresses []*net.IPNet

  // IPv4PodSubnets available subnets to be assign IPv4 addresses to pods from
  // IPv4PodSubnets 可用的子网将被分配IPv4地址给 Pod。
  IPv4PodSubnets []*net.IPNet

  // IPv6PodSubnets available subnets to be assign IPv6 addresses to pods from
  // IPv6PodSubnet可用子网为Pod分配IPv6地址
  IPv6PodSubnets []*net.IPNet

  // IPAM is the IPAM method to use
  // IPAM是要使用的IPAM方法
  IPAM string

  // AutoCreateCiliumNodeResource enables automatic creation of a
  // CiliumNode resource for the local node
  AutoCreateCiliumNodeResource bool

  // ipv4NativeRoutingCIDR describes a CIDR in which pod IPs are routable
  // ipv4NativeRoutingCIDR描述实例IP可路由的CIDR
  ipv4NativeRoutingCIDR *cidr.CIDR

  // EgressMasqueradeInterfaces is the selector used to select interfaces
  // subject to egress masquerading
  // EgressMasalladeInterfaces是用于选择要进行出口伪装的接口的选择器
  EgressMasqueradeInterfaces string

  // PolicyTriggerInterval is the amount of time between when policy updates
  // are triggered.
  // PolicyTriggerInterval是触发策略更新之间的时间量。
  PolicyTriggerInterval time.Duration

  // IdentityAllocationMode specifies what mode to use for identity
  // allocation
  // IdentityAllocationMode指定用于标识分配的模式
  IdentityAllocationMode string

  // DisableCNPStatusUpdates disables updating of CNP NodeStatus in the CNP
  // CRD.
  // DisableCNPStatusUpdate禁用CNP CRD中的CNP NodeStatus更新。
  DisableCNPStatusUpdates bool

  // AllowICMPFragNeeded allows ICMP Fragmentation Needed type packets in
  // the network policy for cilium-agent.
  // AllowICMPFragNeeded允许在cilium-agent的网络策略中对所需类型的数据包进行ICMP分段。
  AllowICMPFragNeeded bool

  // EnableWellKnownIdentities enables the use of well-known identities.
  // This is requires if identiy resolution is required to bring up the
  // control plane, e.g. when using the managed etcd feature
  // EnableWellKnownIdentity允许使用众所周知的身份。如果需要身份分辨率才能启动控制平面，例如在使用托管etcd功能时，则需要执行此操作
  EnableWellKnownIdentities bool

  // CertsDirectory is the root directory to be used by cilium to find
  // certificates locally.
  // CertsDirectory是cilium用来在本地查找证书的根目录。
  CertDirectory string

  // EnableRemoteNodeIdentity enables use of the remote-node identity
  // EnableRemoteNodeIdentity启用远程节点标识
  EnableRemoteNodeIdentity bool

  // Azure options

  // PolicyAuditMode enables non-drop mode for installed policies. In
  // audit mode packets affected by policies will not be dropped.
  // Policy related decisions can be checked via the poicy verdict messages.
  // PolicyAuditMode为已安装的策略启用非丢弃模式。在审核模式下，不会丢弃受策略影响的数据包。
  // 与政策相关的决定可以通过政策裁决消息进行检查。
  PolicyAuditMode bool

  // EnableHubble specifies whether to enable the hubble server.
  // EnableHubble指定是否启用哈勃服务器。
  EnableHubble bool

  // HubbleSocketPath specifies the UNIX domain socket for Hubble server to listen to.
  // HubbleSocketPath指定哈勃服务器要侦听的UNIX域套接字。
  HubbleSocketPath string

  // HubbleListenAddress specifies address for Hubble to listen to.
  // HubbleListenAddress指定哈勃要监听的地址。
  HubbleListenAddress string

  // HubbleTLSDisabled allows the Hubble server to run on the given listen
  // address without TLS.
  // HubbleTLSDisabled允许哈勃服务器在没有TLS的情况下在给定的侦听地址上运行。
  HubbleTLSDisabled bool

  // HubbleTLSCertFile specifies the path to the public key file for the
  // Hubble server. The file must contain PEM encoded data.
  // HubbleTLSCertFile指定哈勃服务器的公钥文件的路径。文件必须包含PEM编码数据。
  HubbleTLSCertFile string

  // HubbleTLSKeyFile specifies the path to the private key file for the
  // Hubble server. The file must contain PEM encoded data.
  // HubbleTLSKeyFile指定哈勃服务器的私钥文件的路径。文件必须包含PEM编码数据。
  HubbleTLSKeyFile string

  // HubbleTLSClientCAFiles specifies the path to one or more client CA
  // certificates to use for TLS with mutual authentication (mTLS). The files
  // must contain PEM encoded data.
  // HubbleTLSClientCAFiles指定用于具有相互身份验证(MTLS)的TLS的一个或多个客户端CA证书的路径。文件必须包含PEM编码数据。
  HubbleTLSClientCAFiles []string

  // HubbleFlowBufferSize specifies the maximum number of flows in Hubble's buffer.
  // Deprecated: please, use HubbleEventBufferCapacity instead.
  // HubbleFlowBufferSize指定哈勃缓冲区中的最大流数。
  HubbleFlowBufferSize int

  // HubbleEventBufferCapacity specifies the capacity of Hubble events buffer.
  // HubbleEventBufferCapacity指定哈勃事件缓冲区的容量
  HubbleEventBufferCapacity int

  // HubbleEventQueueSize specifies the buffer size of the channel to receive monitor events.
  // HubbleEventQueueSize指定接收监控事件的通道的缓冲区大小
  HubbleEventQueueSize int

  // HubbleMetricsServer specifies the addresses to serve Hubble metrics on.
  // HubbleMetricsServer指定提供哈勃度量的地址。
  HubbleMetricsServer string

  // HubbleMetrics specifies enabled metrics and their configuration options.
  // HubbleMetrics指定启用的指标及其配置选项。
  HubbleMetrics []string

  // HubbleExportFilePath specifies the filepath to write Hubble events to.
  // e.g. "/var/run/cilium/hubble/events.log"
  // HubbleExportFilePath指定要将哈勃事件写入的文件路径。
  HubbleExportFilePath string

  // HubbleExportFileMaxSizeMB specifies the file size in MB at which to rotate
  // the Hubble export file.
  // HubbleExportFileMaxSizeMB指定旋转哈勃导出文件的文件大小(MB)。
  HubbleExportFileMaxSizeMB int

  // HubbleExportFileMaxBacks specifies the number of rotated files to keep.
  // HubbleExportFileMaxBacks指定了要保留的 rotated 文件的数量。
  HubbleExportFileMaxBackups int

  // HubbleExportFileCompress specifies whether rotated files are compressed.
  // HubbleExportFileCompress指定是否压缩旋转的文件。
  HubbleExportFileCompress bool

  // EnableHubbleRecorderAPI specifies if the Hubble Recorder API should be served
  // EnableHubbleRecorderAPI指定是否应提供哈勃记录器API
  EnableHubbleRecorderAPI bool

  // HubbleRecorderStoragePath specifies the directory in which pcap files
  // created via the Hubble Recorder API are stored
  // HubbleRecorderStoragePath指定存储通过哈勃记录器API创建的pcap文件的目录
  HubbleRecorderStoragePath string

  // HubbleRecorderSinkQueueSize is the queue size for each recorder sink
  // HubbleRecorderSinkQueueSize是每个记录器接收器的队列大小
  HubbleRecorderSinkQueueSize int

  // K8sHeartbeatTimeout configures the timeout for apiserver heartbeat
  // K8sHeartbeatTimeout配置apiserver心跳超时
  K8sHeartbeatTimeout time.Duration

  // EndpointStatus enables population of information in the
  // CiliumEndpoint.Status resource
  // EndpointStatus启用CiliumEndpointStatus资源中的信息填充
  EndpointStatus map[string]struct{}

  // DisableIptablesFeederRules specifies which chains will be excluded
  // when installing the feeder rules
  // DisableIptablesFeederRules指定安装馈送器规则时将排除哪些链
  DisableIptablesFeederRules []string

  // EnableIPv4FragmentsTracking enables IPv4 fragments tracking for
  // L4-based lookups. Needs LRU map support.
  // EnableIPv4FragmentsTracking为基于L4的查找启用IPv4片段跟踪。需要LRU映射支持。
  EnableIPv4FragmentsTracking bool

  // FragmentsMapEntries is the maximum number of fragmented datagrams
  // that can simultaneously be tracked in order to retrieve their L4
  // ports for all fragments.
  // FragmentsMapEntries是可以同时跟踪的碎片数据报的最大数量，以便为所有片段检索其L4端口。
  FragmentsMapEntries int

  // sizeofCTElement is the size of an element (key + value) in the CT map.
  // sizeofCTElement是CT Map 中一个元素（键+值）的大小。
  sizeofCTElement int

  // sizeofNATElement is the size of an element (key + value) in the NAT map.
  // sizeofNATElement是NAT Map  中一个元素（键+值）的大小。
  sizeofNATElement int

  // sizeofNeighElement is the size of an element (key + value) in the neigh
  // map.
  // sizeofNeighElement是neigh Map中一个元素（key + value）的大小。
  sizeofNeighElement int

  // sizeofSockRevElement is the size of an element (key + value) in the neigh
  // map.
  // sizeofSockRevElement是neigh Map 中一个元素（key + value）的大小。
  sizeofSockRevElement int

  k8sEnableAPIDiscovery bool

  // k8sEnableLeasesFallbackDiscovery enables k8s to fallback to API probing to check
  // for the support of Leases in Kubernetes when there is an error in discovering
  // API groups using Discovery API.
  // We require to check for Leases capabilities in operator only, which uses Leases for leader
  // election purposes in HA mode.
  // This is only enabled for cilium-operator
  // k8sEnableLeasesFallbackDiscovery使k8s能够在使用Discovery API发现API组时出现错误时，
  // 退回到API探测，以检查Kubernetes中的租赁支持。我们只要求检查运营商的租赁能力，
  // 因为运营商在HA模式下使用租赁来选举领导者。这仅在cilium-operator中启用。
  k8sEnableLeasesFallbackDiscovery bool

  // LBMapEntries is the maximum number of entries allowed in BPF lbmap.
  //  LBMapEntries是BPF lbmap中允许的最大条目数。
  LBMapEntries int

  // k8sServiceProxyName is the value of service.kubernetes.io/service-proxy-name label,
  // that identifies the service objects Cilium should handle.
  // If the provided value is an empty string, Cilium will manage service objects when
  // the label is not present. For more details -
  // k8sServiceProxyName是service.kubernetes.io/service-proxy-name标签的值，它标识了Cilium应该处理的服务对象。 
  // 如果提供的值是一个空字符串，Cilium将在标签不存在时管理服务对象。更多细节 -
  // https://github.com/kubernetes/enhancements/blob/master/keps/sig-network/0031-20181017-kube-proxy-services-optional.md
  k8sServiceProxyName string

  // APIRateLimitName enables configuration of the API rate limits
  // APIRateLimitName可以配置API速率限制。
  APIRateLimit map[string]string

  // CRDWaitTimeout is the timeout in which Cilium will exit if CRDs are not
  // available.
  // CRDWaitTimeout是指如果CRD不可用，Cilium将退出的时间。
  CRDWaitTimeout time.Duration

  // EgressMultiHomeIPRuleCompat instructs Cilium to use a new scheme to
  // store rules and routes under ENI and Azure IPAM modes, if false.
  // Otherwise, it will use the old scheme.
  // EgressMultiHomeIPRuleCompat指示Cilium使用新的方案来存储ENI和Azure IPAM模式下的规则和路由，如果是假的。否则，它将使用旧方案。
  EgressMultiHomeIPRuleCompat bool

  // EnableBPFBypassFIBLookup instructs Cilium to enable the FIB lookup bypass optimization for nodeport reverse NAT handling.
  // EnableBPFBypassFIBLookup指示Cilium为nodeport反向NAT处理启用FIB查询旁路优化。
  EnableBPFBypassFIBLookup bool

  // InstallNoConntrackIptRules instructs Cilium to install Iptables rules to skip netfilter connection tracking on all pod traffic.
  // InstallNoConntrackIptRules指示Cilium安装Iptables规则以跳过所有Pod流量的netfilter连接跟踪。
  InstallNoConntrackIptRules bool

  // EnableCustomCalls enables tail call hooks for user-defined custom
  // eBPF programs, typically used to collect custom per-endpoint
  // metrics.
  // EnableCustomCalls使用户定义的自定义eBPF程序的尾部调用钩，通常用于收集自定义的每端点指标。
  EnableCustomCalls bool

  // BGPAnnounceLBIP announces service IPs of type LoadBalancer via BGP.
  // BGPAnnounceLBIP通过BGP宣布LoadBalancer类型的服务IP。
  BGPAnnounceLBIP bool

  // BGPConfigPath is the file path to the BGP configuration. It is
  // compatible with MetalLB's configuration.
  // BGPConfigPath是BGP配置的文件路径。它与MetalLB的配置兼容。
  BGPConfigPath string

  // ExternalClusterIP enables routing to ClusterIP services from outside
  // the cluster. This mirrors the behaviour of kube-proxy.
  // ExternalClusterIP支持从群集外部路由到ClusterIP服务。这反映了Kube-Proxy的行为。
  ExternalClusterIP bool

  // ARPPingRefreshPeriod is the ARP entries refresher period.
  // ARPPingRechresh Period是ARP条目刷新周期。
  ARPPingRefreshPeriod time.Duration
}
```

cilium-agent start argument:

```bash
"auto-direct-node-routes": "false",
"bpf-lb-map-max": "65536",
"bpf-map-dynamic-size-ratio": "0.0025",
"bpf-policy-map-max": "16384",
"cilium-endpoint-gc-interval": "5m0s",
"cluster-id": "",
"cluster-name": "default",
"cluster-pool-ipv4-cidr": "10.0.0.0/8",
"cluster-pool-ipv4-mask-size": "24",
"custom-cni-conf": "false",
"debug": "false",
"disable-cnp-status-updates": "true",
"enable-auto-protect-node-port-range": "true",
"enable-bandwidth-manager": "false",
"enable-bpf-clock-probe": "true",
"enable-bpf-masquerade": "true",
"enable-endpoint-health-checking": "true",
"enable-health-check-nodeport": "true",
"enable-health-checking": "true",
"enable-hubble": "true",
"enable-ipv4": "true",
"enable-ipv6": "false",
"enable-l7-proxy": "true",
"enable-local-redirect-policy": "false",
"enable-policy": "default",
"enable-remote-node-identity": "true",
"enable-session-affinity": "true",
"enable-well-known-identities": "false",
"enable-xt-socket-fallback": "true",
"hubble-disable-tls": "false",
"hubble-listen-address": ":4244",
"hubble-socket-path": "/var/run/cilium/hubble.sock",
"hubble-tls-cert-file": "/var/lib/cilium/tls/hubble/server.crt",
"hubble-tls-client-ca-files": "/var/lib/cilium/tls/hubble/client-ca.crt",
"hubble-tls-key-file": "/var/lib/cilium/tls/hubble/server.key",
"identity-allocation-mode": "crd",
"install-iptables-rules": "true",
"ipam": "cluster-pool",
"kube-proxy-replacement": "probe",
"kube-proxy-replacement-healthz-bind-address": "",
"masquerade": "true",
"monitor-aggregation": "medium",
"monitor-aggregation-flags": "all",
"monitor-aggregation-interval": "5s",
"node-port-bind-protection": "true",
"operator-api-serve-addr": "127.0.0.1:9234",
"preallocate-bpf-maps": "false",
"sidecar-istio-proxy-image": "cilium/istio_proxy",
"tunnel": "vxlan",
"wait-bpf-mount": "false"

```