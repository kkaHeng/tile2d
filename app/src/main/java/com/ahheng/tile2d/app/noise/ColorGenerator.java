package com.ahheng.tile2d.app.noise;

public class ColorGenerator {

    // 24 种颜色，从亮蓝 → 青 → 绿 → 黄 → 橙 → 红 → 暖白 → 纯白
    // 整体亮度高，低噪区明亮，高噪区趋近白色
    private final int[] COLORS = {
        pack(59, 130, 246),   // #3B82F6  亮蓝
        pack(96, 165, 250),   // #60A5FA
        pack(147, 197, 253),  // #93C5FD
        pack(125, 211, 252),  // #7DD3FC
        pack(56, 189, 248),   // #38BDF8
        pack(34, 211, 238),   // #22D3EE  青
        pack(103, 232, 249),  // #67E8F9
        pack(110, 231, 183),  // #6EE7B7  青绿
        pack(52, 211, 153),   // #34D399  亮绿
        pack(163, 230, 53),   // #A3E635  黄绿
        pack(253, 224, 71),   // #FDE047  黄
        pack(250, 204, 21),   // #FACC15
        pack(251, 191, 36),   // #FBBF24  琥珀
        pack(251, 146, 60),   // #FB923C  橙
        pack(249, 115, 22),   // #F97316
        pack(239, 68, 68),    // #EF4444  红
        pack(248, 113, 113),  // #F87171  浅红
        pack(253, 186, 116),  // #FDBA74  橙红
        pack(254, 215, 170),  // #FED7AA  浅橙
        pack(254, 243, 199),  // #FEF3C7  浅黄
        pack(255, 251, 235),  // #FFFBEB  米白
        pack(255, 247, 237),  // #FFF7ED  暖白
        pack(255, 250, 240),  // #FFFAF0  花白
        pack(255, 255, 255)   // #FFFFFF  纯白
    };

    public int getColor(double noise) {
        if (Double.isNaN(noise) || noise <= 0.0) {
            return COLORS[0];
        }
        if (noise >= 1.0) {
            return COLORS[COLORS.length - 1];
        }

        double pos = noise * (COLORS.length - 1);
        int idx = (int) pos;
        double frac = pos - idx;

        if (idx >= COLORS.length - 1) {
            return COLORS[COLORS.length - 1];
        }

        int c1 = COLORS[idx];
        int c2 = COLORS[idx + 1];

        int r1 = (c1 >> 16) & 0xFF;
        int g1 = (c1 >> 8) & 0xFF;
        int b1 = c1 & 0xFF;
        int r2 = (c2 >> 16) & 0xFF;
        int g2 = (c2 >> 8) & 0xFF;
        int b2 = c2 & 0xFF;

        int r = (int) (r1 + (r2 - r1) * frac);
        int g = (int) (g1 + (g2 - g1) * frac);
        int b = (int) (b1 + (b2 - b1) * frac);

        return pack(r, g, b);
    }

    private static int pack(int r, int g, int b) {
        return 0xFF000000 | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }
}