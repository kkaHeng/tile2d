/*
 * Tile2D 的 JS 按需移植（演示用）
 *
 * 只复刻演示 Demo 需要的部分：
 *   - LayoutModel  ：布局模型（原始 / 输出快照）
 *   - LayoutEngine ：滚动核心，与 Java 版逐行对齐（含注释）
 *   - TileDomCore  ：容器层，对照 app 端 TileLayout——瓦片是真实的 DOM 元素，
 *                    进出视窗时挂上/摘下容器，逐格写位置与尺寸，缩放用 scaleFactor
 * 完整框架（TileManager 的预取/濒死区、DimenManager、TileCoreService）见 Java 侧与 docs/。
 *
 * 约定：瓦片 key 用字符串 "列,行"（JS 位运算只有 32 位，无法直接搬 long 编码）。
 */

// ==================== 布局模型（LayoutModel） ====================

class LayoutModel {
    constructor() {
        this.colStart = 0;
        this.rowStart = 0;
        this.colEnd = -1;
        this.rowEnd = -1;
        this.offsetX = 0;
        this.offsetY = 0;
        this.contentWidth = 0;
        this.contentHeight = 0;
    }
    copyTo(m) {
        m.colStart = this.colStart; m.rowStart = this.rowStart;
        m.colEnd = this.colEnd; m.rowEnd = this.rowEnd;
        m.offsetX = this.offsetX; m.offsetY = this.offsetY;
        m.contentWidth = this.contentWidth; m.contentHeight = this.contentHeight;
    }
    newInstance() {
        const m = new LayoutModel();
        this.copyTo(m);
        return m;
    }
    reset() {
        this.colStart = 0; this.rowStart = 0;
        this.colEnd = -1; this.rowEnd = -1;
        this.offsetX = 0; this.offsetY = 0;
        this.contentWidth = 0; this.contentHeight = 0;
    }
}

// ==================== 布局引擎（LayoutEngine）完整移植 ====================

// 核心布局引擎
// 一步一脚印，一步一世界
// 支持跨平台移植(可删除调试代码)
// offset 的取值范围是 [-瓦片尺寸, 0]
// [-瓦片尺寸, 0] 中的瓦片尺寸是当前起始锚点的列宽或行高
// offset 为正时，内容向右/下方向移动，反之向左/上方移动
class LayoutEngine {

    // 数据边界接口
    // 只要你的整数类型支持，设置什么值都行，呃……NaN除外
    // 就算你有数学真无限的整数类型，布局引擎的流程控制也完全不需要改动

    // 视窗交互接口
    // 我没说「视窗」一定得是能看得见的
    // 总之，通过实现这个接口，你可以以任何你想要的方式渲染

    // 原始布局模型，不准在外部修改哈！
    // 输出布局模型，使用这个就行了，爱怎么改就怎么改，反正不会影响视窗内部的状态
    constructor(boundaryInterface, windowInterface) {
        this.boundary = boundaryInterface;
        this.window = windowInterface;
        this.original = new LayoutModel();
        this.output = new LayoutModel();

        // 一些简单的状态变量
        this.horizontalScrollEnabled = true;
        this.verticalScrollEnabled = true;
        this.windowWidth = 0;
        this.windowHeight = 0;
    }

