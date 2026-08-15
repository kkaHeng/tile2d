package com.ahheng.tile2d.util.longmap;

// 基于 fastutil 开地址法(open addressing)的 long->V 哈希表默认实现
// 复刻 fastutil 的 Long2ObjectOpenHashMap:
//  - 2 的幂容量, 掩码取模, 线性探测
//  - 黄金比例快速混合散列(fastutil HashCommon.mix, Koloboke 风格)
//  - 负载因子 0.75, 达到 maxFill 自动扩容
//  - key==0 作为空槽标记, 0 键由 containsNullKey 哨兵独立存储
//  - 删除采用 shiftKeys 前移探测链, 保证链完整
public class LongMapOpenHashMap<V> implements LongMap<V> {

    /** 默认初始容量(2 的幂) */
    private static final int DEFAULT_INITIAL_SIZE = 16;
    /** 默认负载因子 */
    private static final float DEFAULT_LOAD_FACTOR = 0.75f;

    /** 键数组, 0 表示空槽 */
    private long[] key;
    /** 值数组 */
    private V[] value;
    /** 当前元素个数(含 0 键) */
    private int size;
    /** 容量掩码 = 容量 - 1 */
    private int mask;
    /** 容量(2 的幂) */
    private int n;
    /** 是否存储了 0 键 */
    private boolean containsNullKey;
    /** 0 键对应的值(哨兵, 存于 value 数组之外) */
    private V nullValue;
    /** 触发扩容的元素个数上限 */
    private int maxFill;

    public LongMapOpenHashMap() {
        this(DEFAULT_INITIAL_SIZE);
    }

    public LongMapOpenHashMap(int expected) {
        if (expected < 0) throw new IllegalArgumentException("expected must be >= 0: " + expected);
        n = arraySize(expected, DEFAULT_LOAD_FACTOR);
        key = new long[n];
        value = (V[]) new Object[n];
        mask = n - 1;
        maxFill = maxFill(n, DEFAULT_LOAD_FACTOR);
    }

    @Override
    public V get(long k) {
        if (k == 0) return containsNullKey ? nullValue : null;
        long curr;
        int pos = (int) mix(k) & mask;
        if ((curr = key[pos]) == 0) return null;
        if (k == curr) return value[pos];
        while (true) {
            if ((curr = key[pos = (pos + 1) & mask]) == 0) return null;
            if (k == curr) return value[pos];
        }
    }

    @Override
    public void put(long k, V v) {
        if (k == 0) {
            if (containsNullKey) {
                nullValue = v;
                return;
            }
            containsNullKey = true;
            nullValue = v;
            size++;
            if (size >= maxFill) rehash(arraySize(size + 1, DEFAULT_LOAD_FACTOR));
            return;
        }
        long curr;
        int pos = (int) mix(k) & mask;
        if ((curr = key[pos]) == 0) {
            key[pos] = k;
            value[pos] = v;
            size++;
            if (size >= maxFill) rehash(arraySize(size + 1, DEFAULT_LOAD_FACTOR));
            return;
        }
        if (k == curr) {
            value[pos] = v;
            return;
        }
        while (true) {
            if ((curr = key[pos = (pos + 1) & mask]) == 0) {
                key[pos] = k;
                value[pos] = v;
                size++;
                if (size >= maxFill) rehash(arraySize(size + 1, DEFAULT_LOAD_FACTOR));
                return;
            }
            if (k == curr) {
                value[pos] = v;
                return;
            }
        }
    }

    @Override
    public V remove(long k) {
        if (k == 0) {
            if (!containsNullKey) return null;
            containsNullKey = false;
            V old = nullValue;
            nullValue = null;
            size--;
            return old;
        }
        long curr;
        int pos = (int) mix(k) & mask;
        if ((curr = key[pos]) == 0) return null;
        if (k == curr) return removeEntry(pos);
        while (true) {
            if ((curr = key[pos = (pos + 1) & mask]) == 0) return null;
            if (k == curr) return removeEntry(pos);
        }
    }

    // 删除指定槽位并前移探测链后续元素, 保证链完整
    private V removeEntry(int pos) {
        V old = value[pos];
        size--;
        shiftKeys(pos);
        return old;
    }

