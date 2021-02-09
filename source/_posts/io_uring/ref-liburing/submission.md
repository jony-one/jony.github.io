---
title: Submission
date: 2021-02-08 15:56:42
categories: ["Lord of the io_uring"]
tags:
  - io_uring

---

# 介绍

提交I/O请求的顺序通常是这样的:

```c
/* 获取一个 SQE */
struct io_uring_sqe *sqe = io_uring_get_sqe(ring);
/* 设置一个 readv 操作 */
io_uring_prep_readv(sqe, file_fd, fi->iovecs, blocks, 0);
/* 设置用户数据 */
io_uring_sqe_set_data(sqe, fi);
/* 最后提交请i去 */
io_uring_submit(ring);
```

上面的代码使用的是：[cat 使用 liburing 实现](https://unixism.net/loti/tutorial/cat_liburing.html#cat-liburing)

你调用 **io_ring_get_sqe()** 来获取一个提交队列条目或SQE，使用一个提交辅助器来处理你想要完成的I/O类型，比如 **io_uring_prep_readv()** 或 **io_uring_prep_accept()**。调用 **io_uring_set_sqe_data()** 以获取指向唯一标识该请求的数据结构的指针(在完成端获得相同的用户数据)，最后调用io_uring_submit()提交请求。

TODO：您还可以设置轮询以避免调用 **[io_uring_submit()](https://unixism.net/loti/ref-liburing/submission.html#c.io_uring_submit)** 系统调用。

***

struct io_uring_sqe **\*io_uring_get_sqe**(struct 【io_uring](https://unixism.net/loti/ref-liburing/setup_teardown.html#c.io_uring) \*ring)

>	这个函数返回一个提交队列条目，可以用来提交一个I/O操作。在调用[io_uring_submit()](https://unixism.net/loti/ref-liburing/submission.html#c.io_uring_submit)提交内核处理你的请求队列之前，你可以多次调用这个函数来提交I/O请求队列。
>
>	**参数**
>	- ring:  [io_uring_queue_init()](https://unixism.net/loti/ref-liburing/setup_teardown.html#c.io_uring_queue_init) 设置的 [uring](https://unixism.net/loti/ref-liburing/setup_teardown.html#c.io_uring) 结构
>
>	返回值： 一个指向 **io_uring_sqe** 的指针，表示一个空的SQE。如果提交队列已满，则返回NULL。
>
>	请看[提交介绍代码片段](https://unixism.net/loti/ref-liburing/submission.html#submission-intro-snippet)的使用实例。
>
>	# 参考
>	- [io_uring 底层接口编程](https://unixism.net/loti/low_level.html#low-level)
>	- [cp 使用 liburing 实现](https://unixism.net/loti/tutorial/cp_liburing.html#cp-liburing)

***

void **io_uring_sqe_set_data**(*struct* io_uring_sqe \*sqe, void \*data)
	这是一个内联方便函数，用于设置传入的SQE实例的用户数据字段。

>	**参数**
>	- SQE：要为其设置用户数据的SQE实例。
>	- data：一个指向用户数据的指针

***	

void **io_uring_sqe_set_flags**(*struct* io_uring_sqe \*sqe, unsigned flags)
	这是一个内联方便函数，用于设置传入的SQE实例的 flags 字段。

>	**参数**
>	- sqe: 要为其设置用户数据的SQE实例。
>	- flags:你要设置的标志。这是个 bitmap 字段，请参见[io_uring_enter](https://unixism.net/loti/ref-iouring/io_uring_enter.html#io-uring-enter)参考页面，了解各种SQE标志及其含义。

***

int **io_uring_submit**(struct io_uring \*ring)
	将通过io_uring_get_sqe()获取的SQE提交给内核。当你多次调用io_uring_get_sqe()来设置多个I/O请求后，你可以调用一次。

>	**参数**
>
>	- ring：[io_uring_queue_init()](https://unixism.net/loti/ref-liburing/setup_teardown.html#c.io_uring_queue_init) 设置的 [uring](https://unixism.net/loti/ref-liburing/setup_teardown.html#c.io_uring) 结构
>
>	**返回值**：返回提交的sqe数量。
>
>	# 参考
>	- [io_uring 底层接口编程](https://unixism.net/loti/low_level.html#low-level)
>	- [cp 使用 liburing 实现](https://unixism.net/loti/tutorial/cp_liburing.html#cp-liburing)

***

int **io_uring_submit_and_wait**(struct io_uring \*ring, unsigned wait_nr)	
	和**io_uring_submit()**一样，但是需要一个额外的参数wait_nr，让你指定要等待多少个完成。这个调用将阻塞，直到内核处理wait_nr个提交请求，并将它们的详细信息放入完成队列。

>	**参数**
>	- wait_nr： 等待完成的数量。
>	**返回值**：返回提交的sqe数量。

# Submission 辅助器

提交辅助器是方便的函数，它可以轻松地指定你想通过SQE请求的I/O操作。每个支持的I/O类型都有一个函数。
关于**io_uring_prep_readv()**函数的使用实例，请参见[提交介绍代码](https://unixism.net/loti/ref-liburing/submission.html#submission-intro-snippet)片段。

void **io_uring_prep_nop**(struct io_uring_sqe \*sqe)
	此函数用于设置SQE通过读取操作指向的提交队列条目。

>	**参数**
>
>	- sqe：SQE的指针，通常由[io_uring_get_sqe()](https://unixism.net/loti/ref-liburing/submission.html#c.io_uring_get_sqe)返回。
>	- fd：要读取的文件描述符
>	- buf：用于将读取数据复制到其中的缓冲区
>	- nbytes：要读取的字节数
>	- offset：要读取的文件的绝对偏移量
>
>	# 参考
>	- [read(2)](http://man7.org/linux/man-pages/man2/read.2.html)
>	- [lseek(2)](http://man7.org/linux/man-pages/man2/lseek.2.html)

***

void **io_uring_prep_write**(struct io_uring_sqe \*sqe, int fd, const void \*buf, unsigned nbytes, off_t offset)
	这个函数通过写操作设置sqe所指向的提交队列条目。

>	**参数**
>	- sqe：SQE的指针，通常由[io_uring_get_sqe()](https://unixism.net/loti/ref-liburing/submission.html#c.io_uring_get_sqe)返回。
>	- fd：要读取的文件描述符
>	- buf：用于将读取数据复制到其中的缓冲区
>	- nbytes：要读取的字节数
>	- offset：要读取的文件的绝对偏移量	
>
>	# 参考
>	- [write(2)](http://man7.org/linux/man-pages/man2/write.2.html)
>	- [lseek(2)](http://man7.org/linux/man-pages/man2/lseek.2.html)

***

void **io_uring_prep_readv**(struct io_uring_sqe \*sqe, int fd, const struct iovec \*iovecs, unsigned nr_vecs, off_t offset)
	这个函数用 “scatter” 读操作设置sqe指向的提交队列条目，很像[readv(2)](http://man7.org/linux/man-pages/man2/readv.2.html)或[preadv(2)](http://man7.org/linux/man-pages/man2/preadv.2.html)，它们是Linux的 scatter/gather I/O 系列系统调用的一部分。

>	**参数**
>	- sqe：SQE的指针，通常由[io_uring_get_sqe()](https://unixism.net/loti/ref-liburing/submission.html#c.io_uring_get_sqe)返回。
>	- fd：要读取的文件描述符
>	- iovecs：指向iovec结构数组的指针
>	- nr_vecs：由iovecs参数指向的数组中iovec实例的数目
>	- offset：要读取的文件的绝对偏移量	
>
>	# 参考
>	- [readv(2)](http://man7.org/linux/man-pages/man2/readv.2.html)
>	- [使用liburing实现cat程序](https://unixism.net/loti/tutorial/cat_liburing.html#cat-liburing)示例使用了这个函数

***

void **io_uring_prep_read_fixed**(struct io_uring_sqe \*sqe, int fd, void \*buf, unsigned nbytes, off_t offset, int buf_index)
	与io_uring_prep_read()非常类似，该函数通过读取操作设置SQE指向的提交队列条目。主要区别在于，这个函数被设计用来处理通过io_uring_register()注册的一组固定的预分配缓冲区。

>	**参数**
>	- sqe：SQE的指针，通常由[io_uring_get_sqe()](https://unixism.net/loti/ref-liburing/submission.html#c.io_uring_get_sqe)返回。
>	- fd：要读取的文件描述符
>	- buf：用于将读取数据复制到其中的缓冲区
>	- nbytes：要读取的字节数
>	- offset：要读取的文件的绝对偏移量	
>	- buf_index：要使用的预分配缓冲区集的索引。
>
>	# 参考
>	- [io_uring_register()](https://unixism.net/loti/ref-iouring/io_uring_register.html#c.io_uring_register)
>	- [read(2)](http://man7.org/linux/man-pages/man2/read.2.html)
>	- [lseek(2)](http://man7.org/linux/man-pages/man2/lseek.2.html)

***

void **io_uring_prep_writev**(struct io_uring_sqe \*sqe, int fd, const struct iovec \*iovecs, unsigned nr_vecs, off_t offset)
	这个函数使用“gather”写操作来设置sqe所指向的提交队列条目，类似于[writev(2)](http://man7.org/linux/man-pages/man2/writev.2.html)或[pwritev(2)](http://man7.org/linux/man-pages/man2/pwritev.2.html)，它们是Linux的 scatter/gather I/O 系列系统调用的一部分。

>	**参数**
>	- sqe：SQE的指针，通常由[io_uring_get_sqe()](https://unixism.net/loti/ref-liburing/submission.html#c.io_uring_get_sqe)返回。
>	- fd：要写入的文件描述符
>	- iovecs：指向iovec结构数组的指针
>	- nr_vecs：由iovecs参数指向的数组中iovec实例的数目
>	- offset：要读取的文件的绝对偏移量		
>
>	# 参考
>	- [writev(2)](http://man7.org/linux/man-pages/man2/writev.2.html)
>	- [使用liburing实现cat程序](https://unixism.net/loti/tutorial/cat_liburing.html#cat-liburing)示例使用了这个函数

***

void **io_uring_prep_write_fixed**(struct io_uring_sqe \*sqe, int fd, const void \*buf, unsigned nbytes, off_t offset, int buf_index)	
	TODO：补丁缓冲区案例添加

>	与[io_uring_prep_read()](https://unixism.net/loti/ref-liburing/submission.html#c.io_uring_prep_read)非常类似，这个函数通过一个read操作设置sqe所指向的提交队列条目。主要区别在于，这个函数被设计用来处理通过[io_uring_register()](https://unixism.net/loti/ref-iouring/io_uring_register.html#c.io_uring_register)注册的一组固定的预分配缓冲区。
>
>	**参数**
>	- sqe：SQE的指针，通常由[io_uring_get_sqe()](https://unixism.net/loti/ref-liburing/submission.html#c.io_uring_get_sqe)返回。
>	- fd：要写入的文件描述符
>	- buf：用于将读取数据复制到其中的缓冲区
>	- nbytes：要读取的字节数
>	- offset：要读取的文件的绝对偏移量	
>	- buf_index：要使用的预分配缓冲区集的索引。
>
>	# 参考
>	- [io_uring_register()](https://unixism.net/loti/ref-iouring/io_uring_register.html#c.io_uring_register)
>	- [read(2)](http://man7.org/linux/man-pages/man2/read.2.html)
>	- [lseek(2)](http://man7.org/linux/man-pages/man2/lseek.2.html)

***

void **io_uring_prep_fsync**(struct io_uring_sqe \*sqe, int fd, unsigned fsync_flags)
	此函数使用类似fsync(2)的操作设置`SQE`指向的提交队列条目。这会导致磁盘缓存中文件数据和任何缓冲区“脏”数据都同步到磁盘。
	
>	# 注意
>		请务必注意，将此操作排队并不能保证在此操作之前排队的任何写入操作都会将它们写入文件的数据同步到磁盘。这是因为提交队列中的操作可以由内核并行获取和执行。此同步操作可以在其前面排队的其他写入操作之前完成。它真正起到的作用是，在执行此操作时，文件的任何现有“脏”缓冲区数据都会同步到磁盘。
>
>	**参数**
>	- sqe：SQE的指针，通常由[io_uring_get_sqe()](https://unixism.net/loti/ref-liburing/submission.html#c.io_uring_get_sqe)返回。
>	- fd：要读取的文件描述符
>	- fsync_flags: 这个值可以是0，也可以是 `IORING_FSYNC_DATASYNC`，这使得它像[fdatasync(2)](http://man7.org/linux/man-pages/man2/fdatasync.2.html)一样。
>	
>	# 参考
>	- [io_uring_register()](https://unixism.net/loti/ref-iouring/io_uring_register.html#c.io_uring_register)
>	- [read(2)](http://man7.org/linux/man-pages/man2/read.2.html)
>	- [lseek(2)](http://man7.org/linux/man-pages/man2/lseek.2.html)

void **io_uring_prep_close**(struct io_uring_sqe \*sqe, int fd)
	这个函数使用类似close(2)的操作设置sqe所指向的提交队列条目。这将导致fd所指向的文件描述符被关闭。

>	参数：
>	- sqe：SQE的指针，通常由[io_uring_get_sqe()](https://unixism.net/loti/ref-liburing/submission.html#c.io_uring_get_sqe)返回。
>	- fd：要读取的文件描述符
>	# 参考
>	- [close(2)](http://man7.org/linux/man-pages/man2/close.2.html)

***

void **io_uring_prep_openat**(struct io_uring_sqe \*sqe, int dfd, const char \*path, int flags, mode_t mode)
	此函数使用类似openat(2)的操作设置SQE指向的提交队列条目。这会导致PATH指向的文件在相对于由DFD目录文件描述符表示的目录的路径中打开。

>	**参数**
>	- sqe：SQE的指针，通常由[io_uring_get_sqe()](https://unixism.net/loti/ref-liburing/submission.html#c.io_uring_get_sqe)返回。
>	- dfd: 目录文件描述符，代表要打开文件的相对目录。
>	- path: 要打开的文件的路径名
>	- flags: 标志。这些是访问模式的标志。与[open(2)](http://man7.org/linux/man-pages/man2/open.2.html)相同
>	- mode: 模式。创建新文件时应用的文件权限位。与 [open(2)](http://man7.org/linux/man-pages/man2/open.2.html) 中的相同
>
>	# 参考
>	- [openat(2)](http://man7.org/linux/man-pages/man2/openat.2.html)
>	- [open(2)](http://man7.org/linux/man-pages/man2/open.2.html)

***

void **io_uring_prep_openat2**(struct io_uring_sqe \*sqe, int dfd, const char \*path, struct open_how \*how)
	这个函数用类似[openat2(2)](http://man7.org/linux/man-pages/man2/openat2.2.html)的操作来设置sqe所指向的提交队列条目。这将导致path指向的文件在相对于dfd目录文件描述符所代表的目录的路径中被打开。

>	**参数**
>	- sqe：SQE的指针，通常由[io_uring_get_sqe()](https://unixism.net/loti/ref-liburing/submission.html#c.io_uring_get_sqe)返回。
>	- dfd: 目录文件描述符，代表要打开文件的相对目录。
>	- path: 要打开的文件的路径名
>	- flags: 标志。这些是访问模式的标志。与[open(2)](http://man7.org/linux/man-pages/man2/open.2.html)相同
>	- how: 一个指向`open_how`结构的指针，该结构可以让你控制打开文件的方式。参见[openat2(2)](http://man7.org/linux/man-pages/man2/openat2.2.html)了解更多细节。
>
>	# 参考
>	- [openat2(2)](http://man7.org/linux/man-pages/man2/openat2.2.html)
>	- [open(2)](http://man7.org/linux/man-pages/man2/open.2.html)

***

void **io_uring_prep_fallocate**(struct io_uring_sqe \*sqe, int fd, int mode, off_t offset, off_t len)		
	这个函数使用类似[fallocate(2)](http://man7.org/linux/man-pages/man2/fallocate.2.html)的操作设置sqe所指向的提交队列条目。[fallocate(2)](http://man7.org/linux/man-pages/man2/fallocate.2.html)系统调用用于为文件描述符fd表示的文件分配、释放、折叠、置零或增加文件空间。请参阅[fallocate(2)](http://man7.org/linux/man-pages/man2/fallocate.2.html)了解更多细节。

>	**参数**
>	- sqe：SQE的指针，通常由[io_uring_get_sqe()](https://unixism.net/loti/ref-liburing/submission.html#c.io_uring_get_sqe)返回。
>	- fd：要读取的文件描述符
>	- mode:描述了对文件进行的操作。详情请参见fallocate(2)。
>	- offset：要读取的文件的绝对偏移量	
>	- len：操作长度
>
>	# 参考
>	- [fallocate(2)](http://man7.org/linux/man-pages/man2/fallocate.2.html)


***

void **io_uring_prep_statx**(struct io_uring_sqe \*sqe, int dfd, const char \*path, int flags, unsigned mask, struct statx \*statxbuf)
	这个函数用类似 statx(2) 的操作设置了 sqe 所指向的提交队列条目。statx(2) 系统调用会获取 path 指向的文件的元信息，这些元信息会被填入 statxbuf 指向的 statx 结构中。详见 statx(2) 。

>	**参数**
>	- sqe：SQE的指针，通常由[io_uring_get_sqe()](https://unixism.net/loti/ref-liburing/submission.html#c.io_uring_get_sqe)返回。
>	- dfd: 根据this和path的值，path所指向的文件可以被解释为绝对路径文件，相对于process或相对于dfd中的目录描述符引用的目录，以及其他类型的解释都是可能的。详见 [statx(2)。](http://man7.org/linux/man-pages/man2/statx.2.html)
>	- path: 文件路径。结合dfd中的值进行解释。详见statx（2）
>	- flags：这用来影响路径名的查询方式，也可以影响内核在查询远程文件系统上的文件时做什么样的同步。详见 [statx(2) 。](http://man7.org/linux/man-pages/man2/statx.2.html)
>
>	# 参考
>	- [statx(2)](http://man7.org/linux/man-pages/man2/statx.2.html)

***

void **io_uring_prep_fadvise**(struct io_uring_sqe \*sqe, int fd, off_t offset, off_t len, int advice)
	此函数使用类似[posix_fadvise(2)](http://man7.org/linux/man-pages/man2/posix_fadvise.2.html)的操作用于设置SQE指向的提交队列条目。[posix_fadvise(2)](http://man7.org/linux/man-pages/man2/posix_fadvise.2.html)系统调用允许应用程序通知操作系统它计划如何访问由文件描述符FD表示的文件中的数据-顺序、随机或其他方式。这是为了提高应用程序的性能。有关更多详细信息，请参见[posix_fadvise(2)](http://man7.org/linux/man-pages/man2/posix_fadvise.2.html)。

>	**参数**
>	- sqe：SQE的指针，通常由[io_uring_get_sqe()](https://unixism.net/loti/ref-liburing/submission.html#c.io_uring_get_sqe)返回。
>	- fd：要提供 advise 文件的文件描述符
>	- offset：应用 advise 文件的偏移量
>	- len：advise 文件的长度
>
>	# 参考
>	- [posix_fadvise(2)](http://man7.org/linux/man-pages/man2/posix_fadvise.2.html)

***

void **io_uring_prep_madvise**(struct io_uring_sqe \*sqe, void \*addr, off_t length, int advice)
	这个函数使用类似于[madvise(2)](http://man7.org/linux/man-pages/man2/madvise.2.html)的操作设置sqe所指向的提交队列条目。[madvise(2)](http://man7.org/linux/man-pages/man2/madvise.2.html)系统调用让应用程序就addr所指向的内存(长度为字节)通知操作系统。建议包括:应用程序计划如何访问该范围内的内存(顺序、随机或其他方式)，或者当进程产生子进程时，操作系统是否不应该共享该范围内的内存等等。这样做的目的是提高应用程序的性能。详情请参见mdavise(2)。

>	**参数**
>	- sqe：SQE的指针，通常由[io_uring_get_sqe()](https://unixism.net/loti/ref-liburing/submission.html#c.io_uring_get_sqe)返回。
>	- addr：advice应用的内存范围的起始地址。
>	- len： advice 的长度
>
>	# 参考
>	- [madvise(2)](http://man7.org/linux/man-pages/man2/madvise.2.html)

***

void **io_uring_prep_splice**(struct io_uring_sqe \*sqe, int fd_in, loff_t off_in, int fd_out, loff_t off_out, unsigned int nbytes, unsigned int splice_flags)
	这个函数用类似[splice(2)的操作来设置`sqe`所指向的提交队列条目。[splice(2)系统调用在两个文件描述符(`fd_in`和`fd_out`)]()之间复制数据，而不在内核地址空间和用户地址空间之间复制数据。然而，其中一个文件描述符必须代表一个管道。详见 [splice(2) 。]()

>	**参数**
>	- sqe：SQE的指针，通常由[io_uring_get_sqe()](https://unixism.net/loti/ref-liburing/submission.html#c.io_uring_get_sqe)返回。
>	- `fd_in`：要读取文件的描述符
>	- `off_in`：如果`fd_in`是指一个管道，则必须为NULL。如果`fd_in`不是一个管道，且：
>
>		- `off_in`为NULL，则从`fd_in`的文件偏移量中读取数据，并相应调整文件偏移量。
>		- `off_in`是非NULL，那么`off_in`必须指向一个缓冲区，这个缓冲区指定了从`fd_in`读取字节的起始偏移量。在这种情况下，`fd_in`的文件偏移量是不变的。
>
>	- `fd_out` 和 `off_out`： 类似于`fd_in`和`off_in`的语句适用于这些参数。
>	- nbytes：要 copy 的字节数量
>	- flags： 影响复制的位掩码。详见 [splice(2)。]()
>
>	# 参考
>	- [splice(2)](http://man7.org/linux/man-pages/man2/splice.2.html)

***

void **io_uring_prep_recvmsg**(struct io_uring_sqe \*sqe, int fd, struct msghdr \*msg, unsigned flags)
	这个函数用类似splice(2)的操作来设置sqe所指向的提交队列条目。splice(2)系统调用在两个文件描述符(fd_in和fd_out)之间复制数据，而不在内核地址空间和用户地址空间之间复制数据。然而，其中一个文件描述符必须代表一个管道。详见 splice(2) 。

>	**参数**
>	- sqe：SQE的指针，通常由[io_uring_get_sqe()](https://unixism.net/loti/ref-liburing/submission.html#c.io_uring_get_sqe)返回。
>	- fd:要读取的socket。
>	- msg:指向msghdr结构体的指针。
>	- flag:影响读取的位掩码。详见recvmsg(2)。
>
>	# 参考
>	- recvmsg(2)

***

void **io_uring_prep_sendmsg**(struct io_uring_sqe \*sqe, int fd, const struct msghdr \*msg, unsigned flags)
	与io_uring_prep_recvmsg()相同，但用于写入套接字。

>	# 参考
>	- [sendmsg(2)](http://man7.org/linux/man-pages/man2/sendmsg.2.html)

***

void **io_uring_prep_recv**(struct io_uring_sqe \*sqe, int sockfd, void \*buf, size_t len, int flags)
	这个函数使用类似[recv(2)](http://man7.org/linux/man-pages/man2/recv.2.html)的操作设置sqe所指向的提交队列条目。[recv(2)](http://man7.org/linux/man-pages/man2/recv.2.html)系统调用用于从套接字读取数据。它使用`msghdr`结构来减少参数的数量。此调用同时适用于面向连接(如TCP)和无连接(如UDP)套接字。如果没有`flags`参数，它完全等同于read(2)，除了在处理长度为零数据报时有一个小区别。请参阅[recv(2)](http://man7.org/linux/man-pages/man2/recv.2.html)了解更多细节。

>	**参数**
>	- sqe：SQE的指针，通常由[io_uring_get_sqe()](https://unixism.net/loti/ref-liburing/submission.html#c.io_uring_get_sqe)返回。
>	- fd:要读取的socket。
>	- buf：指向读取数据的缓冲区的指针
>	- len：要读取的字节数
>	- flags：影响读取的位掩码。请参见 [recv(2)](http://man7.org/linux/man-pages/man2/recv.2.html)
>
>	# 参考
>	- [recv(2)](http://man7.org/linux/man-pages/man2/recv.2.html)

***

void io_uring_prep_send(struct io_uring_sqe \*sqe, int sockfd, const void \*buf, size_t len, int flags)	
	与[io_uring_prep_recv()](https://unixism.net/loti/ref-liburing/submission.html#c.io_uring_prep_recv)相同，但用于写入套接字。

>	# 参考
>	- [send(2)](http://man7.org/linux/man-pages/man2/send.2.html)

***

void **io_uring_prep_accept**(struct io_uring_sqe \*sqe, int fd, struct sockaddr \*addr, socklen_t \*addrlen, int flags)
	这个函数使用类似于[accept4(2)的操作设置sqe所指向的提交队列条目。[accept4(2)系统调用与面向连接的套接字类型(SOCK_STREAM, SOCK_SEQPACKE](http://man7.org/linux/man-pages/man2/accept4.2.html)T)一起使用。它为侦听套接字fd提取挂起连接队列上的第一个连接请求。当flags参数设置为0时，[accept4(2)就相当于accept(2)。请参阅[accept4(2)了解更多细](http://man7.org/linux/man-pages/man2/accept4.2.html)节。

>	# 参数
>	- sqe：SQE的指针，通常由[io_uring_get_sqe()](https://unixism.net/loti/ref-liburing/submission.html#c.io_uring_get_sqe)返回。
>	- fd:要监听的socket。
>	- addr：指向sockaddr结构的指针。这将由对等方的地址填充
>	- addrlen：socklen_t指针。value-result参数，必须用调用的sockaddr结构的大小填充，并将其设置为对等地址的大小。
>	- flags：影响系统调用的位掩码。详见 [accept4(2)](http://man7.org/linux/man-pages/man2/accept4.2.html) 。
>
>	# 参考
>	-  [accept4(2)](http://man7.org/linux/man-pages/man2/accept4.2.html)

***

void **io_uring_prep_connect**(struct io_uring_sqe \*sqe, int fd, struct sockaddr \*addr, socklen_t addrlen)	
	这个函数通过类似[connect(2)](http://man7.org/linux/man-pages/man2/connect.2.html)的操作设置sqe所指向的提交队列条目。[connect(2)](http://man7.org/linux/man-pages/man2/connect.2.html)系统调用用于将fd中引用的套接字连接到addr中指定的地址。请参阅[connect(2)](http://man7.org/linux/man-pages/man2/connect.2.html)了解更多细节。

>	**参数**
>	- sqe：SQE的指针，通常由[io_uring_get_sqe()](https://unixism.net/loti/ref-liburing/submission.html#c.io_uring_get_sqe)返回。
>	- fd:要监听的socket。
>	- addr：指向sockaddr结构的指针。这将由对等方的地址填充
>	- addrlen：socklen_t指针。value-result参数，必须用调用的sockaddr结构的大小填充，并将其设置为对等地址的大小。
>
>	# 参考
>	- [connect(2)](http://man7.org/linux/man-pages/man2/connect.2.html)

***

void **io_uring_prep_epoll_ctl**(struct io_uring_sqe \*sqe, int epfd, int fd, int op, struct epoll_event \*ev)	
	这个函数使用类似于[epoll_ctl(2)](http://man7.org/linux/man-pages/man2/epoll_ctl.2.html)的操作设置sqe所指向的提交队列条目。[epoll_ctl(2)](http://man7.org/linux/man-pages/man2/epoll_ctl.2.html)系统调用用于在epfd引用的[epoll(7)](http://man7.org/linux/man-pages/man7/epoll.7.html)实例的列表中添加或删除修改条目。op指定的添加、删除或修改操作应用于文件描述符fd。请参阅[epoll_ctl(2)](http://man7.org/linux/man-pages/man2/epoll_ctl.2.html)了解更多细节。

>	**参数**
>	- sqe：SQE的指针，通常由[io_uring_get_sqe()](https://unixism.net/loti/ref-liburing/submission.html#c.io_uring_get_sqe)返回。
>	- epfd：文件描述符表和epoll实例。
>	- fd:要添加、删除或修改的文件描述符。
>	- op: 要执行的epoll操作(EPOLL_CTL_ADD、EPOLL_CTL_DEL或EPOLL_CTL_MOD)
>	- ev: 指向epoll_event结构的指针
>
>	# 参考
>	- [epoll_ctl(2)](http://man7.org/linux/man-pages/man2/epoll_ctl.2.html)
>	- [epoll(7)](http://man7.org/linux/man-pages/man7/epoll.7.html)

***

void **io_uring_prep_poll_add**(struct io_uring_sqe \*sqe, int fd, short poll_mask)
	这个函数使用类似 [poll(2)](http://man7.org/linux/man-pages/man2/poll.2.html) 操作设置sqe所指向的提交队列条目，将文件描述符添加到poll的列表，并侦听poll_mask中指定的事件。与不带EPOLLONESHOT的poll或epoll不同，此接口始终以单次模式工作。也就是说，一旦轮询操作完成，就必须重新提交它。参见[poll(2)](http://man7.org/linux/man-pages/man2/poll.2.html)了解更多细节。

>	**参数**
>	- sqe：SQE的指针，通常由[io_uring_get_sqe()](https://unixism.net/loti/ref-liburing/submission.html#c.io_uring_get_sqe)返回。
>	- fd:用于轮询事件的文件描述符。
>	- poll_mask: 包含要侦听的事件的位掩码
>
>	# 参考
>	- 	[poll(2)](http://man7.org/linux/man-pages/man2/poll.2.html)

***

void **io_uring_prep_poll_remove**(struct io_uring_sqe \*sqe, void \*user_data)
	从通过[poll(2)](http://man7.org/linux/man-pages/man2/poll.2.html)监视请求中删除。

>	**参数**
>	- sqe：SQE的指针，通常由[io_uring_get_sqe()](https://unixism.net/loti/ref-liburing/submission.html#c.io_uring_get_sqe)返回。
>	- user_data:用户数据指针。与此用户数据关联的请求将从进一步的监视中删除。
>
>	# 参考
>	- [poll(2)](http://man7.org/linux/man-pages/man2/poll.2.html)



