    // 同步视窗(帧间高频滚动)
    sync(dx, dy) {
        // 调试代码，可丢弃：Java 版在这里取 timeProvider.cpuNanoTime()，JS 版未移植
        const o = this.original;
        let colStart = o.colStart;
        let rowStart = o.rowStart;
        let colEnd = o.colEnd;
        let rowEnd = o.rowEnd;

        const leftBound = this.boundary.getLeftBound();
        const topBound = this.boundary.getTopBound();
        const rightBound = this.boundary.getRightBound();
        const bottomBound = this.boundary.getBottomBound();
        if (colStart > rightBound ||
            rowStart > bottomBound ||
            colEnd < leftBound ||
            rowEnd < topBound ||
            this.windowWidth <= 0 ||
            this.windowHeight <= 0) {
            // 窗口状态不合法，避免向外传递不合法的坐标，直接短路
            return false;
        }
        // dx/dy 为正时，内容向右/下移动，反之向左/上移动
        let contentWidth = o.contentWidth;
        let contentHeight = o.contentHeight;

        // 横向同步到 [-瓦片宽度, 0]
        if (this.horizontalScrollEnabled) {
            let offsetX = o.offsetX + dx;

            // 起始锚点
            if (contentWidth + offsetX < this.windowWidth && colEnd === rightBound) {
                // 右侧有空白，尝试右对齐，伪造用户向右拖事件
                // 在日常滚动中，通过这种方式避免右边出现空白，继续往左边拖动不会发生变化
                // 在 seek 调整场景中，如果距离数据右边界太近，会触发下面的循环补充左边
                offsetX += this.windowWidth - (contentWidth + offsetX);
            }
            while (offsetX > 0 && colStart > leftBound) {
                // 用户向右拖，内容向右边滚动，锚点左移
                // 左侧瓦片进入
                colStart--;
                const width = this.window.getColWidth(colStart);
                offsetX -= width;
                contentWidth += width;
            }
            let startWidth = this.window.getColWidth(colStart);
            while (offsetX < -startWidth && colStart < rightBound) {
                // 用户向左拖，内容向左边滚动，锚点右移
                // 左侧瓦片离开
                offsetX += startWidth;
                contentWidth -= startWidth;
                colStart++;
                startWidth = this.window.getColWidth(colStart);
            }
            if (colStart > colEnd) {
                // 起点锚点越过结尾锚点了
                // 让结尾锚点跳过前面的循环直接跟上，然后扩展直至填满视窗或抵达数据边界
                colEnd = colStart;
                contentWidth = startWidth;
            }
            if (offsetX > 0 && colStart === leftBound) {
                // 左边存在空白，内容无法填满窗口，强制对齐左边缘
                // 继续往右边拖动不会发生变化
                offsetX = 0;
            }

            // 结尾锚点
            while (contentWidth + offsetX < this.windowWidth && colEnd < rightBound) {
                // 内容填不满窗口，扩展锚点
                // 右侧瓦片进入
                colEnd++;
                contentWidth += this.window.getColWidth(colEnd);
            }
            let endWidth = this.window.getColWidth(colEnd);
            while (contentWidth + offsetX - endWidth > this.windowWidth && colEnd > colStart) {
                // 内容过度超出窗口，收缩锚点
                // 右侧瓦片离开
                contentWidth -= endWidth;
                colEnd--;
                endWidth = this.window.getColWidth(colEnd);
            }
            if (contentWidth > this.windowWidth && contentWidth + offsetX < this.windowWidth && colEnd === rightBound) {
                // 复核发现前面2个循环导致右边出现空白，处理掉
                offsetX += this.windowWidth - (contentWidth + offsetX);
            }
            this.output.offsetX = o.offsetX = offsetX;
        }

        // 纵向同步 (同上)
        if (this.verticalScrollEnabled) {
            let offsetY = o.offsetY + dy;

            if (contentHeight + offsetY < this.windowHeight && rowEnd === bottomBound) {
                offsetY += this.windowHeight - (contentHeight + offsetY);
            }
            while (offsetY > 0 && rowStart > topBound) {
                rowStart--;
                const height = this.window.getRowHeight(rowStart);
                offsetY -= height;
                contentHeight += height;
            }
            let startHeight = this.window.getRowHeight(rowStart);
            while (offsetY < -startHeight && rowStart < bottomBound) {
                offsetY += startHeight;
                contentHeight -= startHeight;
                rowStart++;
                startHeight = this.window.getRowHeight(rowStart);
            }
            if (rowStart > rowEnd) {
                rowEnd = rowStart;
                contentHeight = startHeight;
            }
            if (offsetY > 0 && rowStart === topBound) {
                offsetY = 0;
            }
            while (contentHeight + offsetY < this.windowHeight && rowEnd < bottomBound) {
                rowEnd++;
                contentHeight += this.window.getRowHeight(rowEnd);
            }
            let endHeight = this.window.getRowHeight(rowEnd);
            while (contentHeight + offsetY - endHeight > this.windowHeight && rowEnd > rowStart) {
                contentHeight -= endHeight;
                rowEnd--;
                endHeight = this.window.getRowHeight(rowEnd);
            }
            if (contentHeight > this.windowHeight && contentHeight + offsetY < this.windowHeight && rowEnd === bottomBound) {
                offsetY += this.windowHeight - (contentHeight + offsetY);
            }
            this.output.offsetY = o.offsetY = offsetY;
        }

        const lastColStart = o.colStart;
        const lastRowStart = o.rowStart;
        const lastColEnd = o.colEnd;
        const lastRowEnd = o.rowEnd;
        // 通知视窗计算完毕
        this.window.onWindowCalculated(colStart, rowStart, colEnd, rowEnd);
        if (lastColStart !== colStart || lastRowStart !== rowStart
            || lastColEnd !== colEnd || lastRowEnd !== rowEnd) {
            // 视窗锚点发生变化，批量处理进出
            o.colStart = colStart;
            o.rowStart = rowStart;
            o.colEnd = colEnd;
            o.rowEnd = rowEnd;
            o.contentWidth = contentWidth;
            o.contentHeight = contentHeight;
            o.copyTo(this.output);
            this.diff(lastColStart, lastRowStart, lastColEnd, lastRowEnd,
                colStart, rowStart, colEnd, rowEnd);
        }
        return true;
    }

