package com.ahheng.tile2d.widget.canvas;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;

import com.ahheng.tile2d.TileCoreService;
import com.ahheng.tile2d.TileLayoutModel;
import com.ahheng.tile2d.widget.TileAdapter;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

public class TileView extends View implements TileCoreService.CoreInterface {

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

    public TileView(Context context) {
        super(context);
        init();
    }
    public TileView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    private void init() {
        this.coreService = new TileCoreService<>(getContext(), this);
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        coreService.setDefaultTileWidth((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 80, displayMetrics));
        coreService.setDefaultTileHeight((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 45, displayMetrics));
        
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
                    postInvalidateOnAnimation(); // 每秒刷新一次数字
                }
                choreographer.postFrameCallback(this);
            }
        };
    }

    public void seek(int column, int row, float offsetX, float offsetY) {
        overrideInitLocation = true;
        initLocationColumn = column;
        initLocationRow = row;
        initOffsetX = offsetX;
        initOffsetY = offsetY;
        post(() -> coreService.seek(column, row, offsetX, offsetY));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        coreService.setBounds(getPaddingLeft(), getPaddingTop(), getWidth() - getPaddingRight(), getHeight() - getPaddingBottom());
        if (coreService.getAdapter() != null && coreService.getActiveTileCount() == 0) {
            // 首次加载
            if (overrideInitLocation) {
                coreService.seek(initLocationColumn, initLocationRow, initOffsetX, initOffsetY);
            } else {
                seek(coreService.getAdapter().getLeftBound(), coreService.getAdapter().getTopBound(), 0, 0);
            }
        }
        overrideInitLocation = false;
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
            // 遍历濒死区
            for (Long2ObjectOpenHashMap.Entry<TileHolder> entry : coreService.getDyingTiles().long2ObjectEntrySet()) {
                long id = entry.getLongKey();
                int c = TileCoreService.getColumn(id);
                int r = TileCoreService.getRow(id);
    
                // 对称计算偏移
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
                canvas.save();
                canvas.translate(getPaddingLeft(), getPaddingTop());
                canvas.translate(model.offsetX, model.offsetY);
                canvas.translate(x, y);
    
                tile.draw(canvas); // 先画瓦片本体
                canvas.drawRect(0, 0, tile.getWidth(), tile.getHeight(), dyingOverlayPaint); // 再盖半透红
    
                canvas.restore();
            }
            
            canvas.drawRect(coreService.getBounds(), boundPaint);
            long drawTime = System.nanoTime() - drawStart;
            long theoreticalFps = drawTime > 0 ? 1_000_000_000L / drawTime : 0;
    
            float lineHeight = infoPaint.getTextSize() * 1.25f;
            float ix = infoMargin;
            float iy = infoMargin + infoPaint.getTextSize(); // 第一行 baseline
    
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
        return coreService.handleTouchEvent(event);
    }

    @Override
    public void computeScroll() {
        super.computeScroll();
        coreService.computeScroll();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        lastFpsUpdateTime = System.nanoTime();
        choreographer.postFrameCallback(frameCallback);
    }
    
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        choreographer.removeFrameCallback(frameCallback);
    }

    @Override
    public void updateUI() {
        postInvalidateOnAnimation();
    }

    @Override
    public void onTileIn(TileCoreService.BaseTileHolder holder, int column, int row) {}

    @Override
    public void onTileOut(TileCoreService.BaseTileHolder holder, int column, int row) {}

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

    public void setDebugMode(boolean enabled) {
    	coreService.setDebugMode(enabled);
        if (coreService.isDebugMode()) {
            boundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            boundPaint.setColor(0xff008aff);
            boundPaint.setStyle(Paint.Style.STROKE);
            boundPaint.setStrokeWidth(2);
            
            infoPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            infoPaint.setColor(0xff000000);
            infoPaint.setTextSize(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics()));
            infoMargin = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 4, getResources().getDisplayMetrics());
            dyingOverlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            dyingOverlayPaint.setColor(0x60FF0000);
        } else {
            boundPaint = null;
            infoPaint = null;
            dyingOverlayPaint = null;
        }
    }

    public static abstract class Adapter <T extends TileHolder> extends TileAdapter<T> {
    }

    public static class TileHolder extends TileCoreService.BaseTileHolder {

        private TileView view;

        public void draw(Canvas canvas) {}

    }

}
