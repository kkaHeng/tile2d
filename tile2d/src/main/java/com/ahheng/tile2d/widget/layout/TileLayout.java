package com.ahheng.tile2d.widget.layout;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import com.ahheng.tile2d.LayoutEngine;
import com.ahheng.tile2d.LayoutModel;
import com.ahheng.tile2d.TileCoreService;
import com.ahheng.tile2d.dimen.TileDimenProvider;
import com.ahheng.tile2d.tile.TileEventListener;
import com.ahheng.tile2d.tile.TileAdapter;
import com.ahheng.tile2d.tile.TileRecycledPool;
import com.ahheng.tile2d.util.time.DefaultTimeProvider;
import com.ahheng.tile2d.util.intintmap.IntIntMap;
import com.ahheng.tile2d.util.longmap.LongMap;
import com.ahheng.tile2d.widget.debug.DebugLayer;

// 瓦片布局视图(ViewGroup 渲染端)
// 以真实子 View 承载瓦片,配合核心调度器完成滚动、触摸与调试叠层
public class TileLayout extends ViewGroup {

    public static final int DIMEN_GRAVITY_CENTER = LayoutEngine.DIMEN_GRAVITY_CENTER;
    public static final int DIMEN_GRAVITY_START = LayoutEngine.DIMEN_GRAVITY_START;
    public static final int DIMEN_GRAVITY_END = LayoutEngine.DIMEN_GRAVITY_END;

    private TileCoreService<TileHolder> coreService; // 核心调度器
    private Adapter adapter; // 内容适配器
    private TileEventListener<TileHolder> tileEventListener; // 事件监听器

    // 核心调度器回调:桥接适配器与事件监听器,驱动子 View 布局与预取
    private final TileCoreService.CoreInterface<TileHolder> coreInterface = new TileCoreService.CoreInterface<TileHolder>() {
        @Override
        public void beforeLayout() {
            requestLayoutDepth++;
            if (tileEventListener != null) tileEventListener.onBeforeLayout();
        }

        @Override
        public void updateUI() {
            if (debugMode) startLayoutTime = System.nanoTime();
            try {
                layoutTiles();
            } finally {
                // 确保层级计数器一定回退,避免 onBindTileHolder 异常或布局异常导致拦截机制卡死
                requestLayoutDepth--;
            }
            if (debugMode) layoutTime = startLayoutTime == 0 ? 0 : System.nanoTime() - startLayoutTime;
            if (tileEventListener != null) tileEventListener.onAfterLayout();
            if (requestLayoutDepth == 0 && pendingLayoutRequest) {
                requestLayout();
            }
            schedulePrefetch();
        }

        @Override
        public void onTileIn(TileHolder holder, int column, int row) {
            addViewInLayout(holder.itemView, -1, holder.itemView.getLayoutParams(), false);
            if (tileEventListener != null) tileEventListener.onTileIn(holder, column, row);
        }

        @Override
        public void onTileOut(TileHolder holder, int column, int row) {
            removeViewInLayout(holder.itemView);
            if (tileEventListener != null) tileEventListener.onTileOut(holder, column, row);
        }
        
        @Override
        public void onTileRecycled(TileHolder holder, int column, int row) {
            holder.view = null;
            if (tileEventListener != null) tileEventListener.onTileRecycled(holder, column, row);
        }
        
        @Override
        public void onTileSizeChanged(TileHolder holder, int column, int row, int width, int height) {
            holder.itemView.measure(
                    MeasureSpec.makeMeasureSpec(coreService.getTileWidth(column), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(coreService.getTileHeight(row), MeasureSpec.EXACTLY));
        }

        @Override
        public void onTilePrefetched(TileHolder holder, int column, int row) {
            if (tileEventListener != null) tileEventListener.onTilePrefetched(holder, column, row);
        }

        @Override
        public int getLeftBound() {
            return adapter == null ? 0 : adapter.getLeftBound();
        }

        @Override
        public int getTopBound() {
            return adapter == null ? 0 : adapter.getTopBound();
        }

        @Override
        public int getRightBound() {
            return adapter == null ? -1 : adapter.getRightBound();
        }

        @Override
        public int getBottomBound() {
            return adapter == null ? -1 : adapter.getBottomBound();
        }

        @Override
        public TileHolder onCreateTileHolder(int type) {
            return adapter.onCreateTileHolder(type);
        }

        @Override
        public void onBindTileHolder(TileHolder holder, int column, int row) {
            adapter.onBindTileHolder(holder, column, row);
            if (holder.itemView.getLayoutParams() == null) {
                holder.itemView.setLayoutParams(new ViewGroup.LayoutParams(-2, -2));
            }
            holder.view = TileLayout.this;
            holder.itemView.measure(
                    MeasureSpec.makeMeasureSpec(coreService.getTileWidth(column), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(coreService.getTileHeight(row), MeasureSpec.EXACTLY));
        }

        @Override
        public int getTileType(int column, int row) {
            return adapter.getTileType(column, row);
        }
    };

    // 调试叠层相关
    private DebugLayer debugLayer;
    private boolean debugMode;
    private long startLayoutTime;
    private long layoutTime;

    // 初始化定位覆盖(seek 时暂存目标,等待下次布局生效)
    private boolean overrideInitLocation = false;
    private int initLocationColumn;
    private int initLocationRow;
    private float initOffsetX;
    private float initOffsetY;

    // 布局拦截机制
    private int requestLayoutDepth = 0;
    private boolean pendingLayoutRequest = false;

    // 预取帧驱动：每帧最多创建 prefetchPerFrame 个瓦片（瓦片管理器内部持有，可调），把成本分摊到多帧
    // 默认 8 而非 RV 的 4:RV 是一维列表，二维网格同帧预算只覆盖一条边的 4 格，翻倍匹配二维吞吐
    private boolean prefetchScheduled;
    // 帧回调铁律：debug 模式已开启或预取队列非空，二者任一成立即启动并继续；
    // 二者皆不成立时不挂帧，自然停止
    private final Choreographer.FrameCallback prefetchCallback = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            prefetchScheduled = false;
            coreService.drainPrefetch();
            // 是否继续由 schedulePrefetch 按铁律判断
            schedulePrefetch();
        }
    };

