package com.ahheng.tile2d;

import com.ahheng.tile2d.tile.TileRecycledPool;
import com.ahheng.tile2d.util.longmap.LongMap;
import com.ahheng.tile2d.util.longmap.LongMapOpenHashMap;

// 瓦片管理器(跨平台)
// 负责瓦片相关的生命周期、存储、交互
public class TileManager<T extends TileCoreService.BaseTileHolder> {

    private LongMap<T> activeTiles;
    private LongMap<T> dyingTiles;
    private TileRecycledPool<T> recycledTiles;

    // 濒死区域跟踪(对应活跃视窗范围)
    private int dyingColStart;
    private int dyingColEnd = -1;
    private int dyingRowStart;
    private int dyingRowEnd = -1;

    private int recycledCount;

    private final Callback<T> callback;

    public TileManager(Callback<T> callback) {
        this.callback = callback;
        this.activeTiles = new LongMapOpenHashMap<>();
        this.dyingTiles = new LongMapOpenHashMap<>();
        this.recycledTiles = new TileRecycledPool<>();
    }

    // ========== 生命周期 ==========

    public void in(int column, int row) {
        long id = TileCoreService.getTileId(column, row);
        T tile = dyingTiles.remove(id);
        if (tile == null) {
            int type = callback.getTileType(column, row);
            tile = obtain(type);
            if (tile != null) {
                int width = callback.getTileWidth(column);
                int height = callback.getTileHeight(row);
                tile.column = column;
                tile.row = row;
                tile.width = width;
                tile.height = height;
                callback.onBindTileHolder(tile, column, row);
                tile.onSizeChanged(width, height);
                callback.onTileSizeChanged(tile, column, row, width, height);
            }
        }
        if (tile != null) {
            activeTiles.put(id, tile);
            tile.onInWindow();
            callback.onTileIn(tile, column, row);
        }
    }

    public void out(int column, int row) {
        long id = TileCoreService.getTileId(column, row);
        T tile = activeTiles.remove(id);
        if (tile != null) {
            tile.onOutWindow();
            callback.onTileOut(tile, column, row);
            dyingTiles.put(id, tile);
        }
    }

    public T obtain(int type) {
        T tile = recycledTiles.get(type);
        if (tile != null) {
            recycledCount--;
            return tile;
        }
        tile = callback.onCreateTileHolder(type);
        if (tile != null) tile.type = type;
        return tile;
    }

    public void recycle(T tile) {
        if (tile == null) return;
        recycledTiles.recycle(tile.type, tile);
        tile.onRecycled();
        callback.onTileRecycled(tile,
                tile.column,
                tile.row);
        recycledCount++;
    }

    // ========== 更新操作 ==========

    public void update(int column, int row) {
        if (column >= getDyingLeft() &&
                column <= getDyingRight() &&
                row >= getDyingTop() &&
                row <= getDyingBottom()) {
            callback.beforeLayout();
            rebuildTile(column, row);
            callback.updateUI();
        }
    }

    public void updateRange(int left, int top, int right, int bottom) {
        if (left > right || top > bottom) return;
        int dl = getDyingLeft();
        int dr = getDyingRight();
        int dt = getDyingTop();
        int db = getDyingBottom();

        int intersectLeft = Math.max(left, dl);
        int intersectRight = Math.min(right, dr);
        int intersectTop = Math.max(top, dt);
        int intersectBottom = Math.min(bottom, db);

        if (intersectLeft > intersectRight || intersectTop > intersectBottom) return;

        callback.beforeLayout();
        int c = intersectLeft;
        while (c <= intersectRight) {
            int r = intersectTop;
            while (r <= intersectBottom) {
                rebuildTile(c, r);
                if (r == intersectBottom) break;
                r++;
            }
            if (c == intersectRight) break;
            c++;
        }
        callback.updateUI();
    }

    public void updateColumn(int column) {
        if (column >= getDyingLeft() && column <= getDyingRight()) {
            callback.beforeLayout();
            int row = getDyingTop();
            int end = getDyingBottom();
            while (row <= end) {
                rebuildTile(column, row);
                if (row == end) break;
                row++;
            }
            callback.updateUI();
        }
    }