    // 定义原点(距离无关跳转)
    seek(column, row, offsetX, offsetY) {
        if (this.isEmpty() || !this.checkLocationInBounds(column, row)) {
            return false;
        }
        const rightBound = this.boundary.getRightBound();
        const bottomBound = this.boundary.getBottomBound();
        let contentWidth = 0;
        let contentHeight = 0;
        let colEnd = column;
        let rowEnd = row;

        // 粗略预估(复杂度小于等于一个视窗)
        let c = column;
        while (c <= rightBound) {
            let r = row;
            while (r <= bottomBound) {
                this.window.in(c, r);

                if (c === column) {
                    contentHeight += this.window.getRowHeight(r);
                    if (contentHeight > this.windowHeight) {
                        rowEnd = r;
                        break;
                    }
                } else {
                    if (r === rowEnd) break;
                }
                if (r === bottomBound) {
                    rowEnd = r;
                    break;
                }
                r++;
            }

            contentWidth += this.window.getColWidth(c);
            if (contentWidth > this.windowWidth) {
                colEnd = c;
                break;
            }
            if (c === rightBound) {
                // 已到达尽头
                // 避坑：未更新 colEnd 导致在右下边界处出现 contentWidth、contentHeight 与实际不同步的问题
                colEnd = c;
                break;
            }
            c++;
        }

        const o = this.original;
        // 覆盖状态，避免 sync 依赖错误的旧状态
        o.colStart = column;
        o.rowStart = row;
        o.offsetX = 0;
        o.offsetY = 0;
        o.contentWidth = contentWidth - Math.trunc(offsetX);
        o.contentHeight = contentHeight - Math.trunc(offsetY);
        o.colEnd = colEnd;
        o.rowEnd = rowEnd;
        // 强制同步，避免 sync 认为锚点没有变化导致输出模型看不到结果
        o.copyTo(this.output);

        // 精确调整(如果视窗没填满或 offset 会引发视窗锚点移动)
        this.sync(offsetX, offsetY);
        return true;
    }

    // 对视窗内的尺寸变更事件进行位移补充

