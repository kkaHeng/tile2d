package com.ahheng.tile2d.app.bench;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import com.ahheng.tile2d.LayoutEngine;
import com.ahheng.tile2d.LayoutModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.util.Arrays;
import java.util.Locale;
import java.util.Random;

public class LayoutEngineBenchActivity extends AppCompatActivity {

    private static final int TILE_SIZE_DP = 50;

    private static final int C_GREEN = Color.rgb(30, 140, 30);
    private static final int C_RED = Color.rgb(200, 30, 30);
    private static final int C_BLUE = Color.rgb(30, 100, 200);
    private static final int C_PURPLE = Color.rgb(180, 20, 100);
    private static final int C_ORANGE = Color.rgb(180, 100, 20);
    private static final int C_TITLE = Color.rgb(60, 60, 180);
    private static final int C_ENV_LABEL = Color.rgb(60, 60, 60);
    private static final int C_ENV_VAL = Color.rgb(20, 130, 60);
    private static final int BOUNDARY_LEFT = Integer.MIN_VALUE;
    private static final int BOUNDARY_TOP = Integer.MIN_VALUE;
    private static final int BOUNDARY_RIGHT = Integer.MAX_VALUE;
    private static final int BOUNDARY_BOTTOM = Integer.MAX_VALUE;

    private static final int[] STEP_CANDIDATES_PX = {0, 8, 16, 32};

    private EditText countInput;
    private TextView envText;
    private TextView resultText;

    private LayoutEngine engine;
    private int tileSizePx;
    private int windowWidth;
    private int windowHeight;

    private int inCallCount;
    private int outCallCount;
    private boolean testing;

    private String lastEnvPlain;
    private String lastResultPlain;

