package com.ahheng.tile2d;

public class TileLayoutService {

    private final TileLayoutModel layoutModel;
    private final PlatformService platform;

    private int colStart;
    private int rowStart;
    private int colEnd = -1;
    private int rowEnd = -1;

    // 视窗(左 <-(< 0) offset (> 0)-> 右)
    private float offsetX;
    private float offsetY;
    private int totalWidth;
    private int totalHeight;

    public TileLayoutService(PlatformService service) {
        this.layoutModel = new TileLayoutModel();
        this.platform = service;
    }

    public TileLayoutModel sync(float dx, float dy) {
        if (isEmpty()) {
            return getLayoutModel();
        }

        int colStart = this.colStart;
        int rowStart = this.rowStart;
        int colEnd = this.colEnd;
        int rowEnd = this.rowEnd;
        float offsetX = this.offsetX + dx;
        float offsetY = this.offsetY + dy;
        int totalWidth = this.totalWidth;
        int totalHeight = this.totalHeight;

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
                int width = platform.getTileWidth(colStart);
                offsetX -= width;
                totalWidth += width;
            }
            while (offsetX < -platform.getTileWidth(colStart) && colStart < rightBound) {
                // 内容向左边滚动，锚点右移
                int width = platform.getTileWidth(colStart);
                offsetX += width;
                totalWidth -= width;
                colStart++;
            }
            if (offsetX > 0 && colStart == leftBound) {
                // 左边存在空白，内容无法填满窗口，强制对齐左边缘
                offsetX = 0;
            }

