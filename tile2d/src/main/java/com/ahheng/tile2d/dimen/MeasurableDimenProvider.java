package com.ahheng.tile2d.dimen;

import android.util.SparseIntArray;
import android.view.View;

import com.ahheng.tile2d.TileCoreService;
import com.ahheng.tile2d.widget.TileAdapter;
import com.ahheng.tile2d.widget.TileDimenProvider;
import com.ahheng.tile2d.widget.tile.TileRecycledPool;

// 简易测量工具，不建议在大数据量场景下使用
public class MeasurableDimenProvider implements TileDimenProvider {

    private final TileAdapter<TileCoreService.BaseTileHolder> adapter;
    private final SparseIntArray widths = new SparseIntArray();
    private final SparseIntArray heights = new SparseIntArray();
    private final TileRecycledPool<TileCoreService.BaseTileHolder> recycledTiles = new TileRecycledPool<>();

    private int defaultTileWidth;
    private int defaultTileHeight;
    private boolean minDefault; // 内容比默认尺寸小时，是否使用默认尺寸

    public MeasurableDimenProvider(TileAdapter<?> adapter) {
        this(0, 0, adapter);
    }

    public MeasurableDimenProvider(int defaultTileWidth, int defaultTileHeight, TileAdapter<?> adapter) {
        this.defaultTileWidth = defaultTileWidth;
        this.defaultTileHeight = defaultTileHeight;
        this.adapter = (TileAdapter<TileCoreService.BaseTileHolder>) adapter;
    }

    public boolean isMinDefault() {
    	return minDefault;
    }

    public void setMinDefault(boolean minDefault) {
    	this.minDefault = minDefault;
    }

    public void setDefaultTileWidth(int width) {
        defaultTileWidth = width;
    }

    public void setDefaultTileHeight(int height) {
        defaultTileHeight = height;
    }

    public int getDefaultTileWidth() {
        return defaultTileWidth;
    }

    public int getDefaultTileHeight() {
        return defaultTileHeight;
    }

    public void full() {
        reset();
        measure(adapter.getLeftBound(), adapter.getTopBound(), adapter.getRightBound(), adapter.getBottomBound());
    }

    public void measure(int colStart, int rowStart, int colEnd, int rowEnd) {
        int[] output = new int[2];
        int column = colStart;
        while (column <= colEnd) {
            int row = rowStart;
            while (row <= rowEnd) {
                int type = adapter.getTileType(column, row);
                TileCoreService.BaseTileHolder tile = recycledTiles.get(type);
                if (tile == null) {
                    tile = adapter.onCreateTileHolder(type);
                }
                int width = defaultTileWidth;
                int height = defaultTileHeight;
                if (tile instanceof Measurable) {
                    // 只绑定符合条件的可测量瓦片
                    adapter.onBindTileHolder(tile, column, row);
                    Measurable measurable = (Measurable) tile;
                    measurable.measure(
                            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.UNSPECIFIED),
                            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.UNSPECIFIED),
                            output);
                    width = output[0];
                    height = output[1];
                }
                int lastWidth = widths.indexOfKey(column) >= 0 ? widths.get(column) : (minDefault ? defaultTileWidth : width);
                int lastHeight = heights.indexOfKey(row) >= 0 ? heights.get(row) : (minDefault ? defaultTileHeight : height);
                lastWidth = Math.max(lastWidth, width);
                lastHeight = Math.max(lastHeight, height);
                if (lastWidth != defaultTileWidth) {
                    widths.put(column, lastWidth);
                } else {
                    widths.delete(column);
                }
                if (lastHeight != defaultTileHeight) {
                    heights.put(row, lastHeight);
                } else {
                    heights.delete(row);
                }
                if (tile != null) {
                    recycledTiles.recycle(type, tile);
                }
                if (row == rowEnd) break;
                row++;
            }
            if (column == colEnd) break;
            column++;
        }
    }

    public void reset() {
        widths.clear();
        heights.clear();
    }

    public void clearRecycledTiles() {
        recycledTiles.reset();
    }

    @Override
    public int getTileWidth(int column) {
        return widths.indexOfKey(column) >= 0 ? widths.get(column) : defaultTileWidth;
    }

    @Override
    public int getTileHeight(int row) {
        return heights.indexOfKey(row) >= 0 ? heights.get(row) : defaultTileHeight;
    }

}