    updateWidth(column, oldWidth, newWidth, gravity) {
        if (oldWidth === newWidth) return;
        const o = this.original;
        if (column >= o.colStart && column <= o.colEnd) {
            o.contentWidth += (newWidth - oldWidth);
            let newOffsetX;
            if (gravity === LayoutEngine.DIMEN_GRAVITY_START) {
                // 左对齐，右扩展或收缩
                newOffsetX = o.offsetX;
            } else if (gravity === LayoutEngine.DIMEN_GRAVITY_END) {
                // 右对齐，左扩展或收缩
                newOffsetX = o.offsetX + oldWidth - newWidth;
            } else {
                // 居中对齐，左右扩展或收缩
                newOffsetX = o.offsetX + (oldWidth - newWidth) / 2;
            }
            const dx = newOffsetX - o.offsetX;
            this.output.contentWidth = o.contentWidth;
            this.sync(dx, 0);
        }
    }

    updateHeight(row, oldHeight, newHeight, gravity) {
        if (oldHeight === newHeight) return;
        const o = this.original;
        if (row >= o.rowStart && row <= o.rowEnd) {
            o.contentHeight += (newHeight - oldHeight);
            let newOffsetY;
            if (gravity === LayoutEngine.DIMEN_GRAVITY_START) {
                // 上对齐，下扩展或收缩
                newOffsetY = o.offsetY;
            } else if (gravity === LayoutEngine.DIMEN_GRAVITY_END) {
                // 下对齐，上扩展或收缩
                newOffsetY = o.offsetY + oldHeight - newHeight;
            } else {
                // 居中对齐，上下扩展或收缩
                newOffsetY = o.offsetY + (oldHeight - newHeight) / 2;
            }
            const dy = newOffsetY - o.offsetY;
            this.output.contentHeight = o.contentHeight;
            this.sync(0, dy);
        }
    }

    updateSize(column, oldWidth, newWidth, hGravity, row, oldHeight, newHeight, vGravity) {
        const o = this.original;
        let dx = 0;
        let dy = 0;
        if (column >= o.colStart && column <= o.colEnd && oldWidth !== newWidth) {
            o.contentWidth += (newWidth - oldWidth);
            let newOffsetX;
            if (hGravity === LayoutEngine.DIMEN_GRAVITY_START) {
                // 左对齐，右扩展或收缩
                newOffsetX = o.offsetX;
            } else if (hGravity === LayoutEngine.DIMEN_GRAVITY_END) {
                // 右对齐，左扩展或收缩
                newOffsetX = o.offsetX + oldWidth - newWidth;
            } else {
                // 居中对齐，左右扩展或收缩
                newOffsetX = o.offsetX + (oldWidth - newWidth) / 2;
            }
            dx = newOffsetX - o.offsetX;
            this.output.contentWidth = o.contentWidth;
        }
        if (row >= o.rowStart && row <= o.rowEnd && oldHeight !== newHeight) {
            o.contentHeight += (newHeight - oldHeight);
            let newOffsetY;
            if (vGravity === LayoutEngine.DIMEN_GRAVITY_START) {
                // 上对齐，下扩展或收缩
                newOffsetY = o.offsetY;
            } else if (vGravity === LayoutEngine.DIMEN_GRAVITY_END) {
                // 下对齐，上扩展或收缩
                newOffsetY = o.offsetY + oldHeight - newHeight;
            } else {
                // 居中对齐，上下扩展或收缩
                newOffsetY = o.offsetY + (oldHeight - newHeight) / 2;
            }
            dy = newOffsetY - o.offsetY;
            this.output.contentHeight = o.contentHeight;
        }
        this.sync(dx, dy);
    }

