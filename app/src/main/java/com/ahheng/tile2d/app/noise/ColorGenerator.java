package com.ahheng.tile2d.app.noise;

public class ColorGenerator {

    private final int[] COLORS = {
        pack(2, 6, 23),      // 0.00 极深蓝 #020617
        pack(23, 37, 84),    // 0.09 深夜蓝 #172554
        pack(30, 64, 175),   // 0.18 钴蓝   #1e40af
        pack(59, 130, 246),  // 0.27 亮蓝   #3b82f6
        pack(14, 165, 233),  // 0.36 天蓝   #0ea5e9
        pack(6, 182, 212),   // 0.45 青蓝   #06b6d4
        pack(252, 211, 77),  // 0.54 浅金黄 #fcd34d
        pack(251, 191, 36),  // 0.63 琥珀   #fbbf24
        pack(249, 115, 22),  // 0.72 亮橙   #f97316
        pack(234, 88, 12),   // 0.81 深橙   #ea580c
        pack(220, 38, 38),   // 0.90 红     #dc2626
        pack(127, 29, 29),   // 1.00 暗红   #7f1d1d
    };

    public int getColor(double noise) {
        if (Double.isNaN(noise) || noise <= 0.0) {
            return COLORS[0];
        }
        if (noise >= 1.0) {
            return COLORS[COLORS.length - 1];
        }
        int idx = (int) (noise * COLORS.length);
        if (idx >= COLORS.length) {
            idx = COLORS.length - 1;
        }
        return COLORS[idx];
    }

    private static int pack(int r, int g, int b) {
        return 0xFF000000 | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }
}
