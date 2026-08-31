package com.ahheng.tile2d.app.data;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;

import com.ahheng.tile2d.app.BaseActivity;
import com.ahheng.tile2d.dimen.Measurable;
import com.ahheng.tile2d.dimen.MeasurableDimenProvider;
import com.ahheng.tile2d.widget.layout.TileLayout;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 数据表 Demo：纯文本表格（CSV）本地编辑器。
 * - 持久化：首次启动把 assets/default.csv 落到私有目录 table.csv，之后读写私有文件；右上角菜单可重置。
 * - 渲染：TileLayout + {@link MeasurableDimenProvider} 自动列宽；表头/正文两种 TextView 样式各占一个
 *   瓦片类型；正文按行奇偶做斑马纹。
 * - 编辑：点击单元格弹 Material3 输入框（TextInputLayout）；保存即同步落盘。
 * - 结构：长按单元格弹菜单（删除本列/本行、左移/右移列、上移/下移行）；底部新建列/行并 seek 过去查看。
 * - 文本：底部“复制全文”（TSV/CSV 二选一复制到剪贴板）与“解析”（TSV/CSV 二选一全量替换）。
 */
public class TableActivity extends BaseActivity {

    private static final String FILE_NAME = "table.csv";

    // 瓦片类型：每种视觉样式占一个类型
    private static final int TYPE_HEADER = 0; // 表头样式（第 0 行 / 第 0 列）
    private static final int TYPE_CELL = 1;   // 正文样式（行内斑马纹）

    // 长按菜单项 ID
    private static final int MENU_DELETE_COLUMN = 1;
    private static final int MENU_DELETE_ROW = 2;
    private static final int MENU_MOVE_LEFT = 3;
    private static final int MENU_MOVE_RIGHT = 4;
    private static final int MENU_MOVE_UP = 5;
    private static final int MENU_MOVE_DOWN = 6;

    // 右上角菜单项 ID（nextId 避让基类菜单）
    private static final int MENU_RESET = BaseActivity.nextId();

    private TileLayout tileLayout;
    private TableAdapter adapter;
    private TextTableModel model;
    private MeasurableDimenProvider dimenProvider;

    // 主题色（Material3）
    private int headerBg, headerText, cellBg, zebraBg, cellText, outline, barBg;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        resolveColors();

        ensureDataFile();
        model = TextTableParser.parseCsv(readText(getTableFile()));
        if (model.getRowCount() == 0) {
            // 空文件兜底：保证至少一张可用表格
            model.addRow(Arrays.asList("名称", "值"));
            model.addRow(Arrays.asList("示例", "0"));
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        tileLayout = new TileLayout(this);
        root.addView(tileLayout, new LinearLayout.LayoutParams(-1, 0, 1f));
        buildBottomBar(root);
        setContentView(root);

        int padding = dp2px(8);
        tileLayout.setPadding(padding, padding, padding, padding);
        tileLayout.setZoomEnabled(true);
        tileLayout.setDebugMode(isDebugMode());

        adapter = new TableAdapter();
        tileLayout.setAdapter(adapter);
        dimenProvider = new MeasurableDimenProvider(dp2px(72), dp2px(34), adapter);
        dimenProvider.setMinDefault(true);
        tileLayout.setDimenProvider(dimenProvider);
        dimenProvider.full(); // 全量测量一次：自动列宽/行高
        tileLayout.seek(0, 0, 0, 0);
    }

    // ========== 数据文件（私有目录持久化） ==========

    private File getTableFile() {
        return new File(getFilesDir(), FILE_NAME);
    }

    /** assets 里的默认 CSV 不存在于私有目录时写出（首次启动）。 */
    private void ensureDataFile() {
        File f = getTableFile();
        if (f.exists()) return;
        try {
            copyAssetToFile("default.csv", f);
        } catch (IOException e) {
            writeText(f, "名称,值\n示例,0\n");
        }
    }

    private void copyAssetToFile(String asset, File target) throws IOException {
        try (InputStream in = getAssets().open(asset);
             OutputStream out = new FileOutputStream(target)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        }
    }

