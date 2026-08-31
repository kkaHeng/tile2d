package com.ahheng.tile2d.app.gomoku;

import android.animation.ValueAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Picture;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ahheng.tile2d.app.BaseActivity;
import com.ahheng.tile2d.widget.canvas.TileView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// 五子棋 Demo：有限模式(-100~+100 共 201×201) / 伪无限模式(完整 int32)，两模式均判定五连胜负
// 玩法：双人同屏、人机对战；「AI 自动下棋」开关开启后双方全部由 AI 行棋（等效双 AI 对弈）
// 渲染：TileView 逐格瓦片，AI：棋型评分 + α-β 剪枝
public class GomokuActivity extends BaseActivity {

    private static final int MENU_ID_RESTART = nextId();
    private static final int MENU_ID_VS_AI = nextId();
    private static final int MENU_ID_AI_TOGGLE = nextId();
    private static final int MENU_ID_COPY_LOG = nextId();

    // 有限模式棋盘边界（对称范围 -100 ~ +100，共 201×201 格）
    private static final int BOARD_MIN = -100;
    private static final int BOARD_MAX = 100;

    private TileView tileView;
    private GomokuAdapter adapter;
    private GomokuBoard board;
    private GomokuAI ai;

    // 玩法模式
    private enum Mode { PVP, VS_AI }

    private Mode mode = Mode.PVP;
    private int currentColor = GomokuBoard.BLACK;
    private boolean gameOver;

    // AI 调度（参考扫雷：后台线程计算 + 主线程落子，可随时开关）
    private final Handler aiHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService aiExecutor = Executors.newSingleThreadExecutor();
    private volatile boolean aiPlaying;  // AI 自动下棋开关（扫雷式菜单启停）
    private volatile boolean aiThinking; // AI 正在后台算路，防止重复调度
    private volatile int aiGeneration;   // AI 任务代次：重开/关闭开关时自增，旧任务结果一律丢弃
    private volatile long stateVersion; // 棋局版本号，AI 计算期间棋盘被改动则丢弃结果
    private ValueAnimator cameraAnimator; // AI 落子后视窗平滑跟随动画

    // 预渲染瓦片位图（按屏幕像素尺寸绘制，缩放结算后懒重建）
    // 索引：[0]空格 [1]星位格 [2]黑子 [3]白子 [4]黑子+末手标 [5]白子+末手标
    private static final int B_BG = 0;
    private static final int B_BG_STAR = 1;
    private static final int B_BLACK = 2;
    private static final int B_WHITE = 3;
    private static final int B_BLACK_MARK = 4;
    private static final int B_WHITE_MARK = 5;

    // 录制图库：6 种瓦片外观的绘制指令（Picture 矢量录制），缩放时按 scaleFactor 矢量回放，无需重录
    private Picture[] tilePictures;
    private int recordW = -1; // 录制时的模型尺寸（瓦片尺寸变化才重录；缩放不重录）
    private int recordH = -1;

    // 视觉常量（统一木色棋盘）
    private static final int COLOR_BG = Color.parseColor("#DEB887");      // 木色
    private static final int COLOR_LINE = Color.parseColor("#8B5A2B");    // 深棕格线
    private static final int COLOR_MARK = Color.parseColor("#E53935");    // 末手红标

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        tileView = new TileView(this);
        setContentView(tileView, new ViewGroup.LayoutParams(-1, -1));
        tileView.setDebugMode(isDebugMode());
        tileView.setHorizontalScrollEnabled(true);
        tileView.setVerticalScrollEnabled(true);
        tileView.setDefaultTileWidth(dp2px(48));
        tileView.setDefaultTileHeight(dp2px(48));

        ai = new GomokuAI(4, 10);
        board = new GomokuBoard(BOARD_MIN, BOARD_MAX, isMaxMode());
        tileView.setZoomEnabled(true); // Demo 默认开启缩放
        tileView.setAdapter((adapter = new GomokuAdapter()));

