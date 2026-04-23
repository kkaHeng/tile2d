package com.ahheng.tile2d.widget.layout;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import com.ahheng.tile2d.TileLayoutService;

public class TileLayout extends ViewGroup {


    private TileLayoutService service;
    private Rect bounds;

    public TileLayout(Context context) {
        super(context);
        init();
    }
    public TileLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    private void init() {
        this.service = new TileLayoutService(new PlatformService());
        this.bounds = new Rect();
    }

    private class PlatformService implements TileLayoutService.PlatformService {

        @Override
        public int getWindowWidth() {
            return 0;
        }

        @Override
        public int getWindowHeight() {
            return 0;
        }

        @Override
        public int getTileWidth(int column) {
            return 0;
        }

        @Override
        public int getTileHeight(int row) {
            return 0;
        }

        @Override
        public int getLeftBound() {
            return 0;
        }

        @Override
        public int getTopBound() {
            return 0;
        }

        @Override
        public int getRightBound() {
            return 0;
        }

        @Override
        public int getBottomBound() {
            return 0;
        }

        @Override
        public boolean isHorizontalScrollEnabled() {
            return false;
        }

        @Override
        public boolean isVerticalScrollEnabled() {
            return false;
        }

        @Override
        public void in(int column, int row) {

        }

        @Override
        public void out(int column, int row) {

        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        bounds.set(getPaddingLeft(), getPaddingTop(), getWidth() - getPaddingRight(), getHeight() - getPaddingBottom());
    }

    public static class TileHolder {

        public final View itemView;

        public TileHolder(View itemView) {
            this.itemView = itemView;
        }

        private int type;
        private int column;
        private int row;
        private TileLayout view;

    }

}
