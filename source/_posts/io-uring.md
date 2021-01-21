---
title: io_uring
date: 2021-01-21 14:25:57
tags:
---


## 使用 io_uring 实现 cat 命令
``` c
#include <stdio.h>
#include <stdlib.h>
#include <sys/stat.h>
#include <sys/ioctl.h>
#include <sys/syscall.h>
#include <sys/mman.h>
#include <sys/uio.h>
#include <linux/fs.h>
#include <fcntl.h>
#include <unistd.h>
#include <string.h>

/* 如果你编译失败缺少这个头文件，那么是因为你得内核版本太低了
* */
#include <linux/io_uring.h>

#define QUEUE_DEPTH 1
#define BLOCK_SZ    1024

/*  x86规范 */
#define read_barrier()  __asm__ __volatile__("":::"memory")
#define write_barrier() __asm__ __volatile__("":::"memory")


/**
struct io_uring_sqe {
  __u8  opcode;   // sqe 操作类型代码
  __u8  flags;    // IOSQE_ flags 
  __u16  ioprio;  // ioprio for the request 
  __s32  fd;      // file descriptor to do IO on 
  __u64  off;     // offset into file 
  __u64  addr;    // pointer to buffer or iovecs 
  __u32  len;     // buffer size or number of iovecs 
  union {
    __kernel_rwf_t  rw_flags;
    __u32    fsync_flags;
    __u16    poll_events;
    __u32    sync_range_flags;
    __u32    msg_flags;
  };
  __u64  user_data;   // data to be passed back at completion time 
  union {
    __u16  buf_index; // index into fixed buffers, if used 
    __u64  __pad2[3];
  };
};
*/

struct app_io_sq_ring { // Submission ring
    unsigned *head;
    unsigned *tail;
    unsigned *ring_mask;
    unsigned *ring_entries;
    unsigned *flags;
    unsigned *array;
};

struct app_io_cq_ring { // completions ring
    unsigned *head;
    unsigned *tail;
    unsigned *ring_mask;
    unsigned *ring_entries;
    struct io_uring_cqe *cqes;
};

struct submitter {
    int ring_fd;
    struct app_io_sq_ring sq_ring;
    struct io_uring_sqe *sqes;
    struct app_io_cq_ring cq_ring;
};

struct file_info {
    off_t file_sz;
    struct iovec iovecs[];      /* Referred by readv/writev */
};

/*
* 这段代码是在io_uring相关的系统调用不属于标准C库的时候编写的。 
* 因此，我们推出了自己的系统调用封装函数。
* */

int io_uring_setup(unsigned entries, struct io_uring_params *p)
{
    return (int) syscall(__NR_io_uring_setup, entries, p);
}

int io_uring_enter(int ring_fd, unsigned int to_submit,
                        unsigned int min_complete, unsigned int flags)
{
    return (int) syscall(__NR_io_uring_enter, ring_fd, to_submit, min_complete,
                flags, NULL, 0);
}

/*
* 返回传传入 io_uring 的打开的文件描述符的文件的大小.
* 正确处理常规文件和驱动设备
* */

off_t get_file_size(int fd) {
    struct stat st;

    if(fstat(fd, &st) < 0) {
        perror("fstat");
        return -1;
    }
    if (S_ISBLK(st.st_mode)) {
        unsigned long long bytes;
        if (ioctl(fd, BLKGETSIZE64, &bytes) != 0) {
            perror("ioctl");
            return -1;
        }
        return bytes;
    } else if (S_ISREG(st.st_mode))
        return st.st_size;

    return -1;
}

/*
* io_uring需要进行大量的设置，这些设置看起来相当复杂，但并不都很难理解。
* 因为所有这些案例代码， io_uring的作者创建了liburing，它相对容易使用。
* 但是，您应该花些时间来理解这段代码。
* 了解它的工作原理总是好的。除了吹嘘的权利，它确实给你一种奇怪的极客的感觉。
* */

int app_setup_uring(struct submitter *s) {
    struct app_io_sq_ring *sring = &s->sq_ring;
    struct app_io_cq_ring *cring = &s->cq_ring;
    struct io_uring_params p;
    void *sq_ptr, *cq_ptr;

    /*
    * 我们需要将 io_uring_params 结构传递给的io_uring_setup（）调用并设置为0。 
    * 如果需要，我们可以设置任何标志，但是对于本示例，我们不需要。
    * */
    memset(&p, 0, sizeof(p));
    s->ring_fd = io_uring_setup(QUEUE_DEPTH, &p);
    if (s->ring_fd < 0) {
        perror("io_uring_setup");
        return 1;
    }

    /*
    * io_uring 通过两个 kernel-user 共享空间形成环形缓冲区通信。在最近的内核中，可以用 mmap() 调用来
    * 联合映射这个环形缓冲区。在直接操作完成队列时，提交队列之间有一个间接数组。我们也把它映射进去。
    * */

    int sring_sz = p.sq_off.array + p.sq_entries * sizeof(unsigned);
    int cring_sz = p.cq_off.cqes + p.cq_entries * sizeof(struct io_uring_cqe);

    /* 
    * 在内核版本 5.4 及以上，可能使用一个 mmap()  来映射 completion 缓冲区和 submission缓冲区.
    * 如果去检测内核版本还不如直接使用 io_uring_params 结构的特征字段，这是一个位掩码。如果 IORING_FEAT_SINGLE_MMAP 是
    * 一个 set 表，那么我们可以取消第二个 mmap()
    * */
    if (p.features & IORING_FEAT_SINGLE_MMAP) {
        if (cring_sz > sring_sz) {
            sring_sz = cring_sz;
        }
        cring_sz = sring_sz;
    }

    /* 
    * submission 队列和 completion 队列可以映射到 ring 缓冲区，但是老板的内核只能映射到队列中。
    * */
    sq_ptr = mmap(0, sring_sz, PROT_READ | PROT_WRITE,
            MAP_SHARED | MAP_POPULATE,
            s->ring_fd, IORING_OFF_SQ_RING);
    if (sq_ptr == MAP_FAILED) {
        perror("mmap");
        return 1;
    }

    if (p.features & IORING_FEAT_SINGLE_MMAP) {
        cq_ptr = sq_ptr;
    } else {
        /* 在旧的内核中分别映射完成队列环缓冲区 */
        cq_ptr = mmap(0, cring_sz, PROT_READ | PROT_WRITE,
                MAP_SHARED | MAP_POPULATE,
                s->ring_fd, IORING_OFF_CQ_RING);
        if (cq_ptr == MAP_FAILED) {
            perror("mmap");
            return 1;
        }
    }
    /*  将有用的字段保存在全局app_io_sq_ring结构中，以便以后方便地引用*/
    sring->head = sq_ptr + p.sq_off.head;
    sring->tail = sq_ptr + p.sq_off.tail;
    sring->ring_mask = sq_ptr + p.sq_off.ring_mask;
    sring->ring_entries = sq_ptr + p.sq_off.ring_entries;
    sring->flags = sq_ptr + p.sq_off.flags;
    sring->array = sq_ptr + p.sq_off.array;

    /* 映射到提交队列条目数组中 */
    s->sqes = mmap(0, p.sq_entries * sizeof(struct io_uring_sqe),
            PROT_READ | PROT_WRITE, MAP_SHARED | MAP_POPULATE,
            s->ring_fd, IORING_OFF_SQES);
    if (s->sqes == MAP_FAILED) {
        perror("mmap");
        return 1;
    }

    /* 将有用的字段保存在全局的app_io_cq_ring结构中，以便以后方便地引用 */
    cring->head = cq_ptr + p.cq_off.head;
    cring->tail = cq_ptr + p.cq_off.tail;
    cring->ring_mask = cq_ptr + p.cq_off.ring_mask;
    cring->ring_entries = cq_ptr + p.cq_off.ring_entries;
    cring->cqes = cq_ptr + p.cq_off.cqes;

    return 0;
}

/*
* 将长度为len的字符串输出到标准输出。我们在这里使用缓冲输出以提高效率，因为我们需要逐字符输出。
* */
void output_to_console(char *buf, int len) {
    while (len--) {
        fputc(*buf++, stdout);
    }
}

/*
* 从 completion 队列读取。在这个函数中，我们从 completion 队列中读取完成事件，
* 获取包含文件数据的数据缓冲区，并将其打印到控制台。
* */

void read_from_cq(struct submitter *s) {
    struct file_info *fi;
    struct app_io_cq_ring *cring = &s->cq_ring;
    struct io_uring_cqe *cqe;
    unsigned head, reaped = 0;

    head = *cring->head;

    do {
        read_barrier();
        /*
        * 记住，这是一个环形缓冲区。如果head == tail，则表示缓冲区为空。
        * */
        if (head == *cring->tail)
            break;

        /* 获取一个条目 */
        cqe = &cring->cqes[head & *s->cq_ring.ring_mask];
        fi = (struct file_info*) cqe->user_data;
        if (cqe->res < 0)
            fprintf(stderr, "Error: %s\n", strerror(abs(cqe->res)));

        int blocks = (int) fi->file_sz / BLOCK_SZ;
        if (fi->file_sz % BLOCK_SZ) blocks++;

        for (int i = 0; i < blocks; i++)
            output_to_console(fi->iovecs[i].iov_base, fi->iovecs[i].iov_len);

        head++;
    } while (1);

    *cring->head = head;
    write_barrier();
}
/*
* 提交到 submission 队列.
* 在这个方法, 我们提交请求到 submission 队列. 
* 您可以提交多种类型的请求。我们的请求将是readv()请求，它是通过IORING_OP_READV指定的。
*
* */
int submit_to_sq(char *file_path, struct submitter *s) {
    struct file_info *fi;

    int file_fd = open(file_path, O_RDONLY);
    if (file_fd < 0 ) {
        perror("open");
        return 1;
    }

    struct app_io_sq_ring *sring = &s->sq_ring;
    unsigned index = 0, current_block = 0, tail = 0, next_tail = 0;

    off_t file_sz = get_file_size(file_fd);
    if (file_sz < 0)
        return 1;
    off_t bytes_remaining = file_sz;
    int blocks = (int) file_sz / BLOCK_SZ;
    if (file_sz % BLOCK_SZ) blocks++;

    fi = malloc(sizeof(*fi) + sizeof(struct iovec) * blocks);
    if (!fi) {
        fprintf(stderr, "Unable to allocate memory\n");
        return 1;
    }
    fi->file_sz = file_sz;

    /*
    * 对于需要读取的每个文件块，我们分配一个iovec结构体，该结构体被索引到iovecs数组中。
    * 此数组作为提交的一部分传入。如果您不理解这一点，那么您需要查看readv()和writev()系统调用是如何工作的。
    * */
    while (bytes_remaining) {
        off_t bytes_to_read = bytes_remaining;
        if (bytes_to_read > BLOCK_SZ)
            bytes_to_read = BLOCK_SZ;

        fi->iovecs[current_block].iov_len = bytes_to_read;

        void *buf;
        if( posix_memalign(&buf, BLOCK_SZ, BLOCK_SZ)) {
            perror("posix_memalign");
            return 1;
        }
        fi->iovecs[current_block].iov_base = buf;

        current_block++;
        bytes_remaining -= bytes_to_read;
    }

    /* 将我们的 submission 队列条目添加到SQE ring缓冲区的尾部 */
    next_tail = tail = *sring->tail;
    next_tail++;
    read_barrier();
    index = tail & *s->sq_ring.ring_mask;
    struct io_uring_sqe *sqe = &s->sqes[index];
    sqe->fd = file_fd;
    sqe->flags = 0;
    sqe->opcode = IORING_OP_READV;
    sqe->addr = (unsigned long) fi->iovecs;
    sqe->len = blocks;
    sqe->off = 0;
    sqe->user_data = (unsigned long long) fi;
    sring->array[index] = index;
    tail = next_tail;

    /* 更新尾部以便内核能够看到它. */
    if(*sring->tail != tail) {
        *sring->tail = tail;
        write_barrier();
    }

    /*
    * 告诉内核我们已经通过io_uring_enter()系统调用提交了事件。
    * 我们还传入IOURING_ENTER_GETEVENTS标志，这将导致io_uring_enter()调用
    * 等待min_complete事件(第3个参数)完成。
    * */
    int ret =  io_uring_enter(s->ring_fd, 1,1,
            IORING_ENTER_GETEVENTS);
    if(ret < 0) {
        perror("io_uring_enter");
        return 1;
    }

    return 0;
}

int main(int argc, char *argv[]) {
    struct submitter *s;

    if (argc < 2) {
        fprintf(stderr, "Usage: %s <filename>\n", argv[0]);
        return 1;
    }

    s = malloc(sizeof(*s));
    if (!s) {
        perror("malloc");
        return 1;
    }
    memset(s, 0, sizeof(*s));

    if(app_setup_uring(s)) {
        fprintf(stderr, "Unable to setup uring!\n");
        return 1;
    }

    for (int i = 1; i < argc; i++) {
        if(submit_to_sq(argv[i], s)) {
            fprintf(stderr, "Error reading file\n");
            return 1;
        }
        read_from_cq(s);
    }

    return 0;
}
```


## 参考文档
- [The Low-level io_uring Interface](https://unixism.net/loti/low_level.html)
- [Efficient IO with io_uring PDF](https://kernel.dk/io_uring.pdf)
- [Efficient IO with io_uring github](https://github.com/axboe/liburing)