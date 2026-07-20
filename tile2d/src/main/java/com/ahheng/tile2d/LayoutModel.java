package com.ahheng.tile2d;

// 布局状态模型(快照)
// 支持跨平台移植
public class LayoutModel {

    // 逻辑范围(闭区间)，start > end表示空范围
    public int colStart;
    public int rowStart;
    public int colEnd = -1;
    public int rowEnd = -1;

    // 视窗整体偏移量
    public float offsetX;
    public float offsetY;

    // 视窗内容总尺寸
    public int totalWidth;
    public int totalHeight;
    
    // 调试变量(可移除)
    public long syncTime;

    // 创建新副本
    public LayoutModel newInstance() {
        LayoutModel model = new LayoutModel();
        copyTo(model);
        return model;
    }

    // 复制状态
    public void copyTo(LayoutModel model) {
        model.colStart = this.colStart;
        model.rowStart = this.rowStart;
        model.colEnd = this.colEnd;
        model.rowEnd = this.rowEnd;

        model.offsetX = this.offsetX;
        model.offsetY = this.offsetY;

        model.totalWidth = this.totalWidth;
        model.totalHeight = this.totalHeight;
        
        model.syncTime = this.syncTime;
    }
    
    // 重置状态
    public void reset() {
        colStart = 0;
        rowStart = 0;
        colEnd = -1;
        rowEnd = -1;

        offsetX = 0f;
        offsetY = 0f;

        totalWidth = 0;
        totalHeight = 0;
        
        syncTime = 0L;
    }
}
