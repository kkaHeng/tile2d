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
        colEnd =
        rowStart =
        rowEnd = 0;
        
        offsetX = offsetY = 0;
        
        totalWidth = totalHeight = 0;
    }

}
