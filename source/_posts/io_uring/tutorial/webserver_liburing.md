---
title: liburing 实现 web 服务器
date: 2021-02-07 16:28:41
categories: ["Lord of the io_uring"]
tags:
  - io_uring

---

# liburing 实现 web 服务器

我们在[介绍](https://unixism.net/loti/async_intro.html#async-intro)中讨论过，因为 [select(2)](http://man7.org/linux/man-pages/man2/select.2.html)、 [poll(2)](http://man7.org/linux/man-pages/man2/poll.2.html)和 [epoll(7)](http://man7.org/linux/man-pages/man7/epoll.7.html)报告对本地/常规文件的操作始终处于就绪状态，
所以像 libuv (这个 NodeJS 底层实现)这样的库使用单独的线程池来处理文件 i/o。
 `io_uring` 的一个巨大优势是，它为多种类型的I/O提供了一个单一的、干净的统一的、最重要的是高效的接口。

在这个示例中，我们将研究一个额外的操作 accept()以及如何使用 io_uring 来实现它。加上 readv()和 writev()的操作，
您就有能力编写一个简单的 web 服务器了！这个web服务器是基于我为ZeroHTTPd写的代码，
这个程序的特点是在我写的一系列文章中探索各种 Linux 进程模型以及它们相互之间的性能比较。
已经重写了 ZeroHTTPd 来专门使用 io/uring 接口。

下面是通过 ZeroHTTPd 提供的index页面:

![index页面](/jony.github.io/images/ZeroHTTPd_static.png "index 页面")


现在让我们进入代码。
```c
#include <stdio.h>
#include <netinet/in.h>
#include <string.h>
#include <ctype.h>
#include <unistd.h>
#include <stdlib.h>
#include <signal.h>
#include <liburing.h>
#include <sys/stat.h>
#include <fcntl.h>

#define SERVER_STRING "Server: zerohttpd/0.1\r\n"
#define DEFAULT_SERVER_PORT 8000
#define QUEUE_DEPTH 256
#define READ_SZ 8192

#define EVENT_TYPE_ACCEPT 0
#define EVENT_TYPE_READ 1
#define EVENT_TYPE_WRITE 2

struct request
{
    int event_type;
    int iovec_count;
    int client_socket;
    struct iovec iov[];
};

struct io_uring ring;

const char *unimplemented_content =
    "HTTP/1.0 400 Bad Request\r\n"
    "Content-type: text/html\r\n"
    "\r\n"
    "<html>"
    "<head>"
    "<title>ZeroHTTPd: Unimplemented</title>"
    "</head>"
    "<body>"
    "<h1>Bad Request (Unimplemented)</h1>"
    "<p>Your client sent a request ZeroHTTPd did not understand and it is probably not your fault.</p>"
    "</body>"
    "</html>";

const char *http_404_content =
    "HTTP/1.0 404 Not Found\r\n"
    "Content-type: text/html\r\n"
    "\r\n"
    "<html>"
    "<head>"
    "<title>ZeroHTTPd: Not Found</title>"
    "</head>"
    "<body>"
    "<h1>Not Found (404)</h1>"
    "<p>Your client is asking for an object that was not found on this server.</p>"
    "</body>"
    "</html>";

/*
 * 用于将字符串转换为小写的实用函数。
 * */

void strtolower(char *str)
{
    for (; *str; ++str)
        *str = (char)tolower(*str);
}
/*
 一个打印系统调用和错误详情的函数
 然后以错误代码1退出。非零的意思是事情不顺利。
 */
void fatal_error(const char *syscall)
{
    perror(syscall);
    exit(1);
}

/*
 * 为使代码看起来更干净的辅助功能。
 * */

void *zh_malloc(size_t size)
{
    void *buf = malloc(size);
    if (!buf)
    {
        fprintf(stderr, "Fatal error: unable to allocate memory.\n");
        exit(1);
    }
    return buf;
}

/*
 * 该函数负责设置web服务器使用的主监听套接字。
 * */

int setup_listening_socket(int port)
{
    int sock;
    struct sockaddr_in srv_addr;

    sock = socket(PF_INET, SOCK_STREAM, 0);
    if (sock == -1)
        fatal_error("socket()");

    int enable = 1;
    if (setsockopt(sock,
                   SOL_SOCKET, SO_REUSEADDR,
                   &enable, sizeof(int)) < 0)
        fatal_error("setsockopt(SO_REUSEADDR)");

    memset(&srv_addr, 0, sizeof(srv_addr));
    srv_addr.sin_family = AF_INET;
    srv_addr.sin_port = htons(port);
    srv_addr.sin_addr.s_addr = htonl(INADDR_ANY);

    /* 我们绑定到一个端口，将这个套接字变成一个监听套接字。
                 * */
    if (bind(sock,
             (const struct sockaddr *)&srv_addr,
             sizeof(srv_addr)) < 0)
        fatal_error("bind()");

    if (listen(sock, 10) < 0)
        fatal_error("listen()");

    return (sock);
}

int add_accept_request(int server_socket, struct sockaddr_in *client_addr, socklen_t *client_addr_len)
{
    struct io_uring_sqe *sqe = io_uring_get_sqe(&ring);
    io_uring_prep_accept(sqe, server_socket, (struct sockaddr *)client_addr, client_addr_len, 0);
    struct request *req = malloc(sizeof(*req));
    req->event_type = EVENT_TYPE_ACCEPT;
    io_uring_sqe_set_data(sqe, req);
    io_uring_submit(&ring);

    return 0;
}

int add_read_request(int client_socket)
{
    struct io_uring_sqe *sqe = io_uring_get_sqe(&ring);
    struct request *req = malloc(sizeof(*req) + sizeof(struct iovec));
    req->iov[0].iov_base = malloc(READ_SZ);
    req->iov[0].iov_len = READ_SZ;
    req->event_type = EVENT_TYPE_READ;
    req->client_socket = client_socket;
    memset(req->iov[0].iov_base, 0, READ_SZ);
    /* Linux内核5.5支持readv，但不支持recv()或read() */
    io_uring_prep_readv(sqe, client_socket, &req->iov[0], 1, 0);
    io_uring_sqe_set_data(sqe, req);
    io_uring_submit(&ring);
    return 0;
}

int add_write_request(struct request *req)
{
    struct io_uring_sqe *sqe = io_uring_get_sqe(&ring);
    req->event_type = EVENT_TYPE_WRITE;
    io_uring_prep_writev(sqe, req->client_socket, req->iov, req->iovec_count, 0);
    io_uring_sqe_set_data(sqe, req);
    io_uring_submit(&ring);
    return 0;
}

void _send_static_string_content(const char *str, int client_socket)
{
    struct request *req = zh_malloc(sizeof(*req) + sizeof(struct iovec));
    unsigned long slen = strlen(str);
    req->iovec_count = 1;
    req->client_socket = client_socket;
    req->iov[0].iov_base = zh_malloc(slen);
    req->iov[0].iov_len = slen;
    memcpy(req->iov[0].iov_base, str, slen);
    add_write_request(req);
}

/*
 * 当ZeroHTTPd遇到除GET或POST以外的其他HTTP方法时，这个函数用来通知客户端。
 * */

void handle_unimplemented_method(int client_socket)
{
    _send_static_string_content(unimplemented_content, client_socket);
}

/*
 * 该函数用于在请求的文件未找到时，向客户端发送 "HTTP Not Found "代码和消息。
 * */

void handle_http_404(int client_socket)
{
    _send_static_string_content(http_404_content, client_socket);
}

/*
 * 一旦确定了要服务的静态文件，这个函数就会被用来读取文件，
 * 并使用Linux的sendfile()系统调用在客户端套接字上写入。
 * 这样我们就省去了将文件缓冲区从内核传送到用户空间再传送回来的麻烦。
 * */

void copy_file_contents(char *file_path, off_t file_size, struct iovec *iov)
{
    int fd;

    char *buf = zh_malloc(file_size);
    fd = open(file_path, O_RDONLY);
    if (fd < 0)
        fatal_error("read");

    /* We should really check for short reads here */
    int ret = read(fd, buf, file_size);
    if (ret < file_size)
    {
        fprintf(stderr, "Encountered a short read.\n");
    }
    close(fd);

    iov->iov_base = buf;
    iov->iov_len = file_size;
}

/*
 * 简单的函数，获取我们要服务的文件的文件扩展名。
 * */

const char *get_filename_ext(const char *filename)
{
    const char *dot = strrchr(filename, '.');
    if (!dot || dot == filename)
        return "";
    return dot + 1;
}

/*
 * 发送HTTP 200 OK头，即服务器字符串，对于一些类型的文件，
 * 它还可以根据文件扩展名发送内容类型。它还会发送内容长度头。
 * 最后，它在一行中发送一个'\r\n'，表示头的结束和任何内容的开始。
 * */

void send_headers(const char *path, off_t len, struct iovec *iov)
{
    char small_case_path[1024];
    char send_buffer[1024];
    strcpy(small_case_path, path);
    strtolower(small_case_path);

    char *str = "HTTP/1.0 200 OK\r\n";
    unsigned long slen = strlen(str);
    iov[0].iov_base = zh_malloc(slen);
    iov[0].iov_len = slen;
    memcpy(iov[0].iov_base, str, slen);

    slen = strlen(SERVER_STRING);
    iov[1].iov_base = zh_malloc(slen);
    iov[1].iov_len = slen;
    memcpy(iov[1].iov_base, SERVER_STRING, slen);

    /*
                 * 检查网页上某些常见类型文件的文件扩展名，并发送适当的内容类型头。
                 * 由于扩展名可以混合大小写，如JPG、jpg或Jpg，所以我们在检查前将扩展名变成小写。
                 * */
    const char *file_ext = get_filename_ext(small_case_path);
    if (strcmp("jpg", file_ext) == 0)
        strcpy(send_buffer, "Content-Type: image/jpeg\r\n");
    if (strcmp("jpeg", file_ext) == 0)
        strcpy(send_buffer, "Content-Type: image/jpeg\r\n");
    if (strcmp("png", file_ext) == 0)
        strcpy(send_buffer, "Content-Type: image/png\r\n");
    if (strcmp("gif", file_ext) == 0)
        strcpy(send_buffer, "Content-Type: image/gif\r\n");
    if (strcmp("htm", file_ext) == 0)
        strcpy(send_buffer, "Content-Type: text/html\r\n");
    if (strcmp("html", file_ext) == 0)
        strcpy(send_buffer, "Content-Type: text/html\r\n");
    if (strcmp("js", file_ext) == 0)
        strcpy(send_buffer, "Content-Type: application/javascript\r\n");
    if (strcmp("css", file_ext) == 0)
        strcpy(send_buffer, "Content-Type: text/css\r\n");
    if (strcmp("txt", file_ext) == 0)
        strcpy(send_buffer, "Content-Type: text/plain\r\n");
    slen = strlen(send_buffer);
    iov[2].iov_base = zh_malloc(slen);
    iov[2].iov_len = slen;
    memcpy(iov[2].iov_base, send_buffer, slen);

    /* 发送内容长度头，也就是本例中的文件大小。 */
    sprintf(send_buffer, "content-length: %ld\r\n", len);
    slen = strlen(send_buffer);
    iov[3].iov_base = zh_malloc(slen);
    iov[3].iov_len = slen;
    memcpy(iov[3].iov_base, send_buffer, slen);

    /*
                 * 当浏览器看到一行中的'\r\n'序列时，它就会明白没有更多的标题了。内容可能随之而来。
                 * */
    strcpy(send_buffer, "\r\n");
    slen = strlen(send_buffer);
    iov[4].iov_base = zh_malloc(slen);
    iov[4].iov_len = slen;
    memcpy(iov[4].iov_base, send_buffer, slen);
}

void handle_get_method(char *path, int client_socket)
{
    char final_path[1024];

    /*
                 如果一个路径以尾部的斜杠结束，客户端可能希望索引文件在该目录内。
                 */
    if (path[strlen(path) - 1] == '/')
    {
        strcpy(final_path, "public");
        strcat(final_path, path);
        strcat(final_path, "index.html");
    }
    else
    {
        strcpy(final_path, "public");
        strcat(final_path, path);
    }

    /* stat()系统调用会给出文件的信息，如类型（普通文件、目录等）、大小等。 */
    struct stat path_stat;
    if (stat(final_path, &path_stat) == -1)
    {
        printf("404 Not Found: %s (%s)\n", final_path, path);
        handle_http_404(client_socket);
    }
    else
    {
        /*检查这是否是一个正常的/常规的文件，而不是一个目录或其他东西。*/
        if (S_ISREG(path_stat.st_mode))
        {
            struct request *req = zh_malloc(sizeof(*req) + (sizeof(struct iovec) * 6));
            req->iovec_count = 6;
            req->client_socket = client_socket;
            send_headers(final_path, path_stat.st_size, req->iov);
            copy_file_contents(final_path, path_stat.st_size, &req->iov[5]);
            printf("200 %s %ld bytes\n", final_path, path_stat.st_size);
            add_write_request(req);
        }
        else
        {
            handle_http_404(client_socket);
            printf("404 Not Found: %s\n", final_path);
        }
    }
}

/*
 * 这个函数查看所使用的方法，并调用相应的处理函数。因为我们只实现了GET和POST方法，
 * 所以它调用handle_unimplemented_method()，以防这两个方法不匹配。这将向客户发送一个错误。
 * */

void handle_http_method(char *method_buffer, int client_socket)
{
    char *method, *path, *saveptr;

    method = strtok_r(method_buffer, " ", &saveptr);
    strtolower(method);
    path = strtok_r(NULL, " ", &saveptr);

    if (strcmp(method, "get") == 0)
    {
        handle_get_method(path, client_socket);
    }
    else
    {
        handle_unimplemented_method(client_socket);
    }
}

int get_line(const char *src, char *dest, int dest_sz)
{
    for (int i = 0; i < dest_sz; i++)
    {
        dest[i] = src[i];
        if (src[i] == '\r' && src[i + 1] == '\n')
        {
            dest[i] = '\0';
            return 0;
        }
    }
    return 1;
}

int handle_client_request(struct request *req)
{
    char http_request[1024];
    /* 获取第一行，这将是请求的内容 */
    if (get_line(req->iov[0].iov_base, http_request, sizeof(http_request)))
    {
        fprintf(stderr, "Malformed request\n");
        exit(1);
    }
    handle_http_method(http_request, req->client_socket);
    return 0;
}

void server_loop(int server_socket)
{
    struct io_uring_cqe *cqe;
    struct sockaddr_in client_addr;
    socklen_t client_addr_len = sizeof(client_addr);

    add_accept_request(server_socket, &client_addr, &client_addr_len);

    while (1)
    {
        int ret = io_uring_wait_cqe(&ring, &cqe);
        struct request *req = (struct request *)cqe->user_data;
        if (ret < 0)
            fatal_error("io_uring_wait_cqe");
        if (cqe->res < 0)
        {
            fprintf(stderr, "Async request failed: %s for event: %d\n",
                    strerror(-cqe->res), req->event_type);
            exit(1);
        }

        switch (req->event_type)
        {
        case EVENT_TYPE_ACCEPT:
            add_accept_request(server_socket, &client_addr, &client_addr_len);
            add_read_request(cqe->res);
            free(req);
            break;
        case EVENT_TYPE_READ:
            if (!cqe->res)
            {
                fprintf(stderr, "Empty request!\n");
                break;
            }
            handle_client_request(req);
            free(req->iov[0].iov_base);
            free(req);
            break;
        case EVENT_TYPE_WRITE:
            for (int i = 0; i < req->iovec_count; i++)
            {
                free(req->iov[i].iov_base);
            }
            close(req->client_socket);
            free(req);
            break;
        }
        /* 将此请求标记为已处理 */
        io_uring_cqe_seen(&ring, cqe);
    }
}

void sigint_handler(int signo)
{
    printf("^C pressed. Shutting down.\n");
    io_uring_queue_exit(&ring);
    exit(0);
}

int main()
{
    int server_socket = setup_listening_socket(DEFAULT_SERVER_PORT);

    signal(SIGINT, sigint_handler);
    io_uring_queue_init(QUEUE_DEPTH, &ring, 0);
    server_loop(server_socket);

    return 0;
}
```
# 运行这个程序

这个程序要求您从包含“ public”文件夹的目录中运行它，该文件夹中有一个 `index.html` 文件和一个图像。
如果您按照[构建指令](https://github.com/shuveb/loti-examples)到示例程序，那么您新构建的二进制文件应该位于 `build` 目录中。
你需要换到git repo的根目录下，"public "文件夹就在那里，然后运行它。
构建所有示例并运行这个 webserver 示例的示例会话如下所示:

	$ mkdir build
	$ cd build
	$ cmake ..
	$ cmake --build .
	$ cd ..
	$ build/webserver_liburing
	Minimum kernel version required is: 5.5
	Your kernel version is: 5.6
	ZeroHTTPd listening on port: 8000

## 程序结构

首先，main()函数调用 **setup_listening_socket()** 在指定的端口上监听。但是我们不调用accept()来实际接受连接。
我们通过io_uring请求来实现这一点，后面会解释。

程序的核心是**server_loop()**函数，它向io_uring发送提交(自己和通过其他函数)，
等待完成队列条目并处理它们。让我们仔细看看。

```c
void server_loop(int server_socket) {
        struct io_uring_cqe *cqe;
        struct sockaddr_in client_addr;
        socklen_t client_addr_len = sizeof(client_addr);
        add_accept_request(server_socket, &client_addr, &client_addr_len);
        while (1) {
                int ret = io_uring_wait_cqe(&ring, &cqe);
                struct request *req = (struct request *) cqe->user_data;
                if (ret < 0)
                        fatal_error("io_uring_wait_cqe");
                if (cqe->res < 0) {
                        fprintf(stderr, "Async request failed: %s for event: %d\n",
                                        strerror(-cqe->res), req->event_type);
                        exit(1);
                }
                switch (req->event_type) {
                        case EVENT_TYPE_ACCEPT:
                                add_accept_request(server_socket, &client_addr, &client_addr_len);
                                add_read_request(cqe->res);
                                free(req);
                                break;
                        case EVENT_TYPE_READ:
                                if (!cqe->res) {
                                        fprintf(stderr, "Empty request!\n");
                                        break;
                                }
                                handle_client_request(req);
                                free(req->iov[0].iov_base);
                                free(req);
                                break;
                        case EVENT_TYPE_WRITE:
                                for (int i = 0; i < req->iovec_count; i++) {
                                        free(req->iov[i].iov_base);
                                }
                                close(req->client_socket);
                                free(req);
                                break;
                }
                /* 将此请求标记为已处理 */
                io_uring_cqe_seen(&ring, cqe);
        }
}
```

在进入while循环之前，我们通过调用 `add_accept_request()` 来提交一个 `accept()` 的请求。这样就可以接受任何客户端对服务器的连接。让我们仔细看看。

```c
int add_accept_request(int server_socket, struct sockaddr_in *client_addr,
                                        socklen_t *client_addr_len) {
        struct io_uring_sqe *sqe = io_uring_get_sqe(&ring);
        io_uring_prep_accept(sqe, server_socket, (struct sockaddr *) client_addr,
                                                client_addr_len, 0);
        struct request *req = malloc(sizeof(*req));
        req->event_type = EVENT_TYPE_ACCEPT;
        io_uring_sqe_set_data(sqe, req);
        io_uring_submit(&ring);
        return 0;
}
```

我们得到一个 SQE，并准备一个 accept ()操作，用liburing的io_uring_prep_accept()提交。我们使用一个request结构体来跟踪我们的每个提交。这些实例具有每个请求从一个状态到下一个状态的上下文。让我们来看一下 request 结构体:

```c
struct request {
        int event_type;
        int iovec_count;
        int client_socket;
        struct iovec iov[];
};
```

客户机请求要经过3个状态，上面的结构体可以容纳足够的信息，能够处理这些状态之间的转换。客户端请求的三种状态是:

Accepted -> Request read -> Response written

让我们看看在完成侧大的 switch/case 代码块，一旦 `accept ()` 操作完成后会发生什么:

```c
case EVENT_TYPE_ACCEPT:
     add_accept_request(server_socket, &client_addr, &client_addr_len);
     add_read_request(cqe->res);
     free(req);
     break;
```

既然已经处理了前一个请求，我们就在提交队列中添加一个新的`accept()`请求。否则我们的程序将不会接受任何来自客户端的新连接。
然后我们调用`add_read_request()`函数，它为readv()添加一个提交请求，这样我们就可以从客户端读取HTTP请求。
这里有几件事:
1. 我们本来可以使用`read()`，但是`io_uring`中直到内核版本5.6才支持该操作，而在撰写本文时，
内核版本5.6是一个非常稳定的版本，至少几个月后才会在许多发行版中出现。
2. 使用`readv()`和`writev()`允许我们内置许多通用逻辑，特别是我们稍后会看到的缓冲区管理。现在，让我们看看`add_read_request()`:

```c
int add_read_request(int client_socket) {
        struct io_uring_sqe *sqe = io_uring_get_sqe(&ring);
        struct request *req = malloc(sizeof(*req) + sizeof(struct iovec));
        req->iov[0].iov_base = malloc(READ_SZ);
        req->iov[0].iov_len = READ_SZ;
        req->event_type = EVENT_TYPE_READ;
        req->client_socket = client_socket;
        memset(req->iov[0].iov_base, 0, READ_SZ);
        /* Linux kernel 5.5 has support for readv, but not for recv() or read() */
        io_uring_prep_readv(sqe, client_socket, &req->iov[0], 1, 0);
        io_uring_sqe_set_data(sqe, req);
        io_uring_submit(&ring);
        return 0;
}
```

正如你所看到的，这是相当直接的。我们分配一个足够大的缓冲区来容纳客户机请求，
并在提交请求之前调用 `io_uring_prep_readv ()`,该调用处于liburing状态。完成端相应的处理是由switch/case块中的条件来完成的:

```c
case EVENT_TYPE_READ:
    if (!cqe->res) {
        fprintf(stderr, "Empty request!\n");
        break;
    }
    handle_client_request(req);
    free(req->iov[0].iov_base);
    free(req);
    break;
```

这里，我们实际上调用了 `handle_client_request()` 函数处理 HTTP 请求。如果一切顺利，客户端要求的是磁盘上的一个文件，这段代码运行如下:

```c
struct request *req = zh_malloc(sizeof(*req) + (sizeof(struct iovec) * 6));
req->iovec_count = 6;
req->client_socket = client_socket;
set_headers(final_path, path_stat.st_size, req->iov);
copy_file_contents(final_path, path_stat.st_size, &req->iov[5]);
printf("200 %s %ld bytes\n", final_path, path_stat.st_size);
add_write_request( req);
```
函数 set_headers() 设置了总共5个小缓冲区，由5个不同的 ivec 结构体表示。
最终的 iovec 实例包含正在读取的文件的内容。最后，调用 add_write_request ()来添加一个提交队列条目:

```c
int add_write_request(struct request *req) {
    struct io_uring_sqe *sqe = io_uring_get_sqe(&ring);
    req->event_type = EVENT_TYPE_WRITE;
    io_uring_prep_writev(sqe, req->client_socket, req->iov, req->iovec_count, 0);
    io_uring_sqe_set_data(sqe, req);
    io_uring_submit(&ring);
    return 0;
}
```

此提交导致内核在客户端套接字上写出响应头和文件内容，从而完成请求/响应循环。以下是我们在完成方面所做的:

```c
case EVENT_TYPE_WRITE:
    for (int i = 0; i < req->iovec_count; i++) {
        free(req->iov[i].iov_base);
    }
    close(req->client_socket);
    free(req);
    break;
```

我们释放我们曾经创建的多个指向缓冲区的iovec，释放了请求结构体实例，还关闭了客户端套接字，从而完成了 HTTP 请求的服务。

# 源代码

这个和其他例子的源代码可以在 [Github](https://github.com/shuveb/loti-examples) 上找到。





