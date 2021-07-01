#include <stdio.h>
#include <unistd.h>
#include <sys/resource.h>
#include <bpf/libbpf.h>
#include "minimal.skel.h"

static int libbpf_print_fn(enum libbpf_print_level level, const char *format, va_list args)
{
        return vfprintf(stderr, format, args);
}

static void bump_memlock_rlimit(void)
{
        struct rlimit rlim_new = {
                .rlim_cur       = RLIM_INFINITY,
                .rlim_max       = RLIM_INFINITY,
        };

        if (setrlimit(RLIMIT_MEMLOCK, &rlim_new)) {
                fprintf(stderr, "Failed to increase RLIMIT_MEMLOCK limit!\n");
                exit(1);
        }
}

int main(int argc, char **argv)
{
        struct minimal_bpf *skel;
        int err;

        /* 设置打印日志回掉用于调试，发送到标准输出 */
        libbpf_set_print(libbpf_print_fn);

        /* Bump RLIMIT_MEMLOCK 突破内核的内存限制，允许 BPF 子系统为您的 BPF 程序、Map 分配必要的资源 */
        bump_memlock_rlimit();

        /* 打开 BPF 应用 */
        skel = minimal_bpf__open();
        if (!skel) {
                fprintf(stderr, "Failed to open BPF skeleton\n");
                return 1;
        }

        /* 设置全局变量 */
        skel->bss->my_pid = getpid();

        /* 加载并验证BPF程序 */
        err = minimal_bpf__load(skel);
        if (err) {
                fprintf(stderr, "Failed to load and verify BPF skeleton\n");
                goto cleanup;
        }

        /* 将BPF 程序 handle_tp 附加到对应的内核跟踪点。这里将激活 BPF 程序 => handle_tp*/
        err = minimal_bpf__attach(skel);
        if (err) {
                fprintf(stderr, "Failed to attach BPF skeleton\n");
                goto cleanup;
        }

        printf("Successfully started! Please run `sudo cat /sys/kernel/debug/tracing/trace_pipe` "
               "to see output of the BPF programs.\n");

        for (;;) {
                /* trigger our BPF program */
                fprintf(stderr, ".");
                sleep(1);
        }

cleanup:
        minimal_bpf__destroy(skel);
        return -err;
}