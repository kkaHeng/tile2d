package com.ahheng.tile2d;

public class TileLayoutModel {

    // 布局数据
    public int colStart;
    public int rowStart;
    public int colEnd;
    public int rowEnd;
    public float offsetX;
    public float offsetY;
    public int totalWidth;
    public int totalHeight;
    
    // 调试数据
    public long syncTime;
    public long layoutTime;

    public void reset() {
    	colStart = 
        rowStart = 0;
        colEnd =
        rowEnd = -1;
        
        offsetX = offsetY = 0;
        
        totalWidth = totalHeight = 0;
        
        syncTime =
        layoutTime = 0;
    }

    public TileLayoutModel newInstance() {
    	TileLayoutModel model = new TileLayoutModel();
        model.colStart = colStart;
        model.colEnd = colEnd;
        model.rowStart = rowStart;
        model.rowEnd = rowEnd;
        model.offsetX = offsetX;
        model.offsetY = offsetY;
        model.syncTime = syncTime;
        model.layoutTime = layoutTime;
        return model;
    }

}
