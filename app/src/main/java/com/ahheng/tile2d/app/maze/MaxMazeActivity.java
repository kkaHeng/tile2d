package com.ahheng.tile2d.app.maze;

import android.os.Bundle;
import android.view.ViewGroup;

import com.ahheng.tile2d.app.BaseActivity;
import com.ahheng.tile2d.widget.layout.TileLayout;

public class MaxMazeActivity extends BaseActivity {

    private int tileSize;
    private TileLayout tileLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        tileSize = dp2px(45);
        
        tileLayout = new TileLayout(this);
        tileLayout.setDebugMode(isDebugMode());
        tileLayout.setDefaultTileWidth(tileSize);
        tileLayout.setDefaultTileHeight(tileSize);
        setContentView(tileLayout, new ViewGroup.LayoutParams(-1, -1));
    }

    @Override
    public boolean hasMaxMode() {
        return false;
    }

    @Override
    public boolean hasPlanMode() {
        return false;
    }

    @Override
    protected void onDebugModeChanged(boolean enabled) {
        super.onDebugModeChanged(enabled);
        tileLayout.setDebugMode(enabled);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        tileLayout.setAdapter(null);
    }

}
