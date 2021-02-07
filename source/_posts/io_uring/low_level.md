---
title: io_uring 底层接口
date: 2021-02-07 13:47:18
categories: ["Lord of the io_uring"]
tags:
  - io_uring

---


# io_uring 的底层接口

正如在前一个文章中所建议的那样，您不太可能在正式的程序中使用 `io_uring` 底层的 API。但是知道接口真正使用起来是什么样的方式总是一个好主意。为此，您必须通过共享环缓冲区和相关的 `io_uring` 系统调用来处理 `io_uring` 直接呈现给程序的接口。一个很好、简单的例子，可以很好地展示这个接口。为此，在这里，我们提供了一个模拟 Unix cat 实用程序的示例。为了保持简单，我们将创建一个程序，一次显示一个操作，等待它完成并显示下一个操作等等。虽然一个真正的程序也可以使用同步/阻塞调用来以这种方式完成工作，但这个程序的主要目的是让您熟悉 i/o 接口，而不会受到其他程序逻辑的干扰。

## 熟悉[readv（2）](http://man7.org/linux/man-pages/man2/readv.2.html)系统调用

为了更好地理解这个示例，您需要熟悉 `[readv（2）](http://man7.org/linux/man-pages/man2/readv.2.html)`系统调用。如果你不熟悉它，我建议你读[一个更通俗的介绍](https://unixism.net/2020/04/io-uring-by-example-part-1-introduction/)，然后回到这里继续。

## 底层接口介绍
它的接口很简单。有一个提交队列和一个完成队列。在提交队列中，您可以提交你想要完成的各种操作的信息。例如，在我们当前的程序中，我们希望使用 [readv（2）](http://man7.org/linux/man-pages/man2/readv.2.html)读取文件，因此我们放置一个提交队列请求，将其描述为提交队列条目(SQE)的一部分。此外，您可以放置多个请求。根据队列深度(您可以定义)允许的请求数量。这些操作可以是读、写等操作的混合。这些操作可以是读、写等操作的混合。然后，我们调用 `[io_uring_enter()](https://unixism.net/loti/ref-iouring/io_uring_enter.html#c.io_uring_enter)`系统调用，告诉内核我们已经向提交队列添加了请求。一旦它完成了这些请求的处理，它将结果作为 CQE 的一部分放置在完成队列中，或者为每个相应的 SQE 放置一个完成队列条目。这些 CQEs 可以立即从用户空间访问，因为它们被放置在一个由内核和用户空间共享的缓冲区中。

我们在前面已经讨论了 io_uring 的这个特殊优点，但是聪明的读者会注意到这样一个接口: 用多个 i/o 请求填充到一个队列中，然后进行一次系统调用的接口，而不是对每个I/O请求进行一次系统调用，这样就已经很高效了。为了进一步提高效率，`io_uring` 支持了一种模式，在这种模式下，内核会对你加入提交队列的条目进行轮询，
而你甚至不需要调用 **`[io_uring_enter ()](https://unixism.net/loti/ref-iouring/io_uring_enter.html#c.io_uring_enter)`**来通知内核更新的提交队列条目。另一点需要注意的是，
在 **Specter** 和 **Meltdown** 硬件漏洞被发现并且操作系统为其创建了解决方案之后，系统调用比以往任何时候都要昂贵。因此，对于高性能应用程序来说，减少系统调用的数量确实是一件大事。

在执行这些操作之前，需要设置队列，它们实际上是具有一定深度/长度的环形缓冲区。
您可以调用 **`[io_ uring_setup ()](https://unixism.net/loti/ref-iouring/io_uring_setup.html#c.io_uring_setup)`**系统调用来完成此操作。我们通过将提交队列条目添加到循环缓冲区并从完成队
列循环缓冲区读取完成队列条目来完成真正的工作。这是对这个io_uring接口设计的概述。

## 完成队列条目

现在我们已经有了一个关于如何工作的心智模型，让我们更详细地看看这是如何完成的。与提交队列条目(SQE)相比，
完成队列条目(CQE)非常简单。那么，让我们先来看看。SQE 是一个 **`io_uring_sqe`** 结构的实例，您可以使用它提交请求。
将其添加到提交环缓冲区。CQE 是一个 **io_uring_cqe** 结构的实例，内核对添加到提交队列中的每个 **`io_uring_sqe`** 结构实例进行响应。它包含您通过 SQE 实例请求的操作的结果。

```c
struct io_uring_cqe {
  __u64  user_data;   /* sqe->user_data submission passed back */
  __s32  res;         /* 此事件的结果代码 */
  __u32  flags;
};
```

## 将完成与提交相关联

正如在代码注释中提到的，**user_data** 字段是按原样从 SQE 传递到 CQE 实例的。假设您在提交队列中提交了一组请求，那么这些请求不一定以相同的顺序完成，
也不必以 CQEs 的身份出现在完成队列中。以下面的场景为例: 您的机器上有两个磁盘: 一个是慢速旋转的硬盘驱动器，另一个是超快的 SSD。
您在提交队列中提交2个请求。第一个在慢速旋转的硬盘上读取100kB 的文件，第二个在快速固态硬盘上读取同样大小的文件。
如果维护排序，即使来自 SSD 文件的数据会更早到达，内核是否应该等待来自旋转硬盘上的文件的数据变得可用？这是个不好的想法，
因为这会阻止我们以最快的速度运行。因此，当 CQEs 可用时，它们可以按任意顺序到达。无论哪个操作完成，其结果都会在 CQ 上公布。
由于 CQEs 的到达没有特定的顺序，现在你已经知道了从上面的 **io_uring_cqe** 结构中看到的 CQE 是什么样子的，你如何识别一个特定 CQE 对应的 SQE 请求？一种方法是使用 SQEs 和 CQEs 共有的 **user_data** 字段来标识完成情况。并不是说你要设置一个唯一的 ID 或者其他什么，而是你通常会传递一个指针。如果你对此感到困惑，那就等着看后面的例子吧。

完成队列条目很简单，因为它主要关注系统调用的返回值，这个返回值在 res 字段中返回。例如，如果您读取操作队列成功执行完成，那么它将包含读取的字节数。如果有错误，它将包含一个负的错误号。本质上，**[read(2)](http://man7.org/linux/man-pages/man2/read.2.html)**系统调用自身的内容将返回。

## 排序

虽然我提到了 CQEs 可以以任何顺序到达，但是您可以强制使用 SQE 排序对某些操作进行排序，实际上是将它们链接起来。有关更多细节，请参见[链接](https://unixism.net/loti/tutorial/link_liburing.html#link-liburing)请求教程。

## 提交队列条目

``` c
struct iovec {
     void  *iov_base;    /*开始地址 */
     size_t iov_len;     /*要传输的字节数 */
};
```
iovec 每个结构只指向一个缓冲区。基址和长度。

submission 队列条目比 completion 队列条目稍微复杂一些，因为它需要足够通用，以表示和处理目前 Linux 可能采用的各种 i/o操作：
```c
struct io_uring_sqe {
  __u8  opcode;   /* 指定操作类型 */
  __u8  flags;    /* IOSQE_ 标记 */
  __u16  ioprio;  /* ioprio 请求 */
  __s32  fd;      /* 要执行 IO 的文件描述符 */
  __u64  off;     /* 文件偏移 */
  __u64  addr;    /* 指向缓冲区或者iovecs */
  __u32  len;     /* 缓冲区大小或者iovecs 数量 */
  union {
    __kernel_rwf_t  rw_flags;
    __u32    fsync_flags;
    __u16    poll_events;
    __u32    sync_range_flags;
    __u32    msg_flags;
  };
  __u64  user_data;   /* 完成时传回的数据  */
  union {
    __u16  buf_index; /* 索引到固定缓冲区（如果使用） */
    __u64  __pad2[3];
  };
};
```

我知道这个结构看起来很繁杂。通常使用的字段只有少数，这很容易用一个简单的例子来解释，
比如我们正在处理的这个字段: cat。当你想使用 [readv(2)](http://man7.org/linux/man-pages/man2/readv.2.html)系统调用读取一个文件时:

- opcode 用于指定操作，在这个case 中， [readv(2)](http://man7.org/linux/man-pages/man2/readv.2.html) 使用的是 **IORING_OP_READV** 常量
- fd 用于指定表示要从中读取的文件的文件描述符
- addr 用于指向 iovec 结构的数组，这些结构包含我们为 i/o 分配的缓冲区的地址和长度。
- len  用于保存 iovec 结构的数组的长度。

这并不是很难，不是吗？你填写这些值，让 io_uring 知道该做什么。你可以将多个SQE 加入队列，最后当你想让内核开始处理你 SQE 队列的请求时，调用 **`[io_uring_enter()](https://unixism.net/loti/ref-iouring/io_uring_enter.html#c.io_uring_enter)`** 。



## 使用 io_uring 实现 cat 命令

让我们看看如何通过使用底层 **io_uring** 接口的来实现类似 cat 实用程序。


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
  __u16  ioprio;  // 请求ioprio
  __s32  fd;      // 执行IO操作的文件描述符
  __u64  off;     // 文件中的偏移量 
  __u64  addr;    // 指向缓冲区或iovecs的指针
  __u32  len;     // 缓冲区大小或iovecs数量
  union {
    __kernel_rwf_t  rw_flags;
    __u32    fsync_flags;
    __u16    poll_events;
    __u32    sync_range_flags;
    __u32    msg_flags;
  };
  __u64  user_data;   // 完成时要传回的数据
  union {
    __u16  buf_index; // 索引到固定缓冲区(如果使用)
    __u64  __pad2[3];
  };
};



struct io_uring_params {
  __u32 sq_entries;
  __u32 cq_entries;
  __u32 flags;
  __u32 sq_thread_cpu;
  __u32 sq_thread_idle;
  __u32 resv[5];
  struct io_sqring_offsets sq_off;
  struct io_cqring_offsets cq_off;
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
* 所以，我们推出了自己的系统调用封装函数。
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
* 返回传入的打开文件描述符的文件大小。也能正确处理常规文件和驱动设备。很好。
*
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
* 因为所有这些案例代码， 都在 io_uring的作者创建的liburing中，而且 liburing 相对容易使用。
* 但是，您应该花些时间来理解这段代码。
* 了解它的工作原理总是好的。出去好吹牛逼
* */

int app_setup_uring(struct submitter *s) {
    struct app_io_sq_ring *sring = &s->sq_ring;
    struct app_io_cq_ring *cring = &s->cq_ring;
    struct io_uring_params p;
    void *sq_ptr, *cq_ptr;

    /*
    * 我们需要将 io_uring_params 结构传递给的io_uring_setup（）调用并初始化为0。 
    * 如果需要，我们可以设置任何标志，但是对于本示例，我们不需要，因为这里只是做了简单的了解。
    * 
    * */
    memset(&p, 0, sizeof(p));

    // io_uring_setup 返回值将用于调用 mmap 将两个环缓冲区和一组提交队列映射到用户空间
    s->ring_fd = io_uring_setup(QUEUE_DEPTH, &p);
    if (s->ring_fd < 0) {
        perror("io_uring_setup");
        return 1;
    }

    /*
    * io_uring 通过两个 kernel-user 共享空间形成环形缓冲区通信。在最近的内核中，可以用 mmap() 调用来
    * 联合映射这个环形缓冲区。在直接操作完成队列时，提交队列有一个介于两者之间的间接数组。我们把它也映射进去。
    * */

    int sring_sz = p.sq_off.array + p.sq_entries * sizeof(unsigned);
    int cring_sz = p.cq_off.cqes + p.cq_entries * sizeof(struct io_uring_cqe);

    /* 
    * 在内核版本 5.4 及以上，可能使用一个 mmap()  来映射 completion 缓冲区和 submission缓冲区.
    * 如果去检测内核版本还不如直接使用 io_uring_params 结构的特征字段，这是一个位掩码。如果设置了 IORING_FEAT_SINGLE_MMAP 
    * 那么我们就可以不用再调用第二个mmap()来映射 CQ ring。
    * */
    if (p.features & IORING_FEAT_SINGLE_MMAP) {
        if (cring_sz > sring_sz) {
            sring_sz = cring_sz;
        }
        cring_sz = sring_sz;
    }

    /* 
    * submission 队列和 completion 队列可以映射到 ring 缓冲区，但是老版本的内核只能映射到队列中。
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
* 您可以提交多种类型的请求。我们的将是readv()请求，我们通过IORING_OP_READV来指定。
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
    * 用io_uring_enter()系统调用告诉内核我们已经提交了事件。
    * 我们还传递了IOURING_ENTER_GETEVENTS标志，
    * 它使io_uring_enter()调用等到min_complete事件（第3个参数）完成。
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

## 解释
让我们更深入地研究代码中特定的、重要的领域，看看这个示例程序是如何工作的。

## 初始化设置

在 **main()** 中，我们调用 **app_setup_uring()** ，它完成了我们使用 **io_uring** 所需的初始化工作。首先，我们调用 **io_uring_setup()** 系统调用，将我们需要的队列深度和结构 **io_uring_params** 的实例全部设置为0。当调用返回时，内核将填充这个结构成员中的值。这就是 **io_uring_params** 的样子。
```c
struct io_uring_params {
  __u32 sq_entries;
  __u32 cq_entries;
  __u32 flags;
  __u32 sq_thread_cpu;
  __u32 sq_thread_idle;
  __u32 resv[5];
  struct io_sqring_offsets sq_off;
  struct io_cqring_offsets cq_off;
};
```
在将这个结构作为 **`io_uring_setup ()`**系统调用的一部分传递之前，您唯一可以指定的是 flags 结构成员，但是在这个示例中，
我们不想传递任何 flag 。此外，在这个示例中，我们一个接一个地处理文件。我们不打算做任何并行 i/o，因为这是一个简单的例子，主要是为了了解 io_uring 的原始接口。为此，我们将队列深度设置为1。


来自 **`io_uring_setup ()`** 的返回值、文件描述符和 **io_uring_param** 结构中的其他字段随后将用于调用 [mmap(2)](http://man7.org/linux/man-pages/man2/mmap.2.html)以将两个环缓冲区和一组提交队列条目映射到用户空间。看看吧。我已经删除了一些周围的代码，以便将重点放在 [mmap(2)](http://man7.org/linux/man-pages/man2/mmap.2.html) 调用上。

```c
/* submission 和 completion 队列中映射环形缓冲区.
 * 不过旧的内核只在提交队列中映射。
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
    /* 在旧内核中单独映射完成队列环形缓冲区 */
    cq_ptr = mmap(0, cring_sz, PROT_READ | PROT_WRITE,
            MAP_SHARED | MAP_POPULATE,
            s->ring_fd, IORING_OFF_CQ_RING);
    if (cq_ptr == MAP_FAILED) {
        perror("mmap");
        return 1;
    }
}
/* 映射到提交队列条目数组中 */
s->sqes = mmap(0, p.sq_entries * sizeof(struct io_uring_sqe),
        PROT_READ | PROT_WRITE, MAP_SHARED | MAP_POPULATE,
        s->ring_fd, IORING_OFF_SQES);
```

我们将重要的细节保存在 **`app_io_sq_ring`** 和 **`app_io_cq_ring`** 中，以便以后参考。
当我们将两个环缓冲区分别映射为提交和完成时，您可能想知道第三个映射是用来做什么的。
完成队列环直接对 CQEs 的共享数组建立索引，而提交环在两者之间有一个间接数组。
提交端环形缓冲区是该数组的索引，该数组又包含 SQEs 中的索引。这对于将提交请求嵌入内部数据结构的某些应用程序非常有用。
这种设置允许他们一次提交多个提交条目，同时允许他们更容易地采用io_uring。

	# 注意
	在内核版本5.4及以上，单个 mmap (2)映射提交队列和完成队列。然而，在较老的内核中，
	它们需要单独映射。与检查内核版本不同，
	您可以通过检查 IORING_FEAT_SINGLE_MMAP  功能标志来检查内核使用一个 mmap (2)映射两个队列的能力，就像我们在上面的代码中所做的那样。

	# 参考
	- [io_uring_setup](https://unixism.net/loti/ref-iouring/io_uring_setup.html#io-uring-setup)

## 处理共享 ring 缓冲器

在常规编程中，我们习惯于处理用户空间和内核之间非常清晰的接口: 系统调用。然而，
系统调用确实有成本，对于像 **io_uring** 这样的高性能接口，希望尽可能去掉系统调用。
我们在前面看到，与通常的多个系统调用不同，使用 io_uring 
允许我们批处理多个 i/o 请求，并对 **`[io_uring_enter ()](https://unixism.net/loti/ref-iouring/io_uring_enter.html#c.io_uring_enter)`** 系统调用进行单个调用。或者在[轮询模式](https://unixism.net/loti/tutorial/sq_poll.html#sq-poll)下，甚至不需要调用。

当从用户空间读取或更新共享 ring 缓冲区时，需要注意一些事项，以确保在读取时看到最新的数据，
并在更新之后“刷新”或“同步”写操作，以便内核看到您的更新。这是因为 CPU 可以重新安排读写顺序，
编译器也可以。当读写操作发生在同一个 CPU 上时，这通常不是问题。但是在 **`io_uring`** 的情况下，
当一个共享缓冲区涉及到两个不同的上下文: 用户空间和内核，并且它们在上下文
切换之后可以在不同的 cpu 上运行。您需要确保从用户空间读取之前，先前的写操作是可见的。
或者，当您在 SQE 中填充细节并更新提交缓冲区的尾部时，您希望确保在更新环缓冲区尾部的写入之前，
对 SQE 成员进行的写入是有序的。如果这些写操作没有被排序，内核可能会看到尾部被更新，
但是当它读取 SQE 时，它可能无法在读取 SQE 时找到所需的所有数据。在轮询模式中，
内核正在查找对尾部的更改，这就成了一个真正的问题。
这都是因为 cpu 和编译器为了优化而对读写进行重新排序。

## 读取完成队列条目

一如既往，我们首先处理事情的完成方面，因为它比提交方面更简单。
这些解释甚至是必要的，因为我们需要讨论内存的顺序和我们需要如何处理它。
否则，我们只想看看如何处理环形缓冲区。对于完成事件，内核将 CQEs 添加到循环缓冲区并更新尾部，
而我们在用户空间中从头部读取。在任何环形缓冲区中，如果头部和尾部相等，则表示环形缓冲区为空。看看下面的代码:

```c
unsigned head;
head = cqring->head;
read_barrier(); /* 确保以前的写入可见 */
if (head != cqring->tail) {
    /* 环形缓冲区中有可用的数据 */
    struct io_uring_cqe *cqe;
    unsigned index;
    index = head & (cqring->mask);
    cqe = &cqring->cqes[index];
    /* 在此处完成cqe过程 */
     ...
    /* 我们现在已经消耗了这一项 */
    head++;
}
cqring->head = head;
write_barrier();
```

为了得到头部的索引，应用程序需要使用环形缓冲区的大小掩码来掩码头部。请记住，
上面代码中的任何一行都可能在上下文切换之后运行。因此，在比较之前，
我们有一个 **`read_barrier()`** ，这样，如果内核确实更新了尾部，
我们可以在 if 语句中将其作为比较的一部分来读取。一旦我们获得了 CQE 并处理它，
我们就更新 head，让内核知道我们已经使用了来自 ring 缓冲区的条目。最后一个 **`write_barrier()`**确保我们的写操作可见，这样内核就可以知道它。

## 提交 submission

做一个提交和读取一个完成是相反的。在处理 completion 时，内核将条目添加到尾部，
我们从循环缓冲区的头部读取条目，当 submission 时，我们添加到尾部，内核从循环缓冲区的头部读取条目。

```c
struct io_uring_sqe *sqe;
unsigned tail, index;
tail = sqring->tail;
index = tail & (*sqring->ring_mask);
sqe = &sqring->sqes[index];
/* 此函数调用填写此IO请求的SQE详细信息 */
app_init_io(sqe);
/* 将SQE索引填充到SQ环数组中 */
sqring->array[index] = index;
tail++;
write_barrier();
sqring->tail = tail;
write_barrier();
```

在上面的代码片段中，应用程序中的 **`app_init_io()`** 函数将填充提交请求的详细信息。在尾部更新之前，
我们有一个 **`write_barrier()`** 来确保前面的写操作是有序的。然后我们更新 tail 并再次调用 **`write_barrier()`** 以确保我们的更新被看到。我们在这里按照正确的顺序处理。

## 源代码
本文档中的代码和其他示例可以在这个 [Github](https://github.com/shuveb/loti-examples) 存储库中找到。


## 参考文档
- [The Low-level io_uring Interface](https://unixism.net/loti/low_level.html)
- [Efficient IO with io_uring PDF](https://kernel.dk/io_uring.pdf)
- [Efficient IO with io_uring github](https://github.com/axboe/liburing)