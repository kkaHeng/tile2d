package com.ahheng.tile2d.app.maze;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.LongSparseArray;
import android.util.TypedValue;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.ahheng.tile2d.TileCoreService;
import com.ahheng.tile2d.app.BaseActivity;
import com.ahheng.tile2d.widget.layout.TileLayout;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MaxMazeActivity extends BaseActivity {

    private static final int TYPE_EMPTY = 0;  // 空白，不创建View
    private static final int TYPE_LOADING = -1; // 正在加载，返回null
    private static final int TYPE_WALL = 1; // 墙壁类型ID

    private TileLayout tileLayout;
    private TextView chunkTextView;
    private MaxMazeAdapter adapter;
    private int tileSize;

    private final long seed = 123456789L;

    private ExecutorService executorService;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final Deque<Chunk> chunkPool = new ArrayDeque<>();
    private final LongSparseArray<Chunk> activeChunks = new LongSparseArray<>();
    private Choreographer choreographer;

    private int centerChunkCol = 0;
    private int centerChunkRow = 0;
    private final int expandRange = 1;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        tileSize = dp2px(45);

        FrameLayout root = new FrameLayout(this);
        chunkTextView = new TextView(this);
        chunkTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        TypedValue v = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.textColorPrimary, v, true);
        chunkTextView.setTextColor(ContextCompat.getColor(this, v.resourceId));
        chunkTextView.setGravity(Gravity.END | Gravity.BOTTOM);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-2, -2, Gravity.END | Gravity.BOTTOM);
        params.setMarginEnd(dp2px(8));
        params.bottomMargin = dp2px(4);
        
        tileLayout = new TileLayout(this);
        tileLayout.setDebugMode(isDebugMode());
        tileLayout.setDefaultTileWidth(tileSize);
        tileLayout.setDefaultTileHeight(tileSize);
        root.addView(tileLayout, -1, -1);
        root.addView(chunkTextView, params);
        setContentView(root, new ViewGroup.LayoutParams(-1, -1));

        tileLayout.setAdapter((adapter = new MaxMazeAdapter()));

        executorService = Executors.newFixedThreadPool(4);
        choreographer = Choreographer.getInstance();

        tileLayout.post(() -> {
            if (isFinishing()) return;
            tileLayout.seek(0, 0);
            choreographer.postFrameCallback(frameCallback);
        });
    }

    private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            if (isFinishing()) return;
            // 获取视口中心点对应的瓦片坐标
            int centerCol = tileLayout.findColumn(tileLayout.getWidth() / 2f);
            int centerRow = tileLayout.findRow(tileLayout.getHeight() / 2f);
            int newCenterChunkCol = centerCol >> Chunk.CHUNK_SHIFT;
            int newCenterChunkRow = centerRow >> Chunk.CHUNK_SHIFT;

            // 如果中心区块发生变化，则更新区块加载状态
            if (newCenterChunkCol != centerChunkCol || newCenterChunkRow != centerChunkRow) {
                centerChunkCol = newCenterChunkCol;
                centerChunkRow = newCenterChunkRow;
                updateChunks();
            }
            choreographer.postFrameCallback(this);
        }
    };

    private boolean isInCenterRange(int chunkCol, int chunkRow) {
        return Math.abs(chunkCol - centerChunkCol) <= expandRange && Math.abs(chunkRow - centerChunkRow) <= expandRange;
    }

    @SuppressLint("SetTextI18n")
    private void updateChunks() {
        // 计算实际可用的区块范围（自适应裁剪）
        int actualColStart = Math.max(centerChunkCol - expandRange, Chunk.MIN_CHUNK);
        int actualColEnd   = Math.min(centerChunkCol + expandRange, Chunk.MAX_CHUNK);
        int actualRowStart = Math.max(centerChunkRow - expandRange, Chunk.MIN_CHUNK);
        int actualRowEnd   = Math.min(centerChunkRow + expandRange, Chunk.MAX_CHUNK);
    
        // 移除离开新范围的区块（反向迭代，安全删除）
        boolean removed = false;
        for (int i = activeChunks.size() - 1; i >= 0; i--) {
            long id = activeChunks.keyAt(i);
            int bCol = TileCoreService.getColumn(id);
            int bRow = TileCoreService.getRow(id);
            // 注意：这里判断是否落在新范围内
            if (bCol < actualColStart || bCol > actualColEnd || bRow < actualRowStart || bRow > actualRowEnd) {
                Chunk chunk = activeChunks.get(id);
                if (chunk == null) continue;
                if (chunk.future != null && !chunk.future.isDone()) {
                    chunk.future.cancel(true);
                }
                activeChunks.removeAt(i);
                recycleChunk(chunk);
                removed = true;
            }
        }
    
        // 添加新区块（只遍历裁剪后的实际可用范围）
        for (int bCol = actualColStart; bCol <= actualColEnd; bCol++) {
            for (int bRow = actualRowStart; bRow <= actualRowEnd; bRow++) {
                long id = TileCoreService.getTileId(bCol, bRow);
                if (activeChunks.indexOfKey(id) < 0) {
                    Chunk chunk = obtainChunk(bCol, bRow);
                    activeChunks.put(id, chunk);
                    generateChunk(chunk);
                }
            }
        }
    
        // 刷新 UI
        if (removed) {
            tileLayout.updateAll();
        }
        Chunk center = activeChunks.get(TileCoreService.getTileId(centerChunkCol, centerChunkRow));
        chunkTextView.setText(
                "可见 " + activeChunks.size() + " 个，回收 " + chunkPool.size() + " 个\n" +
                "当前区块：" + centerChunkCol + "," + centerChunkRow + "\n" +
                "区块种子：" + (center == null ? "?" : center.seed)
        );

    }

    private void generateChunk(Chunk chunk) {
        long chunkId = TileCoreService.getTileId(chunk.col, chunk.row);
        int finalCol = chunk.col;
        int finalRow = chunk.row;
        chunk.future = executorService.submit(() -> {
            try {
                // 测试环境用于模拟极慢情况下的耗时
                // Thread.sleep(800);
                
                // 生成边界墙
                for(int i = 0; i < Chunk.CHUNK_SIZE; i++) {
                    chunk.setWall(i, 0, true);
                    chunk.setWall(0, i, true);
                }
                // 生成边界开口(≥1)
                int wallX = chunk.randomOddLocal();
                chunk.setWall(wallX, 0, false);
                int wallY = chunk.randomOddLocal();
                chunk.setWall(0, wallY, false);
                if (chunk.random.nextDouble() <= 0.5) {
                    // 50% 概率额外生成2个边界开口
                    int i = chunk.randomOddLocal();
                    if (Math.abs(i - wallX) > 3) {
                        chunk.setWall(i, 0, false);
                    }
                    i = chunk.randomOddLocal();
                    if (Math.abs(i - wallY) > 3) {
                        chunk.setWall(0, i, false);
                    }
                }
                
                // 递归分割法
                recursiveDivision(1, 1, Chunk.CHUNK_SIZE - 1, Chunk.CHUNK_SIZE - 1, chunk);
                
                // 生成完毕，回到主线程更新状态和UI
                mainHandler.post(() -> {
                    Chunk current = activeChunks.get(chunkId);
                    if (current != null && current.col == finalCol && current.row == finalRow) {
                        current.generated = true;
                        int left = finalCol << Chunk.CHUNK_SHIFT;
                        int top = finalRow << Chunk.CHUNK_SHIFT;
                        tileLayout.updateRange(left, top,
                                left + Chunk.CHUNK_SIZE - 1, top + Chunk.CHUNK_SIZE - 1);
                    }
                });
            } catch (Exception e) {
                // 任务出错或被取消，静默退出
            }
        });
    }

    private void recursiveDivision(int l, int t, int r, int b, Chunk c) {
        if (Thread.currentThread().isInterrupted()) return; // 最快退出
        int w = r - l + 1;
        int h = b - t + 1;
    	if (w < 3 || h < 3) return;
        // 砌墙
        int x;
        int y;
        if (w >= h) {
            int m = w > 5 ? 3 : 1;
            y = t;
            x = c.randomEven(l + m, r - m);
        } else {
            int m = h > 5 ? 3 : 1;
            x = l;
            y = c.randomEven(t + m, b - m);
        }
        for(int i = 0; i < Math.min(w, h); i++) {
            if (w >= h) {
                c.setWall(x, y + i, true);
            } else {
                c.setWall(x + i, y, true);
            }
        }
        
        // 开洞
        double extra = c.random.nextDouble(); // 50% 概率额外生成一个洞
        if (w >= h) {
            int i = c.randomOdd(t, b);
            c.setWall(x, i, false);
            if (extra <= 0.5) {
                int i2 = c.randomOdd(t, b);
                if (Math.abs(i2 - i) > 3) c.setWall(x, i2, false);
            }
        } else {
            int i = c.randomOdd(l, r);
            c.setWall(i, y, false);
            if (extra <= 0.5) {
                int i2 = c.randomOdd(l, r);
                if (Math.abs(i2 - i) > 3) c.setWall(i2, y, false);
            }

        }

        // 递归分割
        if (w >= h) {
            recursiveDivision(l, t, x - 1, b, c);
            recursiveDivision(x + 1, t, r, b, c);
        } else {
            recursiveDivision(l, t, r, y - 1, c);
            recursiveDivision(l, y + 1, r, b, c);
        }
    }

    private Chunk obtainChunk(int col, int row) {
        Chunk chunk = chunkPool.poll();
        if (chunk == null) {
            chunk = new Chunk();
        }
        chunk.col = col;
        chunk.row = row;
        chunk.initRandom(seed);
        return chunk;
    }
    
    private void recycleChunk(Chunk chunk) {
        if (chunk == null) return;
        if (chunk.future != null && !chunk.future.isDone()) {
            chunk.future.cancel(true);
        }
        chunk.clear(); // 完全重置 Chunk 的一切状态
        chunkPool.offer(chunk);
    }

    @Override
    protected void onDebugModeChanged(boolean enabled) {
        tileLayout.setDebugMode(enabled);
    }

    @Override
    public boolean hasMaxMode() {
        return false;
    }

    @Override
    public boolean hasPlanMode() {
        return false;
    }

    private class MaxMazeAdapter extends TileLayout.Adapter {

        @Override
        public int getTileType(int column, int row) {
            int chunkCol = column >> Chunk.CHUNK_SHIFT;
            int chunkRow = row >> Chunk.CHUNK_SHIFT;
            long chunkId = TileCoreService.getTileId(chunkCol, chunkRow);

            Chunk chunk = activeChunks.get(chunkId);
            if (chunk != null && chunk.generated) {
                return chunk.isWall(column & Chunk.CHUNK_MASK, row & Chunk.CHUNK_MASK) ? TYPE_WALL : TYPE_EMPTY;
            } else {
                // 在区块生成完毕前，如果该区块在3x3范围内，返回 TYPE_LOADING
                if (isInCenterRange(chunkCol, chunkRow)) {
                    return TYPE_LOADING;
                } else {
                    return TYPE_EMPTY;
                }
            }
        }

        @Override
        public TileLayout.TileHolder onCreateTileHolder(int type) {
            // TYPE_EMPTY 和 TYPE_LOADING 都返回 null 留空，直到区块生成完毕
            if (type == TYPE_EMPTY || type == TYPE_LOADING) {
                return null;
            }
            ImageView imageView = new ImageView(MaxMazeActivity.this);
            try (InputStream is = getAssets().open("bricks.png")) {
                Bitmap bitmap = BitmapFactory.decodeStream(is);
                imageView.setImageBitmap(Bitmap.createScaledBitmap(bitmap, tileSize, tileSize, false));
            } catch (IOException ignored) {}
            imageView.setLayoutParams(new ViewGroup.LayoutParams(-2, -2));
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            return new TileLayout.TileHolder(imageView);
        }

        @Override
        public void onBindTileHolder(TileLayout.TileHolder holder, int column, int row) {}
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
        choreographer.removeFrameCallback(frameCallback);
        executorService.shutdownNow(); // 立即中断线程池
        for (int i = activeChunks.size() - 1; i >= 0; i--) {
            Chunk chunk = activeChunks.valueAt(i);
            if (chunk != null) {
                recycleChunk(chunk);
            }
        }
        activeChunks.clear();
        chunkPool.clear();
        mainHandler.removeCallbacksAndMessages(null);
        tileLayout.setAdapter(null);
    }

}
