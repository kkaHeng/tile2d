package com.ahheng.tile2d;

import android.graphics.Rect;
import android.view.MotionEvent;

import com.ahheng.tile2d.widget.TileAdapter;
import com.ahheng.tile2d.widget.TileDimenProvider;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;

import java.util.ArrayDeque;
import java.util.Deque;

public class TileCoreService <T extends TileCoreService.BaseTileHolder> {

    private TileAdapter<T> adapter;
    private TileDimenProvider dimenProvider;
    private int defaultTileWidth;
    private int defaultTileHeight;

    private final Rect bounds = new Rect();
    private final TileLayoutService layoutService = new TileLayoutService(new PlatformService());
    private final Long2ObjectOpenHashMap<T> activeTiles = new Long2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<Deque<T>> recycledTiles = new Int2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<T> dyingTiles = new Long2ObjectOpenHashMap<>();
    private final LongArrayFIFOQueue prefetchTiles = new LongArrayFIFOQueue();
    private final Int2IntOpenHashMap widths = new Int2IntOpenHashMap();
    private final Int2IntOpenHashMap heights = new Int2IntOpenHashMap();

    private class PlatformService implements TileLayoutService.PlatformService {

        @Override
        public int getWindowWidth() {
            return bounds.width();
        }

        @Override
        public int getWindowHeight() {
            return bounds.height();
        }

        @Override
        public int getTileWidth(int column) {
            return TileCoreService.this.getTileWidth(column);
        }

        @Override
        public int getTileHeight(int row) {
            return TileCoreService.this.getTileHeight(row);
        }

        @Override
        public int getLeftBound() {
            return adapter.getLeftBound();
        }

        @Override
        public int getTopBound() {
            return adapter.getTopBound();
        }

        @Override
        public int getRightBound() {
            return adapter.getRightBound();
        }

        @Override
        public int getBottomBound() {
            return adapter.getBottomBound();
        }

        @Override
        public boolean isHorizontalScrollEnabled() {
            return true;
        }

        @Override
        public boolean isVerticalScrollEnabled() {
            return false;
        }

        @Override
        public void in(int column, int row) {
            TileCoreService.this.in(column, row);
        }

        @Override
        public void out(int column, int row) {
            TileCoreService.this.out(column, row);
        }
    }

    public boolean handleTouchEvent(MotionEvent event) {
    	return false;
    }

    public void seek(int column, int row, float offsetX, float offsetY) {
        for (T tile : activeTiles.values()) {
            recycle(((BaseTileHolder) tile).type, tile);
        }
        activeTiles.clear();
        layoutService.seek(column, row, offsetX, offsetY);
    }

    public void in(int column, int row) {
    	// 瓦片进入视窗
        long id = getTileId(column, row);
        T tile = dyingTiles.remove(id);
        if (tile != null) {
            activeTiles.put(id, tile);
        } else {
            // 不在濒死区，入队等待帧回调空隙通过适配器加载
            prefetchTiles.enqueue(id);
        }
    }

    public void out(int column, int row) {
    	// 瓦片离开视窗
        long id = getTileId(column, row);
        T tile = activeTiles.remove(id);
        if (tile != null) {
            // 存入濒死区避免立即回收
            dyingTiles.put(id, tile);
        }
    }

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

    public int getTileWidth(int column) {
    	return widths.getOrDefault(column, dimenProvider == null ? defaultTileWidth : dimenProvider.getTileWidth(column));
    }

    public int getTileHeight(int row) {
    	return heights.getOrDefault(row, dimenProvider == null ? defaultTileHeight : dimenProvider.getTileHeight(row));
    }

    public TileAdapter<T> getAdapter() {
        return adapter;
    }

    public void setAdapter(TileAdapter<T> adapter) {
        this.adapter = adapter;
    }

    public TileDimenProvider getDimenProvider() {
    	return dimenProvider;
    }

    public void setDimenProvider(TileDimenProvider dimenProvider) {
    	this.dimenProvider = dimenProvider;
    }

    public Rect getBounds() {
    	return bounds;
    }

    public void setBounds(int left, int top, int right, int bottom) {
    	bounds.set(left, top, right, bottom);
    }

    public int getDefaultTileWidth() {
    	return defaultTileWidth;
    }

    public int getDefaultTileHeight() {
    	return defaultTileHeight;
    }

    public void setDefaultTileWidth(int width) {
    	this.defaultTileWidth = width;
    }

    public void setDefaultTileHeight(int height) {
    	this.defaultTileHeight = height;
    }

    public static long getTileId(int column, int row) {
    	return ((long) column << 32) | (row & 0xFFFFFFFFL);
    }

    public static class BaseTileHolder {
        
        private int type;
        private int column;
        private int row;
        
        public void onRecycled() {}
        
        public void onInWindow() {}
        
        public void onOutWindow() {}
    }

}
