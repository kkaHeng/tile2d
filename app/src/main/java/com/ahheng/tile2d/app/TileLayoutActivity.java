package com.ahheng.tile2d.app;

import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.ahheng.tile2d.widget.layout.TileLayout;

public class TileLayoutActivity extends BaseActivity {

    private TileLayout layout;
    private RandomAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        layout = new TileLayout(this);
        setContentView(layout, new ViewGroup.LayoutParams(-1, -1));

        // int size = dp2px(40);
        // layout.setDefaultTileWidth(size);
        // layout.setDefaultTileHeight(size);
        int padding = dp2px(40);
        layout.setPadding(padding, padding, padding, padding);
        layout.setDebugMode(isDebugMode());
        layout.setAdapter((adapter = new RandomAdapter()));
        layout.seek(0, 0, 0, 0);
    }

    @Override
    protected void onDebugModeChanged(boolean enabled) {
        layout.setDebugMode(enabled);
    }

    public class ColorTileHolder extends TileLayout.TileHolder {
        int backgroundColor;

        private TextView textView;

        public ColorTileHolder() {
            super(new TextView(TileLayoutActivity.this));
            textView = (TextView) itemView;
            textView.setTextColor(Color.WHITE);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            textView.setGravity(Gravity.CENTER);
        }

        public void bind() {
            textView.setBackgroundColor(backgroundColor);
            textView.setText(getColumn() + "," + getRow());
            textView.setTextColor(luminance(backgroundColor) > 0.40 ? Color.BLACK : Color.WHITE);

            textView.setOnClickListener(v -> {
                showToast("单击了：" + getColumn() + "," + getRow());
            });
            textView.setOnLongClickListener(v -> {
                showToast("长按了：" + getColumn() + "," + getRow());
                requestDisallowInterceptTouchEvent(true);
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

    private class RandomAdapter extends TileLayout.Adapter<ColorTileHolder> {
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
            holder.bind();
        }
    }
}
