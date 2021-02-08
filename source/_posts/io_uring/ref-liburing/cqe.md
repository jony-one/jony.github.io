---
title: 'CQE:完成队列条目'
date: 2021-02-08 10:08:15
categories: ["Lord of the io_uring"]
tags:
  - io_uring
  
---

内核为每个提交的队列条目添加了一个完成队列事件(CQE)。CQE 包含作为 SQE 的一部分提交的操作的状态。作为内核处理 SQE 的结果，只有一个 CQE 添加到完成队列中。这两者之间存在一一对应的关系。

	# 注意
	这个结构是原始输入输出接口的一部分，在输入输出接口中定义。

```c
struct io_uring_cqe {
    __u64   user_data;      /* sqe->data 提交内容已传回 */
    __s32   res;            /* 此事件的结果代码 */
    __u32   flags;
};
```