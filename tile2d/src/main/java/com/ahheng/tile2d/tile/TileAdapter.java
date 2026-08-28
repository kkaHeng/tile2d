package com.ahheng.tile2d.tile;

// 瓦片适配器
// 定义内容边界与瓦片创建/绑定,由使用方继承实现
public abstract class TileAdapter <T> {

    // 内容左边界(列),默认覆盖完整 int32 空间
    public int getLeftBound() {
        return Integer.MIN_VALUE;
    }

    // 内容上边界(行),默认覆盖完整 int32 空间
    public int getTopBound() {
        return Integer.MIN_VALUE;
    }

    // 内容右边界(列),默认覆盖完整 int32 空间
    public int getRightBound() {
        return Integer.MAX_VALUE;
    }

    // 内容下边界(行),默认覆盖完整 int32 空间
    public int getBottomBound() {
        return Integer.MAX_VALUE;
    }

    // 创建指定类型的瓦片持有者
    public abstract T onCreateTileHolder(int type);

    // 绑定数据到瓦片持有者
    public abstract void onBindTileHolder(T holder, int column, int row);

    // 查询坐标对应的瓦片类型,默认单类型 0
    public int getTileType(int column, int row) {
        return 0;
    }

    // 内容是否为空(边界倒置即无内容)
    public boolean isEmpty() {
        return getLeftBound() > getRightBound() || getTopBound() > getBottomBound();
    }

}