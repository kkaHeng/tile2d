package com.ahheng.tile2d.app.auto;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import com.ahheng.tile2d.TileCoreService;
import com.ahheng.tile2d.app.BaseActivity;
import com.ahheng.tile2d.widget.layout.TileLayout;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class AutoTileActivity extends BaseActivity {

    private static final String ASSET_PATH = "dirt_tileset.png";
    private static final int TYPE_EMPTY = 0;
    private static final int PIECE_COLS = 4;
    private static final int PIECE_ROWS = 4;
    private static final int MENU_ID_DRAG = 4;

    private TileLayout tileLayout;
    private FrameLayout animationLayer;
    private TileSet tileSet;
    private ConnectionRule connectionRule;
    private final Set<Long> placedTiles = new HashSet<>();
    private final Set<Long> pendingEnterAnimations = new HashSet<>();
    private final Set<Long> draggedTiles = new HashSet<>();
    private final Random random = new Random();

    private boolean dragMode = false;
    private int lastDragCol = Integer.MIN_VALUE;
    private int lastDragRow = Integer.MIN_VALUE;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int size = dp2px(40);
        tileSet = new TileSet(this, ASSET_PATH, size);
        connectionRule = new SimpleConnectionRule();
        FrameLayout root = new FrameLayout(this) {
            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                if (dragMode) {
                    int[] loc = new int[2];
                    tileLayout.getLocationOnScreen(loc);
                    float viewX = ev.getRawX() - loc[0];
                    float viewY = ev.getRawY() - loc[1];

                    int col = tileLayout.findColumn(viewX);
                    int row = tileLayout.findRow(viewY);
                    long id = TileCoreService.getTileId(col, row);

                    switch (ev.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            draggedTiles.clear();
                            draggedTiles.add(id);
                            lastDragCol = col;
                            lastDragRow = row;
                            onTileClick(col, row);
                            return true;

                        case MotionEvent.ACTION_MOVE:
                            if (col != lastDragCol || row != lastDragRow) {
                                lastDragCol = col;
                                lastDragRow = row;
                                if (!draggedTiles.contains(id)) {
                                    draggedTiles.add(id);
                                    onTileClick(col, row);
                                }
                            }
                            return true;

                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            draggedTiles.clear();
                            lastDragCol = Integer.MIN_VALUE;
                            lastDragRow = Integer.MIN_VALUE;
                            return true;
                    }
                    return true;
                }
                return super.dispatchTouchEvent(ev);
            }
        };

        tileLayout = new TileLayout(this);
        animationLayer = new FrameLayout(this);
        animationLayer.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        animationLayer.setClickable(false);

        root.addView(tileLayout, new FrameLayout.LayoutParams(-1, -1));
        root.addView(animationLayer, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root, new ViewGroup.LayoutParams(-1, -1));

        tileLayout.setDebugMode(isDebugMode());
        tileLayout.setDefaultTileWidth(size);
        tileLayout.setDefaultTileHeight(size);

        tileLayout.setAdapter(new AutoTileAdapter());

        presetTiles();
        tileLayout.seek(-4, -8);
    }

    private void presetTiles() {
        placedTiles.add(TileCoreService.getTileId(0, 0));
        placedTiles.add(TileCoreService.getTileId(1, 0));
        placedTiles.add(TileCoreService.getTileId(0, 1));
        placedTiles.add(TileCoreService.getTileId(1, 1));
        refreshArea(0, 0);
    }

    private void onTileClick(int column, int row) {
        long id = TileCoreService.getTileId(column, row);
        if (placedTiles.contains(id)) {
            playBreakAnimation(column, row);
            placedTiles.remove(id);
        } else {
            placedTiles.add(id);
            pendingEnterAnimations.add(id);
        }
        refreshArea(column, row);
    }

    private void refreshArea(int centerColumn, int centerRow) {
        for (int c = centerColumn - 1; c <= centerColumn + 1; c++) {
            for (int r = centerRow - 1; r <= centerRow + 1; r++) {
                tileLayout.update(c, r);
                if (r == centerRow + 1) break;
            }
            if (c == centerColumn + 1) break;
        }
    }

    private void playBreakAnimation(int column, int row) {
        TileLayout.TileHolder holder = tileLayout.getActiveTile(column, row);
        if (holder == null || !(holder.itemView instanceof ImageView)) {
            return;
        }

        BitmapDrawable drawable = (BitmapDrawable) ((ImageView) holder.itemView).getDrawable();
        if (drawable == null) return;
        Bitmap source = drawable.getBitmap();

        float tileX = tileLayout.getTileX(column);
        float tileY = tileLayout.getTileY(row);

        int[] tileLoc = new int[2];
        int[] animLoc = new int[2];
        tileLayout.getLocationInWindow(tileLoc);
        animationLayer.getLocationInWindow(animLoc);

        float baseX = (tileLoc[0] - animLoc[0]) + tileX;
        float baseY = (tileLoc[1] - animLoc[1]) + tileY;

        int pieceW = source.getWidth() / PIECE_COLS;
        int pieceH = source.getHeight() / PIECE_ROWS;

        for (int py = 0; py < PIECE_ROWS; py++) {
            for (int px = 0; px < PIECE_COLS; px++) {
                Bitmap piece = Bitmap.createBitmap(source, px * pieceW, py * pieceH, pieceW, pieceH);

                ImageView pieceView = new ImageView(this);
                pieceView.setImageBitmap(piece);
                pieceView.setLayoutParams(new FrameLayout.LayoutParams(pieceW, pieceH));
                animationLayer.addView(pieceView);

                float startX = baseX + px * pieceW;
                float startY = baseY + py * pieceH;
                pieceView.setX(startX);
                pieceView.setY(startY);

                float vx = (random.nextFloat() - 0.5f) * dpTopx(500);
                float vy = -random.nextFloat() * dpTopx(400) - dpTopx(100);
                float gravity = dpTopx(1200);

                ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
                animator.setDuration(700);
                animator.addUpdateListener(anim -> {
                    float t = anim.getAnimatedFraction() * 0.7f;
                    float cx = startX + vx * t;
                    float cy = startY + vy * t + 0.5f * gravity * t * t;
                    pieceView.setX(cx);
                    pieceView.setY(cy);
                    pieceView.setAlpha(Math.max(0f, 1f - anim.getAnimatedFraction() * 1.5f));
                });
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        animationLayer.removeView(pieceView);
                        piece.recycle();
                    }
                });
                animator.start();
            }
        }
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

    private void setDragMode(boolean enabled) {
        dragMode = enabled;
        invalidateOptionsMenu();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(Menu.NONE, MENU_ID_DRAG, Menu.NONE, "拖动绘制")
                .setCheckable(true)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        MenuItem item = menu.findItem(MENU_ID_DRAG);
        if (item != null) item.setChecked(dragMode);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == MENU_ID_DRAG) {
            setDragMode(!dragMode);
            showToast("拖动绘制: " + (dragMode ? "开启" : "关闭"));
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

    private class AutoTileAdapter extends TileLayout.Adapter {

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
                View emptyView = new View(AutoTileActivity.this);
                emptyView.setLayoutParams(new ViewGroup.LayoutParams(-2, -2));
                emptyView.setClickable(true);
                return new TileLayout.TileHolder(emptyView);
            }

            ImageView imageView = new ImageView(AutoTileActivity.this);
            imageView.setImageBitmap(tileSet.getTile(type));
            imageView.setLayoutParams(new ViewGroup.LayoutParams(-2, -2));
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            imageView.setColorFilter(null);
            return new TileLayout.TileHolder(imageView);
        }

        @Override
        public void onBindTileHolder(TileLayout.TileHolder holder, int column, int row) {
            long id = TileCoreService.getTileId(column, row);
            holder.itemView.setOnClickListener(v -> onTileClick(column, row));

            if (holder.getType() != TYPE_EMPTY && pendingEnterAnimations.remove(id)) {
                playEnterAnimation(holder.itemView);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        for (int i = animationLayer.getChildCount() - 1; i >= 0; i--) {
            View child = animationLayer.getChildAt(i);
            child.animate().cancel();
            if (child instanceof ImageView iv) {
                if (iv.getDrawable() instanceof BitmapDrawable) {
                    Bitmap bmp = ((BitmapDrawable) iv.getDrawable()).getBitmap();
                    if (bmp != null && !bmp.isRecycled()) bmp.recycle();
                }
            }
            animationLayer.removeView(child);
        }
        for (int i = tileLayout.getChildCount() - 1; i >= 0; i--) {
            tileLayout.getChildAt(i).animate().cancel();
        }
        tileLayout.setAdapter(null);
    }

}
