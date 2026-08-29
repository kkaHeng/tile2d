package com.ahheng.tile2d.app.gomoku;

import com.ahheng.tile2d.TileCoreService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

// 五子棋 AI：棋型评分 + α-β 剪枝 + 迭代加深 + 时间预算（标准竞技做法）
// 1) 增量评估：GomokuBoard 维护黑-白分差与五连标志，O(1)
// 2) 必应着法：立即五连、对手一步五连威胁必堵
// 3) 迭代加深 + 时间预算：超时用已完成层的结论，落子间隔恒定
// 4) 评估随机扰动：叶子评分叠加微量噪声，相同开局不再走出固定的应对序列
// 性能要点（实测中棋局变长不能明显变慢）：
//   内部节点用邻域计数粗排（O(候选数)），只有根节点做全量启发式精排
//   搜索宽度随深度递减（8/6/5/4...），主变化走全宽，其余 PVS 零窗试探失败才重搜
//   五连检测用棋盘增量标志，替代每落子一次的四方向扫描
public class GomokuAI {

    public static final int BLACK = GomokuBoard.BLACK;
    public static final int WHITE = GomokuBoard.WHITE;
    public static final int EMPTY = GomokuBoard.EMPTY;

    // 默认思考时间预算（毫秒）
    public static final long DEFAULT_TIME_BUDGET_MS = 250;

    // 框遍历的跨度上限：超过则视为稀疏布局，退回逐棋子周围生成候选，防止伪无限模式极远落子拖死遍历
    private static final long FRAME_SPAN_LIMIT = 4096;

    private final int maxDepth;
    private final int candidateLimit;

    // 评估随机扰动：叶子评分噪声上限。远小于最小战术威胁分（眠三=50），
    // 只影响"近似等价着法"之间的挑选，不会因此漏防冲四/活四
    private static final long EVAL_NOISE = 12;
    private final Random random = new Random();

    // 迭代加深：当前完成深度的最佳着法（由 searchRoot 写入，chooseMove 取最后完成层）
    private final int[] bestMove = new int[2];

    // 搜索超时信号：时间预算耗尽，向上抛出终止本层搜索
    private static final class SearchTimeout extends RuntimeException {
    }

    public GomokuAI() {
        this(4, 10);
    }

    public GomokuAI(int maxDepth, int candidateLimit) {
        this.maxDepth = maxDepth;
        this.candidateLimit = candidateLimit;
    }

    // 计算一步棋（默认时间预算）
    public int[] chooseMove(GomokuBoard board, int color) {
        return chooseMove(board, color, DEFAULT_TIME_BUDGET_MS);
    }

    // 计算一步棋：迭代加深搜索，超时返回已完成深度的最佳着法
    public int[] chooseMove(GomokuBoard board, int color, long timeBudgetMs) {
        long deadline = System.currentTimeMillis() + Math.max(timeBudgetMs, 50);

        List<int[]> candidates = generateCandidates(board);
        if (candidates.isEmpty()) {
            // 空棋盘落中心
            int mid = board.isInfinite() ? 0 : board.getCenter();
            return new int[]{mid, mid};
        }
        rankCandidates(board, candidates, color);
        if (candidates.size() > candidateLimit) {
            candidates = new ArrayList<>(candidates.subList(0, candidateLimit));
        }

        // 1) 立即胜利：落子即成五连，直接返回
        for (int[] c : candidates) {
            board.place(c[0], c[1], color);
            boolean win = board.isFiveMade();
            board.remove(c[0], c[1]);
            if (win) return new int[]{c[0], c[1]};
        }

        // 2) 对手一步五连威胁：必须占住堵点（防守不犯错的关键）
        int opp = other(color);
        List<int[]> threats = new ArrayList<>();
        for (int[] c : candidates) {
            board.place(c[0], c[1], opp);
            boolean win = board.isFiveMade();
            board.remove(c[0], c[1]);
            if (win) threats.add(c);
        }
        if (!threats.isEmpty()) {
            if (threats.size() == 1) {
                return new int[]{threats.get(0)[0], threats.get(0)[1]};
            }
            // 多个威胁点：对手已成活四/双冲四，选启发式最高的堵点尽力防守
            int[] bestT = threats.get(0);
            for (int[] t : threats) {
                if (t[2] > bestT[2]) bestT = t;
            }
            return new int[]{bestT[0], bestT[1]};
        }

        // 3) 迭代加深：深度逐层递增，超时用已完成层的结论
        bestMove[0] = candidates.get(0)[0];
        bestMove[1] = candidates.get(0)[1];
        for (int depth = 1; depth <= maxDepth; depth++) {
            if (System.currentTimeMillis() > deadline) break;
            try {
                searchRoot(board, color, candidates, depth, deadline);
            } catch (SearchTimeout e) {
                break;
            }
        }
        return new int[]{bestMove[0], bestMove[1]};
    }