    public void updateRow(int row) {
        if (row >= getDyingTop() && row <= getDyingBottom()) {
            callback.beforeLayout();
            int column = getDyingLeft();
            int end = getDyingRight();
            while (column <= end) {
                rebuildTile(column, row);
                if (column == end) break;
                column++;
            }
            callback.updateUI();
        }
    }

    private void rebuildTile(int column, int row) {
        long id = TileCoreService.getTileId(column, row);
        LayoutModel model = callback.getLayoutModel();
        T tile;
        if (column >= model.colStart && column <= model.colEnd &&
                row >= model.rowStart && row <= model.rowEnd) {
            // 在活跃区
            tile = activeTiles.get(id);
            if (tile != null) {
                activeTiles.remove(id);
                tile.onOutWindow();
                callback.onTileOut(tile, column, row);
                recycle(tile);
            }
            in(column, row);
        } else {
            // 在濒死区，仅刷新已缓存的瓦片数据,不为 update 预建新瓦片,
            // 否则 updateRange 覆盖整个濒死区时会被填满,导致每帧遍历删除退化为 O(n^2)
            tile = dyingTiles.remove(id);
            if (tile != null) {
                recycle(tile);
                int type = callback.getTileType(column, row);
                T newTile = obtain(type);
                if (newTile != null) {
                    int width = callback.getTileWidth(column);
                    int height = callback.getTileHeight(row);
                    newTile.column = column;
                    newTile.row = row;
                    newTile.width = width;
                    newTile.height = height;
                    callback.onBindTileHolder(newTile, column, row);
                    newTile.onSizeChanged(width, height);
                    callback.onTileSizeChanged(newTile, column, row, width, height);
                    dyingTiles.put(id, newTile);
                }
            }
        }
    }

    // 由 DimenManager 调用,用于同步刷新已存在瓦片的尺寸
    public void resizeTile(int column, int row, int width, int height) {
        long id = TileCoreService.getTileId(column, row);
        T tile = activeTiles.get(id);
        if (tile == null) {
            tile = dyingTiles.get(id);
        }
        if (tile != null) {
            if (width == tile.width &&
                    height == tile.height) {
                return;
            }
            tile.width = width;
            tile.height = height;
            tile.onSizeChanged(width, height);
            callback.onTileSizeChanged(tile, column, row, width, height);
        }
    }

    // ========== 濒死区域 ==========

    // 视窗计算完毕后,清理超出扩展濒死区的瓦片
    public void diffDying(int colStart, int rowStart, int colEnd, int rowEnd) {
        if (dyingColStart == colStart && dyingColEnd == colEnd
                && dyingRowStart == rowStart && dyingRowEnd == rowEnd) {
            return;
        }
        dyingColStart = colStart;
        dyingColEnd = colEnd;
        dyingRowStart = rowStart;
        dyingRowEnd = rowEnd;

        int left = getDyingLeft();
        int top = getDyingTop();
        int right = getDyingRight();
        int bottom = getDyingBottom();

        LongMap.Iterator<T> it = dyingTiles.iterator(true);
        while (it.next()) {
            long id = it.key();
            int c = TileCoreService.getColumn(id);
            int r = TileCoreService.getRow(id);
            if (c < left || c > right || r < top || r > bottom) {
                T tile = it.value();
                it.remove();
                recycle(tile);
            }
        }
    }

    public int getDyingLeft() {
        return dyingColStart > callback.getLeftBound() ? dyingColStart - 1 : callback.getLeftBound();
    }

    public int getDyingTop() {
        return dyingRowStart > callback.getTopBound() ? dyingRowStart - 1 : callback.getTopBound();
    }

    public int getDyingRight() {
        return dyingColEnd < callback.getRightBound() ? dyingColEnd + 1 : callback.getRightBound();
    }

    public int getDyingBottom() {
        return dyingRowEnd < callback.getBottomBound() ? dyingRowEnd + 1 : callback.getBottomBound();
    }

