package com.ahheng.tile2d.app.life;

// 生命游戏经典图案库（细胞自动机史上的标准图案，永不过时）
// 坐标为相对原点的 [列偏移, 行偏移] 扁平数组，行方向向下为正
public final class LifePatterns {

    private LifePatterns() {
    }

    // 滑翔机（Glider，1970 Conway 团队发现）：斜向永久移动，每 4 代前进 1 格
    public static final Pattern GLIDER = new Pattern("滑翔机", "斜向飞行，4 代移动 1 格", new int[]{
            1, 0,
            2, 1,
            0, 2, 1, 2, 2, 2
    });

    // 轻型飞船（LWSS）：水平移动，每 4 代前进 2 格（取向右飞的朝向，与滑翔机同向）
    public static final Pattern LWSS = new Pattern("轻型飞船", "向右飞行，4 代移动 2 格", new int[]{
            0, 0, 3, 0,
            4, 1,
            0, 2, 4, 2,
            1, 3, 2, 3, 3, 3, 4, 3
    });

    // 脉冲星（Pulsar）：周期 3 的振荡器，对称美感极强
    public static final Pattern PULSAR = new Pattern("脉冲星", "周期 3 振荡器", new int[]{
            2, 0, 3, 0, 4, 0, 8, 0, 9, 0, 10, 0,
            0, 2, 5, 2, 7, 2, 12, 2,
            0, 3, 5, 3, 7, 3, 12, 3,
            0, 4, 5, 4, 7, 4, 12, 4,
            2, 5, 3, 5, 4, 5, 8, 5, 9, 5, 10, 5,
            2, 7, 3, 7, 4, 7, 8, 7, 9, 7, 10, 7,
            0, 8, 5, 8, 7, 8, 12, 8,
            0, 9, 5, 9, 7, 9, 12, 9,
            0, 10, 5, 10, 7, 10, 12, 10,
            2, 12, 3, 12, 4, 12, 8, 12, 9, 12, 10, 12
    });

    // 高斯帕滑翔机枪（Gosper Glider Gun，1970 Bill Gosper 发现）
    // 人类发现的第一个无限增长图案：每 30 代发射一架滑翔机
    public static final Pattern GLIDER_GUN = new Pattern("滑翔机枪", "每 30 代发射一架滑翔机，无限增长", new int[]{
            24, 0,
            22, 1, 24, 1,
            12, 2, 13, 2, 20, 2, 21, 2, 34, 2, 35, 2,
            11, 3, 15, 3, 20, 3, 21, 3, 34, 3, 35, 3,
            0, 4, 1, 4, 10, 4, 16, 4, 20, 4, 21, 4,
            0, 5, 1, 5, 10, 5, 14, 5, 16, 5, 17, 5, 22, 5, 24, 5,
            10, 6, 16, 6, 24, 6,
            11, 7, 15, 7,
            12, 8, 13, 8
    });

    // R-pentomino（1970 Conway 手工推演）：仅 5 格起步，混沌演化 1103 代才稳定
    public static final Pattern R_PENTOMINO = new Pattern("R-五格骨牌", "5 格起步，混沌演化 1103 代", new int[]{
            1, 0, 2, 0,
            0, 1, 1, 1,
            1, 2
    });

    // 橡果（Acorn，1971 Charles Corderman 发现）：7 格起步，5206 代后稳定，扩散极广
    public static final Pattern ACORN = new Pattern("橡果", "7 格起步，5206 代后稳定", new int[]{
            1, 0,
            3, 1,
            0, 2, 1, 2, 4, 2, 5, 2, 6, 2
    });

    // 对角线飞船（Diehard）：7 格起步，130 代后彻底消亡
    public static final Pattern DIEHARD = new Pattern("顽固份子", "7 格起步，130 代后彻底消亡", new int[]{
            6, 0,
            0, 1, 1, 1,
            1, 2, 5, 2, 6, 2, 7, 2
    });

    // 蟾蜍（Toad）：周期 2 振荡器
    public static final Pattern TOAD = new Pattern("蟾蜍", "周期 2 振荡器", new int[]{
            1, 0, 2, 0, 3, 0,
            0, 1, 1, 1, 2, 1
    });

    // 方块（Block）：最简静物，永久不变
    public static final Pattern BLOCK = new Pattern("方块", "静物，永久不变", new int[]{
            0, 0, 1, 0,
            0, 1, 1, 1
    });

    public static final Pattern[] ALL = {
            GLIDER, LWSS, PULSAR, GLIDER_GUN, R_PENTOMINO, ACORN, DIEHARD, TOAD, BLOCK
    };

    // 图案定义：名称 + 说明 + 相对坐标
    public static class Pattern {

        public final String name;
        public final String description;
        public final int[] offsets;

        Pattern(String name, String description, int[] offsets) {
            this.name = name;
            this.description = description;
            this.offsets = offsets;
        }

        public int getCellCount() {
            return offsets.length / 2;
        }

        // 图案宽度（列跨度）
        public int getWidth() {
            int max = 0;
            for (int i = 0; i < offsets.length; i += 2) {
                if (offsets[i] > max) max = offsets[i];
            }
            return max + 1;
        }

        // 图案高度（行跨度）
        public int getHeight() {
            int max = 0;
            for (int i = 1; i < offsets.length; i += 2) {
                if (offsets[i] > max) max = offsets[i];
            }
            return max + 1;
        }

        @Override
        public String toString() {
            return name;
        }
    }

}