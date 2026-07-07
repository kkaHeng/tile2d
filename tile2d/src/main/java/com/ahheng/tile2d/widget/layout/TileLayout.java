package com.ahheng.tile2d.widget.layout;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Debug;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import com.ahheng.tile2d.TileCoreService;
import com.ahheng.tile2d.TileLayoutModel;
import com.ahheng.tile2d.TileLayoutService;
import com.ahheng.tile2d.dimen.TileDimenProvider;
import com.ahheng.tile2d.tile.OnTileLifecycleListener;
import com.ahheng.tile2d.tile.TileAdapter;
import com.ahheng.tile2d.tile.TileRecycledPool;
import com.ahheng.tile2d.util.IntIntMap;
import com.ahheng.tile2d.util.LongMap;
import com.ahheng.tile2d.widget.debug.DebugLayer;

public class TileLayout extends ViewGroup {

    public static final int DIMEN_GRAVITY_CENTER = TileLayoutService.DIMEN_GRAVITY_CENTER;
    public static final int DIMEN_GRAVITY_START = TileLayoutService.DIMEN_GRAVITY_START;
    public static final int DIMEN_GRAVITY_END = TileLayoutService.DIMEN_GRAVITY_END;

    private TileCoreService<TileHolder> coreService;
    private Adapter adapter;
    private OnLayoutListener onLayoutListener;
    private OnTileLifecycleListener<TileHolder> onTileLifecycleListener;

