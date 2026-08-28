package com.ahheng.tile2d.dimen;

import android.view.View;

import com.ahheng.tile2d.TileCoreService;
import com.ahheng.tile2d.tile.TileAdapter;
import com.ahheng.tile2d.tile.TileRecycledPool;
import com.ahheng.tile2d.util.intintmap.IntIntMap;
import com.ahheng.tile2d.util.intintmap.IntIntMapOpenHashMap;

// 简易测量工具
// 通过真实绑定瓦片并测量其宽高来推算列宽/行高,不建议在大数据量场景下使用
public class MeasurableDimenProvider implements TileDimenProvider {

    private final TileAdapter<TileCoreService.BaseTileHolder> adapter;
    private IntIntMap widths; // 列宽记录
    private IntIntMap heights; // 行高记录
    private TileRecycledPool<TileCoreService.BaseTileHolder> recycledTiles = new TileRecycledPool<>();

    private int defaultTileWidth;
    private int defaultTileHeight;
    private boolean minDefault; // 内容比默认尺寸小时,是否使用默认尺寸

    public MeasurableDimenProvider(TileAdapter<?> adapter) {
        this(0, 0, adapter);
    }

    public MeasurableDimenProvider(int defaultTileWidth, int defaultTileHeight, TileAdapter<?> adapter) {
        this.defaultTileWidth = defaultTileWidth;
        this.defaultTileHeight = defaultTileHeight;
        this.adapter = (TileAdapter<TileCoreService.BaseTileHolder>) adapter;
        this.widths = new IntIntMapOpenHashMap();
        this.heights = new IntIntMapOpenHashMap();
    }

    // 内容比默认尺寸小时,是否使用默认尺寸
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

    // 全量测量:覆盖适配器全部边界
    public void full() {
        reset();
        measure(adapter.getLeftBound(), adapter.getTopBound(), adapter.getRightBound(), adapter.getBottomBound());
    }

    // 测量指定范围内的所有瓦片,取每列/每行的最大宽高作为列宽/行高
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
                int lastWidth = widths.containsKey(column) ? widths.get(column) : (minDefault ? defaultTileWidth : width);
                int lastHeight = heights.containsKey(row) ? heights.get(row) : (minDefault ? defaultTileHeight : height);
                lastWidth = Math.max(lastWidth, width);
                lastHeight = Math.max(lastHeight, height);
                if (lastWidth != defaultTileWidth) {
                    widths.put(column, lastWidth);
                } else {
                    widths.remove(column);
                }
                if (lastHeight != defaultTileHeight) {
                    heights.put(row, lastHeight);
                } else {
                    heights.remove(row);
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

    // 清空测量记录
    public void reset() {
        widths.clear();
        heights.clear();
    }

    // 清空内部瓦片回收池
    public void clearRecycledTiles() {
        recycledTiles.reset();
    }

    @Override
    public int getTileWidth(int column) {
        return widths.containsKey(column) ? widths.get(column) : defaultTileWidth;
    }

    @Override
    public int getTileHeight(int row) {
        return heights.containsKey(row) ? heights.get(row) : defaultTileHeight;
    }

    @Override
    public void setTileWidth(int column, int width) {
        widths.put(column, width);
    }

    @Override
    public void setTileHeight(int row, int height) {
        heights.put(row, height);
    }

    @Override
    public void deleteTileWidth(int column) {
        widths.remove(column);
    }

    @Override
    public void deleteTileHeight(int row) {
        heights.remove(row);
    }

    // 替换列宽存储(数据迁移到新容器)
    public void setWidths(IntIntMap map) {
        if (map == null) throw new IllegalArgumentException("列宽存储不能设置为空");
        if (map == widths) return;
        map.clear();
        for (IntIntMap.Iterator it = widths.iterator(); it.next(); ) {
            map.put(it.key(), it.value());
        }
        widths.clear();
        widths = map;
    }

    // 替换行高存储(数据迁移到新容器)
    public void setHeights(IntIntMap map) {
        if (map == null) throw new IllegalArgumentException("行高存储不能设置为空");
        if (map == heights) return;
        map.clear();
        for (IntIntMap.Iterator it = heights.iterator(); it.next(); ) {
            map.put(it.key(), it.value());
        }
        heights.clear();
        heights = map;
    }

    // 替换内部回收池(数据迁移到新容器)
    public void setRecycledTiles(TileRecycledPool<TileCoreService.BaseTileHolder> map) {
        if (map == null) throw new IllegalArgumentException("回收池存储不能设置为空");
        if (map == recycledTiles) return;
        map.reset();
        recycledTiles.moveTo(map);
        recycledTiles = map;
    }

}