package com.ahheng.tile2d.widget.canvas;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import com.ahheng.tile2d.TileCoreService;
import com.ahheng.tile2d.TileLayoutService;
import com.ahheng.tile2d.widget.TileAdapter;
import com.ahheng.tile2d.widget.TileDimenProvider;

public class TileView extends View {

    private TileCoreService<Holder> coreService;

    public TileView(Context context) {
        super(context);
        init();
    }
    public TileView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    private void init() {
        this.coreService = new TileCoreService<>();
    }

    
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        coreService.setBounds(getPaddingLeft(), getPaddingTop(), getWidth() - getPaddingRight(), getHeight() - getPaddingBottom());
    }

    public Adapter getAdapter() {
    	return (Adapter) coreService.getAdapter();
    }

    public void setAdapter(Adapter adapter) {
    	coreService.setAdapter(adapter);
    }

    public int getDefaultTileWidth() {
    	return coreService.getDefaultTileWidth();
    }

    public int getDefaultTileHeight() {
    	return coreService.getDefaultTileHeight();
    }

    public void setDefaultTileWidth(int width) {
    	coreService.setDefaultTileWidth(width);
    }

    public void setDefaultTileHeight(int height) {
    	coreService.setDefaultTileHeight(height);
    }

    public static abstract class Adapter extends TileAdapter<Holder> {
    }

    public static class Holder extends TileCoreService.BaseTileHolder {
    
        private TileView view;
        
        public void draw(Canvas canvas, float scaleFactor) {}
        
    }

}
