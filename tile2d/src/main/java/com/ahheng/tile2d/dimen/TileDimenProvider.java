package com.ahheng.tile2d.dimen;

// 瓦片尺寸提供器接口
// 按列/行查询与修改瓦片宽高,由 DimenManager 实现并维护
public interface TileDimenProvider {

    int getTileWidth(int column); // 查询指定列宽

    int getTileHeight(int row); // 查询指定行高

    void setTileWidth(int column, int width); // 设置指定列宽

    void setTileHeight(int row, int height); // 设置指定行高

    void deleteTileWidth(int column); // 删除指定列宽记录,回退默认值

    void deleteTileHeight(int row); // 删除指定行高记录,回退默认值

}