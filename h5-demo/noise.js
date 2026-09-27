/*
 * 噪声纹理工具：java.util.Random 复刻 + PerlinNoise2D + ColorGenerator
 * 与 app 模块柏林噪声 demo 同款：固定种子 123456789、噪声缩放 0.03、24 色渐变映射
 * 这里逐位复刻了 java.util.Random（48 位 LCG），保证与 Java 版的排列洗牌逐位一致，
 * 因此同种子下每个瓦片的噪声值、颜色、文本都与 app 端完全相同。
 */

// java.util.Random：48 位线性同余发生器（用 BigInt 保证不丢精度）
class JavaRandom {
    constructor(seed) {
        // Java: seed = (seed ^ 0x5DEECE66D) & ((1 << 48) - 1)
        this.seed = (BigInt(seed) ^ 0x5DEECE66Dn) & 0xFFFFFFFFFFFFn;
    }

    // Java: next(bits)
    next(bits) {
        this.seed = (this.seed * 0x5DEECE66Dn + 0xBn) & 0xFFFFFFFFFFFFn;
        return Number(this.seed >> BigInt(48 - bits));
    }

    // Java: nextInt(bound) —— 含 2 的幂快速路径与拒绝采样
    nextInt(bound) {
        if ((bound & -bound) === bound) {
            return Number((BigInt(bound) * BigInt(this.next(31))) >> 31n);
        }
        let bits, val;
        do {
            bits = this.next(31);
            val = bits % bound;
        } while (bits - val + (bound - 1) < 0);
        return val;
    }
}

// 标准 2D 柏林噪声（与 Java 版 PerlinNoise2D 同款算法）
class PerlinNoise2D {

    constructor(seed) {
        const PERM_SIZE = 256;
        const perm = new Array(PERM_SIZE * 2);
        for (let i = 0; i < PERM_SIZE; i++) perm[i] = i;

        // 使用种子进行 Fisher-Yates 洗牌（与 Java 版逐次调用 nextInt 的顺序一致）
        const random = new JavaRandom(seed);
        for (let i = PERM_SIZE - 1; i > 0; i--) {
            const j = random.nextInt(i + 1);
            const temp = perm[i];
            perm[i] = perm[j];
            perm[j] = temp;
        }

        // 复制一份到后半段，避免边界检查
        for (let i = 0; i < PERM_SIZE; i++) perm[PERM_SIZE + i] = perm[i];
        this.perm = perm;
    }

    // 标准 Fade 函数：6t^5 - 15t^4 + 10t^3
    static fade(t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    static lerp(a, b, t) {
        return a + t * (b - a);
    }

    static perm(p, i) {
        return p[i & 255];
    }

    // 生成 2D 柏林噪声值，范围约 [-1, 1]
    noise(x, y) {
        const p = this.perm;
        const xi = Math.floor(x) & 255;
        const yi = Math.floor(y) & 255;
        const xf = x - Math.floor(x);
        const yf = y - Math.floor(y);
        const u = PerlinNoise2D.fade(xf);
        const v = PerlinNoise2D.fade(yf);

        const aa = PerlinNoise2D.perm(p, PerlinNoise2D.perm(p, xi) + yi);
        const ab = PerlinNoise2D.perm(p, PerlinNoise2D.perm(p, xi) + yi + 1);
        const ba = PerlinNoise2D.perm(p, PerlinNoise2D.perm(p, xi + 1) + yi);
        const bb = PerlinNoise2D.perm(p, PerlinNoise2D.perm(p, xi + 1) + yi + 1);

        const GRAD2 = PerlinNoise2D.GRAD2;
        const dot = (g, dx, dy) => g[0] * dx + g[1] * dy;
        const g1 = dot(GRAD2[aa & 0x7], xf, yf);
        const g2 = dot(GRAD2[ba & 0x7], xf - 1, yf);
        const g3 = dot(GRAD2[ab & 0x7], xf, yf - 1);
        const g4 = dot(GRAD2[bb & 0x7], xf - 1, yf - 1);

        const xInterp1 = PerlinNoise2D.lerp(g1, g2, u);
        const xInterp2 = PerlinNoise2D.lerp(g3, g4, u);
        return PerlinNoise2D.lerp(xInterp1, xInterp2, v);
    }

    // 生成归一化到 [0, 1] 范围的噪声
    noiseNormalized(x, y) {
        let n = this.noise(x, y);
        if (n < -1.0) n = -1.0;
        else if (n > 1.0) n = 1.0;
        return (n + 1.0) * 0.5;
    }

}

// 2D 梯度向量（8 个标准方向）
PerlinNoise2D.GRAD2 = [
    [1, 1], [-1, 1], [1, -1], [-1, -1],
    [1, 0], [-1, 0], [0, 1], [0, -1]
];

// 颜色生成：24 色渐变，与 Java 版 ColorGenerator 同款（同一张色表、同一套取整规则）
class ColorGenerator {

