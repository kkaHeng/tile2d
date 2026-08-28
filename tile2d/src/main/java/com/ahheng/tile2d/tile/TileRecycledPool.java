package com.ahheng.tile2d.tile;

import com.ahheng.tile2d.TileCoreService;
import com.ahheng.tile2d.util.intmap.IntMap;
import com.ahheng.tile2d.util.intmap.IntMapOpenHashMap;

import java.util.ArrayDeque;
import java.util.Deque;

// 瓦片回收池
// 按类型分桶缓存被回收的瓦片,复用持有者以减少创建开销
public class TileRecycledPool <T extends TileCoreService.BaseTileHolder> {

    private IntMap<Deque<T>> recycledTiles; // 类型 → 回收桶

    public TileRecycledPool() {
        this.recycledTiles = new IntMapOpenHashMap<>();
    }

    // 清空全部回收桶
    public void reset() {
        recycledTiles.clear();
    }

    // 取出指定类型的一个瓦片,桶空返回 null
    public T get(int type) {
        Deque<T> deque = recycledTiles.get(type);
        if (deque == null || deque.isEmpty()) {
            return null;
        }
        return deque.poll();
    }

    // 替换底层存储(数据迁移到新容器)
    public void setRecycledTiles(IntMap<Deque<T>> map) {
        if (map == null) throw new IllegalArgumentException("回收池存储不能设置为空");
        if (map == recycledTiles) return;
        map.clear();
        IntMap<Deque<T>> old = recycledTiles;
        IntMap<Deque<T>> newMap = map;
        for (IntMap.Iterator<Deque<T>> it = old.iterator(); it.next(); ) {
            newMap.put(it.key(), it.value());
        }
        old.clear();
        recycledTiles = newMap;
    }

    // 回收瓦片到对应类型桶
    public void recycle(int type, T tile) {
        Deque<T> deque = recycledTiles.get(type);
        if (deque == null) {
            deque = new ArrayDeque<>();
            recycledTiles.put(type, deque);
        }
        deque.offer(tile);
    }

    // 迁移全部回收桶到目标池
    public void moveTo(TileRecycledPool<T> recycledTiles) {
        if (recycledTiles == null) throw new IllegalArgumentException("回收池存储不能设置为空");
        if (recycledTiles == this) return;
        recycledTiles.recycledTiles.clear();
    	for (IntMap.Iterator<Deque<T>> it = this.recycledTiles.iterator(); it.next(); ) {
            recycledTiles.recycledTiles.put(it.key(), it.value());
        }
        this.recycledTiles.clear();
    }

}