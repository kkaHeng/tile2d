package com.ahheng.tile2d.app.engine;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

// 视窗/内容拖拽交互演示视图
// 仅交互演示,不接入真实引擎
// MODE_TRADITIONAL: 绝对坐标,内容位置固定,视窗可左右拖拽(视窗如放大镜在尺子上滚动)
// MODE_TILE2D: 逻辑坐标 + 像素偏移,视窗固定居中,只能左右拖拽内容(视窗如摄像机固定,内容流动)
class WindowParadigmView extends View {

    static final int MODE_TRADITIONAL = 0;
    static final int MODE_TILE2D = 1;

    private static final int[] PALETTE = {
            Color.rgb(88, 140, 200), Color.rgb(110, 180, 110), Color.rgb(220, 160, 80),
            Color.rgb(190, 100, 150), Color.rgb(120, 150, 210), Color.rgb(80, 180, 170),
            Color.rgb(200, 130, 90), Color.rgb(140, 120, 200), Color.rgb(170, 190, 90),
            Color.rgb(210, 110, 110), Color.rgb(90, 160, 130), Color.rgb(180, 140, 60),
    };

    private static final int BG = 0xFF1B1E24;
    private static final int TRACK = 0xFF2A2F38;
    private static final int TILE_BORDER = 0x33000000;
    private static final int DIM = 0xCC000000;
    private static final int WINDOW_FRAME = 0xFF4FC3F7;
    private static final int TEXT_COLOR = 0xFFB0BEC5;
    private static final int HINT_COLOR = 0xFF78909C;

    private final int mode;
    private final int tileCount;
    private final int tileW;       // 瓦片宽(px)
    private final int windowTiles; // 视窗覆盖瓦片数

    private final Paint bgPaint = new Paint();
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tilePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tileBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint numberPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dimPaint = new Paint();
    private final Paint framePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint statusPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float contentWidth;   // 内容条带总宽
    private float windowW;        // 视窗宽
    private int bandTop;          // 条带顶(px)
    private int bandHeight;       // 条带高(px)
    private float fixedLeft;      // 固定方左边缘
    private float movableLeft;    // 可动方左边缘
    private float initialMovableLeft;

    private boolean dragging;
    private float downX;
    private float dragStartLeft;
    private VelocityTracker tracker;
    private ValueAnimator flingAnimator;

    private final RectF rect = new RectF();

    WindowParadigmView(Context context, int mode, int tileCount, int tileW, int windowTiles) {
        super(context);
        this.mode = mode;
        this.tileCount = tileCount;
        this.tileW = tileW;
        this.windowTiles = windowTiles;
        initPaints();
    }

    private void initPaints() {
        bgPaint.setColor(BG);
        trackPaint.setColor(TRACK);
        trackPaint.setStyle(Paint.Style.FILL);
        tilePaint.setStyle(Paint.Style.FILL);
        tileBorderPaint.setStyle(Paint.Style.STROKE);
        tileBorderPaint.setStrokeWidth(dp(1));
        tileBorderPaint.setColor(TILE_BORDER);
        numberPaint.setColor(0xE6FFFFFF);
        numberPaint.setTextAlign(Paint.Align.CENTER);
        numberPaint.setTextSize(sp(13));
        dimPaint.setColor(DIM);
        framePaint.setStyle(Paint.Style.STROKE);
        framePaint.setStrokeWidth(dp(3));
        framePaint.setColor(WINDOW_FRAME);
        labelBgPaint.setColor(WINDOW_FRAME);
        labelTextPaint.setColor(0xFF0D1B24);
        labelTextPaint.setTextAlign(Paint.Align.CENTER);
        labelTextPaint.setTextSize(sp(11));
        labelTextPaint.setFakeBoldText(true);
        hintPaint.setColor(HINT_COLOR);
        hintPaint.setTextSize(sp(13));
        hintPaint.setTextAlign(Paint.Align.CENTER);
        statusPaint.setColor(TEXT_COLOR);
        statusPaint.setTextSize(sp(12));
        statusPaint.setTextAlign(Paint.Align.CENTER);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        contentWidth = tileCount * tileW;
        windowW = windowTiles * tileW;
        bandHeight = dp(120);
        bandTop = h / 2 - bandHeight / 2;
        if (mode == MODE_TRADITIONAL) {
            fixedLeft = (w - contentWidth) / 2f;      // 内容固定,居中
            initialMovableLeft = (w - windowW) / 2f;  // 视窗默认居中
        } else {
            fixedLeft = (w - windowW) / 2f;           // 视窗固定,居中
            initialMovableLeft = (w - contentWidth) / 2f; // 内容默认居中
        }
        movableLeft = clamp(initialMovableLeft);
    }