    getColor(noise) {
        const COLORS = ColorGenerator.COLORS;
        if (Number.isNaN(noise) || noise <= 0.0) return COLORS[0];
        if (noise >= 1.0) return COLORS[COLORS.length - 1];

        const pos = noise * (COLORS.length - 1);
        const idx = Math.trunc(pos);
        const frac = pos - idx;
        if (idx >= COLORS.length - 1) return COLORS[COLORS.length - 1];

        const c1 = COLORS[idx];
        const c2 = COLORS[idx + 1];
        const r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        const r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        const r = Math.trunc(r1 + (r2 - r1) * frac);
        const g = Math.trunc(g1 + (g2 - g1) * frac);
        const b = Math.trunc(b1 + (b2 - b1) * frac);
        return (0xFF000000 | (r << 16) | (g << 8) | b) >>> 0;
    }

    // 把 0xAARRGGBB 拆成 css 颜色
    static css(argb) {
        return 'rgb(' + ((argb >> 16) & 0xFF) + ',' + ((argb >> 8) & 0xFF) + ',' + (argb & 0xFF) + ')';
    }

    // 相对亮度（与 Java demo 同款公式，用于决定文字用黑还是白）
    static luminance(argb) {
        let r = ((argb >> 16) & 0xFF) / 255.0;
        let g = ((argb >> 8) & 0xFF) / 255.0;
        let b = (argb & 0xFF) / 255.0;
        r = r <= 0.03928 ? r / 12.92 : Math.pow((r + 0.055) / 1.055, 2.4);
        g = g <= 0.03928 ? g / 12.92 : Math.pow((g + 0.055) / 1.055, 2.4);
        b = b <= 0.03928 ? b / 12.92 : Math.pow((b + 0.055) / 1.055, 2.4);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }
}

// 24 种颜色，从亮蓝 → 青 → 绿 → 黄 → 橙 → 红 → 暖白 → 纯白
// 整体亮度高，低噪区明亮，高噪区趋近白色
ColorGenerator.COLORS = [
    0xFF3B82F6, 0xFF60A5FA, 0xFF93C5FD, 0xFF7DD3FC, 0xFF38BDF8, 0xFF22D3EE,
    0xFF67E8F9, 0xFF6EE7B7, 0xFF34D399, 0xFFA3E635, 0xFFFDE047, 0xFFFACC15,
    0xFFFBBF24, 0xFFFB923C, 0xFFF97316, 0xFFEF4444, 0xFFF87171, 0xFFFDBA74,
    0xFFFED7AA, 0xFFFEF3C7, 0xFFFFFBEB, 0xFFFFF7ED, 0xFFFFFAF0, 0xFFFFFFFF
];

if (typeof module !== 'undefined' && module.exports) {
    module.exports = { JavaRandom, PerlinNoise2D, ColorGenerator };
}