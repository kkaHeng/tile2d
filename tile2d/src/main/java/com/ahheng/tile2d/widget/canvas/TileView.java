package com.ahheng.tile2d.widget.canvas;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.LongSparseArray;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import com.ahheng.tile2d.TileCoreService;
import com.ahheng.tile2d.TileLayoutModel;
import com.ahheng.tile2d.widget.TileAdapter;
import com.ahheng.tile2d.widget.TileDimenProvider;
import com.ahheng.tile2d.widget.debug.DebugLayer;

public class TileView extends View {

    private TileCoreService<TileHolder> coreService;
    private Adapter adapter;

    private final TileCoreService.CoreInterface<TileHolder> coreInterface = new TileCoreService.CoreInterface<TileHolder>() {
        
        @Override
        public void updateUI() {
            TileView.this.postInvalidateOnAnimation();
        }

        @Override
        public void onTileIn(TileHolder holder, int column, int row) {
        }

        @Override
        public void onTileOut(TileHolder holder, int column, int row) {
        }
        
        @Override
        public void onTileRecycled(TileHolder holder, int column, int row) {
            holder.view = null;
        }

        @Override
        public void onTileBind(TileHolder holder, int column, int row) {
            holder.view = TileView.this;
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
        }

        @Override
        public int getTileType(int column, int row) {
            return adapter.getTileType(column, row);
        }
    };

    private DebugLayer debugLayer;
    private boolean debugMode;

    private boolean overrideInitLocation = false;
    private int initLocationColumn;
    private int initLocationRow;
    private float initOffsetX;
    private float initOffsetY;

    private TileHolder touchTarget;
    private final int[] touchTargetPos = new int[2];
    private final float[] touchTargetLoc = new float[2];
    private boolean disallowIntercept;

    private float touchDownX;
    private float touchDownY;
    private boolean isClickCandidate;
    private Runnable longPressRunnable;
    private long longPressTimeout = 400L;
    private final int touchSlop;

    public TileView(Context context) {
        super(context);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        init();
    }

    public TileView(Context context, AttributeSet attrs) {
        super(context, attrs);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        init();
    }

    private void init() {
        this.coreService = new TileCoreService<>(getContext(), coreInterface);
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        coreService.setDefaultTileWidth((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 80, displayMetrics));
        coreService.setDefaultTileHeight((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 45, displayMetrics));
    }

    public void offset(float dx, float dy) {
        if (isEmpty()) {
            return;
        }
        coreService.sync(dx, dy);
    }

    public void smoothOffset(float dx, float dy) {
    	if (isEmpty()) {
            return;
        }
        coreService.smoothSync(dx, dy);
    }

    public void smoothOffset(float dx, float dy, int duration) {
        if (isEmpty()) {
            return;
        }
        coreService.smoothSync(dx, dy, duration);
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
        return coreService.getLayoutModel().newInstance();
    }

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

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        coreService.setBounds(getPaddingLeft(), getPaddingTop(), getWidth() - getPaddingRight(), getHeight() - getPaddingBottom());
        coreService.sync(0, 0);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (debugMode && debugLayer != null) debugLayer.startDraw();
        TileLayoutModel model = coreService.getLayoutModel();
        if (model.colStart <= model.colEnd && model.rowStart <= model.rowEnd) {
            canvas.save();
            if (!debugMode) canvas.clipRect(coreService.getBounds());
            canvas.translate(getPaddingLeft(), getPaddingTop());
            canvas.translate(model.offsetX, model.offsetY);
            float x = 0;
            int column = model.colStart;
            while (column <= model.colEnd) {
                int width = coreService.getTileWidth(column);
                float y = 0;
                int row = model.rowStart;
                while (row <= model.rowEnd) {
                    int height = coreService.getTileHeight(row);
                    TileHolder tile = coreService.getActiveTile(column, row);
                    if (tile != null) {
                        canvas.save();
                        canvas.translate(x, y);
                        tile.draw(canvas);
                        canvas.restore();
                    }

                    if (row == model.rowEnd) break;
                    row++;
                    y += height;
                }
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

    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        this.disallowIntercept = disallowIntercept;
        coreService.requestDisallowInterceptTouchEvent(disallowIntercept);
    }

    @Override
    public void computeScroll() {
        super.computeScroll();
        coreService.computeScroll();
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
        removeLongPress();
        resetTouchTarget();
        coreService.resetAnimator();
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
        coreService.setTileWidth(column, width);
    }

    public void setTileHeight(int row, int height) {
        coreService.setTileHeight(row, height);
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

    public void updateAll() {
        coreService.updateAll();
    }

    public Adapter getAdapter() {
        return adapter;
    }

    public void setAdapter(Adapter adapter) {
        if (this.adapter != adapter) {
            resetTouchTarget();
            removeLongPress();
            coreService.reset();
        }
        this.adapter = adapter;
        requestLayout();
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

    public void setDebugMode(boolean enabled) {
        if (debugMode == enabled) return;
        debugMode = enabled;
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
                public LongSparseArray<? extends TileCoreService.BaseTileHolder> getDyingTiles() {
                    return coreService.getDyingTiles();
                }
                @Override
                public void postInvalidateOnAnimation() {
                    TileView.this.postInvalidateOnAnimation();
                }

                @Override
                public long getSyncTime() {
                    return coreService.getSyncTime();
                }

                @Override
                public long getLayoutTime() {
                    return coreService.getLayoutTime();
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
    }

    public TileHolder findTileAt(float viewX, float viewY, int[] outPos, float[] outLoc) {
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

    private MotionEvent toTileEvent(MotionEvent viewEvent) {
        MotionEvent tileEvent = MotionEvent.obtain(viewEvent);
        float offsetX = getPaddingLeft() + touchTargetLoc[0];
        float offsetY = getPaddingTop() + touchTargetLoc[1];
        tileEvent.offsetLocation(-offsetX, -offsetY);
        return tileEvent;
    }

    private void resetTouchTarget() {
        touchTarget = null;
        touchTargetPos[0] = 0;
        touchTargetPos[1] = 0;
        touchTargetLoc[0] = 0;
        touchTargetLoc[1] = 0;
    }

    private void removeLongPress() {
        if (longPressRunnable != null) {
            removeCallbacks(longPressRunnable);
            longPressRunnable = null;
        }
    }

    public static abstract class Adapter extends TileAdapter<TileHolder> {
    }

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

    }

}