    // 处理视窗边界
    diff(oldColStart, oldRowStart, oldColEnd, oldRowEnd,
         newColStart, newRowStart, newColEnd, newRowEnd) {
        if (newColStart > oldColEnd || newRowStart > oldRowEnd || newColEnd < oldColStart || newRowEnd < oldRowStart) {
            // 说明 sync 跑了很远，直接兜底
            // 通常是由于 sync 收到异常巨大的 dx/dy 引起的，和 seek 不冲突
            for (let x = oldColStart; x <= oldColEnd; x++) {
                for (let y = oldRowStart; y <= oldRowEnd; y++) {
                    this.window.out(x, y);
                }
            }
            for (let x = newColStart; x <= newColEnd; x++) {
                for (let y = newRowStart; y <= newRowEnd; y++) {
                    this.window.in(x, y);
                }
            }
            return;
        }
        // 计算最大边界
        const boundLeft = min(oldColStart, newColStart);
        const boundRight = max(oldColEnd, newColEnd);
        const boundTop = min(oldRowStart, newRowStart);
        const boundBottom = max(oldRowEnd, newRowEnd);

        // 计算交集
        const inLeft = max(oldColStart, newColStart);
        const inRight = min(oldColEnd, newColEnd);
        const inTop = max(oldRowStart, newRowStart);
        const inBottom = min(oldRowEnd, newRowEnd);

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
            this.diffRegion(boundLeft, inRight, boundTop, inTop - 1,
                oldColStart, oldRowStart, oldColEnd, oldRowEnd,
                newColStart, newRowStart, newColEnd, newRowEnd);
        }

        // 遍历右边区域
        if (inRight < boundRight) {
            this.diffRegion(inRight + 1, boundRight, boundTop, inBottom,
                oldColStart, oldRowStart, oldColEnd, oldRowEnd,
                newColStart, newRowStart, newColEnd, newRowEnd);
        }

        // 遍历底部区域
        if (inBottom < boundBottom) {
            this.diffRegion(inLeft, boundRight, inBottom + 1, boundBottom,
                oldColStart, oldRowStart, oldColEnd, oldRowEnd,
                newColStart, newRowStart, newColEnd, newRowEnd);
        }

        // 遍历左边区域
        if (boundLeft < inLeft) {
            this.diffRegion(boundLeft, inLeft - 1, inTop, boundBottom,
                oldColStart, oldRowStart, oldColEnd, oldRowEnd,
                newColStart, newRowStart, newColEnd, newRowEnd);
        }
    }

    // 处理区域内的瓦片进出
    diffRegion(xStart, xEnd, yStart, yEnd,
               oldColStart, oldRowStart, oldColEnd, oldRowEnd,
               newColStart, newRowStart, newColEnd, newRowEnd) {
        for (let x = xStart; x <= xEnd; x++) {
            for (let y = yStart; y <= yEnd; y++) {
                const inBefore = x >= oldColStart && x <= oldColEnd && y >= oldRowStart && y <= oldRowEnd;
                const inAfter = x >= newColStart && x <= newColEnd && y >= newRowStart && y <= newRowEnd;
                if (inBefore && !inAfter) {
                    this.window.out(x, y);
                } else if (!inBefore && inAfter) {
                    this.window.in(x, y);
                }
            }
        }
    }

    getLayoutModel() { return this.output; }

    // 检查是否在边界内
    checkLocationInBounds(column, row) {
        return column >= this.boundary.getLeftBound() &&
            column <= this.boundary.getRightBound() &&
            row >= this.boundary.getTopBound() &&
            row <= this.boundary.getBottomBound();
    }

    // 检查边界是否为空
    isEmpty() {
        return this.boundary.getLeftBound() > this.boundary.getRightBound()
            || this.boundary.getTopBound() > this.boundary.getBottomBound();
    }

    // 检查是否触及数据边界(像素级)
    // offset 只参与加减运算，它的值严格限定在 [-瓦片尺寸, 0] 中；
    // 自身累加几乎不产生误差（起点是0，每帧只加一次），误差主要来自外部传入的 dx/dy；
    // 滚到头时，2个补偿条件会把它对齐或重置为0，所以这里直接比较就够，不必留容差。

    isAtLeftBound() {
        return this.original.colStart === this.boundary.getLeftBound() && this.original.offsetX === 0;
    }

    isAtTopBound() {
        return this.original.rowStart === this.boundary.getTopBound() && this.original.offsetY === 0;
    }

    isAtRightBound() {
        return this.original.colEnd === this.boundary.getRightBound()
            && this.original.contentWidth + this.original.offsetX === this.windowWidth;
    }

    isAtBottomBound() {
        return this.original.rowEnd === this.boundary.getBottomBound()
            && this.original.contentHeight + this.original.offsetY === this.windowHeight;
    }

    // 一些简单的状态操作

    reset() {
        this.original.reset();
        this.original.copyTo(this.output);
    }

    setHorizontalScrollEnabled(enabled) {
        this.horizontalScrollEnabled = enabled;
    }

    setVerticalScrollEnabled(enabled) {
        this.verticalScrollEnabled = enabled;
    }

    isHorizontalScrollEnabled() {
        return this.horizontalScrollEnabled;
    }

    isVerticalScrollEnabled() {
        return this.verticalScrollEnabled;
    }

    getWindowWidth() {
        return this.windowWidth;
    }

    getWindowHeight() {
        return this.windowHeight;
    }

    setWindowWidth(width) {
        this.windowWidth = width;
    }

    setWindowHeight(height) {
        this.windowHeight = height;
    }

}