    // 根层搜索：对每个候选落子后深入 α-β，记录本层最佳着法到 bestMove（根层走全宽 + 精排）
    private long searchRoot(GomokuBoard board, int color, List<int[]> candidates,
                            int depth, long deadline) {
        long best = Long.MIN_VALUE;
        long alpha = Long.MIN_VALUE + 1;
        for (int[] c : candidates) {
            board.place(c[0], c[1], color);
            long v;
            if (board.isFiveMade()) {
                v = 10_000_000L + depth * 1000L; // 本层立即获胜
            } else if (best == Long.MIN_VALUE) {
                v = alphabeta(board, depth - 1, alpha, Long.MAX_VALUE - 1,
                        other(color), color, 0, deadline);
            } else {
                // 主变化已定，后续零窗试探，失败才全窗重搜（PVS）
                v = alphabeta(board, depth - 1, alpha, alpha + 1,
                        other(color), color, 0, deadline);
                if (v > alpha) {
                    v = alphabeta(board, depth - 1, alpha, Long.MAX_VALUE - 1,
                            other(color), color, 0, deadline);
                }
            }
            board.remove(c[0], c[1]);
            if (v > best) {
                best = v;
                bestMove[0] = c[0];
                bestMove[1] = c[1];
                alpha = Math.max(alpha, best);
            }
        }
        return best;
    }

    // α-β 剪枝。rootColor 是搜索根节点（走棋方）的颜色，评估始终以 rootColor 为正，
    // color 是当前节点轮到谁走，最大化/最小化由 color == rootColor 决定，保证视角对称。
    // ply 为当前层数（根层=0），搜索宽度随 ply 递减：深处少看几步棋，把预算留给浅层。
    private long alphabeta(GomokuBoard board, int depth, long alpha, long beta,
                           int color, int rootColor, int ply, long deadline) {
        if (System.currentTimeMillis() > deadline) throw new SearchTimeout();
        if (depth == 0) {
            return evaluate(board, rootColor);
        }

        List<int[]> candidates = generateCandidates(board);
        if (candidates.isEmpty()) {
            return evaluate(board, rootColor);
        }
        if (ply == 0) {
            rankCandidates(board, candidates, rootColor);
        } else {
            rankCoarse(board, candidates);
        }
        int width = searchWidth(ply);
        if (candidates.size() > width) {
            candidates = candidates.subList(0, width);
        }

        int opponent = other(color);
        if (color == rootColor) {
            // 最大化节点：rootColor 落子
            long best = Long.MIN_VALUE;
            boolean first = true;
            for (int[] c : candidates) {
                board.place(c[0], c[1], color);
                long v;
                if (board.isFiveMade()) {
                    v = 10_000_000L + depth * 1000L; // rootColor 获胜
                } else if (first) {
                    v = alphabeta(board, depth - 1, alpha, beta, opponent, rootColor, ply + 1, deadline);
                } else {
                    // 零窗试探，证明更优才全窗重搜（PVS）
                    v = alphabeta(board, depth - 1, alpha, alpha + 1, opponent, rootColor, ply + 1, deadline);
                    if (v > alpha) {
                        v = alphabeta(board, depth - 1, alpha, beta, opponent, rootColor, ply + 1, deadline);
                    }
                }
                board.remove(c[0], c[1]);
                first = false;
                best = Math.max(best, v);
                alpha = Math.max(alpha, best);
                if (beta <= alpha) break;
            }
            return best;
        } else {
            // 最小化节点：对手落子
            long best = Long.MAX_VALUE;
            boolean first = true;
            for (int[] c : candidates) {
                board.place(c[0], c[1], color);
                long v;
                if (board.isFiveMade()) {
                    v = -10_000_000L - depth * 1000L; // 对手获胜，rootColor 大劣
                } else if (first) {
                    v = alphabeta(board, depth - 1, alpha, beta, opponent, rootColor, ply + 1, deadline);
                } else {
                    // 零窗试探，证明更优才全窗重搜（PVS）
                    v = alphabeta(board, depth - 1, beta - 1, beta, opponent, rootColor, ply + 1, deadline);
                    if (v < beta) {
                        v = alphabeta(board, depth - 1, alpha, beta, opponent, rootColor, ply + 1, deadline);
                    }
                }
                board.remove(c[0], c[1]);
                first = false;
                best = Math.min(best, v);
                beta = Math.min(beta, best);
                if (beta <= alpha) break;
            }
            return best;
        }
    }

