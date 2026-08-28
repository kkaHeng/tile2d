package com.ahheng.tile2d.dimen;

// 可测量接口
// 将宽高约束与测量结果解耦,供需要自定测量逻辑的容器实现
public interface Measurable {

    // 执行测量,结果写入 out 数组(约定 [宽, 高])
    void measure(int widthMeasureSpec, int heightMeasureSpec, int[] out);

}