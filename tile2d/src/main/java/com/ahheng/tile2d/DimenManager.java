package com.ahheng.tile2d;

import com.ahheng.tile2d.dimen.TileDimenProvider;
import com.ahheng.tile2d.util.intintmap.IntIntMap;
import com.ahheng.tile2d.util.intintmap.IntIntMapOpenHashMap;

// 尺寸管理器(跨平台)
// 负责管理尺寸相关的修改、存储、测量
public class DimenManager {

    private IntIntMap widths;
    private IntIntMap heights;
    private int defaultTileWidth;
    private int defaultTileHeight;
    private TileDimenProvider dimenProvider;

    private final Callback callback;

    public DimenManager(Callback callback) {
        this.callback = callback;
        this.widths = new IntIntMapOpenHashMap();
        this.heights = new IntIntMapOpenHashMap();
    }

    // 查询

    public int getTileWidth(int column) {
        if (widths.containsKey(column)) {
            return widths.get(column);
        }
        if (dimenProvider != null) {
            return dimenProvider.getTileWidth(column);
        }
        return defaultTileWidth;
    }

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

    public void setTileWidth(int column, int width, int gravity) {
        if (callback.isEmpty()) return;
        if (column > callback.getRightBound() || column < callback.getLeftBound())
            throw new IndexOutOfBoundsException("列索引 " + column + " 不在 [" + callback.getLeftBound() + "," + callback.getRightBound() + "] 范围内");
        int old = getTileWidth(column);
        if (width == 0) {
            widths.remove(column);
            width = getTileWidth(column);
        } else {
            widths.put(column, width);
        }
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

    public void setTileHeight(int row, int height, int gravity) {
        if (callback.isEmpty()) return;
        if (row > callback.getBottomBound() || row < callback.getTopBound())
            throw new IndexOutOfBoundsException("行索引 " + row + " 不在 [" + callback.getTopBound() + "," + callback.getBottomBound() + "] 范围内");
        int old = getTileHeight(row);
        if (height == 0) {
            heights.remove(row);
            height = getTileHeight(row);
        } else {
            heights.put(row, height);
        }
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

    public void setTileSize(int column, int width, int horizontalGravity, int row, int height, int verticalGravity) {
        if (callback.isEmpty()) return;
        if (column > callback.getRightBound() || column < callback.getLeftBound())
            throw new IndexOutOfBoundsException("列索引 " + column + " 不在 [" + callback.getLeftBound() + "," + callback.getRightBound() + "] 范围内");
        if (row > callback.getBottomBound() || row < callback.getTopBound())
            throw new IndexOutOfBoundsException("行索引 " + row + " 不在 [" + callback.getTopBound() + "," + callback.getBottomBound() + "] 范围内");
        int oldWidth = getTileWidth(column);
        int oldHeight = getTileHeight(row);

        if (width == 0) {
            widths.remove(column);
            width = getTileWidth(column);
        } else {
            widths.put(column, width);
        }
        boolean widthChanged = (width != oldWidth);

        if (height == 0) {
            heights.remove(row);
            height = getTileHeight(row);
        } else {
            heights.put(row, height);
        }
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

    public void deleteTileWidth(int column, int gravity) {
        setTileWidth(column, 0, gravity);
    }

    public void deleteTileHeight(int row, int gravity) {
        setTileHeight(row, 0, gravity);
    }

    // 默认尺寸

    public int getDefaultTileWidth() {
        return defaultTileWidth;
    }

    public int getDefaultTileHeight() {
        return defaultTileHeight;
    }

    public void setDefaultTileWidth(int width) {
        if (width <= 0) throw new IllegalArgumentException("宽度必须大于 0");
        this.defaultTileWidth = width;
    }

    public void setDefaultTileHeight(int height) {
        if (height <= 0) throw new IllegalArgumentException("高度必须大于 0");
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

    public void setWidths(IntIntMap map) {
        if (map == null) throw new IllegalArgumentException("widths map cannot be null");
        if (map == widths) return;
        map.clear();
        for (IntIntMap.Iterator it = widths.iterator(); it.next(); ) {
            map.put(it.key(), it.value());
        }
        widths.clear();
        widths = map;
    }

    public void setHeights(IntIntMap map) {
        if (map == null) throw new IllegalArgumentException("heights map cannot be null");
        if (map == heights) return;
        map.clear();
        for (IntIntMap.Iterator it = heights.iterator(); it.next(); ) {
            map.put(it.key(), it.value());
        }
        heights.clear();
        heights = map;
    }

    // 清理

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
