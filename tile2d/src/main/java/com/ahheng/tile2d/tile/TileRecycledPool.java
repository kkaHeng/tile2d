package com.ahheng.tile2d.tile;

import android.util.SparseArray;

import com.ahheng.tile2d.TileCoreService;
import com.ahheng.tile2d.util.IntMap;
import com.ahheng.tile2d.util.IntMapSparseArray;

import java.util.ArrayDeque;
import java.util.Deque;

public class TileRecycledPool <T extends TileCoreService.BaseTileHolder> {

    private IntMap<Deque<T>> recycledTiles;

    public TileRecycledPool() {
        this.recycledTiles = new IntMapSparseArray<>();
    }

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

    public void setRecycledTiles(IntMap<Deque<T>> map) {
        if (map == null) throw new IllegalArgumentException("recycledTiles map cannot be null");
        if (map == recycledTiles) return;
        map.clear();
        IntMap<Deque<T>> old = recycledTiles;
        IntMap<Deque<T>> newMap = map;
        for (IntMap.Iterator<Deque<T>> it = old.iterator(); it.next(); ) {
            newMap.put(it.key(), it.value());
        }
        old.clear();
        recycledTiles = newMap;
    }

    public void recycle(int type, T tile) {
        Deque<T> deque = recycledTiles.get(type);
        if (deque == null) {
            deque = new ArrayDeque<>();
            recycledTiles.put(type, deque);
        }
        deque.offer(tile);
    }

    public void moveTo(TileRecycledPool<T> recycledTiles) {
        if (recycledTiles == null) throw new IllegalArgumentException("recycledTiles map cannot be null");
        if (recycledTiles == this) return;
        recycledTiles.recycledTiles.clear();
    	for (IntMap.Iterator<Deque<T>> it = this.recycledTiles.iterator(); it.next(); ) {
            recycledTiles.recycledTiles.put(it.key(), it.value());
        }
        this.recycledTiles.clear();
    }

}