        tileView.post(() -> {
            if (!isFinishing()) {
                seekCenter();
            }
        });
    }

    // 视窗定位：有限模式定位到棋盘中心，伪无限模式定位到原点
    private void seekCenter() {
        if (isMaxMode()) {
            tileView.seek(0, 0, 0, 0);
        } else {
            tileView.seek(board.getCenter(), board.getCenter(), 0, 0);
        }
    }

    // ========== 瓦片绘制指令录制 ==========

    // 按模型尺寸录制 6 种瓦片外观的绘制指令：仅首次或瓦片尺寸变化时重录。
    // 缩放通过 canvas.scale 矢量回放（指令放大不失真），无需按缩放级别重建
    private void ensurePictures(int w, int h) {
        if (w <= 0 || h <= 0) return;
        if (tilePictures != null && recordW == w && recordH == h) return;
        recordPictures(w, h);
    }

    private void recordPictures(int w, int h) {
        if (w <= 0 || h <= 0) return;

        tilePictures = new Picture[6]; // 旧数组失去引用由 GC 回收（Picture 无公开 close()）
        recordW = w;
        recordH = h;

        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(COLOR_BG);
        Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(COLOR_LINE);
        linePaint.setStrokeWidth(Math.max(1f, dp2px(0.6f)));
        Paint starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        starPaint.setColor(COLOR_LINE);
        starPaint.setStyle(Paint.Style.FILL);

        // 基础：木色底 + 十字格线（四条边各画半条，与邻格衔接成连续棋盘线）
        tilePictures[B_BG] = record(w, h, canvas -> drawBg(canvas, w, h, bgPaint, linePaint));
        // 星位格：基础 + 中心圆点
        tilePictures[B_BG_STAR] = record(w, h, canvas -> {
            drawBg(canvas, w, h, bgPaint, linePaint);
            canvas.drawCircle(w / 2f, h / 2f, Math.max(2f, dp2px(2.5f)), starPaint);
        });
        tilePictures[B_BLACK] = drawStonePicture(w, h, GomokuBoard.BLACK, false);
        tilePictures[B_WHITE] = drawStonePicture(w, h, GomokuBoard.WHITE, false);
        tilePictures[B_BLACK_MARK] = drawStonePicture(w, h, GomokuBoard.BLACK, true);
        tilePictures[B_WHITE_MARK] = drawStonePicture(w, h, GomokuBoard.WHITE, true);
    }

    // 录制工具：把绘制逻辑录制成矢量指令
    private Picture record(int w, int h, PictureDrawer drawer) {
        Picture pic = new Picture();
        Canvas canvas = pic.beginRecording(w, h);
        drawer.draw(canvas);
        pic.endRecording();
        return pic;
    }

    // 木色底 + 十字格线（基础外观，供普通格与星位格复用）
    private void drawBg(Canvas canvas, int w, int h, Paint bgPaint, Paint linePaint) {
        canvas.drawRect(0, 0, w, h, bgPaint);
        canvas.drawLine(0, h / 2f, w, h / 2f, linePaint);
        canvas.drawLine(w / 2f, 0, w / 2f, h, linePaint);
    }

    private interface PictureDrawer {
        void draw(Canvas canvas);
    }

    // 棋子瓦片：木色底 + 立体棋子（径向渐变高光）+ 末手红标
    private Picture drawStonePicture(int w, int h, int color, boolean mark) {
        return record(w, h, canvas -> {
            float cx = w / 2f;
            float cy = h / 2f;
            float r = Math.min(w, h) * 0.42f;

            Paint bgFill = new Paint(Paint.ANTI_ALIAS_FLAG);
            bgFill.setColor(COLOR_BG);
            canvas.drawRect(0, 0, w, h, bgFill);
            // 格线
            Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            linePaint.setColor(COLOR_LINE);
            linePaint.setStrokeWidth(Math.max(1f, dp2px(0.6f)));
            canvas.drawLine(0, h / 2f, w, h / 2f, linePaint);
            canvas.drawLine(w / 2f, 0, w / 2f, h, linePaint);

            // 底部阴影（右下偏移，增强立体感）
            Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
            shadow.setColor(0x33000000);
            canvas.drawCircle(cx + r * 0.08f, cy + r * 0.12f, r, shadow);

            // 棋子本体：径向渐变，光源偏左上
            RadialGradient gradient = new RadialGradient(
                    cx - r * 0.35f, cy - r * 0.35f, r * 1.2f,
                    color == GomokuBoard.BLACK
                            ? new int[]{0xFF6B6B6B, 0xFF1A1A1A, 0xFF000000}
                            : new int[]{0xFFFFFFFF, 0xFFE6E6E6, 0xFFB0B0B0},
                    new float[]{0f, 0.55f, 1f},
                    Shader.TileMode.CLAMP);
            Paint stonePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            stonePaint.setShader(gradient);
            canvas.drawCircle(cx, cy, r, stonePaint);

            // 描边
            Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            edgePaint.setStyle(Paint.Style.STROKE);
            edgePaint.setStrokeWidth(Math.max(1f, r * 0.05f));
            edgePaint.setColor(color == GomokuBoard.BLACK ? 0xFF000000 : 0xFF9E9E9E);
            canvas.drawCircle(cx, cy, r, edgePaint);

            // 末手红标：棋子内侧圆环
            if (mark) {
                Paint markPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                markPaint.setStyle(Paint.Style.STROKE);
                markPaint.setStrokeWidth(Math.max(1.5f, r * 0.12f));
                markPaint.setColor(COLOR_MARK);
                canvas.drawCircle(cx, cy, r * 0.45f, markPaint);
            }
        });
    }

    // 是否有限模式星位格（对称棋盘：中心列/行 ± 3 与中心，即 9 个星位点）
    private boolean isStarCell(int col, int row) {
        int c0 = board.getCenter();
        int[] stars = {board.getMin() + 3, c0, board.getMax() - 3};
        for (int sc : stars) {
            for (int sr : stars) {
                if (sc == col && sr == row) return true;
            }
        }
        return false;
    }

    // ========== 游戏逻辑 ==========

    private void restartGame() {
        stopThinking();
        board.clear();
        currentColor = GomokuBoard.BLACK;
        gameOver = false;
        stateVersion++;
        tileView.updateAll();
        invalidateOptionsMenu();
        showToast("新棋局开始");
        // 开关状态跨重开保留：开关开启时双方均为 AI（重开后 AI 执黑先行）；
        // 关闭时人机模式由玩家执黑先行，双人模式全手动
        if (isAITurn()) {
            scheduleAIMove();
        }
    }

    private void setMode(Mode newMode) {
        if (mode == newMode && !gameOver) {
            return;
        }
        mode = newMode;
        // 新模式统一从手动开始：人机对战中 AI 行白棋由模式默认承担（开关保持关闭），
        // 想让双方都由 AI 下棋再手动打开「AI 自动下棋」
        aiPlaying = false;
        showToast(mode == Mode.PVP ? "双人对战" : "人机对战");
        restartGame();
    }

    // 玩家点击落子（由瓦片持有者回调）
    private void onCellClick(int column, int row) {
        if (gameOver) return;
        // AI 回合（正在思考或轮到 AI）不接受玩家落子；开关关闭后玩家可随时接管
        if (aiThinking || isAITurn()) {
            showToast("AI 回合中，可用菜单关闭 AI 下棋");
            return;
        }
        placeStone(column, row);
    }

    // 落子：写入棋盘、判定胜负、刷新视窗、决定下一手
    private void placeStone(int column, int row) {
        if (gameOver || !board.canPlace(column, row)) return;
        board.place(column, row, currentColor);
        stateVersion++;
        tileView.update(column, row);
        highlightLastMove();

        int winner = board.checkWinner();
        if (winner != GomokuBoard.EMPTY) {
            gameOver = true;
            tileView.updateAll();
            showResult(winner == GomokuBoard.BLACK ? "黑棋五连获胜！" : "白棋五连获胜！");
            return;
        }

        currentColor = currentColor == GomokuBoard.BLACK ? GomokuBoard.WHITE : GomokuBoard.BLACK;

        // 下一回合仍由 AI 行棋才继续调度
        if (!gameOver && isAITurn()) {
            scheduleAIMove();
        }
    }

    // 当前回合是否由 AI 行棋。
    // 「AI 自动下棋」开关优先级最高：开启后双方全部由 AI 行棋（对手是谁无所谓）；
    // 关闭时回退到模式默认——人机对战 AI 只行白棋，双人模式全手动
    private boolean isAITurn() {
        if (aiPlaying) return true;
        return mode == Mode.VS_AI && currentColor == GomokuBoard.WHITE;
    }

    // AI 落子后视窗平滑跟随到棋子附近（参考扫雷 startCameraFollow）
    // 1000ms 动画内每帧按剩余距离的 15% 逼近，手指交互时立即打断
    private void startCameraFollow(int col, int row) {
        if (cameraAnimator != null) {
            cameraAnimator.cancel();
        }

        cameraAnimator = ValueAnimator.ofFloat(0f, 1f);
        cameraAnimator.setDuration(1000);

        cameraAnimator.addUpdateListener(a -> {
            if (tileView.isInteractingWithView()) {
                cameraAnimator.cancel();
                return;
            }

            float tileCenterX = tileView.getTileX(col) + tileView.getTileWidth(col) / 2f;
            float tileCenterY = tileView.getTileY(row) + tileView.getTileHeight(row) / 2f;
            float screenCenterX = tileView.getWidth() / 2f;
            float screenCenterY = tileView.getHeight() / 2f;

            float dx = screenCenterX - tileCenterX;
            float dy = screenCenterY - tileCenterY;

            if (Math.abs(dx) > 0.5f || Math.abs(dy) > 0.5f) {
                tileView.offset(dx * 0.15f, dy * 0.15f);
            }
        });

        cameraAnimator.start();
        if (!tileView.isInteractingWithView()) {
            tileView.resetAnimator();
        }
    }

    private void highlightLastMove() {
        int c = board.getLastCol();
        int r = board.getLastRow();
        if (c != Integer.MIN_VALUE) {
            tileView.update(c, r);
        }
    }

    private void showResult(String message) {
        showToast(message);
    }

    // ========== AI 调度 ==========

    // AI 思考有 400ms 节奏延迟，保证可感知的落子节奏
    private void scheduleAIMove() {
        if (gameOver || !isAITurn()) return;
        aiHandler.removeCallbacksAndMessages(null);
        aiHandler.postDelayed(this::runAI, 400);
    }

    private void runAI() {
        if (gameOver || !isAITurn() || aiThinking) return;
        final int color = currentColor;
        final long version = stateVersion;
        final int generation = aiGeneration;
        aiThinking = true;
        // AI 在棋盘副本上搜索：临时落子不会污染真实棋盘，避免 UI 绘制看到搜索中间态
        final GomokuBoard searchBoard = board.copy();

        aiExecutor.execute(() -> {
            if (generation != aiGeneration) {
                aiThinking = false; // 任务已作废，同步清思考标志
                return;
            }
            final int[] move = ai.chooseMove(searchBoard, color);
            aiHandler.post(() -> {
                aiThinking = false;
                // 代次或版本不匹配（重开/关开关/期间被落子）：丢弃本次结果，避免污染新状态
                if (generation != aiGeneration || version != stateVersion) return;
                if (gameOver || !isAITurn()) return;
                if (move == null) {
                    // 无候选点：有限模式平局
                    gameOver = true;
                    showToast("棋盘已满，平局");
                    return;
                }
                placeStone(move[0], move[1]);
                // AI 落子后视窗平滑跟随到棋子附近
                startCameraFollow(move[0], move[1]);
            });
        });
    }

    // 中止在途 AI 计算：代次自增使旧结果作废（不杀线程，规避消息队列竞态）
    private void stopThinking() {
        aiGeneration++;
        aiThinking = false;
        aiHandler.removeCallbacksAndMessages(null);
        if (cameraAnimator != null) {
            cameraAnimator.cancel();
            cameraAnimator = null;
        }
    }

    // 扫雷式 AI 开关：任意模式可开可关，关闭后玩家接管当前行棋方
    private boolean isAIEnabled() {
        return aiPlaying;
    }

    private void toggleAI() {
        aiPlaying = !aiPlaying;
        if (aiPlaying) {
            showToast("双方由 AI 接管");
            scheduleAIMove();
        } else {
            showToast("已切换为手动");
            stopThinking();
        }
        invalidateOptionsMenu();
    }

    // ========== 菜单 ==========

    // 本 demo 只有一种渲染方案，关闭「切换方案」菜单
    @Override
    public boolean hasPlanMode() {
        return false;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        boolean result = super.onCreateOptionsMenu(menu);
        menu.add(Menu.NONE, MENU_ID_RESTART, Menu.NONE, "重新开始")
                .setOnMenuItemClickListener(this)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(Menu.NONE, MENU_ID_VS_AI, Menu.NONE, "人机对战")
                .setOnMenuItemClickListener(this)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(Menu.NONE, MENU_ID_AI_TOGGLE, Menu.NONE, "AI 自动下棋")
                .setCheckable(true)
                .setChecked(isAIEnabled())
                .setOnMenuItemClickListener(this)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(Menu.NONE, MENU_ID_COPY_LOG, Menu.NONE, "复制对局日志")
                .setOnMenuItemClickListener(this)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        return result;
    }

    @Override
    public boolean onMenuItemClick(@NonNull MenuItem menuItem) {
        int id = menuItem.getItemId();
        if (id == MENU_ID_RESTART) {
            restartGame();
            return true;
        }
        if (id == MENU_ID_VS_AI) {
            setMode(Mode.VS_AI);
            return true;
        }
        if (id == MENU_ID_AI_TOGGLE) {
            toggleAI();
            return true;
        }
        if (id == MENU_ID_COPY_LOG) {
            copyMoveLog();
            return true;
        }
        return super.onMenuItemClick(menuItem);
    }

    // 把落子历史按 [黑][列, 行] / [白][列, 行] 格式复制到系统剪贴板
    private void copyMoveLog() {
        String log = board.exportMoveLog();
        if (log.isEmpty()) {
            showToast("还没有落子记录");
            return;
        }
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("gomoku_moves", log));
        showToast("已复制 " + board.getStoneCount() + " 手记录");
    }

    // ========== 模式切换与生命周期 ==========

    @Override
    protected void onPause() {
        super.onPause();
        // 离开前台停止 AI 对弈，避免后台持续自弈耗电（与扫雷/生命游戏的清理惯例一致）
        // 在途计算返回时由 aiGeneration 令牌防线丢弃，无需等待线程
        if (aiPlaying) {
            aiPlaying = false;
            stopThinking();
            invalidateOptionsMenu();
        }
    }

    @Override
    protected void onMaxModeChanged(boolean maxMode) {
        super.onMaxModeChanged(maxMode);
        board = new GomokuBoard(BOARD_MIN, BOARD_MAX, maxMode);
        restartGame();
        seekCenter();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopThinking();
        aiExecutor.shutdownNow();
        tilePictures = null; // Picture 无公开 close()，置空引用交给 GC 回收 native 指令
        tileView.setAdapter(null);
    }

    // ========== 适配器与瓦片 ==========

    private class GomokuAdapter extends TileView.Adapter {

        @Override
        public int getLeftBound() {
            return isMaxMode() ? Integer.MIN_VALUE : BOARD_MIN;
        }

        @Override
        public int getTopBound() {
            return isMaxMode() ? Integer.MIN_VALUE : BOARD_MIN;
        }

        @Override
        public int getRightBound() {
            return isMaxMode() ? Integer.MAX_VALUE : BOARD_MAX;
        }

        @Override
        public int getBottomBound() {
            return isMaxMode() ? Integer.MAX_VALUE : BOARD_MAX;
        }

        @Override
        public TileView.TileHolder onCreateTileHolder(int type) {
            return new GomokuTileHolder();
        }

        @Override
        public void onBindTileHolder(TileView.TileHolder holder, int column, int row) {
            // 瓦片坐标由 BaseTileHolder 维护，无需额外处理
        }
    }

    // 瓦片绘制：录制指令 + 矢量回放（缩放适配 + 3D 立体棋子 + 末手高亮）
    private class GomokuTileHolder extends TileView.TileHolder {

        @Override
        public void draw(Canvas canvas) {
            int col = getColumn();
            int row = getRow();
            // 懒录制：模型尺寸（缩放不触发；瓦片尺寸变化才重录）
            ensurePictures(getWidth(), getHeight());
            if (tilePictures == null) return;

            int stone = board.get(col, row);
            boolean last = stone != GomokuBoard.EMPTY
                    && board.getLastCol() == col && board.getLastRow() == row;
            int idx;
            if (stone == GomokuBoard.BLACK) {
                idx = last ? B_BLACK_MARK : B_BLACK;
            } else if (stone == GomokuBoard.WHITE) {
                idx = last ? B_WHITE_MARK : B_WHITE;
            } else {
                idx = (!isMaxMode() && isStarCell(col, row)) ? B_BG_STAR : B_BG;
            }
            // 矢量回放：指令按 scaleFactor 等比缩放，清晰不失真且无需按缩放级别重建
            Picture pic = tilePictures[idx];
            if (pic == null) return;
            canvas.save();
            canvas.scale(getScaleFactor(), getScaleFactor());
            pic.draw(canvas);
            canvas.restore();
        }

        @Override
        public void onSizeChanged(int width, int height) {
            super.onSizeChanged(width, height);
            // 瓦片模型尺寸变化（非缩放）：标记下次绘制时重录
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