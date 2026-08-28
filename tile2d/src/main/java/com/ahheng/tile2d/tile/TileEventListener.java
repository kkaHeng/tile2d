package com.ahheng.tile2d.tile;

import com.ahheng.tile2d.TileCoreService;

// 瓦片事件监听器
// 观察布局周期与瓦片生命周期,由 TileView / TileLayout 统一回调
public interface TileEventListener<T extends TileCoreService.BaseTileHolder> {

    // 布局周期开始前调用,此时瓦片尚未更新
    void onBeforeLayout();

    // 布局周期结束后调用,瓦片已完成更新与渲染
    void onAfterLayout();

    // 瓦片进入视窗并完成布局
    void onTileIn(T holder, int column, int row);

    // 瓦片离开视窗,进入濒死区,短暂缓冲后可能被回收
    void onTileOut(T holder, int column, int row);

    // 瓦片被回收到池中,即将被复用或释放
    void onTileRecycled(T holder, int column, int row);

    // 瓦片被预取(已创建绑定,尚未进入视窗),适合在此提前完成进窗前的重活
    default void onTilePrefetched(T holder, int column, int row) {
    }

}