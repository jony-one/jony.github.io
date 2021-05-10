#!/usr/bin/env python
# coding: utf-8

from socket import inet_ntop
from bcc import BPF
import ctypes as ct

TASK_COMM_LEN = 16

IFNAMSIZ = 16 # uapi/linux/if.h
XT_TABLE_MAXNAMELEN = 32 # uapi/linux/netfilter/x_tables.h

class RouteEvt(ct.Structure):
    _fields_ = [
        ("comm",    ct.c_char * TASK_COMM_LEN),
        ("ifname",    ct.c_char * IFNAMSIZ),
        ("netns",    ct.c_ulonglong),
    ]

def event_printer(cpu, data, size):
    # Decode event
    event = ct.cast(data, ct.POINTER(RouteEvt)).contents

    # Print event
    print ("Just got a packet from ifname: %16s , NS: %12s" % (event.comm,event.netns))

if __name__ == "__main__":
    b = BPF(src_file='tracepkt.c')
    b["route_evt"].open_perf_buffer(event_printer)

    while True:
        b.kprobe_poll()
