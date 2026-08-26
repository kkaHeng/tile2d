/*
 * 演示页面逻辑：柏林噪声适配器 + 伪无限模式 + 去边界看看
 * 对应 docs/H5移植指南.md 第十步
 */

const MIN_INT = -2147483648;
const MAX_INT = 2147483647;

// 固定种子柏林噪声（与 app 瓦片画板 demo 同款：123456789 + 0.03 缩放）
const perlin = new PerlinNoise2D(123456789);

let maxMode = false;   // 伪无限模式
let showText = false;  // 文本/颜色方案

const adapter = {
    getLeftBound: () => maxMode ? MIN_INT : -50,
    getTopBound: () => maxMode ? MIN_INT : -100,
    getRightBound: () => maxMode ? MAX_INT : 50,
    getBottomBound: () => maxMode ? MAX_INT : 100,
    getTileType: (column, row) =>
        perlin.noiseNormalized(column * 0.03, row * 0.03) < 0.3 ? -1 : 0, // 低噪区稀疏
    onCreateTileHolder: (type) => {
        if (type === -1) return null;
        return {
            type,
            el: (() => {
                const el = document.createElement('div');
                el.style.position = 'absolute';
                el.style.boxSizing = 'border-box';
                el.style.border = '1px solid rgba(255,255,255,.35)';
                el.style.display = 'flex';
                el.style.alignItems = 'center';
                el.style.justifyContent = 'center';
                el.style.font = '10px monospace';
                el.style.overflow = 'hidden';
                return el;
            })(),
        };
    },
    onBindTileHolder: (holder, column, row) => {
        const noise = perlin.noiseNormalized(column * 0.03, row * 0.03);
        const rgb = colorFromNoise((noise - 0.3) / 0.7); // 与 Java demo 相同的映射
        holder.el.style.background = 'rgb(' + rgb[0] + ',' + rgb[1] + ',' + rgb[2] + ')';
        holder.el.style.color = luminance(rgb) > 0.40 ? '#111' : '#fff';
        holder.el.textContent = showText ? (noise / 0.03).toFixed(2) : '';
    },
};

const view = new Tile2DView(document.getElementById('view'));
view.setAdapter(adapter);

// ---- 控制按钮 ----
const status = document.getElementById('status');
const tip = document.getElementById('tip');

function refreshStatus() {
    const m = view.getLayoutModel();
    status.textContent =
        (maxMode ? '伪无限' : '有限') + ' | ' +
        '视窗: [' + m.colStart + ',' + m.rowStart + '] - [' + m.colEnd + ',' + m.rowEnd + ']  ' +
        'offset: (' + m.offsetX.toFixed(1) + ', ' + m.offsetY.toFixed(1) + ')  ' +
        '活跃: ' + view.core.getActiveTileCount() +
        '  回收: ' + view.core.getRecycledTileCount() +
        '  濒死: ' + view.core.getDyingTileCount();
}

function bind(id, fn) {
    document.getElementById(id).addEventListener('click', () => { fn(); refreshStatus(); });
}

// 伪无限模式：边界在 int32 全范围与有限范围之间切换，切换后 snap 拉回视窗
let maxButton;
bind('btn-max', () => {
    maxMode = !maxMode;
    view.snap();
    maxButton.textContent = maxMode ? '伪无限模式（开）' : '伪无限模式';
    tip.textContent = maxMode ? '伪无限模式已开启，去边界看看吧' : '已回到有限范围（-100..100）';
});

// 去边界看看：随机跳转到 8 个极端点之一（与 app demo 同款）
bind('btn-end', () => {
    const i = Math.floor(Math.random() * 8);
    const l = adapter.getLeftBound(), t = adapter.getTopBound();
    const r = adapter.getRightBound(), b = adapter.getBottomBound();
    let x, y, name;
    switch (i) {
        case 0: x = 0; y = t; name = '最上边'; break;
        case 1: x = r; y = t; name = '右上角'; break;
        case 2: x = r; y = 0; name = '最右边'; break;
        case 3: x = r; y = b; name = '右下角'; break;
        case 4: x = 0; y = b; name = '最下边'; break;
        case 5: x = l; y = b; name = '左下角'; break;
        case 6: x = l; y = 0; name = '最左边'; break;
        default: x = l; y = t; name = '左上角';
    }
    view.seek(x, y);
    tip.textContent = '到达' + name + ' (' + x + ',' + y + ')';
});

bind('btn-tl', () => view.seek(0, 0));
bind('btn-br', () => view.seek(adapter.getRightBound(), adapter.getBottomBound()));
bind('btn-rand', () => view.seek(
    Math.floor(Math.random() * 2000) - 1000,
    Math.floor(Math.random() * 2000) - 1000));

// 文本/颜色方案切换
bind('btn-text', () => {
    showText = !showText;
    view.core.updateAll();
    tip.textContent = showText ? '文本方案' : '颜色方案';
});

bind('btn-update', () => view.core.update(10, 10));

let wide = false;
bind('btn-dimen', () => {
    wide = !wide;
    view.core.setTileWidth(0, wide ? 160 : 80, LayoutEngine.DIMEN_GRAVITY_START);
    tip.textContent = wide ? '第 0 列加宽到 160px' : '第 0 列还原 80px';
});

maxButton = document.getElementById('btn-max');
setInterval(refreshStatus, 200);
refreshStatus();