    // 删除 pos 后, 把探测链上后续元素前移填补空洞
    private void shiftKeys(int pos) {
        long curr;
        int last, slot;
        for (;;) {
            pos = (last = pos) + 1 & mask;
            for (;;) {
                if ((curr = key[pos]) == 0) {
                    key[last] = 0;
                    value[last] = null;
                    return;
                }
                slot = (int) mix(curr) & mask;
                if (last <= pos ? last >= slot || slot > pos : last >= slot && slot > pos) break;
                pos = pos + 1 & mask;
            }
            key[last] = curr;
            value[last] = value[pos];
        }
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean containsKey(long k) {
        if (k == 0) return containsNullKey;
        long curr;
        int pos = (int) mix(k) & mask;
        if ((curr = key[pos]) == 0) return false;
        if (k == curr) return true;
        while (true) {
            if ((curr = key[pos = (pos + 1) & mask]) == 0) return false;
            if (k == curr) return true;
        }
    }

    @Override
    public void clear() {
        java.util.Arrays.fill(key, 0);
        java.util.Arrays.fill(value, null);
        size = 0;
        containsNullKey = false;
        nullValue = null;
    }

    // 扩容重建(容量翻倍至满足 size+1 负载)
    private void rehash(int newN) {
        long[] oldKey = key;
        V[] oldValue = value;
        int oldN = n;
        long[] newKey = new long[newN];
        V[] newValue = (V[]) new Object[newN];
        int newMask = newN - 1;
        n = newN;
        mask = newMask;
        maxFill = maxFill(newN, DEFAULT_LOAD_FACTOR);
        int pos;
        for (int i = oldN; i-- != 0; ) {
            long k = oldKey[i];
            if (k != 0) {
                pos = (int) mix(k) & newMask;
                while (newKey[pos] != 0) pos = pos + 1 & newMask;
                newKey[pos] = k;
                newValue[pos] = oldValue[i];
            }
        }
        key = newKey;
        value = newValue;
        // 0 键值保存在 nullValue 字段, 无需迁移
    }

    @Override
    public Iterator<V> iterator(boolean deleteMode) {
        return new Iterator<V>() {
            /** 当前访问槽位(-1 表示尚未开始); 数组遍历完后为 n(进入 0 键阶段) */
            private int pos = -1;
            /** 0 键是否已返回 */
            private boolean nullKeyDone;

            @Override
            public boolean next() {
                while (pos + 1 < n) {
                    pos++;
                    if (key[pos] != 0) return true;
                }
                if (!nullKeyDone) {
                    nullKeyDone = true;
                    if (containsNullKey) {
                        pos = n; // 数组遍历完, 进入 0 键阶段
                        return true;
                    }
                }
                return false;
            }

            @Override
            public long key() {
                if (pos < n) {
                    if (pos < 0 || key[pos] == 0) throw new IllegalStateException();
                    return key[pos];
                }
                if (!containsNullKey) throw new IllegalStateException();
                return 0;
            }

            @Override
            public V value() {
                if (pos < n) {
                    if (pos < 0 || key[pos] == 0) throw new IllegalStateException();
                    return value[pos];
                }
                if (!containsNullKey) throw new IllegalStateException();
                return nullValue;
            }

            @Override
            public void remove() {
                if (pos < n) {
                    if (pos < 0 || key[pos] == 0) throw new IllegalStateException();
                    size--;
                    shiftKeys(pos);
                    pos--; // 回退一格: shiftKeys 可能前移元素到本槽, 下次 next 重新检查
                } else {
                    if (!containsNullKey) throw new IllegalStateException();
                    containsNullKey = false;
                    nullValue = null;
                    size--;
                }
            }
        };
    }

    // ===== fastutil HashCommon 复刻 =====

    // 黄金比例快速混合(fastutil HashCommon.mix(long), Koloboke 风格)
    private static long mix(long x) {
        long h = x * 0x9E3779B97F4A7C15L;
        h ^= h >>> 32;
        return h ^ (h >>> 16);
    }

    // 黄金比例快速混合(fastutil HashCommon.mix(int), Koloboke 风格)
    private static int mix(int x) {
        int h = x * 0x9E3779B9;
        return h ^ (h >>> 16);
    }

    // 最小 2 的幂, 不小于 x(fastutil HashCommon.nextPowerOfTwo)
    private static long nextPowerOfTwo(long x) {
        return 1L << (64 - Long.numberOfLeadingZeros(x - 1));
    }

    // 触发扩容的元素上限(fastutil HashCommon.maxFill)
    private static int maxFill(int n, float f) {
        return Math.min((int) Math.ceil(n * (double) f), n - 1);
    }

    // 计算满足 expected/f 的最小 2 的幂容量(fastutil HashCommon.arraySize)
    private static int arraySize(int expected, float f) {
        long s = Math.max(2, nextPowerOfTwo((long) Math.ceil(expected / (double) f)));
        if (s > (1 << 30)) throw new IllegalArgumentException("Too large (" + expected + " expected elements with load factor " + f + ")");
        return (int) s;
    }
}
