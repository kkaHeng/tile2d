package com.ahheng.tile2d.util.longqueue;

// long 先进先出队列(跨平台)
// 预取任务队列使用本接口存储待预取的瓦片 ID
public interface LongQueue {

    void enqueue(long value);

    long dequeue();

    int size();

    void clear();

}
