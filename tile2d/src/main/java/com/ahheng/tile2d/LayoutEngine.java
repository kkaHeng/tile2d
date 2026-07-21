package com.ahheng.tile2d;

// 核心布局引擎
// 支持跨平台移植
public class LayoutEngine {

    public static final int DIMEN_GRAVITY_CENTER = 0;
    public static final int DIMEN_GRAVITY_START = -1;
    public static final int DIMEN_GRAVITY_END = 1;

    private final BoundaryInterface boundaryInterface;
    private final WindowInterface windowInterface;

    private final LayoutModel original = new LayoutModel();
    private final LayoutModel output = new LayoutModel();

    private boolean horizontalScrollEnabled = true;
    private boolean verticalScrollEnabled = true;
    private int windowWidth;
    private int windowHeight;

    public LayoutEngine(BoundaryInterface boundaryInterface, WindowInterface windowInterface) {
        this.boundaryInterface = boundaryInterface;
        this.windowInterface = windowInterface;
    }

    // 同步视窗
    public boolean sync(float dx, float dy) {
        // 调试代码，可丢弃
        long startTime = System.nanoTime();
        int colStart = original.colStart;
        int rowStart = original.rowStart;
        int colEnd = original.colEnd;
        int rowEnd = original.rowEnd;

        int leftBound = boundaryInterface.getLeftBound();
        int topBound = boundaryInterface.getTopBound();
        int rightBound = boundaryInterface.getRightBound();
        int bottomBound = boundaryInterface.getBottomBound();
        if (colStart > rightBound ||
            rowStart > bottomBound ||
            colEnd < leftBound ||
            rowEnd < topBound) {
            // 窗口状态不合法，避免向外传递不合法的坐标，直接短路
            return false;
        }
        float offsetX = original.offsetX + dx;
        float offsetY = original.offsetY + dy;

        int totalWidth = original.totalWidth;
        int totalHeight = original.totalHeight;

        // 水平同步到 [-tileWidth, 0]
        if (horizontalScrollEnabled) {
            // 起始锚点
            if (offsetX <= 0 && totalWidth + offsetX < windowWidth && colEnd == rightBound) {
                // 右侧有空白，尝试右对齐，伪造用户向右拖事件
                float end = windowWidth - (totalWidth + offsetX);
                offsetX += end;
            }
            while (offsetX > 0 && colStart > leftBound) {
                // 用户向右拖，内容向右边滚动，锚点左移
                colStart--;
                int width = windowInterface.getColWidth(colStart);
                offsetX -= width;
                totalWidth += width;
            }
            int startWidth = windowInterface.getColWidth(colStart);
            while (offsetX < -startWidth && colStart < rightBound) {
                // 用户向左拖，内容向左边滚动，锚点右移
                offsetX += startWidth;
                totalWidth -= startWidth;
                colStart++;
                startWidth = windowInterface.getColWidth(colStart);
            }
            if (offsetX > 0 && colStart == leftBound) {
                // 左边存在空白，内容无法填满窗口，强制对齐左边缘
                offsetX = 0;
            }

            // 结尾锚点
            while (totalWidth + offsetX < windowWidth && colEnd < rightBound) {
                // 内容填不满窗口，扩展锚点
                colEnd++;
                totalWidth += windowInterface.getColWidth(colEnd);
            }
            int endWidth = windowInterface.getColWidth(colEnd);
            while (totalWidth + offsetX - endWidth > windowWidth && colEnd > colStart) {
                // 内容过度超出窗口，收缩锚点
                totalWidth -= endWidth;
                colEnd--;
                endWidth = windowInterface.getColWidth(colEnd);
            }
        }

        // 垂直同步 (同上)
        if (verticalScrollEnabled) {
            if (offsetY <= 0 && totalHeight + offsetY < windowHeight && rowEnd == bottomBound) {
                float end = windowHeight - (totalHeight + offsetY);
                offsetY += end;
            }
            while (offsetY > 0 && rowStart > topBound) {
                rowStart--;
                int height = windowInterface.getRowHeight(rowStart);
                offsetY -= height;
                totalHeight += height;
            }
            int startHeight = windowInterface.getRowHeight(rowStart);
            while (offsetY < -startHeight && rowStart < bottomBound) {
                offsetY += startHeight;
                totalHeight -= startHeight;
                rowStart++;
                startHeight = windowInterface.getRowHeight(rowStart);
            }
            if (offsetY > 0 && rowStart == topBound) {
                offsetY = 0;
            }
            while (totalHeight + offsetY < windowHeight && rowEnd < bottomBound) {
                rowEnd++;
                totalHeight += windowInterface.getRowHeight(rowEnd);
            }
            int endHeight = windowInterface.getRowHeight(rowEnd);
            while (totalHeight + offsetY - endHeight > windowHeight && rowEnd > rowStart) {
                totalHeight -= endHeight;
                rowEnd--;
                endHeight = windowInterface.getRowHeight(rowEnd);
            }
        }
        output.offsetX = original.offsetX = offsetX;
        output.offsetY = original.offsetY = offsetY;
        // 调试代码，可丢弃
        output.syncTime = original.syncTime = System.nanoTime() - startTime;

        int lastColStart = original.colStart;
        int lastRowStart = original.rowStart;
        int lastColEnd = original.colEnd;
        int lastRowEnd = original.rowEnd;
        windowInterface.onWindowCalculated(colStart, rowStart, colEnd, rowEnd);
        if (lastColStart != colStart || lastRowStart != rowStart
                || lastColEnd != colEnd || lastRowEnd != rowEnd) {
            original.colStart = colStart;
            original.rowStart = rowStart;
            original.colEnd = colEnd;
            original.rowEnd = rowEnd;
            original.totalWidth = totalWidth;
            original.totalHeight = totalHeight;
            original.copyTo(output);
            diff(lastColStart, lastRowStart, lastColEnd, lastRowEnd, colStart, rowStart, colEnd, rowEnd);
        }
        return true;
    }