    // 搜索宽度随深度递减：浅层看得宽，深层看得窄，把预算集中在最可能有用的分支
    private int searchWidth(int ply) {
        switch (ply) {
            case 0: return 16;
            case 1: return 12;
            case 2: return 8;
            case 3: return 6;
            default: return 5;
        }
    }

    // 候选点：优先有效边界框外扩两格遍历（棋子聚集时开销≈框面积），
    // 框跨度超限或框面积更大时退回"逐棋子周围两格"（稀疏布局防退化）
    private List<int[]> generateCandidates(GomokuBoard board) {
        Set<Long> seen = new HashSet<>();
        List<int[]> result = new ArrayList<>();
        if (!board.hasStones()) return result;

        long minC = board.getMinCol() - 2;
        long maxC = board.getMaxCol() + 2;
        long minR = board.getMinRow() - 2;
        long maxR = board.getMaxRow() + 2;
        long spanC = maxC - minC + 1;
        long spanR = maxR - minR + 1;
        double area = (double) spanC * (double) spanR;
        double perStone = 25.0 * board.getStoneCount();

        if (spanC <= FRAME_SPAN_LIMIT && spanR <= FRAME_SPAN_LIMIT && area <= perStone) {
            // 框遍历：棋子聚集，框面积 ≤ 逐棋子开销
            for (long c = minC; c <= maxC; c++) {
                for (long r = minR; r <= maxR; r++) {
                    if (c < Integer.MIN_VALUE || c > Integer.MAX_VALUE) continue;
                    if (r < Integer.MIN_VALUE || r > Integer.MAX_VALUE) continue;
                    int nc = (int) c;
                    int nr = (int) r;
                    if (!board.inBounds(nc, nr)) continue;
                    if (board.get(nc, nr) != EMPTY) continue;
                    long key = TileCoreService.getTileId(nc, nr);
                    if (seen.add(key)) {
                        result.add(new int[]{nc, nr, 0});
                    }
                }
            }
            return result;
        }

        // 逐棋子周围两格（稀疏或框过大）
        for (long id : board.getCells().keySet()) {
            int col = TileCoreService.getColumn(id);
            int row = TileCoreService.getRow(id);
            for (int dc = -2; dc <= 2; dc++) {
                for (int dr = -2; dr <= 2; dr++) {
                    int nc = col + dc;
                    int nr = row + dr;
                    if (!board.inBounds(nc, nr)) continue;
                    if (board.get(nc, nr) != EMPTY) continue;
                    long key = TileCoreService.getTileId(nc, nr);
                    if (seen.add(key)) {
                        result.add(new int[]{nc, nr, 0});
                    }
                }
            }
        }
        return result;
    }

    // 内部节点粗排：只统计邻域（8 向 + 16 向二线）双方棋子数与空位，
    // O(候选数 × 24 次 Map 查询)，约为全量启发式的 1/3 成本
    private void rankCoarse(GomokuBoard board, List<int[]> candidates) {
        for (int[] c : candidates) {
            int col = c[0];
            int row = c[1];
            int mine = 0;
            int theirs = 0;
            for (int[] d : RANK_DIRS) {
                int cc = col + d[0];
                int rr = row + d[1];
                if (board.inBounds(cc, rr)) {
                    int stone = board.get(cc, rr);
                    if (stone == BLACK) mine++;
                    else if (stone == WHITE) theirs++;
                }
                cc = col + d[0] * 2;
                rr = row + d[1] * 2;
                if (board.inBounds(cc, rr)) {
                    int stone = board.get(cc, rr);
                    if (stone == BLACK) mine++;
                    else if (stone == WHITE) theirs++;
                }
            }
            c[2] = mine * 8 + theirs * 9 + (mine > 0 && theirs > 0 ? 4 : 0);
        }
        candidates.sort((a, b) -> Integer.compare(b[2], a[2]));
    }

