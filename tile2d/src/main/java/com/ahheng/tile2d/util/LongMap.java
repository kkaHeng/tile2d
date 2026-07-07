package com.ahheng.tile2d.util;

public interface LongMap<V> {

    V get(long key);

    void put(long key, V value);

    V remove(long key);

    int size();

    boolean containsKey(long key);

    void clear();

    default Iterator<V> iterator() {
        return iterator(false);
    }

    Iterator<V> iterator(boolean deleteMode);

    interface Iterator<V> {
        boolean next();
        long key();
        V value();
        void remove();
    }

}