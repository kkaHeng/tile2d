package com.ahheng.tile2d.app;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Picture;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.ahheng.tile2d.app.auto.AutoTileActivity;
import com.ahheng.tile2d.app.engine.LayoutEngineBenchActivity;
import com.ahheng.tile2d.app.engine.WindowParadigmActivity;
import com.ahheng.tile2d.app.data.TableActivity;
import com.ahheng.tile2d.app.gomoku.GomokuActivity;
import com.ahheng.tile2d.app.maze.MaxMazeActivity;
import com.ahheng.tile2d.app.maze.MazeActivity;
import com.ahheng.tile2d.app.minesweeper.MinesweeperActivity;
import com.ahheng.tile2d.app.noise.TileLayoutActivity;
import com.ahheng.tile2d.app.noise.TileViewActivity;
import com.caverock.androidsvg.SVG;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends AppCompatActivity {

    private static final class DemoInfo {
        final String title;
        final String description;
        final Class<?> activityClass;
        final int accentColor;
        final String tag;

        DemoInfo(String title, String description, Class<?> activityClass, int accentColor, String tag) {
            this.title = title;
            this.description = description;
            this.activityClass = activityClass;
            this.accentColor = accentColor;
            this.tag = tag;
        }
    }

    private static final int C_PRIMARY = 0xFF3D5A80;
    private static final int C_TERTIARY = 0xFF4A6359;
    private static final int C_SECONDARY = 0xFF556B7D;
    private static final int C_ERROR = 0xFFBA1A1A;
    private static final int C_AMBER = 0xFFBF7D1A;
    private static final int C_TEAL = 0xFF1A7A6E;
    private static final int C_DEEP_ORANGE = 0xFFB8451A;

    // Jitpack badge URL
    private static final String JITPACK_SVG_URL = "https://jitpack.io/v/kkaHeng/tile2d.svg";
    private static final String JITPACK_PAGE_URL = "https://jitpack.io/#kkaHeng/tile2d";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Hide ActionBar
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        DemoInfo[] demos = new DemoInfo[]{
                new DemoInfo("瓦片画板", "基于 Canvas 的瓦片渲染引擎,展示 Perlin 噪声生成彩色纹理、" +
                        "随机列宽/行高动画、瓦片删除与 Picture 缓存优化", TileViewActivity.class, C_TERTIARY, "canvas"),
                new DemoInfo("瓦片布局", "基于 Android View 体系的瓦片布局,与画板使用相同噪声数据,通过 " +
                        "TextView 渲染,支持点击/长按交互与瓦片移除", TileLayoutActivity.class, C_PRIMARY, "layout"),
                new DemoInfo("数据表", "基于 TileLayout 的纯文本表格(CSV)编辑器,内置元素周期表数据," +
                        "支持单元格编辑、行列增删移动、TSV/CSV 复制与解析,数据同步持久化", TableActivity.class, C_TEAL, "data"),
                new DemoInfo("自动瓦片", "基于连接规则(ConnectionRule)的自动图块拼接系统,支持拖动绘制、 " +
                        "瓦片破碎粒子动画与进入弹跳效果", AutoTileActivity.class, C_AMBER, "auto"),
                new DemoInfo("迷宫生成", "递归回溯算法实时生成迷宫,摄像机平滑跟随,展示 TileLayout 的动态 " +
                        "瓦片更新与范围刷新能力", MazeActivity.class, C_PRIMARY, "maze"),
                new DemoInfo("无限迷宫", "分块(Chunk)驱动的无限迷宫系统,多线程异步生成、区块池回收复用, " +
                        "展示 TileLayout 在海量坐标空间下的承载能力", MaxMazeActivity.class, C_DEEP_ORANGE, "maxmaze"),
                new DemoInfo("扫雷", "完整扫雷游戏实现,支持标记/掀开、存档读档、AI 自动求解与摄像机跟随",
                        MinesweeperActivity.class, C_ERROR, "minesweeper"),
                new DemoInfo("五子棋", "有限模式(-100~+100)与伪无限模式(完整 int32)均判定五连胜负,支持双人同屏、人机对战,「AI 自动下棋」开启后双方全由 AI 行棋,AI 为棋型评分 + α-β 剪枝",
                        GomokuActivity.class, C_PRIMARY, "gomoku"),
                new DemoInfo("性能测试", "LayoutEngine 纯算法基准测试,统计同步滚动/随机跳转场景下的纳秒级 " +
                        "耗时分布与吞吐量", LayoutEngineBenchActivity.class, C_SECONDARY, "bench"),
                new DemoInfo("视窗范式", "对比绝对坐标(放大镜)与逻辑坐标+偏移(摄像机)两种视窗定位范式",
                        WindowParadigmActivity.class, C_SECONDARY, "paradigm"),
        };

        // ===== Root ScrollView (no padding — cards use margins for shadows) =====
        ScrollView scrollView = new ScrollView(this);
        int margin24 = dp2px(24);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        // ===== Header =====
        root.addView(createHeader());

        // ===== Subtitle =====
        root.addView(createSubtitle());

        // ===== Divider =====
        root.addView(createDivider());

        // ===== Demo Cards =====
        for (DemoInfo demo : demos) {
            View card = createDemoCard(demo);
            // Use margins on cards for horizontal spacing, so shadows are never clipped
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cardLp.leftMargin = margin24;
            cardLp.rightMargin = margin24;
            card.setLayoutParams(cardLp);
            root.addView(card);
            addVerticalSpace(root, dp2px(12));
        }

        // ===== Footer =====
        addVerticalSpace(root, dp2px(8));
        root.addView(createFooterDivider());
        addVerticalSpace(root, dp2px(16));
        root.addView(createFooter());
        addVerticalSpace(root, dp2px(24));

        scrollView.addView(root);
        setContentView(scrollView);
    }

    // =========================================================================
    // Header
    // =========================================================================

    private View createHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp2px(24), dp2px(8), dp2px(24), dp2px(8));

        TextView title = new TextView(this);
        title.setText("Tile2D");
        title.setTextSize(32);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(getColorRes(com.google.android.material.R.attr.colorOnBackground));
        header.addView(title);

        TextView tagline = new TextView(this);
        tagline.setText("二维瓦片引擎 \u00B7 示例合集");
        tagline.setTextSize(15);
        tagline.setTextColor(getColorRes(com.google.android.material.R.attr.colorOnSurfaceVariant));
        header.addView(tagline);

        return header;
    }

    private View createSubtitle() {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        sb.append("精选 ");
        int s = sb.length();
        sb.append("8");
        sb.setSpan(new StyleSpan(Typeface.BOLD), s, s + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new ForegroundColorSpan(C_PRIMARY), s, s + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append(" 个 Demo,展示 Tile2D 引擎的核心能力");

        TextView text = new TextView(this);
        text.setText(sb);
        text.setTextSize(13);
        text.setTextColor(getColorRes(com.google.android.material.R.attr.colorOnSurfaceVariant));
        text.setPadding(dp2px(24), dp2px(4), dp2px(24), dp2px(12));
        return text;
    }

    private View createDivider() {
        View divider = new View(this);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp2px(1)));
        divider.setBackgroundColor(getColorRes(com.google.android.material.R.attr.colorOutlineVariant));
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) divider.getLayoutParams();
        lp.bottomMargin = dp2px(16);
        lp.leftMargin = dp2px(24);
        lp.rightMargin = dp2px(24);
        return divider;
    }

    // =========================================================================
    // Demo Card
    // =========================================================================

    private View createDemoCard(DemoInfo demo) {
        int radius = dp2px(16);

        MaterialCardView card = new MaterialCardView(this);
        card.setCardElevation(dp2px(2));
        card.setRadius(radius);
        card.setCardBackgroundColor(getColorRes(com.google.android.material.R.attr.colorSurfaceContainerLow));
        card.setStrokeWidth(0);
        card.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, demo.activityClass)));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp2px(16), dp2px(14), dp2px(16), dp2px(14));

        View accentBar = new View(this);
        accentBar.setLayoutParams(new LinearLayout.LayoutParams(
                dp2px(48), dp2px(3)));
        GradientDrawable barBg = new GradientDrawable();
        barBg.setColor(demo.accentColor);
        barBg.setCornerRadius(dp2px(2));
        accentBar.setBackground(barBg);
        content.addView(accentBar);
        addVerticalSpace(content, dp2px(10));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView tagView = new TextView(this);
        tagView.setText(demo.tag);
        tagView.setTextSize(10);
        tagView.setTypeface(null, Typeface.BOLD);
        tagView.setTextColor(Color.WHITE);
        tagView.setPadding(dp2px(8), dp2px(3), dp2px(8), dp2px(3));
        GradientDrawable tagBg = new GradientDrawable();
        tagBg.setColor(demo.accentColor);
        tagBg.setCornerRadius(dp2px(8));
        tagView.setBackground(tagBg);
        titleRow.addView(tagView);

        addHorizontalSpace(titleRow, dp2px(10));

        TextView titleView = new TextView(this);
        titleView.setText(demo.title);
        titleView.setTextSize(17);
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setTextColor(getColorRes(com.google.android.material.R.attr.colorOnSurface));
        titleRow.addView(titleView);

        content.addView(titleRow);
        addVerticalSpace(content, dp2px(8));

        TextView descView = new TextView(this);
        descView.setText(demo.description);
        descView.setTextSize(13);
        descView.setLineSpacing(dp2px(4), 1f);
        descView.setTextColor(getColorRes(com.google.android.material.R.attr.colorOnSurfaceVariant));
        content.addView(descView);

        addVerticalSpace(content, dp2px(12));

        MaterialButton launchButton = new MaterialButton(this);
        launchButton.setText("开始体验 \u2192");
        launchButton.setTextSize(13);
        launchButton.setBackgroundTintList(ColorStateList.valueOf(demo.accentColor));
        launchButton.setTextColor(Color.WHITE);
        launchButton.setCornerRadius(dp2px(10));
        launchButton.setPadding(dp2px(16), 0, dp2px(12), 0);
        launchButton.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp2px(38)));
        launchButton.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, demo.activityClass)));
        content.addView(launchButton);

        card.addView(content);
        return card;
    }

    // =========================================================================
    // Footer
    // =========================================================================

    private View createFooterDivider() {
        View divider = new View(this);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp2px(1)));
        divider.setBackgroundColor(getColorRes(com.google.android.material.R.attr.colorOutlineVariant));
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) divider.getLayoutParams();
        lp.leftMargin = dp2px(24);
        lp.rightMargin = dp2px(24);
        return divider;
    }

    private View createFooter() {
        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setGravity(Gravity.CENTER_HORIZONTAL);
        footer.setPadding(dp2px(24), 0, dp2px(24), 0);

        TextView footerTitle = new TextView(this);
        footerTitle.setText("相关链接");
        footerTitle.setTextSize(14);
        footerTitle.setTypeface(null, Typeface.BOLD);
        footerTitle.setTextColor(getColorRes(com.google.android.material.R.attr.colorOnSurfaceVariant));
        footerTitle.setGravity(Gravity.CENTER);
        footer.addView(footerTitle);
        addVerticalSpace(footer, dp2px(12));

        LinearLayout linkRow = new LinearLayout(this);
        linkRow.setOrientation(LinearLayout.HORIZONTAL);
        linkRow.setGravity(Gravity.CENTER);

        linkRow.addView(createLinkChip("Gitee", "https://gitee.com/kkaheng/tile2d"));
        addHorizontalSpace(linkRow, dp2px(12));
        linkRow.addView(createLinkChip("GitHub", "https://github.com/kkaheng/tile2d"));

        footer.addView(linkRow);
        addVerticalSpace(footer, dp2px(16));

        // Jitpack badge rendered from real SVG
        ImageView badgeView = createJitpackBadge();
        footer.addView(badgeView);

        return footer;
    }

    private View createLinkChip(String label, String url) {
        MaterialCardView chip = new MaterialCardView(this);
        chip.setCardElevation(dp2px(1));
        chip.setRadius(dp2px(20));
        chip.setCardBackgroundColor(getColorRes(com.google.android.material.R.attr.colorSurfaceContainerHigh));
        chip.setStrokeWidth(dp2px(1));
        chip.setStrokeColor(getColorRes(com.google.android.material.R.attr.colorOutlineVariant));
        chip.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp2px(42)));
        chip.setClickable(true);
        chip.setFocusable(true);
        chip.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        });
        chip.setPadding(0, 0, 0, 0);

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.HORIZONTAL);
        inner.setGravity(Gravity.CENTER_VERTICAL);
        inner.setPadding(dp2px(16), dp2px(8), dp2px(16), dp2px(8));

        View dot = new View(this);
        dot.setLayoutParams(new LinearLayout.LayoutParams(dp2px(8), dp2px(8)));
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(label.equals("GitHub") ? 0xFF2DBA4E : 0xFFC71D23);
        dot.setBackground(dotBg);
        inner.addView(dot);
        addHorizontalSpace(inner, dp2px(8));

        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(13);
        tv.setTypeface(null, Typeface.NORMAL);
        tv.setTextColor(getColorRes(com.google.android.material.R.attr.colorOnSurface));
        inner.addView(tv);

        chip.addView(inner);
        return chip;
    }

    private ImageView createJitpackBadge() {
        int badgeLogicalW = dp2px(98);
        int badgeLogicalH = dp2px(20);

        ImageView imageView = new ImageView(this);
        imageView.setLayoutParams(new LinearLayout.LayoutParams(
                badgeLogicalW, badgeLogicalH));
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setClickable(true);
        imageView.setFocusable(true);
        imageView.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(JITPACK_PAGE_URL));
            startActivity(intent);
        });

        // Fetch and render SVG badge at screen density for sharp output
        new Thread(() -> {
            try {
                URL url = new URL(JITPACK_SVG_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                InputStream is = conn.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();
                conn.disconnect();

                String svgContent = sb.toString();
                SVG svg = SVG.getFromString(svgContent);

                // Render at native size to a Picture first
                Picture picture = svg.renderToPicture();

                // Scale up bitmap to match screen density for crisp rendering
                float density = getResources().getDisplayMetrics().density;
                int bmpW = Math.round(98 * density);
                int bmpH = Math.round(20 * density);

                Bitmap bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                canvas.scale(density, density);
                canvas.drawPicture(picture);

                runOnUiThread(() -> {
                    imageView.setImageBitmap(bitmap);
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        return imageView;
    }

    // =========================================================================
    // Utilities
    // =========================================================================

    private void addVerticalSpace(LinearLayout parent, int px) {
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, px));
        parent.addView(spacer);
    }

    private void addHorizontalSpace(LinearLayout parent, int px) {
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(px, ViewGroup.LayoutParams.MATCH_PARENT));
        parent.addView(spacer);
    }

    private int getColorRes(int attr) {
        android.content.res.TypedArray ta = obtainStyledAttributes(new int[]{attr});
        int color = ta.getColor(0, Color.LTGRAY);
        ta.recycle();
        return color;
    }

    private int dp2px(float dp) {
        return (int) (getResources().getDisplayMetrics().density * dp + 0.5f);
    }
}
