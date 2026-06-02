package com.ahheng.tile2d.widget.layout;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.LongSparseArray;
import android.util.TypedValue;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import com.ahheng.tile2d.TileCoreService;
import com.ahheng.tile2d.TileLayoutModel;
import com.ahheng.tile2d.widget.TileAdapter;
import com.ahheng.tile2d.widget.TileDimenProvider;

public class TileLayout extends ViewGroup {

    private TileCoreService<TileHolder> coreService;
    private Adapter<TileHolder> adapter;

    private final TileCoreService.CoreInterface<TileHolder> coreInterface = new TileCoreService.CoreInterface<TileHolder>() {
        @Override
        public void updateUI() {
            layoutTiles();
            TileLayout.this.postInvalidateOnAnimation();
        }

        @Override
        public void onTileIn(TileHolder holder, int column, int row) {
            if (holder.itemView.getLayoutParams() == null) {
                holder.itemView.setLayoutParams(new ViewGroup.LayoutParams(-2, -2));
            }
            holder.view = TileLayout.this;
            addViewInLayout(holder.itemView, -1, holder.itemView.getLayoutParams(), true);
        }

        @Override
        public void onTileOut(TileHolder holder, int column, int row) {
            holder.view = null;
            removeViewInLayout(holder.itemView);
        }

        @Override
        public void onTileBind(TileHolder holder, int column, int row) {
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

    private Choreographer choreographer;
    private Choreographer.FrameCallback frameCallback;
    private long frameCount = 0;
    private long lastFpsUpdateTime = 0;
    private int actualFps = 0;
    private Paint infoPaint;
    private float infoMargin;
    private Paint boundPaint;
    private Paint dyingOverlayPaint;

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
        if (adapter != null) {
            if (overrideInitLocation) {
                overrideInitLocation = false;
                coreService.seek(initLocationColumn, initLocationRow, initOffsetX, initOffsetY);
            } else {
                coreService.seek(adapter.getLeftBound(), adapter.getTopBound(), 0, 0);
            }
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
        return coreService.isDebugMode();
    }

    public void setDebugMode(boolean enabled) {
        if (coreService.isDebugMode() == enabled) return;
        coreService.setDebugMode(enabled);

        if (enabled) {
            boundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            boundPaint.setColor(0xff008aff);
            boundPaint.setStyle(Paint.Style.STROKE);
            boundPaint.setStrokeWidth(2);

            infoPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            infoPaint.setColor(0xffefefef);
            infoPaint.setTypeface(Typeface.create((Typeface) null, Typeface.BOLD));
            infoPaint.setShadowLayer(6, 0, 0, 0xff333333);
            infoPaint.setTextSize(TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics()));
            infoMargin = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 4, getResources().getDisplayMetrics());

            dyingOverlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            dyingOverlayPaint.setColor(0x60FF0000);

            choreographer = Choreographer.getInstance();
            frameCallback = new Choreographer.FrameCallback() {
                @Override
                public void doFrame(long frameTimeNanos) {
                    frameCount++;
                    long now = System.nanoTime();
                    if (now - lastFpsUpdateTime >= 1_000_000_000L) {
                        actualFps = (int) frameCount;
                        frameCount = 0;
                        lastFpsUpdateTime = now;
                        postInvalidateOnAnimation();
                    }
                    choreographer.postFrameCallback(this);
                }
            };
            if (isAttachedToWindow()) {
                lastFpsUpdateTime = System.nanoTime();
                choreographer.postFrameCallback(frameCallback);
            }
            setClipToPadding(false);
        } else {
            boundPaint = null;
            infoPaint = null;
            dyingOverlayPaint = null;
            if (choreographer != null && frameCallback != null) {
                choreographer.removeFrameCallback(frameCallback);
            }
            choreographer = null;
            frameCallback = null;
            setClipToPadding(true);
        }
        postInvalidateOnAnimation();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (choreographer != null && frameCallback != null) {
            lastFpsUpdateTime = System.nanoTime();
            choreographer.postFrameCallback(frameCallback);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (choreographer != null && frameCallback != null) {
            choreographer.removeFrameCallback(frameCallback);
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        long drawStart = System.nanoTime();
        super.dispatchDraw(canvas);
        if (!coreService.isDebugMode()) return;
        TileLayoutModel model = coreService.getLayoutModel();
        LongSparseArray<TileHolder> dyingTiles = coreService.getDyingTiles();
        
        for (int i = 0; i < dyingTiles.size(); i++) {
            long id = dyingTiles.keyAt(i);
            int c = TileCoreService.getColumn(id);
            int r = TileCoreService.getRow(id);

            float x = 0;
            if (c < model.colStart) {
                x -= coreService.getTileWidth(c);
            } else {
                for (int j = model.colStart; j < c; j++) {
                    x += coreService.getTileWidth(j);
                }
            }

            float y = 0;
            if (r < model.rowStart) {
                y -= coreService.getTileHeight(r);
            } else {
                for (int j = model.rowStart; j < r; j++) {
                    y += coreService.getTileHeight(j);
                }
            }

            TileHolder tile = dyingTiles.valueAt(i);
            if (tile == null) continue;

            canvas.save();
            canvas.translate(getPaddingLeft(), getPaddingTop());
            canvas.translate(model.offsetX, model.offsetY);
            canvas.translate(x, y);
            canvas.drawRect(0, 0, tile.getWidth(), tile.getHeight(), dyingOverlayPaint);
            canvas.restore();
        }

        canvas.drawRect(coreService.getBounds(), boundPaint);

        long drawTime = System.nanoTime() - drawStart;
        long theoreticalFps = drawTime > 0 ? 1_000_000_000L / drawTime : 0;

        float lineHeight = infoPaint.getTextSize() * 1.25f;
        float ix = infoMargin;
        float iy = infoMargin + infoPaint.getTextSize();

        canvas.drawText("实际帧率：" + actualFps + "Hz", ix, iy, infoPaint);
        iy += lineHeight;
        canvas.drawText("理论帧率：" + theoreticalFps + "Hz", ix, iy, infoPaint);
        iy += lineHeight;
        canvas.drawText("同步耗时：" + model.syncTime + "ns", ix, iy, infoPaint);
        iy += lineHeight;
        canvas.drawText("布局耗时：" + model.layoutTime + "ns", ix, iy, infoPaint);
        iy += lineHeight;
        canvas.drawText("活跃瓦片：" + coreService.getActiveTileCount(), ix, iy, infoPaint);
        iy += lineHeight;
        canvas.drawText("回收瓦片：" + coreService.getRecycledTileCount(), ix, iy, infoPaint);
        iy += lineHeight;
        canvas.drawText("濒死瓦片：" + coreService.getDyingTileCount(), ix, iy, infoPaint);
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

    public Adapter<?> getAdapter() {
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

    public static abstract class Adapter <T extends TileHolder> extends TileAdapter<T> {
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
