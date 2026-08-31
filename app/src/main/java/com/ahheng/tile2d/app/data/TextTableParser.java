package com.ahheng.tile2d.app.data;

import java.util.ArrayList;
import java.util.List;

/**
 * 纯文本表格解析器：负责把 TSV / CSV 文本**解析**为 {@link TextTableModel}。
 * 与模型解耦——解析实现可独立演进（换解析库、流式解析等）而不影响模型。
 * 序列化（模型 → 文本，持久化用）见 {@link TextTableSynthesizer}，两者互为逆操作。
 *
 * 标准与行为：
 * - TSV：无转义、tab 分隔（IANA text/tab-separated-values 约定）；字段内不允许 tab，尾空字段保留。
 * - CSV：符合 RFC 4180 —— 逗号分隔、"" 转义、**引号内换行（multiline field）**。
 * - 统一逐字符扫描：兼容 \r\n 与 \n；末尾换行不产生额外空记录；中间空行保留为空记录（空字段行）。
 * - 列数归一：各行不足的列补空串，保证规整矩形网格。
 */
public final class TextTableParser {

    private TextTableParser() {
    }

    public static TextTableModel parseTsv(String text) {
        return parse(text, '\t', false);
    }

    public static TextTableModel parseCsv(String text) {
        return parse(text, ',', true);
    }

    private static TextTableModel parse(String text, char sep, boolean quoting) {
        TextTableModel model = new TextTableModel();
        if (text == null || text.isEmpty()) return model;

        List<List<String>> parsed = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoting && inQuotes) {
                // 引号内：任何字符（含换行）都是字段内容（RFC 4180 multiline field）
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"'); // 转义引号
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(c);
                }
            } else {
                if (quoting && c == '"') {
                    inQuotes = true;
                } else if (c == sep) {
                    row.add(field.toString());
                    field.setLength(0);
                } else if (c == '\r') {
                    // CRLF 的 \r 忽略，由 \n 换行
                } else if (c == '\n') {
                    row.add(field.toString());
                    field.setLength(0);
                    parsed.add(row);
                    row = new ArrayList<>();
                } else {
                    field.append(c);
                }
            }
        }
        // 收尾：无结尾换行的最后一条记录（末尾换行不产生额外空记录）
        if (!row.isEmpty() || field.length() > 0) {
            row.add(field.toString());
            parsed.add(row);
        }

        // 列数归一：不足补空串，保证规整矩形网格
        int cols = 0;
        for (List<String> r : parsed) cols = Math.max(cols, r.size());
        for (List<String> r : parsed) {
            while (r.size() < cols) r.add("");
            model.addRow(r);
        }
        return model;
    }
}