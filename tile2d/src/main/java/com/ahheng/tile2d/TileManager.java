package com.ahheng.tile2d;

import com.ahheng.tile2d.tile.TileRecycledPool;
import com.ahheng.tile2d.util.longmap.LongMap;
import com.ahheng.tile2d.util.longmap.LongMapOpenHashMap;
import com.ahheng.tile2d.util.longqueue.LongQueue;
import com.ahheng.tile2d.util.longqueue.LongQueueArrayFIFO;

// 瓦片管理器（跨平台）
// 负责瓦片相关的生命周期、存储、交互
public class TileManager<T extends TileCoreService.BaseTileHolder> {

    private LongMap<T> activeTiles;
    private LongMap<T> dyingTiles;
    private LongMap<T> prefetchTiles;
    private TileRecycledPool<T> recycledTiles;

    // 濒死区域跟踪
    private int dyingColStart;
    private int dyingColEnd = -1;
    private int dyingRowStart;
    private int dyingRowEnd = -1;
    private int dyingExpand = 1; // 濒死区域扩展范围
    private boolean dyingEnabled = true; // 是否启用濒死区

    // 预取区域跟踪，带方向预测
    private LongQueue prefetchQueue;
    private int prefetchColStart;
    private int prefetchColEnd = -1;
    private int prefetchRowStart;
    private int prefetchRowEnd = -1;
    private int prefetchDirX; // 水平运动方向： -1 左， 0 无， 1 右
    private int prefetchDirY; // 垂直运动方向： -1 上， 0 无， 1 下
    private int prefetchExpand = 1; // 预取区域扩展范围
    private boolean prefetchEnabled = true; // 是否启用预取区（默认开启，经全链路验证稳定）
    private int prefetchPerFrame = 8; // 每帧预取数量上限（帧预算，默认 8 匹配二维吞吐）
    private int prefetchQueuePeak; // 预取队列历史峰值（高水位，DebugLayer 观测用）
    private int recycledCount; // 回收池瓦片数量

    private final Callback<T> callback;

    public TileManager(Callback<T> callback) {
        this.callback = callback;
        this.activeTiles = new LongMapOpenHashMap<>();
        this.dyingTiles = new LongMapOpenHashMap<>();
        this.prefetchTiles = new LongMapOpenHashMap<>();
        this.recycledTiles = new TileRecycledPool<>();
        this.prefetchQueue = new LongQueueArrayFIFO();
    }

    // 生命周期

    public void in(int column, int row) {
        long id = TileCoreService.getTileId(column, row);
        T tile = dyingTiles.remove(id);
        if (tile == null && prefetchEnabled) {
            // 命中预取池，已创建已绑定，直接复用
            tile = prefetchTiles.remove(id);
        }
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
            if (dyingEnabled) {
                dyingTiles.put(id, tile);
            } else {
                // 濒死区关闭，离开视窗直接回收
                recycle(tile);
            }
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

    // 更新操作

    public void update(int column, int row) {
        if (column >= getRefreshLeft() &&
                column <= getRefreshRight() &&
                row >= getRefreshTop() &&
                row <= getRefreshBottom()) {
            callback.beforeLayout();
            rebuildTile(column, row);
            callback.updateUI();
        }
    }

    public void updateRange(int left, int top, int right, int bottom) {
        if (left > right || top > bottom) return;
        int dl = getRefreshLeft();
        int dr = getRefreshRight();
        int dt = getRefreshTop();
        int db = getRefreshBottom();

        int intersectLeft = LayoutEngine.max(left, dl);
        int intersectRight = LayoutEngine.min(right, dr);
        int intersectTop = LayoutEngine.max(top, dt);
        int intersectBottom = LayoutEngine.min(bottom, db);

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
        if (column >= getRefreshLeft() && column <= getRefreshRight()) {
            callback.beforeLayout();
            int row = getRefreshTop();
            int end = getRefreshBottom();
            while (row <= end) {
                rebuildTile(column, row);
                if (row == end) break;
                row++;
            }
            callback.updateUI();
        }
    }

    public void updateRow(int row) {
        if (row >= getRefreshTop() && row <= getRefreshBottom()) {
            callback.beforeLayout();
            int column = getRefreshLeft();
            int end = getRefreshRight();
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
            // 在濒死区，仅刷新已缓存的瓦片数据，不为 update 预建新瓦片，
            // 否则 updateRange 覆盖整个濒死区时会被填满，导致每帧遍历删除退化为 O(n^2)
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
            } else if (prefetchEnabled) {
                // 在预取区，直接丢弃回收，不原地重建，
                // 下次预取规划时会重新入队补上，避免 updateRange 覆盖整个预取区退化
                T ptile = prefetchTiles.remove(id);
                if (ptile != null) recycle(ptile);
            }
        }
    }