    private final Random random = new Random();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) actionBar.setDisplayHomeAsUpEnabled(true);

        tileSizePx = dp2px(TILE_SIZE_DP);
        windowWidth = getResources().getDisplayMetrics().widthPixels;
        windowHeight = getResources().getDisplayMetrics().heightPixels;

        initEngine();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp2px(16), dp2px(12), dp2px(16), dp2px(12));

        // ===== Environment card =====
        MaterialCardView envCard = new MaterialCardView(this);
        envCard.setCardElevation(dp2px(1));
        envCard.setRadius(dp2px(10));
        envCard.setContentPadding(dp2px(14), dp2px(12), dp2px(14), dp2px(12));
        envCard.setCardBackgroundColor(getColorRes(com.google.android.material.R.attr.colorSurfaceContainerLow));

        envText = new TextView(this);
        envText.setTextSize(14);
        updateEnvText();
        envCard.addView(envText);
        root.addView(envCard);

        // ===== Input row =====
        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        inputRow.setPadding(0, dp2px(16), 0, dp2px(12));

        TextView label = new TextView(this);
        label.setText("测试次数");
        label.setTextSize(15);
        label.setTextColor(getColorRes(com.google.android.material.R.attr.colorOnSurface));
        inputRow.addView(label);

        countInput = new EditText(this);
        countInput.setText("1000");
        countInput.setSelectAllOnFocus(true);
        countInput.setTextSize(16);
        countInput.setGravity(Gravity.CENTER);
        countInput.setLayoutParams(new LinearLayout.LayoutParams(dp2px(110), ViewGroup.LayoutParams.WRAP_CONTENT));
        inputRow.addView(countInput);
        root.addView(inputRow);

        // ===== Button row 1: sync + seek =====
        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setPadding(0, 0, 0, dp2px(6));

        MaterialButton syncButton = new MaterialButton(this);
        syncButton.setText("同步(滚动)测试");
        syncButton.setTextSize(13);
        syncButton.setLayoutParams(new LinearLayout.LayoutParams(0, dp2px(48), 1f));
        ((LinearLayout.LayoutParams) syncButton.getLayoutParams()).setMarginEnd(dp2px(4));
        syncButton.setOnClickListener(v -> runSyncBenchmark());
        buttonRow.addView(syncButton);

        MaterialButton seekButton = new MaterialButton(this);
        seekButton.setText("定位(跳转)测试");
        seekButton.setTextSize(13);
        seekButton.setLayoutParams(new LinearLayout.LayoutParams(0, dp2px(48), 1f));
        ((LinearLayout.LayoutParams) seekButton.getLayoutParams()).setMarginStart(dp2px(4));
        seekButton.setOnClickListener(v -> runSeekBenchmark());
        buttonRow.addView(seekButton);

        root.addView(buttonRow);

        // ===== Button row 2: toTheEnd + reset + copy =====
        LinearLayout toolRow = new LinearLayout(this);
        toolRow.setOrientation(LinearLayout.HORIZONTAL);
        toolRow.setPadding(0, 0, 0, dp2px(8));

        MaterialButton toEndButton = new MaterialButton(this);
        toEndButton.setText("去边界看看");
        toEndButton.setTextSize(13);
        toEndButton.setLayoutParams(new LinearLayout.LayoutParams(0, dp2px(42), 1f));
        ((LinearLayout.LayoutParams) toEndButton.getLayoutParams()).setMarginEnd(dp2px(4));
        toEndButton.setOnClickListener(v -> runToTheEnd());
        toolRow.addView(toEndButton);

        MaterialButton resetButton = new MaterialButton(this);
        resetButton.setText("重置引擎");
        resetButton.setTextSize(13);
        resetButton.setLayoutParams(new LinearLayout.LayoutParams(0, dp2px(42), 1f));
        ((LinearLayout.LayoutParams) resetButton.getLayoutParams()).setMarginStart(dp2px(2));
        ((LinearLayout.LayoutParams) resetButton.getLayoutParams()).setMarginEnd(dp2px(2));
        resetButton.setOnClickListener(v -> {
            if (testing) return;
            initEngine();
            updateEnvText();
            lastResultPlain = null;
            resultText.setText(null);
        });
        toolRow.addView(resetButton);

        MaterialButton copyButton = new MaterialButton(this);
        copyButton.setText("复制结果");
        copyButton.setTextSize(13);
        copyButton.setLayoutParams(new LinearLayout.LayoutParams(0, dp2px(42), 1f));
        ((LinearLayout.LayoutParams) copyButton.getLayoutParams()).setMarginStart(dp2px(4));
        copyButton.setOnClickListener(v -> copyResult());
        toolRow.addView(copyButton);

        root.addView(toolRow);

        // ===== Result area =====
        MaterialCardView resultCard = new MaterialCardView(this);
        resultCard.setCardElevation(dp2px(1));
        resultCard.setRadius(dp2px(10));
        resultCard.setContentPadding(0, 0, 0, 0);
        resultCard.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        resultText = new TextView(this);
        resultText.setTextSize(13);
        resultText.setTextColor(getColorRes(com.google.android.material.R.attr.colorOnSurface));
        resultText.setMovementMethod(ScrollingMovementMethod.getInstance());
        resultText.setPadding(dp2px(12), dp2px(12), dp2px(12), dp2px(12));
        resultText.setBackgroundColor(Color.TRANSPARENT);

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(resultText);
        resultCard.addView(scrollView);
        root.addView(resultCard);

        setContentView(root);

        SpannableStringBuilder hint = new SpannableStringBuilder();
        hint.append("就绪,点击上方按钮开始测试");
        hint.setSpan(new ForegroundColorSpan(C_BLUE), 0, hint.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        resultText.setText(hint);
    }

    // =========================================================================
    // Engine
    // =========================================================================

    private void initEngine() {
        inCallCount = 0;
        outCallCount = 0;
        engine = new LayoutEngine(
                new LayoutEngine.BoundaryInterface() {
                    @Override public int getLeftBound() { return BOUNDARY_LEFT; }
                    @Override public int getTopBound() { return BOUNDARY_TOP; }
                    @Override public int getRightBound() { return BOUNDARY_RIGHT; }
                    @Override public int getBottomBound() { return BOUNDARY_BOTTOM; }
                },
                new LayoutEngine.WindowInterface() {
                    @Override public void in(int column, int row) { inCallCount++; }
                    @Override public void out(int column, int row) { outCallCount++; }
                    @Override public void onWindowCalculated(int colStart, int rowStart, int colEnd, int rowEnd) {}
                    @Override public int getColWidth(int column) { return tileSizePx; }
                    @Override public int getRowHeight(int row) { return tileSizePx; }
                });
        engine.setWindowWidth(windowWidth);
        engine.setWindowHeight(windowHeight);
        engine.seek(0, 0, 0, 0);
    }

    // =========================================================================
    // Env text
    // =========================================================================

    private void updateEnvText() {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        int t = sb.length();
        sb.append("测试环境\n");
        sb.setSpan(new StyleSpan(Typeface.BOLD), t, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new ForegroundColorSpan(C_TITLE), t, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        appendStyled(sb, "屏幕尺寸 ", Typeface.NORMAL, C_ENV_LABEL);
        appendStyled(sb, windowWidth + " x " + windowHeight + " px", Typeface.BOLD, C_ENV_VAL);
        sb.append("\n");
        appendStyled(sb, "瓦片大小 ", Typeface.NORMAL, C_ENV_LABEL);
        appendStyled(sb, TILE_SIZE_DP + " dp (" + tileSizePx + " px)", Typeface.BOLD, C_ENV_VAL);
        sb.append("\n");
        appendStyled(sb, "数据边界 ", Typeface.NORMAL, C_ENV_LABEL);
        appendStyled(sb, String.valueOf(BOUNDARY_LEFT) + " ~ " + String.valueOf(BOUNDARY_RIGHT) + " (伪无限)", Typeface.BOLD, C_ENV_VAL);

        envText.setText(sb);

        lastEnvPlain = "测试环境\n" +
                "屏幕尺寸: " + windowWidth + " x " + windowHeight + " px\n" +
                "瓦片大小: " + TILE_SIZE_DP + " dp (" + tileSizePx + " px)\n" +
                "数据边界: " + BOUNDARY_LEFT + " ~ " + BOUNDARY_RIGHT + " (伪无限)";
    }

    // =========================================================================
    // Utils
    // =========================================================================

    private int getTestCount() {
        try {
            int count = Integer.parseInt(countInput.getText().toString().trim());
            if (count <= 0) return 1000;
            if (count > 100000) return 100000;
            return count;
        } catch (NumberFormatException e) {
            return 1000;
        }
    }

    private float randomStepPx() {
        return STEP_CANDIDATES_PX[random.nextInt(STEP_CANDIDATES_PX.length)];
    }

    // =========================================================================
    // Sync benchmark
    // =========================================================================

    private void runSyncBenchmark() {
        if (testing) return;
        int count = getTestCount();
        showLoading("同步测试中... (" + count + " 次)");
        testing = true;

        new Thread(() -> {
            long[] times = new long[count];

            long wallStart = System.nanoTime();
            for (int i = 0; i < count; i++) {
                float sx = randomStepPx() * (random.nextInt(3) - 1);
                float sy = randomStepPx() * (random.nextInt(3) - 1);
                long t0 = System.nanoTime();
                engine.sync(sx, sy);
                times[i] = System.nanoTime() - t0;
            }
            long wallTime = System.nanoTime() - wallStart;

            runOnUiThread(() -> {
                dismissLoading();
                testing = false;
                updateEnvText();
                buildResult("同步(滚动)测试", count, times, wallTime, null);
            });
        }).start();
    }

    // =========================================================================
    // Seek benchmark
    // =========================================================================

    private void runSeekBenchmark() {
        if (testing) return;
        int count = getTestCount();
        showLoading("定位测试中... (" + count + " 次)");
        testing = true;

        new Thread(() -> {
            long[] times = new long[count];

            long wallStart = System.nanoTime();
            for (int i = 0; i < count; i++) {
                int col = random.nextInt();
                int row = random.nextInt();
                long t0 = System.nanoTime();
                engine.seek(col, row, 0, 0);
                times[i] = System.nanoTime() - t0;
            }
            long wallTime = System.nanoTime() - wallStart;

            runOnUiThread(() -> {
                dismissLoading();
                testing = false;
                updateEnvText();
                buildResult("定位(跳转)测试", count, times, wallTime,
                        "坐标范围: 全 Integer 随机 (offset=0,0)");
            });
        }).start();
    }

    // =========================================================================
    // To the End - single extreme boundary jump
    // =========================================================================

    private void runToTheEnd() {
        if (testing) return;

        final int[][] targets = {
                {0, BOUNDARY_TOP},
                {BOUNDARY_RIGHT, BOUNDARY_TOP},
                {BOUNDARY_RIGHT, 0},
                {BOUNDARY_RIGHT, BOUNDARY_BOTTOM},
                {0, BOUNDARY_BOTTOM},
                {BOUNDARY_LEFT, BOUNDARY_BOTTOM},
                {BOUNDARY_LEFT, 0},
                {BOUNDARY_LEFT, BOUNDARY_TOP},
        };
        final String[] labels = {
                "最上边", "右上角", "最右边", "右下角",
                "最下边", "左下角", "最左边", "左上角"
        };

        int pick = random.nextInt(targets.length);
        final int fCol = targets[pick][0];
        final int fRow = targets[pick][1];
        final String label = labels[pick];

        showLoading("正在跳转到 " + label + " ...");
        testing = true;

        new Thread(() -> {
            inCallCount = 0;
            outCallCount = 0;
            long t0 = System.nanoTime();
            engine.seek(fCol, fRow, 0, 0);
            long elapsed = System.nanoTime() - t0;

            runOnUiThread(() -> {
                dismissLoading();
                testing = false;
                updateEnvText();

                LayoutModel m = engine.getLayoutModel();
                int cols = (int) Math.ceil((double) windowWidth / tileSizePx);
                int rows = (int) Math.ceil((double) windowHeight / tileSizePx);
                int visible = (m.colEnd - m.colStart + 1) * (m.rowEnd - m.rowStart + 1);

                // Plain text
                lastResultPlain = "═══ 极端跳转: " + label + " ═══\n" +
                        "目标: col=" + fCol + " row=" + fRow + "\n" +
                        "耗时: " + formatNanos(elapsed) + "\n" +
                        "\n" +
                        "引擎位置: col[" + m.colStart + ".." + m.colEnd +
                        "] row[" + m.rowStart + ".." + m.rowEnd + "]\n" +
                        "offset(" + formatFloat(m.offsetX) + ", " + formatFloat(m.offsetY) +
                        ") total(" + m.totalWidth + ", " + m.totalHeight + ")\n" +
                        "in() 调用: " + inCallCount + "\n" +
                        "可见瓦片: " + visible + " (约 " + cols + "x" + rows + " 列行)";

                // Styled text
                SpannableStringBuilder sb = new SpannableStringBuilder();
                int ts = sb.length();
                sb.append("═══ 极端跳转: ").append(label).append(" ═══\n");
                sb.setSpan(new StyleSpan(Typeface.BOLD), ts, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.setSpan(new ForegroundColorSpan(C_TITLE), ts, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                appendLine(sb, "目标坐标", "col=" + fCol + "  row=" + fRow, C_ORANGE);
                appendLine(sb, "耗时", formatNanos(elapsed), C_PURPLE);

                sb.append("\n");
                appendLine(sb, "引擎位置", "col[" + m.colStart + ".." + m.colEnd + "] row[" + m.rowStart + ".." + m.rowEnd + "]", C_GREEN);
                appendLine(sb, "偏移/总量", "offset(" + formatFloat(m.offsetX) + ", " + formatFloat(m.offsetY)
                        + ")  total(" + m.totalWidth + ", " + m.totalHeight + ")", C_ENV_LABEL);
                appendLine(sb, "in() 调用", String.valueOf(inCallCount), C_BLUE);
                appendLine(sb, "可见瓦片", visible + " (约 " + cols + "x" + rows + " 列行)", C_GREEN);

                resultText.setText(sb);

                Toast.makeText(LayoutEngineBenchActivity.this,
                        "到达" + label, Toast.LENGTH_SHORT).show();
            });
        }).start();
    }

    // =========================================================================
    // Build result (batch tests: sync & seek)
    // =========================================================================

    private void buildResult(String title, int count, long[] times,
                             long wallTime, String extraLine) {
        Arrays.sort(times);
        long total = 0;
        for (long t : times) total += t;
        double avg = (double) total / count;
        long min = times[0], max = times[times.length - 1];
        long median = times[count / 2];
        long p95 = times[(int) (count * 0.95)];
        long p99 = times[(int) (count * 0.99)];

        LayoutModel m = engine.getLayoutModel();
        int cols = (int) Math.ceil((double) windowWidth / tileSizePx);
        int rows = (int) Math.ceil((double) windowHeight / tileSizePx);
        int visible = (m.colEnd - m.colStart + 1) * (m.rowEnd - m.rowStart + 1);

        // Plain text
        StringBuilder plainSb = new StringBuilder();
        plainSb.append("═══ ").append(title).append(" ═══\n");
        if (extraLine != null) plainSb.append(extraLine).append("\n");
        plainSb.append("测试次数: ").append(count).append("\n");
        plainSb.append("总耗时: ").append(formatMs(wallTime)).append("\n");
        plainSb.append("吞吐量: ").append(formatThroughput(count, wallTime)).append("\n");
        plainSb.append("\n");
        plainSb.append("平均值: ").append(formatNanos(avg)).append("\n");
        plainSb.append("最小值: ").append(formatNanos(min)).append("\n");
        plainSb.append("最大值: ").append(formatNanos(max)).append("\n");
        plainSb.append("中位数: ").append(formatNanos(median)).append("\n");
        plainSb.append("P95:    ").append(formatNanos(p95)).append("\n");
        plainSb.append("P99:    ").append(formatNanos(p99)).append("\n");
        plainSb.append("\n");
        plainSb.append("可见瓦片: ").append(visible).append(" (约 ").append(cols).append("x").append(rows).append(" 列行)\n");
        plainSb.append("\n");
        plainSb.append("引擎位置: col[").append(m.colStart).append("..").append(m.colEnd)
                .append("] row[").append(m.rowStart).append("..").append(m.rowEnd).append("]\n");
        plainSb.append("offset(").append(formatFloat(m.offsetX)).append(", ").append(formatFloat(m.offsetY))
                .append(") total(").append(m.totalWidth).append(", ").append(m.totalHeight).append(")");
        lastResultPlain = plainSb.toString();

        // Styled text
        SpannableStringBuilder sb = new SpannableStringBuilder();

        int tStart = sb.length();
        sb.append("═══ ").append(title).append(" ═══\n");
        sb.setSpan(new StyleSpan(Typeface.BOLD), tStart, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new ForegroundColorSpan(C_TITLE), tStart, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        if (extraLine != null) {
            int es = sb.length();
            sb.append(extraLine).append("\n");
            sb.setSpan(new ForegroundColorSpan(C_ORANGE), es, sb.length() - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        appendLine(sb, "测试次数", String.valueOf(count), C_GREEN);
        appendLine(sb, "总耗时", formatMs(wallTime), C_PURPLE);
        appendLine(sb, "吞吐量", formatThroughput(count, wallTime), C_BLUE);

        sb.append("\n");

        appendLine(sb, "平均值", formatNanos(avg), C_PURPLE);
        appendLine(sb, "最小值", formatNanos(min), C_GREEN);
        appendLine(sb, "最大值", formatNanos(max), C_RED);
        appendLine(sb, "中位数", formatNanos(median), C_BLUE);
        appendLine(sb, "P95", formatNanos(p95), C_ORANGE);
        appendLine(sb, "P99", formatNanos(p99), C_PURPLE);

        sb.append("\n");

        appendLine(sb, "可见瓦片", visible + " (约 " + cols + "x" + rows + " 列行)", C_GREEN);

        sb.append("\n");

        appendLine(sb, "引擎位置", "col[" + m.colStart + ".." + m.colEnd + "] row[" + m.rowStart + ".." + m.rowEnd + "]", C_ENV_LABEL);
        appendLine(sb, "偏移/总量", "offset(" + formatFloat(m.offsetX) + ", " + formatFloat(m.offsetY)
                + ")  total(" + m.totalWidth + ", " + m.totalHeight + ")", C_ENV_LABEL);

        resultText.setText(sb);
    }

    // =========================================================================
    // Styling helpers
    // =========================================================================

    private static void appendLine(SpannableStringBuilder sb, String label, String value, int valueColor) {
        int ls = sb.length();
        sb.append(label).append(": ");
        sb.setSpan(new ForegroundColorSpan(Color.rgb(80, 80, 80)), ls, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        int vs = sb.length();
        sb.append(value).append("\n");
        sb.setSpan(new ForegroundColorSpan(valueColor), vs, vs + value.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (label.equals("平均值") || label.equals("最小值") || label.equals("最大值")
                || label.equals("中位数") || label.equals("P95") || label.equals("P99")) {
            sb.setSpan(new StyleSpan(Typeface.BOLD), vs, vs + value.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private static void appendStyled(SpannableStringBuilder sb, String text, int typeface, int color) {
        int s = sb.length();
        sb.append(text);
        sb.setSpan(new StyleSpan(typeface), s, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new ForegroundColorSpan(color), s, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    // =========================================================================
    // Copy
    // =========================================================================

    private void copyResult() {
        if (lastEnvPlain == null) return;
        StringBuilder copy = new StringBuilder();
        copy.append(lastEnvPlain).append("\n\n");
        if (lastResultPlain != null) copy.append(lastResultPlain);

        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("LayoutEngine Benchmark", copy.toString()));
        Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show();
    }

    // =========================================================================
    // Loading dialog
    // =========================================================================

    private androidx.appcompat.app.AlertDialog loadingDialog;

    private void showLoading(String msg) {
        FrameLayout content = new FrameLayout(this);
        content.setPadding(dp2px(32), dp2px(24), dp2px(32), dp2px(24));

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setGravity(Gravity.CENTER);

        CircularProgressIndicator spinner = new CircularProgressIndicator(this, null,
                com.google.android.material.R.attr.circularProgressIndicatorStyle);
        spinner.setIndicatorSize(dp2px(40));
        inner.addView(spinner);

        TextView tv = new TextView(this);
        tv.setText(msg);
        tv.setTextSize(15);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, dp2px(20), 0, 0);
        inner.addView(tv);

        content.addView(inner);

        loadingDialog = new MaterialAlertDialogBuilder(this)
                .setView(content)
                .setCancelable(false)
                .show();
    }

    private void dismissLoading() {
        if (loadingDialog != null && loadingDialog.isShowing()) {
            loadingDialog.dismiss();
            loadingDialog = null;
        }
    }

    // =========================================================================
    // Formatting
    // =========================================================================

    private static String formatNanos(double nanos) {
        if (nanos < 1000) return String.format(Locale.US, "%.2f ns", nanos);
        if (nanos < 1_000_000) return String.format(Locale.US, "%.2f us (%.0f ns)", nanos / 1000, nanos);
        return String.format(Locale.US, "%.3f ms (%.0f ns)", nanos / 1_000_000, nanos);
    }

    private static String formatMs(long nanos) {
        return String.format(Locale.US, "%.3f ms (%.3f s)", nanos / 1_000_000.0, nanos / 1_000_000_000.0);
    }

    private static String formatThroughput(int count, long wallNanos) {
        double sec = wallNanos / 1_000_000_000.0;
        if (sec <= 0) return "N/A";
        return String.format(Locale.US, "%.0f ops/s", count / sec);
    }

    private static String formatFloat(float v) {
        if (v == (int) v) return String.valueOf((int) v);
        return String.format(Locale.US, "%.1f", v);
    }

    private int getColorRes(int attr) {
        android.content.res.TypedArray ta = obtainStyledAttributes(new int[]{attr});
        int color = ta.getColor(0, Color.LTGRAY);
        ta.recycle();
        return color;
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
