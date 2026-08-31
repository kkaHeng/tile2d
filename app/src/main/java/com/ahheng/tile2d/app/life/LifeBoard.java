package com.ahheng.tile2d.app.life;

import java.util.HashMap;
import java.util.Map;

// 生命游戏棋盘（无限平面）
// 存储：活细胞稀疏表 key = 列<<32 | 行(无符号低32位)，value = 年龄（连续存活代数，从 1 起）
//       死细胞不占存储，因此平面在 int32 范围内"无限"，内存只与活细胞数量相关
// 规则（Conway B3/S23）：活细胞邻居 2~3 存活，其余死亡；死细胞邻居恰好 3 复活
// 线程模型：compute 是静态纯函数，只读快照、不改动任何对象，可安全放子线程；
//           主线程编辑时若快照仍被子线程持有，先复制内部表（copy-on-write）再改，杜绝竞态
public class LifeBoard {

    // 活细胞上限：无限增长图案（如滑翔机枪）到顶后由调用方暂停，避免 OOM
    public static final int MAX_POPULATION = 200000;

    // 年龄着色档位数（渲染端按档位选外观，档位不变则无需重绘）
    public static final int AGE_LEVELS = 4;

    private Map<Long, Integer> cells = new HashMap<>();
    private int generation;

    // 快照冻结标记：为 true 表示 cells 引用已交给子线程读取，编辑前必须先复制
    private boolean frozen;

    // ========== 坐标打包 ==========

    public static long key(int column, int row) {
        return ((long) column << 32) | (row & 0xFFFFFFFFL);
    }

    public static int columnOf(long key) {
        return (int) (key >> 32);
    }

    public static int rowOf(long key) {
        return (int) key;
    }

    // ========== 查询 ==========

    public boolean isAlive(int column, int row) {
        return cells.containsKey(key(column, row));
    }

    // 年龄：存活代数（1 = 本代新生），死细胞返回 0
    public int getAge(int column, int row) {
        Integer age = cells.get(key(column, row));
        return age == null ? 0 : age;
    }

    public int getPopulation() {
        return cells.size();
    }

    public int getGeneration() {
        return generation;
    }

    public boolean isEmpty() {
        return cells.isEmpty();
    }

    // 年龄档位（0 = 死亡，1..AGE_LEVELS = 由新到老）：渲染取色与变化判定共用同一口径
    public static int ageLevel(int age) {
        if (age <= 0) return 0;
        if (age == 1) return 1;   // 新生
        if (age <= 3) return 2;   // 年轻
        if (age <= 9) return 3;   // 成熟
        return 4;                 // 长寿
    }

    // ========== 编辑（主线程） ==========

    // 翻转单个细胞状态，返回翻转后是否存活
    public boolean toggle(int column, int row) {
        detachIfFrozen();
        long k = key(column, row);
        if (cells.remove(k) != null) {
            return false;
        }
        cells.put(k, 1);
        return true;
    }

    public void setAlive(int column, int row, boolean alive) {
        detachIfFrozen();
        long k = key(column, row);
        if (alive) {
            cells.put(k, 1);
        } else {
            cells.remove(k);
        }
    }

    public void clear() {
        detachIfFrozen();
        cells.clear();
        generation = 0;
    }

    // 放置图案：offsets 为 [列偏移, 行偏移] 交替的扁平数组，以 (originColumn, originRow) 为原点
    public void placePattern(int[] offsets, int originColumn, int originRow) {
        if (offsets == null) return;
        detachIfFrozen();
        for (int i = 0; i + 1 < offsets.length; i += 2) {
            cells.put(key(originColumn + offsets[i], originRow + offsets[i + 1]), 1);
        }
    }

    // 在指定矩形范围内按概率随机播撒活细胞（不清空原有细胞）
    public void randomFill(int left, int top, int right, int bottom, float probability, java.util.Random random) {
        detachIfFrozen();
        for (int c = left; c <= right; c++) {
            for (int r = top; r <= bottom; r++) {
                if (random.nextFloat() < probability) {
                    cells.put(key(c, r), 1);
                }
            }
        }
    }

    // ========== 世代推进 ==========

