/*
 * Tile2D 的 JS 按需移植（演示用）
 *
 * 只复刻演示 Demo 需要的部分：
 *   - LayoutModel  ：布局模型（原始 / 输出快照）
 *   - LayoutEngine ：滚动核心，与 Java 版逐行对齐（含注释）
 *   - TileCanvasCore：容器层，对照 app 端 TileView——视窗内的瓦片全部画在一张 <canvas> 上，
 *                    进出视窗只维护一份绘制信息缓存，不增删 DOM，缩放用 scaleFactor
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
        // 累加器从 offset 起算，末尾再减去 offset：两者抵消，得到纯「宽度和」。
        // 若从 0 起算、末尾仍减 offset，contentWidth 会整整差一个 offset，
        // 扩展循环的终止列（colEnd）也会偏一列 —— 表现为缩放/跳转后视窗逻辑尺寸不对（留白或超出不回收）。
        let contentWidth = Math.trunc(offsetX);
        let contentHeight = Math.trunc(offsetY);
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


// ==================== Canvas 自绘容器层（TileCanvasCore） ====================
//
// 自绘版：没有 DOM 瓦片，视窗内的所有瓦片都画在一张 <canvas> 上。
//   - 状态变化时一次性清屏重绘：背景色 + 边框 + 文本
//   - 引擎的 in/out 只用来维护一份「绘制信息缓存」（颜色/文本），不再增删 DOM
//   - 缩放 = scaleFactor：几何与字号都乘以它
//   - 尺寸表 = 逐列宽 / 逐行高 + 默认值
//
// 相比 DOM 版：没有几十上百个元素与合成层，帧率稳定、缩放不抖；
// 代价是文本绘制与命中测试都得自己实现（命中测试见 findColumn / findRow）。

class TileCanvasCore {

    constructor(adapter, canvas) {
        this.adapter = adapter;
        this.canvas = canvas;
        this.ctx = canvas ? canvas.getContext('2d') : null;
        this.dpr = (typeof window !== 'undefined' && window.devicePixelRatio) || 1;

        this.paddingLeft = 0;
        this.paddingTop = 0;
        this.containerWidth = 0;
        this.containerHeight = 0;
        this.viewportWidth = 0;
        this.viewportHeight = 0;
        this.scaleFactor = 1;

        this.background = '#0e1117';   // 空白/稀疏区底色（对应 DOM 版的容器背景）

        // 尺寸表：单独设置 > 默认值
        this.widths = new Map();
        this.heights = new Map();
        this.defaultTileWidth = 80;
        this.defaultTileHeight = 45;

        // 绘制信息缓存：'列,行' -> { color, textColor, text } 或 null（稀疏，不画）
        this.paints = new Map();

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

    // ---- 视窗尺寸与内边距 ----

    setPadding(left, top) {
        this.paddingLeft = left;
        this.paddingTop = top;
        this.updateBounds();
    }

    setContainerSize(width, height) {
        this.containerWidth = width;
        this.containerHeight = height;
        this.resizeCanvas();
        this.updateBounds();
    }

    // 画布后备缓冲按设备像素比放大，绘制坐标仍用 CSS 像素
    resizeCanvas() {
        if (!this.canvas) return;
        const w = Math.max(1, Math.round(this.containerWidth * this.dpr));
        const h = Math.max(1, Math.round(this.containerHeight * this.dpr));
        if (this.canvas.width !== w) this.canvas.width = w;
        if (this.canvas.height !== h) this.canvas.height = h;
    }

    updateBounds() {
        // 引擎的窗口 = (容器尺寸 - 内边距) ÷ 缩放，单位是「内容像素」
        // （对应 TileCoreService.updateWindowSize：bounds.width() / scaleFactor）
        const scale = this.scaleFactor || 1;
        this.viewportWidth = Math.max(1, Math.round(((this.containerWidth || 0) - this.paddingLeft * 2) / scale));
        this.viewportHeight = Math.max(1, Math.round(((this.containerHeight || 0) - this.paddingTop * 2) / scale));
        this.engine.setWindowWidth(this.viewportWidth);
        this.engine.setWindowHeight(this.viewportHeight);
        if (this.containerWidth && this.containerHeight) {
            this.engine.sync(0, 0);
            this.draw();
        }
    }

    // ---- 缩放 ----

    getScaleFactor() { return this.scaleFactor; }

    setScaleFactor(scaleFactor) {
        this.scaleFactor = scaleFactor;
        this.updateBounds();
    }

    // 缩放 + 焦点位移一步到位（对应 TileCoreService.applyZoom）
    zoom(nextScale, dx, dy) {
        this.scaleFactor = nextScale;
        const scale = nextScale || 1;
        this.viewportWidth = Math.max(1, Math.round(((this.containerWidth || 0) - this.paddingLeft * 2) / scale));
        this.viewportHeight = Math.max(1, Math.round(((this.containerHeight || 0) - this.paddingTop * 2) / scale));
        this.engine.setWindowWidth(this.viewportWidth);
        this.engine.setWindowHeight(this.viewportHeight);
        const handled = this.engine.sync(dx || 0, dy || 0);
        this.draw();
        return handled;
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
        this.draw();
    }

    setTileWidth(column, width, gravity) {
        width = Math.max(1, Math.round(width));
        const old = this.getTileWidth(column);
        if (old === width) return;
        this.widths.set(column, width);
        this.engine.updateWidth(column, old, width, gravity === undefined ? LayoutEngine.DIMEN_GRAVITY_START : gravity);
        this.draw();
    }

    setTileHeight(row, height, gravity) {
        height = Math.max(1, Math.round(height));
        const old = this.getTileHeight(row);
        if (old === height) return;
        this.heights.set(row, height);
        this.engine.updateHeight(row, old, height, gravity === undefined ? LayoutEngine.DIMEN_GRAVITY_START : gravity);
        this.draw();
    }

    // ---- 滚动 / 跳转 ----

    sync(dx, dy) {
        const handled = this.engine.sync(dx, dy);
        if (handled) this.draw();
        return handled;
    }

    seek(column, row, offsetX, offsetY) {
        // 跳转是「重置视窗」：先清掉绘制缓存，再让引擎重建
        // （对应 TileCoreService.seek 里的 tileManager.clearActiveAndDying）
        this.paints.clear();
        const handled = this.engine.seek(column, row, offsetX || 0, offsetY || 0);
        if (handled) this.draw();
        return handled;
    }

    snap() {
        // 将视窗吸附回内容边界内（越界时跳到最近合法锚点）
        // 对应 Java 的 TileCoreService.snap()：不能只 sync(0,0)——越界时 sync 会直接短路，
        // 什么都不做（伪无限关闭后锚点还在界外的情况就是这么漏掉的）。
        if (this.isEmpty()) return;
        const model = this.getLayoutModel();
        const left = this.adapter.getLeftBound(), top = this.adapter.getTopBound();
        const right = this.adapter.getRightBound(), bottom = this.adapter.getBottomBound();
        if (model.colStart >= left && model.colEnd <= right &&
            model.rowStart >= top && model.rowEnd <= bottom) {
            return; // 已在界内，无需吸附
        }
        const column = Math.max(left, Math.min(model.colStart, right));
        const row = Math.max(top, Math.min(model.rowStart, bottom));
        this.seek(column, row, 0, 0);
    }
    getLayoutModel() { return this.engine.getLayoutModel(); }
    isAtLeftBound() { return this.engine.isAtLeftBound(); }
    isAtTopBound() { return this.engine.isAtTopBound(); }
    isAtRightBound() { return this.engine.isAtRightBound(); }
    isAtBottomBound() { return this.engine.isAtBottomBound(); }
    isEmpty() { return this.engine.isEmpty(); }

    // ---- 绘制信息缓存：进出视窗只更新缓存，不碰 DOM ----

    static key(column, row) { return column + ',' + row; }

    onTileIn(column, row) {
        const type = this.adapter.getTileType(column, row);
        // 稀疏区（type=-1）存 null，绘制时跳过，露出底色
        this.paints.set(TileCanvasCore.key(column, row),
            type === -1 ? null : this.adapter.getPaint(column, row));
    }

    onTileOut(column, row) {
        this.paints.delete(TileCanvasCore.key(column, row));
    }

    getPaint(column, row) {
        return this.paints.get(TileCanvasCore.key(column, row));
    }

    // 单格刷新（数据变了要重画时用）
    update(column, row) {
        const key = TileCanvasCore.key(column, row);
        if (!this.paints.has(key)) return;
        const type = this.adapter.getTileType(column, row);
        this.paints.set(key, type === -1 ? null : this.adapter.getPaint(column, row));
        this.draw();
    }

    // ---- 绘制（对应 TileView.onDraw） ----

    draw() {
        const ctx = this.ctx;
        if (!ctx) return;
        const W = this.containerWidth, H = this.containerHeight;
        if (!W || !H) return;

        ctx.setTransform(this.dpr, 0, 0, this.dpr, 0, 0);
        ctx.fillStyle = this.background;
        ctx.fillRect(0, 0, W, H);

        const model = this.getLayoutModel();
        if (model.colEnd < model.colStart || model.rowEnd < model.rowStart) return;

        const scale = this.scaleFactor;
        const lineW = TileCanvasCore.BORDER_WIDTH;
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.font = (TileCanvasCore.TEXT_SIZE * scale).toFixed(2) + 'px ' + TileCanvasCore.FONT;

        let x = this.paddingLeft + model.offsetX * scale;
        for (let column = model.colStart; ; column++) {
            const w = this.getTileWidth(column) * scale;
            let y = this.paddingTop + model.offsetY * scale;
            for (let row = model.rowStart; ; row++) {
                const h = this.getTileHeight(row) * scale;
                const paint = this.getPaint(column, row);
                if (paint) {
                    ctx.fillStyle = paint.color;
                    ctx.fillRect(x, y, w, h);
                    if (w > 16 && h > 12) {
                        ctx.strokeStyle = TileCanvasCore.BORDER;
                        ctx.lineWidth = lineW;
                        ctx.strokeRect(x + lineW / 2, y + lineW / 2, w - lineW, h - lineW);
                        // 文本裁剪在瓦片内（对应 DOM 的 overflow: hidden）
                        ctx.save();
                        ctx.beginPath();
                        ctx.rect(x, y, w, h);
                        ctx.clip();
                        ctx.fillStyle = paint.textColor;
                        ctx.fillText(paint.text, x + w / 2, y + h / 2);
                        ctx.restore();
                    }
                }
                y += h;
                if (row === model.rowEnd) break;
            }
            x += w;
            if (column === model.colEnd) break;
        }
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

    getPaintCount() { return this.paints.size; }
}

// 瓦片字号（对应 app 端 TileLayout demo 的 14dp）、边框与字体
TileCanvasCore.TEXT_SIZE = 14;
TileCanvasCore.BORDER = '#808080';
TileCanvasCore.BORDER_WIDTH = 0.5;
TileCanvasCore.FONT = '-apple-system, BlinkMacSystemFont, "Segoe UI", system-ui, "PingFang SC", "Microsoft YaHei", sans-serif';

// 供外部加载（浏览器里类仍是全局的；单测放在项目外的临时目录里做）
if (typeof module !== 'undefined' && module.exports) {
    module.exports = { LayoutModel, LayoutEngine, TileCanvasCore };
}
