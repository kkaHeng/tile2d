package com.ahheng.tile2d.app.noise;

import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ahheng.tile2d.TileCoreService;
import com.ahheng.tile2d.TileLayoutModel;
import com.ahheng.tile2d.app.BaseActivity;
import com.ahheng.tile2d.widget.canvas.TileView;
import com.ahheng.tile2d.widget.layout.TileLayout;

import java.util.HashSet;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

public class TileLayoutActivity extends BaseActivity {

    private static final int MENU_ID_RANDOM_WIDTH = nextId();
    private static final int MENU_ID_RANDOM_HEIGHT = nextId();

    private TileLayout layout;
    private RandomAdapter adapter;
    private boolean displayText = false;

    private PerlinNoise2D perlinNoise;
    private ColorGenerator colorGenerator;

    private final Set<Long> removedTiles = new HashSet<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        perlinNoise = new PerlinNoise2D(123456789L);
        colorGenerator = new ColorGenerator();

        layout = new TileLayout(this);
        setContentView(layout, new ViewGroup.LayoutParams(-1, -1));
        int padding = dp2px(40);
        layout.setPadding(padding, padding, padding, padding);
        layout.setDebugMode(isDebugMode());
        layout.setAdapter((adapter = new RandomAdapter()));
        initTextPlan(true);
    }

    private void initColorPlan() {
        displayText = false;
        TileLayoutModel model = layout.getLayoutModel().newInstance();
        int size = dp2px(40);
        layout.setDefaultTileWidth(size);
        layout.setDefaultTileHeight(size);
        layout.setAdapter((adapter = new RandomAdapter()));
        layout.seek(model.colStart, model.rowStart, model.offsetX, model.offsetY);
    }

    private void initTextPlan(boolean first) {
        displayText = true;
        TileLayoutModel model = layout.getLayoutModel().newInstance();
        layout.setDefaultTileWidth(dp2px(80));
        layout.setDefaultTileHeight(dp2px(45));
        layout.setAdapter((adapter = new RandomAdapter()));
        if (first) {
            layout.seek(0, 0, 0, 0);
        } else {
            layout.seek(model.colStart, model.rowStart, model.offsetX, model.offsetY);
        }
    }

    @Override
    protected void onDebugModeChanged(boolean enabled) {
        layout.setDebugMode(enabled);
    }

    @Override
    protected void onMaxModeChanged(boolean maxMode) {
        super.onMaxModeChanged(maxMode);
        layout.snap();
    }

    private ValueAnimator widthAnimator;
    private ValueAnimator heightAnimator;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        boolean result = super.onCreateOptionsMenu(menu);
        menu.add(Menu.NONE, MENU_ID_RANDOM_WIDTH, Menu.NONE, "随机调整宽度")
                .setOnMenuItemClickListener(this)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(Menu.NONE, MENU_ID_RANDOM_HEIGHT, Menu.NONE, "随机调整高度")
                .setOnMenuItemClickListener(this)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        return result;
    }

    @Override
    public boolean onMenuItemClick(@NonNull MenuItem menuItem) {
        int id = menuItem.getItemId();
        if (id == MENU_ID_RANDOM_WIDTH) {
            if (widthAnimator != null) {
                widthAnimator.cancel();
            }
            int column = layout.findColumn(layout.getWidth() / 2f);
            int dp = (new Random().nextInt(19) + 4) * 10;
            int target = dp2px(dp);
            widthAnimator = ValueAnimator.ofInt(layout.getTileWidth(column), target);
            widthAnimator.setDuration(2000);
            widthAnimator.setInterpolator(new OvershootInterpolator());
            widthAnimator.addUpdateListener(a -> {
                int current = (int) a.getAnimatedValue();
                layout.setTileWidth(column, current, TileView.DIMEN_GRAVITY_CENTER);
            });
            widthAnimator.start();
            showToast(String.format(Locale.getDefault(), "调整第 %d 列宽度到 %ddp", column, dp));
        }
        if (id == MENU_ID_RANDOM_HEIGHT) {
            if (heightAnimator != null) {
                heightAnimator.cancel();
            }
            int row = layout.findRow(layout.getHeight() / 2f);
            int dp = (new Random().nextInt(7) + 3) * 10;
            int target = dp2px(dp);
            heightAnimator = ValueAnimator.ofInt(layout.getTileHeight(row), target);
            heightAnimator.setDuration(2000);
            heightAnimator.setInterpolator(new OvershootInterpolator());
            heightAnimator.addUpdateListener(a -> {
                int current = (int) a.getAnimatedValue();
                layout.setTileHeight(row, current, TileView.DIMEN_GRAVITY_CENTER);
            });
            heightAnimator.start();
            showToast(String.format(Locale.getDefault(), "调整第 %d 行高度到 %ddp", row, dp));
        }
        return super.onMenuItemClick(menuItem);
    }

    @Override
    protected void onPlanChanged(int plan) {
        super.onPlanChanged(plan);
        switch (plan) {
            case PLAN_COLOR -> initColorPlan();
            case PLAN_TEXT -> initTextPlan(false);
        }
    }

    @Override
    protected ToTheEnd onInitToTheEnd() {
        return new ToTheEnd() {
            @Override
            public int getLeftBound() {
                return adapter.getLeftBound();
            }
            @Override
            public int getTopBound() {
                return adapter.getTopBound();
            }
            @Override
            public int getRightBound() {
                return adapter.getRightBound();
            }
            @Override
            public int getBottomBound() {
                return adapter.getBottomBound();
            }
            @Override
            public void gogogo(int column, int row) {
                layout.seek(column, row);
            }
        };
    }

    public class ColorTileHolder extends TileLayout.TileHolder {
        int backgroundColor;
        double noise;

        private final TextView textView;

        public ColorTileHolder() {
            super(new TextView(TileLayoutActivity.this));
            textView = (TextView) itemView;
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            textView.setGravity(Gravity.CENTER);
            textView.setBackground(new GradientDrawable());
        }

        public void bind() {
            GradientDrawable bg = (GradientDrawable) textView.getBackground();
            bg.setColor(backgroundColor);
            bg.setStroke((int) Math.ceil(dpTopx(0.5f)), Color.GRAY);
            textView.setBackground(bg);
            if (displayText) {
                textView.setText(String.format(Locale.getDefault(), "%.2f", noise));
                textView.setTextColor(luminance(backgroundColor) > 0.40 ? Color.BLACK : Color.WHITE);
            } else {
                textView.setText("");
            }

            textView.setOnClickListener(v -> {
                showToast("单击了 " + getColumn() + "," + getRow());
            });
            textView.setOnLongClickListener(v -> {
                requestDisallowInterceptTouchEvent(true);
                removedTiles.add(TileCoreService.getTileId(getColumn(), getRow()));
                layout.update(getColumn(), getRow());
                return true;
            });
        }

        private static double luminance(int c) {
            double r = Color.red(c) / 255.0;
            double g = Color.green(c) / 255.0;
            double b = Color.blue(c) / 255.0;
            r = r <= 0.03928 ? r / 12.92 : Math.pow((r + 0.055) / 1.055, 2.4);
            g = g <= 0.03928 ? g / 12.92 : Math.pow((g + 0.055) / 1.055, 2.4);
            b = b <= 0.03928 ? b / 12.92 : Math.pow((b + 0.055) / 1.055, 2.4);
            return 0.2126 * r + 0.7152 * g + 0.0722 * b;
        }
    }

    private class RandomAdapter extends TileLayout.Adapter {
        @Override
        public int getTopBound() {
            return isMaxMode() ? Integer.MIN_VALUE : -100;
        }

        @Override
        public int getLeftBound() {
            return isMaxMode() ? Integer.MIN_VALUE : -50;
        }

        @Override
        public int getRightBound() {
            return isMaxMode() ? Integer.MAX_VALUE : 50;
        }

        @Override
        public int getBottomBound() {
            return isMaxMode() ? Integer.MAX_VALUE : 100;
        }

        @Override
        public int getTileType(int column, int row) {
            if (removedTiles.contains(TileCoreService.getTileId(column, row))) {
                return -1;
            }
            return perlinNoise.noiseNormalized(column * 0.03, row * 0.03) < 0.3 ? -1 : 0;
        }

        @Override
        public TileLayout.TileHolder onCreateTileHolder(int type) {
            if (type == -1) return null;
            return new ColorTileHolder();
        }

        @Override
        public void onBindTileHolder(TileLayout.TileHolder holder, int column, int row) {
            ColorTileHolder colorTileHolder = (ColorTileHolder) holder;
            double noise = perlinNoise.noiseNormalized(column * 0.03, row * 0.03);
            colorTileHolder.backgroundColor = colorGenerator.getColor((noise - 0.3) / 0.7);
            colorTileHolder.noise = noise / 0.03;
            colorTileHolder.bind();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (widthAnimator != null) {
            widthAnimator.cancel();
        }
        if (heightAnimator != null) {
            heightAnimator.cancel();
        }
        layout.setAdapter(null);
    }
}
