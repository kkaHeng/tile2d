package com.ahheng.tile2d.util.intmap;

import android.util.SparseArray;

public class IntMapSparseArray<V> implements IntMap<V> {

    private final SparseArray<V> sparseArray;

    private int mLastKey = 0;
    private int mLastIndex = -2;  // -2 表示无效
    private boolean mCacheValid = false;

    public IntMapSparseArray() {
        this.sparseArray = new SparseArray<>();
    }

    public IntMapSparseArray(int initialCapacity) {
        this.sparseArray = new SparseArray<>(initialCapacity);
    }

    private int findIndex(int key) {
        if (mCacheValid && mLastKey == key) {
            return mLastIndex;
        }
        int index = sparseArray.indexOfKey(key);
        mLastKey = key;
        mLastIndex = index;
        mCacheValid = true;
        return index;
    }

    private void invalidateCache() {
        mCacheValid = false;
        mLastIndex = -2;
    }

    @Override
    public V get(int key) {
        int index = findIndex(key);
        return index >= 0 ? sparseArray.valueAt(index) : null;
    }

    @Override
    public void put(int key, V value) {
        int index = findIndex(key);
        if (index >= 0) {
            sparseArray.setValueAt(index, value);
        } else {
            sparseArray.put(key, value);
            invalidateCache();
        }
    }

    @Override
    public V remove(int key) {
        int index = findIndex(key);
        if (index >= 0) {
            V old = sparseArray.valueAt(index);
            sparseArray.removeAt(index);
            invalidateCache();
            return old;
        }
        return null;
    }

    @Override
    public int size() {
        return sparseArray.size();
    }

    @Override
    public boolean containsKey(int key) {
        return findIndex(key) >= 0;
    }

    @Override
    public void clear() {
        sparseArray.clear();
        invalidateCache();
    }

    @Override
    public IntMap.Iterator<V> iterator(boolean deleteMode) {
        return new IntMap.Iterator<V>() {
            private final int size = sparseArray.size();
            private int pos = deleteMode ? size : -1;
            @Override
            public boolean next() {
                if (!deleteMode && pos + 1 < size) {
                    pos++;
                    return true;
                }
                if (deleteMode && pos > 0) {
                    pos--;
                    return true;
                }
                return false;
            }
            @Override
            public int key() {
                if (pos < 0 || pos >= size) throw new IllegalStateException();
                return sparseArray.keyAt(pos);
            }
            @Override
            public V value() {
                if (pos < 0 || pos >= size) throw new IllegalStateException();
                return sparseArray.valueAt(pos);
            }
            @Override
            public void remove() {
                if (pos < 0 || pos >= sparseArray.size()) {
                    throw new IllegalStateException();
                }
                sparseArray.removeAt(pos);
                if (!deleteMode) pos--;
                invalidateCache();
            }
        };
    }
}
