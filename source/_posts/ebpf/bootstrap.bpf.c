// SPDX-License-Identifier: GPL-2.0 OR BSD-3-Clause
/* Copyright (c) 2020 Facebook */
#include "vmlinux.h"
#include <bpf/bpf_helpers.h>
#include <bpf/bpf_tracing.h>
#include <bpf/bpf_core_read.h>
#include "bootstrap.h"

char LICENSE[] SEC("license") = "Dual BSD/GPL";

struct {
        __uint(type, BPF_MAP_TYPE_HASH);
        __uint(max_entries, 8192);
        __type(key, pid_t);
        __type(value, u64);
} exec_start SEC(".maps");

struct {
        __uint(type, BPF_MAP_TYPE_RINGBUF);
        __uint(max_entries, 256 * 1024);
} rb SEC(".maps");

const volatile unsigned long long min_duration_ns = 0;

SEC("tp/sched/sched_process_exec")
int handle_exec(struct trace_event_raw_sched_process_exec *ctx)
{
        struct task_struct *task;
        unsigned fname_off;
        struct event *e;
        pid_t pid;
        u64 ts;

        /* 记住这个PID的exec()的执行时间 */
        pid = bpf_get_current_pid_tgid() >> 32;
        ts = bpf_ktime_get_ns();
        bpf_map_update_elem(&exec_start, &pid, &ts, BPF_ANY);

        /* 当指定最小持续时间时，不发出执行事件 */
        if (min_duration_ns)
                return 0;

        /* 从BPF ringbuf中保留样本 */
        e = bpf_ringbuf_reserve(&rb, sizeof(*e), 0);
        if (!e)
                return 0;

        /* 用数据填报样本 */
        task = (struct task_struct *)bpf_get_current_task();

        e->exit_event = false;
        e->pid = pid;
        // 读取 task->real_parent->tgid
        e->ppid = BPF_CORE_READ(task, real_parent, tgid);
        bpf_get_current_comm(&e->comm, sizeof(e->comm));

        fname_off = ctx->__data_loc_filename & 0xFFFF;
        bpf_probe_read_str(&e->filename, sizeof(e->filename), (void *)ctx + fname_off);

        /* 成功地将其提交给用户空间进行后处理 */
        bpf_ringbuf_submit(e, 0);
        return 0;
}

SEC("tp/sched/sched_process_exit")
int handle_exit(struct trace_event_raw_sched_process_template* ctx)
{
        struct task_struct *task;
        struct event *e;
        pid_t pid, tid;
        u64 id, ts, *start_ts, duration_ns = 0;

        /* 获取退出线程/进程的PID和TID */
        id = bpf_get_current_pid_tgid();
        pid = id >> 32;
        tid = (u32)id;

        /* 忽略线程并退出 */
        if (pid != tid)
                return 0;

        /* 如果我们记录了这个进程的开始，就计算出生命周期的长度 */
        start_ts = bpf_map_lookup_elem(&exec_start, &pid);
        if (start_ts)
                duration_ns = bpf_ktime_get_ns() - *start_ts;
        else if (min_duration_ns)
                return 0;
        bpf_map_delete_elem(&exec_start, &pid);

        /* 如果进程中没有足够的时间，就提前返回*/
        if (min_duration_ns && duration_ns < min_duration_ns)
                return 0;

        /* 从BPF ringbuf中保留样本 */
        e = bpf_ringbuf_reserve(&rb, sizeof(*e), 0);
        if (!e)
                return 0;

        /* 用数据填报样本 */
        task = (struct task_struct *)bpf_get_current_task();

        e->exit_event = true;
        e->duration_ns = duration_ns;
        e->pid = pid;
        e->ppid = BPF_CORE_READ(task, real_parent, tgid);
        e->exit_code = (BPF_CORE_READ(task, exit_code) >> 8) & 0xff;
        bpf_get_current_comm(&e->comm, sizeof(e->comm));

        /* 将数据发送到用户空间进行后处理 */
        bpf_ringbuf_submit(e, 0);
        return 0;
}