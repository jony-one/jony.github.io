#include <linux/bpf.h>
#include <linux/if_ether.h>
#include <linux/if_packet.h>
#include <linux/ip.h>
#include <bpf/bpf_helpers.h>
#include "bpf_legacy.h"

// 定义 Map
struct bpf_map_def SEC("maps") my_map =  {
	.type = BPF_MAP_TYPE_ARRAY,
	.key_size = sizeof(__u32),
	.value_size = sizeof(long),
	.max_entries = 256,
};

SEC("socket1")
int bpf_prog1(struct __sk_buff *skb)
{
	//
	int index = load_byte(skb, ETH_HLEN + offsetof(struct iphdr, protocol));
	long *value;
	// 根据帧的 L2 目的地址进行类型划分，PACKET_OUTGOING 表示分包正在被发送。
	if (skb->pkt_type != PACKET_OUTGOING)
		return 0;
	// 查找 Map 指定元素并返回其值
	value = bpf_map_lookup_elem(&my_map, &index);
	if (value)
	{
		// 同步加
		__sync_fetch_and_add(value, skb->len);
	}
	return 0;
}
char _license[] SEC("license") = "GPL";
