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

import java.util.Locale;
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
            collectStats();
            long now = System.nanoTime();
            if (now - lastFpsUpdateTime >= 1_000_000_000L) {
                actualFps = (int) frameCount;
                frameCount = 0;
                lastFpsUpdateTime = now;
                settleAverages();
                callback.postInvalidateOnAnimation();
            }
            choreographer.postFrameCallback(this);
        }
    };

    private long frameCount;
    private long lastFpsUpdateTime;
    private int actualFps;
    private long drawStart = -1;
    private long drawEnd = -1;

    private long sumDrawTime = 0;
    private long sumSyncTime = 0;
    private long sumLayoutTime = 0;
    private long drawSampleCount = 0;
    private long syncSampleCount = 0;
    private long layoutSampleCount = 0;

    private long lastSyncTime = -1;
    private long lastLayoutTime = -1;

    private String fpsText = "实际帧率：0Hz";
    private String theoreticalFpsText = "理论帧率：0Hz";
    private String syncTimeText = "同步耗时：0ns";
    private String layoutTimeText = "布局耗时：0ns";
    private String activeTileText = "活跃瓦片：0";
    private String recycledTileText = "回收瓦片：0";
    private String dyingTileText = "濒死瓦片：0";
    private String layoutRangeText = "布局范围：0,0 0,0";
    private String offsetText = "当前位置：0,0";
    private String dimensionText = "内容尺寸：0/0";

    private int cachedActiveTileCount = -1;
    private int cachedRecycledTileCount = -1;
    private int cachedDyingTileCount = -1;
    private int cachedColStart;
    private int cachedRowStart;
    private int cachedColEnd;
    private int cachedRowEnd;
    private float cachedOffsetX;
    private float cachedOffsetY;
    private int cachedTotalWidth;
    private int cachedTotalHeight;

    public DebugLayer(Context context, Callback callback) {
        this(new Paint(Paint.ANTI_ALIAS_FLAG) {
            {
                Resources r = context.getResources();
                setColor(0xff333333);
                setStyle(Style.STROKE);
                setStrokeWidth(dpToPx(r, 1));
                setShadowLayer(dpToPx(r, 2), 0, 0, 0xffefefef);
            }
        },
        new Paint(Paint.ANTI_ALIAS_FLAG) {
            {
                Resources r = context.getResources();
                setColor(0xffefefef);
                setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
                setTextSize(dpToPx(r, 12));
                setShadowLayer(dpToPx(r, 3), 0, 0, 0xff333333);
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
        }, dpToPx(context.getResources(), 4), callback);
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
        lastSyncTime = -1;
        lastLayoutTime = -1;
        drawStart = -1;
        drawEnd = -1;
        choreographer.postFrameCallback(frameCallback);
    }

    public void end() {
        choreographer.removeFrameCallback(frameCallback);
    }

    public void startDraw() {
        drawStart = System.nanoTime();
    }

    private void collectStats() {
        if (drawEnd > drawStart) {
            sumDrawTime += (drawEnd - drawStart);
            drawSampleCount++;
        }

        TileLayoutModel model = callback.getLayoutModel();
        long sync = callback.getSyncTime();
        if (sync != lastSyncTime) {
            sumSyncTime += sync;
            syncSampleCount++;
            lastSyncTime = sync;
        }

        long layout = callback.getLayoutTime();
        if (layout != lastLayoutTime) {
            sumLayoutTime += layout;
            layoutSampleCount++;
            lastLayoutTime = layout;
        }

        int activeTileCount = callback.getActiveTileCount();
        if (activeTileCount != cachedActiveTileCount) {
            cachedActiveTileCount = activeTileCount;
            activeTileText = "活跃瓦片：" + activeTileCount;
        }

        int recycledTileCount = callback.getRecycledTileCount();
        if (recycledTileCount != cachedRecycledTileCount) {
            cachedRecycledTileCount = recycledTileCount;
            recycledTileText = "回收瓦片：" + recycledTileCount;
        }

        LongSparseArray<? extends TileCoreService.BaseTileHolder> dyingTiles = callback.getDyingTiles();
        int dyingTileCount = dyingTiles.size();
        if (dyingTileCount != cachedDyingTileCount) {
            cachedDyingTileCount = dyingTileCount;
            dyingTileText = "濒死瓦片：" + dyingTileCount;
        }

        if (model.colStart != cachedColStart || model.rowStart != cachedRowStart 
                || model.colEnd != cachedColEnd || model.rowEnd != cachedRowEnd) {
            cachedColStart = model.colStart;
            cachedRowStart = model.rowStart;
            cachedColEnd = model.colEnd;
            cachedRowEnd = model.rowEnd;
            layoutRangeText = "布局范围：" + cachedColStart + "," + cachedRowStart + " " + cachedColEnd + "," + cachedRowEnd;
        }
        
        if (model.offsetX != cachedOffsetX || model.offsetY != cachedOffsetY) {
            cachedOffsetX = model.offsetX;
            cachedOffsetY = model.offsetY;
            offsetText = String.format(Locale.getDefault(), "当前位置：%.2f,%.2f", cachedOffsetX, cachedOffsetY);
        }
        
        if (model.totalWidth != cachedTotalWidth || model.totalHeight != cachedTotalHeight) {
            cachedTotalWidth = model.totalWidth;
            cachedTotalHeight = model.totalHeight;
            dimensionText = "内容尺寸：" + cachedTotalWidth + "/" + cachedTotalHeight;
        }
    }

    private void settleAverages() {
        fpsText = "实际帧率：" + actualFps + "Hz";

        if (drawSampleCount > 0) {
            long avgDrawTime = sumDrawTime / drawSampleCount;
            long avgTheoreticalFps = avgDrawTime > 0 ? 1_000_000_000L / avgDrawTime : 0;
            theoreticalFpsText = "理论帧率：" + avgTheoreticalFps + "Hz";
        } else {
            theoreticalFpsText = "理论帧率：0Hz";
        }

        if (syncSampleCount > 0) {
            syncTimeText = "同步耗时：" + (sumSyncTime / syncSampleCount) + "ns";
        }

        if (layoutSampleCount > 0) {
            layoutTimeText = "布局耗时：" + (sumLayoutTime / layoutSampleCount) + "ns";
        }

        sumDrawTime = 0; drawSampleCount = 0;
        sumSyncTime = 0; syncSampleCount = 0;
        sumLayoutTime = 0; layoutSampleCount = 0;
    }

    public void draw(Canvas canvas) {
        // 在这里结束统计，确保结果仅包含非测试数据
        drawEnd = System.nanoTime();
        
        TileLayoutModel model = callback.getLayoutModel();
        LongSparseArray<? extends TileCoreService.BaseTileHolder> dyingTiles = callback.getDyingTiles();
        Rect bounds = callback.getBounds();
        
        // 开始绘制濒死区
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

        // 开始绘制数据面板
        float lineHeight = infoPaint.getTextSize() * 1.25f;
        float ix = infoMargin;
        float iy = infoMargin + infoPaint.getTextSize();

        canvas.drawText(fpsText, ix, iy, infoPaint);
        iy += lineHeight;
        canvas.drawText(theoreticalFpsText, ix, iy, infoPaint);
        iy += lineHeight;
        canvas.drawText(syncTimeText, ix, iy, infoPaint);
        iy += lineHeight;
        canvas.drawText(layoutTimeText, ix, iy, infoPaint);
        iy += lineHeight;
        canvas.drawText(activeTileText, ix, iy, infoPaint);
        iy += lineHeight;
        canvas.drawText(recycledTileText, ix, iy, infoPaint);
        iy += lineHeight;
        canvas.drawText(dyingTileText, ix, iy, infoPaint);
        iy += lineHeight;
        canvas.drawText(layoutRangeText, ix, iy, infoPaint);
        iy += lineHeight;
        canvas.drawText(offsetText, ix, iy, infoPaint);
        iy += lineHeight;
        canvas.drawText(dimensionText, ix, iy, infoPaint);
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

        long getSyncTime();

        long getLayoutTime();
        
    }

    private static float dpToPx(Resources res, float dp) {
        return res.getDisplayMetrics().density * dp;
    }

}
