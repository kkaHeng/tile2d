package com.ahheng.tile2d.app;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

import com.ahheng.tile2d.TileCoreService;
import com.ahheng.tile2d.TileLayoutModel;
import com.ahheng.tile2d.widget.canvas.TileView;

import java.util.HashSet;
import java.util.Set;

public class TileViewActivity extends BaseActivity {

    private TileView view;
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
        
        view = new TileView(this);
        setContentView(view, new ViewGroup.LayoutParams(-1, -1));
        int padding = dp2px(40);
        view.setPadding(padding, padding, padding, padding);
        view.setDebugMode(isDebugMode());
        view.setAdapter((adapter = new RandomAdapter()));
        initTextPlan(true);


        Handler handler = new Handler(getMainLooper());
        handler.postDelayed(() -> {
            // 测试延迟调整宽度
            view.setTileWidth(0, dp2px(160));
        }, 2000);
         
    }

    private void initColorPlan(boolean first) {
        displayText = false;
        TileLayoutModel model = view.getLayoutModel();
        int size = dp2px(40);
        view.setDefaultTileWidth(size);
        view.setDefaultTileHeight(size);
        view.setAdapter((adapter = new RandomAdapter()));
        if (first) {
            view.seek(0, 0, 0, 0);
        } else {
            view.seek(model.colStart, model.rowStart, model.offsetX, model.offsetY);
        }
    }

    private void initTextPlan(boolean first) {
        displayText = true;
        TileLayoutModel model = view.getLayoutModel();
        view.setDefaultTileWidth(dp2px(80));
        view.setDefaultTileHeight(dp2px(45));
        view.setAdapter((adapter = new RandomAdapter()));
        if (first) {
            view.seek(0, 0, 0, 0);
        } else {
            view.seek(model.colStart, model.rowStart, model.offsetX, model.offsetY);
        }
    }

    @Override
    protected void onDebugModeChanged(boolean enabled) {
        view.setDebugMode(enabled);
    }

    @Override
    protected void onMaxModeChanged(boolean maxMode) {
        super.onMaxModeChanged(maxMode);
        view.snap();
    }

    @Override
    protected void onPlanChanged(int plan) {
        super.onPlanChanged(plan);
        switch (plan) {
            case PLAN_COLOR -> initColorPlan(false);
            case PLAN_TEXT -> initTextPlan(false);
        }
    }

    public class ColorTileHolder extends TileView.TileHolder {
        int backgroundColor;
        double noise;

        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private String cachedText;
        private int cachedTextColor;
        private float cachedTextY;
        private int lastWidth = -1, lastHeight = -1;

        public ColorTileHolder() {
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(3f);
            borderPaint.setColor(Color.GRAY);
            textPaint.setTextAlign(Paint.Align.CENTER);
        }

        @Override
        public void onInWindow() {
            super.onInWindow();
            cachedText = String.format("%.2f", noise);
            cachedTextColor = luminance(backgroundColor) > 0.40 ? Color.BLACK : Color.WHITE;
        }

        @Override
        public void draw(Canvas canvas) {
            int w = getWidth();
            int h = getHeight();

            fillPaint.setColor(backgroundColor);
            canvas.drawRect(0, 0, w, h, fillPaint);
            canvas.drawRect(0, 0, w, h, borderPaint);

            if (!displayText) return;
            textPaint.setColor(cachedTextColor);
            if (w != lastWidth || h != lastHeight) {
                lastWidth = w;
                lastHeight = h;
                textPaint.setTextSize(Math.min(w, h) * 0.28f);
                Paint.FontMetrics fm = textPaint.getFontMetrics();
                cachedTextY = h / 2f - (fm.ascent + fm.descent) / 2f;
            }
            canvas.drawText(cachedText, w / 2f, cachedTextY, textPaint);
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

        @Override
        public boolean onClick() {
            showToast("单击了 " + getColumn() + "," + getRow());
            return false;
        }

        @Override
        public void onLongClick() {
            requestDisallowInterceptTouchEvent(true);
            removedTiles.add(TileCoreService.getTileId(getColumn(), getRow()));
            view.update(getColumn(), getRow());
        }
    }

    private class RandomAdapter extends TileView.Adapter<ColorTileHolder> {
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
        }
    }

}
