package com.ahheng.tile2d.app;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.ViewGroup;

import androidx.appcompat.app.AppCompatActivity;

import com.ahheng.tile2d.widget.canvas.TileView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TileView view = new TileView(this);
        setContentView(view, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // int size = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 40, getResources().getDisplayMetrics());
        // view.setDefaultTileWidth(size);
        // view.setDefaultTileHeight(size);
        view.setPadding(150, 150, 150, 150);
        view.setDebugMode(true);
        view.setAdapter(new InfiniteColorAdapter());
        view.seek(0, 0, 0f, 0f);
    }

    /* ==================== ColorTileHolder：文字/边框/亮度自适应全保留 ==================== */
    public static class ColorTileHolder extends TileView.TileHolder {
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
            // 亮底黑字，暗底白字，阈值 0.40
            cachedTextColor = luminance(backgroundColor) > 0.40 ? Color.BLACK : Color.WHITE;
        }

        @Override
        public void draw(Canvas canvas) {
            int w = getWidth();
            int h = getHeight();

            // 1. 背景
            fillPaint.setColor(backgroundColor);
            canvas.drawRect(0, 0, w, h, fillPaint);

            // 2. 边框（不需要时注释掉下面这行）
            canvas.drawRect(0, 0, w, h, borderPaint);

            // 3. 文字（不需要时注释掉下面这段）
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
    }

    /* ==================== 天空蓝噪声 ==================== */
    public static class SkyNoise {
        private final int[] perm = new int[512];

        public SkyNoise(long seed) {
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

    /* ==================== Adapter ==================== */
    public static class InfiniteColorAdapter extends TileView.Adapter {
        private final SkyNoise noise = new SkyNoise(12345L);

        @Override
        public TileView.TileHolder onCreateTileHolder(int type) {
            return new ColorTileHolder();
        }

        @Override
        public void onBindTileHolder(TileView.TileHolder holder, int column, int row) {
            ColorTileHolder h = (ColorTileHolder) holder;
            h.backgroundColor = noise.generateColor(column, row);
        }
    }
}
