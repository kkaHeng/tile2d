package com.ahheng.tile2d;

public class TileCoreService {

    private final TileLayoutModel layoutModel;
    private final PlatformService platform;

    private int colStart;
    private int rowStart;
    private int colEnd;
    private int rowEnd;

    // 视窗(左 <-(< 0) offset (> 0)-> 右)
    private float offsetX;
    private float offsetY;
    private int totalWidth;
    private int totalHeight;

    public TileCoreService(PlatformService service) {
        this.layoutModel = new TileLayoutModel();
        this.platform = service;
    }

    public TileLayoutModel sync(float dx, float dy) {
        int colStart = this.colStart;
        int rowStart = this.rowStart;
        int colEnd = this.colEnd;;
        int rowEnd = this.rowEnd;
        float offsetX = this.offsetX + dx;
        float offsetY = this.offsetY + dy;
        int totalWidth = this.totalWidth;
        int totalHeight = this.totalHeight;
        float appendWidth = 0;
        float appendHeight = 0;
        
        int windowWidth = platform.getWindowWidth();
        int windowHeight = platform.getWindowHeight();
        int leftBound = platform.getLeftBound();
        int topBound = platform.getTopBound();
        int rightBound = platform.getRightBound();
        int bottomBound = platform.getBottomBound();
        
        // 水平同步到 [-tileWidth, 0]
        if (platform.isHorizontalScrollEnabled()) {
            // 起始锚点
            if (offsetX < 0 && totalWidth + offsetX < windowWidth && colEnd == rightBound) {
                // 右侧有空白
                float end = windowWidth - (totalWidth + offsetX);
                offsetX += end;
            }
            while (offsetX > 0 && colStart > leftBound) {
                // 内容向右边滚动，锚点左移
                colStart--;
                in(colStart, rowStart, rowEnd);
                int width = platform.getTileWidth(colStart);
                offsetX -= width;
                appendWidth += width;
            }
            while (offsetX < -platform.getTileWidth(colStart) && colStart < rightBound) {
                // 内容向左滚动，锚点右移
                int width = platform.getTileWidth(colStart);
                offsetX += width;
                appendWidth -= width;
                out(colStart, rowStart, rowEnd);
                colStart++;
            }
            if (offsetX > 0 && colEnd == leftBound) {
                // 左边存在空白，内容无法填满窗口，强制对齐左边缘
                offsetX = 0;
            }

            // 结尾锚点
            totalWidth += appendWidth;
            while (totalWidth < windowWidth && colEnd < rightBound) {
                // 内容填不满窗口
                colEnd++;
                in(colEnd, rowStart, rowEnd);
                totalWidth += platform.getTileWidth(colEnd);
            }
            while (totalWidth - platform.getTileWidth(colEnd) > windowWidth && colEnd > colStart) {
                // 内容过度超出窗口
                totalWidth -= platform.getTileWidth(colEnd);
                out(colEnd, rowStart, rowEnd);
                colEnd--;
            }
        }
        
        // ...
        
        this.colStart = colStart;
        this.rowStart = rowStart;
        this.colEnd = colEnd;
        this.rowEnd = rowEnd;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.totalWidth = totalWidth;
        this.totalHeight = totalHeight;
        
    	return getLayoutModel();
    }

    public TileLayoutModel seek(int column, int row, float offsetX, float offsetY) {
        checkLocationInBounds(column, row);
        int rightBound = platform.getRightBound();
        int bottomBound = platform.getBottomBound();
        int windowWidth = platform.getWindowWidth();
        int windowHeight = platform.getWindowHeight();
        int totalWidth = 0;
        int totalHeight = 0;
        int colEnd = column;
        int rowEnd = row;
        
        int c = column;
        while (c <= rightBound) {
            int width = platform.getTileWidth(c);
            int r = row;
            int h = 0;
            while (r <= bottomBound) {
                int height = platform.getTileHeight(r);
                platform.in(c, r);
                
                h += height;
                if (h > windowHeight) {
                    totalHeight = h;
                    rowEnd = r;
                    break;
                }
                if (r == bottomBound) break;
                r++;
            }
            totalWidth += width;
            if (totalWidth > windowWidth) {
                colEnd = c;
                break;
            }
            if (c == rightBound) break;
            c++;
        }
        
        this.colStart = column;
        this.rowStart = row;
        this.offsetX = 0;
        this.offsetY = 0;
        this.totalWidth = totalWidth;
        this.totalHeight = totalHeight;
        this.colEnd = colEnd;
        this.rowEnd = rowEnd;
    	return sync(offsetX, offsetY);
    }

    private void in(int column, int rowStart, int rowEnd) {
    	int r = rowStart;
        while (r <= rowEnd) {
            platform.in(column, r);
            if (r == rowEnd) break;
            r++;
        }
    }

    private void out(int column, int rowStart, int rowEnd) {
    	int r = rowStart;
        while (r <= rowEnd) {
            platform.out(column, r);
            if (r == rowEnd) break;
            r++;
        }
    }

    public TileLayoutModel getLayoutModel() {
        layoutModel.colStart = colStart;
        layoutModel.rowStart = rowStart;
        layoutModel.colEnd = colEnd;
        layoutModel.rowEnd = rowEnd;
        layoutModel.offsetX = offsetX;
        layoutModel.offsetY = offsetY;
        layoutModel.totalWidth = totalWidth;
        layoutModel.totalHeight = totalHeight;
    	return this.layoutModel;
    }

    public long getTileId(int column, int row) {
    	return ((long) column << 32) | (row & 0xFFFFFFFFL);
    }

    public boolean contains(int column, int row) {
    	return column >= platform.getLeftBound() && column <= platform.getRightBound()
        && row >= platform.getTopBound() && row <= platform.getBottomBound();
    }

    public void checkLocationInBounds(int column, int row) {
        int left = platform.getLeftBound();
        int top = platform.getTopBound();
        int right = platform.getRightBound();
        int bottom = platform.getBottomBound();
        if (column < left || column > right || row < top || row > bottom) {
            throw new IllegalArgumentException("(" + column + "," + row + ") 不在边界 (" + left + "," + top + "," + right + "," + bottom + ") 范围内");
        }
    }

    public interface PlatformService {
        
        int getWindowWidth();
        
        int getWindowHeight();
        
        int getTileWidth(int column);
        
        int getTileHeight(int row);
        
        int getLeftBound();
        
        int getTopBound();
        
        int getRightBound();
        
        int getBottomBound();
        
        boolean isHorizontalScrollEnabled();
        
        boolean isVerticalScrollEnabled();
        
        void in(int column, int row);
        
        void out(int column, int row);
    }

}
