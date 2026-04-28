package com.ahheng.tile2d.widget.canvas;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import com.ahheng.tile2d.TileCoreService;
import com.ahheng.tile2d.TileLayoutModel;
import com.ahheng.tile2d.widget.TileAdapter;
import com.ahheng.tile2d.widget.TileDimenProvider;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

public class TileView extends View implements TileCoreService.CoreInterface<TileView.TileHolder> {

    private TileCoreService<TileHolder> coreService;

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

    private TileHolder touchTarget;
    private final int[] touchTargetPos = new int[2];
    private final float[] touchTargetLoc = new float[2];
    private boolean disallowIntercept;

    private float touchDownX;
    private float touchDownY;
    private boolean isClickCandidate;
    private Runnable longPressRunnable;
    private long longPressTimeout = 600L;
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
        this.coreService = new TileCoreService<>(getContext(), this);
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        coreService.setDefaultTileWidth((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 80, displayMetrics));
        coreService.setDefaultTileHeight((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 45, displayMetrics));
    }

    public void offset(float dx, float dy) {
    	if (isEmpty() || coreService.getActiveTileCount() == 0) return;
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

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        coreService.setBounds(getPaddingLeft(), getPaddingTop(), getWidth() - getPaddingRight(), getHeight() - getPaddingBottom());
        if (overrideInitLocation) {
            overrideInitLocation = false;
            coreService.seek(initLocationColumn, initLocationRow, initOffsetX, initOffsetY);
        } else if (coreService.getAdapter() != null && coreService.getActiveTileCount() == 0) {
            coreService.seek(coreService.getAdapter().getLeftBound(), coreService.getAdapter().getTopBound(), 0, 0);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long drawStart = System.nanoTime();
        TileLayoutModel model = coreService.getLayoutModel();
        if (model.colStart <= model.colEnd && model.rowStart <= model.rowEnd) {
            canvas.save();
            if (!coreService.isDebugMode()) canvas.clipRect(coreService.getBounds());
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
        if (coreService.isDebugMode()) {
            for (Long2ObjectOpenHashMap.Entry<TileHolder> entry : coreService.getDyingTiles().long2ObjectEntrySet()) {
                long id = entry.getLongKey();
                int c = TileCoreService.getColumn(id);
                int r = TileCoreService.getRow(id);
    
                float x = 0;
                int col = model.colStart;
                while (col > c) {
                    col--;
                    x -= coreService.getTileWidth(col);
                }
                while (col < c) {
                    x += coreService.getTileWidth(col);
                    col++;
                }
    
                float y = 0;
                int row = model.rowStart;
                while (row > r) {
                    row--;
                    y -= coreService.getTileHeight(row);
                }
                while (row < r) {
                    y += coreService.getTileHeight(row);
                    row++;
                }
    
                TileHolder tile = entry.getValue();
                if (tile == null) continue;
                canvas.save();
                canvas.translate(getPaddingLeft(), getPaddingTop());
                canvas.translate(model.offsetX, model.offsetY);
                canvas.translate(x, y);
    
                tile.draw(canvas);
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
            canvas.drawText("同步耗时：" + coreService.getSyncTime() + "ns", ix, iy, infoPaint);
            iy += lineHeight;
            canvas.drawText("活跃瓦片：" + coreService.getActiveTileCount(), ix, iy, infoPaint);
            iy += lineHeight;
            canvas.drawText("回收瓦片：" + coreService.getRecycledTileCount(), ix, iy, infoPaint);
            iy += lineHeight;
            canvas.drawText("濒死瓦片：" + coreService.getDyingTileCount(), ix, iy, infoPaint);
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

            float contentX = event.getX() - getPaddingLeft();
            float contentY = event.getY() - getPaddingTop();
            touchTarget = findTileAt(contentX, contentY, touchTargetPos, touchTargetLoc);

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

            if (intercepted || action == MotionEvent.ACTION_CANCEL) {
                MotionEvent tileEvent = toTileEvent(event);
                tileEvent.setAction(MotionEvent.ACTION_CANCEL);
                touchTarget.onTouchEvent(tileEvent);
                tileEvent.recycle();
                resetTouchTarget();
                removeLongPress();
                isClickCandidate = false;
            } else {
                MotionEvent tileEvent = toTileEvent(event);
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
        if (choreographer != null && frameCallback != null) {
            lastFpsUpdateTime = System.nanoTime();
            choreographer.postFrameCallback(frameCallback);
        }
    }
    
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (choreographer != null && frameCallback != null) choreographer.removeFrameCallback(frameCallback);
    }

    @Override
    public void updateUI() {
        postInvalidateOnAnimation();
    }

    @Override
    public void onTileIn(TileHolder holder, int column, int row) {
        holder.view = this;
    }

    @Override
    public void onTileOut(TileHolder holder, int column, int row) {
        holder.view = null;
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

    public Adapter<?> getAdapter() {
        return (Adapter<?>) coreService.getAdapter();
    }

    public void setAdapter(Adapter adapter) {
        if (coreService.getAdapter() != adapter) {
            resetTouchTarget();
            removeLongPress();
        }
        coreService.setAdapter(adapter);
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

    public void setDebugMode(boolean enabled) {
    	coreService.setDebugMode(enabled);
        if (coreService.isDebugMode()) {
            boundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            boundPaint.setColor(0xff008aff);
            boundPaint.setStyle(Paint.Style.STROKE);
            boundPaint.setStrokeWidth(2);
            
            infoPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            infoPaint.setColor(0xff333333);
            infoPaint.setTypeface(Typeface.create((Typeface) null, Typeface.BOLD));
            infoPaint.setShadowLayer(4, 0, 0, 0xffcdcdcd);
            infoPaint.setTextSize(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics()));
            infoMargin = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 4, getResources().getDisplayMetrics());
            dyingOverlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            dyingOverlayPaint.setColor(0x60FF0000);
            
            this.choreographer = Choreographer.getInstance();
            this.frameCallback = new Choreographer.FrameCallback() {
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
                choreographer.postFrameCallback(frameCallback);
            }
        } else {
            boundPaint = null;
            infoPaint = null;
            dyingOverlayPaint = null;
            choreographer.removeFrameCallback(frameCallback);
            choreographer = null;
            frameCallback = null;
        }
        postInvalidateOnAnimation();
    }

    public TileHolder findTileAt(float contentX, float contentY, int[] outPos, float[] outLoc) {
        TileLayoutModel model = coreService.getLayoutModel();
        float x = model.offsetX;
        int col = model.colStart;
        while (col <= model.colEnd) {
            int width = coreService.getTileWidth(col);
            if (contentX >= x && contentX < x + width) {
                float y = model.offsetY;
                int row = model.rowStart;
                while (row <= model.rowEnd) {
                    int height = coreService.getTileHeight(row);
                    if (contentY >= y && contentY < y + height) {
                        if (outPos != null) {
                            outPos[0] = col;
                            outPos[1] = row;
                        }
                        if (outLoc != null) {
                            outLoc[0] = x;
                            outLoc[1] = y;
                        }
                        return coreService.getActiveTile(col, row);
                    }
                    y += height;
                    if (row == model.rowEnd) break;
                    row++;
                }
            }
            x += width;
            if (col == model.colEnd) break;
            col++;
        }
        return null;
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

    public static abstract class Adapter <T extends TileHolder> extends TileAdapter<T> {
    }

    public static class TileHolder extends TileCoreService.BaseTileHolder {

        private TileView view;

        public void draw(Canvas canvas) {}
        
        public boolean onTouchEvent(MotionEvent event) {
            return false;
        }

        public boolean onClick() {
            return false;
        }

        public void onLongClick() {}
        
        public void postInvalidate() {
            if (view != null) view.postInvalidate();
        }

        public void postInvalidateOnAnimation() {
        	if (view != null) view.postInvalidateOnAnimation();
        }

    }

}
