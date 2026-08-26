/*
 * 噪声纹理工具：PerlinNoise2D + ColorGenerator
 * 与 app 模块瓦片画板 demo 同款：固定种子柏林噪声 + 24 色渐变映射
 */

// 确定性随机数（SplitMix64，32位版）：保证同一种子洗牌结果可复现
function createRandom(seed) {
    let x = seed >>> 0;
    return function () {
        x = (x + 0x9E3779B9) >>> 0;
        let z = x;
        z = Math.imul(z ^ (z >>> 16), 0x21F0AAAD);
        z = Math.imul(z ^ (z >>> 15), 0x735A2D97);
        return (z ^ (z >>> 15)) >>> 0;
    };
}

// 标准 2D 柏林噪声（与 Java 版 PerlinNoise2D 同款算法）
class PerlinNoise2D {
    constructor(seed) {
        const perm = new Array(512);
        for (let i = 0; i < 256; i++) perm[i] = i;
        const next = createRandom(seed);
        // Fisher-Yates 洗牌
        for (let i = 255; i > 0; i--) {
            const j = next() % (i + 1);
            const t = perm[i];
            perm[i] = perm[j];
            perm[j] = t;
        }
        // 复制一份到后半段，避免边界检查
        for (let i = 0; i < 256; i++) perm[256 + i] = perm[i];
        this.perm = perm;
    }

    fade(t) { return t * t * t * (t * (t * 6 - 15) + 10); }

    // 噪声值，范围约 [-1, 1]
    noise(x, y) {
        const p = this.perm;
        const xi = Math.floor(x) & 255;
        const yi = Math.floor(y) & 255;
        const xf = x - Math.floor(x);
        const yf = y - Math.floor(y);
        const u = this.fade(xf);
        const v = this.fade(yf);
        const aa = p[p[xi] + yi];
        const ab = p[p[xi] + yi + 1];
        const ba = p[p[xi + 1] + yi];
        const bb = p[p[xi + 1] + yi + 1];
        // 2D 梯度向量（8 个标准方向）
        const GRAD2 = [[1, 1], [-1, 1], [1, -1], [-1, -1], [1, 0], [-1, 0], [0, 1], [0, -1]];
        const g1 = GRAD2[aa & 7][0] * xf + GRAD2[aa & 7][1] * yf;
        const g2 = GRAD2[ba & 7][0] * (xf - 1) + GRAD2[ba & 7][1] * yf;
        const g3 = GRAD2[ab & 7][0] * xf + GRAD2[ab & 7][1] * (yf - 1);
        const g4 = GRAD2[bb & 7][0] * (xf - 1) + GRAD2[bb & 7][1] * (yf - 1);
        // 双线性插值
        const x1 = g1 + u * (g2 - g1);
        const x2 = g3 + u * (g4 - g3);
        return x1 + v * (x2 - x1);
    }

    // 归一化到 [0, 1]
    noiseNormalized(x, y) {
        let n = this.noise(x, y);
        if (n < -1) n = -1;
        else if (n > 1) n = 1;
        return (n + 1) * 0.5;
    }
}

// 24 色渐变表，与 Java 版 ColorGenerator 同款：亮蓝 → 青 → 绿 → 黄 → 橙 → 红 → 暖白 → 纯白
const COLOR_TABLE = [
    [59, 130, 246], [96, 165, 250], [147, 197, 253], [125, 211, 252], [56, 189, 248],
    [34, 211, 238], [103, 232, 249], [110, 231, 183], [52, 211, 153], [163, 230, 53],
    [253, 224, 71], [250, 204, 21], [251, 191, 36], [251, 146, 60], [249, 115, 22],
    [239, 68, 68], [248, 113, 113], [253, 186, 116], [254, 215, 170], [254, 243, 199],
    [255, 251, 235], [255, 247, 237], [255, 250, 240], [255, 255, 255],
];

// 噪声值映射为颜色（相邻颜色线性插值）
function colorFromNoise(noise) {
    if (isNaN(noise) || noise <= 0) return COLOR_TABLE[0];
    if (noise >= 1) return COLOR_TABLE[COLOR_TABLE.length - 1];
    const pos = noise * (COLOR_TABLE.length - 1);
    const idx = Math.floor(pos);
    const frac = pos - idx;
    if (idx >= COLOR_TABLE.length - 1) return COLOR_TABLE[COLOR_TABLE.length - 1];
    const c1 = COLOR_TABLE[idx], c2 = COLOR_TABLE[idx + 1];
    return [
        Math.round(c1[0] + (c2[0] - c1[0]) * frac),
        Math.round(c1[1] + (c2[1] - c1[1]) * frac),
        Math.round(c1[2] + (c2[2] - c1[2]) * frac),
    ];
}

// 相对亮度（sRGB 线性化后加权），用于选择前景文字颜色
function luminance(rgb) {
    const f = (v) => {
        v /= 255;
        return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    };
    return 0.2126 * f(rgb[0]) + 0.7152 * f(rgb[1]) + 0.0722 * f(rgb[2]);
}

if (typeof module !== 'undefined' && module.exports) {
    module.exports = { PerlinNoise2D, colorFromNoise, luminance };
}
