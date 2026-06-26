package com.ahheng.tile2d.app.maze;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.LongSparseArray;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.ahheng.tile2d.TileCoreService;
import com.ahheng.tile2d.TileLayoutModel;
import com.ahheng.tile2d.app.BaseActivity;
import com.ahheng.tile2d.app.auto.TileSet;
import com.ahheng.tile2d.widget.layout.TileLayout;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class MaxMazeActivity extends BaseActivity {

    private static final String ASSET_PATH = "dirt_tileset.png";
    private static final int TYPE_EMPTY = 0;
    private static final int MAX_CHUNKS = 9;

    private int tileSize;
    private TileLayout tileLayout;
    private MazeAdapter adapter;
    private TileSet tileSet;

    // 主线程独占
    private final LongSparseArray<Chunk> chunks = new LongSparseArray<>(MAX_CHUNKS);
    private final Deque<Chunk> recycledChunks = new ArrayDeque<>();
    private final LongSparseArray<Boolean> pendingChunks = new LongSparseArray<>();

    // 跨线程队列：子线程 offer，主线程 poll
    private final ConcurrentLinkedQueue<ChunkResult> resultQueue = new ConcurrentLinkedQueue<>();

    private ExecutorService executor;
    private Handler mainHandler;
    private final AtomicBoolean destroyed = new AtomicBoolean(false);

    // 视窗中心区块（用于淘汰策略）
    private int lastCenterCx, lastCenterCy;

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

        executor = Executors.newFixedThreadPool(4);
        mainHandler = new Handler(Looper.getMainLooper());

        tileLayout.setAdapter((adapter = new MazeAdapter()));
        tileLayout.seek(0, 0);
    }

    @Override
    public boolean hasMaxMode() {
        return true;
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

    // ========== 区块流式生成 ==========

    private void submitChunkGeneration(int cx, int cy) {
        long chunkId = TileCoreService.getTileId(cx, cy);
        if (chunks.get(chunkId) != null || pendingChunks.get(chunkId) != null) {
            return;
        }
        pendingChunks.put(chunkId, Boolean.TRUE);

        executor.execute(() -> {
            // 快速填充墙壁（未来替换为递归分割）
            boolean[][] data = new boolean[Chunk.CHUNK_SIZE][Chunk.CHUNK_SIZE];
            for (int r = 0; r < Chunk.CHUNK_SIZE; r++) {
                if (Thread.currentThread().isInterrupted()) return;
                Arrays.fill(data[r], true);
            }

            if (destroyed.get()) return;

            resultQueue.offer(new ChunkResult(chunkId, cx, cy, data));
            mainHandler.post(() -> {
                if (!destroyed.get()) consumeResults();
            });
        });
    }

    /** 主线程消费所有已完成的区块 */
    private void consumeResults() {
        ChunkResult result;
        while ((result = resultQueue.poll()) != null) {
            pendingChunks.remove(result.chunkId);

            // 更新视窗中心（用于判断是否需要保留）
            updateViewportCenter();

            // 视窗已远离，直接丢弃
            if (!isChunkNearViewport(result.cx, result.cy)) {
                continue;
            }

            // 池满则淘汰最远区块
            if (chunks.size() >= MAX_CHUNKS) {
                trimFarthestChunk(lastCenterCx, lastCenterCy);
            }

            Chunk chunk = obtainChunk();
            chunk.setChunkPosition(result.cx, result.cy);
            chunk.copyFrom(result.data);
            chunks.put(result.chunkId, chunk);

            // 局部刷新刚生成的区块
            int left = result.cx << Chunk.CHUNK_SHIFT;
            int top = result.cy << Chunk.CHUNK_SHIFT;
            int right = left + Chunk.CHUNK_SIZE - 1;
            int bottom = top + Chunk.CHUNK_SIZE - 1;
            tileLayout.updateRange(left, top, right, bottom);
        }
    }

    // ========== 视窗与淘汰 ==========

    private void updateViewportCenter() {
        TileLayoutModel model = tileLayout.getLayoutModel();
        if (model == null) return;
        lastCenterCx = ((model.colStart + model.colEnd) >> 1) >> Chunk.CHUNK_SHIFT;
        lastCenterCy = ((model.rowStart + model.rowEnd) >> 1) >> Chunk.CHUNK_SHIFT;
    }

    private boolean isChunkNearViewport(int cx, int cy) {
        int dx = Math.abs(cx - lastCenterCx);
        int dy = Math.abs(cy - lastCenterCy);
        return dx <= 2 && dy <= 2; // 5×5 区块范围
    }

    private void trimFarthestChunk(int centerCx, int centerCy) {
        int farthestIdx = -1;
        int maxDist = -1;
        for (int i = 0; i < chunks.size(); i++) {
            Chunk c = chunks.valueAt(i);
            int dist = Math.abs(c.getChunkX() - centerCx) + Math.abs(c.getChunkY() - centerCy);
            if (dist > maxDist) {
                maxDist = dist;
                farthestIdx = i;
            }
        }
        if (farthestIdx >= 0) {
            Chunk removed = chunks.valueAt(farthestIdx);
            chunks.removeAt(farthestIdx);
            recycledChunks.offer(removed);
        }
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

    // ========== Adapter ==========

    private long lastChunkId = Long.MIN_VALUE;
    private Chunk lastChunk;

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
            return new TileLayout.TileHolder(imageView);
        }

        @Override
        public int getTileType(int column, int row) {
            int cx = column >> Chunk.CHUNK_SHIFT;
            int cy = row >> Chunk.CHUNK_SHIFT;
            int lx = column & Chunk.CHUNK_MASK;
            int ly = row & Chunk.CHUNK_MASK;
            long chunkId = TileCoreService.getTileId(cx, cy);

            // 同区块缓存：1023/1024 命中率
            Chunk chunk;
            if (chunkId == lastChunkId) {
                chunk = lastChunk;
            } else {
                chunk = chunks.get(chunkId);
                lastChunkId = chunkId;
                lastChunk = chunk;
            }

            if (chunk != null) {
                return chunk.isWall(lx, ly) ? 35 : TYPE_EMPTY;
            }

            // 未生成：提交后台任务，当前留空
            submitChunkGeneration(cx, cy);
            return TYPE_EMPTY;
        }

        @Override
        public void onBindTileHolder(TileLayout.TileHolder holder, int column, int row) {
            // 静态墙壁，无需动态绑定
        }
    }

    // ========== 生命周期 ==========

    @Override
    protected void onDestroy() {
        destroyed.set(true);
        executor.shutdownNow();
        resultQueue.clear();
        tileLayout.setAdapter(null);
        chunks.clear();
        recycledChunks.clear();
        pendingChunks.clear();
        super.onDestroy();
    }

    // ========== 数据传输对象 ==========

    private static class ChunkResult {
        final long chunkId;
        final int cx, cy;
        final boolean[][] data;

        ChunkResult(long chunkId, int cx, int cy, boolean[][] data) {
            this.chunkId = chunkId;
            this.cx = cx;
            this.cy = cy;
            this.data = data;
        }
    }
}
