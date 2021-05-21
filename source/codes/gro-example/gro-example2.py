from bcc import BPF
import ctypes

bpf_text = """

#include <net/inet_sock.h>
#include <bcc/proto.h>



struct receive_evt_t {
    __u32 len;
    __u32 protocol;
    __u32 src_ipv4;
    __u32 src_port;
    __u32 family;
};

BPF_PERF_OUTPUT(receive_evt);


int kprobe__inet_gro_receive(struct __sk_buff  *skb)
{
    __u32 len;
    __u32 protocol;
    __u32 src_ipv4;
    __u32 src_port;
    __u32 family;

    if(skb == NULL){
        return 0;
    }
    // get mac_len
    bpf_probe_read_kernel(&len,sizeof(len),&(skb->len));
  
    bpf_probe_read_kernel(&protocol,sizeof(protocol),&(skb->sk->protocol));

    bpf_probe_read_kernel(&src_ipv4,sizeof(src_ipv4),&(skb->sk->src_ip4));

    bpf_probe_read_kernel(&src_port,sizeof(src_port),&(skb->sk->src_port));

    bpf_probe_read_kernel(&family,sizeof(family),&(skb->sk->family));

    struct receive_evt_t evt = {
        .len = len,
//        .protocol = protocol,
//        .src_ipv4 = src_ipv4,
//        .src_port = src_port,
//        .family = family,
    };

    receive_evt.perf_submit_skb(skb,skb->len,&evt,sizeof(evt));
    return 0;
};

"""

class GroEvt(ctypes.Structure):
    _fields_ = [
            ("len",ctypes.c_ulonglong),
            ("protocol",ctypes.c_ulonglong),
            ("src_ipv4",ctypes.c_ulonglong),
            ("src_port",ctypes.c_ulonglong),
            ("family",ctypes.c_ulonglong),
            ]


def print_event(cpu,data,size):
    event = ctypes.cast(data, ctypes.POINTER(GroEvt)).contents
    print("{} {} {} {} {}".format(event.len, event.protocol, event.src_ipv4, event.src_port, event.family))

b = BPF(text = bpf_text)
#b.trace_print()
b["receive_evt"].open_perf_buffer(print_event)
while True:
   b.kprobe_poll()

