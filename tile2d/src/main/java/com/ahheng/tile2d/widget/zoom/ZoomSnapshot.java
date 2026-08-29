package com.ahheng.tile2d.widget.zoom;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

// 缩放快照(安卓渲染端共用)
// 双指缩放期间接管渲染:把缩放开始瞬间的画面固化为位图,全程只对这张位图做平移与缩放,
// 布局引擎与瓦片体系完全不参与,抬手结算后才释放
public class ZoomSnapshot {

    private final Renderer renderer;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);

    private Bitmap bitmap;
    private boolean active;

    // 快照的屏幕空间变换:先平移再缩放(与 TileCoreService 结算公式一致)
    private float scale = 1;
    private float translateX;
    private float translateY;

    public ZoomSnapshot(Renderer renderer) {
        this.renderer = renderer;
    }

    // 是否处于快照渲染模式
    public boolean isActive() {
        return active;
    }

    // 截取当前画面:尺寸非法或位图申请失败时返回 false(调用方据此放弃进入缩放模式)
    public boolean capture() {
        int width = renderer.getSnapshotWidth();
        int height = renderer.getSnapshotHeight();
        if (width <= 0 || height <= 0) return false;

        // 尺寸一致时复用上一张位图,避免连续捏合反复申请大块内存
        if (bitmap != null && (bitmap.isRecycled() || bitmap.getWidth() != width || bitmap.getHeight() != height)) {
            releaseBitmap();
        }
        if (bitmap == null) {
            try {
                bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            } catch (OutOfMemoryError e) {
                // 内存不足时降级:不进入快照模式,由调用方退回即时缩放或忽略手势
                bitmap = null;
                return false;
            }
        }
        bitmap.eraseColor(0);
        // 内容按屏幕坐标原样绘制,保证快照与当帧画面像素级一致
        renderer.drawSnapshotContent(new Canvas(bitmap));

        active = true;
        scale = 1;
        translateX = 0;
        translateY = 0;
        renderer.invalidateSnapshot();
        return true;
    }

    // 更新快照变换
    public void update(float scale, float translateX, float translateY) {
        if (!active) return;
        this.scale = scale;
        this.translateX = translateX;
        this.translateY = translateY;
        renderer.invalidateSnapshot();
    }

    // 退出快照渲染模式并释放位图
    public void release() {
        if (!active && bitmap == null) return;
        active = false;
        scale = 1;
        translateX = 0;
        translateY = 0;
        releaseBitmap();
        renderer.invalidateSnapshot();
    }

    // 绘制快照:与结算公式对应,先平移后缩放
    public void draw(Canvas canvas) {
        if (!active || bitmap == null || bitmap.isRecycled()) return;
        int count = canvas.save();
        canvas.translate(translateX, translateY);
        canvas.scale(scale, scale);
        canvas.drawBitmap(bitmap, 0, 0, paint);
        canvas.restoreToCount(count);
    }

    private void releaseBitmap() {
        if (bitmap != null) {
            bitmap.recycle();
            bitmap = null;
        }
    }

    // 渲染端适配接口
    public interface Renderer {

        // 快照位图宽度(通常为视图宽度)
        int getSnapshotWidth();

        // 快照位图高度(通常为视图高度)
        int getSnapshotHeight();

        // 把当前内容按屏幕坐标绘制到快照画布
        void drawSnapshotContent(Canvas canvas);

        // 请求重绘
        void invalidateSnapshot();

    }

}
