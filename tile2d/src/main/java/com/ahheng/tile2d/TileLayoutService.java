package com.ahheng.tile2d;

public class TileLayoutService {

    private final TileLayoutModel layoutModel;
    private final PlatformService platform;

    private boolean horizontalScrollEnabled = true;
    private boolean verticalScrollEnabled = true;

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

    public boolean sync(float dx, float dy) {

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
        
        if (colStart > rightBound ||
            rowStart > bottomBound ||
            colEnd < leftBound ||
            rowEnd < topBound) {
            // 窗口状态不合法，避免向外传递不合法的坐标，直接短路
            return false;
        }

        // 水平同步到 [-tileWidth, 0]
        if (horizontalScrollEnabled) {
            // 起始锚点
            if (offsetX <= 0 && totalWidth + offsetX < windowWidth && colEnd == rightBound) {
                // 右侧有空白，尝试右对齐
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
            int startWidth = platform.getTileWidth(colStart);
            while (offsetX < -startWidth && colStart < rightBound) {
                // 内容向左边滚动，锚点右移
                offsetX += startWidth;
                totalWidth -= startWidth;
                colStart++;
                startWidth = platform.getTileWidth(colStart);
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
            int endWidth = platform.getTileWidth(colEnd);
            while (totalWidth + offsetX - endWidth > windowWidth && colEnd > colStart) {
                // 内容过度超出窗口
                totalWidth -= endWidth;
                colEnd--;
                endWidth = platform.getTileWidth(colEnd);
            }
        }

        // 垂直同步到 [-tileHeight, 0]
        if (verticalScrollEnabled) {
            // 起始锚点
            if (offsetY <= 0 && totalHeight + offsetY < windowHeight && rowEnd == bottomBound) {
                // 底部有空白，尝试下对齐
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
            int startHeight = platform.getTileHeight(rowStart);
            while (offsetY < -startHeight && rowStart < bottomBound) {
                // 内容向上边滚动，锚点下移
                offsetY += startHeight;
                totalHeight -= startHeight;
                rowStart++;
                startHeight = platform.getTileHeight(rowStart);
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
            int endHeight = platform.getTileHeight(rowEnd);
            while (totalHeight + offsetY - endHeight > windowHeight && rowEnd > rowStart) {
                // 内容过度超出窗口
                totalHeight -= endHeight;
                rowEnd--;
                endHeight = platform.getTileHeight(rowEnd);
            }
        }
        platform.beforeDiff(colStart, rowStart, colEnd, rowEnd);
        diff(this.colStart, this.rowStart, this.colEnd, this.rowEnd, colStart, rowStart, colEnd, rowEnd);
        this.colStart = colStart;
        this.rowStart = rowStart;
        this.colEnd = colEnd;
        this.rowEnd = rowEnd;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.totalWidth = totalWidth;
        this.totalHeight = totalHeight;
        return true;
    }

    public boolean seek(int column, int row, float offsetX, float offsetY) {
        if (isEmpty() || !checkLocationInBounds(column, row)) {
            return false;
        }
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
                if (r == bottomBound) {
                    rowEnd = r;
                    break;
                }
                r++;
            }

            totalWidth += platform.getTileWidth(c);
            if (totalWidth > windowWidth) {
                colEnd = c;
                break;
            }
            if (c == rightBound) {
                // 已到达尽头
                // 避坑：未更新 colEnd 导致在右下边界处出现 totalWidth、totalHeight 与实际不同步的问题
                colEnd = c;
                break;
            }
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
        sync(offsetX, offsetY);
        return true;
    }

    public void updateWidth(int column, int oldWidth, int newWidth) {
        if (column >= colStart && column <= colEnd) {
            totalWidth += (newWidth - oldWidth);
            float dx = 0;
            if (column == colStart) {
                dx = offsetX + oldWidth - newWidth;
                dx = min(0, max(-newWidth, dx));
                dx -= offsetX;
            }
            sync(dx, 0);
        }
    }

    public void updateHeight(int row, int oldHeight, int newHeight) {
        if (row >= rowStart && row <= rowEnd) {
            totalHeight += (newHeight - oldHeight);
            float dy = 0;
            if (row == rowStart) {
                dy = offsetY + oldHeight - newHeight;
                dy = min(0, max(-newHeight, dy));
                dy -= offsetY;
            }
            sync(0, dy);
        }
    }

    private void diff(int oldColStart, int oldRowStart, int oldColEnd, int oldRowEnd, int newColStart, int newRowStart, int newColEnd, int newRowEnd) {
        if (oldColStart == newColStart && oldRowStart == newRowStart
                && oldColEnd == newColEnd && oldRowEnd == newRowEnd) {
            return; // 提前退出
        }
        if (newColStart > oldColEnd || newRowStart > oldRowEnd || newColEnd < oldColStart || newRowEnd < oldRowStart) {
            // 说明 sync 跑了很远，直接兜底
            int oldX = oldColStart;
            while (oldX <= oldColEnd) {
                int oldY = oldRowStart;
                while (oldY <= oldRowEnd) {
                    platform.out(oldX, oldY);
                    if (oldY == oldRowEnd) break;
                    oldY++;
                }
                if (oldX == oldColEnd) break;
                oldX++;
            }

            int newX = newColStart;
            while (newX <= newColEnd) {
                int newY = newRowStart;
                while (newY <= newRowEnd) {
                    platform.in(newX, newY);
                    if (newY == newRowEnd) break;
                    newY++;
                }
                if (newX == newColEnd) break;
                newX++;
            }
            return;
        }
        // 计算最大边界
        int boundLeft = min(oldColStart, newColStart);
        int boundRight = max(oldColEnd, newColEnd);
        int boundTop = min(oldRowStart, newRowStart);
        int boundBottom = max(oldRowEnd, newRowEnd);

        // 计算交集
        int inLeft = max(oldColStart, newColStart);
        int inRight = min(oldColEnd, newColEnd);
        int inTop = max(oldRowStart, newRowStart);
        int inBottom = min(oldRowEnd, newRowEnd);

        // 遍历顶部区域
        if (boundTop < inTop) {
            int x = inLeft;
            while (x <= boundRight) {
                int y = boundTop;
                while (y <= inTop - 1) {
                    boolean inBefore = x >= oldColStart && x <= oldColEnd && y >= oldRowStart && y <= oldRowEnd;
                    boolean inAfter = x >= newColStart && x <= newColEnd && y >= newRowStart && y <= newRowEnd;
                    if (inBefore && !inAfter) {
                        platform.out(x, y);
                    } else if (!inBefore && inAfter) {
                        platform.in(x, y);
                    }
                    if (y == inTop - 1) break;
                    y++;
                }
                if (x == boundRight) break;
                x++;
            }
        }

        // 遍历右边区域
        if (inRight < boundRight) {
            int x = inRight + 1;
            while (x <= boundRight) {
                int y = inTop;
                while (y <= boundBottom) {
                    boolean inBefore = x >= oldColStart && x <= oldColEnd && y >= oldRowStart && y <= oldRowEnd;
                    boolean inAfter = x >= newColStart && x <= newColEnd && y >= newRowStart && y <= newRowEnd;
                    if (inBefore && !inAfter) {
                        platform.out(x, y);
                    } else if (!inBefore && inAfter) {
                        platform.in(x, y);
                    }
                    if (y == boundBottom) break;
                    y++;
                }
                if (x == boundRight) break;
                x++;
            }
        }

        // 遍历底部区域
        if (inBottom < boundBottom) {
            int x = boundLeft;
            while (x <= inRight) {
                int y = inBottom + 1;
                while (y <= boundBottom) {
                    boolean inBefore = x >= oldColStart && x <= oldColEnd && y >= oldRowStart && y <= oldRowEnd;
                    boolean inAfter = x >= newColStart && x <= newColEnd && y >= newRowStart && y <= newRowEnd;
                    if (inBefore && !inAfter) {
                        platform.out(x, y);
                    } else if (!inBefore && inAfter) {
                        platform.in(x, y);
                    }
                    if (y == boundBottom) break;
                    y++;
                }
                if (x == inRight) break;
                x++;
            }
        }

        // 遍历左边区域
        if (boundLeft < inLeft) {
            int x = boundLeft;
            while (x <= inLeft - 1) {
                int y = boundTop;
                while (y <= inBottom) {
                    boolean inBefore = x >= oldColStart && x <= oldColEnd && y >= oldRowStart && y <= oldRowEnd;
                    boolean inAfter = x >= newColStart && x <= newColEnd && y >= newRowStart && y <= newRowEnd;
                    if (inBefore && !inAfter) {
                        platform.out(x, y);
                    } else if (!inBefore && inAfter) {
                        platform.in(x, y);
                    }
                    if (y == inBottom) break;
                    y++;
                }
                if (x == inLeft - 1) break;
                x++;
            }
        }

//      已废弃的旧算法：
//        int y = boundTop;
//        while (y <= boundBottom) {
//            int x = boundLeft;
//            while (x <= boundRight) {
//                if (!(x >= inLeft && x <= inRight && y >= inTop && y <= inBottom)) {
//                    boolean inBefore = x >= oldColStart && x <= oldColEnd && y >= oldRowStart && y <= oldRowEnd;
//                    boolean inAfter = x >= newColStart && x <= newColEnd && y >= newRowStart && y <= newRowEnd;
//
//                    if (inBefore && !inAfter) {
//                        platform.out(x, y);
//                    } else if (!inBefore && inAfter) {
//                        platform.in(x, y);
//                    }
//                }
//                if (x == boundRight) break;
//                x++;
//            }
//
//            if (y == boundBottom) break;
//            y++;
//        }
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

    public boolean checkLocationInBounds(int column, int row) {
        return column >= platform.getLeftBound() &&
                column <= platform.getRightBound() &&
                row >= platform.getTopBound() &&
                row <= platform.getBottomBound();
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
        return colEnd == platform.getRightBound() && totalWidth + offsetX == platform.getWindowWidth();
    }

    public boolean isAtBottomBound() {
        return rowEnd == platform.getBottomBound() && totalHeight + offsetY == platform.getWindowHeight();
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

    public void setHorizontalScrollEnabled(boolean enabled) {
        horizontalScrollEnabled = enabled;
    }

    public void setVerticalScrollEnabled(boolean enabled) {
        verticalScrollEnabled = enabled;
    }

    public boolean isHorizontalScrollEnabled() {
        return horizontalScrollEnabled;
    }

    public boolean isVerticalScrollEnabled() {
        return verticalScrollEnabled;
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

        void in(int column, int row);

        void out(int column, int row);
        
        void beforeDiff(int colStart, int rowStart, int colEnd, int rowEnd);
    }

    private static int min(int a, int b) {
        if (a <= b) {
            return a;
        } else {
            return b;
        }
    }

    private static int max(int a, int b) {
        if (a >= b) {
            return a;
        } else {
            return b;
        }
    }

    private static float min(float a, float b) {
        if (a != a) {
            return a;
        }
        if (b != b) {
            return b;
        }
        if (a <= b) {
            return a;
        } else {
            return b;
        }
    }

    private static float max(float a, float b) {
        if (a != a) {
            return a;
        }
        if (b != b) {
            return b;
        }
        if (a >= b) {
            return a;
        } else {
            return b;
        }
    }

}