    // 定义原点
    public boolean seek(int column, int row, float offsetX, float offsetY) {
        if (isEmpty() || !checkLocationInBounds(column, row)) {
            return false;
        }
        int rightBound = boundaryInterface.getRightBound();
        int bottomBound = boundaryInterface.getBottomBound();
        int totalWidth = (int) offsetX;
        int totalHeight = (int) offsetY;
        int colEnd = column;
        int rowEnd = row;

        int c = column;
        while (c <= rightBound) {
            int r = row;
            while (r <= bottomBound) {
                windowInterface.in(c, r);

                if (c == column) {
                    totalHeight += windowInterface.getRowHeight(r);
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

            totalWidth += windowInterface.getColWidth(c);
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

        original.colStart = column;
        original.rowStart = row;
        original.offsetX = 0;
        original.offsetY = 0;
        original.totalWidth = totalWidth - (int) offsetX;
        original.totalHeight = totalHeight - (int) offsetY;
        original.colEnd = colEnd;
        original.rowEnd = rowEnd;
        original.copyTo(output);
        sync(offsetX, offsetY);
        return true;
    }

    public void updateWidth(int column, int oldWidth, int newWidth, int gravity) {
        if (column >= original.colStart && column <= original.colEnd) {
            original.totalWidth += (newWidth - oldWidth);
            float newOffsetX;
            if (gravity == DIMEN_GRAVITY_START) {
                // 左对齐，右扩展或收缩
                newOffsetX = original.offsetX;
            } else if (gravity == DIMEN_GRAVITY_END) {
                // 右对齐，左扩展或收缩
                newOffsetX = original.offsetX + oldWidth - newWidth;
            } else {
                // 居中对齐，左右扩展或收缩
                newOffsetX = original.offsetX + (oldWidth - newWidth) / 2f;
            }
            float dx = newOffsetX - original.offsetX;
            output.totalWidth = original.totalWidth;
            sync(dx, 0);
        }
    }

    public void updateHeight(int row, int oldHeight, int newHeight, int gravity) {
        if (row >= original.rowStart && row <= original.rowEnd) {
            original.totalHeight += (newHeight - oldHeight);
            float newOffsetY;
            if (gravity == DIMEN_GRAVITY_START) {
                // 上对齐，下扩展或收缩
                newOffsetY = original.offsetY;
            } else if (gravity == DIMEN_GRAVITY_END) {
                // 下对齐，上扩展或收缩
                newOffsetY = original.offsetY + oldHeight - newHeight;
            } else {
                // 居中对齐，上下扩展或收缩
                newOffsetY = original.offsetY + (oldHeight - newHeight) / 2f;
            }
            float dy = newOffsetY - original.offsetY;
            output.totalHeight = original.totalHeight;
            sync(0, dy);
        }
    }

    public void updateSize(int column, int oldWidth, int newWidth, int hGravity,
                           int row, int oldHeight, int newHeight, int vGravity) {
        float dx = 0;
        float dy = 0;
        if (column >= original.colStart && column <= original.colEnd) {
            original.totalWidth += (newWidth - oldWidth);
            float newOffsetX;
            if (hGravity == DIMEN_GRAVITY_START) {
                // 左对齐，右扩展或收缩
                newOffsetX = original.offsetX;
            } else if (hGravity == DIMEN_GRAVITY_END) {
                // 右对齐，左扩展或收缩
                newOffsetX = original.offsetX + oldWidth - newWidth;
            } else {
                // 居中对齐，左右扩展或收缩
                newOffsetX = original.offsetX + (oldWidth - newWidth) / 2f;
            }
            dx = newOffsetX - original.offsetX;
            output.totalWidth = original.totalWidth;
        }
        if (row >= original.rowStart && row <= original.rowEnd) {
            original.totalHeight += (newHeight - oldHeight);
            float newOffsetY;
            if (vGravity == DIMEN_GRAVITY_START) {
                // 上对齐，下扩展或收缩
                newOffsetY = original.offsetY;
            } else if (vGravity == DIMEN_GRAVITY_END) {
                // 下对齐，上扩展或收缩
                newOffsetY = original.offsetY + oldHeight - newHeight;
            } else {
                // 居中对齐，上下扩展或收缩
                newOffsetY = original.offsetY + (oldHeight - newHeight) / 2f;
            }
            dy = newOffsetY - original.offsetY;
            output.totalHeight = original.totalHeight;
        }
        sync(dx, dy);
    }

    // 处理视窗边界
    private void diff(int oldColStart, int oldRowStart,
                      int oldColEnd, int oldRowEnd,
                      int newColStart, int newRowStart,
                      int newColEnd, int newRowEnd) {
        if (newColStart > oldColEnd || newRowStart > oldRowEnd || newColEnd < oldColStart || newRowEnd < oldRowStart) {
            // 说明 sync 跑了很远，直接兜底
            int oldX = oldColStart;
            while (oldX <= oldColEnd) {
                int oldY = oldRowStart;
                while (oldY <= oldRowEnd) {
                    windowInterface.out(oldX, oldY);
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
                    windowInterface.in(newX, newY);
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
            diffRegion(inLeft, boundRight, boundTop, inTop - 1,
                    oldColStart, oldRowStart, oldColEnd, oldRowEnd,
                    newColStart, newRowStart, newColEnd, newRowEnd);
        }

        // 遍历右边区域
        if (inRight < boundRight) {
            diffRegion(inRight + 1, boundRight, inTop, boundBottom,
                    oldColStart, oldRowStart, oldColEnd, oldRowEnd,
                    newColStart, newRowStart, newColEnd, newRowEnd);
        }

        // 遍历底部区域
        if (inBottom < boundBottom) {
            diffRegion(boundLeft, inRight, inBottom + 1, boundBottom,
                    oldColStart, oldRowStart, oldColEnd, oldRowEnd,
                    newColStart, newRowStart, newColEnd, newRowEnd);
        }

        // 遍历左边区域
        if (boundLeft < inLeft) {
            diffRegion(boundLeft, inLeft - 1, inTop, inBottom,
                    oldColStart, oldRowStart, oldColEnd, oldRowEnd,
                    newColStart, newRowStart, newColEnd, newRowEnd);
        }
    }

    // 处理区域内的晶格进出
    private void diffRegion(int xStart, int xEnd, int yStart, int yEnd,
                            int oldColStart, int oldRowStart, int oldColEnd, int oldRowEnd,
                            int newColStart, int newRowStart, int newColEnd, int newRowEnd) {
        int x = xStart;
        while (x <= xEnd) {
            int y = yStart;
            while (y <= yEnd) {
                boolean inBefore = x >= oldColStart && x <= oldColEnd && y >= oldRowStart && y <= oldRowEnd;
                boolean inAfter = x >= newColStart && x <= newColEnd && y >= newRowStart && y <= newRowEnd;
                if (inBefore && !inAfter) {
                    windowInterface.out(x, y);
                } else if (!inBefore && inAfter) {
                    windowInterface.in(x, y);
                }
                if (y == yEnd) break;
                y++;
            }
            if (x == xEnd) break;
            x++;
        }
    }

    public LayoutModel getLayoutModel() {
        return output;
    }

    // 检查晶格是否在边界内
    public boolean checkLocationInBounds(int column, int row) {
        return column >= boundaryInterface.getLeftBound() &&
                column <= boundaryInterface.getRightBound() &&
                row >= boundaryInterface.getTopBound() &&
                row <= boundaryInterface.getBottomBound();
    }

    // 检查边界是否为空
    public boolean isEmpty() {
        return boundaryInterface.getLeftBound() > boundaryInterface.getRightBound()
            || boundaryInterface.getTopBound() > boundaryInterface.getBottomBound();
    }

    public boolean isAtLeftBound() {
        return original.colStart == boundaryInterface.getLeftBound() && original.offsetX == 0;
    }

    public boolean isAtTopBound() {
        return original.rowStart == boundaryInterface.getTopBound() && original.offsetY == 0;
    }

    public boolean isAtRightBound() {
        return original.colEnd == boundaryInterface.getRightBound() && original.totalWidth + original.offsetX == windowWidth;
    }

    public boolean isAtBottomBound() {
        return original.rowEnd == boundaryInterface.getBottomBound() && original.totalHeight + original.offsetY == windowHeight;
    }

    public void reset() {
        original.reset();
        original.copyTo(output);
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

    public int getWindowWidth() {
        return windowWidth;
    }

    public int getWindowHeight() {
        return windowHeight;
    }

    public void setWindowWidth(int width) {
        windowWidth = width;
    }

    public void setWindowHeight(int height) {
        windowHeight = height;
    }

    // 逻辑边界接口(闭区间)
    public interface BoundaryInterface {

        // 获取左边界(支持 MIN_VALUE)
        int getLeftBound();

        // 获取上边界(支持 MIN_VALUE)
        int getTopBound();

        // 获取右边界(支持 MAX_VALUE)
        int getRightBound();

        // 获取下边界(支持 MAX_VALUE)
        int getBottomBound();

    }

    // 视窗交互接口
    public interface WindowInterface {

        // 使指定晶格加载并进入视窗
        void in(int column, int row);

        // 使指定晶格离开视窗
        void out(int column, int row);

        // 新视窗计算完毕，即将进行边界处理
        void onWindowCalculated(int colStart, int rowStart, int colEnd, int rowEnd);

        // 获取指定列宽
        int getColWidth(int column);

        // 获取指定行高
        int getRowHeight(int row);

    }

    // 跨平台跨语言兼容方法

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
