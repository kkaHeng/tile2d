package com.ahheng.tile2d;

import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ViewConfiguration;
import android.widget.Scroller;

// 事件处理器(安卓相关)
// 负责处理安卓相关的事件与动画输入,并通知核心调度器进行布局扰动操作
public class EventHandler {

    private final Scroller scroller; // 惯性滚动器
    private final GestureDetector gestureDetector; // 手势识别器
    private final ScaleGestureDetector scaleDetector; // 捏合识别器
    private final int minVelocity; // 触发甩动的最小速度
    private final int maxVelocity; // 甩动速度上限(压缩到系统上限的 80%)
    private final Callback callback;

    private boolean disallowIntercept; // 是否禁止父容器拦截触摸
    private boolean isInteractingWithView; // 是否正在与视图交互
    private boolean pinching; // 本次触摸序列是否已进入捏合(用于抑制甩动与结算时机)
    private int lastScrollerX; // 上次滚动位置(用于计算增量)
    private int lastScrollerY;

    public EventHandler(Context context, Callback callback) {
        this.callback = callback;
        this.scroller = new Scroller(context);
        ViewConfiguration vc = ViewConfiguration.get(context);
        this.minVelocity = vc.getScaledMinimumFlingVelocity();
        this.maxVelocity = (int) (vc.getScaledMaximumFlingVelocity() * 0.8f);
        this.gestureDetector = new GestureDetector(context, new GestureListener());
        gestureDetector.setIsLongpressEnabled(false);
        this.scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
    }

    // 是否正在与视图交互(拖动/甩动/捏合中)
    public boolean isInteractingWithView() {
        return isInteractingWithView;
    }

    // 设置禁止拦截标志,由父容器在触摸开始时调用
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        this.disallowIntercept = disallowIntercept;
    }

    // 分发触摸事件:DOWN 重置状态,其余交给手势识别器
    // 缩放模式的结算时机：所有手指都抬起(ACTION_UP)之后
    public void handleTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();

        if (action == MotionEvent.ACTION_DOWN) {
            disallowIntercept = false;
            isInteractingWithView = false;
            pinching = false;
            resetAnimator();
        }

        if (!disallowIntercept) {
            // 捏合优先：第二根手指落下即进入快照缩放模式，之后平移与缩放共用同一条累积通道
            if (action == MotionEvent.ACTION_POINTER_DOWN && event.getPointerCount() >= 2) {
                if (callback.beginZoom()) {
                    pinching = true;
                    isInteractingWithView = true;
                }
            }
            // 顺序要求：先让手势识别器把焦点位移累积成平移，再用新焦点叠加捏合缩放，
            // 否则会用旧变换配新焦点，产生轻微的焦点漂移
            gestureDetector.onTouchEvent(event);
            scaleDetector.onTouchEvent(event);
        }

        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
            // 两只手都抬起来（或事件被取消）才结算，中途抬起一根手指仍留在缩放模式
            if (pinching) {
                pinching = false;
                callback.endZoom();
            }
            disallowIntercept = false;
            isInteractingWithView = false;
        }
    }


    // 驱动惯性滚动:有位移则同步布局,否则仅刷新 UI
    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            int currX = scroller.getCurrX();
            int currY = scroller.getCurrY();
            float dx = currX - lastScrollerX;
            float dy = currY - lastScrollerY;
            lastScrollerX = currX;
            lastScrollerY = currY;

            boolean scrolled = dx != 0 && callback.isHorizontalScrollEnabled();
            if (dy != 0 && callback.isVerticalScrollEnabled()) scrolled = true;

            if (scrolled) {
                float scaleFactor = callback.getScaleFactor();
                callback.sync(dx / scaleFactor, dy / scaleFactor);
            } else {
                callback.updateUI();
            }
        }
    }

    // 中止进行中的惯性滚动
    public void resetAnimator() {
        if (!scroller.isFinished()) scroller.abortAnimation();
    }

    // 重置全部交互状态
    public void reset() {
        disallowIntercept = false;
        isInteractingWithView = false;
        pinching = false;
        lastScrollerX = lastScrollerY = 0;
        resetAnimator();
    }

    // 捏合监听:把增量因子与焦点交给核心调度器累积到快照
    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {

        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            // 快照可能因渲染层不支持而未开启，此时不消费捏合
            if (!pinching && callback.beginZoom()) {
                pinching = true;
                isInteractingWithView = true;
            }
            return pinching;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            if (!pinching) return false;
            callback.updateZoom(detector.getScaleFactor(), detector.getFocusX(), detector.getFocusY());
            return true;
        }
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {

        // 拖动:位移取反后同步布局(内容跟随手指)
        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            boolean scrolled = false;
            float dx = 0, dy = 0;
            if (callback.isHorizontalScrollEnabled()) {
                dx = -distanceX;
                scrolled = true;
            }
            if (callback.isVerticalScrollEnabled()) {
                dy = -distanceY;
                scrolled = true;
            }
            isInteractingWithView = scrolled || pinching;
            if (scrolled) {
                float scaleFactor = callback.getScaleFactor();
                callback.sync(dx / scaleFactor, dy / scaleFactor);
                return true;
            }
            return false;
        }

        // 甩动:按轴独立校验速度并限幅,启动惯性滚动
        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            // 缩放会话中不启动惯性滚动:多指抬手的速度没有意义,且惯性会污染统一结算
            if (pinching || callback.isZooming()) {
                return false;
            }
            boolean flingX = false, flingY = false;

            if (callback.isHorizontalScrollEnabled() && Math.abs(velocityX) >= minVelocity) {
                velocityX = velocityX < 0 ? Math.max(velocityX, -maxVelocity) : Math.min(velocityX, maxVelocity);
                flingX = true;
            } else {
                velocityX = 0;
            }

            if (callback.isVerticalScrollEnabled() && Math.abs(velocityY) >= minVelocity) {
                velocityY = velocityY < 0 ? Math.max(velocityY, -maxVelocity) : Math.min(velocityY, maxVelocity);
                flingY = true;
            } else {
                velocityY = 0;
            }

            if (!flingX && !flingY) {
                return false;
            }

            lastScrollerX = 0;
            lastScrollerY = 0;
            scroller.fling(0, 0, (int) velocityX, (int) velocityY,
                    callback.isHorizontalScrollEnabled() ? Integer.MIN_VALUE : 0,
                    callback.isHorizontalScrollEnabled() ? Integer.MAX_VALUE : 0,
                    callback.isVerticalScrollEnabled() ? Integer.MIN_VALUE : 0,
                    callback.isVerticalScrollEnabled() ? Integer.MAX_VALUE : 0);
            callback.updateUI();
            return true;
        }
    }

    // 调度回调(由宿主实现)
    public interface Callback {

        boolean isHorizontalScrollEnabled(); // 水平滚动是否启用

        boolean isVerticalScrollEnabled(); // 垂直滚动是否启用

        void sync(float dx, float dy); // 同步布局

        void updateUI(); // 刷新 UI

        float getScaleFactor();

        boolean isZooming(); // 是否处于快照缩放模式

        boolean beginZoom(); // 请求进入快照缩放模式,返回是否成功

        void updateZoom(float relativeScale, float focusX, float focusY); // 累积捏合输入

        void endZoom(); // 结算缩放

    }

}