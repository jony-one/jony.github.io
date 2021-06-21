from bcc import BPF
import ctypes

bpf_text = """

#include <net/inet_sock.h>
#include <bcc/proto.h>



struct receive_evt_t {
    __u16 mac_len;
    u16 sk_protocol;
    unsigned int len;
};

BPF_PERF_OUTPUT(receive_evt);


int kprobe__inet_gro_receive(struct pt_regs *ctx, struct list_head *head,struct sk_buff *skb)
{
    __u16 mac_len;
    u16 sk_protocol;
    unsigned int len;

    // get mac_len
    bpf_probe_read_kernel(&mac_len,sizeof(mac_len),&(skb->mac_len));
    bpf_probe_read_kernel(&sk_protocol,sizeof(sk_protocol),&(skb->sk->sk_protocol));
    bpf_probe_read_kernel(&len,sizeof(len),&(skb->len));

    struct receive_evt_t evt = {
        .mac_len = mac_len,
        .sk_protocol = sk_protocol,
        .len = len,
    };

    receive_evt.perf_submit(ctx, &evt, sizeof(evt));
    return 0;
};

"""

class GroEvt(ctypes.Structure):
    _fields_ = [
            ("mac_len",ctypes.c_ulong),
            ("sk_protocol",ctypes.c_ulong),
            ("len",ctypes.c_ulong),
            ]


def print_event(cpu,data,size):
    event = ctypes.cast(data, ctypes.POINTER(GroEvt)).contents
    print("mac:{} protocol:{} len:{}".format(event.mac_len,event.sk_protocol,event.len))

b = BPF(text = bpf_text)
#b.trace_print()
b["receive_evt"].open_perf_buffer(print_event)
while True:
   b.kprobe_poll()