    // 取出供子线程计算的快照：标记冻结，之后主线程任何编辑都会先复制内部表
    public Map<Long, Integer> snapshotForCompute() {
        frozen = true;
        return cells;
    }

    // 应用子线程算好的下一代：整表替换 + 代数自增
    public void applyGeneration(Generation next) {
        if (next == null) return;
        cells = next.cells;
        frozen = false;
        generation++;
    }

    // 编辑前的 copy-on-write：内部表若已交给子线程，先复制一份再改，
    // 子线程继续读旧表（其结果会被调用方按代次令牌丢弃）
    private void detachIfFrozen() {
        if (frozen) {
            cells = new HashMap<>(cells);
            frozen = false;
        }
    }

    // 计算下一代（纯函数，可在任意线程执行）
    // 只遍历活细胞及其邻居，复杂度与活细胞数成正比，与平面大小无关
    public static Generation compute(Map<Long, Integer> snapshot) {
        // 邻居计数表：只有活细胞的邻居会进表，因此死区完全不参与计算
        Map<Long, int[]> counts = new HashMap<>(Math.max(16, snapshot.size() * 2));
        for (Long aliveKey : snapshot.keySet()) {
            int col = columnOf(aliveKey);
            int row = rowOf(aliveKey);
            for (int dc = -1; dc <= 1; dc++) {
                for (int dr = -1; dr <= 1; dr++) {
                    if (dc == 0 && dr == 0) continue;
                    // int32 边界保护：越界邻居直接跳过，避免坐标溢出回绕
                    if (dc < 0 && col == Integer.MIN_VALUE) continue;
                    if (dc > 0 && col == Integer.MAX_VALUE) continue;
                    if (dr < 0 && row == Integer.MIN_VALUE) continue;
                    if (dr > 0 && row == Integer.MAX_VALUE) continue;
                    long nk = key(col + dc, row + dr);
                    int[] slot = counts.get(nk);
                    if (slot == null) {
                        counts.put(nk, new int[]{1});
                    } else {
                        slot[0]++;
                    }
                }
            }
        }

        Map<Long, Integer> next = new HashMap<>(Math.max(16, snapshot.size() * 2));
        LongList changed = new LongList(Math.max(16, snapshot.size() / 2));

        // 遍历候选（所有有活邻居的格子）：存活判定 + 复活判定
        for (Map.Entry<Long, int[]> e : counts.entrySet()) {
            long k = e.getKey();
            int n = e.getValue()[0];
            Integer age = snapshot.get(k);
            if (age != null) {
                if (n == 2 || n == 3) {
                    int newAge = age + 1;
                    next.put(k, newAge);
                    // 只有着色档位变化才需要重绘，避免长寿细胞每代都刷
                    if (ageLevel(newAge) != ageLevel(age)) changed.add(k);
                } else {
                    changed.add(k); // 拥挤/孤独死亡
                }
            } else if (n == 3) {
                next.put(k, 1);
                changed.add(k); // 繁殖新生
            }
        }

        // 邻居数为 0 的活细胞不会出现在 counts 中，需单独补上它们的死亡
        for (Long aliveKey : snapshot.keySet()) {
            if (!counts.containsKey(aliveKey)) {
                changed.add(aliveKey);
            }
        }

        return new Generation(next, changed.toArray());
    }

    // 一代的计算结果：下一代完整状态 + 需要重绘的坐标集合
    public static class Generation {

        public final Map<Long, Integer> cells;
        public final long[] changed;

        Generation(Map<Long, Integer> cells, long[] changed) {
            this.cells = cells;
            this.changed = changed;
        }

        public int getPopulation() {
            return cells.size();
        }

        public boolean isStable() {
            return changed.length == 0;
        }
    }

    // 轻量 long 动态数组：避免 ArrayList<Long> 的装箱开销
    private static class LongList {

        private long[] data;
        private int size;

        LongList(int capacity) {
            data = new long[Math.max(16, capacity)];
        }

        void add(long value) {
            if (size == data.length) {
                long[] bigger = new long[data.length << 1];
                System.arraycopy(data, 0, bigger, 0, size);
                data = bigger;
            }
            data[size++] = value;
        }

        long[] toArray() {
            long[] result = new long[size];
            System.arraycopy(data, 0, result, 0, size);
            return result;
        }
    }

}
