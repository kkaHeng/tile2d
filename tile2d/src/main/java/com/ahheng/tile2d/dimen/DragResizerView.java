package com.ahheng.tile2d.dimen;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import com.ahheng.tile2d.LayoutEngine;

// 拖拽调整尺寸视图
// 在角落提供两个拖拽手柄,拖动后通过回调更新瓦片宽高与对齐方向
public class DragResizerView extends View {

    public static final int DIRECTION_NONE = -1; // 未激活
    public static final int DIRECTION_START = 0; // 左上手柄
    public static final int DIRECTION_END = 1; // 右下手柄

    private Paint paint; // 边框与手柄画笔
    private int indicatorSize; // 手柄边长
    private int direction = DIRECTION_NONE; // 当前激活的拖拽方向
    private float downX; // 按下位置(用于计算增量)
    private float downY;
    private float currX; // 当前位置
    private float currY;
    private Callback callback;

    public DragResizerView(Context context) {
        super(context);
        init();
    }
    public DragResizerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    public DragResizerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        TypedValue value = new TypedValue();
        getContext().getTheme().resolveAttribute(android.R.attr.colorPrimary, value, true);
        paint.setColor(getResources().getColor(value.resourceId));
        indicatorSize = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 14, getResources().getDisplayMetrics());
        paint.setStrokeWidth(indicatorSize / 4f);
    }

    public int getIndicatorSize() {
        return indicatorSize;
    }

    public void setIndicatorSize(int indicatorSize) {
        this.indicatorSize = indicatorSize;
        paint.setStrokeWidth(indicatorSize / 4f);
    }

    public int getDirection() {
        return direction;
    }

    // 绘制边框与左上/右下两个拖拽手柄
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawRect(0, 0, getWidth() - 1, getHeight() - 1, paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRect(0, 0, indicatorSize, indicatorSize, paint);
        canvas.drawRect(getWidth() - indicatorSize, getHeight() - indicatorSize, getWidth(), getHeight(), paint);
    }

    // 触摸命中手柄后激活对应方向,拖动期间持续回调
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        currX = event.getRawX();
        currY = event.getRawY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = currX;
                downY = currY;
                float x = event.getX();
                float y = event.getY();
                if (x <= indicatorSize && y <= indicatorSize) {
                    direction = DIRECTION_START;
                } else if (x >= getWidth() - indicatorSize && y >= getHeight() - indicatorSize) {
                    direction = DIRECTION_END;
                }
                if (direction != DIRECTION_NONE) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (direction != DIRECTION_NONE) {
                    drag();
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                direction = DIRECTION_NONE;
                break;
        }
        return super.onTouchEvent(event);
    }

    // 计算本次位移并回调:左上手柄缩小,右下手柄放大
    private void drag() {
        int dx = (int) (currX - downX);
        int dy = (int) (currY - downY);
        int width = callback.getTileWidth();
        int height = callback.getTileHeight();
        int gravity = LayoutEngine.DIMEN_GRAVITY_CENTER;
        if (direction == DIRECTION_START) {
            width -= dx;
            height -= dy;
            gravity = LayoutEngine.DIMEN_GRAVITY_END;
        } else if (direction == DIRECTION_END) {
            width += dx;
            height += dy;
            gravity = LayoutEngine.DIMEN_GRAVITY_START;
        }
        if (width > 0 && height > 0) {
            callback.onDrag(direction, width, height, gravity);
        }
        downX = currX;
        downY = currY;
    }

    // 设置拖拽回调
    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public interface Callback {
        void onDrag(int direction, int width, int height, int gravity); // 拖拽产生的新尺寸与对齐方向
        int getTileWidth(); // 当前瓦片宽
        int getTileHeight(); // 当前瓦片高
    }

}