    // 可动方左边缘的合法范围,保证视窗始终被内容覆盖
    private float minMovable() {
        if (mode == MODE_TRADITIONAL) return 0; // 视窗左边缘不超出屏幕左缘
        return fixedLeft + windowW - contentWidth;
    }

    private float maxMovable() {
        if (mode == MODE_TRADITIONAL) return getWidth() - windowW; // 视窗右边缘不超出屏幕右缘
        return fixedLeft;
    }

    private float clamp(float v) {
        return Math.max(minMovable(), Math.min(maxMovable(), v));
    }

    // 触摸
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                stopFling();
                dragging = true;
                downX = event.getX();
                dragStartLeft = movableLeft;
                obtainTracker().addMovement(event);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (dragging) {
                    float dx = event.getX() - downX;
                    movableLeft = clamp(dragStartLeft + dx);
                    tracker.addMovement(event);
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (dragging) {
                    tracker.addMovement(event);
                    tracker.computeCurrentVelocity(1000);
                    float vx = tracker.getXVelocity();
                    dragging = false;
                    releaseTracker();
                    startFling(vx);
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                releaseTracker();
                movableLeft = clamp(movableLeft);
                invalidate();
                return true;
        }
        return super.onTouchEvent(event);
    }

    private VelocityTracker obtainTracker() {
        if (tracker == null) tracker = VelocityTracker.obtain();
        else tracker.clear();
        return tracker;
    }

    private void releaseTracker() {
        if (tracker != null) {
            tracker.recycle();
            tracker = null;
        }
    }

