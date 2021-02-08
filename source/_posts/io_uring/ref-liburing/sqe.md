---
title: 'SQE:提交队列条目'
date: 2021-02-08 10:06:19
categories: ["Lord of the io_uring"]
tags:
  - io_uring

---


提交队列条目(SQE)是用来告诉 **`io_uring`** 你想要做什么，比如读取一个文件，写一个文件，监听一个 socket 上的连接等等。

	# 注意
	这个结构是原始 io_uring 接口的一部分，在 io_uring.h 中定义。

## struct io_uring_sqe

```c
struct io_uring_sqe {
    __u8    opcode;         /* 此sqe的操作类型 */
    __u8    flags;          /* IOSQE_ 标记 */
    __u16   ioprio;         /* 请求的ioprio */
    __s32   fd;             /* 要执行IO的文件描述符 */
    union {
        __u64       off;    /* offset into file */
        __u64       addr2;
    };
    union {
        __u64       addr;   /* 指向缓冲区或iovecs的指针 */
        __u64       splice_off_in;
    };
    __u32   len;            /* 缓冲区大小或iovec的数量 */
    union {
        __kernel_rwf_t      rw_flags;
        __u32               fsync_flags;
        __u16               poll_events;
        __u32               sync_range_flags;
        __u32               msg_flags;
        __u32               timeout_flags;
        __u32               accept_flags;
        __u32               cancel_flags;
        __u32               open_flags;
        __u32               statx_flags;
        __u32               fadvise_advice;
        __u32               splice_flags;
    };
    /* 在完成时传回的数据 */
    __u64   user_data;
    union {
        struct {
            /* pack this to avoid bogus arm OABI complaints */
            union {
                /* 索引到固定缓冲区（如果使用 */
                __u16       buf_index;
                /* 用于分组缓冲区选择 */
                __u16       buf_group;
            } __attribute__((packed));
            /* personality to use, if used */
            __u16   personality;
            __s32   splice_fd_in;
        };
        __u64       __pad2[3];
    };
};
```