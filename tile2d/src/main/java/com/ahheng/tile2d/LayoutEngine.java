package com.ahheng.tile2d;

import com.ahheng.tile2d.util.time.TimeProvider;

// 核心布局引擎
// 一步一脚印，一步一世界
// 支持跨平台移植(可删除调试代码)
// offset 的取值范围是 [-瓦片尺寸, 0]
// [-瓦片尺寸, 0] 中的瓦片尺寸是当前起始锚点的列宽或行高
// offset 为正时，内容向右/下方向移动，反之向左/上方移动
public class LayoutEngine {

    // 更新尺寸时可用的补偿方式

    public static final int DIMEN_GRAVITY_CENTER = 0; // 居中对齐
    public static final int DIMEN_GRAVITY_START = -1; // 左对齐
    public static final int DIMEN_GRAVITY_END = 1; // 右对齐

    // 数据边界接口
    // 只要你的整数类型支持，设置什么值都行，呃……NaN除外
    // 就算你有数学真无限的整数类型，布局引擎的流程控制也完全不需要改动
    private final BoundaryInterface boundaryInterface;

    // 视窗交互接口
    // 我没说「视窗」一定得是能看得见的
    // 总之，通过实现这个接口，你可以以任何你想要的方式渲染
    private final WindowInterface windowInterface;

    // 原始布局模型，不准在外部修改哈！
    private final LayoutModel original = new LayoutModel();

    // 输出布局模型，使用这个就行了，爱怎么改就怎么改，反正不会影响视窗内部的状态
    private final LayoutModel output = new LayoutModel();

    // 一些简单的状态变量

    private boolean horizontalScrollEnabled = true;
    private boolean verticalScrollEnabled = true;
    private int windowWidth;
    private int windowHeight;
    
    // 调试变量，跨平台可删除

    private TimeProvider timeProvider;
    private long startTime;

    public LayoutEngine(BoundaryInterface boundaryI, WindowInterface windowI) {
        boundaryInterface = boundaryI;
        windowInterface = windowI;
    }

    // 同步视窗(帧间高频滚动)
    public boolean sync(float dx, float dy) {
        // 调试代码，可丢弃
        if (timeProvider != null) startTime = timeProvider.cpuNanoTime();
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
            rowEnd < topBound ||
            windowWidth <= 0 ||
            windowHeight <= 0) {
            // 窗口状态不合法，避免向外传递不合法的坐标，直接短路
            return false;
        }
        // dx/dy 为正时，内容向右/下移动，反之向左/上移动
        int contentWidth = original.contentWidth;
        int contentHeight = original.contentHeight;

        // 横向同步到 [-瓦片宽度, 0]
        if (horizontalScrollEnabled) {
            float offsetX = original.offsetX + dx;

            // 起始锚点
            if (contentWidth + offsetX < windowWidth && colEnd == rightBound) {
                // 右侧有空白，尝试右对齐，伪造用户向右拖事件
                // 在日常滚动中，通过这种方式避免右边出现空白，继续往左边拖动不会发生变化
                // 在 seek 中，如果距离数据右边界太近，会触发下面的循环补充左边
                offsetX = windowWidth - contentWidth;
            }
            while (offsetX > 0 && colStart > leftBound) {
                // 用户向右拖，内容向右边滚动，锚点左移
                // 左侧瓦片进入
                colStart--;
                int width = windowInterface.getColWidth(colStart);
                offsetX -= width;
                contentWidth += width;
            }
            int startWidth = windowInterface.getColWidth(colStart);
            while (offsetX < -startWidth && colStart < rightBound) {
                // 用户向左拖，内容向左边滚动，锚点右移
                // 左侧瓦片离开
                offsetX += startWidth;
                contentWidth -= startWidth;
                colStart++;
                startWidth = windowInterface.getColWidth(colStart);
            }
            if (colStart > colEnd) {
                // 起点锚点越过结尾锚点了
                // 让结尾锚点跳过前面的循环直接跟上，然后扩展直至填满视窗或抵达数据边界
                colEnd = colStart;
                contentWidth = startWidth;
            }
            if (offsetX > 0 && colStart == leftBound) {
                // 左边存在空白，内容无法填满窗口，强制对齐左边缘
                // 继续往右边拖动不会发生变化
                offsetX = 0;
            }

            // 结尾锚点
            while (contentWidth + offsetX < windowWidth && colEnd < rightBound) {
                // 内容填不满窗口，扩展锚点
                // 右侧瓦片进入
                colEnd++;
                contentWidth += windowInterface.getColWidth(colEnd);
            }
            int endWidth = windowInterface.getColWidth(colEnd);
            while (contentWidth + offsetX - endWidth > windowWidth && colEnd > colStart) {
                // 内容过度超出窗口，收缩锚点
                // 右侧瓦片离开
                contentWidth -= endWidth;
                colEnd--;
                endWidth = windowInterface.getColWidth(colEnd);
            }
            if (contentWidth > windowWidth && contentWidth + offsetX < windowWidth && colEnd == rightBound) {
                // 复核发现前面2个循环导致右边出现空白，处理掉
                offsetX = windowWidth - contentWidth;
            }
            output.offsetX = original.offsetX = offsetX;
        }

