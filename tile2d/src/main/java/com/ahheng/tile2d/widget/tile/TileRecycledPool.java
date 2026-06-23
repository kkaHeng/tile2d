package com.ahheng.tile2d.widget.tile;

import android.util.SparseArray;

import com.ahheng.tile2d.TileCoreService;

import java.util.ArrayDeque;
import java.util.Deque;

public class TileRecycledPool <T extends TileCoreService.BaseTileHolder> {

    private final SparseArray<Deque<T>> recycledTiles = new SparseArray<>();

    public void reset() {
        recycledTiles.clear();
    }

    public T get(int type) {
        Deque<T> deque = recycledTiles.get(type);
        if (deque == null || deque.isEmpty()) {
            return null;
        }
        return deque.poll();
    }

    public void recycle(int type, T tile) {
        Deque<T> deque = recycledTiles.get(type);
        if (deque == null) {
            deque = new ArrayDeque<>();
            recycledTiles.put(type, deque);
        }
        deque.offer(tile);
    }

}
