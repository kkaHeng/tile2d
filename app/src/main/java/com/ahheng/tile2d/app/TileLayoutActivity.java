package com.ahheng.tile2d.app;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.ahheng.tile2d.TileCoreService;
import com.ahheng.tile2d.TileLayoutModel;
import com.ahheng.tile2d.widget.layout.TileLayout;

import java.util.HashSet;
import java.util.Set;

public class TileLayoutActivity extends BaseActivity {

    private TileLayout layout;
    private RandomAdapter adapter;
    private boolean displayText = false;

    private PerlinNoise2D perlinNoise;
    private ColorGenerator colorGenerator;

    private Set<Long> removedTiles = new HashSet<>();

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

        Handler handler = new Handler(getMainLooper());
        handler.postDelayed(() -> {
            // 测试延迟调整宽度
            layout.setTileWidth(0, dp2px(160));
        }, 2000);
    }

    private void initColorPlan(boolean first) {
        displayText = false;
        TileLayoutModel model = layout.getLayoutModel();
        int size = dp2px(40);
        layout.setDefaultTileWidth(size);
        layout.setDefaultTileHeight(size);
        layout.setAdapter((adapter = new RandomAdapter()));
        if (first) {
            layout.seek(0, 0, 0, 0);
        } else {
            layout.seek(model.colStart, model.rowStart, model.offsetX, model.offsetY);
        }
    }

    private void initTextPlan(boolean first) {
        displayText = true;
        TileLayoutModel model = layout.getLayoutModel();
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

    @Override
    protected void onPlanChanged(int plan) {
        super.onPlanChanged(plan);
        switch (plan) {
            case PLAN_COLOR -> initColorPlan(false);
            case PLAN_TEXT -> initTextPlan(false);
        }
    }

    public class ColorTileHolder extends TileLayout.TileHolder {
        int backgroundColor;
        double noise;

        private TextView textView;

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
                textView.setText(String.format("%.2f", noise));
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

    private class RandomAdapter extends TileLayout.Adapter<ColorTileHolder> {
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
        public ColorTileHolder onCreateTileHolder(int type) {
            if (type == -1) return null;
            return new ColorTileHolder();
        }

        @Override
        public void onBindTileHolder(ColorTileHolder holder, int column, int row) {
            double noise = perlinNoise.noiseNormalized(column * 0.03, row * 0.03);
            holder.backgroundColor = colorGenerator.getColor((noise - 0.03) / 0.97);
            holder.noise = noise / 0.03;
            holder.bind();
        }
    }
}