        // 纵向同步 (同上)
        if (verticalScrollEnabled) {
            float offsetY = original.offsetY + dy;

            if (contentHeight + offsetY < windowHeight && rowEnd == bottomBound) {
                offsetY = windowHeight - contentHeight;
            }
            while (offsetY > 0 && rowStart > topBound) {
                rowStart--;
                int height = windowInterface.getRowHeight(rowStart);
                offsetY -= height;
                contentHeight += height;
            }
            int startHeight = windowInterface.getRowHeight(rowStart);
            while (offsetY < -startHeight && rowStart < bottomBound) {
                offsetY += startHeight;
                contentHeight -= startHeight;
                rowStart++;
                startHeight = windowInterface.getRowHeight(rowStart);
            }
            if (rowStart > rowEnd) {
                // 起点一跳越过结尾锚点：让结尾锚点跟着走（行方向同理）
                rowEnd = rowStart;
                contentHeight = startHeight;
            }
            if (offsetY > 0 && rowStart == topBound) {
                offsetY = 0;
            }
            while (contentHeight + offsetY < windowHeight && rowEnd < bottomBound) {
                rowEnd++;
                contentHeight += windowInterface.getRowHeight(rowEnd);
            }
            int endHeight = windowInterface.getRowHeight(rowEnd);
            while (contentHeight + offsetY - endHeight > windowHeight && rowEnd > rowStart) {
                contentHeight -= endHeight;
                rowEnd--;
                endHeight = windowInterface.getRowHeight(rowEnd);
            }
            if (contentHeight > windowHeight && contentHeight + offsetY < windowHeight && rowEnd == bottomBound) {
                offsetY = windowHeight - contentHeight;
            }
            output.offsetY = original.offsetY = offsetY;
        }
        // 调试代码，可丢弃
        if (timeProvider != null) output.syncTime = original.syncTime = timeProvider.cpuNanoTime() - startTime;

