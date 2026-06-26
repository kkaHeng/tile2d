package com.ahheng.tile2d.app.maze;

import android.os.Bundle;
import android.util.LongSparseArray;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.ahheng.tile2d.TileCoreService;
import com.ahheng.tile2d.app.BaseActivity;
import com.ahheng.tile2d.app.auto.TileSet;
import com.ahheng.tile2d.widget.layout.TileLayout;

import java.util.ArrayDeque;
import java.util.Deque;

public class MaxMazeActivity extends BaseActivity {

    private static final String ASSET_PATH = "dirt_tileset.png";
    private static final int TYPE_EMPTY = 0;
    private static final int MAX_CHUNKS = 9;

    private int tileSize;
    private TileLayout tileLayout;
    private MazeAdapter adapter;
    private TileSet tileSet;

    private final LongSparseArray<Chunk> chunks = new LongSparseArray<>(MAX_CHUNKS);
    private final Deque<Chunk> recycledChunks = new ArrayDeque<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        tileSize = dp2px(45);
        tileSet = new TileSet(this, ASSET_PATH, tileSize);

        tileLayout = new TileLayout(this);
        tileLayout.setDebugMode(isDebugMode());
        tileLayout.setDefaultTileWidth(tileSize);
        tileLayout.setDefaultTileHeight(tileSize);
        setContentView(tileLayout, new ViewGroup.LayoutParams(-1, -1));

        tileLayout.setAdapter((adapter = new MazeAdapter()));
        tileLayout.seek(0, 0);
    }

    @Override
    public boolean hasMaxMode() {
        return false;
    }

    @Override
    public boolean hasPlanMode() {
        return false;
    }

    @Override
    protected void onDebugModeChanged(boolean enabled) {
        super.onDebugModeChanged(enabled);
        tileLayout.setDebugMode(enabled);
    }

    /**
     * 获取指定全局坐标对应的区块；惰性生成，同步填充，池满时淘汰最远区块。
     */
    private Chunk getChunk(int column, int row) {
        int cx = column >> Chunk.CHUNK_SHIFT;
        int cy = row >> Chunk.CHUNK_SHIFT;
        long chunkId = TileCoreService.getTileId(cx, cy);

        Chunk chunk = chunks.get(chunkId);
        if (chunk != null) {
            return chunk;
        }

        chunk = obtainChunk();
        chunk.setChunkPosition(cx, cy);
        fillWalls(chunk);

        chunks.put(chunkId, chunk);

        // 池子超限，淘汰离当前区块最远的
        if (chunks.size() > MAX_CHUNKS) {
            trimFarthestChunk(cx, cy);
        }

        return chunk;
    }

    private Chunk obtainChunk() {
        Chunk c = recycledChunks.poll();
        if (c == null) {
            c = new Chunk();
        } else {
            c.clear();
        }
        return c;
    }

    private void fillWalls(Chunk chunk) {
        for (int r = 0; r < Chunk.CHUNK_SIZE; r++) {
            for (int c = 0; c < Chunk.CHUNK_SIZE; c++) {
                chunk.setWall(c, r, true);
            }
        }
    }

    private void trimFarthestChunk(int centerCx, int centerCy) {
        int farthestIndex = -1;
        int maxDist = -1;

        for (int i = 0; i < chunks.size(); i++) {
            Chunk c = chunks.valueAt(i);
            int dist = Math.abs(c.getChunkX() - centerCx) + Math.abs(c.getChunkY() - centerCy);
            if (dist > maxDist) {
                maxDist = dist;
                farthestIndex = i;
            }
        }

        if (farthestIndex >= 0) {
            Chunk removed = chunks.valueAt(farthestIndex);
            chunks.removeAt(farthestIndex);
            recycledChunks.offer(removed);
        }
    }

    private class WallTileHolder extends TileLayout.TileHolder {
        public WallTileHolder(ImageView view) {
            super(view);
        }
    }

    public class MazeAdapter extends TileLayout.Adapter {

        @Override
        public TileLayout.TileHolder onCreateTileHolder(int type) {
            if (type == TYPE_EMPTY) {
                return null;
            }
            ImageView imageView = new ImageView(MaxMazeActivity.this);
            imageView.setImageBitmap(tileSet.getTile(type));
            imageView.setLayoutParams(new ViewGroup.LayoutParams(-2, -2));
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            imageView.setColorFilter(null);
            return new WallTileHolder(imageView);
        }

        @Override
        public int getTileType(int column, int row) {
            Chunk chunk = getChunk(column, row);
            if (chunk.isWall(column & Chunk.CHUNK_MASK, row & Chunk.CHUNK_MASK)) {
                return 35; // dirt_tileset 中的全孤立墙
            }
            return TYPE_EMPTY;
        }

        @Override
        public void onBindTileHolder(TileLayout.TileHolder holder, int column, int row) {
            // 静态墙壁，无需重新绑定
        }
    }

    @Override
    protected ToTheEnd onInitToTheEnd() {
        return new ToTheEnd() {
            @Override
            public int getLeftBound() {
                return adapter.getLeftBound();
            }
            @Override
            public int getTopBound() {
                return adapter.getTopBound();
            }
            @Override
            public int getRightBound() {
                return adapter.getRightBound();
            }
            @Override
            public int getBottomBound() {
                return adapter.getBottomBound();
            }
            @Override
            public void gogogo(int column, int row) {
                tileLayout.seek(column, row);
            }
        };
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        tileLayout.setAdapter(null);
        chunks.clear();
        recycledChunks.clear();
    }
}
