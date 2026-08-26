/*
 * Tile2D H5 移植示例 - 纯 JS 实现，无框架依赖
 * 对应 docs/H5移植指南.md 教程，布局引擎为完整移植
 * 瓦片 key 使用字符串 "列,行"（JS 位运算只有 32 位，无法直接搬 long 编码）
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
        this.totalWidth = 0;
        this.totalHeight = 0;
    }
    copyTo(m) {
        m.colStart = this.colStart; m.rowStart = this.rowStart;
        m.colEnd = this.colEnd; m.rowEnd = this.rowEnd;
        m.offsetX = this.offsetX; m.offsetY = this.offsetY;
        m.totalWidth = this.totalWidth; m.totalHeight = this.totalHeight;
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
        this.totalWidth = 0; this.totalHeight = 0;
    }
}

// ==================== 布局引擎（LayoutEngine）完整移植 ====================

class LayoutEngine {
    constructor(boundaryInterface, windowInterface) {
        this.boundary = boundaryInterface;
        this.window = windowInterface;
        this.original = new LayoutModel();
        this.output = new LayoutModel();
        this.horizontalScrollEnabled = true;
        this.verticalScrollEnabled = true;
        this.windowWidth = 0;
        this.windowHeight = 0;
    }

    // 按像素偏移滚动视窗，dx/dy 是视觉位移：正数 dx 使内容向右移动
    sync(dx, dy) {
        const o = this.original;
        let colStart = o.colStart, rowStart = o.rowStart, colEnd = o.colEnd, rowEnd = o.rowEnd;
        const leftBound = this.boundary.getLeftBound();
        const topBound = this.boundary.getTopBound();
        const rightBound = this.boundary.getRightBound();
        const bottomBound = this.boundary.getBottomBound();
        // 1. 合法性短路
        if (colStart > rightBound || rowStart > bottomBound ||
            colEnd < leftBound || rowEnd < topBound ||
            this.windowWidth <= 0 || this.windowHeight <= 0) {
            return false;
        }
        // 2. 偏移叠加
        let offsetX = o.offsetX + dx;
        let offsetY = o.offsetY + dy;
        let totalWidth = o.totalWidth;
        let totalHeight = o.totalHeight;

        // 3. 水平同步
        if (this.horizontalScrollEnabled) {
            // 3a. 内容未填满且已到右边界：尝试右对齐
            if (offsetX <= 0 && totalWidth + offsetX < this.windowWidth && colEnd === rightBound) {
                offsetX += this.windowWidth - (totalWidth + offsetX);
            }
            // 3b. 向右拖，锚点左移，纳入新列
            while (offsetX > 0 && colStart > leftBound) {
                colStart--;
                const w = this.window.getColWidth(colStart);
                offsetX -= w;
                totalWidth += w;
            }
            // 3c. 向左拖，锚点右移，移出旧列
            let startWidth = this.window.getColWidth(colStart);
            while (offsetX < -startWidth && colStart < rightBound) {
                offsetX += startWidth;
                totalWidth -= startWidth;
                colStart++;
                startWidth = this.window.getColWidth(colStart);
            }
            // 3d. 已到左边界：钳位
            if (offsetX > 0 && colStart === leftBound) {
                offsetX = 0;
            }
            // 3e. 内容填不满，向右扩展右锚点
            while (totalWidth + offsetX < this.windowWidth && colEnd < rightBound) {
                colEnd++;
                totalWidth += this.window.getColWidth(colEnd);
            }
            // 3f. 内容超出太多，收缩右锚点
            let endWidth = this.window.getColWidth(colEnd);
            while (totalWidth + offsetX - endWidth > this.windowWidth && colEnd > colStart) {
                totalWidth -= endWidth;
                colEnd--;
                endWidth = this.window.getColWidth(colEnd);
            }
        }

        // 4. 垂直同步（同上）
        if (this.verticalScrollEnabled) {
            if (offsetY <= 0 && totalHeight + offsetY < this.windowHeight && rowEnd === bottomBound) {
                offsetY += this.windowHeight - (totalHeight + offsetY);
            }
            while (offsetY > 0 && rowStart > topBound) {
                rowStart--;
                const h = this.window.getRowHeight(rowStart);
                offsetY -= h;
                totalHeight += h;
            }
            let startHeight = this.window.getRowHeight(rowStart);
            while (offsetY < -startHeight && rowStart < bottomBound) {
                offsetY += startHeight;
                totalHeight -= startHeight;
                rowStart++;
                startHeight = this.window.getRowHeight(rowStart);
            }
            if (offsetY > 0 && rowStart === topBound) {
                offsetY = 0;
            }
            while (totalHeight + offsetY < this.windowHeight && rowEnd < bottomBound) {
                rowEnd++;
                totalHeight += this.window.getRowHeight(rowEnd);
            }
            let endHeight = this.window.getRowHeight(rowEnd);
            while (totalHeight + offsetY - endHeight > this.windowHeight && rowEnd > rowStart) {
                totalHeight -= endHeight;
                rowEnd--;
                endHeight = this.window.getRowHeight(rowEnd);
            }
        }

        // 5. 写快照
        this.output.offsetX = o.offsetX = offsetX;
        this.output.offsetY = o.offsetY = offsetY;

        // 6. 通知视窗计算完毕，范围变化时 diff
        const lastColStart = o.colStart, lastRowStart = o.rowStart;
        const lastColEnd = o.colEnd, lastRowEnd = o.rowEnd;
        this.window.onWindowCalculated(colStart, rowStart, colEnd, rowEnd);
        if (lastColStart !== colStart || lastRowStart !== rowStart ||
            lastColEnd !== colEnd || lastRowEnd !== rowEnd) {
            o.colStart = colStart; o.rowStart = rowStart;
            o.colEnd = colEnd; o.rowEnd = rowEnd;
            o.totalWidth = totalWidth;
            o.totalHeight = totalHeight;
            o.copyTo(this.output);
            this.diff(lastColStart, lastRowStart, lastColEnd, lastRowEnd,
                colStart, rowStart, colEnd, rowEnd);
        }
        return true;
    }

    // 跳转到指定坐标并定义原点
    seek(column, row, offsetX, offsetY) {
        if (this.isEmpty() || !this.checkLocationInBounds(column, row)) {
            return false;
        }
        const rightBound = this.boundary.getRightBound();
        const bottomBound = this.boundary.getBottomBound();
        let totalWidth = offsetX;
        let totalHeight = offsetY;
        let colEnd = column, rowEnd = row;
        let c = column;
        while (c <= rightBound) {
            let r = row;
            while (r <= bottomBound) {
                this.window.in(c, r);
                if (c === column) {
                    totalHeight += this.window.getRowHeight(r);
                    if (totalHeight > this.windowHeight) { rowEnd = r; break; }
                } else {
                    if (r === rowEnd) break;
                }
                if (r === bottomBound) { rowEnd = r; break; }
                r++;
            }
            totalWidth += this.window.getColWidth(c);
            if (totalWidth > this.windowWidth) { colEnd = c; break; }
            if (c === rightBound) { colEnd = c; break; }
            c++;
        }
        const o = this.original;
        o.colStart = column; o.rowStart = row;
        o.offsetX = 0; o.offsetY = 0;
        o.totalWidth = totalWidth - offsetX;
        o.totalHeight = totalHeight - offsetY;
        o.colEnd = colEnd; o.rowEnd = rowEnd;
        o.copyTo(this.output);
        this.sync(offsetX, offsetY);
        return true;
    }

    // 计算新旧视窗矩形的差异
    diff(oldColStart, oldRowStart, oldColEnd, oldRowEnd,
         newColStart, newRowStart, newColEnd, newRowEnd) {
        if (newColStart > oldColEnd || newRowStart > oldRowEnd ||
            newColEnd < oldColStart || newRowEnd < oldRowStart) {
            // 完全不相交：旧范围全部 out，新范围全部 in
            for (let x = oldColStart; x <= oldColEnd; x++) {
                for (let y = oldRowStart; y <= oldRowEnd; y++) this.window.out(x, y);
            }
            for (let x = newColStart; x <= newColEnd; x++) {
                for (let y = newRowStart; y <= newRowEnd; y++) this.window.in(x, y);
            }
            return;
        }
        const boundLeft = Math.min(oldColStart, newColStart);
        const boundRight = Math.max(oldColEnd, newColEnd);
        const boundTop = Math.min(oldRowStart, newRowStart);
        const boundBottom = Math.max(oldRowEnd, newRowEnd);
        const inLeft = Math.max(oldColStart, newColStart);
        const inRight = Math.min(oldColEnd, newColEnd);
        const inTop = Math.max(oldRowStart, newRowStart);
        const inBottom = Math.min(oldRowEnd, newRowEnd);
        // 按「上、右、下、左」四个区域分解并集
        if (boundTop < inTop) {
            this.diffRegion(boundLeft, inRight, boundTop, inTop - 1,
                oldColStart, oldRowStart, oldColEnd, oldRowEnd,
                newColStart, newRowStart, newColEnd, newRowEnd);
        }
        if (inRight < boundRight) {
            this.diffRegion(inRight + 1, boundRight, boundTop, inBottom,
                oldColStart, oldRowStart, oldColEnd, oldRowEnd,
                newColStart, newRowStart, newColEnd, newRowEnd);
        }
        if (inBottom < boundBottom) {
            this.diffRegion(inLeft, boundRight, inBottom + 1, boundBottom,
                oldColStart, oldRowStart, oldColEnd, oldRowEnd,
                newColStart, newRowStart, newColEnd, newRowEnd);
        }
        if (boundLeft < inLeft) {
            this.diffRegion(boundLeft, inLeft - 1, inTop, boundBottom,
                oldColStart, oldRowStart, oldColEnd, oldRowEnd,
                newColStart, newRowStart, newColEnd, newRowEnd);
        }
    }

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

    // 尺寸变化后的视窗补偿
    updateWidth(column, oldWidth, newWidth, gravity) {
        const o = this.original;
        if (column >= o.colStart && column <= o.colEnd) {
            o.totalWidth += (newWidth - oldWidth);
            let newOffsetX;
            if (gravity === LayoutEngine.DIMEN_GRAVITY_START) {
                newOffsetX = o.offsetX;
            } else if (gravity === LayoutEngine.DIMEN_GRAVITY_END) {
                newOffsetX = o.offsetX + oldWidth - newWidth;
            } else {
                newOffsetX = o.offsetX + (oldWidth - newWidth) / 2;
            }
            const dx = newOffsetX - o.offsetX;
            this.output.totalWidth = o.totalWidth;
            this.sync(dx, 0);
        }
    }

    updateHeight(row, oldHeight, newHeight, gravity) {
        const o = this.original;
        if (row >= o.rowStart && row <= o.rowEnd) {
            o.totalHeight += (newHeight - oldHeight);
            let newOffsetY;
            if (gravity === LayoutEngine.DIMEN_GRAVITY_START) {
                newOffsetY = o.offsetY;
            } else if (gravity === LayoutEngine.DIMEN_GRAVITY_END) {
                newOffsetY = o.offsetY + oldHeight - newHeight;
            } else {
                newOffsetY = o.offsetY + (oldHeight - newHeight) / 2;
            }
            const dy = newOffsetY - o.offsetY;
            this.output.totalHeight = o.totalHeight;
            this.sync(0, dy);
        }
    }

    updateSize(column, oldWidth, newWidth, hGravity, row, oldHeight, newHeight, vGravity) {
        let dx = 0, dy = 0;
        const o = this.original;
        if (column >= o.colStart && column <= o.colEnd) {
            o.totalWidth += (newWidth - oldWidth);
            let newOffsetX;
            if (hGravity === LayoutEngine.DIMEN_GRAVITY_START) {
                newOffsetX = o.offsetX;
            } else if (hGravity === LayoutEngine.DIMEN_GRAVITY_END) {
                newOffsetX = o.offsetX + oldWidth - newWidth;
            } else {
                newOffsetX = o.offsetX + (oldWidth - newWidth) / 2;
            }
            dx = newOffsetX - o.offsetX;
            this.output.totalWidth = o.totalWidth;
        }
        if (row >= o.rowStart && row <= o.rowEnd) {
            o.totalHeight += (newHeight - oldHeight);
            let newOffsetY;
            if (vGravity === LayoutEngine.DIMEN_GRAVITY_START) {
                newOffsetY = o.offsetY;
            } else if (vGravity === LayoutEngine.DIMEN_GRAVITY_END) {
                newOffsetY = o.offsetY + oldHeight - newHeight;
            } else {
                newOffsetY = o.offsetY + (oldHeight - newHeight) / 2;
            }
            dy = newOffsetY - o.offsetY;
            this.output.totalHeight = o.totalHeight;
        }
        this.sync(dx, dy);
    }

    getLayoutModel() { return this.output; }

    checkLocationInBounds(column, row) {
        return column >= this.boundary.getLeftBound() && column <= this.boundary.getRightBound() &&
            row >= this.boundary.getTopBound() && row <= this.boundary.getBottomBound();
    }

    isEmpty() {
        return this.boundary.getLeftBound() > this.boundary.getRightBound() ||
            this.boundary.getTopBound() > this.boundary.getBottomBound();
    }

    isAtLeftBound() {
        return this.original.colStart === this.boundary.getLeftBound() && this.original.offsetX === 0;
    }

    isAtTopBound() {
        return this.original.rowStart === this.boundary.getTopBound() && this.original.offsetY === 0;
    }

    isAtRightBound() {
        return this.original.colEnd === this.boundary.getRightBound() &&
            this.original.totalWidth + this.original.offsetX === this.windowWidth;
    }

    isAtBottomBound() {
        return this.original.rowEnd === this.boundary.getBottomBound() &&
            this.original.totalHeight + this.original.offsetY === this.windowHeight;
    }

    reset() {
        this.original.reset();
        this.original.copyTo(this.output);
    }

    setHorizontalScrollEnabled(enabled) { this.horizontalScrollEnabled = enabled; }
    setVerticalScrollEnabled(enabled) { this.verticalScrollEnabled = enabled; }
    isHorizontalScrollEnabled() { return this.horizontalScrollEnabled; }
    isVerticalScrollEnabled() { return this.verticalScrollEnabled; }

    getWindowWidth() { return this.windowWidth; }
    getWindowHeight() { return this.windowHeight; }
    setWindowWidth(width) { this.windowWidth = width; }
    setWindowHeight(height) { this.windowHeight = height; }
}

LayoutEngine.DIMEN_GRAVITY_CENTER = 0;
LayoutEngine.DIMEN_GRAVITY_START = -1;
LayoutEngine.DIMEN_GRAVITY_END = 1;

// ==================== 瓦片管理器（TileManager）演示级实现 ====================

class TileManager {
    // 瓦片 key：字符串 "列,行"
    static key(column, row) { return column + ',' + row; }

    constructor(callback) {
        this.callback = callback;
        this.active = new Map();
        this.dying = new Map();
        this.recycled = new Map(); // type -> 数组（栈）
        this.recycledCount = 0;
        this.dyingColStart = 0; this.dyingColEnd = -1;
        this.dyingRowStart = 0; this.dyingRowEnd = -1;
        this.dyingExpand = 1;
        this.dyingEnabled = true;
    }

    in(column, row) {
        const key = TileManager.key(column, row);
        let tile = this.dying.get(key);
        if (tile) {
            // 从濒死池复用，跳过绑定
            this.dying.delete(key);
        } else {
            const type = this.callback.getTileType(column, row);
            tile = this.obtain(type);
            if (tile) {
                tile.column = column; tile.row = row;
                tile.width = this.callback.getTileWidth(column);
                tile.height = this.callback.getTileHeight(row);
                this.callback.onBindTileHolder(tile, column, row);
            }
        }
        if (tile) {
            this.active.set(key, tile);
            if (tile.onInWindow) tile.onInWindow();
            this.callback.onTileIn(tile, column, row);
        }
    }

    out(column, row) {
        const key = TileManager.key(column, row);
        const tile = this.active.get(key);
        if (tile) {
            this.active.delete(key);
            if (tile.onOutWindow) tile.onOutWindow();
            this.callback.onTileOut(tile, column, row);
            if (this.dyingEnabled) {
                this.dying.set(key, tile);
            } else {
                this.recycle(tile);
            }
        }
    }

    obtain(type) {
        const stack = this.recycled.get(type);
        let tile = (stack && stack.length > 0) ? stack.pop() : null;
        if (tile) {
            this.recycledCount--;
        } else {
            tile = this.callback.onCreateTileHolder(type);
            if (tile) tile.type = type;
        }
        return tile;
    }

    recycle(tile) {
        if (!tile) return;
        let stack = this.recycled.get(tile.type);
        if (!stack) { stack = []; this.recycled.set(tile.type, stack); }
        stack.push(tile);
        if (tile.onRecycled) tile.onRecycled();
        this.callback.onTileRecycled(tile, tile.column, tile.row);
        this.recycledCount++;
    }

    // 视窗计算完毕后清理超出濒死区的瓦片
    diffDying(colStart, rowStart, colEnd, rowEnd) {
        if (this.dyingColStart === colStart && this.dyingColEnd === colEnd &&
            this.dyingRowStart === rowStart && this.dyingRowEnd === rowEnd) {
            return;
        }
        this.dyingColStart = colStart; this.dyingColEnd = colEnd;
        this.dyingRowStart = rowStart; this.dyingRowEnd = rowEnd;
        const left = this.getDyingLeft(), top = this.getDyingTop();
        const right = this.getDyingRight(), bottom = this.getDyingBottom();
        for (const [key, tile] of this.dying) {
            const sep = key.indexOf(',');
            const c = parseInt(key.slice(0, sep), 10);
            const r = parseInt(key.slice(sep + 1), 10);
            if (c < left || c > right || r < top || r > bottom) {
                this.dying.delete(key);
                this.recycle(tile);
            }
        }
    }

    // 濒死区边界：逐格行走，避免 int32 减法溢出
    getDyingLeft() {
        if (!this.dyingEnabled) return this.dyingColStart;
        const leftBound = this.callback.getLeftBound();
        let left = this.dyingColStart, expand = this.dyingExpand;
        while (expand > 0 && left > leftBound) { left--; expand--; }
        return left;
    }

    getDyingTop() {
        if (!this.dyingEnabled) return this.dyingRowStart;
        const topBound = this.callback.getTopBound();
        let top = this.dyingRowStart, expand = this.dyingExpand;
        while (expand > 0 && top > topBound) { top--; expand--; }
        return top;
    }

    getDyingRight() {
        if (!this.dyingEnabled) return this.dyingColEnd;
        const rightBound = this.callback.getRightBound();
        let right = this.dyingColEnd, expand = this.dyingExpand;
        while (expand > 0 && right < rightBound) { right++; expand--; }
        return right;
    }

    getDyingBottom() {
        if (!this.dyingEnabled) return this.dyingRowEnd;
        const bottomBound = this.callback.getBottomBound();
        let bottom = this.dyingRowEnd, expand = this.dyingExpand;
        while (expand > 0 && bottom < bottomBound) { bottom++; expand--; }
        return bottom;
    }

    getDyingExpand() { return this.dyingExpand; }
    setDyingExpand(expand) {
        if (expand <= 0) throw new Error('濒死区扩展范围必须大于 0: ' + expand);
        this.dyingExpand = expand;
    }
    isDyingEnabled() { return this.dyingEnabled; }
    setDyingEnabled(enabled) {
        this.dyingEnabled = enabled;
        if (!enabled) {
            for (const [key, tile] of this.dying) {
                this.dying.delete(key);
                this.recycle(tile);
            }
        }
    }

    // 同步刷新已有瓦片的尺寸记录
    resizeTile(column, row, width, height) {
        const key = TileManager.key(column, row);
        let tile = this.active.get(key);
        if (!tile) tile = this.dying.get(key);
        if (tile && (width !== tile.width || height !== tile.height)) {
            tile.width = width; tile.height = height;
            if (tile.onSizeChanged) tile.onSizeChanged(width, height);
            this.callback.onTileSizeChanged(tile, column, row, width, height);
        }
    }

    // 更新操作
    update(column, row) {
        if (column >= this.getDyingLeft() && column <= this.getDyingRight() &&
            row >= this.getDyingTop() && row <= this.getDyingBottom()) {
            this.callback.beforeLayout();
            this.rebuildTile(column, row);
            this.callback.updateUI();
        }
    }

    updateRange(left, top, right, bottom) {
        if (left > right || top > bottom) return;
        const il = Math.max(left, this.getDyingLeft()), ir = Math.min(right, this.getDyingRight());
        const it = Math.max(top, this.getDyingTop()), ib = Math.min(bottom, this.getDyingBottom());
        if (il > ir || it > ib) return;
        this.callback.beforeLayout();
        for (let c = il; c <= ir; c++) {
            for (let r = it; r <= ib; r++) this.rebuildTile(c, r);
        }
        this.callback.updateUI();
    }

    updateColumn(column) {
        if (column >= this.getDyingLeft() && column <= this.getDyingRight()) {
            this.callback.beforeLayout();
            for (let r = this.getDyingTop(); r <= this.getDyingBottom(); r++) this.rebuildTile(column, r);
            this.callback.updateUI();
        }
    }

    updateRow(row) {
        if (row >= this.getDyingTop() && row <= this.getDyingBottom()) {
            this.callback.beforeLayout();
            for (let c = this.getDyingLeft(); c <= this.getDyingRight(); c++) this.rebuildTile(c, row);
            this.callback.updateUI();
        }
    }

    rebuildTile(column, row) {
        const key = TileManager.key(column, row);
        const model = this.callback.getLayoutModel();
        if (column >= model.colStart && column <= model.colEnd &&
            row >= model.rowStart && row <= model.rowEnd) {
            // 在活跃区：先回收再重新 in
            const tile = this.active.get(key);
            if (tile) {
                this.active.delete(key);
                if (tile.onOutWindow) tile.onOutWindow();
                this.callback.onTileOut(tile, column, row);
                this.recycle(tile);
            }
            this.in(column, row);
        } else {
            // 在濒死区：只刷新已缓存数据
            const tile = this.dying.get(key);
            if (tile) {
                this.dying.delete(key);
                this.recycle(tile);
                const type = this.callback.getTileType(column, row);
                const newTile = this.obtain(type);
                if (newTile) {
                    newTile.column = column; newTile.row = row;
                    newTile.width = this.callback.getTileWidth(column);
                    newTile.height = this.callback.getTileHeight(row);
                    this.callback.onBindTileHolder(newTile, column, row);
                    if (newTile.onSizeChanged) newTile.onSizeChanged(newTile.width, newTile.height);
                    this.callback.onTileSizeChanged(newTile, column, row, newTile.width, newTile.height);
                    this.dying.set(key, newTile);
                }
            }
        }
    }

    getActiveTile(column, row) { return this.active.get(TileManager.key(column, row)) || null; }
    getActiveTileCount() { return this.active.size; }
    getRecycledTileCount() { return this.recycledCount; }
    getDyingTileCount() { return this.dying.size; }
    getDyingTiles() { return this.dying; }

    clearAll() {
        for (const [key, tile] of this.active) {
            if (tile.onOutWindow) tile.onOutWindow();
            this.callback.onTileOut(tile, tile.column, tile.row);
            this.recycle(tile);
        }
        this.active.clear();
        for (const [key, tile] of this.dying) this.recycle(tile);
        this.dying.clear();
        this.recycled.clear();
        this.recycledCount = 0;
        this.dyingColStart = this.dyingRowStart = 0;
        this.dyingColEnd = this.dyingRowEnd = -1;
    }

    clearActiveAndDying() {
        for (const [key, tile] of this.active) {
            if (tile.onOutWindow) tile.onOutWindow();
            this.callback.onTileOut(tile, tile.column, tile.row);
            this.recycle(tile);
        }
        this.active.clear();
        for (const [key, tile] of this.dying) this.recycle(tile);
        this.dying.clear();
    }
}

// ==================== 尺寸管理器（DimenManager）演示级实现 ====================

class DimenManager {
    constructor(callback) {
        this.callback = callback;
        this.widths = new Map();
        this.heights = new Map();
        this.defaultTileWidth = 0;
        this.defaultTileHeight = 0;
        this.dimenProvider = null;
    }

    // 三级优先级：单独设置 > 尺寸提供者 > 默认值
    getTileWidth(column) {
        if (this.widths.has(column)) return this.widths.get(column);
        if (this.dimenProvider) return this.dimenProvider.getTileWidth(column);
        return this.defaultTileWidth;
    }

    getTileHeight(row) {
        if (this.heights.has(row)) return this.heights.get(row);
        if (this.dimenProvider) return this.dimenProvider.getTileHeight(row);
        return this.defaultTileHeight;
    }

    setTileWidth(column, width, gravity) {
        if (this.callback.isEmpty()) return;
        if (column > this.callback.getRightBound() || column < this.callback.getLeftBound()) {
            throw new RangeError('列索引越界: ' + column);
        }
        let old = this.getTileWidth(column);
        if (width === 0) {
            this.widths.delete(column);
            width = this.getTileWidth(column);
        } else {
            this.widths.set(column, width);
        }
        if (width === old) return;
        // 同步濒死区内该列所有瓦片的尺寸
        const dl = this.callback.getDyingLeft(), dr = this.callback.getDyingRight();
        if (column >= dl && column <= dr) {
            for (let r = this.callback.getDyingTop(); r <= this.callback.getDyingBottom(); r++) {
                this.callback.resizeTile(column, r, width, this.getTileHeight(r));
            }
        }
        this.callback.beforeLayout();
        this.callback.updateWidth(column, old, width, gravity);
        this.callback.updateUI();
    }

    setTileHeight(row, height, gravity) {
        if (this.callback.isEmpty()) return;
        if (row > this.callback.getBottomBound() || row < this.callback.getTopBound()) {
            throw new RangeError('行索引越界: ' + row);
        }
        let old = this.getTileHeight(row);
        if (height === 0) {
            this.heights.delete(row);
            height = this.getTileHeight(row);
        } else {
            this.heights.set(row, height);
        }
        if (height === old) return;
        const dt = this.callback.getDyingTop(), db = this.callback.getDyingBottom();
        if (row >= dt && row <= db) {
            for (let c = this.callback.getDyingLeft(); c <= this.callback.getDyingRight(); c++) {
                this.callback.resizeTile(c, row, this.getTileWidth(c), height);
            }
        }
        this.callback.beforeLayout();
        this.callback.updateHeight(row, old, height, gravity);
        this.callback.updateUI();
    }

    setTileSize(column, width, hGravity, row, height, vGravity) {
        if (this.callback.isEmpty()) return;
        if (column > this.callback.getRightBound() || column < this.callback.getLeftBound()) {
            throw new RangeError('列索引越界: ' + column);
        }
        if (row > this.callback.getBottomBound() || row < this.callback.getTopBound()) {
            throw new RangeError('行索引越界: ' + row);
        }
        const oldWidth = this.getTileWidth(column);
        const oldHeight = this.getTileHeight(row);
        let widthChanged = false, heightChanged = false;
        if (width === 0) { this.widths.delete(column); width = this.getTileWidth(column); }
        else { this.widths.set(column, width); }
        widthChanged = (width !== oldWidth);
        if (height === 0) { this.heights.delete(row); height = this.getTileHeight(row); }
        else { this.heights.set(row, height); }
        heightChanged = (height !== oldHeight);
        if (!widthChanged && !heightChanged) return;
        // 同步其余瓦片尺寸
        if (widthChanged) {
            const dl = this.callback.getDyingLeft(), dr = this.callback.getDyingRight();
            if (column >= dl && column <= dr) {
                for (let r = this.callback.getDyingTop(); r <= this.callback.getDyingBottom(); r++) {
                    if (r !== row) this.callback.resizeTile(column, r, width, this.getTileHeight(r));
                }
            }
        }
        if (heightChanged) {
            const dt = this.callback.getDyingTop(), db = this.callback.getDyingBottom();
            if (row >= dt && row <= db) {
                for (let c = this.callback.getDyingLeft(); c <= this.callback.getDyingRight(); c++) {
                    if (c !== column) this.callback.resizeTile(c, row, this.getTileWidth(c), height);
                }
            }
        }
        this.callback.resizeTile(column, row, width, height);
        this.callback.beforeLayout();
        this.callback.updateSize(column, oldWidth, width, hGravity, row, oldHeight, height, vGravity);
        this.callback.updateUI();
    }

    deleteTileWidth(column, gravity) { this.setTileWidth(column, 0, gravity); }
    deleteTileHeight(row, gravity) { this.setTileHeight(row, 0, gravity); }

    getDefaultTileWidth() { return this.defaultTileWidth; }
    getDefaultTileHeight() { return this.defaultTileHeight; }
    setDefaultTileWidth(width) {
        if (width <= 0) throw new Error('宽度必须大于 0');
        this.defaultTileWidth = width;
    }
    setDefaultTileHeight(height) {
        if (height <= 0) throw new Error('高度必须大于 0');
        this.defaultTileHeight = height;
    }

    getDimenProvider() { return this.dimenProvider; }
    setDimenProvider(provider) { this.dimenProvider = provider; }

    setWidths(map) {
        map.clear();
        for (const [k, v] of this.widths) map.set(k, v);
        this.widths.clear();
        this.widths = map;
    }

    setHeights(map) {
        map.clear();
        for (const [k, v] of this.heights) map.set(k, v);
        this.heights.clear();
        this.heights = map;
    }

    clear() { this.widths.clear(); this.heights.clear(); }
}

// ==================== 核心调度层（TileCoreService） ====================

class TileCoreService {
    constructor(coreInterface) {
        this.core = coreInterface;
        this.layoutEngine = new LayoutEngine(this, this);
        this.tileManager = new TileManager(this);
        this.dimenManager = new DimenManager(this);
        this.bounds = { left: 0, top: 0, right: 0, bottom: 0 };
    }

    // ---- 布局引擎的边界接口 ----
    getLeftBound() { return this.core.getLeftBound(); }
    getTopBound() { return this.core.getTopBound(); }
    getRightBound() { return this.core.getRightBound(); }
    getBottomBound() { return this.core.getBottomBound(); }

    // ---- 布局引擎的视窗接口 ----
    in(column, row) { this.tileManager.in(column, row); }
    out(column, row) { this.tileManager.out(column, row); }
    onWindowCalculated(colStart, rowStart, colEnd, rowEnd) {
        this.tileManager.diffDying(colStart, rowStart, colEnd, rowEnd);
    }
    getColWidth(column) { return this.dimenManager.getTileWidth(column); }
    getRowHeight(row) { return this.dimenManager.getTileHeight(row); }

    // ---- 瓦片管理器的回调 ----
    getTileType(column, row) { return this.core.getTileType(column, row); }
    onCreateTileHolder(type) { return this.core.onCreateTileHolder(type); }
    onBindTileHolder(holder, column, row) { this.core.onBindTileHolder(holder, column, row); }
    onTileIn(holder, column, row) { this.core.onTileIn(holder, column, row); }
    onTileOut(holder, column, row) { this.core.onTileOut(holder, column, row); }
    onTileRecycled(holder, column, row) { this.core.onTileRecycled(holder, column, row); }
    onTileSizeChanged(holder, column, row, width, height) { this.core.onTileSizeChanged(holder, column, row, width, height); }
    getTileWidth(column) { return this.dimenManager.getTileWidth(column); }
    getTileHeight(row) { return this.dimenManager.getTileHeight(row); }
    getLayoutModel() { return this.layoutEngine.getLayoutModel(); }
    beforeLayout() { this.core.beforeLayout(); }
    updateUI() { this.core.updateUI(); }

    // ---- 尺寸管理器的回调 ----
    isEmpty() { return this.layoutEngine.isEmpty(); }
    getDyingLeft() { return this.tileManager.getDyingLeft(); }
    getDyingTop() { return this.tileManager.getDyingTop(); }
    getDyingRight() { return this.tileManager.getDyingRight(); }
    getDyingBottom() { return this.tileManager.getDyingBottom(); }
    resizeTile(column, row, width, height) { this.tileManager.resizeTile(column, row, width, height); }
    updateWidth(column, oldWidth, newWidth, gravity) { this.layoutEngine.updateWidth(column, oldWidth, newWidth, gravity); }
    updateHeight(row, oldHeight, newHeight, gravity) { this.layoutEngine.updateHeight(row, oldHeight, newHeight, gravity); }
    updateSize(column, oldWidth, newWidth, hGravity, row, oldHeight, newHeight, vGravity) {
        this.layoutEngine.updateSize(column, oldWidth, newWidth, hGravity, row, oldHeight, newHeight, vGravity);
    }

    // ---- 公共 API ----
    sync(dx, dy) {
        this.core.beforeLayout();
        this.layoutEngine.sync(dx, dy);
        this.core.updateUI();
    }

    seek(column, row, offsetX, offsetY) {
        if (this.isEmpty()) return;
        this.tileManager.clearActiveAndDying();
        this.core.beforeLayout();
        this.layoutEngine.seek(column, row, offsetX, offsetY);
        this.core.updateUI();
    }

    snap() {
        if (this.isEmpty()) return;
        const model = this.layoutEngine.getLayoutModel();
        const left = this.core.getLeftBound(), top = this.core.getTopBound();
        const right = this.core.getRightBound(), bottom = this.core.getBottomBound();
        if (model.colStart >= left && model.colEnd <= right &&
            model.rowStart >= top && model.rowEnd <= bottom) {
            return;
        }
        const column = Math.max(left, Math.min(model.colStart, right));
        const row = Math.max(top, Math.min(model.rowStart, bottom));
        this.seek(column, row, 0, 0);
    }

    update(column, row) { this.tileManager.update(column, row); }
    updateRange(left, top, right, bottom) { this.tileManager.updateRange(left, top, right, bottom); }
    updateColumn(column) { this.tileManager.updateColumn(column); }
    updateRow(row) { this.tileManager.updateRow(row); }
    updateAll() {
        const m = this.layoutEngine.getLayoutModel();
        this.seek(m.colStart, m.rowStart, m.offsetX, m.offsetY);
    }

    setTileWidth(column, width, gravity) { this.dimenManager.setTileWidth(column, width, gravity); }
    setTileHeight(row, height, gravity) { this.dimenManager.setTileHeight(row, height, gravity); }
    setTileSize(column, width, hGravity, row, height, vGravity) {
        this.dimenManager.setTileSize(column, width, hGravity, row, height, vGravity);
    }
    deleteTileWidth(column, gravity) { this.dimenManager.deleteTileWidth(column, gravity); }
    deleteTileHeight(row, gravity) { this.dimenManager.deleteTileHeight(row, gravity); }

    getTileX(column) {
        const model = this.layoutEngine.getLayoutModel();
        let x = this.bounds.left + model.offsetX;
        let c = model.colStart;
        while (c < column) { x += this.dimenManager.getTileWidth(c); c++; }
        while (c > column) { c--; x -= this.dimenManager.getTileWidth(c); }
        return x;
    }

    getTileY(row) {
        const model = this.layoutEngine.getLayoutModel();
        let y = this.bounds.top + model.offsetY;
        let r = model.rowStart;
        while (r < row) { y += this.dimenManager.getTileHeight(r); r++; }
        while (r > row) { r--; y -= this.dimenManager.getTileHeight(r); }
        return y;
    }

    findColumn(x) {
        const model = this.layoutEngine.getLayoutModel();
        const leftBound = this.core.getLeftBound(), rightBound = this.core.getRightBound();
        let col = model.colStart;
        if (col > rightBound) return leftBound;
        let currX = this.bounds.left + model.offsetX;
        while (col > leftBound && x < currX) { col--; currX -= this.dimenManager.getTileWidth(col); }
        while (col < rightBound && x >= currX + this.dimenManager.getTileWidth(col)) {
            currX += this.dimenManager.getTileWidth(col);
            col++;
        }
        return col;
    }

    findRow(y) {
        const model = this.layoutEngine.getLayoutModel();
        const topBound = this.core.getTopBound(), bottomBound = this.core.getBottomBound();
        let row = model.rowStart;
        if (row > bottomBound) return topBound;
        let currY = this.bounds.top + model.offsetY;
        while (row > topBound && y < currY) { row--; currY -= this.dimenManager.getTileHeight(row); }
        while (row < bottomBound && y >= currY + this.dimenManager.getTileHeight(row)) {
            currY += this.dimenManager.getTileHeight(row);
            row++;
        }
        return row;
    }

    setBounds(left, top, right, bottom) {
        this.bounds.left = left; this.bounds.top = top;
        this.bounds.right = right; this.bounds.bottom = bottom;
        this.layoutEngine.setWindowWidth(right - left);
        this.layoutEngine.setWindowHeight(bottom - top);
    }
    getBounds() { return this.bounds; }

    getDefaultTileWidth() { return this.dimenManager.getDefaultTileWidth(); }
    getDefaultTileHeight() { return this.dimenManager.getDefaultTileHeight(); }
    setDefaultTileWidth(width) { this.dimenManager.setDefaultTileWidth(width); }
    setDefaultTileHeight(height) { this.dimenManager.setDefaultTileHeight(height); }

    getDyingExpand() { return this.tileManager.getDyingExpand(); }
    setDyingExpand(expand) { this.tileManager.setDyingExpand(expand); }
    isDyingEnabled() { return this.tileManager.isDyingEnabled(); }
    setDyingEnabled(enabled) { this.tileManager.setDyingEnabled(enabled); }

    getActiveTile(column, row) { return this.tileManager.getActiveTile(column, row); }
    getActiveTileCount() { return this.tileManager.getActiveTileCount(); }
    getRecycledTileCount() { return this.tileManager.getRecycledTileCount(); }
    getDyingTileCount() { return this.tileManager.getDyingTileCount(); }

    isAtLeftBound() { return !this.isEmpty() && this.layoutEngine.isAtLeftBound(); }
    isAtTopBound() { return !this.isEmpty() && this.layoutEngine.isAtTopBound(); }
    isAtRightBound() { return !this.isEmpty() && this.layoutEngine.isAtRightBound(); }
    isAtBottomBound() { return !this.isEmpty() && this.layoutEngine.isAtBottomBound(); }

    setHorizontalScrollEnabled(enabled) { this.layoutEngine.setHorizontalScrollEnabled(enabled); }
    setVerticalScrollEnabled(enabled) { this.layoutEngine.setVerticalScrollEnabled(enabled); }
    isHorizontalScrollEnabled() { return this.layoutEngine.isHorizontalScrollEnabled(); }
    isVerticalScrollEnabled() { return this.layoutEngine.isVerticalScrollEnabled(); }

    reset() {
        this.tileManager.clearAll();
        this.dimenManager.clear();
        this.layoutEngine.reset();
    }
}

// ==================== DOM 渲染层（Tile2DView） ====================

class Tile2DView {
    constructor(container, options = {}) {
        this.container = container;
        this.adapter = null;
        this.paddingLeft = options.paddingLeft || 0;
        this.paddingTop = options.paddingTop || 0;

        // 内容层：绝对定位 + transform 承载 offset，瓦片按累加坐标绝对定位
        this.content = document.createElement('div');
        this.content.style.position = 'absolute';
        this.content.style.left = this.paddingLeft + 'px';
        this.content.style.top = this.paddingTop + 'px';
        this.content.style.transformOrigin = '0 0';
        container.appendChild(this.content);

        this.core = new TileCoreService({
            beforeLayout: () => {},
            updateUI: () => this.layoutTiles(),
            onTileIn: (holder) => { this.content.appendChild(holder.el); },
            onTileOut: (holder) => { holder.el.remove(); },
            onTileRecycled: (holder) => { holder.el.remove(); },
            onTileSizeChanged: () => {},
            getLeftBound: () => this.adapter ? this.adapter.getLeftBound() : 0,
            getTopBound: () => this.adapter ? this.adapter.getTopBound() : 0,
            getRightBound: () => this.adapter ? this.adapter.getRightBound() : -1,
            getBottomBound: () => this.adapter ? this.adapter.getBottomBound() : -1,
            onCreateTileHolder: (type) => this.adapter.onCreateTileHolder(type),
            onBindTileHolder: (holder, column, row) => this.adapter.onBindTileHolder(holder, column, row),
            getTileType: (column, row) => this.adapter.getTileType(column, row),
        });
        this.core.setDefaultTileWidth(80);
        this.core.setDefaultTileHeight(45);

        // 尺寸变化同步视窗
        this.resizeObserver = new ResizeObserver(() => this.updateBounds());
        this.resizeObserver.observe(container);

        this.attachInput();
    }

    updateBounds() {
        const w = Math.max(0, this.container.clientWidth - this.paddingLeft);
        const h = Math.max(0, this.container.clientHeight - this.paddingTop);
        this.core.setBounds(this.paddingLeft, this.paddingTop,
            this.paddingLeft + w, this.paddingTop + h);
        if (w > 0 && h > 0) this.core.sync(0, 0);
    }

    setAdapter(adapter) {
        if (this.adapter !== adapter) this.core.reset();
        this.adapter = adapter;
        this.updateBounds();
        this.core.seek(adapter.getLeftBound(), adapter.getTopBound(), 0, 0);
    }

    // 布局：内容层偏移用 transform，瓦片按累加坐标定位
    layoutTiles() {
        const model = this.core.getLayoutModel();
        this.content.style.transform = 'translate(' + model.offsetX + 'px,' + model.offsetY + 'px)';
        let x = 0;
        for (let column = model.colStart; column <= model.colEnd; column++) {
            let y = 0;
            for (let row = model.rowStart; row <= model.rowEnd; row++) {
                const tile = this.core.getActiveTile(column, row);
                if (tile) {
                    tile.el.style.left = x + 'px';
                    tile.el.style.top = y + 'px';
                    tile.el.style.width = tile.width + 'px';
                    tile.el.style.height = tile.height + 'px';
                }
                y += this.core.getTileHeight(row);
            }
            x += this.core.getTileWidth(column);
        }
    }

    // 输入：指针拖拽 + 滚轮
    attachInput() {
        let dragging = false, lastX = 0, lastY = 0;
        this.container.addEventListener('pointerdown', (e) => {
            if (e.button !== undefined && e.button !== 0) return;
            dragging = true;
            lastX = e.clientX;
            lastY = e.clientY;
            this.container.setPointerCapture(e.pointerId);
        });
        this.container.addEventListener('pointermove', (e) => {
            if (!dragging) return;
            const dx = e.clientX - lastX;
            const dy = e.clientY - lastY;
            lastX = e.clientX;
            lastY = e.clientY;
            this.core.sync(dx, dy); // 手指向右 → 内容向右 → offset 增加
        });
        const end = () => { dragging = false; };
        this.container.addEventListener('pointerup', end);
        this.container.addEventListener('pointercancel', end);
        this.container.addEventListener('wheel', (e) => {
            e.preventDefault();
            this.core.sync(-e.deltaX, -e.deltaY);
        }, { passive: false });
    }

    offset(dx, dy) { this.core.sync(dx, dy); }
    seek(column, row) { this.core.seek(column, row, 0, 0); }
    snap() { this.core.snap(); }
    getLayoutModel() { return this.core.getLayoutModel(); }
}

// 供 Node 单测加载（浏览器中此分支不执行，类仍是全局的）
if (typeof module !== 'undefined' && module.exports) {
    module.exports = {
        LayoutModel, LayoutEngine, TileManager, DimenManager, TileCoreService, Tile2DView,
    };
}