    // 粗排扫描方向（8 邻域，代码中另扫 2 格远）
    private static final int[][] RANK_DIRS = {
            {1, 0}, {0, 1}, {1, 1}, {1, -1}, {-1, 0}, {0, -1}, {-1, -1}, {-1, 1}
    };

    // 启发式排序：落子后己方/对方棋型改善分，降序（只用于根节点）
    private void rankCandidates(GomokuBoard board, List<int[]> candidates, int color) {
        for (int[] c : candidates) {
            c[2] = (int) Math.min(Integer.MAX_VALUE, heuristic(board, c[0], c[1], color));
        }
        candidates.sort((a, b) -> Integer.compare(b[2], a[2]));
    }

    // 全盘评估：由棋盘增量维护的黑-白分差直接给出（O(1)），叠加微量随机扰动
    private long evaluate(GomokuBoard board, int color) {
        return board.evaluateFor(color) + random.nextInt((int) (EVAL_NOISE * 2 + 1)) - EVAL_NOISE;
    }

    // 以 (col,row) 为落子点，检查该点所在四条线上是否有五连（非热路径用）
    public static boolean hasFive(GomokuBoard board, int col, int row, int color) {
        int[][] dirs = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
        for (int[] d : dirs) {
            int count = 1;
            int c = col + d[0];
            int r = row + d[1];
            while (board.inBounds(c, r) && board.get(c, r) == color) {
                count++;
                c += d[0];
                r += d[1];
            }
            c = col - d[0];
            r = row - d[1];
            while (board.inBounds(c, r) && board.get(c, r) == color) {
                count++;
                c -= d[0];
                r -= d[1];
            }
            if (count >= 5) return true;
        }
        return false;
    }

    // 单点启发式：落子 (col,row) 后对己方与对方棋型的改善（用于候选点排序）
    private long heuristic(GomokuBoard board, int col, int row, int color) {
        long score = 0;
        int[][] dirs = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
        for (int[] d : dirs) {
            // 己方方向
            int len = 1;
            int pc = col + d[0];
            int pr = row + d[1];
            while (board.inBounds(pc, pr) && board.get(pc, pr) == color) {
                len++;
                pc += d[0];
                pr += d[1];
            }
            int bc = col - d[0];
            int br = row - d[1];
            while (board.inBounds(bc, br) && board.get(bc, br) == color) {
                len++;
                bc -= d[0];
                br -= d[1];
            }
            boolean openBack = board.inBounds(bc, br) && board.get(bc, br) == EMPTY;
            boolean openFront = board.inBounds(pc, pr) && board.get(pc, pr) == EMPTY;
            int open = (openBack ? 1 : 0) + (openFront ? 1 : 0);
            score += GomokuBoard.lineScore(len, open);

            // 对方方向（防守权重略高，防止只顾进攻）
            int opp = other(color);
            int olen = 1;
            pc = col + d[0];
            pr = row + d[1];
            while (board.inBounds(pc, pr) && board.get(pc, pr) == opp) {
                olen++;
                pc += d[0];
                pr += d[1];
            }
            bc = col - d[0];
            br = row - d[1];
            while (board.inBounds(bc, br) && board.get(bc, br) == opp) {
                olen++;
                bc -= d[0];
                br -= d[1];
            }
            boolean oBack = board.inBounds(bc, br) && board.get(bc, br) == EMPTY;
            boolean oFront = board.inBounds(pc, pr) && board.get(pc, pr) == EMPTY;
            int oOpen = (oBack ? 1 : 0) + (oFront ? 1 : 0);
            score += GomokuBoard.lineScore(olen, oOpen) * 11 / 10; // 防守权重略高
        }
        return score;
    }

    private static int other(int color) {
        return color == BLACK ? WHITE : BLACK;
    }

}