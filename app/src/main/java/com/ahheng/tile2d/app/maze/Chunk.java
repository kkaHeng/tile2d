package com.ahheng.tile2d.app.maze;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.Future;

public class Chunk {

    public static final int CHUNK_SIZE = 32;
    public static final int CHUNK_MASK = CHUNK_SIZE - 1;
    public static final int CHUNK_SHIFT = Integer.numberOfTrailingZeros(CHUNK_SIZE);
    public static final int MIN_CHUNK = Integer.MIN_VALUE >> CHUNK_SHIFT;
    public static final int MAX_CHUNK = Integer.MAX_VALUE >> CHUNK_SHIFT;

    private final boolean[][] tiles = new boolean[CHUNK_SIZE][CHUNK_SIZE];

    public int col;
    public int row;
    public Future<?> future;
    public boolean generated;
    public long seed;
    public Random random;

    public boolean isWall(int x, int y) {
        return tiles[x][y];
    }

    public void setWall(int x, int y, boolean isWall) {
        tiles[x][y] = isWall;
    }

    public void clear() {
        for (int i = 0; i < CHUNK_SIZE; i++) {
            Arrays.fill(tiles[i], false);
        }
        col = 0;
        row = 0;
        future = null;
        random = null;
        generated = false;
    }

    public void initRandom(long baseSeed) {
    	seed = combineSeed(baseSeed, col, row);
        random = new Random(seed);
    }

    // 生成偶数坐标
    public int randomEvenLocal() {
        return random.nextInt(CHUNK_SIZE / 2) * 2;
    }

    // 生成奇数坐标
    public int randomOddLocal() {
        return random.nextInt(CHUNK_SIZE / 2) * 2 + 1;
    }

    // 生成 [minInclusive, maxInclusive] 范围内的随机偶数
    public int randomEven(int minInclusive, int maxInclusive) {
        // 确保 min 为偶数
        int min = (minInclusive % 2 == 0) ? minInclusive : minInclusive + 1;
        int max = (maxInclusive % 2 == 0) ? maxInclusive : maxInclusive - 1;
        if (min > max) throw new IllegalArgumentException("无偶数可用");
        int count = (max - min) / 2 + 1; // 偶数个数
        return min + random.nextInt(count) * 2;
    }
    
    // 生成 [minInclusive, maxInclusive] 范围内的随机奇数
    public int randomOdd(int minInclusive, int maxInclusive) {
        // 确保 min 为奇数
        int min = (minInclusive % 2 != 0) ? minInclusive : minInclusive + 1;
        int max = (maxInclusive % 2 != 0) ? maxInclusive : maxInclusive - 1;
        if (min > max) throw new IllegalArgumentException("无奇数可用");
        int count = (max - min) / 2 + 1; // 奇数个数
        return min + random.nextInt(count) * 2;
    }

    // SplitMix64 的最终混合器（JDK SplittableRandom 同款算法）
    private static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    public static long combineSeed(long baseSeed, int a, int b) {
        long seed = baseSeed;
        // 逐个混合，顺序固定，且能避免对称性冲突
        seed = mix64(seed ^ (a & 0xffffffffL)); // int 转无符号 32 位
        seed = mix64(seed ^ (b & 0xffffffffL));
        return seed;
    }

}
