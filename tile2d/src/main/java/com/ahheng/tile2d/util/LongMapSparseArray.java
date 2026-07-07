package com.ahheng.tile2d.util;

import android.util.LongSparseArray;

public class LongMapSparseArray<V> implements LongMap<V> {

    private final LongSparseArray<V> sparseArray;

    private long mLastKey = 0;
    private int mLastIndex = -2;
    private boolean mCacheValid = false;

    public LongMapSparseArray() {
        this.sparseArray = new LongSparseArray<>();
    }

    public LongMapSparseArray(int initialCapacity) {
        this.sparseArray = new LongSparseArray<>(initialCapacity);
    }

    private int findIndex(long key) {
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
    public V get(long key) {
        int index = findIndex(key);
        return index >= 0 ? sparseArray.valueAt(index) : null;
    }

    @Override
    public void put(long key, V value) {
        int index = findIndex(key);
        if (index >= 0) {
            sparseArray.setValueAt(index, value);
        } else {
            sparseArray.put(key, value);
            invalidateCache();
        }
    }

    @Override
    public V remove(long key) {
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
    public boolean containsKey(long key) {
        return findIndex(key) >= 0;
    }

    @Override
    public void clear() {
        sparseArray.clear();
        invalidateCache();
    }

    @Override
    public LongMap.Iterator<V> iterator(boolean deleteMode) {
        return new LongMap.Iterator<V>() {
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
            public long key() {
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
