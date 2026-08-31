package com.ahheng.tile2d.app.data;

/**
 * 纯文本表格合成器（序列化器）：把 {@link TextTableModel} **合成**为 TSV / CSV 文本。
 * 与 {@link TextTableParser}（解析：文本 → 模型）互为逆操作，用于持久化
 * （保存到 assets、本地文件，或导出后由网络直链/编辑器复用）。
 * 与模型解耦——序列化实现可独立演进而不影响模型。
 *
 * 标准与行为（与解析器对称）：
 * - TSV：无转义、tab 分隔，字段原样输出（字段内不应含 tab，见 IANA text/tab-separated-values 约定）。
 * - CSV：符合 RFC 4180 —— 字段含分隔符 / 引号 / 换行时加引号包裹，内部引号 "" 转义。
 * - 矩形网格：按模型行数 × 列数逐格输出，行尾统一 \n。
 */
public final class TextTableSynthesizer {

    private TextTableSynthesizer() {
    }

    /** 合成（导出）为 TSV 文本。 */
    public static String toTsv(TextTableModel model) {
        return toText(model, '\t', false);
    }

    /** 合成（导出）为 CSV 文本。 */
    public static String toCsv(TextTableModel model) {
        return toText(model, ',', true);
    }

    private static String toText(TextTableModel model, char sep, boolean quoting) {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < model.getRowCount(); r++) {
            for (int c = 0; c < model.getColumnCount(); c++) {
                if (c > 0) sb.append(sep);
                sb.append(quote(model.get(r, c), sep, quoting));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /** CSV 合成：字段含分隔符/引号/换行时才加引号；TSV 原样输出。 */
    private static String quote(String field, char sep, boolean quoting) {
        if (!quoting) return field;
        if (field.indexOf(sep) >= 0 || field.indexOf('"') >= 0
                || field.indexOf('\n') >= 0 || field.indexOf('\r') >= 0) {
            return '"' + field.replace("\"", "\"\"") + '"';
        }
        return field;
    }
}