/*
 * 柏林噪声瓦片 Demo（Canvas 自绘）
 *
 * 视窗内的瓦片全部画在一张 <canvas> 上：一次清屏 + 逐格画背景色 / 边框 / 文本，
 * 没有 DOM 瓦片、没有合成层，帧率稳定。
 * 数据源与 app 模块柏林噪声 demo 逐位一致：种子 123456789、噪声缩放 0.03、
 * 稀疏判据 < 0.3、24 色渐变映射、文本方案（背景色 + 灰边 + 噪声数值），无纯色方案。
 * 引擎用 h5-demo/tile2d.js 的 LayoutEngine（与 Java 版逐行对齐），
 * 容器层 TileCanvasCore 对照 TileView：scaleFactor 缩放、findColumn/findRow 命中测试。
 */
(function () {
    'use strict';

    const MIN_INT = -2147483648;
    const MAX_INT = 2147483647;
    const PADDING = 20;          // 容器内边距（对应 TileView demo 的 setPadding）

    // ==================== 数据源（与 app 同款） ====================

    const perlin = new PerlinNoise2D(123456789);
    const colorGen = new ColorGenerator();
    const NOISE_SCALE = 0.03;    // column * 0.03
    const SPARSE_THRESHOLD = 0.3;

    function noiseAt(column, row) {
        return perlin.noiseNormalized(column * NOISE_SCALE, row * NOISE_SCALE);
    }

    // ==================== 状态 ====================

    let maxMode = false;
    let debugMode = false;
    let scaleFactor = 1;         // 缩放（几何与字号都乘它）
    let showHud = false;

    // ==================== 适配器（对应 TileView.Adapter） ====================

    const adapter = {
        getLeftBound: () => maxMode ? MIN_INT : -50,
        getTopBound: () => maxMode ? MIN_INT : -100,
        getRightBound: () => maxMode ? MAX_INT : 50,
        getBottomBound: () => maxMode ? MAX_INT : 100,

        // 低噪区稀疏：不产生瓦片（与 app 一致）
        getTileType: (column, row) => noiseAt(column, row) < SPARSE_THRESHOLD ? -1 : 0,

        // 自绘所需的数据：背景色 + 文字颜色 + 文本（与 app 端 ColorTileHolder.bind 一致）
        getPaint: (column, row) => {
            const noise = noiseAt(column, row);
            const color = colorGen.getColor((noise - SPARSE_THRESHOLD) / (1 - SPARSE_THRESHOLD));
            return {
                color: ColorGenerator.css(color),
                textColor: ColorGenerator.luminance(color) > 0.40 ? '#111111' : '#ffffff',
                text: (noise / NOISE_SCALE).toFixed(2),
            };
        },
    };

    // ==================== 容器层 ====================

    const view = document.getElementById('view');   // <canvas>
    const core = new TileCanvasCore(adapter, view);
    core.setPadding(PADDING, PADDING);
    core.setDefaultSize(80, 45);      // 对应 app 的 dp2px(80) / dp2px(45)
    core.seek(0, 0, 0, 0);

    // 容器矩形缓存：#view 是 fixed 铺满，只在尺寸变化时才需重读（避免每个触摸事件都强制同步布局）
    const containerRect = { left: 0, top: 0, width: 0, height: 0 };

    function measureContainer() {
        const r = view.getBoundingClientRect();
        containerRect.left = r.left; containerRect.top = r.top;
        containerRect.width = r.width; containerRect.height = r.height;
        core.setContainerSize(Math.max(1, Math.round(r.width)), Math.max(1, Math.round(r.height)));
    }

    // ==================== 缩放 ====================

    const SCALE_MIN = 0.5, SCALE_MAX = 3;

    // 以 (fx, fy)（css 像素，相对容器左上角）为焦点缩放；不给焦点就按容器中心
    function zoomTo(nextScale, fx, fy) {
        nextScale = Math.max(SCALE_MIN, Math.min(SCALE_MAX, nextScale));
        if (Math.abs(nextScale - scaleFactor) < 0.0001) return;
        const focusX = (fx === undefined ? containerRect.width / 2 : fx) - PADDING;
        const focusY = (fy === undefined ? containerRect.height / 2 : fy) - PADDING;
        const oldScale = scaleFactor;
        // 焦点下的内容点保持不动：dx = 焦点到内边距的距离 * (1/新缩放 - 1/旧缩放)
        const dx = focusX * (1 / nextScale - 1 / oldScale);
        const dy = focusY * (1 / nextScale - 1 / oldScale);
        scaleFactor = nextScale;
        core.zoom(nextScale, dx, dy);
    }

    // ==================== 惯性滚动 ====================

    let inertiaVX = 0, inertiaVY = 0;      // 内容像素 / 毫秒
    const INERTIA_MIN = 0.02;
    const INERTIA_FRICTION = 0.94;
    let rafId = 0, lastFrameTime = 0;

    function startInertia(vx, vy) {
        inertiaVX = vx;
        inertiaVY = vy;
        startAnimation();
    }

    function stopInertia() {
        inertiaVX = inertiaVY = 0;
    }

    // 统一的帧循环：只负责惯性
    function tick(now) {
        const dt = lastFrameTime ? Math.min(64, now - lastFrameTime) : 16;
        lastFrameTime = now;

        if (inertiaVX || inertiaVY) {
            core.sync(inertiaVX * dt, inertiaVY * dt);
            const decay = Math.pow(INERTIA_FRICTION, dt / 16.7);
            inertiaVX *= decay;
            inertiaVY *= decay;
            if (Math.abs(inertiaVX) < INERTIA_MIN) inertiaVX = 0;
            if (Math.abs(inertiaVY) < INERTIA_MIN) inertiaVY = 0;
        }

        if (sizeAnim) stepSizeAnimation(now);

        if (inertiaVX || inertiaVY || sizeAnim) {
            rafId = requestAnimationFrame(tick);
        } else {
            rafId = 0;
            lastFrameTime = 0;
        }
    }

    function startAnimation() {
        if (!rafId) {
            lastFrameTime = 0;
            rafId = requestAnimationFrame(tick);
        }
    }

    // ==================== 手势 ====================

    const pointers = new Map();
    let dragging = false;
    let pinchStartDist = 0, pinchStartScale = 1, pinchCenterX = 0, pinchCenterY = 0;
    let dragMoved = 0;
    let velocitySamples = [];
    let lastTapTime = 0, lastTapX = 0, lastTapY = 0;

    function pointerPos(e) {
        return { x: e.clientX - containerRect.left, y: e.clientY - containerRect.top };
    }

    function onPointerDown(e) {
        view.setPointerCapture(e.pointerId);
        const p = pointerPos(e);
        pointers.set(e.pointerId, p);

        if (pointers.size === 1) {
            stopInertia();
            dragging = true;
            dragMoved = 0;
            velocitySamples = [{ t: performance.now(), x: p.x, y: p.y }];
            view.classList.add('dragging');
        } else if (pointers.size === 2) {
            const pts = [...pointers.values()];
            pinchStartDist = Math.hypot(pts[0].x - pts[1].x, pts[0].y - pts[1].y) || 1;
            pinchStartScale = scaleFactor;
            pinchCenterX = (pts[0].x + pts[1].x) / 2;
            pinchCenterY = (pts[0].y + pts[1].y) / 2;
            dragging = false;
            view.classList.remove('dragging');
        }
    }

    function onPointerMove(e) {
        if (!pointers.has(e.pointerId)) return;
        const p = pointerPos(e);
        const prev = pointers.get(e.pointerId);
        pointers.set(e.pointerId, p);

        if (pointers.size >= 2) {
            const pts = [...pointers.values()];
            const dist = Math.hypot(pts[0].x - pts[1].x, pts[0].y - pts[1].y) || 1;
            zoomTo(pinchStartScale * (dist / pinchStartDist), pinchCenterX, pinchCenterY);
            return;
        }

        if (!dragging) return;
        // 手指 / 鼠标位移 → 内容位移（除以缩放，保证跟手）
        const dx = (p.x - prev.x) / scaleFactor;
        const dy = (p.y - prev.y) / scaleFactor;
        dragMoved += Math.abs(p.x - prev.x) + Math.abs(p.y - prev.y);
        core.sync(dx, dy);

        const now = performance.now();
        velocitySamples.push({ t: now, x: p.x, y: p.y });
        while (velocitySamples.length > 2 && now - velocitySamples[0].t > 100) velocitySamples.shift();
    }

    function onPointerUp(e) {
        const p = pointers.get(e.pointerId);
        pointers.delete(e.pointerId);

        if (pointers.size === 0 && dragging) {
            dragging = false;
            view.classList.remove('dragging');

            if (velocitySamples.length >= 2) {
                const first = velocitySamples[0], last = velocitySamples[velocitySamples.length - 1];
                const dt = last.t - first.t;
                if (dt > 0) {
                    const vx = (last.x - first.x) / dt / scaleFactor;
                    const vy = (last.y - first.y) / dt / scaleFactor;
                    if (Math.abs(vx) > 0.05 || Math.abs(vy) > 0.05) startInertia(vx, vy);
                }
            }
            velocitySamples = [];

            // 没怎么移动才算「点击」：单击弹坐标，双击放大 / 还原
            if (dragMoved <= 8 && p) {
                const now = performance.now();
                if (now - lastTapTime < 300 && Math.hypot(p.x - lastTapX, p.y - lastTapY) < 40) {
                    const next = scaleFactor > 1.5 ? 1 : 2;
                    zoomTo(next, p.x, p.y);
                    toast('缩放 ' + next.toFixed(1) + 'x');
                    lastTapTime = 0;
                } else {
                    lastTapTime = now;
                    lastTapX = p.x; lastTapY = p.y;
                    toast('点击了 ' + core.findColumn(p.x) + ',' + core.findRow(p.y));
                }
            }
        }
        if (pointers.size < 2) pinchStartDist = 0;
    }

    view.addEventListener('pointerdown', onPointerDown);
    view.addEventListener('pointermove', onPointerMove);
    view.addEventListener('pointerup', onPointerUp);
    view.addEventListener('pointercancel', onPointerUp);
    view.addEventListener('contextmenu', (e) => e.preventDefault());

    // PC 端以鼠标拖动为主；滚轮仅在按住 Ctrl/⌘ 时用于缩放
    view.addEventListener('wheel', (e) => {
        if (!(e.ctrlKey || e.metaKey)) return;
        e.preventDefault();
        zoomTo(scaleFactor * Math.exp(-e.deltaY * 0.002), e.clientX, e.clientY);
    }, { passive: false });

    window.addEventListener('resize', measureContainer);
    window.addEventListener('orientationchange', () => setTimeout(measureContainer, 120));

    // ==================== 尺寸动画（随机调整宽度 / 高度） ====================

    let sizeAnim = null;
    const SIZE_ANIM_DURATION = 2000;

    // 与 app 的 OvershootInterpolator 同味：末端轻微过冲
    function overshoot(t) {
        const s = 1.70158;
        const u = t - 1;
        return u * u * ((s + 1) * u + s) + 1;
    }

    function animateSize(kind) {
        openSheet(false);   // 关掉挡住屏幕中心的下拉菜单，好看清动效发生在中心那一列/行
        const now = performance.now();
        // 取「引擎视窗」的中心（与画出来的瓦片窗口同源，避免用了过期的屏幕矩形而对不上）
        const centerX = core.paddingLeft + (core.viewportWidth * core.scaleFactor) / 2;
        const centerY = core.paddingTop + (core.viewportHeight * core.scaleFactor) / 2;
        if (kind === 'width') {
            const column = core.findColumn(centerX);
            const from = core.getTileWidth(column);
            const to = (Math.floor(Math.random() * 19) + 4) * 10;   // 40 ~ 220，与 app 同范围
            sizeAnim = { kind: 'width', column, from, to, start: now };
            toast('调整第 ' + column + ' 列宽度到 ' + to + 'px');
        } else {
            const row = core.findRow(centerY);
            const from = core.getTileHeight(row);
            const to = (Math.floor(Math.random() * 7) + 3) * 10;    // 30 ~ 90，与 app 同范围
            sizeAnim = { kind: 'height', row, from, to, start: now };
            toast('调整第 ' + row + ' 行高度到 ' + to + 'px');
        }
        startAnimation();
    }

    function stepSizeAnimation(now) {
        const t = Math.min(1, (now - sizeAnim.start) / SIZE_ANIM_DURATION);
        const value = sizeAnim.from + (sizeAnim.to - sizeAnim.from) * overshoot(t);
        if (sizeAnim.kind === 'width') {
            core.setTileWidth(sizeAnim.column, value, LayoutEngine.DIMEN_GRAVITY_CENTER);
        } else {
            core.setTileHeight(sizeAnim.row, value, LayoutEngine.DIMEN_GRAVITY_CENTER);
        }
        if (t >= 1) sizeAnim = null;
    }

    // ==================== 菜单 ====================

    const sheet = document.getElementById('sheet');
    const mask = document.getElementById('mask');
    const refs = {
        debug: document.getElementById('m-debug'),
        max: document.getElementById('m-max'),
    };

    function openSheet(open) {
        sheet.classList.toggle('on', open);
        mask.classList.toggle('on', open);
    }

    document.getElementById('menuBtn').addEventListener('click', () => openSheet(true));
    document.getElementById('closeBtn').addEventListener('click', () => openSheet(false));
    mask.addEventListener('click', () => openSheet(false));

    refs.debug.addEventListener('click', () => {
        debugMode = !debugMode;
        showHud = debugMode;
        refs.debug.classList.toggle('on', debugMode);
        document.getElementById('hud').classList.toggle('on', showHud);
        document.getElementById('debugBox').classList.toggle('on', showHud);
        toast('Debug 模式: ' + (debugMode ? '开启' : '关闭'));
        refreshStatus();   // 立即摆好虚线框位置
    });

    refs.max.addEventListener('click', () => {
        maxMode = !maxMode;
        refs.max.classList.toggle('on', maxMode);
        core.snap();
        toast(maxMode ? '伪无限模式已开启，去边界看看吧' : '已回到有限范围（-50..50 / -100..100）');
    });

    document.getElementById('m-rand-w').addEventListener('click', () => animateSize('width'));
    document.getElementById('m-rand-h').addEventListener('click', () => animateSize('height'));

    const END_TARGETS = {
        tl: (l, t) => [l, t, '左上角'],
        t: (l, t) => [0, t, '最上边'],
        tr: (l, t, r) => [r, t, '右上角'],
        l: (l, t) => [l, 0, '最左边'],
        origin: () => [0, 0, '原点'],
        r: (l, t, r) => [r, 0, '最右边'],
        bl: (l, t, r, b) => [l, b, '左下角'],
        b: (l, t, r, b) => [0, b, '最下边'],
        br: (l, t, r, b) => [r, b, '右下角'],
    };

    document.querySelectorAll('[data-end]').forEach((el) => {
        el.addEventListener('click', () => {
            const l = adapter.getLeftBound(), t = adapter.getTopBound();
            const r = adapter.getRightBound(), b = adapter.getBottomBound();
            const [column, row, name] = END_TARGETS[el.dataset.end](l, t, r, b);
            core.seek(column, row, 0, 0);
            toast('到达' + name + '：' + column + ',' + row);
        });
    });

    document.getElementById('m-zoom-in').addEventListener('click', () => { zoomTo(scaleFactor * 1.4); toast('缩放 ' + scaleFactor.toFixed(1) + 'x'); });
    document.getElementById('m-zoom-out').addEventListener('click', () => { zoomTo(scaleFactor / 1.4); toast('缩放 ' + scaleFactor.toFixed(1) + 'x'); });
    document.getElementById('m-zoom-reset').addEventListener('click', () => { zoomTo(1); toast('缩放已重置'); });

    // 菜单项点完自动收起（像原生菜单一样），避免面板挡住屏幕中心、看不到效果
    sheet.querySelectorAll('.item').forEach((el) => el.addEventListener('click', () => openSheet(false)));

    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') openSheet(false);
    });

    // ==================== 状态提示 ====================

    const toastEl = document.getElementById('toast');
    let toastTimer = 0;

    function toast(text) {
        toastEl.textContent = text;
        toastEl.classList.add('on');
        clearTimeout(toastTimer);
        toastTimer = setTimeout(() => toastEl.classList.remove('on'), 1400);
    }

    const statusEl = document.getElementById('status');
    const hudEl = document.getElementById('hud');
    const debugBoxEl = document.getElementById('debugBox');

    function refreshStatus() {
        const m = core.getLayoutModel();
        const mode = maxMode ? '伪无限' : '有限';
        const bound = core.isAtLeftBound() ? '左上' : core.isAtRightBound() ? '右下' : '';
        statusEl.textContent = mode + ' · ' + scaleFactor.toFixed(1) + 'x'
            + ' · [' + m.colStart + ',' + m.rowStart + ']-[' + m.colEnd + ',' + m.rowEnd + ']'
            + (bound ? ' · ' + bound : '');
        if (showHud) {
            hudEl.textContent =
                '窗口   ' + core.viewportWidth + ' × ' + core.viewportHeight + '（内容像素）\n' +
                '视窗   [' + m.colStart + ',' + m.rowStart + '] - [' + m.colEnd + ',' + m.rowEnd + ']\n' +
                'offset ' + m.offsetX.toFixed(2) + ' , ' + m.offsetY.toFixed(2) + '\n' +
                '内容宽 ' + m.contentWidth + ' 高 ' + m.contentHeight + '\n' +
                '缩放   ' + scaleFactor.toFixed(2) + 'x\n' +
                '画布   ' + view.width + ' × ' + view.height + '（设备像素）\n' +
                '缓存   ' + core.getPaintCount() + ' 格';

            // 虚线框 = 引擎视窗在屏幕上的矩形（= 容器按内边距内缩；对应 Java 的 getBounds）
            debugBoxEl.style.left = core.paddingLeft + 'px';
            debugBoxEl.style.top = core.paddingTop + 'px';
            debugBoxEl.style.width = Math.max(0, core.containerWidth - core.paddingLeft * 2) + 'px';
            debugBoxEl.style.height = Math.max(0, core.containerHeight - core.paddingTop * 2) + 'px';
        }
    }
    setInterval(refreshStatus, 200);

    // ==================== 启动 ====================

    measureContainer();
    openSheet(false);
    refreshStatus();

    // 便于在控制台里观察
    window.tile2dDemo = {
        core, adapter, perlin, colorGen, zoomTo, toast, view,
        get scaleFactor() { return scaleFactor; },
    };

})();