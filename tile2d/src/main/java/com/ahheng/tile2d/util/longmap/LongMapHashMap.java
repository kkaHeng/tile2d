package com.ahheng.tile2d.util.longmap;

import java.util.HashMap;
import java.util.Map;

// long 键对象值映射表:基于 java.util.HashMap 的包装实现
// 键装箱为 Long,适用于对性能不敏感或键较稀疏的场景
public class LongMapHashMap<V> implements LongMap<V> {

    private final Map<Long, V> map;

    public LongMapHashMap() {
        this.map = new HashMap<>();
    }

    public LongMapHashMap(int initialCapacity) {
        this.map = new HashMap<>(initialCapacity);
    }

    @Override
    public V get(long key) {
        return map.get(key);
    }

    @Override
    public void put(long key, V value) {
        map.put(key, value);
    }

    @Override
    public V remove(long key) {
        return map.remove(key);
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public boolean containsKey(long key) {
        return map.containsKey(key);
    }

    @Override
    public void clear() {
        map.clear();
    }

    // 迭代器包装:deleteMode 由底层迭代器支持,语义等价
    @Override
    public LongMap.Iterator<V> iterator(boolean deleteMode) {
        java.util.Iterator<Map.Entry<Long, V>> i = map.entrySet().iterator();
        return new LongMap.Iterator<V>() {
            private Map.Entry<Long, V> e;
            @Override
            public boolean next() {
                if (i.hasNext()) {
                    e = i.next();
                    return true;
                }
                e = null;
                return false;
            }
            @Override
            public long key() {
                if (e == null) throw new IllegalStateException();
                return e.getKey();
            }
            @Override
            public V value() {
                if (e == null) throw new IllegalStateException();
                return e.getValue();
            }
            @Override
            public void remove() {
                if (e == null) throw new IllegalStateException();
                i.remove();
                e = null;
            }
        };
    }

}