#include <arpa/inet.h>
#include <assert.h>
// #include <bpf/bpf.h>
// #include "bpf/bpf_load.h"
// #include "bpf/sock_example.h"
#include <errno.h>
#include <linux/bpf.h>
#include <linux/if_ether.h>
#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <unistd.h>

// case not running
char bpf_log_buf[1024];

int main(int argc,char **argv){
    int sock = -1, i, key;
    int tcp_cnt, udp_cnt, icmp_cnt;

    char filename[256];
    snprintf(filename, sizeof(filename), "%s", argv[1]);

    if (load_bpf_file(filename))
    {
        printf("%s",bpf_log_buf);
        return 1;
    }

    sock = open_raw_sock("lo");

    if(setsockopt(sock, SOL_SOCKET, SO_ATTACH_BPF, prog_fd,sizeof(prog_fd[0]))){

    }

    return 0;

}