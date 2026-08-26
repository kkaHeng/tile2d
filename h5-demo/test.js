/*
 * 布局引擎 node 单测（引擎不依赖 DOM，可脱离浏览器验证）
 * 运行：node h5-demo/test.js
 */
const { LayoutEngine, TileCoreService } = require('./tile2d.js');

let failed = 0;
function assert(cond, msg) {
    if (cond) { console.log('PASS:', msg); }
    else { failed++; console.log('FAIL:', msg); }
}

// 固定尺寸：列宽 80，行高 45；视窗 800x450
const boundary = { getLeftBound: () => 0, getTopBound: () => 0, getRightBound: () => 99, getBottomBound: () => 99 };
const windowI = {
    in: () => {}, out: () => {}, onWindowCalculated: () => {},
    getColWidth: () => 80, getRowHeight: () => 45,
};
const engine = new LayoutEngine(boundary, windowI);
engine.setWindowWidth(800);
engine.setWindowHeight(450);

// 1. seek 定义原点
engine.seek(0, 0, 0, 0);
let m = engine.getLayoutModel();
assert(m.colStart === 0 && m.rowStart === 0, 'seek(0,0) 锚点为原点');
assert(engine.isAtLeftBound() && engine.isAtTopBound(), '初始在左上边界');

// 2. sync 滚动：左移 400px（5 列），锚点变为 4，offset 余 -80
engine.sync(-400, -225);
m = engine.getLayoutModel();
assert(m.colStart === 4 && m.rowStart === 4, '左移 400px 锚点变 (4,4)');
assert(m.offsetX === -80 && m.offsetY === -45, 'offset 余 (-80,-45)');
assert(m.colEnd === 14 && m.rowEnd === 14, '右锚点自动扩展补满视窗');

// 3. 内容位移验证：getTileX(4) 从 320 变为 -80
const core = new TileCoreService({
    beforeLayout: () => {}, updateUI: () => {},
    onTileIn: () => {}, onTileOut: () => {}, onTileRecycled: () => {}, onTileSizeChanged: () => {},
    getLeftBound: () => 0, getTopBound: () => 0, getRightBound: () => 99, getBottomBound: () => 99,
    onCreateTileHolder: () => ({}), onBindTileHolder: () => {}, getTileType: () => 0,
});
core.setBounds(0, 0, 800, 450);
core.setDefaultTileWidth(80);
core.setDefaultTileHeight(45);
core.seek(0, 0, 0, 0);
assert(core.getTileX(4) === 320, '滚动前 getTileX(4)=320');
core.sync(-400, -225);
assert(core.getTileX(4) === -80 && core.getTileY(4) === -45, '滚动后内容位移恰好 (-400,-225)');

// 4. 右下角：内容填不满视窗，锚点自动回填
core.seek(99, 99, 0, 0);
m = core.getLayoutModel();
assert(m.colStart === 90 && m.rowStart === 90, 'seek(99,99) 锚点回填到 (90,90)');
assert(core.isAtRightBound() && core.isAtBottomBound(), '位于右下边界');

// 5. 尺寸变更补偿：加宽第 0 列，右侧少一列，offsetX 不变
core.seek(0, 0, 0, 0);
core.setTileWidth(0, 160, LayoutEngine.DIMEN_GRAVITY_START);
m = core.getLayoutModel();
assert(m.colEnd === 9, '加宽后 colEnd 收缩到 9');
assert(core.getTileX(1) === 160, 'getTileX(1)=160');
assert(m.offsetX === 0, 'START gravity 下 offsetX 不变');

// 6. 边界钳位：向左上拖过界被拉回
core.seek(0, 0, 0, 0);
core.sync(500, 500);
assert(core.isAtLeftBound() && core.isAtTopBound(), '拖过界钳位回左上角');


// ---------- 伪无限模式开关回归测试 ----------
// 复现场景：开启伪无限 → 跳到 int32 边界 → 关闭伪无限 → snap 拉回 → 能继续滚动
let bounds = { l: -2147483648, t: -2147483648, r: 2147483647, b: 2147483647 };
const maxCore = new TileCoreService({
    beforeLayout: () => {}, updateUI: () => {},
    onTileIn: () => {}, onTileOut: () => {}, onTileRecycled: () => {}, onTileSizeChanged: () => {},
    getLeftBound: () => bounds.l, getTopBound: () => bounds.t,
    getRightBound: () => bounds.r, getBottomBound: () => bounds.b,
    onCreateTileHolder: () => ({}), onBindTileHolder: () => {}, getTileType: () => 0,
});
maxCore.setBounds(0, 0, 800, 450);
maxCore.setDefaultTileWidth(80);
maxCore.setDefaultTileHeight(45);

