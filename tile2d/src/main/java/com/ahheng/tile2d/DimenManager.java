package com.ahheng.tile2d;

import com.ahheng.tile2d.dimen.TileDimenProvider;
import com.ahheng.tile2d.util.intintmap.IntIntMap;
import com.ahheng.tile2d.util.intintmap.IntIntMapOpenHashMap;

// 尺寸管理器(跨平台)
// 负责管理尺寸相关的修改、存储、测量
public class DimenManager {

    private IntIntMap widths; // 列宽记录
    private IntIntMap heights; // 行高记录
    private int defaultTileWidth;
    private int defaultTileHeight;
    private TileDimenProvider dimenProvider; // 外部尺寸提供者(优先级低于本地记录)

    private final Callback callback;

    public DimenManager(Callback callback) {
        this.callback = callback;
        this.widths = new IntIntMapOpenHashMap();
        this.heights = new IntIntMapOpenHashMap();
    }

    // 查询

    // 查询列宽:本地记录 > 尺寸提供者 > 默认值
    public int getTileWidth(int column) {
        if (widths.containsKey(column)) {
            return widths.get(column);
        }
        if (dimenProvider != null) {
            return dimenProvider.getTileWidth(column);
        }
        return defaultTileWidth;
    }

    // 查询行高:本地记录 > 尺寸提供者 > 默认值
    public int getTileHeight(int row) {
        if (heights.containsKey(row)) {
            return heights.get(row);
        }
        if (dimenProvider != null) {
            return dimenProvider.getTileHeight(row);
        }
        return defaultTileHeight;
    }

    // 修改

    // 设置列宽并同步刷新濒死区内受影响瓦片;width 必须大于 0，删除记录请用 deleteTileWidth
    public void setTileWidth(int column, int width, int gravity) {
        if (width <= 0) return;
        if (callback.isEmpty()) return;
        if (column > callback.getRightBound() || column < callback.getLeftBound()) return;
        int old = getTileWidth(column);
        widths.put(column, width);
        applyWidthChange(column, old, width, gravity);
    }

    // 删除列宽记录，回退默认值并同步刷新濒死区内受影响瓦片
    public void deleteTileWidth(int column, int gravity) {
        if (callback.isEmpty()) return;
        if (column > callback.getRightBound() || column < callback.getLeftBound()) return;
        int old = getTileWidth(column);
        widths.remove(column);
        int width = getTileWidth(column);
        applyWidthChange(column, old, width, gravity);
    }

    // 列宽变更后的同步：无变化跳过，否则刷新濒死区并通知布局
    private void applyWidthChange(int column, int old, int width, int gravity) {
        if (width == old) return;

        int dyingLeft = callback.getDyingLeft();
        int dyingRight = callback.getDyingRight();
        if (column >= dyingLeft && column <= dyingRight) {
            int row = callback.getDyingTop();
            int end = callback.getDyingBottom();
            while (row <= end) {
                callback.resizeTile(column, row, width, getTileHeight(row));
                if (row == end) break;
                row++;
            }
        }

        callback.beforeLayout();
        callback.updateWidth(column, old, width, gravity);
        callback.updateUI();
    }

    // 设置行高并同步刷新濒死区内受影响瓦片;height 必须大于 0，删除记录请用 deleteTileHeight
    public void setTileHeight(int row, int height, int gravity) {
        if (height <= 0) return;
        if (callback.isEmpty()) return;
        if (row > callback.getBottomBound() || row < callback.getTopBound()) return;
        int old = getTileHeight(row);
        heights.put(row, height);
        applyHeightChange(row, old, height, gravity);
    }

    // 删除行高记录，回退默认值并同步刷新濒死区内受影响瓦片
    public void deleteTileHeight(int row, int gravity) {
        if (callback.isEmpty()) return;
        if (row > callback.getBottomBound() || row < callback.getTopBound()) return;
        int old = getTileHeight(row);
        heights.remove(row);
        int height = getTileHeight(row);
        applyHeightChange(row, old, height, gravity);
    }

