package com.ahheng.tile2d.app.gomoku;

import com.ahheng.tile2d.TileCoreService;

import java.util.HashMap;
import java.util.Map;

// 五子棋棋盘模型（稀疏存储）
// 有限模式边界 [min, max] 闭合（默认 -100 ~ +100，201×201），伪无限模式边界为完整 int32 空间
// 评估采用增量维护：落子/提子只更新该点四条线上的棋型分差，与棋子总数无关，
// 全盘分数 O(1) 可得，避免 α-β 搜索中每叶子节点全盘扫描导致"越下越慢"
public class GomokuBoard {

    public static final int EMPTY = 0;
    public static final int BLACK = 1;
    public static final int WHITE = 2;

    // 棋型权重 [连子长度][两端开放数(0/1/2)]，长度≥5 视为必胜
    private static final long[][] SCORE = {
            {},
            {0, 5, 10},        // 单子：眠/半活/活
            {5, 50, 200},      // 活二
            {50, 800, 5000},   // 活三
            {800, 10000, 100000}, // 活四
            {10000000, 10000000, 10000000} // 五连
    };

    private final int min; // 有限模式最小坐标（含）
    private final int max; // 有限模式最大坐标（含）
    private final boolean infinite; // 是否为伪无限模式
    private final Map<Long, Integer> cells = new HashMap<>();

    private int lastCol = Integer.MIN_VALUE;
    private int lastRow = Integer.MIN_VALUE;
    private int stoneCount;

    // 对局日志：落子时直接追加格式化文本（[黑][列, 行] / [白][列, 行]，每手一行）。
    // 历史不可撤销，无需结构化中间态；AI 搜索在副本上进行且副本关闭记录，
    // 搜索的临时落子不会进入任何日志；真实棋盘只在玩家/AI 正式落子时追加
    private final StringBuilder moveLog = new StringBuilder();
    private boolean recordTrace = true;

    // 增量评估：黑方棋型贡献 - 白方棋型贡献（每落子只更新四条线，O(1) 可逆）
    private long totalScore;

    // 最近一次 place 是否形成五连（搜索即时判定用，省去 hasFive 的重复扫描）
    private boolean fiveMade;

    // 有效棋子边界（long 防 int 极值溢出）。只增不减：remove 边界点不收缩，
    // 供候选点生成框遍历使用，框偏大只多遍历空格，不影响正确性
    private long minCol = Long.MAX_VALUE;
    private long maxCol = Long.MIN_VALUE;
    private long minRow = Long.MAX_VALUE;
    private long maxRow = Long.MIN_VALUE;

    public GomokuBoard(int min, int max, boolean infinite) {
        this.min = min;
        this.max = max;
        this.infinite = infinite;
    }

    // 有限模式单边跨度（含端点），伪无限模式下仅用于 AI 空棋盘落点判断
    public int getSize() {
        return max - min + 1;
    }

    public int getMin() {
        return min;
    }

    public int getMax() {
        return max;
    }

    // 棋盘中心坐标（long 运算防极值溢出）
    public int getCenter() {
        return (int) (((long) min + max) / 2);
    }

    public boolean isInfinite() {
        return infinite;
    }

    public int getStoneCount() {
        return stoneCount;
    }

    public int getLastCol() {
        return lastCol;
    }

    public int getLastRow() {
        return lastRow;
    }

    public boolean hasStones() {
        return !cells.isEmpty();
    }

    public long getMinCol() {
        return minCol;
    }

    public long getMaxCol() {
        return maxCol;
    }

    public long getMinRow() {
        return minRow;
    }

    public long getMaxRow() {
        return maxRow;
    }

    // 坐标是否在棋盘范围内
    public boolean inBounds(int column, int row) {
        return infinite || (column >= min && column <= max && row >= min && row <= max);
    }

    // 指定坐标是否可落子
    public boolean canPlace(int column, int row) {
        if (!inBounds(column, row)) return false;
        return !cells.containsKey(TileCoreService.getTileId(column, row));
    }

