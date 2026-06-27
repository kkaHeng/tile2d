package com.ahheng.tile2d.app.noise;

import java.util.Random;

/**
 * 标准2D柏林噪声（Perlin Noise）实现
 * 基于Ken Perlin的经典算法，支持通过种子生成可复现的噪声
 */
public class PerlinNoise2D {
    
    // 排列数组长度
    private static final int PERM_SIZE = 256;
    private static final int PERM_MASK = 255;
    
    // 排列数组（512长度，避免取模运算）
    private final int[] perm = new int[PERM_SIZE * 2];
    
    // 2D梯度向量（8个标准方向）
    private static final int[][] GRAD2 = {
        {1, 1}, {-1, 1}, {1, -1}, {-1, -1},
        {1, 0}, {-1, 0}, {0, 1}, {0, -1}
    };
    
    /**
     * 使用指定种子构造柏林噪声生成器
     * @param seed 随机种子，相同种子产生完全相同的噪声
     */
    public PerlinNoise2D(long seed) {
        // 初始化0-255的排列
        for (int i = 0; i < PERM_SIZE; i++) {
            perm[i] = i;
        }
        
        // 使用种子进行Fisher-Yates洗牌
        Random random = new Random(seed);
        for (int i = PERM_SIZE - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int temp = perm[i];
            perm[i] = perm[j];
            perm[j] = temp;
        }
        
        // 复制一份到后半段，避免边界检查
        System.arraycopy(perm, 0, perm, PERM_SIZE, PERM_SIZE);
    }
    
    /**
     * 标准Fade函数：6t^5 - 15t^4 + 10t^3
     * 使插值更平滑，消除一阶和二阶导数不连续
     */
    private static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }
    
    /**
     * 线性插值
     */
    private static double lerp(double a, double b, double t) {
        return a + t * (b - a);
    }
    
    /**
     * 计算梯度向量与距离向量的点积
     */
    private static double dotProduct(int[] grad, double x, double y) {
        return grad[0] * x + grad[1] * y;
    }
    
    /**
     * 获取排列数组中的值（带索引取模）
     */
    private int perm(int i) {
        return perm[i & PERM_MASK];
    }
    
    /**
     * 生成2D柏林噪声值
     * @param x X坐标
     * @param y Y坐标
     * @return 噪声值，范围约为 [-1, 1]
     */
    public double noise(double x, double y) {
        // 确定输入点所在的单元格
        int xi = (int) Math.floor(x) & PERM_MASK;
        int yi = (int) Math.floor(y) & PERM_MASK;
        
        // 计算单元格内的相对坐标
        double xf = x - Math.floor(x);
        double yf = y - Math.floor(y);
        
        // 应用Fade函数得到平滑插值权重
        double u = fade(xf);
        double v = fade(yf);
        
        // 获取单元格四个角的排列索引
        int aa = perm(perm(xi) + yi);
        int ab = perm(perm(xi) + yi + 1);
        int ba = perm(perm(xi + 1) + yi);
        int bb = perm(perm(xi + 1) + yi + 1);
        
        // 计算四个角的影响值（梯度向量与距离向量的点积）
        double x1 = xf;     double y1 = yf;
        double x2 = xf - 1; double y2 = yf;
        double x3 = xf;     double y3 = yf - 1;
        double x4 = xf - 1; double y4 = yf - 1;
        
        double g1 = dotProduct(GRAD2[aa & 0x7], x1, y1);
        double g2 = dotProduct(GRAD2[ba & 0x7], x2, y2);
        double g3 = dotProduct(GRAD2[ab & 0x7], x3, y3);
        double g4 = dotProduct(GRAD2[bb & 0x7], x4, y4);
        
        // 双线性插值
        double xInterp1 = lerp(g1, g2, u);
        double xInterp2 = lerp(g3, g4, u);
        
        return lerp(xInterp1, xInterp2, v);
    }
    
    /**
     * 生成归一化到 [0, 1] 范围的噪声
     */
    public double noiseNormalized(double x, double y) {
        double n = noise(x, y);
        if (n < -1.0) n = -1.0;
        else if (n > 1.0) n = 1.0;
        return (n + 1.0) * 0.5;
    }

}
