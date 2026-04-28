package com.ahheng.tile2d.widget.layout;

import android.content.Context;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Choreographer;
import android.view.View;
import android.view.ViewGroup;

import com.ahheng.tile2d.TileCoreService;
import com.ahheng.tile2d.TileLayoutModel;
import com.ahheng.tile2d.widget.TileAdapter;

public class TileLayout extends ViewGroup implements TileCoreService.CoreInterface<TileLayout.TileHolder> {


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

    public TileLayout(Context context) {
        super(context);
        init();
    }
    public TileLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    private void init() {
        this.coreService = new TileCoreService<>(getContext(), this);
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        coreService.setDefaultTileWidth((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 80, displayMetrics));
        coreService.setDefaultTileHeight((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 45, displayMetrics));
    }

    private void layoutTiles() {
        TileLayoutModel model = coreService.getLayoutModel();

        int column = model.colStart;
        float x = model.offsetX;
        while (column <= model.colEnd) {
            int row = model.rowStart;
            float y = model.offsetY;
            while (row <= model.rowEnd) {
                TileHolder tile = coreService.getActiveTile(column, row);
                if (tile != null) {

                }

                y += coreService.getTileHeight(row);
            }
            x += coreService.getTileWidth(column);
        }
    }

    @Override
    public void updateUI() {
        layoutTiles();
    }

    @Override
    public void onTileIn(TileHolder holder, int column, int row) {
        attachViewToParent(holder.itemView, -1, holder.itemView.getLayoutParams());
    }

    @Override
    public void onTileOut(TileHolder holder, int column, int row) {
        detachViewFromParent(holder.itemView);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        coreService.setBounds(getPaddingLeft(), getPaddingTop(), getWidth() - getPaddingRight(), getHeight() - getPaddingBottom());
        if (overrideInitLocation) {
            overrideInitLocation = false;
            coreService.seek(initLocationColumn, initLocationRow, initOffsetX, initOffsetY);
        } else if (coreService.getAdapter() != null && coreService.getActiveTileCount() == 0) {
            coreService.seek(coreService.getAdapter().getLeftBound(), coreService.getAdapter().getTopBound(), 0, 0);
        }
    }

    public void offset(float dx, float dy) {
        if (isEmpty() || coreService.getActiveTileCount() == 0) {
            return;
        }
        coreService.sync(dx, dy);
    }

    public void seek(int column, int row, float offsetX, float offsetY) {
        overrideInitLocation = true;
        initLocationColumn = column;
        initLocationRow = row;
        initOffsetX = offsetX;
        initOffsetY = offsetY;
        requestLayout();
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
