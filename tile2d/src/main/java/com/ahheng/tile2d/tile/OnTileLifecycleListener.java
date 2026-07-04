package com.ahheng.tile2d.tile;

import com.ahheng.tile2d.TileCoreService;

public interface OnTileLifecycleListener <T extends TileCoreService.BaseTileHolder> {
    void onTileIn(T holder, int column, int row);
    void onTileOut(T holder, int column, int row);
    void onTileRecycled(T holder, int column, int row);
}