    // 松手后按滑动速度惯性滚动,带衰减与边界回弹
    private void startFling(float vx) {
        if (Math.abs(vx) < 400) {
            movableLeft = clamp(movableLeft);
            invalidate();
            return;
        }
        float start = movableLeft;
        float target = movableLeft + vx * 0.35f;
        flingAnimator = ValueAnimator.ofFloat(0f, 1f);
        flingAnimator.setDuration(500);
        flingAnimator.setInterpolator(new DecelerateInterpolator());
        flingAnimator.addUpdateListener(a -> {
            float t = a.getAnimatedFraction();
            movableLeft = clamp(start + (target - start) * t);
            invalidate();
        });
        flingAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                movableLeft = clamp(movableLeft);
                invalidate();
            }
        });
        flingAnimator.start();
    }

    private void stopFling() {
        if (flingAnimator != null) {
            flingAnimator.cancel();
            flingAnimator = null;
        }
    }

    // 绘制
    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        canvas.drawColor(BG);

        // 内容左边缘与视窗左边缘(依模式取固定/可动方)
        float cLeft = (mode == MODE_TRADITIONAL) ? fixedLeft : movableLeft;
        float wLeft = (mode == MODE_TRADITIONAL) ? movableLeft : fixedLeft;
        float cRight = cLeft + contentWidth;
        float wRight = wLeft + windowW;

        drawTrack(canvas, cLeft, cRight);
        drawTiles(canvas, cLeft);
        drawDimOutside(canvas, cLeft, cRight, wLeft, wRight);
        drawWindow(canvas, wLeft, wRight);
        drawLabels(canvas, w, h);
    }

    // 内容条带底板
    private void drawTrack(Canvas canvas, float cLeft, float cRight) {
        rect.set(cLeft, bandTop - dp(14), cRight, bandTop + bandHeight + dp(14));
        canvas.drawRoundRect(rect, dp(10), dp(10), trackPaint);
    }

    // 逐瓦片色块 + 编号
    private void drawTiles(Canvas canvas, float cLeft) {
        float radius = dp(6);
        for (int i = 0; i < tileCount; i++) {
            float x0 = cLeft + i * tileW;
            rect.set(x0 + dp(3), bandTop + dp(3), x0 + tileW - dp(3), bandTop + bandHeight - dp(3));
            tilePaint.setColor(PALETTE[i % PALETTE.length]);
            canvas.drawRoundRect(rect, radius, radius, tilePaint);
            canvas.drawRoundRect(rect, radius, radius, tileBorderPaint);
            numberPaint.setColor(0xE6FFFFFF);
            canvas.drawText(String.valueOf(i),
                    rect.centerX(), centerYForText(rect.centerY(), numberPaint), numberPaint);
        }
    }

    // 视窗外的内容变暗
    private void drawDimOutside(Canvas canvas, float cLeft, float cRight, float wLeft, float wRight) {
        if (cLeft < wLeft) {
            rect.set(cLeft, bandTop - dp(14), Math.min(wLeft, cRight), bandTop + bandHeight + dp(14));
            canvas.drawRect(rect, dimPaint);
        }
        if (wRight < cRight) {
            rect.set(Math.max(wRight, cLeft), bandTop - dp(14), cRight, bandTop + bandHeight + dp(14));
            canvas.drawRect(rect, dimPaint);
        }
    }

    // 视窗框 + 顶部标签
    private void drawWindow(Canvas canvas, float wLeft, float wRight) {
        float top = bandTop - dp(24);
        float bottom = bandTop + bandHeight + dp(24);
        rect.set(wLeft, top, wRight, bottom);
        canvas.drawRoundRect(rect, dp(8), dp(8), framePaint);

        // 顶部"视窗"标签
        float labelW = dp(46);
        float labelH = dp(20);
        rect.set(wRight / 2f + wLeft / 2f - labelW / 2f, top - labelH / 2f, wRight / 2f + wLeft / 2f + labelW / 2f, top + labelH / 2f);
        canvas.drawRoundRect(rect, dp(10), dp(10), labelBgPaint);
        canvas.drawText("视窗", rect.centerX(), centerYForText(rect.centerY(), labelTextPaint), labelTextPaint);
    }

    private void drawLabels(Canvas canvas, int w, int h) {
        canvas.drawText(hint(), w / 2f, dp(36), hintPaint);
        // 两范式统一显示"视窗偏移": 传统为绝对坐标下的视窗偏移,
        // Tile2D 为逻辑坐标下的像素偏移(布局引擎 offsetX,范围 [-tileW, 0])
        String text = "视窗偏移 " + fmt(
                (mode == MODE_TRADITIONAL) ? movableLeft - initialMovableLeft : offsetX());
        canvas.drawText(text, w / 2f, h - dp(28), statusPaint);
    }

    private String hint() {
        if (mode == MODE_TRADITIONAL) return "视窗如放大镜,在固定内容上滚动";
        return "视窗如摄像机固定,内容流动";
    }

    // Tile2D 范式: 视窗固定,内容相对视窗移动
    // 定位 = 逻辑坐标(锚点列) + 像素偏移(offsetX),offsetX = 锚点瓦片左边缘相对视窗左边缘的偏移,
    // 同步后范围 [-tileW, 0]
    private float offsetX() {
        float p = fixedLeft - movableLeft; // 视窗左边缘在内容中的位置(内容左边缘为原点)
        int anchorCol = Math.floorDiv((int) p, tileW);
        return -((int) (p - anchorCol * tileW)); // 取负余数,范围 [-tileW, 0]
    }

    private static String fmt(float v) {
        int i = Math.round(v);
        if (i == 0) return "0px";
        return i > 0 ? "+" + i + "px" : i + "px";
    }

    private static float centerYForText(float centerY, Paint paint) {
        return centerY - (paint.ascent() + paint.descent()) / 2f;
    }

    @Override
    protected void onDetachedFromWindow() {
        stopFling();
        releaseTracker();
        super.onDetachedFromWindow();
    }

    private int dp(float v) {
        return (int) (getResources().getDisplayMetrics().density * v + 0.5f);
    }

    private float sp(float v) {
        return v * getResources().getDisplayMetrics().scaledDensity;
    }
}
