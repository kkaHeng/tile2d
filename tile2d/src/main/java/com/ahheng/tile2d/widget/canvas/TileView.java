package com.ahheng.tile2d.widget.canvas;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Debug;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

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

// 瓦片画布视图(自绘渲染端)
// 以 Canvas 直接绘制瓦片,配合核心调度器完成滚动、触摸与调试叠层
public class TileView extends View {

    public static final int DIMEN_GRAVITY_CENTER = LayoutEngine.DIMEN_GRAVITY_CENTER;
    public static final int DIMEN_GRAVITY_START = LayoutEngine.DIMEN_GRAVITY_START;
    public static final int DIMEN_GRAVITY_END = LayoutEngine.DIMEN_GRAVITY_END;

    private TileCoreService<TileHolder> coreService; // 核心调度器
    private Adapter adapter; // 内容适配器
    private TileEventListener<TileHolder> tileEventListener; // 事件监听器

    // 核心调度器回调:桥接适配器与事件监听器,驱动重绘与预取
    private final TileCoreService.CoreInterface<TileHolder> coreInterface = new TileCoreService.CoreInterface<TileHolder>() {
        @Override
        public void beforeLayout() {
            if (tileEventListener != null) tileEventListener.onBeforeLayout();
        }

        @Override
        public void updateUI() {
            if (debugMode) startLayoutTime = Debug.threadCpuTimeNanos();
            TileView.this.postInvalidateOnAnimation();
            if (debugMode) layoutTime = startLayoutTime == 0 ? 0 : Debug.threadCpuTimeNanos() - startLayoutTime;
            if (tileEventListener != null) tileEventListener.onAfterLayout();
            schedulePrefetch();
        }

        @Override
        public void onTileIn(TileHolder holder, int column, int row) {
            if (tileEventListener != null) tileEventListener.onTileIn(holder, column, row);
        }

        @Override
        public void onTileOut(TileHolder holder, int column, int row) {
            if (tileEventListener != null) tileEventListener.onTileOut(holder, column, row);
        }
        
        @Override
        public void onTileRecycled(TileHolder holder, int column, int row) {
            if (tileEventListener != null) tileEventListener.onTileRecycled(holder, column, row);
        }
        
        @Override
        public void onTileSizeChanged(TileHolder holder, int column, int row, int width, int height) {}

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
            TileHolder holder = adapter.onCreateTileHolder(type);
            if (holder != null) holder.view = TileView.this;
            return holder;
        }