    private String readText(File f) {
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[(int) f.length()];
            int off = 0, n;
            while (off < buf.length && (n = in.read(buf, off, buf.length - off)) != -1) off += n;
            return new String(buf, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private void writeText(File f, String text) {
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            showToast("保存失败：" + e.getMessage());
        }
    }

    /** 模型变更后同步落盘（编辑保存 / 行列增删移动 / 解析替换 / 重置）。 */
    private void save() {
        writeText(getTableFile(), TextTableSynthesizer.toCsv(model));
    }

    // ========== 底部操作栏 ==========

    private void buildBottomBar(LinearLayout root) {
        root.addView(buildButtonRow("新建列", "新建行"), new LinearLayout.LayoutParams(-1, -2));
        root.addView(buildButtonRow("复制全文", "解析"), new LinearLayout.LayoutParams(-1, -2));
    }

    private LinearLayout buildButtonRow(String leftText, String rightText) {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setPadding(dp2px(12), dp2px(6), dp2px(12), dp2px(6));
        bar.setBackgroundColor(barBg);

        MaterialButton left = new MaterialButton(this);
        left.setText(leftText);
        MaterialButton right = new MaterialButton(this);
        right.setText(rightText);

        switch (leftText) {
            case "新建列" -> left.setOnClickListener(v -> addColumnAndView());
            case "新建行" -> left.setOnClickListener(v -> addRowAndView());
            case "复制全文" -> left.setOnClickListener(v -> showCopyDialog());
            case "解析" -> left.setOnClickListener(v -> showParseDialog());
        }
        switch (rightText) {
            case "新建列" -> right.setOnClickListener(v -> addColumnAndView());
            case "新建行" -> right.setOnClickListener(v -> addRowAndView());
            case "复制全文" -> right.setOnClickListener(v -> showCopyDialog());
            case "解析" -> right.setOnClickListener(v -> showParseDialog());
        }

        bar.addView(left, leftLp());
        bar.addView(right, rightLp());
        return bar;
    }

    private LinearLayout.LayoutParams leftLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.setMargins(0, 0, dp2px(4), 0); // 与右侧按钮留 8dp 间距
        return lp;
    }

