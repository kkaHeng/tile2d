package com.ahheng.tile2d.tile;

public abstract class TileAdapter <T> {

    public int getLeftBound() {
        return Integer.MIN_VALUE;
    }

    public int getTopBound() {
        return Integer.MIN_VALUE;
    }

    public int getRightBound() {
        return Integer.MAX_VALUE;
    }

    public int getBottomBound() {
        return Integer.MAX_VALUE;
    }

    public abstract T onCreateTileHolder(int type);

    public abstract void onBindTileHolder(T holder, int column, int row);

    public int getTileType(int column, int row) {
        return 0;
    }

    public boolean isEmpty() {
        return getLeftBound() > getRightBound() || getTopBound() > getBottomBound();
    }

}