// 跨平台跨语言兼容方法（其他语言用原生 min/max 或直接比较即可）

function min(a, b) {
    return a <= b ? a : b;
}

function max(a, b) {
    return a >= b ? a : b;
}

// 更新尺寸时可用的补偿方式

LayoutEngine.DIMEN_GRAVITY_CENTER = 0; // 居中对齐
LayoutEngine.DIMEN_GRAVITY_START = -1; // 左对齐
LayoutEngine.DIMEN_GRAVITY_END = 1; // 右对齐

// ==================== 演示用轻量容器层（TileDomCore） ====================
//
// 对照 app 端 TileLayout（ViewGroup + 真实子 View 承载瓦片）的 JS 版：
//   - 瓦片 = 真实的 DOM 元素（div），进出视窗时挂上/摘下容器
//   - 摆放 = layoutTiles()：沿视窗逐格算像素矩形，写进元素的 transform / 宽高
//   - 缩放 = scaleFactor：几何与字号都乘以它（与 TileLayout 的 scale() 一致）
//   - 尺寸表 = 逐列宽 / 逐行高 + 默认值；瓦片按类型回收复用

class TileDomCore {

    constructor(adapter, container) {
        this.adapter = adapter;
        this.container = container;   // 承载瓦片的容器（position: relative; overflow: hidden）

        this.paddingLeft = 0;
        this.paddingTop = 0;
        this.containerWidth = 0;
        this.containerHeight = 0;
        this.viewportWidth = 0;
        this.viewportHeight = 0;
        this.scaleFactor = 1;         // 缩放（几何与字号都乘它）

        // 尺寸表：单独设置 > 默认值
        this.widths = new Map();
        this.heights = new Map();
        this.defaultTileWidth = 80;
        this.defaultTileHeight = 45;

        // 瓦片：活跃表 + 按类型回收栈
        this.active = new Map();
        this.recycled = new Map();
        this.recycledCount = 0;

        const self = this;
        this.engine = new LayoutEngine({
            getLeftBound: () => adapter.getLeftBound(),
            getTopBound: () => adapter.getTopBound(),
            getRightBound: () => adapter.getRightBound(),
            getBottomBound: () => adapter.getBottomBound(),
        }, {
            in: (c, r) => self.onTileIn(c, r),
            out: (c, r) => self.onTileOut(c, r),
            onWindowCalculated: () => {},
            getColWidth: (c) => self.getTileWidth(c),
            getRowHeight: (r) => self.getTileHeight(r),
        });
    }

    // ---- 视窗尺寸与内边距（对应 TileLayout 的 updateBounds / setPadding） ----