        @Override
        public void onBindTileHolder(TileHolder holder, int column, int row) {
            adapter.onBindTileHolder(holder, column, row);
            holder.view = TileView.this;
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

    // 触摸目标跟踪
    private TileHolder touchTarget;
    private final int[] touchTargetPos = new int[2];
    private final float[] touchTargetLoc = new float[2];
    private boolean disallowIntercept;

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

    private float touchDownX;
    private float touchDownY;
    private boolean isClickCandidate;
    private Runnable longPressRunnable;
    private long longPressTimeout = 400L;
    private final int touchSlop;

    // 构造:基础构造
    public TileView(Context context) {
        super(context);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        init();
    }

    // 构造:XML 属性构造
    public TileView(Context context, AttributeSet attrs) {
        super(context, attrs);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        init();
    }

    // 初始化核心调度器与默认尺寸
    private void init() {
        this.coreService = new TileCoreService<>(getContext(), coreInterface);
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        coreService.setDefaultTileWidth((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 80, displayMetrics));
        coreService.setDefaultTileHeight((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 45, displayMetrics));
        coreService.setTimeProvider(new DefaultTimeProvider());
    }

    // 缩放数值
    private float scale(float num) {
    	return num * coreService.getScaleFactor();
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

    // 缩放

    public void zoom(float scaleFactor) {
    	zoom(scaleFactor, 0, 0, 0, 0);
    }

    public void zoom(float scaleFactor, float focusX, float focusY, float dx, float dy) {
    	coreService.zoom(scaleFactor, focusX, focusY, dx, dy);
    }

    public float getScaleFactor() {
    	return coreService.getScaleFactor();
    }

    public float getMinScaleFactor() {
    	return coreService.getMinScaleFactor();
    }

    public void setMinScaleFactor(float scaleFactor) {
    	coreService.setMinScaleFactor(scaleFactor);
    }

    public float getMaxScaleFactor() {
    	return coreService.getMaxScaleFactor();
    }

    public void setMaxScaleFactor(float scaleFactor) {
    	coreService.setMaxScaleFactor(scaleFactor);
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

    // 布局回调:首次或 seek 时建立视窗锚点
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (adapter != null) {
            if (overrideInitLocation) {
                overrideInitLocation = false;
                coreService.seek(initLocationColumn, initLocationRow, initOffsetX, initOffsetY);
            } else if (coreService.getActiveTileCount() == 0) {
                coreService.seek(adapter.getLeftBound(), adapter.getTopBound(), 0, 0);
            }
        }
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

    // 绘制:按视窗范围遍历瓦片并逐个绘制,叠加调试叠层
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (debugMode && debugLayer != null) debugLayer.startDraw();
        LayoutModel model = coreService.getLayoutModel();
        if (model.colStart <= model.colEnd && model.rowStart <= model.rowEnd) {
            canvas.save();
            if (!debugMode) canvas.clipRect(coreService.getBounds());
            canvas.translate(getPaddingLeft(), getPaddingTop());
            canvas.translate(scale(model.offsetX), scale(model.offsetY));
            float x = 0;
            int column = model.colStart;
            while (column <= model.colEnd) {
                float width = scale(coreService.getTileWidth(column));
                canvas.translate(x, 0);
                float y = 0;
                int row = model.rowStart;
                while (row <= model.rowEnd) {
                    float height = scale(coreService.getTileHeight(row));
                    TileHolder tile = coreService.getActiveTile(column, row);
                    if (tile != null) {
                        canvas.translate(0, y);
                        tile.draw(canvas);
                        canvas.translate(0, -y);
                    }

                    if (row == model.rowEnd) break;
                    row++;
                    y += height;
                }
                canvas.translate(-x, 0);
                if (column == model.colEnd) break;
                column++;
                x += width;
            }
            canvas.restore();
        }
        if (debugMode && debugLayer != null) {
            debugLayer.draw(canvas);
        }
    }

    // 触摸事件:命中瓦片则转发给瓦片,同时处理点击/长按/滚动判定
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (coreService.isEmpty()) {
            return super.onTouchEvent(event);
        }

        int action = event.getActionMasked();
        coreService.handleTouchEvent(event);

        if (action == MotionEvent.ACTION_DOWN) {
            disallowIntercept = false;
            coreService.requestDisallowInterceptTouchEvent(false);

            touchTarget = findTileAt(event.getX(), event.getY(), touchTargetPos, touchTargetLoc);

            if (touchTarget != null) {
                MotionEvent tileEvent = toTileEvent(event);
                touchTarget.onTouchEvent(tileEvent);
                tileEvent.recycle();
            }
            touchDownX = event.getX();
            touchDownY = event.getY();
            isClickCandidate = true;

            removeLongPress();
            longPressRunnable = () -> {
                if (touchTarget != null && !coreService.isInteractingWithView()) {
                    touchTarget.onLongClick();
                    isClickCandidate = false;
                }
            };
            postDelayed(longPressRunnable, longPressTimeout);

            return true;
        }

        if (touchTarget != null) {
            boolean isScrolling = coreService.isInteractingWithView();
            boolean intercepted = !disallowIntercept && isScrolling;

            MotionEvent tileEvent = toTileEvent(event);
            if (intercepted || action == MotionEvent.ACTION_CANCEL) {
                tileEvent.setAction(MotionEvent.ACTION_CANCEL);
                touchTarget.onTouchEvent(tileEvent);
                tileEvent.recycle();
                resetTouchTarget();
                removeLongPress();
                isClickCandidate = false;
            } else {
                touchTarget.onTouchEvent(tileEvent);
                tileEvent.recycle();

                if (action == MotionEvent.ACTION_MOVE) {
                    if (Math.abs(event.getX() - touchDownX) > touchSlop
                            || Math.abs(event.getY() - touchDownY) > touchSlop) {
                        isClickCandidate = false;
                        removeLongPress();
                    }
                }
            }
        }

        if (action == MotionEvent.ACTION_UP) {
            removeLongPress();
            if (touchTarget != null && isClickCandidate && !coreService.isInteractingWithView()) {
                touchTarget.onClick();
            }
            resetTouchTarget();
        }

        return true;
    }

    // 父容器拦截开关(透传核心调度器)
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        this.disallowIntercept = disallowIntercept;
        coreService.requestDisallowInterceptTouchEvent(disallowIntercept);
    }

    // 驱动惯性滚动
    @Override
    public void computeScroll() {
        super.computeScroll();
        coreService.computeScroll();
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
        removeLongPress();
        resetTouchTarget();
        coreService.resetAnimator();
        // 铁律：从窗口移除必须停止帧回调，无条件摘除（不依赖 prefetchScheduled 标记）
        prefetchScheduled = false;
        Choreographer.getInstance().removeFrameCallback(prefetchCallback);
    }

