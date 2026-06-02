package com.ahheng.tile2d.app;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;

import com.ahheng.tile2d.TileLayoutModel;
import com.ahheng.tile2d.widget.canvas.TileView;

public class TileViewActivity extends BaseActivity {

    private TileView view;
    private RandomAdapter adapter;
    private boolean displayText = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        view = new TileView(this);
        setContentView(view, new ViewGroup.LayoutParams(-1, -1));
        int padding = dp2px(40);
        view.setPadding(padding, padding, padding, padding);
        view.setDebugMode(isDebugMode());
        view.setAdapter((adapter = new RandomAdapter()));
        initTextPlan(true);

        /*
        Handler handler = new Handler(getMainLooper());
        handler.postDelayed(() -> {
            view.setTileWidth(0, dp2px(40));
            view.setTileHeight(0, dp2px(80));
        }, 2000);
         */
    }

    private void initColorPlan(boolean first) {
        displayText = false;
        int size = dp2px(40);
        view.setDefaultTileWidth(size);
        view.setDefaultTileHeight(size);
        view.setAdapter((adapter = new RandomAdapter()));
        if (first) {
            view.seek(0, 0, 0, 0);
        } else {
            TileLayoutModel model = view.getLayoutModel();
            view.seek(model.colStart, model.rowStart, model.offsetX, model.offsetY);
        }
    }

    private void initTextPlan(boolean first) {
        displayText = true;
        view.setDefaultTileWidth(dp2px(80));
        view.setDefaultTileHeight(dp2px(45));
        view.setAdapter((adapter = new RandomAdapter()));
        if (first) {
            view.seek(0, 0, 0, 0);
        } else {
            TileLayoutModel model = view.getLayoutModel();
            view.seek(model.colStart, model.rowStart, model.offsetX, model.offsetY);
        }
    }

    @Override
    protected void onDebugModeChanged(boolean enabled) {
        view.setDebugMode(enabled);
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

        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private String cachedText;
        private int cachedTextColor;
        private float cachedTextY;
        private int lastWidth = -1, lastHeight = -1;

        public ColorTileHolder() {
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setColor(Color.GRAY);
            borderPaint.setStrokeWidth(3f);
            textPaint.setTextAlign(Paint.Align.CENTER);
        }

        @Override
        public void onInWindow() {
            super.onInWindow();
            cachedText = getColumn() + "," + getRow();
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
            showToast("长按了 " + getColumn() + "," + getRow());
            // view.setAdapter(null);
            requestDisallowInterceptTouchEvent(true);
        }
    }

    public static class ColorNoise {
        private final int[] perm = new int[512];

        public ColorNoise(long seed) {
            int[] p = new int[256];
            for (int i = 0; i < 256; i++) p[i] = i;
            java.util.Random rand = new java.util.Random(seed);
            for (int i = 255; i > 0; i--) {
                int j = rand.nextInt(i + 1);
                int tmp = p[i]; p[i] = p[j]; p[j] = tmp;
            }
            for (int i = 0; i < 512; i++) perm[i] = p[i & 255];
        }

        public int generateColor(int x, int y) {
            float n = (float) noise(x * 0.03, y * 0.03);
            float t = (n + 1f) * 0.5f;
            float[] hsv = new float[3];
            hsv[0] = 205f;
            hsv[1] = 0.55f + t * 0.35f;
            hsv[2] = 0.25f + t * 0.70f;
            return Color.HSVToColor(hsv);
        }

        private double noise(double x, double y) {
            int X = (int) Math.floor(x) & 255;
            int Y = (int) Math.floor(y) & 255;
            x -= Math.floor(x);
            y -= Math.floor(y);
            double u = fade(x);
            double v = fade(y);
            int A = perm[X] + Y;
            int B = perm[X + 1] + Y;
            return lerp(v,
                    lerp(u, grad(perm[A], x, y), grad(perm[B], x - 1, y)),
                    lerp(u, grad(perm[A + 1], x, y - 1), grad(perm[B + 1], x - 1, y - 1)));
        }

        private static double fade(double t) {
            return t * t * t * (t * (t * 6 - 15) + 10);
        }

        private static double lerp(double t, double a, double b) {
            return a + t * (b - a);
        }

        private static double grad(int hash, double x, double y) {
            int h = hash & 7;
            double u = (h < 4) ? x : y;
            double v = (h < 4) ? y : x;
            return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
        }
    }

    private class RandomAdapter extends TileView.Adapter<ColorTileHolder> {
        @Override
        public int getTopBound() {
            return -50;
        }

        @Override
        public int getLeftBound() {
            return -50;
        }

        @Override
        public int getRightBound() {
            return 50;
        }

        @Override
        public int getBottomBound() {
            return 50;
        }

        private final ColorNoise noise = new ColorNoise(12345L);

        @Override
        public ColorTileHolder onCreateTileHolder(int type) {
            return new ColorTileHolder();
        }

        @Override
        public void onBindTileHolder(ColorTileHolder holder, int column, int row) {
            holder.backgroundColor = noise.generateColor(column, row);
        }
    }

}
