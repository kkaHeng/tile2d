package com.ahheng.tile2d.app.minesweeper;

import com.ahheng.tile2d.TileCoreService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MinesweeperSolver {

    // 风险阈值：如果必猜且最低风险高于 30%，AI 将放弃操作
    private static final float RISK_THRESHOLD = 0.3f;

    public static class Action {
        public enum Type { REVEAL, FLAG, UNFLAG }
        public final Type type;
        public final long targetId;
        public Action(Type type, long targetId) {
            this.type = type;
            this.targetId = targetId;
        }
    }

    public static class Step {
        public final List<Action> actions;
        public Step(List<Action> actions) {
            this.actions = actions;
        }
    }

    public static class Constraint {
        Set<Long> vars;
        int mineCount;
        public Constraint(Set<Long> vars, int mineCount) {
            this.vars = vars;
            this.mineCount = mineCount;
        }
    }

    public Step solve(MinesweeperActivity.BoardSnapshot snapshot) {
        Map<Long, Integer> board = snapshot.cells;
        int mineDensity = snapshot.mineDensity;

        // 适配器边界:以 long 存储防止 int32 溢出
        long left   = snapshot.leftBound;
        long top    = snapshot.topBound;
        long right  = snapshot.rightBound;
        long bottom = snapshot.bottomBound;

        // 1. 构建约束网络
        List<Constraint> constraints = new ArrayList<>();
        Set<Long> boundaryUnknowns = new HashSet<>();

        for (Map.Entry<Long, Integer> entry : board.entrySet()) {
            long id = entry.getKey();
            int val = entry.getValue();
            if (val >= 0) {
                int c = TileCoreService.getColumn(id);
                int r = TileCoreService.getRow(id);
                int flaggedCount = 0;
                Set<Long> unknowns = new HashSet<>();
                boolean isComplete = true;
                
                for (int dc = -1; dc <= 1; dc++) {
                    for (int dr = -1; dr <= 1; dr++) {
                        if (dc == 0 && dr == 0) continue;
                        long nc = (long) c + dc;
                        long nr = (long) r + dr;
                        // 以适配器边界为物理边界,用 long 防止 int32 溢出回绕
                        if (nc < left || nc > right || nr < top || nr > bottom) continue;
                        long nid = TileCoreService.getTileId((int) nc, (int) nr);
                        Integer nVal = board.get(nid);
                        
                        if (nVal == null) {
                            isComplete = false;
                            break;
                        }
                        
                        if (nVal == -2) flaggedCount++; 
                        else if (nVal == -1) {          
                            unknowns.add(nid);
                            boundaryUnknowns.add(nid);
                        }
                    }
                    if (!isComplete) break;
                }
                
                if (isComplete && !unknowns.isEmpty()) {
                    constraints.add(new Constraint(unknowns, val - flaggedCount));
                }
            }
        }

        Set<Long> safeCells = new HashSet<>();
        Set<Long> mineCells = new HashSet<>();

        // 2. 逻辑推理：基础规则 + 子集减法规则
        boolean changed = true;
        while (changed) {
            changed = false;
            List<Constraint> activeConstraints = new ArrayList<>();
            for (Constraint c : constraints) {
                Set<Long> newVars = new HashSet<>();
                int newCount = c.mineCount;
                for (Long v : c.vars) {
                    if (safeCells.contains(v)) continue;
                    if (mineCells.contains(v)) {
                        newCount--;
                    } else {
                        newVars.add(v);
                    }
                }
                if (!newVars.isEmpty()) {
                    activeConstraints.add(new Constraint(newVars, newCount));
                }
            }
            constraints = activeConstraints;

            for (Constraint c : constraints) {
                if (c.mineCount == 0) {
                    safeCells.addAll(c.vars);
                    changed = true;
                } else if (c.mineCount == c.vars.size()) {
                    mineCells.addAll(c.vars);
                    changed = true;
                }
            }

            for (int i = 0; i < constraints.size(); i++) {
                Constraint c1 = constraints.get(i);
                for (int j = 0; j < constraints.size(); j++) {
                    if (i == j) continue;
                    Constraint c2 = constraints.get(j);
                    if (c1.vars.containsAll(c2.vars) && c1.vars.size() > c2.vars.size()) {
                        Set<Long> diff = new HashSet<>(c1.vars);
                        diff.removeAll(c2.vars);
                        int diffCount = c1.mineCount - c2.mineCount;
                        
                        if (diffCount == 0 && !safeCells.containsAll(diff)) {
                            safeCells.addAll(diff);
                            changed = true;
                        } else if (diffCount == diff.size() && !mineCells.containsAll(diff)) {
                            mineCells.addAll(diff);
                            changed = true;
                        }
                    }
                }
            }
        }

        List<Action> actions = new ArrayList<>();

        // 3. 批量插旗
        for (Long id : mineCells) {
            Integer state = board.get(id);
            if (state != null && state != -2) {
                actions.add(new Action(Action.Type.FLAG, id));
            }
        }

        // 4. 批量拆旗
        for (Long id : safeCells) {
            Integer state = board.get(id);
            if (state != null && state == -2) {
                actions.add(new Action(Action.Type.UNFLAG, id));
            }
        }

        if (!actions.isEmpty()) {
            return new Step(actions);
        }

        // 5. 点开绝对安全格
        if (!safeCells.isEmpty()) {
            long bestId = findBestGuess(safeCells, snapshot);
            actions.add(new Action(Action.Type.REVEAL, bestId));
            return new Step(actions);
        }

        // 6. 概率猜测阶段
        float minProb = 1.1f;
        long bestBoundaryGuess = -1;
        
        for (Constraint c : constraints) {
            if (c.vars.isEmpty()) continue;
            float prob = (float) c.mineCount / c.vars.size();
            if (prob < minProb) {
                minProb = prob;
                bestBoundaryGuess = findBestGuess(c.vars, snapshot);
            }
        }

        float globalProb = mineDensity / 256.0f;
        
        // 如果边界最小概率 >= 全局概率，去点非边界未知格更安全
        if (minProb >= globalProb) {
            // 只要全局概率低于阈值，就敢去点非边界盲区
            if (globalProb < RISK_THRESHOLD) {
                for (Map.Entry<Long, Integer> entry : board.entrySet()) {
                    if (entry.getValue() == -1 && !boundaryUnknowns.contains(entry.getKey())) {
                        actions.add(new Action(Action.Type.REVEAL, entry.getKey()));
                        return new Step(actions);
                    }
                }
            }
        } else {
            // 边界概率比全局更低，如果低于阈值，就点边界
            if (minProb < RISK_THRESHOLD && bestBoundaryGuess != -1) {
                actions.add(new Action(Action.Type.REVEAL, bestBoundaryGuess));
                return new Step(actions);
            }
        }

        // 如果走到这里，说明最低概率也 >= 阈值，或者没有概率可算且全局概率也 >= 阈值
        // AI 认为走投无路，罢工
        return null;
    }

    private long findBestGuess(Set<Long> ids, MinesweeperActivity.BoardSnapshot snapshot) {
        long bestId = -1;
        double minDist = Double.MAX_VALUE;
        for (long id : ids) {
            int c = TileCoreService.getColumn(id);
            int r = TileCoreService.getRow(id);
            double dist = Math.pow(c - snapshot.centerCol, 2) + Math.pow(r - snapshot.centerRow, 2);
            if (dist < minDist) {
                minDist = dist;
                bestId = id;
            }
        }
        return bestId;
    }
}
