package com.ahheng.tile2d.widget.layout;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.LongSparseArray;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import com.ahheng.tile2d.TileCoreService;
import com.ahheng.tile2d.TileLayoutModel;
import com.ahheng.tile2d.widget.TileAdapter;
import com.ahheng.tile2d.widget.TileDimenProvider;
import com.ahheng.tile2d.widget.debug.DebugLayer;

public class TileLayout extends ViewGroup {

    private TileCoreService<TileHolder> coreService;
    private Adapter adapter;

    private final TileCoreService.CoreInterface<TileHolder> coreInterface = new TileCoreService.CoreInterface<TileHolder>() {
        @Override
        public void updateUI() {
            layoutTiles();
            TileLayout.this.postInvalidateOnAnimation();
        }

        @Override
        public void onTileIn(TileHolder holder, int column, int row) {
            addViewInLayout(holder.itemView, -1, holder.itemView.getLayoutParams(), false);
        }

        @Override
        public void onTileOut(TileHolder holder, int column, int row) {
            removeViewInLayout(holder.itemView);
        }
        
        @Override
        public void onTileRecycled(TileHolder holder, int column, int row) {
            holder.view = null;
        }

        @Override
        public void onTileBind(TileHolder holder, int column, int row) {
            if (holder.itemView.getLayoutParams() == null) {
                holder.itemView.setLayoutParams(new ViewGroup.LayoutParams(-2, -2));
            }
            holder.view = TileLayout.this;
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
                row++;
            }
            x += coreService.getTileWidth(column);
            column++;
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (adapter != null && coreService.getActiveTileCount() == 0) {
            if (overrideInitLocation) {
                overrideInitLocation = false;
                coreService.seek(initLocationColumn, initLocationRow, initOffsetX, initOffsetY);
            } else {
                coreService.seek(adapter.getLeftBound(), adapter.getTopBound(), 0, 0);
            }
        }
    }

    @Override
    public void requestLayout() {
        super.requestLayout();
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
                public LongSparseArray<? extends TileCoreService.BaseTileHolder> getDyingTiles() {
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
                public long getLayoutTime() {
                    return coreService.getLayoutTime();
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

    public void smoothOffset(float dx, float dy) {
    	if (isEmpty()) {
            return;
        }
        coreService.smoothSync(dx, dy);
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

    public TileLayoutModel getLayoutModel() {
        return coreService.getLayoutModel().newInstance();
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

    public Adapter getAdapter() {
        return adapter;
    }

    public void setAdapter(Adapter adapter) {
        if (this.adapter != adapter) {
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

}
