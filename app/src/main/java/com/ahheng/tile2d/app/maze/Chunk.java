package com.ahheng.tile2d.app.maze;

import java.util.Arrays;

public class Chunk {

    public static final int CHUNK_SIZE = 32;
    public static final int CHUNK_MASK = CHUNK_SIZE - 1;
    public static final int CHUNK_SHIFT = 5;

    private final boolean[][] tiles = new boolean[CHUNK_SIZE][CHUNK_SIZE];
    private int chunkX;
    private int chunkY;

    public boolean isWall(int x, int y) {
        return tiles[x][y];
    }

    public void setWall(int x, int y, boolean isWall) {
        tiles[x][y] = isWall;
    }

    public void setChunkPosition(int x, int y) {
        this.chunkX = x;
        this.chunkY = y;
    }

    public int getChunkX() {
        return chunkX;
    }

    public int getChunkY() {
        return chunkY;
    }

    public void clear() {
        for (int i = 0; i < CHUNK_SIZE; i++) {
            Arrays.fill(tiles[i], false);
        }
        chunkX = 0;
        chunkY = 0;
    }
}
