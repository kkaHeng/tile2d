package com.ahheng.tile2d.util.time;

import android.os.Debug;

// 默认时间提供器(Android)
// nanoTime: 墙钟时间(System.nanoTime),用于帧预算等与帧周期同尺度的计量
// cpuNanoTime: 当前线程 CPU 时间(Debug.threadCpuTimeNanos),用于排除线程抢占干扰的纯性能分析
public class DefaultTimeProvider implements TimeProvider {

    @Override
    public long nanoTime() {
        return System.nanoTime();
    }

    @Override
    public long cpuNanoTime() {
        return Debug.threadCpuTimeNanos();
    }
}