    setPadding(left, top) {
        this.paddingLeft = left;
        this.paddingTop = top;
        this.updateBounds();
    }

    setContainerSize(width, height) {
        this.containerWidth = width;
        this.containerHeight = height;
        this.updateBounds();
    }

    updateBounds() {
        // 引擎的窗口 = 容器尺寸减去内边距（与 TileLayout 一致）
        this.viewportWidth = Math.max(1, Math.round((this.containerWidth || 0) - this.paddingLeft * 2));
        this.viewportHeight = Math.max(1, Math.round((this.containerHeight || 0) - this.paddingTop * 2));
        this.engine.setWindowWidth(this.viewportWidth);
        this.engine.setWindowHeight(this.viewportHeight);
        if (this.containerWidth && this.containerHeight) {
            this.engine.sync(0, 0);
            this.layoutTiles();
        }
    }

    // ---- 缩放（对应 TileLayout.scale() + setScaleFactor） ----

    getScaleFactor() { return this.scaleFactor; }

    setScaleFactor(scaleFactor) {
        this.scaleFactor = scaleFactor;
        this.layoutTiles();
    }

    scale(num) { return num * this.scaleFactor; }

    // ---- 尺寸表 ----

    getTileWidth(column) {
        const w = this.widths.get(column);
        return w === undefined ? this.defaultTileWidth : w;
    }

    getTileHeight(row) {
        const h = this.heights.get(row);
        return h === undefined ? this.defaultTileHeight : h;
    }

    setDefaultSize(width, height) {
        this.defaultTileWidth = Math.max(1, Math.round(width));
        this.defaultTileHeight = Math.max(1, Math.round(height));
        this.layoutTiles();
    }

    setTileWidth(column, width, gravity) {
        width = Math.max(1, Math.round(width));
        const old = this.getTileWidth(column);
        if (old === width) return;
        this.widths.set(column, width);
        this.engine.updateWidth(column, old, width, gravity === undefined ? LayoutEngine.DIMEN_GRAVITY_START : gravity);
        this.layoutTiles();
    }

    setTileHeight(row, height, gravity) {
        height = Math.max(1, Math.round(height));
        const old = this.getTileHeight(row);
        if (old === height) return;
        this.heights.set(row, height);
        this.engine.updateHeight(row, old, height, gravity === undefined ? LayoutEngine.DIMEN_GRAVITY_START : gravity);
        this.layoutTiles();
    }

    // ---- 滚动 / 跳转 ----

    sync(dx, dy) {
        const handled = this.engine.sync(dx, dy);
        if (handled) this.layoutTiles();
        return handled;
    }

    seek(column, row, offsetX, offsetY) {
        const handled = this.engine.seek(column, row, offsetX || 0, offsetY || 0);
        if (handled) this.layoutTiles();
        return handled;
    }

    snap() { this.sync(0, 0); }
    getLayoutModel() { return this.engine.getLayoutModel(); }
    isAtLeftBound() { return this.engine.isAtLeftBound(); }
    isAtTopBound() { return this.engine.isAtTopBound(); }
    isAtRightBound() { return this.engine.isAtRightBound(); }
    isAtBottomBound() { return this.engine.isAtBottomBound(); }
    isEmpty() { return this.engine.isEmpty(); }

    // ---- 瓦片进出：挂上 / 摘下真实 DOM 元素 ----

    static key(column, row) { return column + ',' + row; }

    onTileIn(column, row) {
        const key = TileDomCore.key(column, row);
        if (this.active.has(key)) return;
        const type = this.adapter.getTileType(column, row);
        const tile = this.obtain(type);
        if (!tile) return;
        tile.type = type;
        tile.column = column;
        tile.row = row;
        this.adapter.onBindTileHolder(tile, column, row);
        this.container.appendChild(tile.el);
        this.active.set(key, tile);
    }

