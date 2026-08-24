package com.ahheng.tile2d.util.deque;

import java.util.NoSuchElementException;

// 基于环形数组的 LongDeque 默认实现
// 自动扩容：容量满时自动翻倍，元素不会丢失
// 队列为空时 removeFirst / removeLast 抛 NoSuchElementException
public class RingLongDeque implements LongDeque {

    private static final int DEFAULT_CAPACITY = 16;

    private long[] elements;
    private int head; // 队首下标
    private int size;

    public RingLongDeque() {
        this(DEFAULT_CAPACITY);
    }

    public RingLongDeque(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive: " + capacity);
        }
        this.elements = new long[capacity];
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public void clear() {
        head = 0;
        size = 0;
    }

    @Override
    public long addFirst(long value) {
        if (size == elements.length) {
            grow();
        }
        head = (head - 1 + elements.length) % elements.length;
        elements[head] = value;
        size++;
        return 0L;
    }

    @Override
    public long addLast(long value) {
        if (size == elements.length) {
            grow();
        }
        elements[(head + size) % elements.length] = value;
        size++;
        return 0L;
    }

    // 容量翻倍，并把环形数据重新排到下标 0 开始
    private void grow() {
        long[] newElements = new long[elements.length << 1];
        for (int i = 0; i < size; i++) {
            newElements[i] = elements[(head + i) % elements.length];
        }
        elements = newElements;
        head = 0;
    }

    @Override
    public long removeFirst() {
        if (size == 0) {
            throw new NoSuchElementException("deque is empty");
        }
        long value = elements[head];
        head = (head + 1) % elements.length;
        size--;
        return value;
    }

    @Override
    public long removeLast() {
        if (size == 0) {
            throw new NoSuchElementException("deque is empty");
        }
        long value = elements[(head + size - 1) % elements.length];
        size--;
        return value;
    }

}
