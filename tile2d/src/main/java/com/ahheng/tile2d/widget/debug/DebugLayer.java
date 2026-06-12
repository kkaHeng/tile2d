package com.ahheng.tile2d.widget.debug;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.util.LongSparseArray;
import android.util.TypedValue;
import android.view.Choreographer;

import com.ahheng.tile2d.TileCoreService;
import com.ahheng.tile2d.TileLayoutModel;

import java.util.Objects;

public class DebugLayer {

    private final Callback callback;
    private final Paint infoPaint;
    private final Paint boundPaint;
    private final Paint dyingOverlayPaint;
    private final float infoMargin;
    private final Choreographer choreographer;
    private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            frameCount++;
            long now = System.nanoTime();
            if (now - lastFpsUpdateTime >= 1_000_000_000L) {
                actualFps = (int) frameCount;
                frameCount = 0;
                lastFpsUpdateTime = now;
                callback.postInvalidateOnAnimation();
            }
            choreographer.postFrameCallback(this);
        }
    };

    private long frameCount;
    private long lastFpsUpdateTime;
    private int actualFps;
    private long drawStart;

    public DebugLayer(Context context, Callback callback) {
        this(new Paint(Paint.ANTI_ALIAS_FLAG) {
            {
                Resources r = context.getResources();
                setColor(0xff333333);
                setStyle(Style.STROKE);
                setStrokeWidth(dpTopx(r, 1));
                setShadowLayer(dpTopx(r, 2), 0, 0, 0xffefefef);
            }
        },
        new Paint(Paint.ANTI_ALIAS_FLAG) {
            {
                Resources r = context.getResources();
                setColor(0xffefefef);
                setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
                setTextSize(dpTopx(r, 12));
                setShadowLayer(dpTopx(r, 3), 0, 0, 0xff333333);
            }
        }, 
        new Paint(Paint.ANTI_ALIAS_FLAG) {
            {
                Resources r = context.getResources();
                TypedValue v = new TypedValue();
                Resources.Theme t = context.getTheme();
                t.resolveAttribute(android.R.attr.colorPrimary, v, true);
                int c = r.getColor(v.resourceId);
                setColor(Color.argb(128, Color.red(c), Color.green(c), Color.blue(c)));
            }
        }, dpTopx(context.getResources(), 4), callback);
    }

    public DebugLayer(Paint boundPaint, Paint infoPaint, Paint dyingOverlayPaint, float infoMargin, Callback callback) {
        this.boundPaint = Objects.requireNonNull(boundPaint);
        this.infoPaint = Objects.requireNonNull(infoPaint);
        this.dyingOverlayPaint = Objects.requireNonNull(dyingOverlayPaint);
        this.callback = Objects.requireNonNull(callback);
        this.infoMargin = infoMargin;
        this.choreographer = Choreographer.getInstance();
    }

    public void start() {
    	lastFpsUpdateTime = System.nanoTime();
        choreographer.postFrameCallback(frameCallback);
    }

    public void end() {
    	choreographer.removeFrameCallback(frameCallback);
    }

    public void startDraw() {
    	drawStart = System.nanoTime();
    }

    public void draw(Canvas canvas) {
        TileLayoutModel model = callback.getLayoutModel();
        LongSparseArray<? extends TileCoreService.BaseTileHolder> dyingTiles = callback.getDyingTiles();
        Rect bounds = callback.getBounds();
        
        canvas.save();
        canvas.translate(bounds.left, bounds.top);
        canvas.translate(model.offsetX, model.offsetY);
    	for (int i = 0; i < dyingTiles.size(); i++) {
            long id = dyingTiles.keyAt(i);
            int c = TileCoreService.getColumn(id);
            int r = TileCoreService.getRow(id);

            float x = 0;
            if (c < model.colStart) {
                x -= callback.getTileWidth(c);
            } else {
                for (int j = model.colStart; j < c; j++) {
                    x += callback.getTileWidth(j);
                }
            }

            float y = 0;
            if (r < model.rowStart) {
                y -= callback.getTileHeight(r);
            } else {
                for (int j = model.rowStart; j < r; j++) {
                    y += callback.getTileHeight(j);
                }
            }
            TileCoreService.BaseTileHolder tile = dyingTiles.valueAt(i);

            canvas.save();
            canvas.translate(x, y);
            canvas.drawRect(0, 0, tile.getWidth(), tile.getHeight(), dyingOverlayPaint);
            canvas.restore();
        }
        canvas.restore();
        canvas.drawRect(bounds, boundPaint);
        
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
        canvas.drawText("活跃瓦片：" + callback.getActiveTileCount(), ix, iy, infoPaint);
        iy += lineHeight;
        canvas.drawText("回收瓦片：" + callback.getRecycledTileCount(), ix, iy, infoPaint);
        iy += lineHeight;
        canvas.drawText("濒死瓦片：" + dyingTiles.size(), ix, iy, infoPaint);
        iy += lineHeight;
        canvas.drawText("布局范围：" + model.colStart + "," + model.colEnd + " " + model.rowStart + "," + model.rowEnd, ix, iy, infoPaint);
    }

    public interface Callback {
    
        int getActiveTileCount();
        
        int getRecycledTileCount();
    
        int getTileWidth(int column);
    
        int getTileHeight(int row);
        
        Rect getBounds();
        
        TileLayoutModel getLayoutModel();
        
        LongSparseArray<? extends TileCoreService.BaseTileHolder> getDyingTiles();
        
        void postInvalidateOnAnimation();
        
    }

    private static float dpTopx(Resources res, float dp) {
    	return res.getDisplayMetrics().density * dp;
    }

}