    // 由 DimenManager 调用，用于同步刷新已存在瓦片的尺寸
    public void resizeTile(int column, int row, int width, int height) {
        long id = TileCoreService.getTileId(column, row);
        T tile = activeTiles.get(id);
        if (tile == null) {
            tile = dyingTiles.get(id);
        }
        if (tile == null) {
            tile = prefetchTiles.get(id);
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

    // 濒死区域

    // 视窗计算完毕后，清理超出扩展濒死区的瓦片
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

    // 从基准值出发沿单方向逐格行走最多 expand 步，被 bound 夹住即停（防越界）
    // forward=true 向右/下(value 递增),false 向左/上(value 递减)
    // 濒死区与预取区的 8 个边界 getter 共用，消除重复
    private static int expandToBound(int value, int expand, int bound, boolean forward) {
        if (forward) {
            while (expand > 0 && value < bound) {
                value++;
                expand--;
            }
        } else {
            while (expand > 0 && value > bound) {
                value--;
                expand--;
            }
        }
        return value;
    }

    public int getDyingLeft() {
        // 关闭时退化为视窗边界
        return dyingEnabled ? expandToBound(dyingColStart, dyingExpand, callback.getLeftBound(), false) : dyingColStart;
    }

    public int getDyingTop() {
        // 关闭时退化为视窗边界
        return dyingEnabled ? expandToBound(dyingRowStart, dyingExpand, callback.getTopBound(), false) : dyingRowStart;
    }

    public int getDyingRight() {
        // 关闭时退化为视窗边界
        return dyingEnabled ? expandToBound(dyingColEnd, dyingExpand, callback.getRightBound(), true) : dyingColEnd;
    }

    public int getDyingBottom() {
        // 关闭时退化为视窗边界
        return dyingEnabled ? expandToBound(dyingRowEnd, dyingExpand, callback.getBottomBound(), true) : dyingRowEnd;
    }

    public int getDyingExpand() {
        return dyingExpand;
    }

    public void setDyingExpand(int expand) {
        if (expand <= 0) return; // 非法参数无响应
        dyingExpand = expand;
    }

    public boolean isDyingEnabled() {
        return dyingEnabled;
    }

    public void setDyingEnabled(boolean enabled) {
        dyingEnabled = enabled;
        if (!enabled) {
            // 关闭时立即回收濒死区内全部瓦片，不留缓冲
            LongMap.Iterator<T> it = dyingTiles.iterator(true);
            while (it.next()) {
                T tile = it.value();
                it.remove();
                recycle(tile);
            }
        }
    }

    // 预取区域

    // 视窗计算完毕后，基于运动方向重新规划预取
    public void diffPrefetch(int colStart, int rowStart, int colEnd, int rowEnd) {
        if (!prefetchEnabled) return;

        // 计算相对上次视窗的位移方向（首帧/重置后区间为空，方向未知）
        int dirX = 0;
        int dirY = 0;
        boolean sameWindow = false;
        if (prefetchColStart <= prefetchColEnd && prefetchRowStart <= prefetchRowEnd) {
            // 有上次记录
            dirX = compareInt(colStart, prefetchColStart);
            dirY = compareInt(rowStart, prefetchRowStart);
            sameWindow = prefetchColStart == colStart && prefetchColEnd == colEnd
                    && prefetchRowStart == rowStart && prefetchRowEnd == rowEnd;
        }

        // 更新锚点与方向
        prefetchColStart = colStart;
        prefetchColEnd = colEnd;
        prefetchRowStart = rowStart;
        prefetchRowEnd = rowEnd;
        prefetchDirX = dirX;
        prefetchDirY = dirY;

        // 视窗完全没动，保留已预取的前方瓦片
        if (sameWindow) return;

        // 按当前方向计算预取矩形（仅朝运动方向扩展，其余三边贴合视窗）
        int pl = getPrefetchLeft();
        int pt = getPrefetchTop();
        int pr = getPrefetchRight();
        int pb = getPrefetchBottom();

        // 淘汰落在新方向矩形之外的预取瓦片
        LongMap.Iterator<T> it = prefetchTiles.iterator(true);
        while (it.next()) {
            long id = it.key();
            int c = TileCoreService.getColumn(id);
            int r = TileCoreService.getRow(id);
            if (c < pl || c > pr || r < pt || r > pb) {
                T tile = it.value();
                it.remove();
                recycle(tile);
            }
        }

        // 只把运动方向前方条带（方向矩形 - 视窗矩形）内未持有的坐标入队
        prefetchQueue.clear();
        // 上条带
        if (pt < rowStart) {
            enqueueRegion(pl, pr, pt, rowStart - 1);
        }
        // 下条带
        if (rowEnd < pb) {
            enqueueRegion(pl, pr, rowEnd + 1, pb);
        }
        // 左条带
        if (pl < colStart) {
            enqueueRegion(pl, colStart - 1, rowStart, rowEnd);
        }
        // 右条带
        if (colEnd < pr) {
            enqueueRegion(colEnd + 1, pr, rowStart, rowEnd);
        }

        // 记录队列历史峰值（高水位，DebugLayer 观测用）
        int queued = prefetchQueue.size();
        if (queued > prefetchQueuePeak) prefetchQueuePeak = queued;
    }

    // 三值比较，跨平台展开
    private static int compareInt(int a, int b) {
        return a < b ? -1 : (a > b ? 1 : 0);
    }

    // 遍历闭区间矩形，把三池均未持有的坐标入队
    private void enqueueRegion(int cStart, int cEnd, int rStart, int rEnd) {
        int c = cStart;
        while (c <= cEnd) {
            int r = rStart;
            while (r <= rEnd) {
                long id = TileCoreService.getTileId(c, r);
                if (!activeTiles.containsKey(id) &&
                    !dyingTiles.containsKey(id) &&
                    !prefetchTiles.containsKey(id)) {
                    prefetchQueue.enqueue(id);
                }
                if (r == rEnd) break;
                r++;
            }
            if (c == cEnd) break;
            c++;
        }
    }

    // 消费预取队列，单次最多创建 prefetchPerFrame 个瓦片，返回队列是否仍有剩余
    // 由渲染端在帧空闲时驱动，把创建绑定成本分摊到多帧。
    public boolean drainPrefetch() {
        if (!prefetchEnabled) return false;
        int count = 0;
        while (prefetchQueue.size() > 0 && count < prefetchPerFrame) {
            long id = prefetchQueue.dequeue();
            prefetchOne(TileCoreService.getColumn(id), TileCoreService.getRow(id));
            count++;
        }
        return prefetchQueue.size() > 0;
    }

    // 获取每帧预取数量上限（帧预算）
    public int getPrefetchPerFrame() {
        return prefetchPerFrame;
    }

    // 设置每帧预取数量上限，非法参数（小于等于 0）无响应
    public void setPrefetchPerFrame(int count) {
        if (count <= 0) return;
        prefetchPerFrame = count;
    }

    // 预取单个瓦片：创建并绑定，但不进活跃区，不触发进窗回调
    private void prefetchOne(int column, int row) {
        long id = TileCoreService.getTileId(column, row);
        if (activeTiles.containsKey(id) ||
            dyingTiles.containsKey(id) ||
            prefetchTiles.containsKey(id)) {
            return;
        }
        int type = callback.getTileType(column, row);
        T tile = obtain(type);
        if (tile == null) return; // 稀疏坐标，正常跳过
        int width = callback.getTileWidth(column);
        int height = callback.getTileHeight(row);
        tile.column = column;
        tile.row = row;
        tile.width = width;
        tile.height = height;
        callback.onBindTileHolder(tile, column, row);
        tile.onSizeChanged(width, height);
        callback.onTileSizeChanged(tile, column, row, width, height);
        prefetchTiles.put(id, tile);
        callback.onTilePrefetched(tile, column, row);
    }

    // 预取矩形边界：仅朝运动方向扩展，其余三边贴合视窗本体

    public int getPrefetchLeft() {
        return prefetchEnabled && prefetchDirX < 0
                ? expandToBound(prefetchColStart, prefetchExpand, callback.getLeftBound(), false)
                : prefetchColStart;
    }

    public int getPrefetchTop() {
        return prefetchEnabled && prefetchDirY < 0
                ? expandToBound(prefetchRowStart, prefetchExpand, callback.getTopBound(), false)
                : prefetchRowStart;
    }

    public int getPrefetchRight() {
        return prefetchEnabled && prefetchDirX > 0
                ? expandToBound(prefetchColEnd, prefetchExpand, callback.getRightBound(), true)
                : prefetchColEnd;
    }

    public int getPrefetchBottom() {
        return prefetchEnabled && prefetchDirY > 0
                ? expandToBound(prefetchRowEnd, prefetchExpand, callback.getBottomBound(), true)
                : prefetchRowEnd;
    }

    public int getPrefetchExpand() {
        return prefetchExpand;
    }

    public void setPrefetchExpand(int expand) {
        if (expand <= 0) return; // 非法参数无响应
        prefetchExpand = expand;
    }

    public boolean isPrefetchEnabled() {
        return prefetchEnabled;
    }

    public void setPrefetchEnabled(boolean enabled) {
        prefetchEnabled = enabled;
        if (!enabled) {
            clearPrefetch();
            prefetchColStart = prefetchRowStart = 0;
            prefetchColEnd = prefetchRowEnd = -1;
            prefetchDirX = prefetchDirY = 0;
        }
    }

    // 清空预取池与队列，回收全部预取瓦片，峰值随预取区生命周期归零
    public void clearPrefetch() {
        LongMap.Iterator<T> it = prefetchTiles.iterator(true);
        while (it.next()) {
            T tile = it.value();
            it.remove();
            recycle(tile);
        }
        prefetchQueue.clear();
        prefetchQueuePeak = 0;
    }

    // 获取预取瓦片个数
    public int getPrefetchTileCount() {
        return prefetchTiles.size();
    }

    // 预取队列历史峰值（高水位，随预取区生命周期归零，无需手动重置）
    public int getPrefetchQueuePeak() {
        return prefetchQueuePeak;
    }

    // 检查预取队列是否还有待消费的坐标
    public boolean hasPrefetchPending() {
        return prefetchQueue.size() > 0;
    }

    public LongMap<T> getPrefetchTiles() {
        return prefetchTiles;
    }

    // 查询

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

    // 存储替换

    public void setActiveTiles(LongMap<T> map) {
        if (map == null) return; // 非法参数无响应
        if (map == activeTiles) return;
        map.clear();
        for (LongMap.Iterator<T> it = activeTiles.iterator(); it.next(); ) {
            map.put(it.key(), it.value());
        }
        activeTiles.clear();
        activeTiles = map;
    }

    public void setDyingTiles(LongMap<T> map) {
        if (map == null) return; // 非法参数无响应
        if (map == dyingTiles) return;
        map.clear();
        for (LongMap.Iterator<T> it = dyingTiles.iterator(); it.next(); ) {
            map.put(it.key(), it.value());
        }
        dyingTiles.clear();
        dyingTiles = map;
    }

    public void setPrefetchTiles(LongMap<T> map) {
        if (map == null) return; // 非法参数无响应
        if (map == prefetchTiles) return;
        map.clear();
        for (LongMap.Iterator<T> it = prefetchTiles.iterator(); it.next(); ) {
            map.put(it.key(), it.value());
        }
        prefetchTiles.clear();
        prefetchTiles = map;
    }

    public void setPrefetchQueue(LongQueue queue) {
        if (queue == null) return; // 非法参数无响应
        if (queue == prefetchQueue) return;
        queue.clear();
        // 迁移旧队列中待预取的坐标，替换容器不能丢失未消费的预取任务
        while (prefetchQueue.size() > 0) {
            queue.enqueue(prefetchQueue.dequeue());
        }
        prefetchQueue = queue;
    }

    public void setRecycledTiles(TileRecycledPool<T> map) {
        if (map == null) return; // 非法参数无响应
        if (map == recycledTiles) return;
        map.reset();
        recycledTiles.moveTo(map);
        recycledTiles = map;
    }

    // 清理

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

        // 清理预取瓦片
        clearPrefetch();

        // 清理缓存
        recycledTiles.reset();
        recycledCount = 0;

        // 清理状态
        dyingColStart = dyingRowStart = 0;
        dyingColEnd = dyingRowEnd = -1;
        prefetchColStart = prefetchRowStart = 0;
        prefetchColEnd = prefetchRowEnd = -1;
        prefetchDirX = prefetchDirY = 0;
    }

    // 由 CoreScheduler.seek 调用：跳转时清理所有瓦片
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

        // 跳转后预取目标全部失效，清空预取池并重置跟踪状态（区间置空即无上次记录）
        clearPrefetch();
        prefetchColStart = prefetchRowStart = 0;
        prefetchColEnd = prefetchRowEnd = -1;
        prefetchDirX = prefetchDirY = 0;
    }

