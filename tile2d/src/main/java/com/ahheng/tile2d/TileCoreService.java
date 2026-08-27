package com.ahheng.tile2d;

import android.content.Context;
import android.graphics.Rect;
import android.view.MotionEvent;

import com.ahheng.tile2d.dimen.TileDimenProvider;
import com.ahheng.tile2d.tile.TileRecycledPool;
import com.ahheng.tile2d.util.intintmap.IntIntMap;
import com.ahheng.tile2d.util.longmap.LongMap;
import com.ahheng.tile2d.util.longqueue.LongQueue;
import com.ahheng.tile2d.util.time.TimeProvider;

// 核心调度器(轻量中央控制器)
// 关联布局引擎、瓦片管理器、尺寸管理器、事件处理器、渲染交互端
public class TileCoreService<T extends TileCoreService.BaseTileHolder> implements
        LayoutEngine.BoundaryInterface,
        LayoutEngine.WindowInterface,
        TileManager.Callback<T>,
        DimenManager.Callback,
        EventHandler.Callback {

    private final CoreInterface<T> coreInterface;
    private final LayoutEngine layoutEngine;
    private final TileManager<T> tileManager;
    private final DimenManager dimenManager;
    private final EventHandler eventHandler;

    private final Rect bounds = new Rect();

    // 调试统计
    private boolean isDebugMode;
    private TimeProvider timeProvider;
    private long startBindTime;
    private long bindTime;

    public TileCoreService(Context context, CoreInterface<T> coreInterface) {
        this.coreInterface = coreInterface;
        this.layoutEngine = new LayoutEngine(this, this);
        this.tileManager = new TileManager<>(this);
        this.dimenManager = new DimenManager(this);
        this.eventHandler = new EventHandler(context, this);
    }

    // 布局引擎的边界接口

    @Override
    public int getLeftBound() {
        return coreInterface.getLeftBound();
    }

    @Override
    public int getTopBound() {
        return coreInterface.getTopBound();
    }

    @Override
    public int getRightBound() {
        return coreInterface.getRightBound();
    }

    @Override
    public int getBottomBound() {
        return coreInterface.getBottomBound();
    }

    // 布局引擎的窗口接口

    @Override
    public void in(int column, int row) {
        tileManager.in(column, row);
    }

    @Override
    public void out(int column, int row) {
        tileManager.out(column, row);
    }

    @Override
    public void onWindowCalculated(int colStart, int rowStart, int colEnd, int rowEnd) {
        // 核心服务的统计计时仍依赖 debug 开关,时钟来源为时间提供器
        if (isDebugMode && timeProvider != null) {
            startBindTime = timeProvider.cpuNanoTime();
        }
        tileManager.diffDying(colStart, rowStart, colEnd, rowEnd);
        // 预取规划:仅淘汰越界瓦片并把环带坐标入队,不在此处创建瓦片,
        // 真正的创建绑定由渲染端调 drainPrefetch 分摊到帧空闲
        tileManager.diffPrefetch(colStart, rowStart, colEnd, rowEnd);
    }

    @Override
    public int getColWidth(int column) {
        return dimenManager.getTileWidth(column);
    }

    @Override
    public int getRowHeight(int row) {
        return dimenManager.getTileHeight(row);
    }

    // 瓦片管理器的回调接口

    @Override
    public int getTileType(int column, int row) {
        return coreInterface.getTileType(column, row);
    }

    @Override
    public T onCreateTileHolder(int type) {
        return coreInterface.onCreateTileHolder(type);
    }

    @Override
    public void onBindTileHolder(T holder, int column, int row) {
        coreInterface.onBindTileHolder(holder, column, row);
    }

    @Override
    public void onTileIn(T holder, int column, int row) {
        coreInterface.onTileIn(holder, column, row);
    }

    @Override
    public void onTileOut(T holder, int column, int row) {
        coreInterface.onTileOut(holder, column, row);
    }

    @Override
    public void onTileRecycled(T holder, int column, int row) {
        coreInterface.onTileRecycled(holder, column, row);
    }

    @Override
    public void onTileSizeChanged(T holder, int column, int row, int width, int height) {
        coreInterface.onTileSizeChanged(holder, column, row, width, height);
    }

    @Override
    public void onTilePrefetched(T holder, int column, int row) {
        coreInterface.onTilePrefetched(holder, column, row);
    }

    @Override
    public int getTileWidth(int column) {
        return dimenManager.getTileWidth(column);
    }

    @Override
    public int getTileHeight(int row) {
        return dimenManager.getTileHeight(row);
    }

    @Override
    public LayoutModel getLayoutModel() {
        return layoutEngine.getLayoutModel();
    }

    @Override
    public void beforeLayout() {
        coreInterface.beforeLayout();
    }

    @Override
    public void updateUI() {
        if (isDebugMode && timeProvider != null && startBindTime != 0) {
            bindTime = timeProvider.cpuNanoTime() - startBindTime;
            startBindTime = 0;
        }
        coreInterface.updateUI();
    }

    // 尺寸管理器的回调接口

    @Override
    public boolean isEmpty() {
        return layoutEngine.isEmpty();
    }

    @Override
    public int getDyingLeft() {
        return tileManager.getDyingLeft();
    }

    @Override
    public int getDyingTop() {
        return tileManager.getDyingTop();
    }

    @Override
    public int getDyingRight() {
        return tileManager.getDyingRight();
    }

    @Override
    public int getDyingBottom() {
        return tileManager.getDyingBottom();
    }

    public int getDyingExpand() {
        return tileManager.getDyingExpand();
    }

    public void setDyingExpand(int expand) {
        tileManager.setDyingExpand(expand);
    }

    public boolean isDyingEnabled() {
        return tileManager.isDyingEnabled();
    }

    public void setDyingEnabled(boolean enabled) {
        tileManager.setDyingEnabled(enabled);
    }

    // 预取区 API

    public int getPrefetchExpand() {
        return tileManager.getPrefetchExpand();
    }

    public void setPrefetchExpand(int expand) {
        tileManager.setPrefetchExpand(expand);
    }

    public int getPrefetchLimit() {
        return tileManager.getPrefetchLimit();
    }

    public void setPrefetchLimit(int limit) {
        tileManager.setPrefetchLimit(limit);
    }

    public boolean isPrefetchEnabled() {
        return tileManager.isPrefetchEnabled();
    }

    public void setPrefetchEnabled(boolean enabled) {
        tileManager.setPrefetchEnabled(enabled);
    }

    // 消费预取队列,由渲染端在帧空闲时驱动,返回队列是否仍有剩余
    public boolean drainPrefetch(int maxCount) {
        return tileManager.drainPrefetch(maxCount);
    }

    @Override
    public void resizeTile(int column, int row, int width, int height) {
        tileManager.resizeTile(column, row, width, height);
    }

    @Override
    public void updateWidth(int column, int oldWidth, int newWidth, int gravity) {
        layoutEngine.updateWidth(column, oldWidth, newWidth, gravity);
    }

    @Override
    public void updateHeight(int row, int oldHeight, int newHeight, int gravity) {
        layoutEngine.updateHeight(row, oldHeight, newHeight, gravity);
    }

    @Override
    public void updateSize(int column, int oldWidth, int newWidth, int hGravity,
                           int row, int oldHeight, int newHeight, int vGravity) {
        layoutEngine.updateSize(column, oldWidth, newWidth, hGravity, row, oldHeight, newHeight, vGravity);
    }

    // 事件处理器的回调接口

    @Override
    public boolean isHorizontalScrollEnabled() {
        return layoutEngine.isHorizontalScrollEnabled();
    }

    @Override
    public boolean isVerticalScrollEnabled() {
        return layoutEngine.isVerticalScrollEnabled();
    }

    @Override
    public void sync(float dx, float dy) {
        coreInterface.beforeLayout();
        layoutEngine.sync(dx, dy);
        updateUI();
    }

    // 公共 API

    public void setHorizontalScrollEnabled(boolean enabled) {
        layoutEngine.setHorizontalScrollEnabled(enabled);
    }

    public void setVerticalScrollEnabled(boolean enabled) {
        layoutEngine.setVerticalScrollEnabled(enabled);
    }

    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        eventHandler.requestDisallowInterceptTouchEvent(disallowIntercept);
    }

    public boolean isInteractingWithView() {
        return eventHandler.isInteractingWithView();
    }

    public void handleTouchEvent(MotionEvent event) {
        eventHandler.handleTouchEvent(event);
    }

    public void computeScroll() {
        eventHandler.computeScroll();
    }

    public void resetAnimator() {
        eventHandler.resetAnimator();
    }

    public void seek(int column, int row, float offsetX, float offsetY) {
        if (isEmpty()) return;
        tileManager.clearActiveAndDying();
        coreInterface.beforeLayout();
        layoutEngine.seek(column, row, offsetX, offsetY);
        updateUI();
    }

    public void snap() {
        if (isEmpty()) {
            return;
        }
        LayoutModel model = layoutEngine.getLayoutModel();
        int left = coreInterface.getLeftBound();
        int top = coreInterface.getTopBound();
        int right = coreInterface.getRightBound();
        int bottom = coreInterface.getBottomBound();

        if (model.colStart >= left &&
                model.colEnd <= right &&
                model.rowStart >= top &&
                model.rowEnd <= bottom) {
            return;
        }
        int column = Math.max(left, Math.min(model.colStart, right));
        int row = Math.max(top, Math.min(model.rowStart, bottom));
        seek(column, row, 0, 0);
    }

    public void update(int column, int row) {
        tileManager.update(column, row);
    }

    public void updateRange(int left, int top, int right, int bottom) {
        tileManager.updateRange(left, top, right, bottom);
    }

    public void updateColumn(int column) {
        tileManager.updateColumn(column);
    }

    public void updateRow(int row) {
        tileManager.updateRow(row);
    }

    public void updateAll() {
        LayoutModel model = layoutEngine.getLayoutModel();
        seek(model.colStart, model.rowStart, model.offsetX, model.offsetY);
    }

    public void setTileWidth(int column, int width, int gravity) {
        dimenManager.setTileWidth(column, width, gravity);
    }

    public void setTileHeight(int row, int height, int gravity) {
        dimenManager.setTileHeight(row, height, gravity);
    }

    public void setTileSize(int column, int width, int hGravity, int row, int height, int vGravity) {
        dimenManager.setTileSize(column, width, hGravity, row, height, vGravity);
    }

    public void deleteTileWidth(int column, int gravity) {
        dimenManager.deleteTileWidth(column, gravity);
    }

    public void deleteTileHeight(int row, int gravity) {
        dimenManager.deleteTileHeight(row, gravity);
    }

    // 从视窗起始锚点开始计算指定列相对于视窗的 X 坐标，包含渲染端的左内边距
    public float getTileX(int column) {
        LayoutModel model = layoutEngine.getLayoutModel();
        float x = bounds.left + model.offsetX;
        int c = model.colStart;
        while (c < column) {
            x += dimenManager.getTileWidth(c);
            c++;
        }
        while (c > column) {
            c--;
            x -= dimenManager.getTileWidth(c);
        }
        return x;
    }

    // 从视窗起始锚点开始计算指定行相对于视窗的 Y 坐标，包含渲染端的上内边距
    public float getTileY(int row) {
        LayoutModel model = layoutEngine.getLayoutModel();
        float y = bounds.top + model.offsetY;
        int r = model.rowStart;
        while (r < row) {
            y += dimenManager.getTileHeight(r);
            r++;
        }
        while (r > row) {
            r--;
            y -= dimenManager.getTileHeight(r);
        }
        return y;
    }

    // 从视窗起始锚点开始查找包含指定 X 坐标的的列索引，参考原点是渲染端的原点
    public int findColumn(float x) {
        LayoutModel model = layoutEngine.getLayoutModel();
        int leftBound = coreInterface.getLeftBound();
        int rightBound = coreInterface.getRightBound();
        int col = model.colStart;

        if (col > rightBound) return leftBound;

        float currX = bounds.left + model.offsetX;
        while (col > leftBound && x < currX) {
            col--;
            currX -= dimenManager.getTileWidth(col);
        }
        while (col < rightBound && x >= currX + dimenManager.getTileWidth(col)) {
            currX += dimenManager.getTileWidth(col);
            col++;
        }
        return col;
    }

    // 从视窗起始锚点开始查找包含指定 Y 坐标的的行索引，参考原点是渲染端的原点
    public int findRow(float y) {
        LayoutModel model = layoutEngine.getLayoutModel();
        int topBound = coreInterface.getTopBound();
        int bottomBound = coreInterface.getBottomBound();
        int row = model.rowStart;

        if (row > bottomBound) return topBound;

        float currY = bounds.top + model.offsetY;
        while (row > topBound && y < currY) {
            row--;
            currY -= dimenManager.getTileHeight(row);
        }
        while (row < bottomBound && y >= currY + dimenManager.getTileHeight(row)) {
            currY += dimenManager.getTileHeight(row);
            row++;
        }
        return row;
    }

    public void reset() {
        tileManager.clearAll();
        dimenManager.clear();
        eventHandler.reset();
        layoutEngine.reset();
        startBindTime = 0;
        bindTime = 0;
    }

    public TileDimenProvider getDimenProvider() {
        return dimenManager.getDimenProvider();
    }

    public void setDimenProvider(TileDimenProvider dimenProvider) {
        dimenManager.setDimenProvider(dimenProvider);
    }

    public Rect getBounds() {
        return bounds;
    }

    public void setBounds(int left, int top, int right, int bottom) {
        bounds.set(left, top, right, bottom);
        layoutEngine.setWindowWidth(bounds.width());
        layoutEngine.setWindowHeight(bounds.height());
    }

    public int getDefaultTileWidth() {
        return dimenManager.getDefaultTileWidth();
    }

    public int getDefaultTileHeight() {
        return dimenManager.getDefaultTileHeight();
    }

    public void setDefaultTileWidth(int width) {
        dimenManager.setDefaultTileWidth(width);
    }

    public void setDefaultTileHeight(int height) {
        dimenManager.setDefaultTileHeight(height);
    }

    public LongMap<T> getDyingTiles() {
        return tileManager.getDyingTiles();
    }

    public int getActiveTileCount() {
        return tileManager.getActiveTileCount();
    }

    public int getRecycledTileCount() {
        return tileManager.getRecycledTileCount();
    }

    public int getDyingTileCount() {
        return tileManager.getDyingTileCount();
    }

    public int getPrefetchTileCount() {
        return tileManager.getPrefetchTileCount();
    }

    public LongMap<T> getPrefetchTiles() {
        return tileManager.getPrefetchTiles();
    }

    public T getActiveTile(int column, int row) {
        return tileManager.getActiveTile(column, row);
    }

    public long getBindTime() {
        return bindTime;
    }

    public boolean isAtLeftBound() {
        return !isEmpty() && layoutEngine.isAtLeftBound();
    }

    public boolean isAtTopBound() {
        return !isEmpty() && layoutEngine.isAtTopBound();
    }

    public boolean isAtRightBound() {
        return !isEmpty() && layoutEngine.isAtRightBound();
    }

    public boolean isAtBottomBound() {
        return !isEmpty() && layoutEngine.isAtBottomBound();
    }

    public void setActiveTiles(LongMap<T> map) {
        tileManager.setActiveTiles(map);
    }

    public void setDyingTiles(LongMap<T> map) {
        tileManager.setDyingTiles(map);
    }

    public void setPrefetchTiles(LongMap<T> map) {
        tileManager.setPrefetchTiles(map);
    }

    public void setPrefetchQueue(LongQueue queue) {
        tileManager.setPrefetchQueue(queue);
    }

    public void setWidths(IntIntMap map) {
        dimenManager.setWidths(map);
    }

    public void setHeights(IntIntMap map) {
        dimenManager.setHeights(map);
    }

    public void setRecycledTiles(TileRecycledPool<T> map) {
        tileManager.setRecycledTiles(map);
    }

    // 子模块访问器(供渲染层扩展使用)
    public LayoutEngine getLayoutEngine() {
        return layoutEngine;
    }

    public TileManager<T> getTileManager() {
        return tileManager;
    }

    public DimenManager getDimenManager() {
        return dimenManager;
    }

    public EventHandler getEventHandler() {
        return eventHandler;
    }

    public boolean isDebugMode() {
        return isDebugMode;
    }

    public void setDebugMode(boolean isDebugMode) {
        this.isDebugMode = isDebugMode;
        layoutEngine.setTimeProvider(isDebugMode ? timeProvider : null);
    }

    public void setTimeProvider(TimeProvider provider) {
        this.timeProvider = provider;
        layoutEngine.setTimeProvider(isDebugMode ? provider : null);
    }

    // 静态工具方法

    // 从瓦片坐标生成唯一瓦片 ID
    public static long getTileId(int column, int row) {
        return ((long) column << 32) | (row & 0xFFFFFFFFL);
    }

    // 从瓦片 ID 提取列索引
    public static int getColumn(long id) {
        return (int) (id >> 32);
    }

    // 从瓦片 ID 提取行索引
    public static int getRow(long id) {
        return (int) (id & 0xFFFFFFFFL);
    }

    // 基础瓦片持有者

    public static class BaseTileHolder {

        // 包级私有，供同包的 TileManager 直接访问以设置瓦片坐标与尺寸
        int type;
        int column;
        int row;
        int width;
        int height;

        public void onRecycled() {
        }

        public void onInWindow() {
        }

        public void onOutWindow() {
        }

        public void onSizeChanged(int width, int height) {
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }

        public int getColumn() {
            return column;
        }

        public int getRow() {
            return row;
        }

        public int getType() {
            return type;
        }

    }

    // 渲染交互端接口

    public interface CoreInterface<T extends BaseTileHolder> {

        void beforeLayout();

        void updateUI();

        void onTileIn(T holder, int column, int row);

        void onTileOut(T holder, int column, int row);
        void onTileRecycled(T holder, int column, int row);

        void onTileSizeChanged(T holder, int column, int row, int width, int height);

        void onTilePrefetched(T holder, int column, int row);


        int getLeftBound();

        int getTopBound();

        int getRightBound();

        int getBottomBound();

        T onCreateTileHolder(int type);

        void onBindTileHolder(T holder, int column, int row);

        int getTileType(int column, int row);

    }

}
