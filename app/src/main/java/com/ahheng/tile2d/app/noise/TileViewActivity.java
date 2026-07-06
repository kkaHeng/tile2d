package com.ahheng.tile2d.app.noise;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Picture;
import android.os.Bundle;
import android.os.Handler;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ahheng.tile2d.TileCoreService;
import com.ahheng.tile2d.TileLayoutModel;
import com.ahheng.tile2d.app.BaseActivity;
import com.ahheng.tile2d.widget.canvas.TileView;

import java.util.HashSet;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

public class TileViewActivity extends BaseActivity {

    private static final int MENU_ID_RANDOM_WIDTH = nextId();
    private static final int MENU_ID_RANDOM_HEIGHT = nextId();

    private TileView view;
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
        
        view = new TileView(this);
        setContentView(view, new ViewGroup.LayoutParams(-1, -1));
        int padding = dp2px(40);
        view.setPadding(padding, padding, padding, padding);
        view.setDebugMode(isDebugMode());
        view.setAdapter((adapter = new RandomAdapter()));
        initTextPlan(true);

    }

    private void initColorPlan() {
        displayText = false;
        TileLayoutModel model = view.getLayoutModel().newInstance();
        int size = dp2px(40);
        view.setDefaultTileWidth(size);
        view.setDefaultTileHeight(size);
        view.setAdapter((adapter = new RandomAdapter()));
        view.seek(model.colStart, model.rowStart, model.offsetX, model.offsetY);
    }

    private void initTextPlan(boolean first) {
        displayText = true;
        TileLayoutModel model = view.getLayoutModel().newInstance();
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
            int column = view.findColumn(view.getWidth() / 2f);
            int dp = (new Random().nextInt(19) + 4) * 10;
            int target = dp2px(dp);
            widthAnimator = ValueAnimator.ofInt(view.getTileWidth(column), target);
            widthAnimator.setDuration(2000);
            widthAnimator.setInterpolator(new OvershootInterpolator());
            widthAnimator.addUpdateListener(a -> {
                int current = (int) a.getAnimatedValue();
                view.setTileWidth(column, current, TileView.DIMEN_GRAVITY_CENTER);
            });
            widthAnimator.start();
            showToast(String.format(Locale.getDefault(), "调整第 %d 列宽度到 %ddp", column, dp));
        }
        if (id == MENU_ID_RANDOM_HEIGHT) {
            if (heightAnimator != null) {
                heightAnimator.cancel();
            }
            int row = view.findRow(view.getHeight() / 2f);
            int dp = (new Random().nextInt(7) + 3) * 10;
            int target = dp2px(dp);
            heightAnimator = ValueAnimator.ofInt(view.getTileHeight(row), target);
            heightAnimator.setDuration(2000);
            heightAnimator.setInterpolator(new OvershootInterpolator());
            heightAnimator.addUpdateListener(a -> {
                int current = (int) a.getAnimatedValue();
                view.setTileHeight(row, current, TileView.DIMEN_GRAVITY_CENTER);
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
                view.seek(column, row);
            }
        };
    }

    public class ColorTileHolder extends TileView.TileHolder {
        int backgroundColor;
        double noise;
    
        // 绘制工具
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    
        // 文本数据
        private String cachedText;
        private int cachedTextColor;
        private float cachedTextY; // 仅用于 drawText 兜底模式
    
        // Picture 缓存
        private Picture picture;
        private int pictureWidth;  // 上次录制时的宽
        private int pictureHeight; // 上次录制时的高
        private boolean needReplay; // 是否需要重新录制（数据或尺寸变化）
    
        public ColorTileHolder() {
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(3f);
            borderPaint.setColor(Color.GRAY);
            textPaint.setTextAlign(Paint.Align.CENTER);
            needReplay = true;
        }

        public void bind() {
            cachedText = String.format(Locale.getDefault(), "%.2f", noise);
            cachedTextColor = luminance(backgroundColor) > 0.40 ? Color.BLACK : Color.WHITE;
            textPaint.setColor(cachedTextColor);
            fillPaint.setColor(backgroundColor);
            needReplay = true;
            recordPicture();
        }
    
        @Override
        public void onSizeChanged(int width, int height) {
            super.onSizeChanged(width, height);
            textPaint.setTextSize(Math.min(width, height) * 0.28f);
            Paint.FontMetrics fm = textPaint.getFontMetrics();
            cachedTextY = height / 2f - (fm.ascent + fm.descent) / 2f;
            needReplay = true;
            if (cachedText != null && width > 0 && height > 0) {
                recordPicture();
            }
        }
    
        private void recordPicture() {
            if (getWidth() <= 0 || getHeight() <= 0) return;
            if (!needReplay && picture != null) return;
    
            if (picture == null) {
                picture = new Picture();
            }
            Canvas canvas = picture.beginRecording(getWidth(), getHeight());
            fillPaint.setColor(backgroundColor);
            canvas.drawRect(0, 0, getWidth(), getHeight(), fillPaint);
            canvas.drawRect(0, 0, getWidth(), getHeight(), borderPaint);
            if (displayText && cachedText != null) {
                textPaint.setColor(cachedTextColor);
                canvas.drawText(cachedText, getWidth() / 2f, cachedTextY, textPaint);
            }
            picture.endRecording();
            needReplay = false;
        }
    
        public void invalidatePicture() {
            needReplay = true;
            if (getWidth() > 0 && getHeight() > 0) {
                recordPicture();
            }
        }
    
        @Override
        public void draw(Canvas canvas) {
            if (picture == null || needReplay) {
                if (getWidth() > 0 && getHeight() > 0) {
                    recordPicture();
                } else {
                    // 未就绪时至少绘制背景（防止空白）
                    fillPaint.setColor(backgroundColor);
                    canvas.drawRect(0, 0, getWidth(), getHeight(), fillPaint);
                    return;
                }
            }
    
            // 直接回放 Picture
            if (picture != null) {
                canvas.drawPicture(picture);
            }
    
            /* ========== 以下是保留的原始 drawText 兜底方案 ==========
              当 Picture 出现异常或需要调试时，可注释掉上面的 drawPicture，
              取消注释下面的代码，即可恢复直接绘制。
            */
            /*
            canvas.drawRect(0, 0, getWidth(), getHeight(), fillPaint);
            canvas.drawRect(0, 0, getWidth(), getHeight(), borderPaint);
            if (displayText && cachedText != null) {
                textPaint.setColor(cachedTextColor);
                canvas.drawText(cachedText, getWidth() / 2f, cachedTextY, textPaint);
            }
            */
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

    private class RandomAdapter extends TileView.Adapter {
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
        public TileView.TileHolder onCreateTileHolder(int type) {
            if (type == -1) return null;
            return new ColorTileHolder();
        }

        @Override
        public void onBindTileHolder(TileView.TileHolder holder, int column, int row) {
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
        view.setAdapter(null);
    }

}
