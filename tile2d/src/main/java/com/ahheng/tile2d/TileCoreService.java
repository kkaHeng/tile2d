package com.ahheng.tile2d;

import com.ahheng.tile2d.widget.TileAdapter;

import java.util.ArrayDeque;
import java.util.Deque;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

public class TileCoreService <T> {

    private TileAdapter<T> adapter;
    private final Long2ObjectMap<T> activeTiles = new Long2ObjectOpenHashMap<>();
    private final Int2ObjectMap<Deque<T>> recycledTiles = new Int2ObjectOpenHashMap<>();


    public T obtain(int type) {
        Deque<T> tiles = recycledTiles.get(type);
        if (tiles != null) {
            return tiles.poll();
        }
        return adapter.onCreateTileHolder(type);
    }

    public void recycle(int type, T tile) {
        Deque<T> tiles = recycledTiles.computeIfAbsent(type, k -> new ArrayDeque<>());
        tiles.offer(tile);
    }

    public TileAdapter<T> getAdapter() {
        return adapter;
    }

    public void setAdapter(TileAdapter<T> adapter) {
        this.adapter = adapter;
    }

}
