package com.ahheng.tile2d;

public class TileLayoutModel {

    public int colStart;
    public int rowStart;
    public int colEnd;
    public int rowEnd;
    public float offsetX;
    public float offsetY;
    public int totalWidth;
    public int totalHeight;

    public void reset() {
    	colStart = 
        rowStart = 0;
        colEnd =
        rowEnd = -1;
        
        offsetX = offsetY = 0;
        
        totalWidth = totalHeight = 0;
    }

}
