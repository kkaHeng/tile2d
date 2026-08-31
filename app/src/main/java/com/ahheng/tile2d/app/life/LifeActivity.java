package com.ahheng.tile2d.app.life;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Picture;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ahheng.tile2d.app.BaseActivity;
import com.ahheng.tile2d.widget.canvas.TileView;

import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// 生命游戏 Demo（Conway's Game of Life，无限平面）
// 数据：LifeBoard 稀疏活细胞表（int32 全域，死细胞不占存储），规则 B3/S23
// 渲染：TileView 逐格瓦片 + Picture 矢量录制（5 种外观：死亡 + 4 档年龄），缩放不重录
// 线程：单后台线程算下一代（纯函数 + 快照 copy-on-write），主线程只做局部重绘
// 增量：每代只 update 状态/年龄档位发生变化的格子，视窗外的变化不产生任何绘制开销
public class LifeActivity extends BaseActivity {

    private static final int MENU_ID_PLAY = nextId();
    private static final int MENU_ID_STEP = nextId();
    private static final int MENU_ID_CLEAR = nextId();
    private static final int MENU_ID_RANDOM = nextId();
    private static final int MENU_ID_PATTERN = nextId();
    private static final int MENU_ID_SPEED = nextId();

    // 图案子菜单项 ID（按 LifePatterns.ALL 顺序分配）
    private static final int[] MENU_ID_PATTERNS = new int[LifePatterns.ALL.length];

    static {
        for (int i = 0; i < MENU_ID_PATTERNS.length; i++) {
            MENU_ID_PATTERNS[i] = nextId();
        }
    }

    // 速度档位（每代间隔毫秒）
    private static final long[] SPEED_INTERVALS = {500L, 250L, 120L, 60L, 16L};
    private static final String[] SPEED_NAMES = {"0.5x 慢速", "1x 常速", "2x 快速", "4x 极速", "满帧"};
    private static final int[] MENU_ID_SPEEDS = new int[SPEED_INTERVALS.length];

    static {
        for (int i = 0; i < MENU_ID_SPEEDS.length; i++) {
            MENU_ID_SPEEDS[i] = nextId();
        }
    }

    // 瓦片外观索引：0 死亡，1..4 年龄档位（新生 / 年轻 / 成熟 / 长寿）
    private static final int TILE_TYPES = LifeBoard.AGE_LEVELS + 1;

    // 视觉常量：深色底 + 年龄渐变（新生亮青 → 长寿深蓝紫），冷色系突出"演化"观感
    private static final int COLOR_DEAD = Color.parseColor("#12161C");   // 近黑底
    private static final int COLOR_GRID = Color.parseColor("#232A34");   // 格线
    private static final int[] COLOR_AGES = {
            Color.parseColor("#5EEAD4"), // 新生：亮青
            Color.parseColor("#38BDF8"), // 年轻：天蓝
            Color.parseColor("#6366F1"), // 成熟：靛蓝
            Color.parseColor("#8B5CF6"), // 长寿：紫
    };

    private TileView tileView;
    private LifeBoard board;
    private TextView statusBar;

    // 演化调度：单后台线程计算 + 主线程应用（参考五子棋 AI 调度的代次令牌模式）
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService computeExecutor = Executors.newSingleThreadExecutor();
    private volatile boolean playing;      // 是否连续演化
    private volatile boolean computing;    // 后台是否正在算，防重复调度
    private volatile int computeToken;     // 计算代次令牌：编辑/清空/暂停时自增，旧结果一律丢弃
    private int speedIndex = 1;            // 当前速度档位

    // Picture 录制图库（5 种外观），仅瓦片模型尺寸变化时重录，缩放走矢量回放
    private Picture[] tilePictures;
    private int recordW = -1;
    private int recordH = -1;

    private final Random random = new Random();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        board = new LifeBoard();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        setContentView(root, new ViewGroup.LayoutParams(-1, -1));

        tileView = new TileView(this);
        tileView.setDebugMode(isDebugMode());
        tileView.setHorizontalScrollEnabled(true);
        tileView.setVerticalScrollEnabled(true);
        tileView.setDefaultTileWidth(dp2px(16));
        tileView.setDefaultTileHeight(dp2px(16));
        tileView.setZoomEnabled(true);
        tileView.setMinScaleFactor(0.4f);
        tileView.setMaxScaleFactor(3f);
        tileView.setAdapter(new LifeAdapter());
        root.addView(tileView, new LinearLayout.LayoutParams(-1, 0, 1f));

        statusBar = new TextView(this);
        statusBar.setPadding(dp2px(12), dp2px(8), dp2px(12), dp2px(8));
        statusBar.setTextColor(Color.parseColor("#B9C4D4"));
        statusBar.setBackgroundColor(Color.parseColor("#0C1015"));
        root.addView(statusBar, new LinearLayout.LayoutParams(-1, -2));

