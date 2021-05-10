#include <bcc/proto.h>
#include <uapi/linux/ip.h>
#include <uapi/linux/ipv6.h>
#include <uapi/linux/icmp.h>
#include <uapi/linux/icmpv6.h>
#include <net/inet_sock.h>
#include <linux/netfilter/x_tables.h>

#define ROUTE_EVT_IF 1
#define ROUTE_EVT_IPTABLE 2

#define member_address(source_struct, source_member) \
({ \
    void* __ret; \
    __ret = (void*)(((char*)source_struct)+ offsetof(typeof(*source_struct), source_member)); \
})

#define member_read(destination, source_struct, source_member)  \
  do{                                                           \
    bpf_probe_read(destination, sizeof(source_struct->source_member), (((char*)source_struct) + offsetof(typeof(*source_struct), source_member)) );   \
  } while(0)


// Event structure
struct route_evt_t {
        // char comm[TASK_COMM_LEN];
    /* Content flags */
    u64 flags;

    /* Routing information */
    char ifname[IFNAMSIZ];
    u64 netns;

    /* Packet type (IPv4 or IPv6) and address */
    u64 ip_version; // familiy (IPv4 or IPv6)
    u64 icmptype;
    u64 icmpid;     // In practice, this is the PID of the ping process (see "ident" field in https://github.com/iputils/iputils/blob/master/ping_common.c)
    u64 icmpseq;    // Sequence number
    u64 saddr[2];   // Source address. IPv4: store in saddr[0]
    u64 daddr[2];   // Dest   address. IPv4: store in daddr[0]

    /* Iptables trace */
    u64 hook;
    u64 verdict;
    char tablename[XT_TABLE_MAXNAMELEN];
};

BPF_PERF_OUTPUT(route_evt);

static inline int do_trace(void* ctx, struct sk_buff* skb)
{
    // Built event for userland
    struct route_evt_t evt = {};
    // bpf_get_current_comm(evt.comm, TASK_COMM_LEN);

    // add NIC info 
	struct net_device *dev;
	member_read(&dev, skb, dev);
	bpf_probe_read(&evt.ifname, IFNAMSIZ, dev->name);



    // Send event to userland
    route_evt.perf_submit(ctx, &evt, sizeof(evt));


    struct net* net;

    possible_net_t *skc_net = &dev->nd_net;
    member_read(&net,skc_net, net);
    struct ns_common* ns = member_address(net, ns);
    member_read(&evt.netns, ns, inum);
    

    char* head;
    u16 mac_header;
    member_read(&head, skb, head);
    member_read(&mac_head, skb, mac_header);
    #define MAC_HEADER_SIZE 14;
    char* ip_header_address = head + mac_header + MAC_HEADER_SIZE;

    u8 ip_version;
    bpf_probe_read(&ip_version, sizeof(u8), ip_header_address);
    ip_version = ip_version >> 4 & 0xf;

    if (evt->ip_version == 4) {
        // Load IP Header
        struct iphdr iphdr;
        bpf_probe_read(&iphdr, sizeof(iphdr), ip_header_address);

        // Load protocol and address
        icmp_offset_from_ip_header = iphdr.ihl * 4;
        l4proto      = iphdr.protocol;
        evt->saddr[0] = iphdr.saddr;
        evt->daddr[0] = iphdr.daddr;

        // Load constants
        proto_icmp = IPPROTO_ICMP;
        proto_icmp_echo_request = ICMP_ECHO;
        proto_icmp_echo_reply   = ICMP_ECHOREPLY;
    }

    char* icmp_header_address = ip_header_address + icmp_offset_from_ip_header;
    struct icmphdr icmphdr;
    bpf_probe_read(&icmphdr, sizeof(icmphdr), icmp_header_address);

    if(icmphdr.type != ICMP_ECHO && icmphdr.type != ICMP_ECHOREPLY){
        return 0;
    }

    evt.icmptype = icmphdr.type;
    evt.icmpid = icmphdr.un.echo.id;
    evt.icmpseq = icmphdr.un.echo.id;
    evt.icmpid = be16_to_cpu(evt.icmpid);
    evt.icmpseq = be16_to_cpu(evt.icmpseq);


    return 0;
}

/**
  * Attach to Kernel Tracepoints
  */
TRACEPOINT_PROBE(net, netif_rx) {
    return do_trace(args, (struct sk_buff*)args->skbaddr);
}

TRACEPOINT_PROBE(net, net_dev_queue) {
    return do_trace(args, (struct sk_buff*)args->skbaddr);
}

TRACEPOINT_PROBE(net, napi_gro_receive_entry) {
    return do_trace(args, (struct sk_buff*)args->skbaddr);
}

TRACEPOINT_PROBE(net, netif_receive_skb_entry) {
    return do_trace(args, (struct sk_buff*)args->skbaddr);
}