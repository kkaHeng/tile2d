package com.ahheng.tile2d.util.intintmap;

import java.util.HashMap;
import java.util.Map;

// int 键 int 值映射表:基于 java.util.HashMap 的包装实现
// 键值均装箱,适用于对性能不敏感或键较稀疏的场景
public class IntIntMapHashMap implements IntIntMap {

    private final Map<Integer, Integer> map;

    public IntIntMapHashMap() {
        this.map = new HashMap<>();
    }

    public IntIntMapHashMap(int initialCapacity) {
        this.map = new HashMap<>(initialCapacity);
    }

    @Override
    public int get(int key) {
        Integer v = map.get(key);
        return v != null ? v : 0;
    }

    @Override
    public int get(int key, int defaultValue) {
        Integer v = map.get(key);
        return v != null ? v : defaultValue;
    }

    @Override
    public void put(int key, int value) {
        map.put(key, value);
    }

    @Override
    public int remove(int key) {
        Integer removed = map.remove(key);
        return removed != null ? removed : 0;
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public boolean containsKey(int key) {
        return map.containsKey(key);
    }

    @Override
    public void clear() {
        map.clear();
    }

    // 迭代器包装:deleteMode 由底层迭代器支持,语义等价
    @Override
    public IntIntMap.Iterator iterator(boolean deleteMode) {
        java.util.Iterator<Map.Entry<Integer, Integer>> i = map.entrySet().iterator();
        return new IntIntMap.Iterator() {
            private Map.Entry<Integer, Integer> e;
            @Override
            public boolean next() {
                if (i.hasNext()) {
                    e = i.next();
                    return true;
                }
                return false;
            }
            @Override
            public int key() {
                if (e == null) throw new IllegalStateException();
                return e.getKey();
            }
            @Override
            public int value() {
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