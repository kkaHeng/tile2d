package com.ahheng.tile2d.util.time;

// 时间提供器接口
// 提供墙钟与 CPU 时间两种计量，由调用点按语义自行选择
public interface TimeProvider {

    // 墙钟时间(纳秒,单调),与帧周期同尺度,用于预算/调度类计量
    // 例如预取的时间预算按帧周期计算,必须用墙钟对比,否则线程被抢占时预算会失真
    long nanoTime();

    // 当前线程累计 CPU 时间(纳秒),排除线程被抢占的干扰,用于纯性能分析
    // 平台无 CPU 时钟时默认退化为墙钟
    default long cpuNanoTime() {
        return nanoTime();
    }

}
