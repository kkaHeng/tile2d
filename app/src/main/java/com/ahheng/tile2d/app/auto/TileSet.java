package com.ahheng.tile2d.app.auto;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

/**
 * 瓦片集管理器。
 * 从 assets 加载原始瓦片集图片（176×80），按 16×16 网格切割，
 * 使用最近邻算法放大到目标显示尺寸，保证像素风锐利。
 */
public class TileSet {

    // 原始瓦片集规格
    private static final int SRC_TILE_SIZE = 16;
    private static final int SRC_COLS = 11;
    private static final int SRC_ROWS = 5;

    // 空单元格坐标（行,列）
    private static final Set<Long> EMPTY_CELLS = new HashSet<>();
    static {
        EMPTY_CELLS.add(pack(0, 10));
        EMPTY_CELLS.add(pack(1, 10));
        EMPTY_CELLS.add(pack(4, 0));
        EMPTY_CELLS.add(pack(4, 1));
        EMPTY_CELLS.add(pack(4, 2));
        EMPTY_CELLS.add(pack(4, 3));
        EMPTY_CELLS.add(pack(4, 9));
        EMPTY_CELLS.add(pack(4, 10));
    }

    private final Bitmap[] tiles;
    private final int displaySize;

    /**
     * @param context     上下文
     * @param assetPath   assets 路径，如 "dirt_tileset.png"
     * @param displaySize 目标显示尺寸（如 64），最近邻放大
     */
    public TileSet(Context context, String assetPath, int displaySize) {
        this.displaySize = displaySize;
        Bitmap src = loadFromAssets(context, assetPath);

        // 只创建有效瓦片（跳过空单元格），类型 ID 按行优先顺序分配，从 1 开始
        int validCount = SRC_COLS * SRC_ROWS - EMPTY_CELLS.size(); // 47
        this.tiles = new Bitmap[validCount + 1]; // 索引 0 不用，1~47 有效

        int typeId = 1;
        for (int r = 0; r < SRC_ROWS; r++) {
            for (int c = 0; c < SRC_COLS; c++) {
                if (EMPTY_CELLS.contains(pack(r, c))) {
                    continue;
                }
                Bitmap cell = Bitmap.createBitmap(src, c * SRC_TILE_SIZE, r * SRC_TILE_SIZE,
                        SRC_TILE_SIZE, SRC_TILE_SIZE);
                // false = 最近邻（Nearest Neighbor），保持像素风硬边缘
                tiles[typeId] = Bitmap.createScaledBitmap(cell, displaySize, displaySize, false);
                cell.recycle();
                typeId++;
            }
        }
        src.recycle();
    }

    private static Bitmap loadFromAssets(Context context, String path) {
        try (InputStream is = context.getAssets().open(path)) {
            return BitmapFactory.decodeStream(is);
        } catch (IOException e) {
            throw new RuntimeException("无法从 assets 加载瓦片集: " + path, e);
        }
    }

    private static long pack(int row, int col) {
        return ((long) row << 32) | (col & 0xFFFFFFFFL);
    }

    public Bitmap getTile(int type) {
        if (type < 1 || type >= tiles.length) return null;
        return tiles[type];
    }

    public int getTileCount() {
        return tiles.length - 1;
    }

    public int getDisplaySize() {
        return displaySize;
    }
}
