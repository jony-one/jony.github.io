---
title: eBPF 扩展阅读-BPF Map
date: 2021-02-25 16:26:47
categories: 
	- [eBPF]
tags:
  - ebpf
author: Jony
---

# eBPF 扩展阅读 - BPF Maps

通过向程序传递消息而引起程序行为被调用，这种方式在软件工程中使用广泛。BPF 的功能之一就是内核中运行的代码和加载这些代码的程序可以通过**消息传递**的方式实现实时通信。

BPF Map 以键/值保存在内核中，可以被任何 BPF 程序访问。**用户空间** 使用文件描述符的方式访问 BPF Map。BPF Map 中可以保存任何类型的数据。本质是是保存的**二进制数据**，所以内核并不关心 BPF Map  保存的内容。

## 1. 创建 BPF Maps

创建 BPF Maps 最直接的方法是使用 bpf 系统调用。

```c
int bpf(int cmd, union bpf_attr *attr, unsigned int size );
```

cmd是eBPF支持的cmd，分为三类： 操作注入的代码、操作用于通信的map、前两个操作的混合。
创建一个新的 Map ：

**第一个参数**：BPF_MAP_CREATE 表示创建一个新的 Map 。调用后将返回与创建 Map 相关的文件描述符。
**第二个参数**是 Map 的设置。如下：

```c
union bpf_attr {
	struct 
	{
		__u32 map_type;
		__u32 key_size;
		__u32 value_size;
		__u32 max_entries;
		__u32 map_flags;
	};
}
```
**第三个参数**是设置属性的大小。

实际操作创建一个 Key 和 Value 为无符号整数的哈希表 Map：

```c
union bpf_attr my_map {
	.map_type = BPF_MAP_TYPE_HASH,
	.key_size = sizeof(int),
	.value_size = sizeof(int),
	.max_entries = 100,
	.map_flags = BPF_F_NO_PREALLOC,
};

int fd = bpf(BPF_MAP_CREATE, &my_map, sizeof(my_map));
```
如果系统调用失败，内核将会返回 `-1`,失败有三种原因，可以通过 `errno` 来区分你：
- 属性无效：内核将 `errno` 变量设置为 `EINVAL`
- 无权操作：内核将 `errno` 变量设置为 `EPERM`
- 内存不足：内核将 `errno` 变量设置为 `ENOMEM`

---

当然内核也包含了一些约定和帮助函数，用于生成和使用 BPF Map。调用这些约定比直接执行系统调用更为常用，因为约定的方式更具有可读性且更易遵循。但是这些约定底层仍然是通过 bpf 系统调用来创建 `Map`。比如 `bpf_create_map` 就封装了上述的编写的代码，如下：

```c
int fd;
fd = bpf_create_map(BPF_MAP_TYPE_HASH, sizeof(int), sizeof(int), 100, BPF_F_NO_PREALLOC);
```

---

当然如果事先知道将要使用的 Map 类型的话，我们也可以预先定义 Map。让程序更易于理解：

```c
struct bpf_map_def SEC("maps") my_map = {
	.map_type = BPF_MAP_TYPE_HASH,
	.key_size = sizeof(int),
	.value_size = sizeof(int),
	.max_entries = 100,
	.map_flags = BPF_F_NO_PREALLOC,
};
```

这种方式使用 `section` 属性来定义 Map，本示例中为 `SEC("maps")`。这个`宏`告诉内核该结构是 BPF Map ，并告诉内核创建相应的 Map。使用这种方式的创建 Map 如果需要使用与之想关联的文件描述符可以使用如下方式：

```c
int fd = map_data[0].fd;
```

因为内核使用 `map_data	` 全局变量来保存 BPF 程序 Map 信息。这个变量是数组结构，存储的内容是按照程序中指定 Map 的顺序进行排序。
初始化完成后，就可以使用它们在**内核和用户空间**之间传递消息。

## 使用 Map

内核和用户空间之间通信的基础就是编写 BPF 程序。内核和用户空间都可以访问 Map。

### 更新 BPF Map 元素

创建 Map 后，就需要使用其保存数据。我们可以使用 `bpf_map_update_elem` 来实现功能。
**内核程序**需要从 `bpf/bpf_helpers.h` 文件加载 `bpf_map_update_elem` 函数。
**用户空间程序**则需要从 `tools/lib/bpf/bpf.h` 文件加载 `bpf_map_update_elem` 函数。

因为二者的访问方式不同，**内核程序** 可以直接访问 `Map` ，而用户空间需要使用**文件描述符**来引用 `Map` 。而且两者访问 `Map` 的行为也略有不同。在内核上可以直接访问内存中的 `Map` ，而且操作也是**原子性**的。但是在**用户控件**中运行的代码需要发送消息到内核，需要先复制值再进行更新 `Map`，这使得更新操作非**原子性**的。如果更新操作成功就会范围 `0`，否则返回 `-1` 错误信息将会写入到全局变量 errno 中。