    // 刷新范围边界：濒死区与预取区的外接矩形
    // update 系列据此判断坐标是否需要刷新，覆盖两个分支
    private int getRefreshLeft() {
        return prefetchEnabled ? LayoutEngine.min(getDyingLeft(), getPrefetchLeft()) : getDyingLeft();
    }

    private int getRefreshTop() {
        return prefetchEnabled ? LayoutEngine.min(getDyingTop(), getPrefetchTop()) : getDyingTop();
    }

    private int getRefreshRight() {
        return prefetchEnabled ? LayoutEngine.max(getDyingRight(), getPrefetchRight()) : getDyingRight();
    }

    private int getRefreshBottom() {
        return prefetchEnabled ? LayoutEngine.max(getDyingBottom(), getPrefetchBottom()) : getDyingBottom();
    }

    // 回调接口

    public interface Callback<T extends TileCoreService.BaseTileHolder> {

        // 适配器回调
        int getTileType(int column, int row);

        T onCreateTileHolder(int type);

        void onBindTileHolder(T holder, int column, int row);

        void onTileIn(T holder, int column, int row);

        void onTileOut(T holder, int column, int row);

        void onTileRecycled(T holder, int column, int row);

        void onTileSizeChanged(T holder, int column, int row, int width, int height);

        void onTilePrefetched(T holder, int column, int row);

        // 尺寸查询（委托给 DimenManager）
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