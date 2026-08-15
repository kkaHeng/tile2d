package com.ahheng.tile2d.util.intintmap;

public interface IntIntMap {

    int get(int key);

    int get(int key, int defaultValue);

    void put(int key, int value);

    int remove(int key);

    int size();

    boolean containsKey(int key);

    void clear();

    default Iterator iterator() {
        return iterator(false);
    }

    Iterator iterator(boolean deleteMode);

    interface Iterator {
        boolean next();
        int key();
        int value();
        void remove();
    }

}