    // 构造:基础构造
    public TileLayout(Context context) {
        super(context);
        init();
    }

    // 构造:XML 属性构造
    public TileLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    // 初始化核心调度器与默认尺寸
    private void init() {
        this.coreService = new TileCoreService<>(getContext(), coreInterface);
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        coreService.setDefaultTileWidth((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 80, displayMetrics));
        coreService.setDefaultTileHeight((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 45, displayMetrics));
        coreService.setTimeProvider(new DefaultTimeProvider());
        setWillNotDraw(false);
    }

    // 布局全部活跃瓦片的子 View 位置
    private void layoutTiles() {
        LayoutModel model = coreService.getLayoutModel();

        int column = model.colStart;
        float x = getPaddingLeft() + model.offsetX;
        while (column <= model.colEnd) {
            int row = model.rowStart;
            float y = getPaddingTop() + model.offsetY;
            while (row <= model.rowEnd) {
                TileHolder tile = coreService.getActiveTile(column, row);
                if (tile != null) {
                    tile.itemView.layout((int) x, (int) y, (int) x + coreService.getTileWidth(column), (int) y + coreService.getTileHeight(row));
                }

                y += coreService.getTileHeight(row);
                if (row == model.rowEnd) break;
                row++;
            }
            x += coreService.getTileWidth(column);
            if (column == model.colEnd) break;
            column++;
        }
        
        postInvalidateOnAnimation();
    }

    // 测量全部活跃瓦片的子 View
    private void measureTiles() {
        LayoutModel model = coreService.getLayoutModel();
        int column = model.colStart;
        int width = MeasureSpec.makeMeasureSpec(coreService.getTileWidth(column), MeasureSpec.EXACTLY);
        while (column <= model.colEnd) {
            int row = model.rowStart;
            int height = MeasureSpec.makeMeasureSpec(coreService.getTileHeight(row), MeasureSpec.EXACTLY);
            while (row <= model.rowEnd) {
                TileHolder tile = coreService.getActiveTile(column, row);
                if (tile != null) {
                    tile.itemView.measure(width, height);
                }
                if (row == model.rowEnd) break;
                row++;
                height = MeasureSpec.makeMeasureSpec(coreService.getTileHeight(row), MeasureSpec.EXACTLY);
            }
            if (column == model.colEnd) break;
            column++;
            width = MeasureSpec.makeMeasureSpec(coreService.getTileWidth(column), MeasureSpec.EXACTLY);
        }
    }

