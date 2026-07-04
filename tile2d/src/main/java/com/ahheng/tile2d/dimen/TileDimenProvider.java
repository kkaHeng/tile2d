package com.ahheng.tile2d.dimen;

public interface TileDimenProvider {

    int getTileWidth(int column);

    int getTileHeight(int row);

    void setTileWidth(int column, int width);

    void setTileHeight(int row, int height);

    void deleteTileWidth(int column);

    void deleteTileHeight(int row);

}
