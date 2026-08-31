package com.ahheng.tile2d.app.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 二维表格数据模型（纯数据，不含解析）。
 * 类比 LayoutModel 之于布局引擎：本模型是表格数据的唯一来源，适配器/渲染层只读它。
 * 纯文本解析/序列化由独立的 {@link TextTableParser} 负责，实现方式可单独演进。
 *
 * 设计决策：
 * - 存储用 List&lt;List&lt;String&gt;&gt; 而非 String[][]：行列增删无需整体重建（编辑器友好）、
 *   支持从空表动态扩展；两层引用访问 O(1)，表格数据量级下性能足够。
 * - 不用稀疏结构：表格是规整矩形网格，空单元格用空串表示即可。
 */
public class TextTableModel {

    private final List<List<String>> rows = new ArrayList<>();

    // ========== 查询 ==========

    public int getRowCount() {
        return rows.size();
    }

    public int getColumnCount() {
        return rows.isEmpty() ? 0 : rows.get(0).size();
    }

    /** 取单元格值，越界返回空串（防御）。 */
    public String get(int row, int col) {
        if (row < 0 || row >= rows.size()) return "";
        List<String> r = rows.get(row);
        if (col < 0 || col >= r.size()) return "";
        return r.get(col);
    }

    // ========== 修改 ==========

    public void set(int row, int col, String value) {
        if (row < 0 || row >= rows.size()) return;
        List<String> r = rows.get(row);
        if (col < 0 || col >= r.size()) return;
        r.set(col, value == null ? "" : value);
    }

    /** 末尾追加一行空单元格。 */
    public void addRow() {
        rows.add(newRow(getColumnCount()));
    }

    public void insertRow(int row) {
        rows.add(row, newRow(getColumnCount()));
    }

    public void removeRow(int row) {
        if (row >= 0 && row < rows.size()) rows.remove(row);
    }

    /** 追加一行由外部提供的单元格（解析器/粘贴导入用），深拷贝防止外部修改。 */
    public void addRow(List<String> cells) {
        rows.add(new ArrayList<>(cells));
    }

    /** 末尾追加一列空单元格。 */
    public void addColumn() {
        for (List<String> r : rows) r.add("");
    }

    public void insertColumn(int col) {
        for (List<String> r : rows) r.add(col, "");
    }

    public void removeColumn(int col) {
        for (List<String> r : rows) {
            if (col >= 0 && col < r.size()) r.remove(col);
        }
    }

    /** 交换两列（左移/右移列用）：from 与 to 列整体互换。 */
    public void moveColumn(int from, int to) {
        int cols = getColumnCount();
        if (from == to || from < 0 || from >= cols || to < 0 || to >= cols) return;
        for (List<String> r : rows) {
            String tmp = r.get(from);
            r.set(from, r.get(to));
            r.set(to, tmp);
        }
    }

    /** 交换两行（上移/下移行用）：from 与 to 行整体互换。 */
    public void moveRow(int from, int to) {
        if (from == to || from < 0 || from >= rows.size() || to < 0 || to >= rows.size()) return;
        Collections.swap(rows, from, to);
    }

    private List<String> newRow(int cols) {
        List<String> row = new ArrayList<>(cols);
        for (int i = 0; i < cols; i++) row.add("");
        return row;
    }

    // ========== 快照 ==========

    /** 深拷贝（编辑器撤销 / 网络刷新对比用）。 */
    public TextTableModel copy() {
        TextTableModel model = new TextTableModel();
        for (List<String> row : rows) {
            model.rows.add(new ArrayList<>(row));
        }
        return model;
    }

    @Override
    public String toString() {
        return "TextTableModel(" + getRowCount() + "x" + getColumnCount() + ")";
    }
}