        // 初始图案：滑翔机枪（人类发现的第一个无限增长图案，最能展示无限平面）
        board.placePattern(LifePatterns.GLIDER_GUN.offsets, -18, -4);
        updateStatus();

        tileView.post(() -> {
            if (!isFinishing()) {
                tileView.seek(0, 0, 0, 0);
            }
        });
    }

    // ========== 瓦片外观录制 ==========

    // 按模型尺寸录制 5 种外观：仅首次或瓦片尺寸变化时重录，缩放通过 canvas.scale 矢量回放
    private void ensurePictures(int w, int h) {
        if (w <= 0 || h <= 0) return;
        if (tilePictures != null && recordW == w && recordH == h) return;
        recordPictures(w, h);
    }

    private void recordPictures(int w, int h) {
        if (w <= 0 || h <= 0) return;

        tilePictures = new Picture[TILE_TYPES];
        recordW = w;
        recordH = h;

        Paint deadPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        deadPaint.setColor(COLOR_DEAD);
        Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(COLOR_GRID);
        gridPaint.setStrokeWidth(Math.max(1f, dpTopx(0.5f)));

        // 死细胞：底色 + 右下两条格线（与邻格衔接成连续网格）
        tilePictures[0] = record(w, h, canvas -> {
            canvas.drawRect(0, 0, w, h, deadPaint);
            canvas.drawLine(0, h, w, h, gridPaint);
            canvas.drawLine(w, 0, w, h, gridPaint);
        });

        // 活细胞：底色 + 格线 + 圆角方块（年龄越大越暗、越饱满）
        for (int level = 1; level < TILE_TYPES; level++) {
            final int color = COLOR_AGES[level - 1];
            // 新生细胞略小并带外发光感，长寿细胞几乎填满格子
            final float inset = Math.min(w, h) * (level == 1 ? 0.22f : level == 2 ? 0.15f : 0.10f);
            final float radius = Math.min(w, h) * (level >= 3 ? 0.14f : 0.28f);
            tilePictures[level] = record(w, h, canvas -> {
                canvas.drawRect(0, 0, w, h, deadPaint);
                canvas.drawLine(0, h, w, h, gridPaint);
                canvas.drawLine(w, 0, w, h, gridPaint);

                Paint cell = new Paint(Paint.ANTI_ALIAS_FLAG);
                cell.setColor(color);
                canvas.drawRoundRect(inset, inset, w - inset, h - inset, radius, radius, cell);
            });
        }
    }

    private Picture record(int w, int h, PictureDrawer drawer) {
        Picture pic = new Picture();
        Canvas canvas = pic.beginRecording(w, h);
        drawer.draw(canvas);
        pic.endRecording();
        return pic;
    }

    private interface PictureDrawer {
        void draw(Canvas canvas);
    }

    // ========== 演化调度 ==========

    private void togglePlay() {
        if (playing) {
            pause();
            showToast("已暂停");
        } else {
            if (board.isEmpty()) {
                showToast("画布是空的，先点几个细胞或放置图案");
                return;
            }
            playing = true;
            scheduleNextGeneration();
            showToast("开始演化");
        }
        invalidateOptionsMenu();
        updateStatus();
    }

    private void pause() {
        playing = false;
        // 令牌自增：在途计算结果作废，不等待线程返回
        computeToken++;
        computing = false;
        mainHandler.removeCallbacksAndMessages(null);
    }

    // 按当前速度档位安排下一代计算
    private void scheduleNextGeneration() {
        if (!playing) return;
        mainHandler.postDelayed(this::computeGeneration, SPEED_INTERVALS[speedIndex]);
    }

    // 单步：不进入连续演化，只算一代
    private void step() {
        if (board.isEmpty()) {
            showToast("画布是空的");
            return;
        }
        if (computing) return;
        computeGeneration();
    }

    // 提交一代计算到后台线程；结果回主线程应用并局部重绘
    private void computeGeneration() {
        if (computing) return;
        computing = true;
        final int token = computeToken;
        // 快照交给子线程只读；主线程若此间编辑，LifeBoard 会 copy-on-write，互不干扰
        final Map<Long, Integer> snapshot = board.snapshotForCompute();

        computeExecutor.execute(() -> {
            if (token != computeToken) {
                // 任务已作废：仍回主线程清标志，保证 computing 只由主线程改写
                mainHandler.post(() -> {
                    if (token == computeToken) computing = false;
                });
                return;
            }
            final LifeBoard.Generation next = LifeBoard.compute(snapshot);
            mainHandler.post(() -> {
                computing = false;
                // 令牌不匹配：期间发生了编辑/清空/暂停，丢弃本次结果
                if (token != computeToken) {
                    // 演化仍在进行（编辑打断了在途计算）时重新安排下一代，
                    // 否则演化循环会静默中断：playing 仍为 true 却永远等不到下一次计算
                    if (playing) scheduleNextGeneration();
                    return;
                }
                applyGeneration(next);
            });
        });
    }

    // 单次增量刷新的上限：变化格子超过该数量时改用一次全量刷新，
    // 避免逐格 update 触发成百上千次布局回调（全量刷新只走一趟重排）
    private static final int INCREMENTAL_LIMIT = 256;

    // 应用一代结果：整表替换 + 只重绘变化的格子
    private void applyGeneration(LifeBoard.Generation next) {
        board.applyGeneration(next);

        if (next.changed.length > INCREMENTAL_LIMIT) {
            tileView.updateAll();
        } else {
            // 增量刷新：逐个 update 变化坐标（视窗外的坐标由引擎判定后直接跳过，零绘制开销）
            for (long key : next.changed) {
                tileView.update(LifeBoard.columnOf(key), LifeBoard.rowOf(key));
            }
        }

        updateStatus();

        if (next.isStable()) {
            // 全局静止（静物/空盘）：没有任何变化，继续算也是同一结果，直接停
            if (playing) {
                pause();
                invalidateOptionsMenu();
                showToast(board.isEmpty() ? "全部消亡，演化结束" : "已进入稳定态，演化结束");
            }
            return;
        }

        if (board.getPopulation() >= LifeBoard.MAX_POPULATION) {
            pause();
            invalidateOptionsMenu();
            showToast("种群达到 " + LifeBoard.MAX_POPULATION + " 上限，已暂停");
            return;
        }

        scheduleNextGeneration();
    }

    // ========== 编辑 ==========

    private void onCellClick(int column, int row) {
        // 编辑即令牌自增：在途计算作废，避免"算旧状态、覆盖新编辑"
        computeToken++;
        computing = false;
        boolean alive = board.toggle(column, row);
        tileView.update(column, row);
        updateStatus();
        if (!playing) {
            showToast(String.format(Locale.getDefault(), "%s (%d, %d)", alive ? "放置" : "移除", column, row));
        }
    }

    private void clearBoard() {
        pause();
        board.clear();
        tileView.updateAll();
        invalidateOptionsMenu();
        updateStatus();
        showToast("已清空");
    }

    // 在当前视窗范围内随机播撒活细胞（概率 25%）
    private void randomFill() {
        pause();
        int left = tileView.findColumn(0);
        int top = tileView.findRow(0);
        int right = tileView.findColumn(tileView.getWidth());
        int bottom = tileView.findRow(tileView.getHeight());
        board.randomFill(left, top, right, bottom, 0.25f, random);
        tileView.updateAll();
        invalidateOptionsMenu();
        updateStatus();
        showToast("已在视窗内随机播撒");
    }

    // 把图案放在视窗中心（清空后放置，便于观察单个图案的完整演化）
    private void placePattern(LifePatterns.Pattern pattern) {
        pause();
        board.clear();
        int centerColumn = tileView.findColumn(tileView.getWidth() / 2f);
        int centerRow = tileView.findRow(tileView.getHeight() / 2f);
        board.placePattern(pattern.offsets,
                centerColumn - pattern.getWidth() / 2,
                centerRow - pattern.getHeight() / 2);
        tileView.updateAll();
        invalidateOptionsMenu();
        updateStatus();
        showToast(pattern.name + "：" + pattern.description);
    }

    private void setSpeed(int index) {
        speedIndex = index;
        showToast("演化速度：" + SPEED_NAMES[index]);
        invalidateOptionsMenu();
        updateStatus();
    }

    private void updateStatus() {
        statusBar.setText(String.format(Locale.getDefault(),
                "第 %d 代   活细胞 %d   %s   %s",
                board.getGeneration(), board.getPopulation(),
                SPEED_NAMES[speedIndex], playing ? "演化中" : "已暂停"));
    }

    // ========== 菜单 ==========

    // 本 demo 只有一种渲染方案，也没有有限/无限切换（永远是无限平面）
    @Override
    public boolean hasPlanMode() {
        return false;
    }

    @Override
    public boolean hasMaxMode() {
        return false;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        boolean result = super.onCreateOptionsMenu(menu);
        menu.add(Menu.NONE, MENU_ID_PLAY, Menu.NONE, playing ? "暂停" : "开始演化")
                .setOnMenuItemClickListener(this)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(Menu.NONE, MENU_ID_STEP, Menu.NONE, "单步演化")
                .setOnMenuItemClickListener(this)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

        SubMenu patternMenu = menu.addSubMenu(Menu.NONE, MENU_ID_PATTERN, Menu.NONE, "经典图案");
        patternMenu.getItem().setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        for (int i = 0; i < LifePatterns.ALL.length; i++) {
            patternMenu.add(Menu.NONE, MENU_ID_PATTERNS[i], Menu.NONE, LifePatterns.ALL[i].name)
                    .setOnMenuItemClickListener(this);
        }

        SubMenu speedMenu = menu.addSubMenu(Menu.NONE, MENU_ID_SPEED, Menu.NONE, "演化速度");
        speedMenu.getItem().setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        for (int i = 0; i < SPEED_NAMES.length; i++) {
            speedMenu.add(Menu.NONE, MENU_ID_SPEEDS[i], Menu.NONE, SPEED_NAMES[i])
                    .setCheckable(true)
                    .setChecked(i == speedIndex)
                    .setOnMenuItemClickListener(this);
        }

        menu.add(Menu.NONE, MENU_ID_RANDOM, Menu.NONE, "随机播撒")
                .setOnMenuItemClickListener(this)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(Menu.NONE, MENU_ID_CLEAR, Menu.NONE, "清空画布")
                .setOnMenuItemClickListener(this)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        return result;
    }

    @Override
    public boolean onMenuItemClick(@NonNull MenuItem menuItem) {
        int id = menuItem.getItemId();
        if (id == MENU_ID_PLAY) {
            togglePlay();
            return true;
        }
        if (id == MENU_ID_STEP) {
            step();
            return true;
        }
        if (id == MENU_ID_CLEAR) {
            clearBoard();
            return true;
        }
        if (id == MENU_ID_RANDOM) {
            randomFill();
            return true;
        }
        for (int i = 0; i < MENU_ID_PATTERNS.length; i++) {
            if (id == MENU_ID_PATTERNS[i]) {
                placePattern(LifePatterns.ALL[i]);
                return true;
            }
        }
        for (int i = 0; i < MENU_ID_SPEEDS.length; i++) {
            if (id == MENU_ID_SPEEDS[i]) {
                setSpeed(i);
                return true;
            }
        }
        return super.onMenuItemClick(menuItem);
    }

    // ========== 生命周期 ==========

    @Override
    protected void onDebugModeChanged(boolean enabled) {
        if (tileView != null) tileView.setDebugMode(enabled);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 离开前台停止演化，避免后台空转耗电
        if (playing) {
            pause();
            invalidateOptionsMenu();
            updateStatus();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        pause();
        computeExecutor.shutdownNow();
        tilePictures = null; // Picture 无公开 close()，置空交给 GC 回收 native 指令
        if (tileView != null) tileView.setAdapter(null);
    }

    // ========== 适配器与瓦片 ==========

    private class LifeAdapter extends TileView.Adapter {

        // 无限平面：完整 int32 边界
        @Override
        public int getLeftBound() {
            return Integer.MIN_VALUE;
        }

        @Override
        public int getTopBound() {
            return Integer.MIN_VALUE;
        }

        @Override
        public int getRightBound() {
            return Integer.MAX_VALUE;
        }

        @Override
        public int getBottomBound() {
            return Integer.MAX_VALUE;
        }

        // 一种瓦片类型即可：外观差异由 Picture 索引承担，避免按年龄拆类型导致池碎片
        @Override
        public TileView.TileHolder onCreateTileHolder(int type) {
            return new LifeTileHolder();
        }

        @Override
        public void onBindTileHolder(TileView.TileHolder holder, int column, int row) {
            // 状态从 board 现取，无需缓存
        }
    }

    // 瓦片绘制：按年龄档位选录制指令 + 矢量回放（缩放清晰不失真）
    private class LifeTileHolder extends TileView.TileHolder {

        @Override
        public void draw(Canvas canvas) {
            ensurePictures(getWidth(), getHeight());
            if (tilePictures == null) return;

            int level = LifeBoard.ageLevel(board.getAge(getColumn(), getRow()));
            Picture pic = tilePictures[level];
            if (pic == null) return;

            canvas.save();
            canvas.scale(getScaleFactor(), getScaleFactor());
            pic.draw(canvas);
            canvas.restore();
        }

        @Override
        public void onSizeChanged(int width, int height) {
            super.onSizeChanged(width, height);
            // 模型尺寸变化（非缩放）：标记下次绘制时重录
            if (tilePictures != null && (recordW != width || recordH != height)) {
                recordW = -1;
                recordH = -1;
            }
        }

        @Override
        public boolean onClick() {
            onCellClick(getColumn(), getRow());
            return true;
        }
    }

}