**`内核`**调用 `bpf_map_update_elem` 函数需要四个参数。
- 第一个指向已定义 `Map` 的指针。
- 第二个指向要更新的**键**的指针。
- 第三个参数是我们要存入的值
- 第四个参数是指定更新 `Map` 的方式：
	- 如果传递 0（常量：BPF_ANY），表示如果元素存在，**内核**将**更新元素**；如果不存在则**创建**
	- 如果传递 1（常量：BPF_NOEXIST），表示仅在元素不存在时，内核**创建元素**
	- 如果传递 2（常量：BPF_EXIST），表示仅在元素存在时，内核**更新元素**


#### 示例程序

在**内核空间**直接访问元素：

```c
int key, value, result;
key = 1, value = 12;
// other operator BPF_NOEXSIT and BPF_EXSIT etd;
result = bpf_map_update_elem(&my_map, &key, &value, BPF_ANY);
if (result == 0)
{
	printf("Kernel create or update element Success !\n");
}
if (result != 0)
{
	printf("Kernel create or update elem Fail :%d %s\n", result, stderror(errno) );
}
```

在**用户空间**访问元素需要使用到**文件描述符**，而不是直接使用 `Map` 的指针，当然**文件描述符**使用全局文件描述`map_data[0].fd`：
```c
int key, value, result;
key = 1, value = 12;

result = bpf_map_update_elem(map_data[0].fd, &key, &value, BPF_ANY);
if (result == 0)
{
	printf("User Space create or update element Success !\n");
}
if (result != 0)
{
	printf("User Space create or update element Fail :%d %s\n", result, stderror(errno) );
}
```


### 读取 BPF Map 元素

BPF 根据程序执行的位置提供了**两个不同**的帮助函数来读取 `Map` 元素，即**内核**与**用户空间**。这两个帮助函数名都为 `bpf_map_lookup_elem`。与更新帮助函数一样，它们仅在第一个参数上有所不同。第二个参数即为读取的键，第三个参数是指向变量的指针，该变量将保存从 `Map` 中读取的值。

#### 示例程序

在**内核空间**访问 `BPF Map` 中的值：

```c
int key, value, result;
key = 1;
result = bpf_map_lookup_elem(&my_map, &key, &value);

if (result == 0)
{
	printf("Kernel lookup element Success %d!\n",value);
}
if (result != 0)
{
	printf("Kernel create or update elem Fail :%d %s\n", result, stderror(errno) );
}
```

在**用户空间**访问 `BPF Map` 的值：

```c
int key, value, result;
key = 1;

result = bpf_map_lookup_elem(map_data[0].fd, &key, &value);
if (result == 0)
{
	printf("User Space lookup element Success %d!\n", value);
}
if (result != 0)
{
	printf("User Space lookup element Fail :%d %s\n", result, stderror(errno) );
}
```

### 删除 BPF Map 元素

下面将要介绍的是**删除**操作。与读取 `BPF Map` 元素相同， BPF 为我们提供了两个帮助函数来删除，函数名都为 `bpf_map_delete_element` 。
- 第一个参数与之前介绍帮助函数的一样
- 第二个参数即为要删除的键的引用
- 返回值也与之前的一样，但是情况有所不同，如果存在则返回正常结果，如果不存在则返回负数，并返回 `not found` 信息。

#### 示例程序

**内核空间**操作删除帮助函数：

```c
int key, result;
key = 1;
result = bpf_map_delete_element(&my_map, &key);
if (result == 0)
{
	printf("User Space delete element Success %d!\n", value);
}
if (result != 0)
{
	printf("User Space delete element Fail :%d %s\n", result, stderror(errno) );
}
```

**用户空间**操作删除帮助函数：

```c
int key, result;
key = 1;
result = bpf_map_delete_element(map_data[0].fd, &key);
if (result == 0)
{
	printf("User Space delete element Success %d!\n", value);
}
if (result != 0)
{
	printf("User Space delete element Fail :%d %s\n", result, stderror(errno) );
}
```

### 迭代 BPF Map 元素

当然除了 CURD 之外，我们还需要查找指定的 `Map` 元素。BPF 也提供了此类功能的帮助函数 `bpf_map_get_next_key` 指令。该指令与之前的指令不同，该指令只能运行与 `用户空间` 。
该指令需要三个参数：
- 第一个参数是 `Map` 的文件描述符
- 第二个参数是要查找的 Key
- 第三个参数是 `Map` 中的下一个 Key 即：next_key

一般情况下获取 `key` 的内容只需要调用 `bpf_map_lookup_elem` 即可，但是某些情况下想要获取当前 `key` 的下一个 `next_key` 是什么就需要调用这个帮助函数。

#### 示例代码

我们打印 `Map` 中的所有 `Key` ，只需要设置 `next_key` 为不存在的 `key` 即可，当返回值为**负数**时，代表以及遍历了 `Map` 的全部元素：

```c
int next_key, lookup_key;
lookup_key = -1;

while(bpf_map_get_next_key(map_data[0].fd, &lookup_key, &next_key) == 0 ){
	printf("The next key in the map is: %s\n", next_key);
	lookup_key = next_key;
}
```

如果我们之前存入了Key ：1、2、3、4
则将会输出：
	The next key in the map is: 1
	The next key in the map is: 2
	The next key in the map is: 3
	The next key in the map is: 4


