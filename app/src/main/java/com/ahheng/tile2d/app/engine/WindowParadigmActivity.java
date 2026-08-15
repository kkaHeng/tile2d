package com.ahheng.tile2d.app.engine;

import android.os.Bundle;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

// 布局引擎视窗范式演示
// 对比传统与 Tile2D 两种视窗/内容拖拽范式,均基于自定义绘制,不接入真实引擎
// 固定全屏布局(不使用 ScrollView,避免干扰拖动)
public class WindowParadigmActivity extends AppCompatActivity {

    private static final int TILE_COUNT = 16;
    private static final int TILE_W_DP = 40;
    private static final int WINDOW_TILES = 4;

    private int textColor;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TypedValue colorValue = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.textColorPrimary, colorValue, true);
        textColor = ContextCompat.getColor(this, colorValue.resourceId);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) actionBar.setDisplayHomeAsUpEnabled(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        // 传统范式: 绝对坐标定位,视窗如放大镜在尺子上滚动
        root.addView(section("传统范式",
                        "使用绝对坐标定位：视窗像放大镜在尺子上滚动。内容位置固定，视窗是可移动的。绝对坐标的取值范围是 [0, 内容总尺寸]。可以左右拖拽。",
                        WindowParadigmView.MODE_TRADITIONAL),
                new LinearLayout.LayoutParams(-1, 0, 1f));

        // Tile2D 范式: 逻辑坐标 + 像素偏移定位,视窗如摄像机固定,内容流动
        root.addView(section("Tile2D 范式",
                        "使用逻辑坐标 + 像素偏移定位：视窗像摄像机固定在原地，内容是流动的。"
                                + "像素偏移的取值范围 [-瓦片尺寸, 0]。可以左右拖拽。",
                        WindowParadigmView.MODE_TILE2D),
                new LinearLayout.LayoutParams(-1, 0, 1f));

        setContentView(root);
    }

    private ViewGroup section(String titleText, String descText, int mode) {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(0, dp2px(10), 0, dp2px(10));
        section.addView(title(titleText));
        section.addView(desc(descText));

        WindowParadigmView view = new WindowParadigmView(this, mode, TILE_COUNT, dp2px(TILE_W_DP), WINDOW_TILES);
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(-1, 0, 1f);
        vp.leftMargin = dp2px(16);
        vp.rightMargin = dp2px(16);
        vp.topMargin = dp2px(10);
        section.addView(view, vp);
        return section;
    }

    private TextView title(CharSequence text) {
        TextView title = new TextView(this);
        title.setText(text);
        title.setTextSize(20);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setTextColor(textColor);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
        params.leftMargin = dp2px(16);
        title.setLayoutParams(params);
        return title;
    }

    private TextView desc(CharSequence text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(12);
        tv.setLineSpacing(dp2px(3), 1f);
        tv.setTextColor(textColor);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
        params.leftMargin = dp2px(16);
        params.rightMargin = dp2px(16);
        params.topMargin = dp2px(2);
        tv.setLayoutParams(params);
        return tv;
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private int dp2px(float dp) {
        return (int) (getResources().getDisplayMetrics().density * dp + 0.5f);
    }

}
