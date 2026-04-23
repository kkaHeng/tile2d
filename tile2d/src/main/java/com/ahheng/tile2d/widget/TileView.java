package com.ahheng.tile2d.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import com.ahheng.tile2d.TileCoreService;

public class TileView extends View {

    private TileCoreService service;
    private Rect bounds;

    public TileView(Context context) {
        super(context);
        init();
    }
    public TileView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    private void init() {
    	this.service = new TileCoreService(new PlatformService());
        this.bounds = new Rect();
    }

    private class PlatformService implements TileCoreService.PlatformService {
        
        @Override
        public int getBoundsWidth() {
            return bounds.width();
        }
        
        @Override
        public int getBoundsHeight() {
            return bounds.height();
        }
        
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        bounds.set(getPaddingLeft(), getPaddingTop(), getWidth() - getPaddingRight(), getHeight() - getPaddingBottom());
    }

    public static class TileHolder {
    
        private int type;
        private int column;
        private int row;
        private TileView view;
        
        public void draw(Canvas canvas, float scaleFactor) {
        }
        
    }

}