    // 落子，返回是否成功。增量更新四条线的棋型分差与五连标志，并记录历史
    public boolean place(int column, int row, int color) {
        if (!canPlace(column, row)) return false;
        long emptyScore = lineScoreIfEmpty(column, row);
        cells.put(TileCoreService.getTileId(column, row), color);
        fiveMade = false;
        long filledScore = lineScoreIfFilled(column, row, color);
        totalScore += filledScore - emptyScore;
        if (recordTrace) {
            moveLog.append(color == BLACK ? "[黑]" : "[白]")
                    .append("[").append(column).append(", ").append(row).append("]\n");
        }
        // 扩展有效边界（只增不减）
        if (column < minCol) minCol = column;
        if (column > maxCol) maxCol = column;
        if (row < minRow) minRow = row;
        if (row > maxRow) maxRow = row;
        lastCol = column;
        lastRow = row;
        stoneCount++;
        return true;
    }

    public int get(int column, int row) {
        Integer color = cells.get(TileCoreService.getTileId(column, row));
        return color == null ? EMPTY : color;
    }

    // 撤销最后一手（AI 搜索用），增量分数同步回退
    public void undo(int column, int row) {
        remove(column, row);
    }

    // 直接移除指定坐标的棋子（AI 搜索用），不维护 last。增量分数同步回退
    public void remove(int column, int row) {
        int color = get(column, row);
        if (color == EMPTY) return;
        long filledScore = lineScoreIfFilled(column, row, color);
        cells.remove(TileCoreService.getTileId(column, row));
        long emptyScore = lineScoreIfEmpty(column, row);
        totalScore -= filledScore - emptyScore;
        stoneCount--;
    }

    public boolean isEmpty() {
        return cells.isEmpty();
    }

    // 胜负判定：从最后一手向四个方向检查是否五连（有限/伪无限通用）。
    // 成本 O(4方向×8步)≈32 次哈希查询/手，与棋盘大小、棋子总数无关；
    // 伪无限模式无边界，延伸由非同色子/空位自然终止
    public int checkWinner() {
        if (lastCol == Integer.MIN_VALUE) return EMPTY;
        int color = get(lastCol, lastRow);
        if (color == EMPTY) return EMPTY;

        int[][] dirs = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
        for (int[] dir : dirs) {
            int count = 1;
            // 正方向
            int c = lastCol + dir[0];
            int r = lastRow + dir[1];
            while (inBounds(c, r) && get(c, r) == color) {
                count++;
                c += dir[0];
                r += dir[1];
            }
            // 反方向
            c = lastCol - dir[0];
            r = lastRow - dir[1];
            while (inBounds(c, r) && get(c, r) == color) {
                count++;
                c -= dir[0];
                r -= dir[1];
            }
            if (count >= 5) return color;
        }
        return EMPTY;
    }

    // 清空棋盘
    public void clear() {
        cells.clear();
        moveLog.setLength(0);
        lastCol = Integer.MIN_VALUE;
        lastRow = Integer.MIN_VALUE;
        stoneCount = 0;
        totalScore = 0;
        minCol = Long.MAX_VALUE;
        maxCol = Long.MIN_VALUE;
        minRow = Long.MAX_VALUE;
        maxRow = Long.MIN_VALUE;
    }

    // 深拷贝（AI 搜索在副本上进行，避免搜索的临时落子被 UI 绘制看到）。
    // 副本关闭历史记录：搜索的临时落子不进入任何历史，真实棋盘历史不受影响
    public GomokuBoard copy() {
        GomokuBoard b = new GomokuBoard(min, max, infinite);
        b.cells.putAll(cells);
        b.recordTrace = false; // AI 副本不记录落子历史
        b.lastCol = lastCol;
        b.lastRow = lastRow;
        b.stoneCount = stoneCount;
        b.totalScore = totalScore;
        b.minCol = minCol;
        b.maxCol = maxCol;
        b.minRow = minRow;
        b.maxRow = maxRow;
        return b;
    }

    // 供 AI 读取全部棋子的迭代视图
    public Map<Long, Integer> getCells() {
        return cells;
    }

    // 对局日志：按时间顺序的落子记录文本（[黑][列, 行] / [白][列, 行]，每手一行）
    public String exportMoveLog() {
        int len = moveLog.length();
        if (len == 0) return "";
        // 去掉末尾换行
        return moveLog.substring(0, len - 1);
    }

    // 全盘评估：以 color 为正的棋型分差（增量维护，O(1)）
    public long evaluateFor(int color) {
        return color == BLACK ? totalScore : -totalScore;
    }