    private final TileCoreService.CoreInterface<TileHolder> coreInterface = new TileCoreService.CoreInterface<TileHolder>() {
        @Override
        public void beforeLayout() {
            requestLayoutDepth++;
            if (onLayoutListener != null) onLayoutListener.onBeforeLayout();
        }

        @Override
        public void updateUI() {
            if (debugMode) startLayoutTime = Debug.threadCpuTimeNanos();
            layoutTiles();
            requestLayoutDepth--;
            TileLayout.this.postInvalidateOnAnimation();
            if (debugMode) layoutTime = startLayoutTime == 0 ? 0 : Debug.threadCpuTimeNanos() - startLayoutTime;
            if (onLayoutListener != null) onLayoutListener.onAfterLayout();
            if (requestLayoutDepth == 0 && requestLayout) {
                requestLayout();
            }
        }

        @Override
        public void onTileIn(TileHolder holder, int column, int row) {
            addViewInLayout(holder.itemView, -1, holder.itemView.getLayoutParams(), false);
            if (onTileLifecycleListener != null) onTileLifecycleListener.onTileIn(holder, column, row);
        }

        @Override
        public void onTileOut(TileHolder holder, int column, int row) {
            removeViewInLayout(holder.itemView);
            if (onTileLifecycleListener != null) onTileLifecycleListener.onTileOut(holder, column, row);
        }
        
        @Override
        public void onTileRecycled(TileHolder holder, int column, int row) {
            holder.view = null;
            if (onTileLifecycleListener != null) onTileLifecycleListener.onTileRecycled(holder, column, row);
        }
        
        @Override
        public void onTileSizeChanged(TileHolder holder, int column, int row, int width, int height) {
            holder.itemView.measure(
                    MeasureSpec.makeMeasureSpec(coreService.getTileWidth(column), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(coreService.getTileHeight(row), MeasureSpec.EXACTLY));
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

        @Override
        public boolean isDebugMode() {
            return debugMode;
        }
    };

    private DebugLayer debugLayer;
    private boolean debugMode;
    private long startLayoutTime;
    private long layoutTime;

    private boolean overrideInitLocation = false;
    private int initLocationColumn;
    private int initLocationRow;
    private float initOffsetX;
    private float initOffsetY;

    private int requestLayoutDepth = 0;
    private boolean requestLayout = false;

    public TileLayout(Context context) {
        super(context);
        init();
    }
    public TileLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    private void init() {
        this.coreService = new TileCoreService<>(getContext(), coreInterface);
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        coreService.setDefaultTileWidth((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 80, displayMetrics));
        coreService.setDefaultTileHeight((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 45, displayMetrics));
        setWillNotDraw(false);
    }

    private void layoutTiles() {
        TileLayoutModel model = coreService.getLayoutModel();

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
    }

    private void measureTiles() {
        TileLayoutModel model = coreService.getLayoutModel();
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
        if (!init) {
            layoutTiles();
            postInvalidateOnAnimation();
        }
        requestLayout = false;
    }

    @Override
    public void requestLayout() {
        requestLayout = true;
        if (requestLayoutDepth > 0) {
            return;
        }
        super.requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (requestLayout) {
            measureTiles();
        }
    }

    @Override
    public void computeScroll() {
        super.computeScroll();
        coreService.computeScroll();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        coreService.setBounds(getPaddingLeft(), getPaddingTop(), getWidth() - getPaddingRight(), getHeight() - getPaddingBottom());
        coreService.sync(0, 0);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (!coreService.isEmpty()) {
            coreService.handleTouchEvent(event);
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            return false;
        }
        return !coreService.isEmpty() && coreService.isInteractingWithView();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return !coreService.isEmpty();
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        super.requestDisallowInterceptTouchEvent(disallowIntercept);
        coreService.requestDisallowInterceptTouchEvent(disallowIntercept);
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public void setDebugMode(boolean enabled) {
        if (debugMode == enabled) return;
        debugMode = enabled;
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
                public int getTileWidth(int column) {
                    return coreService.getTileWidth(column);
                }
                
                @Override
                public int getTileHeight(int row) {
                    return coreService.getTileHeight(row);
                }
                
                @Override
                public Rect getBounds() {
                    return coreService.getBounds();
                }
                @Override
                public TileLayoutModel getLayoutModel() {
                    return coreService.getLayoutModel();
                }
                @Override
                public LongMap<? extends TileCoreService.BaseTileHolder> getDyingTiles() {
                    return coreService.getDyingTiles();
                }
                @Override
                public void postInvalidateOnAnimation() {
                    TileLayout.this.postInvalidateOnAnimation();
                }

                @Override
                public long getSyncTime() {
                    return coreService.getSyncTime();
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
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (debugLayer != null) {
            debugLayer.start();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (debugLayer != null) {
            debugLayer.end();
        }
        coreService.resetAnimator();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (debugMode && debugLayer != null) debugLayer.startDraw();
        super.dispatchDraw(canvas);
        if (debugMode && debugLayer != null) {
            debugLayer.draw(canvas);
        }
    }

    public void offset(float dx, float dy) {
        if (isEmpty()) {
            return;
        }
        coreService.sync(dx, dy);
    }

    public void seek(int column, int row) {
        seek(column, row, 0, 0);
    }
    public void seek(int column, int row, float offsetX, float offsetY) {
        if (isEmpty()) return;
        overrideInitLocation = true;
        initLocationColumn = column;
        initLocationRow = row;
        initOffsetX = offsetX;
        initOffsetY = offsetY;
        requestLayout();
    }

    public void snap() {
    	coreService.snap();
    }

    public float getTileX(int column) {
        return coreService.getTileX(column);
    }

    public float getTileY(int row) {
        return coreService.getTileY(row);
    }

    public int findColumn(float x) {
        return coreService.findColumn(x);
    }

    public int findRow(float y) {
        return coreService.findRow(y);
    }

    public TileLayoutModel getLayoutModel() {
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

    public Adapter getAdapter() {
        return adapter;
    }

    public void setAdapter(Adapter adapter) {
        if (this.adapter != adapter) {
            coreService.reset();
        }
        this.adapter = adapter;
        requestLayoutDepth = 0;
        requestLayout();
    }

    public void setOnLayoutListener(OnLayoutListener onLayoutListener) {
        this.onLayoutListener = onLayoutListener;
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
    
    public void setWidths(IntIntMap map) {
        coreService.setWidths(map);
    }
    
    public void setHeights(IntIntMap map) {
        coreService.setHeights(map);
    }
    
    public void setRecycledTiles(TileRecycledPool<TileHolder> pool) {
        coreService.setRecycledTiles(pool);
    }

    public void setOnTileLifecycleListener(OnTileLifecycleListener<TileHolder> onTileLifecycleListener) {
        this.onTileLifecycleListener = onTileLifecycleListener;
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

    public static abstract class Adapter extends TileAdapter<TileHolder> {
    }

    public static class TileHolder extends TileCoreService.BaseTileHolder {

        public final View itemView;

        private TileLayout view;

        public TileHolder(View itemView) {
            this.itemView = itemView;
        }

        public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
            if (view != null) view.requestDisallowInterceptTouchEvent(disallowIntercept);
        }

    }

    public interface OnLayoutListener {
        void onBeforeLayout(); // 布局前
        void onAfterLayout(); // 布局后
    }

}