    // 布局回调:首次或 seek 时建立视窗锚点
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        boolean init = false;
        if (adapter != null) {
            if (overrideInitLocation) {
                overrideInitLocation = false;
                coreService.seek(initLocationColumn, initLocationRow, initOffsetX, initOffsetY);
                init = true;
            } else if (coreService.getActiveTileCount() == 0) {
                coreService.seek(adapter.getLeftBound(), adapter.getTopBound(), 0, 0);
                init = true;
            }
        }
        if (!init && pendingLayoutRequest) {
            layoutTiles();
        }
        pendingLayoutRequest = false;
    }

    // 拦截机制：在同步周期内(requestLayoutDepth > 0)累积布局请求，避免瓦片未就绪时触发无效的 measure/layout
    @Override
    public void requestLayout() {
        pendingLayoutRequest = true;
        if (requestLayoutDepth > 0) {
            return;
        }
        super.requestLayout();
    }

    // 测量回调:有待处理布局请求时先测量瓦片
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (pendingLayoutRequest) {
            measureTiles();
        }
    }

    // 驱动惯性滚动
    @Override
    public void computeScroll() {
        super.computeScroll();
        coreService.computeScroll();
    }

    // 尺寸变化回调:刷新视窗边界
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateBounds();
    }

    // 更新核心调度器视窗边界并触发一次同步
    private void updateBounds() {
        coreService.setBounds(getPaddingLeft(), getPaddingTop(), getWidth() - getPaddingRight(), getHeight() - getPaddingBottom());
        if (getWidth() != 0 && getHeight() != 0) {
            coreService.sync(0, 0);
        }
    }
    
    // 内边距变化时同步刷新视窗边界
    @Override
    public void setPadding(int left, int top, int right, int bottom) {
        super.setPadding(left, top, right, bottom);
        updateBounds();
    }

    // 触摸分发:先交给核心调度器处理手势
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (!coreService.isEmpty()) {
            coreService.handleTouchEvent(event);
        }
        return super.dispatchTouchEvent(event);
    }

    // 拦截策略:DOWN 不拦截,滚动中拦截子 View 事件
    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            return false;
        }
        return !coreService.isEmpty() && coreService.isInteractingWithView();
    }

    // 触摸事件:内容非空即消费
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return !coreService.isEmpty();
    }

    // 父容器拦截开关(透传核心调度器)
    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        super.requestDisallowInterceptTouchEvent(disallowIntercept);
        coreService.requestDisallowInterceptTouchEvent(disallowIntercept);
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    // 开关调试模式:创建/销毁调试叠层并同步核心调度器
    public void setDebugMode(boolean enabled) {
        if (debugMode == enabled) return;
        debugMode = enabled;
        coreService.setDebugMode(enabled);
        if (enabled) {
            debugLayer = new DebugLayer(getContext(), new DebugLayer.Callback() {
                @Override
                public int getActiveTileCount() {
                    return coreService.getActiveTileCount();
                }

                @Override
                public int getRecycledTileCount() {
                    return coreService.getRecycledTileCount();
                }

                @Override
                public int getDyingTileCount() {
                    return coreService.getDyingTileCount();
                }

                @Override
                public int getPrefetchTileCount() {
                    return coreService.getPrefetchTileCount();
                }

                @Override
                public int getPrefetchQueuePeak() {
                    return coreService.getPrefetchQueuePeak();
                }

                @Override
                public Rect getBounds() {
                    return coreService.getBounds();
                }
                @Override
                public LayoutModel getLayoutModel() {
                    return coreService.getLayoutModel();
                }
                @Override
                public void postInvalidateOnAnimation() {
                    TileLayout.this.postInvalidateOnAnimation();
                }
                @Override
                public long getBindTime() {
                    return coreService.getBindTime();
                }

                @Override
                public long getLayoutTime() {
                    return layoutTime;
                }
            });
            if (isAttachedToWindow()) {
                debugLayer.start();
            }
            setClipToPadding(false);
        } else {
            if (debugLayer != null) debugLayer.end();
            debugLayer = null;
            setClipToPadding(true);
        }
        postInvalidateOnAnimation();
        // 铁律：debug 开启即启动帧回调；关闭时若队列也为空，下一帧自然停止
        schedulePrefetch();
    }

    // 进入窗口:启动调试采集并按铁律检查预取帧回调
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (debugLayer != null) {
            debugLayer.start();
        }
        // 铁律：进入窗口时按条件检查是否启动帧回调（debug 开启或预取队列非空）
        schedulePrefetch();
    }

    // 移出窗口:停止调试采集与帧回调
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (debugLayer != null) {
            debugLayer.end();
        }
        coreService.resetAnimator();
        // 铁律：从窗口移除必须停止帧回调，无条件摘除（不依赖 prefetchScheduled 标记）
        prefetchScheduled = false;
        Choreographer.getInstance().removeFrameCallback(prefetchCallback);
    }

    // 绘制:叠加调试叠层
    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (debugMode && debugLayer != null) debugLayer.startDraw();
        super.dispatchDraw(canvas);
        if (debugMode && debugLayer != null) {
            debugLayer.draw(canvas);
        }
    }

    // 增量偏移视窗
    public void offset(float dx, float dy) {
        if (isEmpty()) {
            return;
        }
        coreService.sync(dx, dy);
    }

    // 跳转到指定锚点(零偏移)
    public void seek(int column, int row) {
        seek(column, row, 0, 0);
    }

    // 跳转到指定锚点与偏移(暂存后由下次布局生效)
    public void seek(int column, int row, float offsetX, float offsetY) {
        if (isEmpty()) return;
        overrideInitLocation = true;
        initLocationColumn = column;
        initLocationRow = row;
        initOffsetX = offsetX;
        initOffsetY = offsetY;
        requestLayout();
    }

    // 将视窗吸附回内容边界内
    public void snap() {
    	coreService.snap();
    }

    // 查询指定列相对视窗的 X 坐标
    public float getTileX(int column) {
        return coreService.getTileX(column);
    }

    // 查询指定行相对视窗的 Y 坐标
    public float getTileY(int row) {
        return coreService.getTileY(row);
    }

    // 查找包含指定 X 坐标的列索引
    public int findColumn(float x) {
        return coreService.findColumn(x);
    }

    // 查找包含指定 Y 坐标的行索引
    public int findRow(float y) {
        return coreService.findRow(y);
    }

    // 查询布局模型
    public LayoutModel getLayoutModel() {
        return coreService.getLayoutModel();
    }

    public boolean isHorizontalScrollEnabled() {
        return coreService.isHorizontalScrollEnabled();
    }

    public void setHorizontalScrollEnabled(boolean horizontalScrollEnabled) {
        coreService.setHorizontalScrollEnabled(horizontalScrollEnabled);
    }

    public boolean isVerticalScrollEnabled() {
        return coreService.isVerticalScrollEnabled();
    }

    public void setVerticalScrollEnabled(boolean verticalScrollEnabled) {
        coreService.setVerticalScrollEnabled(verticalScrollEnabled);
    }

    public int getTileWidth(int column) {
        return coreService.getTileWidth(column);
    }

    public int getTileHeight(int row) {
        return coreService.getTileHeight(row);
    }

    public void setTileWidth(int column, int width) {
        coreService.setTileWidth(column, width, DIMEN_GRAVITY_START);
    }

    public void setTileWidth(int column, int width, int gravity) {
        coreService.setTileWidth(column, width, gravity);
    }

    public void deleteTileWidth(int column) {
        coreService.deleteTileWidth(column, DIMEN_GRAVITY_START);
    }

    public void deleteTileWidth(int column, int gravity) {
        coreService.deleteTileWidth(column, gravity);
    }

    public void setTileHeight(int row, int height) {
        coreService.setTileHeight(row, height, DIMEN_GRAVITY_START);
    }

    public void setTileHeight(int row, int height, int gravity) {
        coreService.setTileHeight(row, height, gravity);
    }

    public void deleteTileHeight(int row) {
        coreService.deleteTileHeight(row, DIMEN_GRAVITY_START);
    }

    public void deleteTileHeight(int row, int gravity) {
        coreService.deleteTileHeight(row, gravity);
    }

    public void setTileSize(int column, int width, int row, int height) {
        coreService.setTileSize(column, width, DIMEN_GRAVITY_START, row, height, DIMEN_GRAVITY_START);
    }

    public void setTileSize(int column, int width, int hGravity, int row, int height, int vGravity) {
        coreService.setTileSize(column, width, hGravity, row, height, vGravity);
    }

    public void updateColumn(int column) {
    	coreService.updateColumn(column);
    }

    public void updateRow(int row) {
    	coreService.updateRow(row);
    }

    public void update(int column, int row) {
    	coreService.update(column, row);
    }

    public void updateRange(int left, int top, int right, int bottom) {
        coreService.updateRange(left, top, right, bottom);
    }

    public void updateAll() {
        coreService.updateAll();
    }

    public void resetAnimator() {
    	coreService.resetAnimator();
    }

    // 帧回调铁律：debug 模式已开启或预取队列非空，二者任一成立即启动并继续；
    // 二者皆不成立时不挂帧，自然停止。已挂则不重复挂，避免同一帧多次驱动。
    // View 未附加到窗口时禁止挂帧，与 detach 必停的铁律一致
    private void schedulePrefetch() {
        if (prefetchScheduled) return;
        if (!isAttachedToWindow()) return;
        if (!debugMode && !coreService.hasPrefetchPending()) return;
        prefetchScheduled = true;
        Choreographer.getInstance().postFrameCallback(prefetchCallback);
    }

    public Adapter getAdapter() {
        return adapter;
    }

    // 设置适配器:更换时重置全部瓦片状态
    public void setAdapter(Adapter adapter) {
        if (this.adapter != adapter) {
            coreService.reset();
        }
        this.adapter = adapter;
        requestLayoutDepth = 0;
        requestLayout();
    }

    // 设置瓦片事件监听器
    public void setTileEventListener(TileEventListener<TileHolder> tileEventListener) {
        this.tileEventListener = tileEventListener;
    }

    public int getDefaultTileWidth() {
        return coreService.getDefaultTileWidth();
    }

    public int getDefaultTileHeight() {
        return coreService.getDefaultTileHeight();
    }

    public void setDefaultTileWidth(int width) {
        coreService.setDefaultTileWidth(width);
    }

    public void setDefaultTileHeight(int height) {
        coreService.setDefaultTileHeight(height);
    }

    public TileDimenProvider getDimenProvider() {
        return coreService.getDimenProvider();
    }

    public void setDimenProvider(TileDimenProvider dimenProvider) {
        coreService.setDimenProvider(dimenProvider);
    }

    public void setActiveTiles(LongMap<TileHolder> map) {
        coreService.setActiveTiles(map);
    }
    
    public void setDyingTiles(LongMap<TileHolder> map) {
        coreService.setDyingTiles(map);
    }
    
    public int getDyingExpand() {
        return coreService.getDyingExpand();
    }

    public void setDyingExpand(int expand) {
        coreService.setDyingExpand(expand);
    }

    public boolean isDyingEnabled() {
        return coreService.isDyingEnabled();
    }

    public void setDyingEnabled(boolean enabled) {
        coreService.setDyingEnabled(enabled);
    }

    public int getPrefetchExpand() {
        return coreService.getPrefetchExpand();
    }

    public void setPrefetchExpand(int expand) {
        coreService.setPrefetchExpand(expand);
    }

    public boolean isPrefetchEnabled() {
        return coreService.isPrefetchEnabled();
    }

    public void setPrefetchEnabled(boolean enabled) {
        coreService.setPrefetchEnabled(enabled);
        // 铁律：开启后若队列非空即启动帧回调；关闭时若队列已清空，下一帧自然停止
        schedulePrefetch();
    }

    // 获取每帧预取数量上限（帧预算，由瓦片管理器持有）
    public int getPrefetchPerFrame() {
        return coreService.getPrefetchPerFrame();
    }

    // 设置每帧预取数量上限，非法参数（小于等于 0）无响应
    public void setPrefetchPerFrame(int count) {
        coreService.setPrefetchPerFrame(count);
    }

    public void setPrefetchTiles(LongMap<TileHolder> map) {
        coreService.setPrefetchTiles(map);
    }
    
    public void setWidths(IntIntMap map) {
        coreService.setWidths(map);
    }
    
    public void setHeights(IntIntMap map) {
        coreService.setHeights(map);
    }
    
    public void setRecycledTiles(TileRecycledPool<TileHolder> pool) {
        coreService.setRecycledTiles(pool);
    }

    public TileHolder getActiveTile(int column, int row) {
        return coreService.getActiveTile(column, row);
    }

    public boolean isEmpty() {
        return coreService.isEmpty();
    }

    public boolean isAtLeftBound() {
        return coreService.isAtLeftBound();
    }

    public boolean isAtTopBound() {
        return coreService.isAtTopBound();
    }

    public boolean isAtRightBound() {
        return coreService.isAtRightBound();
    }

    public boolean isAtBottomBound() {
        return coreService.isAtBottomBound();
    }

    public boolean isInteractingWithView() {
        return coreService.isInteractingWithView();
    }

    // 适配器基类(由使用方继承)
    public static abstract class Adapter extends TileAdapter<TileHolder> {
    }

    // 布局瓦片持有者:以子 View 承载内容
    public static class TileHolder extends TileCoreService.BaseTileHolder {

        public final View itemView; // 瓦片子 View

        private TileLayout view;

        public TileHolder(View itemView) {
            this.itemView = itemView;
        }

        public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
            if (view != null) view.requestDisallowInterceptTouchEvent(disallowIntercept);
        }

    }

}