// 伪无限：跳到 int32 最小值附近
maxCore.seek(-2147483648, -2147483648, 0, 0);
let mm = maxCore.getLayoutModel();
assert(mm.colStart === -2147483648 && mm.rowStart === -2147483648, '伪无限下可跳到 int32 边界');

// 关闭伪无限：边界缩回有限范围，视窗已越界，sync 会短路
bounds = { l: -50, t: -100, r: 50, b: 100 };
// TileCoreService.sync 不返回引擎结果，直接测引擎层
assert(maxCore.layoutEngine.sync(-80, -45) === false, '关闭伪无限后视窗越界，sync 短路');

// snap 拉回：锚点被钳制到有限范围左上角
maxCore.snap();
mm = maxCore.getLayoutModel();
assert(mm.colStart === -50 && mm.rowStart === -100, 'snap 后视窗回到 (-50,-100)，实际 (' + mm.colStart + ',' + mm.rowStart + ')');

// 拉回后必须能继续滚动（不再短路）
maxCore.sync(-400, -225);
mm = maxCore.getLayoutModel();
// 位移 400px = 5 格（4 个整格 + 余量 offset -80）；225px = 5 行（4 行 + 余量 offset -45）
assert(mm.colStart === -46 && mm.offsetX === -80 && mm.rowStart === -96 && mm.offsetY === -45,
    'snap 后滚动恢复正常，锚点 (-46,-96) offset (-80,-45)，实际 (' + mm.colStart + ',' + mm.rowStart + ') (' + mm.offsetX + ',' + mm.offsetY + ')');
assert(maxCore.getActiveTileCount() > 0, '滚动后瓦片正常加载');


// ---------- 噪声纹理测试（noise.js） ----------
const { PerlinNoise2D, colorFromNoise, luminance } = require('./noise.js');

// 同一种子两次构造结果完全一致
const a = new PerlinNoise2D(123456789);
const b = new PerlinNoise2D(123456789);
let same = true;
for (let i = 0; i < 50; i++) {
    const x = i * 1.7 - 25, y = i * 0.3 + 3;
    if (a.noiseNormalized(x, y) !== b.noiseNormalized(x, y)) { same = false; break; }
}
assert(same, '同种子噪声可复现');

let inRange = true;
for (let i = 0; i < 200; i++) {
    const n = a.noiseNormalized(i * 0.03, i * 0.07);
    if (n < 0 || n > 1) { inRange = false; break; }
}
assert(inRange, 'noiseNormalized 输出在 [0,1]');

// int32 边界处采样正常（伪无限模式的关键）
const minN = a.noiseNormalized(-2147483648 * 0.03, -2147483648 * 0.03);
const maxN = a.noiseNormalized(2147483647 * 0.03, 2147483647 * 0.03);
assert(!isNaN(minN) && !isNaN(maxN) && minN >= 0 && maxN <= 1, 'int32 边界处噪声正常');

// 颜色映射：起点亮蓝、终点纯白
const c1 = colorFromNoise(0);
const c2 = colorFromNoise(1);
assert(c1[0] === 59 && c1[2] === 246, '噪声 0 → 渐变起点亮蓝');
assert(c2[0] === 255 && c2[1] === 255 && c2[2] === 255, '噪声 1 → 渐变终点纯白');
assert(luminance([255, 255, 255]) > 0.9 && luminance([0, 0, 0]) < 0.01, 'luminance 计算正常');

// 稀疏比例：柏林噪声分布集中在 0.5 附近，<0.3 的比例约为 5%~15%（与 Java demo 同阈值同特征）
let sparse = 0, total = 0;
for (let x = -500; x <= 500; x += 3) {
    for (let y = -500; y <= 500; y += 3) {
        total++;
        if (a.noiseNormalized(x * 0.03, y * 0.03) < 0.3) sparse++;
    }
}
assert(sparse / total > 0.04 && sparse / total < 0.15,
    '稀疏比例合理 (' + (sparse / total).toFixed(3) + ')');

