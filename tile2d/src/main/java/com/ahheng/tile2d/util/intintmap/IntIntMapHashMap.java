package com.ahheng.tile2d.util.intintmap;

import java.util.HashMap;
import java.util.Map;

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