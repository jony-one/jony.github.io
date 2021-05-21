#include <stdio.h>
#include <assert.h>
#include <linux/bpf.h>
#include <bpf/bpf.h>
#include <bpf/libbpf.h>
#include "sock_example.h"
#include <unistd.h>
#include <arpa/inet.h>

int main(int ac, char **argv)
{
	// bpf 的文件对象
	struct bpf_object *obj;
	// map 文件描述符
	// 程序文件描述符
	int map_fd, prog_fd;
	// 文件名称
	char filename[256];
	int i, sock;
	FILE *f;
	
	snprintf(filename, sizeof(filename), "%s_kern.o", argv[0]);
	// 加载对象，到 BPF_PROG_TYPE_SOCKET_FILTER 返回对象引用
	if (bpf_prog_load(filename, BPF_PROG_TYPE_SOCKET_FILTER,
			  &obj, &prog_fd))
	{
		printf("The Kernel didn't load the BPF Program!\n");
		return 1;
	}
	// 查找 map
	map_fd = bpf_object__find_map_fd_by_name(obj, "my_map");
	
	// 打开 socket 
	sock = open_raw_sock("ens33");
	// 将程序加载到 socket
	assert(setsockopt(sock, SOL_SOCKET, SO_ATTACH_BPF, &prog_fd,
			  sizeof(prog_fd)) == 0);
	// 
	f = popen("ping -4 -c5 localhost", "r");
	(void) f;

	for (i = 0; i < 20; i++) {
		long long tcp_cnt, udp_cnt, icmp_cnt;
		int key;
		
		// map 查找 key 并返回值
		key = IPPROTO_TCP;
		assert(bpf_map_lookup_elem(map_fd, &key, &tcp_cnt) == 0);

		key = IPPROTO_UDP;
		assert(bpf_map_lookup_elem(map_fd, &key, &udp_cnt) == 0);

		key = IPPROTO_ICMP;
		assert(bpf_map_lookup_elem(map_fd, &key, &icmp_cnt) == 0);

		printf("TCP %lld UDP %lld ICMP %lld bytes\n",
		       tcp_cnt, udp_cnt, icmp_cnt);
		sleep(1);
	}

	return 0;
}
