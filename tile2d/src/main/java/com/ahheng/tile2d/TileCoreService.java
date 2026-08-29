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

// 核心调度器（轻量中央控制器）
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

    // 缩放
    private float scaleFactor = 1;
    private float minScaleFactor = 0.5f;
    private float maxScaleFactor = 2;

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
        // 核心服务的统计计时仍依赖 debug 开关，时钟来源为时间提供器
        if (isDebugMode && timeProvider != null) {
            startBindTime = timeProvider.cpuNanoTime();
        }
        tileManager.diffDying(colStart, rowStart, colEnd, rowEnd);
        // 预取规划：仅淘汰越界瓦片并把环带坐标入队，不在此处创建瓦片，
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

    // 获取指定列的宽度。坐标系：原始坐标系
    @Override
    public int getTileWidth(int column) {
        return dimenManager.getTileWidth(column);
    }

    // 获取指定行的高度。坐标系：原始坐标系
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
        if (expand <= 0) throw new IllegalArgumentException("濒死区扩展范围必须大于 0");
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
        if (expand <= 0) throw new IllegalArgumentException("预取区扩展范围必须大于 0");
        tileManager.setPrefetchExpand(expand);
    }

    public boolean isPrefetchEnabled() {
        return tileManager.isPrefetchEnabled();
    }

    public void setPrefetchEnabled(boolean enabled) {
        tileManager.setPrefetchEnabled(enabled);
    }

    // 获取每帧预取数量上限（帧预算）
    public int getPrefetchPerFrame() {
        return tileManager.getPrefetchPerFrame();
    }

    // 设置每帧预取数量上限，非法参数（小于等于 0）无响应
    public void setPrefetchPerFrame(int count) {
        if (count <= 0) return;
        tileManager.setPrefetchPerFrame(count);
    }

    // 消费预取队列，由渲染端在帧空闲时驱动，返回队列是否仍有剩余
    public boolean drainPrefetch() {
        return tileManager.drainPrefetch();
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

    // 水平滚动开关
    public void setHorizontalScrollEnabled(boolean enabled) {
        layoutEngine.setHorizontalScrollEnabled(enabled);
    }

    // 垂直滚动开关
    public void setVerticalScrollEnabled(boolean enabled) {
        layoutEngine.setVerticalScrollEnabled(enabled);
    }

    // 禁止父容器拦截触摸(由宿主在触摸时转发)
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        eventHandler.requestDisallowInterceptTouchEvent(disallowIntercept);
    }

    // 是否正在与视图交互(拖动/甩动中)
    public boolean isInteractingWithView() {
        return eventHandler.isInteractingWithView();
    }

    // 触摸事件分发(委托给事件处理器)
    public void handleTouchEvent(MotionEvent event) {
        eventHandler.handleTouchEvent(event);
    }

    // 驱动惯性滚动(委托给事件处理器)
    public void computeScroll() {
        eventHandler.computeScroll();
    }

    // 中止进行中的惯性滚动
    public void resetAnimator() {
        eventHandler.resetAnimator();
    }

    // 跳转到指定锚点并重排全部瓦片
    public void seek(int column, int row, float offsetX, float offsetY) {
        if (isEmpty()) return;
        tileManager.clearActiveAndDying();
        coreInterface.beforeLayout();
        layoutEngine.seek(column, row, offsetX, offsetY);
        updateUI();
    }

    // 将视窗吸附回内容边界内(越界时跳到最近合法锚点)
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

    // 刷新单个瓦片数据
    public void update(int column, int row) {
        tileManager.update(column, row);
    }

    // 刷新矩形范围内的全部瓦片
    public void updateRange(int left, int top, int right, int bottom) {
        tileManager.updateRange(left, top, right, bottom);
    }

    // 刷新整列瓦片
    public void updateColumn(int column) {
        tileManager.updateColumn(column);
    }

    // 刷新整行瓦片
    public void updateRow(int row) {
        tileManager.updateRow(row);
    }

    // 全量刷新(按当前视窗锚点重排)
    public void updateAll() {
        LayoutModel model = layoutEngine.getLayoutModel();
        seek(model.colStart, model.rowStart, model.offsetX, model.offsetY);
    }

    // 设置列宽并同步刷新(委托给尺寸管理器)。坐标系：原始坐标系
    public void setTileWidth(int column, int width, int gravity) {
        if (width <= 0) throw new IllegalArgumentException("宽度必须大于 0");
        if (column < getLeftBound() || column > getRightBound())
            throw new IndexOutOfBoundsException("列索引 " + column + " 不在 [" + getLeftBound() + "," + getRightBound() + "] 范围内");
        dimenManager.setTileWidth(column, width, gravity);
    }

    // 设置行高并同步刷新(委托给尺寸管理器)。坐标系：原始坐标系
    public void setTileHeight(int row, int height, int gravity) {
        if (height <= 0) throw new IllegalArgumentException("高度必须大于 0");
        if (row < getTopBound() || row > getBottomBound())
            throw new IndexOutOfBoundsException("行索引 " + row + " 不在 [" + getTopBound() + "," + getBottomBound() + "] 范围内");
        dimenManager.setTileHeight(row, height, gravity);
    }

    // 同时设置列宽与行高(委托给尺寸管理器)。坐标系：原始坐标系
    public void setTileSize(int column, int width, int hGravity, int row, int height, int vGravity) {
        if (width <= 0) throw new IllegalArgumentException("宽度必须大于 0");
        if (height <= 0) throw new IllegalArgumentException("高度必须大于 0");
        if (column < getLeftBound() || column > getRightBound())
            throw new IndexOutOfBoundsException("列索引 " + column + " 不在 [" + getLeftBound() + "," + getRightBound() + "] 范围内");
        if (row < getTopBound() || row > getBottomBound())
            throw new IndexOutOfBoundsException("行索引 " + row + " 不在 [" + getTopBound() + "," + getBottomBound() + "] 范围内");
        dimenManager.setTileSize(column, width, hGravity, row, height, vGravity);
    }

    // 删除列宽记录,回退默认值
    public void deleteTileWidth(int column, int gravity) {
        dimenManager.deleteTileWidth(column, gravity);
    }

    // 删除行高记录,回退默认值
    public void deleteTileHeight(int row, int gravity) {
        dimenManager.deleteTileHeight(row, gravity);
    }

    // 从视窗起始锚点开始计算指定列相对于视窗的 X 坐标，包含渲染端的左内边距。坐标系：屏幕坐标系
    public float getTileX(int column) {
        LayoutModel model = layoutEngine.getLayoutModel();
        float x = bounds.left + (model.offsetX * scaleFactor);
        int c = model.colStart;
        while (c < column) {
            x += dimenManager.getTileWidth(c) * scaleFactor;
            c++;
        }
        while (c > column) {
            c--;
            x -= dimenManager.getTileWidth(c) * scaleFactor;
        }
        return x;
    }

    // 从视窗起始锚点开始计算指定行相对于视窗的 Y 坐标，包含渲染端的上内边距。坐标系：屏幕坐标系
    public float getTileY(int row) {
        LayoutModel model = layoutEngine.getLayoutModel();
        float y = bounds.top + (model.offsetY * scaleFactor);
        int r = model.rowStart;
        while (r < row) {
            y += dimenManager.getTileHeight(r) * scaleFactor;
            r++;
        }
        while (r > row) {
            r--;
            y -= dimenManager.getTileHeight(r) * scaleFactor;
        }
        return y;
    }

    // 从视窗起始锚点开始查找包含指定 X 坐标的的列索引，参考原点是渲染端的原点。坐标系：屏幕坐标系
    public int findColumn(float x) {
        LayoutModel model = layoutEngine.getLayoutModel();
        int leftBound = coreInterface.getLeftBound();
        int rightBound = coreInterface.getRightBound();
        int col = model.colStart;

        if (col > rightBound) return leftBound;

        float currX = bounds.left + (model.offsetX * scaleFactor);
        while (col > leftBound && x < currX) {
            col--;
            currX -= dimenManager.getTileWidth(col) * scaleFactor;
        }
        while (col < rightBound && x >= currX + dimenManager.getTileWidth(col) * scaleFactor) {
            currX += dimenManager.getTileWidth(col) * scaleFactor;
            col++;
        }
        return col;
    }

    // 从视窗起始锚点开始查找包含指定 Y 坐标的的行索引，参考原点是渲染端的原点。坐标系：屏幕坐标系
    public int findRow(float y) {
        LayoutModel model = layoutEngine.getLayoutModel();
        int topBound = coreInterface.getTopBound();
        int bottomBound = coreInterface.getBottomBound();
        int row = model.rowStart;

        if (row > bottomBound) return topBound;

        float currY = bounds.top + (model.offsetY * scaleFactor);
        while (row > topBound && y < currY) {
            row--;
            currY -= dimenManager.getTileHeight(row) * scaleFactor;
        }
        while (row < bottomBound && y >= currY + dimenManager.getTileHeight(row) * scaleFactor) {
            currY += dimenManager.getTileHeight(row) * scaleFactor;
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

    private void updateWindowSize() {
    	layoutEngine.setWindowWidth(Math.round(bounds.width() / scaleFactor));
        layoutEngine.setWindowHeight(Math.round(bounds.height() / scaleFactor));
    }

    public float getScaleFactor() {
    	return scaleFactor;
    }

    public void zoom(float scale, float focusX, float focusY, float dx, float dy) {
        if (!(scale > 0)) throw new IllegalArgumentException("缩放因子必须大于0");
        float old = scaleFactor;
    	scaleFactor = Math.max(minScaleFactor, Math.min(maxScaleFactor, scale));
        if (old == scaleFactor && dx == 0 && dy == 0) return;
        LayoutModel model = getLayoutModel();
        updateWindowSize();
        float offsetX = model.offsetX + (focusX - bounds.left) * (1f / scaleFactor - 1f / old) + dx / scaleFactor;
        float offsetY = model.offsetY + (focusY - bounds.top) * (1f / scaleFactor - 1f / old) + dy / scaleFactor;
        seek(model.colStart, model.rowStart, offsetX, offsetY);
    }

    public float getMinScaleFactor() {
    	return minScaleFactor;
    }

    public void setMinScaleFactor(float scale) {
        if (!(scale > 0)) throw new IllegalArgumentException("最小缩放因子必须大于0");
    	if (scale > maxScaleFactor) throw new IllegalArgumentException("最小缩放因子不能大于最大缩放因子：" + maxScaleFactor);
        minScaleFactor = scale;
        zoom(scaleFactor, 0, 0, 0, 0);
    }

    public float getMaxScaleFactor() {
    	return maxScaleFactor;
    }

    public void setMaxScaleFactor(float scale) {
        if (!(scale > 0)) throw new IllegalArgumentException("最大缩放因子必须大于0");
    	if (scale < minScaleFactor) throw new IllegalArgumentException("最大缩放因子不能小于最小缩放因子：" + minScaleFactor);
        maxScaleFactor = scale;
        zoom(scaleFactor, 0, 0, 0, 0);
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
        updateWindowSize();
    }

    public int getDefaultTileWidth() {
        return dimenManager.getDefaultTileWidth();
    }

    public int getDefaultTileHeight() {
        return dimenManager.getDefaultTileHeight();
    }

    public void setDefaultTileWidth(int width) {
        if (width <= 0) throw new IllegalArgumentException("宽度必须大于 0");
        dimenManager.setDefaultTileWidth(width);
    }

    public void setDefaultTileHeight(int height) {
        if (height <= 0) throw new IllegalArgumentException("高度必须大于 0");
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

    // 调试观测：预取队列历史峰值（高水位，随预取区生命周期归零，无需手动重置）
    public int getPrefetchQueuePeak() {
        return tileManager.getPrefetchQueuePeak();
    }

    // 预取队列是否还有待消费的坐标（帧回调继续条件之一：debug 开启或队列非空）
    public boolean hasPrefetchPending() {
        return tileManager.hasPrefetchPending();
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
        if (map == null) throw new IllegalArgumentException("活跃区存储不能设置为空");
        tileManager.setActiveTiles(map);
    }

    public void setDyingTiles(LongMap<T> map) {
        if (map == null) throw new IllegalArgumentException("濒死区存储不能设置为空");
        tileManager.setDyingTiles(map);
    }

    public void setPrefetchTiles(LongMap<T> map) {
        if (map == null) throw new IllegalArgumentException("预取区存储不能设置为空");
        tileManager.setPrefetchTiles(map);
    }

    public void setPrefetchQueue(LongQueue queue) {
        if (queue == null) throw new IllegalArgumentException("预取队列不能设置为空");
        tileManager.setPrefetchQueue(queue);
    }

    public void setWidths(IntIntMap map) {
        if (map == null) throw new IllegalArgumentException("列宽存储不能设置为空");
        dimenManager.setWidths(map);
    }

    public void setHeights(IntIntMap map) {
        if (map == null) throw new IllegalArgumentException("行高存储不能设置为空");
        dimenManager.setHeights(map);
    }

    public void setRecycledTiles(TileRecycledPool<T> map) {
        if (map == null) throw new IllegalArgumentException("回收池存储不能设置为空");
        tileManager.setRecycledTiles(map);
    }

    // 子模块访问器（供渲染层扩展使用）
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
