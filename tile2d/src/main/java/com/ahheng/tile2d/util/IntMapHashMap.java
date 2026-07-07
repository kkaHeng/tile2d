package com.ahheng.tile2d.util;

import java.util.HashMap;
import java.util.Map;

public class IntMapHashMap<V> implements IntMap<V> {

    private final Map<Integer, V> map;

    public IntMapHashMap() {
        this.map = new HashMap<>();
    }

    public IntMapHashMap(int initialCapacity) {
        this.map = new HashMap<>(initialCapacity);
    }

    @Override
    public V get(int key) {
        return map.get(key);
    }

    @Override
    public void put(int key, V value) {
        map.put(key, value);
    }

    @Override
    public V remove(int key) {
        return map.remove(key);
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

    @Override
    public IntMap.Iterator<V> iterator(boolean deleteMode) {
        java.util.Iterator<Map.Entry<Integer, V>> i = map.entrySet().iterator();
        return new IntMap.Iterator<V>() {
            Map.Entry<Integer, V> e;
    
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
            public int key() {
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
