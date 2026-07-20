package com.ahheng.tile2d;

import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.Scroller;

// 事件处理器(安卓相关)
// 负责处理安卓相关的事件与动画输入，并通知核心调度器进行布局扰动操作
public class EventHandler {

    private final Scroller scroller;
    private final GestureDetector gestureDetector;
    private final int minVelocity;
    private final int maxVelocity;
    private final Callback callback;

    private boolean disallowIntercept;
    private boolean isInteractingWithView;
    private int lastScrollerX;
    private int lastScrollerY;

    public EventHandler(Context context, Callback callback) {
        this.callback = callback;
        this.scroller = new Scroller(context);
        ViewConfiguration vc = ViewConfiguration.get(context);
        this.minVelocity = vc.getScaledMinimumFlingVelocity();
        this.maxVelocity = (int) (vc.getScaledMaximumFlingVelocity() * 0.8f);
        this.gestureDetector = new GestureDetector(context, new GestureListener());
        gestureDetector.setIsLongpressEnabled(false);
    }

    public boolean isInteractingWithView() {
        return isInteractingWithView;
    }

    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        this.disallowIntercept = disallowIntercept;
    }

    public void handleTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();

        if (action == MotionEvent.ACTION_DOWN) {
            disallowIntercept = false;
            isInteractingWithView = false;
            resetAnimator();
        }

        if (!disallowIntercept) {
            gestureDetector.onTouchEvent(event);
        }

        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
            disallowIntercept = false;
            isInteractingWithView = false;
        }
    }

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
                callback.sync(dx, dy);
            } else {
                callback.updateUI();
            }
        }
    }

    public void resetAnimator() {
        if (!scroller.isFinished()) scroller.abortAnimation();
    }

    public void reset() {
        disallowIntercept = false;
        isInteractingWithView = false;
        lastScrollerX = lastScrollerY = 0;
        resetAnimator();
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {

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
            isInteractingWithView = scrolled;
            if (scrolled) {
                callback.sync(dx, dy);
                return true;
            }
            return false;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
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

    public interface Callback {

        boolean isHorizontalScrollEnabled();

        boolean isVerticalScrollEnabled();

        void sync(float dx, float dy);

        void updateUI();

    }

}