    public static long lineScore(int len, int open) {
        if (len >= 5) return SCORE[5][0];
        if (len <= 0) return 0;
        return SCORE[len][open];
    }

    // ========== 增量评估核心 ==========

    private static final int[][] DIRS = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};

    // (col,row) 为空时，过该点四条线的分数之和（左右两段，近端皆开放）
    private long lineScoreIfEmpty(int column, int row) {
        long score = 0;
        for (int[] d : DIRS) {
            score += segmentScore(column, row, d, 1) + segmentScore(column, row, d, -1);
        }
        return score;
    }

    // (col,row) 落 color 后，过该点四条线的分数之和：
    // 落子点同色合并段 + 两侧紧邻的对方段（近端被落子堵住，开放数 -1）
    private long lineScoreIfFilled(int column, int row, int color) {
        long score = 0;
        for (int[] d : DIRS) {
            score += mergedScore(column, row, d, color)
                    + closedSegmentScore(column, row, d, 1, color)
                    + closedSegmentScore(column, row, d, -1, color);
        }
        return score;
    }

    // 从 (col,row) 沿 dir*dirSign 方向的相邻格开始的连续段分数。
    // 前提：(col,row) 为空（计算"落子前"状态），故段的近端（朝向落子点）必开放
    private long segmentScore(int column, int row, int[] d, int dirSign) {
        int c = column + d[0] * dirSign;
        int r = row + d[1] * dirSign;
        if (!inBounds(c, r)) return 0;
        int segColor = get(c, r);
        if (segColor == EMPTY) return 0;

        int len = 1;
        int ec = c + d[0] * dirSign;
        int er = r + d[1] * dirSign;
        while (inBounds(ec, er) && get(ec, er) == segColor) {
            len++;
            ec += d[0] * dirSign;
            er += d[1] * dirSign;
        }
        // 开放数：远端是否空 + 近端（落子点）必空
        boolean openFar = inBounds(ec, er) && get(ec, er) == EMPTY;
        int open = (openFar ? 1 : 0) + 1;
        long s = lineScore(len, open);
        return segColor == BLACK ? s : -s;
    }

    // 落子后紧邻落子点的对方连续段：近端被落子堵住（开放数 -1），远端照常判断
    private long closedSegmentScore(int column, int row, int[] d, int dirSign, int color) {
        int c = column + d[0] * dirSign;
        int r = row + d[1] * dirSign;
        if (!inBounds(c, r)) return 0;
        int segColor = get(c, r);
        if (segColor == EMPTY || segColor == color) return 0; // 只处理对方段

        int len = 1;
        int ec = c + d[0] * dirSign;
        int er = r + d[1] * dirSign;
        while (inBounds(ec, er) && get(ec, er) == segColor) {
            len++;
            ec += d[0] * dirSign;
            er += d[1] * dirSign;
        }
        // 近端（朝向落子点）已被堵，只有远端可能开放
        boolean openFar = inBounds(ec, er) && get(ec, er) == EMPTY;
        int open = (openFar ? 1 : 0);
        long s = lineScore(len, open);
        return segColor == BLACK ? s : -s;
    }

    // (col,row) 落 color 后，沿 dir 方向向两侧延伸的合并段分数
    private long mergedScore(int column, int row, int[] d, int color) {
        int len = 1;
        int c = column + d[0];
        int r = row + d[1];
        while (inBounds(c, r) && get(c, r) == color) {
            len++;
            c += d[0];
            r += d[1];
        }
        boolean openFront = inBounds(c, r) && get(c, r) == EMPTY;
        c = column - d[0];
        r = row - d[1];
        while (inBounds(c, r) && get(c, r) == color) {
            len++;
            c -= d[0];
            r -= d[1];
        }
        boolean openBack = inBounds(c, r) && get(c, r) == EMPTY;
        if (len >= 5) fiveMade = true;
        int open = (openFront ? 1 : 0) + (openBack ? 1 : 0);
        long s = lineScore(len, open);
        return color == BLACK ? s : -s;
    }

    // 最近一次 place 是否形成五连（增量五连检测，搜索热路径用）
    public boolean isFiveMade() {
        return fiveMade;
    }

}