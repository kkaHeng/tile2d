package com.ahheng.tile2d.app.minesweeper;

import android.animation.ValueAnimator;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;

import androidx.annotation.Nullable;

import com.ahheng.tile2d.LayoutModel;
import com.ahheng.tile2d.TileCoreService;
import com.ahheng.tile2d.app.BaseActivity;
import com.ahheng.tile2d.widget.canvas.TileView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MinesweeperActivity extends BaseActivity {

    private static final long DEFAULT_SEED = 5201314L;

    private static final String SAVE_FILE = "minesweeper.dat";
    private static final int MENU_ID_RESTART = nextId();
    private static final int MENU_ID_TOGGLE_FLAG = nextId();
    private static final int MENU_ID_JUMP_CENTER = nextId();
    private static final int MENU_ID_AI_PLAY = nextId();

    private static final int MINE_DENSITY = 40;

    // 预渲染素材索引 (穷举所有15种状态)
    private static final int IMG_NORMAL = 0;
    private static final int IMG_EMPTY = 1;
    private static final int IMG_NUM_1 = 2;
    private static final int IMG_NUM_2 = 3;
    private static final int IMG_NUM_3 = 4;
    private static final int IMG_NUM_4 = 5;
    private static final int IMG_NUM_5 = 6;
    private static final int IMG_NUM_6 = 7;
    private static final int IMG_NUM_7 = 8;
    private static final int IMG_NUM_8 = 9;
    private static final int IMG_FLAG = 10;
    private static final int IMG_MINE = 11;          // 游戏结束:未插旗的雷
    private static final int IMG_EXPLODED = 12;      // 游戏结束:踩爆的雷
    private static final int IMG_FLAG_MINE = 13;     // 游戏结束:正确插旗的雷
    private static final int IMG_WRONG_FLAG = 14;    // 游戏结束:错误插旗的安全格

    private TileView tileView;
    private MineAdapter adapter;

    private long worldSeed;
    private boolean firstClick = true;
    private final Set<Long> safeZone = new HashSet<>();
    private final Set<Long> revealed = new HashSet<>();
    private final Set<Long> flagged = new HashSet<>();
    private final Set<Long> exploded = new HashSet<>();

    private boolean flagMode = false;
    private boolean gameOver = false;
    private boolean gameWon = false;

    private int lastCenterCol = 0;
    private int lastCenterRow = 0;

    // 预渲染图库
    private Bitmap[] tileBitmaps;

    // AI
    private boolean aiPlaying = false;
    private volatile boolean aiRunning = false;
    private final Handler aiHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService aiExecutor = Executors.newSingleThreadExecutor();
    private ValueAnimator cameraAnimator;
    private int aiCurrentCol = 0;
    private int aiCurrentRow = 0;
    private boolean aiSleeping;
    
    // AI 探索机制
    private int aiExploreAttempts = 0;
    private static final int MAX_EXPLORE_ATTEMPTS = 8;
    private final int[][] directions = {
            {-1, -1}, {0, -1}, {1, -1},
            {1, 0},           {1, 1},
            {0, 1}, {-1, 1}, {-1, 0}
    };

    public static class BoardSnapshot {
        public final Map<Long, Integer> cells;
        public final int mineDensity;
        public final int centerCol;
        public final int centerRow;
        public final int leftBound;
        public final int topBound;
        public final int rightBound;
        public final int bottomBound;

        public BoardSnapshot(Map<Long, Integer> cells, int mineDensity, int centerCol, int centerRow,
                            int leftBound, int topBound, int rightBound, int bottomBound) {
            this.cells = cells;
            this.mineDensity = mineDensity;
            this.centerCol = centerCol;
            this.centerRow = centerRow;
            this.leftBound = leftBound;
            this.topBound = topBound;
            this.rightBound = rightBound;
            this.bottomBound = bottomBound;
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        tileView = new TileView(this);
        setContentView(tileView);
        tileView.setDebugMode(isDebugMode());

        int tileSize = dp2px(40);
        tileView.setDefaultTileWidth(tileSize);
        tileView.setDefaultTileHeight(tileSize);

        generateTileBitmaps(tileSize, tileSize);

        loadGame();
        tileView.setZoomEnabled(true); // Demo 默认开启缩放
        tileView.setAdapter((adapter = new MineAdapter()));

        tileView.post(() -> {
            if (!isFinishing()) {
                tileView.seek(lastCenterCol, lastCenterRow, 0, 0);
            }
        });
    }

    // ========== 素材预渲染 ==========
    private void generateTileBitmaps(int w, int h) {
        if (w <= 0 || h <= 0) return;

        if (tileBitmaps == null) {
            tileBitmaps = new Bitmap[15];
        } else {
            for (Bitmap b : tileBitmaps) {
                if (b != null) b.recycle();
            }
        }

        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint flagPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint minePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
        textPaint.setTextSize(Math.min(w, h) * 0.6f);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        flagPaint.setStyle(Paint.Style.FILL);
        minePaint.setStyle(Paint.Style.FILL);
        minePaint.setColor(Color.BLACK);

        for (int i = 0; i < 15; i++) {
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bmp);
            drawTileState(canvas, i, w, h, bgPaint, linePaint, textPaint, flagPaint, minePaint);
            tileBitmaps[i] = bmp;
        }
    }

    private void drawTileState(Canvas canvas, int state, int w, int h, Paint bgPaint, Paint linePaint, Paint textPaint, Paint flagPaint, Paint minePaint) {
        boolean isExploded = (state == IMG_EXPLODED);
        boolean isRevealedBase = (state == IMG_EMPTY || (state >= IMG_NUM_1 && state <= IMG_NUM_8));

        if (isExploded) {
            bgPaint.setColor(Color.parseColor("#FF0000"));
            canvas.drawRect(0, 0, w, h, bgPaint);
            drawMineGraphics(canvas, w, h, linePaint, minePaint);
        } else if (isRevealedBase) {
            bgPaint.setColor(Color.parseColor("#C0C0C0"));
            canvas.drawRect(0, 0, w, h, bgPaint);

            linePaint.setColor(Color.parseColor("#808080"));
            linePaint.setStrokeWidth(1f);
            canvas.drawLine(0, 0, w, 0, linePaint);
            canvas.drawLine(0, 0, 0, h, linePaint);
            canvas.drawLine(w, 0, w, h, linePaint);
            canvas.drawLine(0, h, w, h, linePaint);

            if (state >= IMG_NUM_1 && state <= IMG_NUM_8) {
                int num = state - IMG_NUM_1 + 1;
                textPaint.setColor(getNumberColor(num));
                Paint.FontMetrics fm = textPaint.getFontMetrics();
                float textY = h / 2f - (fm.ascent + fm.descent) / 2f;
                canvas.drawText(String.valueOf(num), w / 2f, textY, textPaint);
            }
        } else {
            // 未翻开的基础底色
            bgPaint.setColor(Color.parseColor("#C0C0C0"));
            canvas.drawRect(0, 0, w, h, bgPaint);

            linePaint.setColor(Color.parseColor("#FFFFFF"));
            linePaint.setStrokeWidth(Math.max(2, Math.min(w, h) * 0.08f));
            float inset = linePaint.getStrokeWidth() / 2f;
            canvas.drawLine(inset, inset, w - inset, inset, linePaint);
            canvas.drawLine(inset, inset, inset, h - inset, linePaint);

            linePaint.setColor(Color.parseColor("#808080"));
            canvas.drawLine(w - inset, inset, w - inset, h - inset, linePaint);
            canvas.drawLine(inset, h - inset, w - inset, h - inset, linePaint);

            if (state == IMG_MINE) {
                drawMineGraphics(canvas, w, h, linePaint, minePaint);
            } else if (state == IMG_FLAG) {
                drawFlagGraphics(canvas, w, h, linePaint, flagPaint, minePaint);
            } else if (state == IMG_FLAG_MINE) {
                drawMineGraphics(canvas, w, h, linePaint, minePaint);
                drawFlagGraphics(canvas, w, h, linePaint, flagPaint, minePaint);
            } else if (state == IMG_WRONG_FLAG) {
                drawFlagGraphics(canvas, w, h, linePaint, flagPaint, minePaint);
                linePaint.setColor(Color.RED);
                linePaint.setStrokeWidth(Math.max(2, Math.min(w, h) * 0.1f));
                canvas.drawLine(0, 0, w, h, linePaint);
                canvas.drawLine(w, 0, 0, h, linePaint);
            }
        }
    }

    private int getNumberColor(int n) {
        switch (n) {
            case 1: return Color.parseColor("#0000FF");
            case 2: return Color.parseColor("#008000");
            case 3: return Color.parseColor("#FF0000");
            case 4: return Color.parseColor("#000080");
            case 5: return Color.parseColor("#800000");
            case 6: return Color.parseColor("#008080");
            case 7: return Color.parseColor("#000000");
            case 8: return Color.parseColor("#808080");
            default: return Color.BLACK;
        }
    }

    private void drawFlagGraphics(Canvas canvas, int w, int h, Paint linePaint, Paint flagPaint, Paint minePaint) {
        float cx = w / 2f;
        float cy = h / 2f;
        float size = Math.min(w, h) * 0.35f;

        linePaint.setColor(Color.BLACK);
        linePaint.setStrokeWidth(Math.max(2, size * 0.12f));
        float poleTop = cy - size * 0.9f;
        float poleBottom = cy + size * 0.6f;
        canvas.drawLine(cx, poleTop, cx, poleBottom, linePaint);

        flagPaint.setColor(Color.parseColor("#FF0000"));
        Path flagPath = new Path();
        flagPath.moveTo(cx + linePaint.getStrokeWidth() / 2f, poleTop + size * 0.1f);
        flagPath.lineTo(cx + size * 1.1f, poleTop + size * 0.5f);
        flagPath.lineTo(cx + linePaint.getStrokeWidth() / 2f, poleTop + size * 0.9f);
        flagPath.close();
        canvas.drawPath(flagPath, flagPaint);

        minePaint.setColor(Color.BLACK);
        float baseW = size * 0.5f;
        float baseH = size * 0.15f;
        canvas.drawRect(cx - baseW / 2f, poleBottom, cx + baseW / 2f, poleBottom + baseH, minePaint);
    }

    private void drawMineGraphics(Canvas canvas, int w, int h, Paint linePaint, Paint minePaint) {
        float cx = w / 2f;
        float cy = h / 2f;
        float radius = Math.min(w, h) * 0.22f;

        linePaint.setColor(Color.BLACK);
        linePaint.setStrokeWidth(Math.max(2, radius * 0.18f));
        for (int i = 0; i <= 7; i++) {
            double angle = i * Math.PI / 4;
            float x1 = cx + (float) (Math.cos(angle) * radius * 0.5);
            float y1 = cy + (float) (Math.sin(angle) * radius * 0.5);
            float x2 = cx + (float) (Math.cos(angle) * radius * 1.3);
            float y2 = cy + (float) (Math.sin(angle) * radius * 1.3);
            canvas.drawLine(x1, y1, x2, y2, linePaint);
        }

        minePaint.setColor(Color.BLACK);
        canvas.drawCircle(cx, cy, radius, minePaint);

        minePaint.setColor(Color.WHITE);
        canvas.drawCircle(cx - radius * 0.25f, cy - radius * 0.25f, radius * 0.18f, minePaint);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (aiPlaying) {
            aiSleeping = true;
            stopAI();
        }
        saveGame();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (aiSleeping) {
            aiSleeping = false;
            startAI();
            showToast("AI 起床了");
        }
    }

    @Override
    protected void onMaxModeChanged(boolean maxMode) {
        super.onMaxModeChanged(maxMode);
        tileView.snap();
    }

    private File getSaveFile() {
        return new File(getFilesDir(), SAVE_FILE);
    }

    private void saveGame() {
        LayoutModel model = tileView.getLayoutModel();
        lastCenterCol = model.colStart;
        lastCenterRow = model.rowStart;

        try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(getSaveFile()))) {
            dos.writeLong(worldSeed);
            dos.writeBoolean(firstClick);
            dos.writeInt(lastCenterCol);
            dos.writeInt(lastCenterRow);

            writeSet(dos, safeZone);
            writeSet(dos, revealed);
            writeSet(dos, flagged);
            writeSet(dos, exploded);
            dos.writeBoolean(gameWon);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void writeSet(DataOutputStream dos, Set<Long> set) throws IOException {
        dos.writeInt(set.size());
        for (long id : set) {
            dos.writeLong(id);
        }
    }

    private void loadGame() {
        File file = getSaveFile();
        if (!file.exists()) {
            worldSeed = DEFAULT_SEED;
            return;
        }

        try (DataInputStream dis = new DataInputStream(new FileInputStream(file))) {
            worldSeed = dis.readLong();
            firstClick = dis.readBoolean();
            lastCenterCol = dis.readInt();
            lastCenterRow = dis.readInt();

            readSet(dis, safeZone);
            readSet(dis, revealed);
            readSet(dis, flagged);
            readSet(dis, exploded);
            gameWon = dis.readBoolean();

            gameOver = gameWon || !exploded.isEmpty();
        } catch (IOException e) {
            worldSeed = DEFAULT_SEED;
            firstClick = true;
            safeZone.clear();
            revealed.clear();
            flagged.clear();
            exploded.clear();
            gameWon = false;
        }
    }

    private void readSet(DataInputStream dis, Set<Long> set) throws IOException {
        set.clear();
        int size = dis.readInt();
        for (int i = 0; i <= size - 1; i++) {
            set.add(dis.readLong());
        }
    }

    private void restartGame() {
        worldSeed = DEFAULT_SEED;
        firstClick = true;
        gameOver = false;
        gameWon = false;
        safeZone.clear();
        revealed.clear();
        flagged.clear();
        exploded.clear();
        tileView.updateAll();
        showToast("新游戏开始");
    }

    private static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    private boolean isMine(int column, int row) {
        long id = TileCoreService.getTileId(column, row);
        if (safeZone.contains(id)) return false;

        long hash = mix64(mix64(worldSeed ^ id));
        return ((hash & 0xFFL) < MINE_DENSITY);
    }

    private boolean isValidCoord(int column, int row) {
        return column >= adapter.getLeftBound() && column <= adapter.getRightBound() &&
                row >= adapter.getTopBound() && row <= adapter.getBottomBound();
    }

    private int countMinesAround(int column, int row) {
        int count = 0;
        long left   = adapter.getLeftBound();
        long right  = adapter.getRightBound();
        long top    = adapter.getTopBound();
        long bottom = adapter.getBottomBound();
        for (int dc = -1; dc <= 1; dc++) {
            for (int dr = -1; dr <= 1; dr++) {
                if (dc == 0 && dr == 0) continue;
                long nc = (long) column + dc;
                long nr = (long) row + dr;
                // 以适配器边界为物理边界,用 long 防止 int32 溢出回绕
                if (nc < left || nc > right || nr < top || nr > bottom) continue;
                if (isMine((int) nc, (int) nr)) {
                    count++;
                }
            }
        }
        return count;
    }

    private void reveal(int column, int row) {
        if (gameOver) return;

        long id = TileCoreService.getTileId(column, row);
        if (revealed.contains(id) || flagged.contains(id)) return;

        if (firstClick) {
            long left   = adapter.getLeftBound();
            long right  = adapter.getRightBound();
            long top    = adapter.getTopBound();
            long bottom = adapter.getBottomBound();
            for (int dc = -1; dc <= 1; dc++) {
                for (int dr = -1; dr <= 1; dr++) {
                    long nc = (long) column + dc;
                    long nr = (long) row + dr;
                    // 以适配器边界为物理边界,用 long 防止 int32 溢出
                    if (nc < left || nc > right || nr < top || nr > bottom) continue;
                    safeZone.add(TileCoreService.getTileId((int) nc, (int) nr));
                }
            }
            firstClick = false;
        }

        if (isMine(column, row)) {
            exploded.add(id);
            revealed.add(id);
            gameOver = true;
            tileView.updateAll();
            if (aiPlaying) {
                showResultDialog("💥 踩雷了", "AI 未能完成扫雷");
            } else {
                showToast("游戏结束!");
            }
            return;
        }

        Set<Long> batch = new HashSet<>();
        bfsReveal(column, row, batch);

        if (!batch.isEmpty()) {
            int minC = Integer.MAX_VALUE;
            int minR = Integer.MAX_VALUE;
            int maxC = Integer.MIN_VALUE;
            int maxR = Integer.MIN_VALUE;
            for (long rid : batch) {
                int c = TileCoreService.getColumn(rid);
                int r = TileCoreService.getRow(rid);
                if (c < minC) minC = c;
                if (r < minR) minR = r;
                if (c > maxC) maxC = c;
                if (r > maxR) maxR = r;
            }
            tileView.updateRange(minC - 1, minR - 1, maxC + 1, maxR + 1);
        }

        // 胜利判定(仅非伪无限模式)
        if (checkWinCondition()) {
            gameOver = true;
            gameWon = true;
            autoFlagAllMines();
            tileView.updateAll();
            showResultDialog("🎉 恭喜胜利!", "您已成功翻开所有安全格!");
        }
    }

    private void bfsReveal(int startCol, int startRow, Set<Long> batch) {
        long left   = adapter.getLeftBound();
        long right  = adapter.getRightBound();
        long top    = adapter.getTopBound();
        long bottom = adapter.getBottomBound();

        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{startCol, startRow});

        while (!queue.isEmpty()) {
            int[] pos = queue.poll();
            int c = pos[0];
            int r = pos[1];
            long id = TileCoreService.getTileId(c, r);

            if (revealed.contains(id) || flagged.contains(id) || batch.contains(id)) continue;
            if (isMine(c, r)) continue;

            batch.add(id);

            int mines = countMinesAround(c, r);
            if (mines == 0) {
                for (int dc = -1; dc <= 1; dc++) {
                    for (int dr = -1; dr <= 1; dr++) {
                        if (dc == 0 && dr == 0) continue;
                        long nc = (long) c + dc;
                        long nr = (long) r + dr;
                        // 以适配器边界为物理边界,用 long 防止 int32 溢出回绕死循环
                        if (nc < left || nc > right || nr < top || nr > bottom) continue;
                        queue.add(new int[]{(int) nc, (int) nr});
                    }
                }
            }
        }

        revealed.addAll(batch);
    }

    // 胜利判定:仅非伪无限模式有效。棋盘内不存在"非雷且未翻开"的格子即胜利。
    private boolean checkWinCondition() {
        // 伪无限模式无限棋盘点,不存在胜利
        if (isMaxMode()) return false;
        long left   = adapter.getLeftBound();
        long right  = adapter.getRightBound();
        long top    = adapter.getTopBound();
        long bottom = adapter.getBottomBound();
        for (long c = left; c <= right; c++) {
            for (long r = top; r <= bottom; r++) {
                int ci = (int) c;
                int ri = (int) r;
                long id = TileCoreService.getTileId(ci, ri);
                if (!revealed.contains(id) && !isMine(ci, ri)) {
                    return false;
                }
            }
        }
        return true;
    }

    // 胜利时自动将所有未标旗的雷标上旗,不泄露雷位
    private void autoFlagAllMines() {
        long left   = adapter.getLeftBound();
        long right  = adapter.getRightBound();
        long top    = adapter.getTopBound();
        long bottom = adapter.getBottomBound();
        for (long c = left; c <= right; c++) {
            for (long r = top; r <= bottom; r++) {
                int ci = (int) c;
                int ri = (int) r;
                long id = TileCoreService.getTileId(ci, ri);
                if (!flagged.contains(id) && !revealed.contains(id) && isMine(ci, ri)) {
                    flagged.add(id);
                }
            }
        }
    }

    // AI探索:全盘扫描找最近的未翻开+未标旗格。找不到返回-1。
    private long findNearestUnrevealedCell(int fromCol, int fromRow) {
        long left   = adapter.getLeftBound();
        long right  = adapter.getRightBound();
        long top    = adapter.getTopBound();
        long bottom = adapter.getBottomBound();
        long bestId = -1;
        double minDist = Double.MAX_VALUE;
        for (long c = left; c <= right; c++) {
            for (long r = top; r <= bottom; r++) {
                int ci = (int) c;
                int ri = (int) r;
                long id = TileCoreService.getTileId(ci, ri);
                if (!revealed.contains(id) && !flagged.contains(id)) {
                    double dist = (c - fromCol) * (c - fromCol) + (r - fromRow) * (r - fromRow);
                    if (dist < minDist) {
                        minDist = dist;
                        bestId = id;
                    }
                }
            }
        }
        return bestId;
    }

    private void toggleFlag(int column, int row) {
        if (gameOver) return;
        long id = TileCoreService.getTileId(column, row);
        if (revealed.contains(id)) return;

        if (flagged.contains(id)) {
            flagged.remove(id);
        } else {
            flagged.add(id);
        }
        tileView.update(column, row);
    }

    private void updateRangeForSet(Set<Long> set) {
        if (set.isEmpty()) return;
        int minC = Integer.MAX_VALUE;
        int minR = Integer.MAX_VALUE;
        int maxC = Integer.MIN_VALUE;
        int maxR = Integer.MIN_VALUE;
        for (long id : set) {
            int c = TileCoreService.getColumn(id);
            int r = TileCoreService.getRow(id);
            if (c < minC) minC = c;
            if (r < minR) minR = r;
            if (c > maxC) maxC = c;
            if (r > maxR) maxR = r;
        }
        tileView.updateRange(minC - 1, minR - 1, maxC + 1, maxR + 1);
    }

    // ========== AI ==========

    // Material 3 结果对话框,持久展示供挂机场景查看
    private void showResultDialog(String title, String message) {
        if (isFinishing() || isDestroyed()) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("重新开始", (DialogInterface d, int w) -> {
                    stopAI();
                    restartGame();
                })
                .setNeutralButton("关闭", null)
                .setCancelable(false)
                .show();
    }

    private void startAI() {
        if (aiPlaying) return;
        aiPlaying = true;
        aiRunning = true;
        flagMode = false;
        aiExploreAttempts = 0;

        if (gameOver) {
            restartGame();
        }

        if (firstClick) {
            aiCurrentCol = tileView.findColumn(tileView.getWidth() / 2f);
            aiCurrentRow = tileView.findRow(tileView.getHeight() / 2f);
            reveal(aiCurrentCol, aiCurrentRow);
        } else {
            if (!revealed.isEmpty()) {
                long sumC = 0, sumR = 0;
                for (long id : revealed) {
                    sumC += TileCoreService.getColumn(id);
                    sumR += TileCoreService.getRow(id);
                }
                aiCurrentCol = (int) (sumC / revealed.size());
                aiCurrentRow = (int) (sumR / revealed.size());
            } else {
                aiCurrentCol = 0;
                aiCurrentRow = 0;
            }
        }

        startCameraFollow(aiCurrentCol, aiCurrentRow);
        scheduleNextAIMove();
        invalidateOptionsMenu();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void stopAI() {
        aiPlaying = false;
        aiRunning = false; 
        aiHandler.removeCallbacksAndMessages(null);
        if (cameraAnimator != null) {
            cameraAnimator.cancel();
            cameraAnimator = null;
        }
        invalidateOptionsMenu();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void scheduleNextAIMove() {
        if (!aiPlaying) return;
        aiHandler.postDelayed(this::executeAIMove, 500); 
    }

    private void executeAIMove() {
        if (!aiPlaying || gameOver) {
            stopAI();
            return;
        }

        final BoardSnapshot snapshot = getBoardSnapshot(aiCurrentCol, aiCurrentRow);

        aiExecutor.execute(() -> {
            if (!aiRunning) return;
            
            MinesweeperSolver solver = new MinesweeperSolver();
            final MinesweeperSolver.Step step = solver.solve(snapshot);
            
            aiHandler.post(() -> {
                if (!aiPlaying || gameOver) {
                    stopAI();
                    return;
                }
                
                if (step == null || step.actions.isEmpty()) {
                    // 非伪无限模式:全盘扫描找最近未翻开格,直接翻开探索
                    if (!isMaxMode()) {
                        long nextId = findNearestUnrevealedCell(aiCurrentCol, aiCurrentRow);
                        if (nextId == -1) {
                            // 全盘无未翻开安全格 → 胜利(双保险,reveal()中已有主判定)
                            stopAI();
                            showResultDialog("🎉 AI 已完成!", "AI 已成功完成扫雷");
                            return;
                        }
                        aiExploreAttempts = 0;
                        int nx = TileCoreService.getColumn(nextId);
                        int ny = TileCoreService.getRow(nextId);
                        // 直接翻开探索:若为雷则gameOver,若安全则获取新信息
                        reveal(nx, ny);
                        aiCurrentCol = nx;
                        aiCurrentRow = ny;
                        if (gameOver) {
                            stopAI();
                            return;
                        }
                        startCameraFollow(aiCurrentCol, aiCurrentRow);
                        scheduleNextAIMove();
                        return;
                    }

                    aiExploreAttempts++;
                    if (aiExploreAttempts > MAX_EXPLORE_ATTEMPTS) {
                        stopAI();
                        showResultDialog("😵 AI 投降了", "AI 遇到复杂局面,无法继续推理");
                        return;
                    }

                    // 伪无限模式:随机选择一个方向跳出当前区域继续探索
                    Random rand = new Random();
                    int[] dir = directions[rand.nextInt(8)];
                    // 使用 long 防止溢出,以适配器边界为物理边界向内收缩贴边
                    long targetCol = (long) aiCurrentCol + dir[0] * 20L;
                    long targetRow = (long) aiCurrentRow + dir[1] * 20L;
                    aiCurrentCol = (int) Math.max((long) adapter.getLeftBound(), Math.min((long) adapter.getRightBound(), targetCol));
                    aiCurrentRow = (int) Math.max((long) adapter.getTopBound(), Math.min((long) adapter.getBottomBound(), targetRow));

                    startCameraFollow(aiCurrentCol, aiCurrentRow);
                    scheduleNextAIMove();
                    return;
                }

                // 只要有动作,就重置探索计数
                aiExploreAttempts = 0;

                Set<Long> updateSet = new HashSet<>();
                boolean hasReveal = false;
                int revealC = 0, revealR = 0;

                for (MinesweeperSolver.Action action : step.actions) {
                    long id = action.targetId;
                    int c = TileCoreService.getColumn(id);
                    int r = TileCoreService.getRow(id);
                    
                    if (action.type == MinesweeperSolver.Action.Type.FLAG) {
                        if (!revealed.contains(id) && !flagged.contains(id)) {
                            flagged.add(id);
                            updateSet.add(id);
                        }
                    } else if (action.type == MinesweeperSolver.Action.Type.UNFLAG) {
                        if (flagged.contains(id)) {
                            flagged.remove(id);
                            updateSet.add(id);
                        }
                    } else if (action.type == MinesweeperSolver.Action.Type.REVEAL) {
                        revealC = c;
                        revealR = r;
                        hasReveal = true;
                        break; 
                    }
                }

                if (!updateSet.isEmpty()) {
                    updateRangeForSet(updateSet);
                    long firstId = step.actions.get(0).targetId;
                    aiCurrentCol = TileCoreService.getColumn(firstId);
                    aiCurrentRow = TileCoreService.getRow(firstId);
                }

                if (hasReveal) {
                    reveal(revealC, revealR);
                    aiCurrentCol = revealC;
                    aiCurrentRow = revealR;
                }

                // 无实际进展(所有动作均为no-op):计数防止死循环
                if (updateSet.isEmpty() && !hasReveal) {
                    aiExploreAttempts++;
                    if (aiExploreAttempts > MAX_EXPLORE_ATTEMPTS) {
                        stopAI();
                        showResultDialog("😵 AI 投降了", "AI 遇到复杂局面,无法继续推理");
                        return;
                    }
                }

                startCameraFollow(aiCurrentCol, aiCurrentRow);
                scheduleNextAIMove();
            });
        });
    }

    private BoardSnapshot getBoardSnapshot(int centerCol, int centerRow) {
        Map<Long, Integer> cells = new HashMap<>();
        int radius = 15;

        // 使用 long 防止溢出,以适配器边界为物理边界向内收缩
        int startCol = (int) Math.max((long) centerCol - radius, (long) adapter.getLeftBound());
        int endCol   = (int) Math.min((long) centerCol + radius, (long) adapter.getRightBound());
        int startRow = (int) Math.max((long) centerRow - radius, (long) adapter.getTopBound());
        int endRow   = (int) Math.min((long) centerRow + radius, (long) adapter.getBottomBound());

        // 使用 long 循环计数器,防止 int 在 Integer.MAX_VALUE 处溢出回绕死循环
        for (long c = startCol; c <= endCol; c++) {
            for (long r = startRow; r <= endRow; r++) {
                int ci = (int) c;
                int ri = (int) r;
                long id = TileCoreService.getTileId(ci, ri);
                if (flagged.contains(id)) {
                    cells.put(id, -2);
                } else if (revealed.contains(id)) {
                    if (isMine(ci, ri)) {
                        cells.put(id, -3); 
                    } else {
                        cells.put(id, countMinesAround(ci, ri));
                    }
                } else {
                    cells.put(id, -1); 
                }
            }
        }
        return new BoardSnapshot(cells, MINE_DENSITY, centerCol, centerRow,
                adapter.getLeftBound(), adapter.getTopBound(),
                adapter.getRightBound(), adapter.getBottomBound());
    }

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

    // ========== 菜单 ==========

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(Menu.NONE, MENU_ID_RESTART, Menu.NONE, "重新开始")
                .setOnMenuItemClickListener(this)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(Menu.NONE, MENU_ID_TOGGLE_FLAG, Menu.NONE, "标记模式")
                .setCheckable(true)
                .setOnMenuItemClickListener(this)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(Menu.NONE, MENU_ID_JUMP_CENTER, Menu.NONE, "回到原点")
                .setOnMenuItemClickListener(this)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(Menu.NONE, MENU_ID_AI_PLAY, Menu.NONE, "看AI玩")
                .setCheckable(true)
                .setOnMenuItemClickListener(this)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        MenuItem flagItem = menu.findItem(MENU_ID_TOGGLE_FLAG);
        if (flagItem != null) flagItem.setChecked(flagMode);
        MenuItem aiItem = menu.findItem(MENU_ID_AI_PLAY);
        if (aiItem != null) aiItem.setChecked(aiPlaying);
        return true;
    }

    @Override
    public boolean onMenuItemClick(MenuItem menuItem) {
        int id = menuItem.getItemId();
        if (id == MENU_ID_RESTART) {
            stopAI();
            restartGame();
            return true;
        }
        if (id == MENU_ID_TOGGLE_FLAG) {
            if (aiPlaying) {
                showToast("AI 游戏中不可切换标记模式");
                return true;
            }
            flagMode = !flagMode;
            showToast(flagMode ? "标记模式:开启" : "标记模式:关闭");
            return true;
        }
        if (id == MENU_ID_JUMP_CENTER) {
            tileView.seek(0, 0);
            return true;
        }
        if (id == MENU_ID_AI_PLAY) {
            if (aiPlaying) {
                stopAI();
                showToast("AI 已停止");
            } else {
                startAI();
                showToast("AI 开始游戏");
            }
            return true;
        }
        return super.onMenuItemClick(menuItem);
    }

    @Override
    protected void onDebugModeChanged(boolean enabled) {
        tileView.setDebugMode(enabled);
    }

    @Override
    public boolean hasMaxMode() {
        return true;
    }

    @Override
    public boolean hasPlanMode() {
        return false;
    }

    @Override
    protected ToTheEnd onInitToTheEnd() {
        return new ToTheEnd() {
            @Override public int getLeftBound() { return adapter.getLeftBound(); }
            @Override public int getTopBound() { return adapter.getTopBound(); }
            @Override public int getRightBound() { return adapter.getRightBound(); }
            @Override public int getBottomBound() { return adapter.getBottomBound(); }
            @Override public void gogogo(int column, int row) { tileView.seek(column, row); }
        };
    }

    // ========== TileHolder ==========

    public class MineTileHolder extends TileView.TileHolder {

        private int currentState = -1;

        public MineTileHolder() {
        }

        @Override
        public void onInWindow() {
            super.onInWindow();
            updateContent();
        }

        @Override
        public void onSizeChanged(int width, int height) {
            super.onSizeChanged(width, height);
            updateContent();
        }

        private void updateContent() {
            int c = getColumn();
            int r = getRow();
            long id = TileCoreService.getTileId(c, r);

            boolean isRevealed = revealed.contains(id);
            boolean isFlagged = flagged.contains(id);
            boolean isExploded = exploded.contains(id);
            boolean isMine = isMine(c, r);

            if (isExploded) {
                currentState = IMG_EXPLODED;
            } else if (gameOver) {
                if (gameWon) {
                    // 胜利:不暴露雷位,已翻开正常显示,未翻开显示旗标
                    if (isRevealed) {
                        int mines = countMinesAround(c, r);
                        currentState = mines == 0 ? IMG_EMPTY : IMG_NUM_1 + mines - 1;
                    } else if (isFlagged) {
                        currentState = IMG_FLAG;
                    } else {
                        // 防御:胜利时所有未翻开格应已被自动标旗
                        currentState = IMG_NORMAL;
                    }
                } else {
                    if (isMine) {
                        if (isFlagged) {
                            currentState = IMG_FLAG_MINE;
                        } else {
                            currentState = IMG_MINE;
                        }
                    } else {
                        if (isFlagged) {
                            currentState = IMG_WRONG_FLAG;
                        } else if (!isRevealed) {
                            currentState = IMG_NORMAL;
                        } else {
                            int mines = countMinesAround(c, r);
                            currentState = mines == 0 ? IMG_EMPTY : IMG_NUM_1 + mines - 1;
                        }
                    }
                }
            } else {
                if (isRevealed) {
                    int mines = countMinesAround(c, r);
                    if (mines == 0) {
                        currentState = IMG_EMPTY;
                    } else {
                        currentState = IMG_NUM_1 + mines - 1;
                    }
                } else {
                    if (isFlagged) {
                        currentState = IMG_FLAG;
                    } else {
                        currentState = IMG_NORMAL;
                    }
                }
            }
        }

        @Override
        public void draw(Canvas canvas) {
            if (currentState != -1 && tileBitmaps != null) {
                Bitmap bmp = tileBitmaps[currentState];
                if (bmp != null && !bmp.isRecycled()) {
                    canvas.drawBitmap(bmp, 0, 0, null);
                }
            }
        }

        @Override
        public boolean onClick() {
            if (aiPlaying) return true;
            int c = getColumn();
            int r = getRow();
            if (flagMode) {
                toggleFlag(c, r);
            } else {
                reveal(c, r);
            }
            return true;
        }

        @Override
        public void onLongClick() {
            if (aiPlaying) return;
            int c = getColumn();
            int r = getRow();
            toggleFlag(c, r);
        }
    }

    // ========== Adapter ==========

    private class MineAdapter extends TileView.Adapter {
        @Override
        public int getLeftBound() {
            return isMaxMode() ? Integer.MIN_VALUE : -30;
        }

        @Override
        public int getTopBound() {
            return isMaxMode() ? Integer.MIN_VALUE : -30;
        }

        @Override
        public int getRightBound() {
            return isMaxMode() ? Integer.MAX_VALUE : 30;
        }

        @Override
        public int getBottomBound() {
            return isMaxMode() ? Integer.MAX_VALUE : 30;
        }

        @Override
        public TileView.TileHolder onCreateTileHolder(int type) {
            return new MineTileHolder();
        }

        @Override
        public void onBindTileHolder(TileView.TileHolder holder, int column, int row) {
            ((MineTileHolder) holder).updateContent();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        aiPlaying = false;
        aiHandler.removeCallbacksAndMessages(null);
        if (cameraAnimator != null) {
            cameraAnimator.cancel();
            cameraAnimator = null;
        }
        tileView.setAdapter(null);
        aiExecutor.shutdownNow();
        
        if (tileBitmaps != null) {
            for (Bitmap b : tileBitmaps) {
                if (b != null) b.recycle();
            }
            tileBitmaps = null;
        }
    }
}
