package com.ahheng.tile2d.app.auto;

import com.ahheng.tile2d.TileCoreService;

import java.util.Set;

/**
 * 基于 4 方向邻居的自动连接规则（修正版）。
 *
 * 语义：浅色硬边 = 该方向有邻居（mask 位=1），深色边缘 = 该方向无邻居（mask 位=0）。
 *
 * 主连接系统（16 瓦片完整集）位于原始位置 (0,0) 到 (3,3)，
 * 按行优先跳过空单元格后分配的类型 ID 如下：
 *
 *   (0,0) type 1   mask 0110 (6)   左上 corner：右+下有邻居
 *   (0,1) type 2   mask 1110 (14)  上边缘：右+下+左有邻居
 *   (0,2) type 3   mask 1100 (12)  右上 corner：下+左有邻居
 *   (0,3) type 4   mask 0100 (4)   只有下有邻居
 *   (1,0) type 11  mask 0111 (7)   左边缘：上+右+下有邻居
 *   (1,1) type 12  mask 1111 (15)  中心：全有邻居
 *   (1,2) type 13  mask 1101 (13)  右边缘：上+下+左有邻居
 *   (1,3) type 14  mask 0101 (5)   垂直中间：上+下有邻居
 *   (2,0) type 21  mask 0011 (3)   左下 corner：上+右有邻居
 *   (2,1) type 22  mask 1011 (11)  下边缘：上+右+左有邻居
 *   (2,2) type 23  mask 1001 (9)   右下 corner：上+左有邻居
 *   (2,3) type 24  mask 0001 (1)   只有上有邻居
 *   (3,0) type 32  mask 0010 (2)   只有右有邻居
 *   (3,1) type 33  mask 1010 (10)  水平中间：右+左有邻居
 *   (3,2) type 34  mask 1000 (8)   只有左有邻居
 *   (3,3) type 35  mask 0000 (0)   全孤立
 */
public class SimpleConnectionRule implements ConnectionRule {

    // 16 种 mask 状态对应的主连接系统类型 ID
    private static final int[] MASK_TO_TYPE = {
        35, // 0000 全孤立
        24, // 0001 只有上
        32, // 0010 只有右
        21, // 0011 上+右
         4, // 0100 只有下
        14, // 0101 上+下
         1, // 0110 右+下
        11, // 0111 上+右+下
        34, // 1000 只有左
        23, // 1001 上+左
        33, // 1010 右+左
        22, // 1011 上+右+左
         3, // 1100 下+左
        13, // 1101 上+下+左
         2, // 1110 右+下+左
        12, // 1111 全邻居
    };

    @Override
    public int resolve(int column, int row, Set<Long> placedTiles) {
        boolean up    = placedTiles.contains(TileCoreService.getTileId(column, row - 1));
        boolean right = placedTiles.contains(TileCoreService.getTileId(column + 1, row));
        boolean down  = placedTiles.contains(TileCoreService.getTileId(column, row + 1));
        boolean left  = placedTiles.contains(TileCoreService.getTileId(column - 1, row));

        int mask = 0;
        if (up)    mask |= 1;
        if (right) mask |= 2;
        if (down)  mask |= 4;
        if (left)  mask |= 8;

        return MASK_TO_TYPE[mask];
    }
}