**注意**
另外需要注意的是。BPF 使用 `bpf_map_get_next_key` 在遍历 `Map` 前不复制 `Map` 的值。如果程序正在遍历 `Map` 元素，程序的其他代码删除了映射中的元素，当遍历程序尝试查找下一个值是**已经删除的键**时，`bpf_map_get_next_key` 将重新开始查找，代码如下：

```c
int next_key, lookup_key;
lookup_key = -1;

while(bpf_map_get_next_key(map_data[0].fd, &lookup_key, &next_key) == 0){
	printf("The next key in the map is: %s\n", next_key);
	if (next_key == 2)
	{
		printf("Deleting key `2`\n", );
		bpf_map_delete_element(map_data[0].fd, &next_key);
	}
	lookup_key = next_key;
}
```

如果我们之前存入了Key ：1、2、3、4、5
则将会输出：
	The next key in the map is: 1
	The next key in the map is: 2
	Deleting key `2`
	The next key in the map is: 1
	The next key in the map is: 3
	The next key in the map is: 4
	The next key in the map is: 5

### 查找并删除元素

内核为 BPF Map 提供了一个在 `Map` 中查找指定的键并删除元素同时将该元素的值赋予元素的功能 `bpf_map_lookup_and_delete_element`。当我们使用**队列**和**栈映射**时，这个功能将会派上用场。

#### 示例代码

```c
int key, value, result, it;
key = 1;

for (it = 0; it < 2; ++it)
{
	result = bpf_map_lookup_and_delete_element(map_data[0].fd, &key, &value);
	if (result == 0)
	{
		printf("Value read from the map: '%d'\n", value);
	}else{
		printf("Failed to read value from the map: %d (%s)\n", result, stderror(errno) );
	}
}
```
在这个示例中，我们尝试两次提取并删除相同的元素。第一次可能执行成功，但是第二次将会失败，并且错误为 `not found` 。


### 并发访问 Map 元素

既然 `Map` 是全局共享的，那么就肯定会存在多个应用程序并发访问相同的 `Map` 。这可能会在 BPF 程序中产生竞争条件，并使得 `Map` 的行为不可预测。为了防止竞争条件，BPF 引入了 BPF **自旋锁**的概念，可以在操作 `Map` 映射时对访问的 `Map` 元素进行锁定，**自旋锁**仅适用于 `数组`、`哈希`、`cgroup` 存储 Map。

**内核**中有两个帮组函数与**自旋锁**一起使用：bpf_spin_lock 锁定，bpf_spin_unlock 解锁。工作原理：`使用充当信号的数据结构访问包括信号的元素，当信号锁定后，其他程序无法访问该元素值，直至信号被解除`。
**用户空间**可以使用一个标志来改变锁的状态：`BPF_F_LOCK`。

#### 示例代码

使用**自旋锁**，我们需要做的第一件事就是创建要锁定访问的元素，然后为该元素添加信号：

```c
struct concurrent_element
{
	struct bpf_spin_lock semaphore;
	int count;
};
```

我们需要将这个结构保存在 BPF Map 中，并在元素中使用 semaphore 防止对元素不可预测的访问。
**该元素必须使用 BPF 类型格式（BPF Type Format，BTF）进行注释，以便验证器知道如何解释 BTF**。

这里我们使用 `libbpf` 的内核宏来注释这个并发映射，代码如下：

```c
struct bpf_map_def SEC("maps") concurrent_map
{
	.type = BPF_MAP_TYPE_HASH,
	.key_size = sizeof(int),
	.value_size = sizeof(struct concurrent_element),
	.max_entries = 100,
};

BPF_ANNOTATE_KV_PAIR(concurrent_map, int, struct concurrent_element);
```
在 BPF 程序中，我们可以使用这两个锁帮助函数保护这些元素防止竞争条件。Map 元素的信号被锁定，程序就可以安全的修改元素的值：

```c
int bpf_program(struct pt_regs *ctx){
	int key = 0;
	struct concurrent_element init_value = {},
	struct concurrent_element *read_value,

	bpf_map_create_elem(&concurrent_map, &key, &init_value, BPF_NOEXSIT);

	read_value = bpf_map_lookup_elem(&concurrent_map, &key);
	bpf_spin_lock(&read_value->semaphore); // lock
	read_value->count += 100; //原子更新
	bpf_spin_unlock(&read_value->semaphore); // unlock
}
```

上述是在内核空间，对 Map 元素的并发访问进行控制。

在**用户空间**上，我们可以使用标识 `BPF_F_LOCK` 保存并发映射的引用。我们可以在 `bpf_map_update_elem` 和 `bpf_map_lookup_elem_flags` 两个帮助函数中使用此标志。从而无需担心数据竞争问题。

到目前为止，BPF 已经支持大于 150 个辅助函数，30 个 BPF Maps。


## 参考文档
- [BPF系统调用API v1](https://www.cnblogs.com/zhangzl2013/p/4008838.html)
- [Linux内核功能eBPF入门学习](https://blog.csdn.net/eydwyz/article/details/107983479)