    onTileOut(column, row) {
        const key = TileDomCore.key(column, row);
        const tile = this.active.get(key);
        if (!tile) return;
        this.active.delete(key);
        if (tile.el.parentNode === this.container) this.container.removeChild(tile.el);
        this.adapter.onTileOut(tile, column, row);
        let stack = this.recycled.get(tile.type);
        if (!stack) {
            stack = [];
            this.recycled.set(tile.type, stack);
        }
        stack.push(tile);
        this.recycledCount++;
    }

    obtain(type) {
        const stack = this.recycled.get(type);
        if (stack && stack.length > 0) {
            this.recycledCount--;
            return stack.pop();
        }
        return this.adapter.onCreateTileHolder(type);
    }

    getActiveTile(column, row) { return this.active.get(TileDomCore.key(column, row)) || null; }

    // ---- 摆放（对应 TileLayout.layoutTiles） ----

    layoutTiles() {
        const model = this.getLayoutModel();
        if (model.colEnd < model.colStart || model.rowEnd < model.rowStart) return;

        const pad = this.paddingLeft;
        const padY = this.paddingTop;
        let x = pad + this.scale(model.offsetX);
        for (let column = model.colStart; ; column++) {
            const width = this.scale(this.getTileWidth(column));
            let y = padY + this.scale(model.offsetY);
            for (let row = model.rowStart; ; row++) {
                const height = this.scale(this.getTileHeight(row));
                const tile = this.getActiveTile(column, row);
                if (tile) {
                    const el = tile.el;
                    // 位置用 transform（只改合成层，不触发重排）
                    el.style.transform = 'translate3d(' + Math.round(x) + 'px,' + Math.round(y) + 'px,0)';
                    el.style.width = Math.round(width) + 'px';
                    el.style.height = Math.round(height) + 'px';
                    // 字号随缩放走（对应 TileLayout 的 14dp * scaleFactor）
                    el.style.fontSize = this.scale(TileDomCore.TEXT_SIZE).toFixed(2) + 'px';
                }
                y += height;
                if (row === model.rowEnd) break;
            }
            x += width;
            if (column === model.colEnd) break;
        }
    }

    // ---- 单格刷新 / 全量重绑 ----

    update(column, row) {
        const key = TileDomCore.key(column, row);
        const tile = this.active.get(key);
        if (!tile) return;
        const type = this.adapter.getTileType(column, row);
        if (type !== tile.type) {
            this.onTileOut(column, row);
            this.onTileIn(column, row);
        } else {
            this.adapter.onBindTileHolder(tile, column, row);
        }
        this.layoutTiles();
    }

    updateAll() {
        for (const tile of this.active.values()) {
            this.adapter.onBindTileHolder(tile, tile.column, tile.row);
        }
        this.layoutTiles();
    }

    // ---- 命中测试（对应 TileLayout.findColumn / findRow） ----

    findColumn(x) {
        const model = this.getLayoutModel();
        const contentX = (x - this.paddingLeft) / this.scaleFactor;
        let px = model.offsetX;
        for (let c = model.colStart; c <= model.colEnd; c++) {
            const w = this.getTileWidth(c);
            if (contentX < px + w) return c;
            px += w;
        }
        return model.colEnd;
    }

    findRow(y) {
        const model = this.getLayoutModel();
        const contentY = (y - this.paddingTop) / this.scaleFactor;
        let py = model.offsetY;
        for (let r = model.rowStart; r <= model.rowEnd; r++) {
            const h = this.getTileHeight(r);
            if (contentY < py + h) return r;
            py += h;
        }
        return model.rowEnd;
    }

    // ---- 统计 ----

    getActiveTileCount() { return this.active.size; }
    getRecycledTileCount() { return this.recycledCount; }

}

// 瓦片字号（对应 app 端 TileLayout demo 的 14dp）
TileDomCore.TEXT_SIZE = 14;

// 供外部加载（浏览器里类仍是全局的；单测放在项目外的临时目录里做）

if (typeof module !== 'undefined' && module.exports) {
    module.exports = { LayoutModel, LayoutEngine, TileDomCore };
}
