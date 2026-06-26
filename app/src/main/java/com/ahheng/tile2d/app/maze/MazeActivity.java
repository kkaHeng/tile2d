package com.ahheng.tile2d.app.maze;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import com.ahheng.tile2d.TileCoreService;
import com.ahheng.tile2d.TileLayoutModel;
import com.ahheng.tile2d.app.BaseActivity;
import com.ahheng.tile2d.app.auto.ConnectionRule;
import com.ahheng.tile2d.app.auto.SimpleConnectionRule;
import com.ahheng.tile2d.app.auto.TileSet;
import com.ahheng.tile2d.widget.layout.TileLayout;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class MazeActivity extends BaseActivity {

    private static final String ASSET_PATH = "dirt_tileset.png";
    private static final int TYPE_EMPTY = 0;
    private static final int MENU_MAZE = 5;

    private static final int MAZE_STEP_MS = 20;
    private static final int MAZE_BACKTRACK_MS = 5;
    private static final int[][] MAZE_DIRS = {{0, -2}, {0, 2}, {-2, 0}, {2, 0}};
    private static final int[][] MAZE_WALLS = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};

    private static final int MAZE_DIM = 51;
    private static final int TOTAL_PATH_CELLS = MAZE_DIM * MAZE_DIM;

    private static final float CAMERA_LERP = 0.05f;

    private TileLayout tileLayout;
    private MazeAdapter adapter;
    private TileSet tileSet;
    private ConnectionRule connectionRule;
    private final Set<Long> placedTiles = new HashSet<>();
    private final Set<Long> pendingEnterAnimations = new HashSet<>();
    private final Random random = new Random();

    private boolean mazeMode = false;
    private Handler mazeHandler;
    private final Set<Long> mazeVisited = new HashSet<>();
    private final ArrayDeque<int[]> mazeStack = new ArrayDeque<>();
    private LinearProgressIndicator progressIndicator;

    private int targetFollowCol = 0;
    private int targetFollowRow = 0;

    private Choreographer choreographer;
    private final Choreographer.FrameCallback cameraFrameCallback = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            if (!mazeMode || isFinishing()) return;

            int tileSize = tileLayout.getDefaultTileWidth();
            TileLayoutModel model = tileLayout.getLayoutModel();

            float targetPixelX = model.offsetX + (targetFollowCol - model.colStart) * tileSize;
            float targetPixelY = model.offsetY + (targetFollowRow - model.rowStart) * tileSize;

            float centerX = tileLayout.getWidth() / 2f - tileSize / 2f;
            float centerY = tileLayout.getHeight() / 2f - tileSize / 2f;

            float dx = centerX - targetPixelX;
            float dy = centerY - targetPixelY;

            if (Math.abs(dx) > 0.5f || Math.abs(dy) > 0.5f) {
                tileLayout.offset(dx * CAMERA_LERP, dy * CAMERA_LERP);
            }

            choreographer.postFrameCallback(this);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int size = dp2px(40);
        tileSet = new TileSet(this, ASSET_PATH, size);
        connectionRule = new SimpleConnectionRule();

        FrameLayout root = new FrameLayout(this);

        tileLayout = new TileLayout(this);
        tileLayout.setDebugMode(isDebugMode());
        tileLayout.setDefaultTileWidth(size);
        tileLayout.setDefaultTileHeight(size);

        progressIndicator = new LinearProgressIndicator(this);
        progressIndicator.setIndeterminate(false);
        progressIndicator.setMax(TOTAL_PATH_CELLS);
        progressIndicator.setProgress(0);
        progressIndicator.setVisibility(View.GONE);
        FrameLayout.LayoutParams progressLp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        progressLp.bottomMargin = dp2px(4);
        progressIndicator.setLayoutParams(progressLp);

        root.addView(tileLayout, new FrameLayout.LayoutParams(-1, -1));
        root.addView(progressIndicator, progressLp);
        setContentView(root, new ViewGroup.LayoutParams(-1, -1));

        tileLayout.setAdapter((adapter = new MazeAdapter()));

        choreographer = Choreographer.getInstance();

        tileLayout.post(() -> {
            if (isFinishing()) return;
            tileLayout.seek(0, 0);
            setMazeMode(true);
        });
    }

    private void setMazeMode(boolean enabled) {
        mazeMode = enabled;
        invalidateOptionsMenu();
        if (enabled) {
            startGenerateMaze();
            startCameraFollow();
        } else {
            if (mazeHandler != null) mazeHandler.removeCallbacks(mazeRunnable);
            stopCameraFollow();
            progressIndicator.setVisibility(View.GONE);
        }
    }

    private void startGenerateMaze() {
        if (!mazeMode) return;
        placedTiles.clear();
        mazeVisited.clear();
        mazeStack.clear();
        tileLayout.updateAll();
        progressIndicator.setProgress(0);
        progressIndicator.setVisibility(View.VISIBLE);

        long startId = TileCoreService.getTileId(0, 0);
        placedTiles.add(startId);
        mazeVisited.add(startId);
        mazeStack.push(new int[]{0, 0});

        targetFollowCol = 0;
        targetFollowRow = 0;
        refreshArea(0, 0);

        if (mazeHandler == null) mazeHandler = new Handler(Looper.getMainLooper());
        mazeHandler.removeCallbacks(mazeRunnable);
        mazeHandler.post(mazeRunnable);
    }

    private void startCameraFollow() {
        choreographer.removeFrameCallback(cameraFrameCallback);
        choreographer.postFrameCallback(cameraFrameCallback);
    }

    private void stopCameraFollow() {
        choreographer.removeFrameCallback(cameraFrameCallback);
    }

    private final Runnable mazeRunnable = new Runnable() {
        @Override
        public void run() {
            if (!mazeMode) return;
            if (mazeStack.isEmpty()) {
                progressIndicator.setProgress(TOTAL_PATH_CELLS);
                progressIndicator.setVisibility(View.GONE);
                showToast("迷宫生成完毕");
                setMazeMode(false);
                return;
            }

            int[] curr = mazeStack.pop();
            int c = curr[0];
            int r = curr[1];

            int[] order = {0, 1, 2, 3};
            for (int i = 3; i > 0; i--) {
                int j = random.nextInt(i + 1);
                int tmp = order[i];
                order[i] = order[j];
                order[j] = tmp;
            }

            int focusCol = c;
            int focusRow = r;
            boolean found = false;

            for (int i = 0; i < 4; i++) {
                int dirIdx = order[i];
                int nc = c + MAZE_DIRS[dirIdx][0];
                int nr = r + MAZE_DIRS[dirIdx][1];
                if (nc < -50 || nc > 50 || nr < -50 || nr > 50) continue;

                long nid = TileCoreService.getTileId(nc, nr);
                if (!mazeVisited.contains(nid)) {
                    int wc = c + MAZE_WALLS[dirIdx][0];
                    int wr = r + MAZE_WALLS[dirIdx][1];
                    long wid = TileCoreService.getTileId(wc, wr);
                    placedTiles.add(wid);
                    pendingEnterAnimations.add(wid);

                    placedTiles.add(nid);
                    mazeVisited.add(nid);
                    pendingEnterAnimations.add(nid);

                    mazeStack.push(curr);
                    mazeStack.push(new int[]{nc, nr});

                    refreshArea(wc, wr);
                    focusCol = nc;
                    focusRow = nr;
                    found = true;
                    break;
                }
            }

            targetFollowCol = focusCol;
            targetFollowRow = focusRow;
            progressIndicator.setProgress(mazeVisited.size());

            long nextDelay = found ? MAZE_STEP_MS : MAZE_BACKTRACK_MS;
            mazeHandler.postDelayed(this, nextDelay);
        }
    };

    private void refreshArea(int centerColumn, int centerRow) {
        int left = centerColumn > adapter.getLeftBound() ? centerColumn - 1 : centerColumn;
        int top = centerRow > adapter.getTopBound() ? centerRow - 1 : centerRow;
        int right = centerColumn < adapter.getRightBound() ? centerColumn + 1 : centerColumn;
        int bottom = centerRow < adapter.getBottomBound() ? centerRow + 1 : centerRow;
        tileLayout.updateRange(left, top, right, bottom);
    }

    private void playEnterAnimation(View view) {
        view.setScaleX(0f);
        view.setScaleY(0f);
        view.setAlpha(0f);
        view.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(250)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.8f))
                .start();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(Menu.NONE, MENU_MAZE, Menu.NONE, "迷宫模式")
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        MenuItem item = menu.findItem(MENU_MAZE);
        if (item != null) item.setTitle(mazeMode ? "停止生成" : "重新开始");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == MENU_MAZE) {
            setMazeMode(!mazeMode);
            showToast(mazeMode ? "开始生成迷宫" : "停止生成");
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDebugModeChanged(boolean enabled) {
        tileLayout.setDebugMode(enabled);
    }

    @Override
    public boolean hasPlanMode() {
        return false;
    }

    @Override
    protected void onMaxModeChanged(boolean maxMode) {
        super.onMaxModeChanged(maxMode);
        tileLayout.snap();
    }

    private class MazeAdapter extends TileLayout.Adapter {

        @Override
        public int getLeftBound() {
            return isMaxMode() ? Integer.MIN_VALUE : -50;
        }

        @Override
        public int getTopBound() {
            return isMaxMode() ? Integer.MIN_VALUE : -50;
        }

        @Override
        public int getRightBound() {
            return isMaxMode() ? Integer.MAX_VALUE : 50;
        }

        @Override
        public int getBottomBound() {
            return isMaxMode() ? Integer.MAX_VALUE : 50;
        }

        @Override
        public int getTileType(int column, int row) {
            try {
                if (!placedTiles.contains(TileCoreService.getTileId(column, row))) {
                    return TYPE_EMPTY;
                }
                return connectionRule.resolve(column, row, placedTiles);
            } catch (Exception e) {
                showToast(e.getMessage());
                return TYPE_EMPTY;
            }
        }

        @Override
        public TileLayout.TileHolder onCreateTileHolder(int type) {
            if (type == TYPE_EMPTY) {
                return null;
            }
            ImageView imageView = new ImageView(MazeActivity.this);
            imageView.setImageBitmap(tileSet.getTile(type));
            imageView.setLayoutParams(new ViewGroup.LayoutParams(-2, -2));
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            imageView.setColorFilter(null);
            return new TileLayout.TileHolder(imageView);
        }

        @Override
        public void onBindTileHolder(TileLayout.TileHolder holder, int column, int row) {
            long id = TileCoreService.getTileId(column, row);
            if (holder.getType() != TYPE_EMPTY && pendingEnterAnimations.remove(id)) {
                playEnterAnimation(holder.itemView);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mazeMode = false;
        if (mazeHandler != null) {
            mazeHandler.removeCallbacksAndMessages(null);
        }
        if (choreographer != null) {
            choreographer.removeFrameCallback(cameraFrameCallback);
        }
        tileLayout.setAdapter(null);
    }

}
