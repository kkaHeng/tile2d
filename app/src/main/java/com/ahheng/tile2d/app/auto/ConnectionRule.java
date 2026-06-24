package com.ahheng.tile2d.app.auto;

import java.util.Set;

/**
 * 瓦片连接规则接口。
 * 根据当前坐标及其邻居的放置状态，决定该位置应显示哪种视觉瓦片类型。
 */
public interface ConnectionRule {
    int resolve(int column, int row, Set<Long> placedTiles);
}