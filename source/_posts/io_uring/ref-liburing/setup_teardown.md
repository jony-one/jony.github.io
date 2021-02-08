---
title: 设置和删除
date: 2021-02-08 14:05:59
tags:
  - io_uring
author: Jony
---

本节将介绍一些函数，帮助您在程序中设置和删除io_uring。

*struct* **io_uring**

```c
struct io_uring {
    struct io_uring_sq sq;
    struct io_uring_cq cq;
    unsigned flags;
    int ring_fd;
};
```

TODO：下面两个结构真的是必需的吗?如果它们只在内部使用，请删除它们。

*struct* **io_uring_sq**

```c
struct io_uring_sq {
    unsigned *khead;
    unsigned *ktail;
    unsigned *kring_mask;
    unsigned *kring_entries;
    unsigned *kflags;
    unsigned *kdropped;
    unsigned *array;
    struct io_uring_sqe *sqes;

    unsigned sqe_head;
    unsigned sqe_tail;

    size_t ring_sz;
    void *ring_ptr;
};
```
***

*struct* **io_uring_cq**

```c
struct io_uring_cq {
    unsigned *khead;
    unsigned *ktail;
    unsigned *kring_mask;
    unsigned *kring_entries;
    unsigned *koverflow;
    struct io_uring_cqe *cqes;

    size_t ring_sz;
    void *ring_ptr;
};
```
***

int **io_uring_queue_init**(unsigned *entries*, struct [io_uring](https://unixism.net/loti/ref-liburing/setup_teardown.html#c.io_uring) \*ring, unsigned *flags*)
>	初始化 io_uring 以便在你的程序中使用. 在你使用 io_uring 做任何事情之前，你应该先调用这个函数。
>	**参数**
>	entries：您要为提交队列请求的条目数。每一个请求包含一个I/O操作的细节。
>	ring：指向将由内核填充的 [io_uring](https://unixism.net/loti/ref-liburing/setup_teardown.html#c.io_uring) 结构的指针。
>	*flags*： 您要传递的 flags 。有关详细信息，请参阅 [io_uring_setup](https://unixism.net/loti/ref-iouring/io_uring_setup.html#io-uring-setup)。

**返回值**:成功返回0，失败返回`-errono`。您可以使用[strerror(3)](http://man7.org/linux/man-pages/man3/strerror.3.html)来获得可读的失败原因版本。

	# 参考
	[cat 使用 liburing 实现的Demo](https://unixism.net/loti/tutorial/cat_liburing.html#eg-cat-uring)



-----------------------------------------

int **io_uring_queue_init_params**(unsigned entries, struct [io_uring](https://unixism.net/loti/ref-liburing/setup_teardown.html#c.io_uring) \*ring, struct io_uring_params \*p)
      在功能上等同于io_uring_queue_init()，但另外还带有指向io_uring_params结构的指针，允许您指定自己的io_uring_params结构。

      在 **io_uring_params** 结构中，您只能指定可以用于设置[各种 flags](https://unixism.net/loti/ref-iouring/io_uring_setup.html#io-uring-setup)和 `sq_thread_cpu` 和 `sq_thread_idle` 字段的 flags，这些字段用于设置CPU的亲和性和提交队列空闲时间。结构的其他字段在返回时由内核填充。当使用[io_uring_queue_init()](https://unixism.net/loti/ref-liburing/setup_teardown.html#c.io_uring_queue_init)时，不需要指定这些值。这个函数的存在为你解决了这个问题。


-----------------------------------------

int **io_uring_queue_mmap**(int *fd*, struct io_uring_params \*p, struct io_uring \*ring)

>	这是一个底层函数，只有当你想控制`io_uring`初始化的很多方面时才会用到。在调用这个函数之前，你应该已经调用了底层的[io_uring_setup()](https://unixism.net/loti/ref-iouring/io_uring_setup.html#c.io_uring_setup)。然后，你可以使用这个函数来为你[mmap(2)](http://man7.org/linux/man-pages/man2/mmap.2.html) 映射到 ring。
>
>	# 参数
>	- fd: [io_uring_setup()](https://unixism.net/loti/ref-iouring/io_uring_setup.html#c.io_uring_setup)返回的文件描述符
>   - p: 指向 io_uring_params 的指针
>   - ring: 指向 [io_uring](https://unixism.net/loti/ref-liburing/setup_teardown.html#c.io_uring) 的指针

	#  参考
	[底层 io_uring 接口使用](https://unixism.net/loti/low_level.html#low-level)

-----------------------------------------	

int **io_uring_ring_dontfork**(struct io_uring \*ring)
	如果你不想让你的进程的子进程继承环形映射，请使用此调用。

	# 参数
	- ring: 由**[io_uring_queue_init()](https://unixism.net/loti/ref-liburing/setup_teardown.html#c.io_uring_queue_init)**设置的 **[io_uring](https://unixism.net/loti/ref-liburing/setup_teardown.html#c.io_uring)** 结构

	返回值：成功返回0，失败返回-errono。您可以使用strerror(3)来获得人类可读的失败原因版本。

	# 参考
	[madvice(2)](http://man7.org/linux/man-pages/man2/madvice.2.html),尤其是MADV_DONTFORK。

void io_uring_queue_exit(struct io_uring \*ring)

	io_uring的删除函数。删除所有设置共享环缓冲区的映射，并关闭内核返回的低级io_uring文件描述符。

	# 参数
	- ring: 由io_uring_queue_init()设置的io_uring结构。










