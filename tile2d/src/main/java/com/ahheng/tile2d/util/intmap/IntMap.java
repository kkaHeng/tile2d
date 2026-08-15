package com.ahheng.tile2d.util.intmap;

public interface IntMap<V> {

    V get(int key);

    void put(int key, V value);

    V remove(int key);

    int size();

    boolean containsKey(int key);

    void clear();

    default Iterator<V> iterator() {
        return iterator(false);
    }

    Iterator<V> iterator(boolean deleteMode);

    interface Iterator<V> {
        boolean next();
        int key();
        V value();
        void remove();
    }

}