    // 行高变更后的同步：无变化跳过，否则刷新濒死区并通知布局
    private void applyHeightChange(int row, int old, int height, int gravity) {
        if (height == old) return;

        int dyingTop = callback.getDyingTop();
        int dyingBottom = callback.getDyingBottom();
        if (row >= dyingTop && row <= dyingBottom) {
            int column = callback.getDyingLeft();
            int end = callback.getDyingRight();
            while (column <= end) {
                callback.resizeTile(column, row, getTileWidth(column), height);
                if (column == end) break;
                column++;
            }
        }

        callback.beforeLayout();
        callback.updateHeight(row, old, height, gravity);
        callback.updateUI();
    }

    // 同时设置列宽与行高,一次性同步刷新受影响瓦片;宽高必须大于 0，删除记录请用 deleteTileWidth/deleteTileHeight
    public void setTileSize(int column, int width, int horizontalGravity, int row, int height, int verticalGravity) {
        if (width <= 0 || height <= 0) return;
        if (callback.isEmpty()) return;
        if (column > callback.getRightBound() || column < callback.getLeftBound()) return;
        if (row > callback.getBottomBound() || row < callback.getTopBound()) return;
        int oldWidth = getTileWidth(column);
        int oldHeight = getTileHeight(row);

        widths.put(column, width);
        boolean widthChanged = (width != oldWidth);

        heights.put(row, height);
        boolean heightChanged = (height != oldHeight);

        if (!widthChanged && !heightChanged) return;

        if (widthChanged) {
            int dyingLeft = callback.getDyingLeft();
            int dyingRight = callback.getDyingRight();
            if (column >= dyingLeft && column <= dyingRight) {
                int r = callback.getDyingTop();
                int end = callback.getDyingBottom();
                while (r <= end) {
                    if (r != row) callback.resizeTile(column, r, width, getTileHeight(r));
                    if (r == end) break;
                    r++;
                }
            }
        }
        if (heightChanged) {
            int dyingTop = callback.getDyingTop();
            int dyingBottom = callback.getDyingBottom();
            if (row >= dyingTop && row <= dyingBottom) {
                int c = callback.getDyingLeft();
                int end = callback.getDyingRight();
                while (c <= end) {
                    if (c != column) callback.resizeTile(c, row, getTileWidth(c), height);
                    if (c == end) break;
                    c++;
                }
            }
        }
        callback.resizeTile(column, row, width, height);

        callback.beforeLayout();
        callback.updateSize(
                column, oldWidth, width, horizontalGravity,
                row, oldHeight, height, verticalGravity
        );
        callback.updateUI();
    }

    // 默认尺寸

    public int getDefaultTileWidth() {
        return defaultTileWidth;
    }

    public int getDefaultTileHeight() {
        return defaultTileHeight;
    }

    public void setDefaultTileWidth(int width) {
        if (width <= 0) return;
        this.defaultTileWidth = width;
    }

    public void setDefaultTileHeight(int height) {
        if (height <= 0) return;
        this.defaultTileHeight = height;
    }

    // 尺寸提供者

    public TileDimenProvider getDimenProvider() {
        return dimenProvider;
    }

    public void setDimenProvider(TileDimenProvider dimenProvider) {
        this.dimenProvider = dimenProvider;
    }

    // 存储替换

    // 替换列宽存储(数据迁移到新容器)
    public void setWidths(IntIntMap map) {
        if (map == null) return;
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
        if (map == null) return;
        if (map == heights) return;
        map.clear();
        for (IntIntMap.Iterator it = heights.iterator(); it.next(); ) {
            map.put(it.key(), it.value());
        }
        heights.clear();
        heights = map;
    }

    // 清理

    // 清空全部尺寸记录
    public void clear() {
        widths.clear();
        heights.clear();
    }

    // 回调接口

    public interface Callback {

        // 边界
        int getLeftBound();

        int getTopBound();

        int getRightBound();

        int getBottomBound();

        // 濒死区
        int getDyingLeft();

        int getDyingTop();

        int getDyingRight();

        int getDyingBottom();

        // 瓦片同步刷新
        void resizeTile(int column, int row, int width, int height);

        // 布局扰动
        void updateWidth(int column, int oldWidth, int newWidth, int gravity);

        void updateHeight(int row, int oldHeight, int newHeight, int gravity);

        void updateSize(int column, int oldWidth, int newWidth, int hGravity,
                        int row, int oldHeight, int newHeight, int vGravity);

        // 调度
        void beforeLayout();

        void updateUI();

        // 状态
        boolean isEmpty();

    }

}