        int lastColStart = original.colStart;
        int lastRowStart = original.rowStart;
        int lastColEnd = original.colEnd;
        int lastRowEnd = original.rowEnd;
        // 通知视窗计算完毕
        windowInterface.onWindowCalculated(colStart, rowStart, colEnd, rowEnd);
        if (lastColStart != colStart || lastRowStart != rowStart
            || lastColEnd != colEnd || lastRowEnd != rowEnd) {
            // 视窗锚点发生变化，批量处理进出
            original.colStart = colStart;
            original.rowStart = rowStart;
            original.colEnd = colEnd;
            original.rowEnd = rowEnd;
            original.contentWidth = contentWidth;
            original.contentHeight = contentHeight;
            original.copyTo(output);
            diff(lastColStart, lastRowStart, lastColEnd, lastRowEnd, colStart, rowStart, colEnd, rowEnd);
        }
        return true;
    }

    // 定义原点(距离无关跳转)
    public boolean seek(int column, int row, float offsetX, float offsetY) {
        if (isEmpty() || !checkLocationInBounds(column, row)) {
            return false;
        }
        int rightBound = boundaryInterface.getRightBound();
        int bottomBound = boundaryInterface.getBottomBound();
        // 强转无关紧要，后面 sync 会精修
        int contentWidth = (int) offsetX;
        int contentHeight = (int) offsetY;
        int colEnd = column;
        int rowEnd = row;

        // 粗略预估(复杂度小于等于一个视窗)
        int c = column;
        while (c <= rightBound) {
            int r = row;
            while (r <= bottomBound) {
                windowInterface.in(c, r);

                if (c == column) {
                    contentHeight += windowInterface.getRowHeight(r);
                    if (contentHeight > windowHeight) {
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

            contentWidth += windowInterface.getColWidth(c);
            if (contentWidth > windowWidth) {
                colEnd = c;
                break;
            }
            if (c == rightBound) {
                // 已到达尽头
                // 避坑：未更新 colEnd 导致在右下边界处出现 contentWidth、contentHeight 与实际不同步的问题
                colEnd = c;
                break;
            }
            c++;
        }

        // 覆盖状态，避免 sync 依赖错误的旧状态
        original.colStart = column;
        original.rowStart = row;
        original.offsetX = 0;
        original.offsetY = 0;
        original.contentWidth = contentWidth - (int) offsetX;
        original.contentHeight = contentHeight - (int) offsetY;
        original.colEnd = colEnd;
        original.rowEnd = rowEnd;
        // 强制同步，避免 sync 认为锚点没有变化导致输出模型看不到结果
        original.copyTo(output);
        
        // 精确调整(如果视窗没填满或 offset 会引发视窗锚点移动)
        sync(offsetX, offsetY);
        return true;
    }

    // 对视窗内的尺寸变更事件进行位移补充

    public void updateWidth(int column, int oldWidth, int newWidth, int gravity) {
        if (oldWidth == newWidth) return;
        if (column >= original.colStart && column <= original.colEnd) {
            original.contentWidth += (newWidth - oldWidth);
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
            output.contentWidth = original.contentWidth;
            sync(dx, 0);
        }
    }

    public void updateHeight(int row, int oldHeight, int newHeight, int gravity) {
        if (oldHeight == newHeight) return;
        if (row >= original.rowStart && row <= original.rowEnd) {
            original.contentHeight += (newHeight - oldHeight);
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
            output.contentHeight = original.contentHeight;
            sync(0, dy);
        }
    }

    public void updateSize(int column, int oldWidth, int newWidth, int hGravity,
                           int row, int oldHeight, int newHeight, int vGravity) {
        float dx = 0;
        float dy = 0;
        if (column >= original.colStart && column <= original.colEnd && oldWidth != newWidth) {
            original.contentWidth += (newWidth - oldWidth);
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
            output.contentWidth = original.contentWidth;
        }
        if (row >= original.rowStart && row <= original.rowEnd && oldHeight != newHeight) {
            original.contentHeight += (newHeight - oldHeight);
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
            output.contentHeight = original.contentHeight;
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
            // 通常是由于 sync 收到异常巨大的 dx/dy 引起的，和 seek 不冲突
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
        
        /*
        +-----------------------+--------+
        |   左上        上       |   右上  |
        +--------+--------------+        |
        |        |              |        |
        |   左   |      视窗     |   右   |
        |        |              |        |
        |        +--------------+--------+
        |  左下   |      下          右下  |
        +--------+-----------------------+
        */

        // 遍历顶部区域
        if (boundTop < inTop) {
            diffRegion(boundLeft, inRight, boundTop, inTop - 1,
                    oldColStart, oldRowStart, oldColEnd, oldRowEnd,
                    newColStart, newRowStart, newColEnd, newRowEnd);
        }

        // 遍历右边区域
        if (inRight < boundRight) {
            diffRegion(inRight + 1, boundRight, boundTop, inBottom,
                    oldColStart, oldRowStart, oldColEnd, oldRowEnd,
                    newColStart, newRowStart, newColEnd, newRowEnd);
        }

        // 遍历底部区域
        if (inBottom < boundBottom) {
            diffRegion(inLeft, boundRight, inBottom + 1, boundBottom,
                    oldColStart, oldRowStart, oldColEnd, oldRowEnd,
                    newColStart, newRowStart, newColEnd, newRowEnd);
        }

        // 遍历左边区域
        if (boundLeft < inLeft) {
            diffRegion(boundLeft, inLeft - 1, inTop, boundBottom,
                    oldColStart, oldRowStart, oldColEnd, oldRowEnd,
                    newColStart, newRowStart, newColEnd, newRowEnd);
        }
    }

    // 处理区域内的瓦片进出
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

    // 检查是否在边界内
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

    // 检查是否触及数据边界(像素级)
    // offset 只参与加减运算，它的值严格限定在 [-瓦片尺寸, 0] 中；
    // 自身累加几乎不产生误差（起点是0，每帧只加一次），误差主要来自外部传入的 dx/dy；
    // 滚到头时，2个补偿条件会把它对齐或重置为0，所以这里直接比较就够，不必留容差。

    public boolean isAtLeftBound() {
        return original.colStart == boundaryInterface.getLeftBound() && original.offsetX == 0;
    }

    public boolean isAtTopBound() {
        return original.rowStart == boundaryInterface.getTopBound() && original.offsetY == 0;
    }

    public boolean isAtRightBound() {
        return original.colEnd == boundaryInterface.getRightBound() && original.contentWidth + original.offsetX == windowWidth;
    }

    public boolean isAtBottomBound() {
        return original.rowEnd == boundaryInterface.getBottomBound() && original.contentHeight + original.offsetY == windowHeight;
    }

    // 一些简单的状态操作

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

    // 调试代码，跨平台可删除

    public void setTimeProvider(TimeProvider timeProvider) {
        this.timeProvider = timeProvider;
    }

    // 数据边界接口(闭区间)
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

        // 使指定瓦片加载并进入视窗
        void in(int column, int row);

        // 使指定瓦片离开视窗
        void out(int column, int row);

        // 新视窗计算完毕，即将进行边界处理(瓦片进出事件)
        void onWindowCalculated(int colStart, int rowStart, int colEnd, int rowEnd);

        // 获取指定列宽
        int getColWidth(int column);

        // 获取指定行高
        int getRowHeight(int row);

    }

    // 跨平台跨语言兼容方法

    public static int min(int a, int b) {
        if (a <= b) {
            return a;
        } else {
            return b;
        }
    }

    public static int max(int a, int b) {
        if (a >= b) {
            return a;
        } else {
            return b;
        }
    }

    public static float min(float a, float b) {
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

    public static float max(float a, float b) {
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