    // 内边距变化时同步刷新视窗边界
    @Override
    public void setPadding(int left, int top, int right, int bottom) {
        super.setPadding(left, top, right, bottom);
        updateBounds();
    }

    public long getLongPressTimeout() {
        return longPressTimeout;
    }

    public void setLongPressTimeout(long longPressTimeout) {
        this.longPressTimeout = longPressTimeout;
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

    public void setTileSize(int column, int width, int row, int height) {
        coreService.setTileSize(column, width, DIMEN_GRAVITY_START, row, height, DIMEN_GRAVITY_START);
    }

    public void setTileSize(int column, int width, int hGravity, int row, int height, int vGravity) {
        coreService.setTileSize(column, width, hGravity, row, height, vGravity);
    }

    public void deleteTileHeight(int row) {
        coreService.deleteTileHeight(row, DIMEN_GRAVITY_START);
    }

    public void deleteTileHeight(int row, int gravity) {
        coreService.deleteTileHeight(row, gravity);
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

    public Adapter getAdapter() {
        return adapter;
    }

    // 设置适配器:更换时重置全部瓦片状态
    public void setAdapter(Adapter adapter) {
        if (this.adapter != adapter) {
            resetTouchTarget();
            removeLongPress();
            coreService.reset();
        }
        this.adapter = adapter;
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

    public boolean isDebugMode() {
        return debugMode;
    }

    // 开关调试模式:创建/销毁调试叠层并同步核心调度器
    public void setDebugMode(boolean enabled) {
        if (debugMode == enabled) return;
        debugMode = enabled;
        coreService.setDebugMode(enabled);
        if (debugMode) {
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
                    TileView.this.postInvalidateOnAnimation();
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
        } else {
            if (debugLayer != null) debugLayer.end();
            debugLayer = null;
        }
        postInvalidateOnAnimation();
        // 铁律：debug 开启即启动帧回调；关闭时若队列也为空，下一帧自然停止
        schedulePrefetch();
    }

    // 查找视图坐标命中的瓦片,同时输出瓦片坐标与内容坐标
    private TileHolder findTileAt(float viewX, float viewY, int[] outPos, float[] outLoc) {
        int col = findColumn(viewX);
        int row = findRow(viewY);
        if (outPos != null) {
            outPos[0] = col;
            outPos[1] = row;
        }
        if (outLoc != null) {
            // 转回 content 坐标，兼容触摸事件转换
            outLoc[0] = getTileX(col) - getPaddingLeft();
            outLoc[1] = getTileY(row) - getPaddingTop();
        }
        return coreService.getActiveTile(col, row);
    }

    // 视图事件坐标转换为瓦片本地坐标
    private MotionEvent toTileEvent(MotionEvent viewEvent) {
        MotionEvent tileEvent = MotionEvent.obtain(viewEvent);
        float offsetX = getPaddingLeft() + touchTargetLoc[0];
        float offsetY = getPaddingTop() + touchTargetLoc[1];
        tileEvent.offsetLocation(-offsetX, -offsetY);
        return tileEvent;
    }

    // 重置触摸目标与坐标缓存
    private void resetTouchTarget() {
        touchTarget = null;
        touchTargetPos[0] = 0;
        touchTargetPos[1] = 0;
        touchTargetLoc[0] = 0;
        touchTargetLoc[1] = 0;
    }

    // 移除待触发的长按回调
    private void removeLongPress() {
        if (longPressRunnable != null) {
            removeCallbacks(longPressRunnable);
            longPressRunnable = null;
        }
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

    // 适配器基类(由使用方继承)
    public static abstract class Adapter extends TileAdapter<TileHolder> {
    }

    // 画布瓦片持有者:提供绘制与触摸回调入口
    public static class TileHolder extends TileCoreService.BaseTileHolder {

        private TileView view;

        public void draw(Canvas canvas) {
        }

        public boolean onTouchEvent(MotionEvent event) {
            return false;
        }

        public boolean onClick() {
            return false;
        }

        public void onLongClick() {
        }

        public void postInvalidate() {
            if (view != null) view.postInvalidate();
        }

        public void postInvalidateOnAnimation() {
            if (view != null) view.postInvalidateOnAnimation();
        }

        public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
            if (view != null) view.requestDisallowInterceptTouchEvent(disallowIntercept);
        }

        public float getScaleFactor() {
        	return view != null ? view.getScaleFactor() : 1;
        }

    }

}