package com.ahheng.tile2d.util.longqueue;

// long 先进先出队列(跨平台)
// 预取任务队列使用本接口存储待预取的瓦片 ID
public interface LongQueue {

    void enqueue(long value); // 队尾入队

    long dequeue(); // 队首出队,空队列调用会抛异常

    int size(); // 当前元素个数

    void clear(); // 清空全部元素

}