            // 结尾锚点
            while (totalWidth + offsetX < windowWidth && colEnd < rightBound) {
                // 内容填不满窗口
                colEnd++;
                totalWidth += platform.getTileWidth(colEnd);
            }
            while (totalWidth + offsetX - platform.getTileWidth(colEnd) > windowWidth && colEnd > colStart) {
                // 内容过度超出窗口
                totalWidth -= platform.getTileWidth(colEnd);
                colEnd--;
            }
        }

        // 垂直同步到 [-tileHeight, 0]
        if (platform.isVerticalScrollEnabled()) {
            // 起始锚点
            if (offsetY < 0 && totalHeight + offsetY < windowHeight && rowEnd == bottomBound) {
                // 底部有空白
                float end = windowHeight - (totalHeight + offsetY);
                offsetY += end;
            }
            while (offsetY > 0 && rowStart > topBound) {
                // 内容向下边滚动，锚点上移
                rowStart--;
                int height = platform.getTileHeight(rowStart);
                offsetY -= height;
                totalHeight += height;
            }
            while (offsetY < -platform.getTileHeight(rowStart) && rowStart < bottomBound) {
                // 内容向上边滚动，锚点下移
                int height = platform.getTileHeight(rowStart);
                offsetY += height;
                totalHeight -= height;
                rowStart++;
            }
            if (offsetY > 0 && rowStart == topBound) {
                // 顶部存在空白，内容无法填满窗口，强制对齐上边缘
                offsetY = 0;
            }

            // 结尾锚点
            while (totalHeight + offsetY < windowHeight && rowEnd < bottomBound) {
                // 内容填不满窗口
                rowEnd++;
                totalHeight += platform.getTileHeight(rowEnd);
            }
            while (totalHeight + offsetY - platform.getTileHeight(rowEnd) > windowHeight && rowEnd > rowStart) {
                // 内容过度超出窗口
                totalHeight -= platform.getTileHeight(rowEnd);
                rowEnd--;
            }
        }

        TileLayoutModel model = getLayoutModel();
        platform.prediff(colStart, rowStart, colEnd, rowEnd);
        diff(this.colStart, this.rowStart, this.colEnd, this.rowEnd, colStart, rowStart, colEnd, rowEnd);
        this.colStart = colStart;
        this.rowStart = rowStart;
        this.colEnd = colEnd;
        this.rowEnd = rowEnd;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.totalWidth = totalWidth;
        this.totalHeight = totalHeight;

        return model;
    }

    public TileLayoutModel seek(int column, int row, float offsetX, float offsetY) {
        if (isEmpty()) {
            return getLayoutModel();
        }
        checkLocationInBounds(column, row);
        int rightBound = platform.getRightBound();
        int bottomBound = platform.getBottomBound();
        int windowWidth = platform.getWindowWidth();
        int windowHeight = platform.getWindowHeight();
        int totalWidth = (int) offsetX;
        int totalHeight = (int) offsetY;
        int colEnd = column;
        int rowEnd = row;

        int c = column;
        while (c <= rightBound) {
            int r = row;
            while (r <= bottomBound) {
                platform.in(c, r);

                if (c == column) {
                    totalHeight += platform.getTileHeight(r);
                    if (totalHeight > windowHeight) {
                        rowEnd = r;
                        break;
                    }
                } else {
                    if (r == rowEnd) break;
                }
                if (r == bottomBound) break;
                r++;
            }

            totalWidth += platform.getTileWidth(c);
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
        this.totalWidth = totalWidth - (int) offsetX;
        this.totalHeight = totalHeight - (int) offsetY;
        this.colEnd = colEnd;
        this.rowEnd = rowEnd;
        return sync(offsetX, offsetY);
    }

    private void diff(int oldColStart, int oldRowStart, int oldColEnd, int oldRowEnd, int newColStart, int newRowStart, int newColEnd, int newRowEnd) {
        if (oldColStart == newColStart && oldRowStart == newRowStart
                && oldColEnd == newColEnd && oldRowEnd == newRowEnd) {
            return; // 提前退出
        }
        // 计算最大边界
        int boundLeft = Math.min(oldColStart, newColStart);
        int boundRight = Math.max(oldColEnd, newColEnd);
        int boundTop = Math.min(oldRowStart, newRowStart);
        int boundBottom = Math.max(oldRowEnd, newRowEnd);

        // 计算交集
        int inLeft = Math.max(oldColStart, newColStart);
        int inRight = Math.min(oldColEnd, newColEnd);
        int inTop = Math.max(oldRowStart, newRowStart);
        int inBottom = Math.min(oldRowEnd, newRowEnd);

        int y = boundTop;
        while (y <= boundBottom) {
            int x = boundLeft;
            while (x <= boundRight) {
                if (!(x >= inLeft && x <= inRight && y >= inTop && y <= inBottom)) {
                    boolean inBefore = x >= oldColStart && x <= oldColEnd && y >= oldRowStart && y <= oldRowEnd;
                    boolean inAfter = x >= newColStart && x <= newColEnd && y >= newRowStart && y <= newRowEnd;

                    if (inBefore && !inAfter) {
                        platform.out(x, y);
                    } else if (!inBefore && inAfter) {
                        platform.in(x, y);
                    }
                }
                if (x == boundRight) break;
                x++;
            }

            if (y == boundBottom) break;
            y++;
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

    public boolean isEmpty() {
        return platform.getLeftBound() > platform.getRightBound() || platform.getTopBound() > platform.getBottomBound();
    }

    public boolean isAtLeftBound() {
        return colStart == platform.getLeftBound() && offsetX == 0;
    }

    public boolean isAtTopBound() {
        return rowStart == platform.getTopBound() && offsetY == 0;
    }

    public boolean isAtRightBound() {
        return colEnd == platform.getRightBound() && (int) (totalWidth + offsetX) == platform.getWindowWidth();
    }

    public boolean isAtBottomBound() {
        return rowEnd == platform.getBottomBound() && (int) (totalHeight + offsetY) == platform.getWindowHeight();
    }

    public void reset() {
    	colStart =
        rowStart = 0;
        colEnd =
        rowEnd = -1;
        
        offsetX = offsetY = 0;
        
        totalWidth = totalHeight = 0;
        
        layoutModel.reset();
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
        
        void prediff(int colStart, int rowStart, int colEnd, int rowEnd);
    }

}
