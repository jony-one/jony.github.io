from bcc import BPF
import ctypes
# Hello BPF Program
bpf_text = """
#include <net/inet_sock.h>
#include <bcc/proto.h>

struct listen_evt_t {
    u64 laddr;
    u64 lport;
    u64 netns;
    u64 backlog;
};

BPF_PERF_OUTPUT(listen_evt);


int kprobe__inet_listen(struct pt_regs *ctx, struct socket *sock, int backlog)
{
    // Cast types. Intermediate cast not needed, kept for readability
    struct sock *sk = sock->sk;
    struct inet_sock *inet = inet_sk(sk);
    
    // Working values. You *need* to initialize them to give them "life" on the stack and use them afterward
    u32 laddr = 0;
    u16 lport = 0;

    // Pull in details. As 'inet_sk' is internally a type cast, we need to use 'bpf_probe_read'
    // read: load into 'laddr' 'sizeof(laddr)' bytes from address 'inet->inet_rcv_saddr'
    bpf_probe_read(&laddr, sizeof(laddr), &(inet->inet_rcv_saddr));
    bpf_probe_read(&lport, sizeof(lport), &(inet->inet_sport));
    
    u32 netns = 0;
    netns = sk->__sk_common.skc_net.net->ns.inum;

    // Push event
    bpf_trace_printk("Listening on %x %d with %d \\n", ntohl(laddr), ntohs(lport), netns);
    
    struct listen_evt_t evt = { 
        .laddr = ntohl(laddr), 
        .lport = ntohs(lport), 
        .netns = netns, 
        .backlog = backlog,
    };



    listen_evt.perf_submit(ctx, &evt, sizeof(evt));
    return 0;
};
"""

class ListenEvt(ctypes.Structure):
    _fields_ = [
                ("laddr", ctypes.c_ulonglong),
                ("lport", ctypes.c_ulonglong),
                ("netns", ctypes.c_ulonglong),
                ("backlog", ctypes.c_ulonglong),
            ]

def print_event(cpu, data, size):
    event = ctypes.cast(data, ctypes.POINTER(ListenEvt)).contents
    print("Listening on %x %d with %d pending connections in container %d" % (event.laddr,event.lport,event.backlog,event.netns))

# 2. Build and Inject program
b = BPF(text=bpf_text)

b["listen_evt"].open_perf_buffer(print_event)
while True:
    b.kprobe_poll()