    private LinearLayout.LayoutParams rightLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.setMargins(dp2px(4), 0, 0, 0);
        return lp;
    }

    // ========== 结构操作 ==========

    /** 行列结构变更后的统一收尾：落盘 + 重测尺寸（列宽随行列错位）+ 全屏刷新 + 视口吸附回边界。 */
    private void afterStructureChanged() {
        save();
        dimenProvider.full();
        tileLayout.updateAll();
        tileLayout.snap();
    }

    private void addColumnAndView() {
        model.addColumn();
        afterStructureChanged();
        tileLayout.seek(model.getColumnCount() - 1, 0, 0, 0); // 跳到新列
    }

    private void addRowAndView() {
        model.addRow();
        afterStructureChanged();
        tileLayout.seek(0, model.getRowCount() - 1, 0, 0); // 跳到新行
    }

    private void removeColumn(int column) {
        if (model.getColumnCount() <= 1) {
            showToast("至少保留一列");
            return;
        }
        model.removeColumn(column);
        afterStructureChanged();
    }

    private void removeRow(int row) {
        if (model.getRowCount() <= 1) {
            showToast("至少保留一行");
            return;
        }
        model.removeRow(row);
        afterStructureChanged();
    }

    private void moveColumn(int from, int to) {
        if (to < 0 || to >= model.getColumnCount()) return;
        model.moveColumn(from, to);
        afterStructureChanged();
    }

    private void moveRow(int from, int to) {
        if (to < 0 || to >= model.getRowCount()) return;
        model.moveRow(from, to);
        afterStructureChanged();
    }

    // ========== 编辑对话框（点击单元格） ==========

    private void showEditDialog(int column, int row) {
        TextInputLayout til = new TextInputLayout(this);
        til.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        til.setHint("单元格内容");
        til.setLayoutParams(new ViewGroup.LayoutParams(-1, -2));
        til.setPadding(dp2px(16), 0, dp2px(16), 0); // 不与对话框边缘贴死
        TextInputEditText input = new TextInputEditText(this);
        input.setText(model.get(row, column));
        input.setBackground(null); // Outlined 描边由 TextInputLayout 提供，EditText 自带背景会遮住
        input.setSingleLine(false);
        input.setMinLines(3);
        input.setMaxLines(6);
        input.setMaxHeight(maxHeightByScreen(0.30f, 120f)); // 最大高度=屏高30%（下限120dp），超长内部滚动
        input.setGravity(Gravity.TOP | Gravity.START);
        til.addView(input);

        String pos = (row == 0 ? "表头" : "第 " + row + " 行")
                + " · " + (column == 0 ? "行首" : "第 " + column + " 列");
        new MaterialAlertDialogBuilder(this)
                .setTitle("编辑 " + pos)
                .setView(til)
                .setPositiveButton("保存", (d, w) -> {
                    model.set(row, column, input.getText().toString());
                    save();
                    dimenProvider.measure(column, row, column, row); // 该格所在列/行尺寸跟随内容
                    tileLayout.updateAll(); // 全屏刷新
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ========== 长按菜单（单元格结构操作） ==========

    private void showCellMenu(View anchor, int column, int row) {
        PopupMenu popup = new PopupMenu(this, anchor);
        Menu menu = popup.getMenu();
        menu.add(Menu.NONE, MENU_DELETE_COLUMN, 0, "删除本列");
        menu.add(Menu.NONE, MENU_DELETE_ROW, 0, "删除本行");
        menu.add(Menu.NONE, MENU_MOVE_LEFT, 0, "左移列");
        menu.add(Menu.NONE, MENU_MOVE_RIGHT, 0, "右移列");
        menu.add(Menu.NONE, MENU_MOVE_UP, 0, "上移行");
        menu.add(Menu.NONE, MENU_MOVE_DOWN, 0, "下移行");
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case MENU_DELETE_COLUMN -> removeColumn(column);
                case MENU_DELETE_ROW -> removeRow(row);
                case MENU_MOVE_LEFT -> moveColumn(column, column - 1);
                case MENU_MOVE_RIGHT -> moveColumn(column, column + 1);
                case MENU_MOVE_UP -> moveRow(row, row - 1);
                case MENU_MOVE_DOWN -> moveRow(row, row + 1);
            }
            return true;
        });
        popup.show();
    }

    // ========== 复制全文（底部按钮） ==========

    private void showCopyDialog() {
        String csv = TextTableSynthesizer.toCsv(model);
        String tsv = TextTableSynthesizer.toTsv(model);

        TextView tv = new TextView(this);
        tv.setText(csv);
        tv.setTextIsSelectable(true);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tv.setPadding(dp2px(16), dp2px(8), dp2px(16), dp2px(8));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(tv);

        new MaterialAlertDialogBuilder(this)
                .setTitle("表格全文（" + model.getRowCount() + "×" + model.getColumnCount() + "）")
                .setView(scroll)
                .setPositiveButton("复制TSV", (d, w) -> copyText(tsv))
                .setNeutralButton("复制CSV", (d, w) -> copyText(csv))
                .setNegativeButton("关闭", null)
                .show();
    }

    // ========== 解析替换（底部按钮） ==========

    private void showParseDialog() {
        TextInputLayout til = new TextInputLayout(this);
        til.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        til.setHint("粘贴 TSV / CSV 文本，将全量替换当前表格");
        til.setLayoutParams(new ViewGroup.LayoutParams(-1, -2));
        til.setPadding(dp2px(16), 0, dp2px(16), 0); // 不与对话框边缘贴死
        TextInputEditText input = new TextInputEditText(this);
        input.setText(TextTableSynthesizer.toCsv(model));
        input.setBackground(null); // Outlined 描边由 TextInputLayout 提供，EditText 自带背景会遮住
        input.setSingleLine(false);
        input.setMinLines(5);
        input.setMaxLines(12);
        input.setMaxHeight(maxHeightByScreen(0.45f, 160f)); // 最大高度=屏高45%（下限160dp），超长内部滚动
        input.setGravity(Gravity.TOP | Gravity.START);
        til.addView(input);

        new MaterialAlertDialogBuilder(this)
                .setTitle("解析并全量替换")
                .setView(til)
                .setPositiveButton("解析TSV", (d, w) -> parseReplace(input.getText().toString(), true))
                .setNeutralButton("解析CSV", (d, w) -> parseReplace(input.getText().toString(), false))
                .setNegativeButton("取消", null)
                .show();
    }

    private void parseReplace(String text, boolean tsv) {
        TextTableModel parsed = tsv ? TextTableParser.parseTsv(text) : TextTableParser.parseCsv(text);
        if (parsed.getRowCount() == 0) {
            showToast("解析结果为空");
            return;
        }
        model = parsed;
        afterStructureChanged();
        tileLayout.seek(0, 0, 0, 0);
        showToast("已替换为 " + model.getRowCount() + "×" + model.getColumnCount() + " 表格");
    }

    // ========== 剪贴板 ==========

    private void copyText(String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("table", text));
        showToast("已复制 " + text.length() + " 字符到剪贴板");
    }

    // ========== 重置表格（右上角菜单） ==========

    private void resetTable() {
        try {
            copyAssetToFile("default.csv", getTableFile());
            model = TextTableParser.parseCsv(readText(getTableFile()));
            afterStructureChanged();
            tileLayout.seek(0, 0, 0, 0);
            showToast("已重置为内置版本");
        } catch (IOException e) {
            showToast("重置失败：" + e.getMessage());
        }
    }

    // ========== 适配器与瓦片 ==========

    private class TableAdapter extends TileLayout.Adapter {

        @Override
        public int getLeftBound() {
            return 0;
        }

        @Override
        public int getTopBound() {
            return 0;
        }

        @Override
        public int getRightBound() {
            return model.getColumnCount() - 1;
        }

        @Override
        public int getBottomBound() {
            return model.getRowCount() - 1;
        }

        @Override
        public int getTileType(int column, int row) {
            return (row == 0 || column == 0) ? TYPE_HEADER : TYPE_CELL;
        }

        @Override
        public TileLayout.TileHolder onCreateTileHolder(int type) {
            return new CellHolder(type);
        }

        @Override
        public void onBindTileHolder(TileLayout.TileHolder holder, int column, int row) {
            ((CellHolder) holder).bind(column, row);
        }
    }

    /** 单元格持有者：TextView 承载文本，实现 Measurable 供自动列宽测量；正文行按奇偶做斑马纹。 */
    private class CellHolder extends TileLayout.TileHolder implements Measurable {

        final TextView textView;
        final int type;
        private final GradientDrawable bg;

        CellHolder(int type) {
            super(new TextView(TableActivity.this));
            this.type = type;
            textView = (TextView) itemView;
            // 未 addView 的 TextView 在 setText 时 checkForRelayout 会读 LayoutParams.width，
            // 必须保证非空（MeasurableDimenProvider.full() 的临时测量对象不经过 addViewInLayout）
            textView.setLayoutParams(new ViewGroup.LayoutParams(-2, -2));
            textView.setGravity(Gravity.CENTER);
            textView.setTypeface(null, type == TYPE_HEADER ? Typeface.BOLD : Typeface.NORMAL);
            textView.setPadding(dp2px(6), dp2px(2), dp2px(6), dp2px(2));
            bg = new GradientDrawable();
            textView.setBackground(bg);
            textView.setTextColor(type == TYPE_HEADER ? headerText : cellText);
        }

        void bind(int column, int row) {
            bg.setColor(type == TYPE_HEADER ? headerBg : (row % 2 == 0 ? cellBg : zebraBg));
            // 圆角与描边宽度均跟随缩放（缩放后重绑时更新）
            bg.setCornerRadius(dpTopx(4) * getScaleFactor());
            bg.setStroke((int) Math.ceil(dpTopx(0.5f) * getScaleFactor()), outline);
            textView.setBackground(bg);
            textView.setText(model.get(row, column));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP,
                    (type == TYPE_HEADER ? 14 : 13) * getScaleFactor());
            textView.setOnClickListener(v -> showEditDialog(column, row));
            textView.setOnLongClickListener(v -> {
                requestDisallowInterceptTouchEvent(true);
                showCellMenu(textView, column, row);
                return true;
            });
        }

        @Override
        public void measure(int widthMeasureSpec, int heightMeasureSpec, int[] out) {
            textView.measure(widthMeasureSpec, heightMeasureSpec);
            out[0] = textView.getMeasuredWidth();
            out[1] = textView.getMeasuredHeight();
        }
    }

    // ========== 主题色（Material3） ==========

    private void resolveColors() {
        // 全部使用 material 1.5.0+（M3 稳定版）确定存在的 attr
        headerBg = resolveColor(com.google.android.material.R.attr.colorPrimaryContainer);
        headerText = resolveColor(com.google.android.material.R.attr.colorOnPrimaryContainer);
        cellBg = resolveColor(com.google.android.material.R.attr.colorSurface);       // 偶数行
        zebraBg = resolveColor(com.google.android.material.R.attr.colorSurfaceVariant); // 奇数行
        cellText = resolveColor(com.google.android.material.R.attr.colorOnSurface);
        outline = resolveColor(com.google.android.material.R.attr.colorOutlineVariant);
        barBg = resolveColor(com.google.android.material.R.attr.colorSurface);
    }

    private int resolveColor(int attr) {
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(attr, tv, true);
        return tv.data;
    }

    /** 按当前屏幕高度比例计算输入框最大高度（横竖屏自适应），带最小 dp 下限防极端矮屏。 */
    private int maxHeightByScreen(float ratio, float minDp) {
        int byScreen = (int) (getResources().getDisplayMetrics().heightPixels * ratio);
        return Math.max(dp2px(minDp), byScreen);
    }

    // ========== BaseActivity 钩子与菜单 ==========

    @Override
    public boolean hasMaxMode() {
        return false;
    }

    @Override
    public boolean hasPlanMode() {
        return false;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu); // 保留 Debug 模式
        menu.add(Menu.NONE, MENU_RESET, Menu.NONE, "重置表格")
                .setOnMenuItemClickListener(this)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        return true;
    }

    @Override
    public boolean onMenuItemClick(@NonNull MenuItem menuItem) {
        if (menuItem.getItemId() == MENU_RESET) {
            resetTable();
            return true;
        }
        return super.onMenuItemClick(menuItem);
    }

    @Override
    protected void onDebugModeChanged(boolean enabled) {
        if (tileLayout != null) tileLayout.setDebugMode(enabled);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tileLayout != null) tileLayout.setAdapter(null);
    }
}