    // ========== 查询 ==========

    public T getActiveTile(int column, int row) {
        return activeTiles.get(TileCoreService.getTileId(column, row));
    }

    public int getActiveTileCount() {
        return activeTiles.size();
    }

    public int getRecycledTileCount() {
        return recycledCount;
    }

    public int getDyingTileCount() {
        return dyingTiles.size();
    }

    public LongMap<T> getDyingTiles() {
        return dyingTiles;
    }

    // ========== 存储替换 ==========

    public void setActiveTiles(LongMap<T> map) {
        if (map == null) throw new IllegalArgumentException("activeTiles map cannot be null");
        if (map == activeTiles) return;
        map.clear();
        for (LongMap.Iterator<T> it = activeTiles.iterator(); it.next(); ) {
            map.put(it.key(), it.value());
        }
        activeTiles.clear();
        activeTiles = map;
    }

    public void setDyingTiles(LongMap<T> map) {
        if (map == null) throw new IllegalArgumentException("dyingTiles map cannot be null");
        if (map == dyingTiles) return;
        map.clear();
        for (LongMap.Iterator<T> it = dyingTiles.iterator(); it.next(); ) {
            map.put(it.key(), it.value());
        }
        dyingTiles.clear();
        dyingTiles = map;
    }

    public void setRecycledTiles(TileRecycledPool<T> map) {
        if (map == null) throw new IllegalArgumentException("recycledTiles map cannot be null");
        if (map == recycledTiles) return;
        map.reset();
        recycledTiles.moveTo(map);
        recycledTiles = map;
    }

    // ========== 清理 ==========

    public void clearAll() {
        // 清理活跃瓦片
        LongMap.Iterator<T> itActive = activeTiles.iterator();
        while (itActive.next()) {
            long id = itActive.key();
            T tile = itActive.value();
            callback.onTileOut(tile, TileCoreService.getColumn(id), TileCoreService.getRow(id));
            if (tile != null) tile.onOutWindow();
            recycle(tile);
        }
        activeTiles.clear();

        // 清理濒死瓦片
        LongMap.Iterator<T> itDying = dyingTiles.iterator();
        while (itDying.next()) {
            recycle(itDying.value());
        }
        dyingTiles.clear();

        // 清理缓存
        recycledTiles.reset();
        recycledCount = 0;

        // 清理状态
        dyingColStart = dyingRowStart = 0;
        dyingColEnd = dyingRowEnd = -1;
    }

    // 由 CoreScheduler.seek 调用:跳转时清理所有瓦片
    public void clearActiveAndDying() {
        LongMap.Iterator<T> itActive = activeTiles.iterator();
        while (itActive.next()) {
            T tile = itActive.value();
            long id = itActive.key();
            tile.onOutWindow();
            callback.onTileOut(tile, TileCoreService.getColumn(id), TileCoreService.getRow(id));
            recycle(tile);
        }
        activeTiles.clear();

        LongMap.Iterator<T> itDying = dyingTiles.iterator();
        while (itDying.next()) {
            recycle(itDying.value());
        }
        dyingTiles.clear();
    }

    // ========== 回调接口 ==========

    public interface Callback<T extends TileCoreService.BaseTileHolder> {

        // 适配器回调
        int getTileType(int column, int row);

        T onCreateTileHolder(int type);

        void onBindTileHolder(T holder, int column, int row);

        void onTileIn(T holder, int column, int row);

        void onTileOut(T holder, int column, int row);

        void onTileRecycled(T holder, int column, int row);

        void onTileSizeChanged(T holder, int column, int row, int width, int height);

        // 尺寸查询(委托给 DimenManager)
        int getTileWidth(int column);

        int getTileHeight(int row);

        // 边界
        int getLeftBound();

        int getTopBound();

        int getRightBound();

        int getBottomBound();

        // 布局状态
        LayoutModel getLayoutModel();

        // 调度回调
        void beforeLayout();

        void updateUI();

    }

}
