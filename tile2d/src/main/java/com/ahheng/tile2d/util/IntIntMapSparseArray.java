package com.ahheng.tile2d.util;

import android.util.SparseIntArray;

public class IntIntMapSparseArray implements IntIntMap {

    private final SparseIntArray sparseArray;

    private int mLastKey = 0;
    private int mLastIndex = -2;
    private boolean mCacheValid = false;

    public IntIntMapSparseArray() {
        this.sparseArray = new SparseIntArray();
    }

    public IntIntMapSparseArray(int initialCapacity) {
        this.sparseArray = new SparseIntArray(initialCapacity);
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
    public int get(int key) {
        int index = findIndex(key);
        return index >= 0 ? sparseArray.valueAt(index) : 0;
    }

    @Override
    public int get(int key, int defaultValue) {
        int index = findIndex(key);
        return index >= 0 ? sparseArray.valueAt(index) : defaultValue;
    }

    @Override
    public void put(int key, int value) {
        int index = findIndex(key);
        if (index >= 0) {
            sparseArray.setValueAt(index, value);
        } else {
            sparseArray.put(key, value);
            invalidateCache();
        }
    }

    @Override
    public int remove(int key) {
        int index = findIndex(key);
        if (index >= 0) {
            int old = sparseArray.valueAt(index);
            sparseArray.removeAt(index);
            invalidateCache();
            return old;
        }
        return 0;
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
    public IntIntMap.Iterator iterator(boolean deleteMode) {
        return new IntIntMap.Iterator() {
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
            public int value() {
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
