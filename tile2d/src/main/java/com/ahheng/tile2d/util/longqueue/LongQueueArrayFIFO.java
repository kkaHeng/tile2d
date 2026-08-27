package com.ahheng.tile2d.util.longqueue;

// 基于 fastutil 循环数组的 long 先进先出队列默认实现
// 复刻 fastutil 的 LongArrayFIFOQueue:
//  - 2 的幂容量, 掩码取模, 环形读写
//  - 队首出队、队尾入队, 均为 O(1), 无节点分配、无装箱
//  - 满载时容量翻倍, 按逻辑顺序重排到新数组
//  - 出队不清空槽位(long 为值类型, 不存在对象泄漏)
public class LongQueueArrayFIFO implements LongQueue {

    private static final int DEFAULT_INITIAL_SIZE = 16; // 默认初始容量(2 的幂)

    private long[] array; // 环形数组, 容量为 2 的幂
    private int head; // 队首索引
    private int tail; // 队尾索引(下一个写入位置)
    private int mask; // 容量掩码 = 容量 - 1
    private int size; // 当前元素个数

    public LongQueueArrayFIFO() {
        this(DEFAULT_INITIAL_SIZE);
    }

    public LongQueueArrayFIFO(int expected) {
        if (expected < 0) throw new IllegalArgumentException("expected must be >= 0: " + expected);
        int n = arraySize(expected);
        array = new long[n];
        mask = n - 1;
    }

    @Override
    public void enqueue(long value) {
        if (size == array.length) grow(array.length << 1);
        array[tail] = value;
        tail = (tail + 1) & mask;
        size++;
    }

    @Override
    public long dequeue() {
        if (size == 0) throw new IllegalStateException("队列为空, 出队前应先检查 size()");
        long value = array[head];
        head = (head + 1) & mask;
        size--;
        return value;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public void clear() {
        head = tail = size = 0;
    }

    // 扩容重建(容量翻倍), 按逻辑顺序重排, 重置读写指针
    private void grow(int newN) {
        long[] newArray = new long[newN];
        int i = head;
        int count = size;
        int pos = 0;
        while (count > 0) {
            newArray[pos] = array[i];
            i = (i + 1) & mask;
            pos++;
            count--;
        }
        array = newArray;
        mask = newN - 1;
        head = 0;
        tail = size;
    }

    // fastutil HashCommon 复刻

    // 最小 2 的幂, 不小于 x(fastutil HashCommon.nextPowerOfTwo)
    private static long nextPowerOfTwo(long x) {
        return 1L << (64 - Long.numberOfLeadingZeros(x - 1));
    }

    // 计算满足 expected 的最小 2 的幂容量
    private static int arraySize(int expected) {
        long s = Math.max(2, nextPowerOfTwo(expected));
        if (s > (1 << 30)) throw new IllegalArgumentException("Too large (" + expected + " expected elements)");
        return (int) s;
    }
}