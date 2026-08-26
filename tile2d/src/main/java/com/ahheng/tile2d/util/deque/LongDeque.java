package com.ahheng.tile2d.util.deque;

// 长整数双端队列
public interface LongDeque {

    int size(); // 获取逻辑元素数量

    void clear(); // 元素 = 0

    long addFirst(long value); // → 元素

    long addLast(long value); // 元素 ←

    long removeFirst(); // ← 元素

    long removeLast(); // 元素 →

}
