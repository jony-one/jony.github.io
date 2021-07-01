# SPDX-License-Identifier: (LGPL-2.1 OR BSD-2-Clause)
OUTPUT := .output
CLANG ?= clang
LLVM_STRIP ?= llvm-strip

BPFTOOL ?= $(abspath ../../tools/bpftool)
LIBBPF_SRC := $(abspath ../../libbpf/src)
LIBBPF_OBJ := $(abspath $(OUTPUT)/libbpf.a)
VMLINUX := ../../vmlinux/vmlinux.h
# Use our own libbpf API headers and Linux UAPI headers distributed with
# libbpf to avoid dependency on system-wide headers, which could be missing or
# outdated
# 默认情况下会将编译的文件放到 .output 下面。
# 1. 引入 uapi/*.h 和 vmlinux.h 头文件
INCLUDES := -I$(OUTPUT) -I../../libbpf/include/uapi -I$(dir $(VMLINUX))
# 使用调试信息进行编译
# 2. 编译带有 debug 文件
CFLAGS := -g -Wall
ARCH := $(shell uname -m | sed 's/x86_64/x86/')

APPS = minimal bootstrap uprobe kprobe fentry

# Get Clang's default includes on this system. We'll explicitly add these dirs
# to the includes list when compiling with `-target bpf` because otherwise some
# architecture-specific dirs will be "missing" on some architectures/distros -
# headers such as asm/types.h, asm/byteorder.h, asm/socket.h, asm/sockios.h,
# sys/cdefs.h etc. might be missing.
#
# Use '-idirafter': Don't interfere with include mechanics except where the
# build would have failed anyways.
CLANG_BPF_SYS_INCLUDES = $(shell $(CLANG) -v -E - </dev/null 2>&1 \
        | sed -n '/<...> search starts here:/,/End of search list./{ s| \(/.*\)|-idirafter \1|p }')

ifeq ($(V),1)
        Q =
        msg =
else
        Q = @
        msg = @printf '  %-8s %s%s\n'                                   \
                      "$(1)"                                            \
                      "$(patsubst $(abspath $(OUTPUT))/%,%,$(2))"       \
                      "$(if $(3), $(3))";
        MAKEFLAGS += --no-print-directory
endif

.PHONY: all
all: $(APPS)

.PHONY: clean
clean:
        $(call msg,CLEAN)
        $(Q)rm -rf $(OUTPUT) $(APPS)

$(OUTPUT) $(OUTPUT)/libbpf:
        $(call msg,MKDIR,$@)
        $(Q)mkdir -p $@
# libbpf 构建为静态库，API 头文件放到 .output 文件夹下
# Build libbpf
$(LIBBPF_OBJ): $(wildcard $(LIBBPF_SRC)/*.[ch] $(LIBBPF_SRC)/Makefile) | $(OUTPUT)/libbpf
        $(call msg,LIB,$@)
        $(Q)$(MAKE) -C $(LIBBPF_SRC) BUILD_STATIC_ONLY=1                      \
                    OBJDIR=$(dir $@)/libbpf DESTDIR=$(dir $@)                 \
                    INCLUDEDIR= LIBDIR= UAPIDIR=                              \
                    install

# Build BPF code
# 将 BPF C 代码构建到已编译的目标文件中
# -g 选项标识让 clang 输出 BTF 信息
# -D__TARGET_ARCH_$(ARCH) 为bpf_tracing.h头定义了必要的宏，处理低级别的struct pt_regs宏
$(OUTPUT)/%.bpf.o: %.bpf.c $(LIBBPF_OBJ) $(wildcard %.h) $(VMLINUX) | $(OUTPUT)
        $(call msg,BPF,$@)
        $(Q)$(CLANG) -g -O2 -target bpf -D__TARGET_ARCH_$(ARCH) $(INCLUDES) $(CLANG_BPF_SYS_INCLUDES) -c $(filter %.c,$^) -o $@
        $(Q)$(LLVM_STRIP) -g $@ # strip useless DWARF info

# Generate BPF skeletons
$(OUTPUT)/%.skel.h: $(OUTPUT)/%.bpf.o | $(OUTPUT)
        $(call msg,GEN-SKEL,$@)
        $(Q)$(BPFTOOL) gen skeleton $< > $@ # bpftool用于通过bpftool gen框架命令生成相应的BPF框架头(.skel.h)

# 保证内核空间程序更新时，用户空间程序也实时更新
# Build user-space code
$(patsubst %,$(OUTPUT)/%.o,$(APPS)): %.o: %.skel.h

$(OUTPUT)/%.o: %.c $(wildcard %.h) | $(OUTPUT)
        $(call msg,CC,$@)
        $(Q)$(CC) $(CFLAGS) $(INCLUDES) -c $(filter %.c,$^) -o $@

# Build application binary
$(APPS): %: $(OUTPUT)/%.o $(LIBBPF_OBJ) | $(OUTPUT)
        $(call msg,BINARY,$@)
        $(Q)$(CC) $(CFLAGS) $^ -lelf -lz -o $@

# delete failed targets
.DELETE_ON_ERROR:

# keep intermediate (.skel.h, .bpf.o, etc) targets
.SECONDARY:

# 最终生产的文件仅仅 200kb 适用于任何一个内核版本