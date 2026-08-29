# Tile2D

在屏幕的方寸之间，铺展无限可能。

---

[![](https://jitpack.io/v/kkaHeng/tile2d.svg)](https://jitpack.io/#kkaHeng/tile2d)


## 简介

Tile2D 是一个支持**伪无限**索引空间的 Android 二维虚拟容器。

## 特性

### 视窗裁剪

视窗外的瓦片**不加载**、**不渲染**，使用`RecyclerView`风格的**适配器模式**降低学习成本，因为`RecyclerView`是安卓开发绕不开的一座大山。

**虚拟化**渲染是保持高性能的关键，避免性能随数据总量线性下降，这是滚动容器的基本配置。

### 双端渲染

提供`TileLayout`与`TileView`两种**默认**渲染范式，其中`TileView`使用`Canvas`渲染，而`TileLayout`则支持**原生View**。

很少有UI框架会兼容不同的UI生态，要么自研UI底层，要么只支持原生View。支持**选择**与**扩展**渲染/交互范式可以免去**生态迁移成本**。

### 范围支持

适配器的**逻辑索引范围**支持**int32**的完整空间，单轴约**42亿**左右。

通常很多传统范式不支持**int32**的**最大值**，甚至连**负数**都不支持，这是因为**数组思维**和它的**左闭右开**常识，还存在淡化**逻辑索引/坐标**存在感的情况。

### 濒死预取

有**濒死区**和**预取区**两种缓存策略，**提前加载**和**保存最近**离开的瓦片。

瓦片离开视窗时**暂存**，回滚时可以**跳过**重新绑定直接复活。预取**默认开启**：按运动**方向预测**，只朝视窗前方扩展条带并分批预加载（每帧最多 8 个，可调），进入视窗时直接转正，避免一次性加载导致阻塞。

### 可变尺寸

支持**低成本**修改指定行、指定列的尺寸，窗口外修改时成本会进一步降低。

在修改尺寸时，支持自定义**对齐方向**，让列/行本身在某方向上不动，其他方向**扩展**或**收缩**。

在很多传统范式中，**尺寸**和**坐标**被严重耦合，导致修改尺寸时需要大量**重算坐标**引起严重性能问题。

### 精度安全

窗口位于**int32**边界附近或离**0**很远时，像素精度不会退化。

除非**单个瓦片**的尺寸直接撞**精度墙**，不过绝大多数UI系统都不支持这么大的**纹理尺寸**，而且，通过拆分内容到不同瓦片也能有效避免这样的问题。

很多传统算法会在滚动到很远的距离时出现精度丢失问题，导致内容**视觉跳动**，出现如瓦片重叠、间距过大、线条宽度异常等问题。

### 容器替换

支持替换底层**数据容器**，追求更高的性能上限，包括**瓦片池**、**尺寸表**、**回收池**等等，基于简单的自定义接口，可以自由组合**第三方数据结构**实现替换。

例如`HashMap`的操作速度快但**装箱**与**拆箱**会有性能问题，而`SparseArray`没有装箱拆箱问题，但**速度较慢**。此外，框架**默认**的数据结构是综合了这两个的**优点**。

### 稀疏存储

不会尝试申请用不上的内存空间（数组），框架全链路支持**稀疏数据**，例如`onCreateTileHolder`可以**安全**返回`null`。

瓦片容器、尺寸表等数据结构都是稀疏存储的，没有使用的位置保持**不存在**，而不是简单的`null`。

---

## 快速开始

### 添加依赖（Gradle）

在你的根目录 `settings.gradle` 文件的 `repositories` 末尾添加它：
```gradle
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google()
        maven { url 'https://jitpack.io' }
    }
}
```

引入依赖：
```gradle
dependencies {
    implementation 'com.github.kkaHeng:tile2d:26.8.1'
}
```

查看最新版本：[Jitpack](https://jitpack.io/#kkaHeng/tile2d)，或者点击页面顶部的徽章
> 版本号规则：年份.月份.大版本.小版本

### 基础使用

#### 使用 TileView（自定义绘制）

```java
TileView tileView = new TileView(context);
tileView.setAdapter(new TileView.Adapter() {
    @Override
    public TileView.TileHolder onCreateTileHolder(int type) {
        return new MyTileHolder();
    }

    @Override
    public void onBindTileHolder(TileView.TileHolder holder, int column, int row) {
        // 绑定数据
    }
});
```

#### 使用 TileLayout（标准 View）

```java
TileLayout tileLayout = new TileLayout(context);
tileLayout.setAdapter(new TileLayout.Adapter() {
    @Override
    public TileLayout.TileHolder onCreateTileHolder(int type) {
        return new MyTileHolder(new TextView(context));
    }

    @Override
    public void onBindTileHolder(TileLayout.TileHolder holder, int column, int row) {
        // 绑定数据
    }
});
```

适配器**默认**的边界是**int32**的**最小值**与**最大值**，就是完整空间。你可以在适配器中重写以下方法实现**自定义**数据范围。边界是**闭区间**，即边界本身也是**合法坐标**。

```java
// 从 -10,-10 到 10,10 共 21列、21行，总计 441 个单元格
@Override
public int getLeftBound() {
    return -10; // 返回左边界
}

@Override
public int getTopBound() {
    return -10; // 返回上边界
}

@Override
public int getRightBound() {
    return 10; // 返回右边界
}

@Override
public int getBottomBound() {
    return 10; // 返回下边界
}
```

---

## 架构

### 示意图

```
渲染交互层
    ↑
  核心层-------┬-------┬-------┐
    ↓      手势处理  瓦片管理  尺寸管理  
 布局引擎
```

### 上层

这一层是最终**渲染**与**交互**的载体，是最外层的壳，类比成**手脚**与**触觉**，掌握瓦片**进出视窗**、事件处理的方式。

代表类：`TileView`、`TileLayout`。

**新手**和**一般业务**需求只需要了解这一层即可。推荐新手使用`TileLayout`，它的性能其实也不差，只是内存占用稍高。

### 中层

这一层是**核心中枢**，类比成**神经系统**，负责接收**上层**的输入，转发到**底层**处理，再通知**上层**反应。

代表类：`TileCoreService`。

### 底层

这一层是框架的**大脑**，负责处理**窗口移动**、**瓦片管理**、**尺寸管理**、**手势处理**等复杂任务。

代表类：`LayoutEngine`（窗口移动）、`TileManager`（瓦片管理）、`DimenManager`（尺寸管理）、`EventHandler`（手势处理）。

---

## 生命周期

```
创建/复用→绑定→预取（可选）→进入视窗→离开视窗→濒死（可选）→回收
```

---

## 许可证

本项目采用 [MIT License](LICENSE) 开源协议。

---

## 扩展阅读

- [API文档](docs/API文档.md) — 更详细的使用方式。
- [安卓平台扩展指南](docs/安卓平台扩展指南.md) — 教怎么在安卓平台内扩展/迁移。
- [跨平台移植指南](docs/跨平台指南.md) — 教怎么在其他平台实现。

---

## 联系方式

- 作者：阿恒
- 邮箱：kkaheng163@163.com
- GitHub：[https://github.com/kkaHeng](https://github.com/kkaHeng)

---

## 示例 Demo

App 内置 **10 个示例**，覆盖两种渲染范式（Canvas 自绘 / 标准 View）与多种应用场景。

- **Debug 模式**
实时显示**活跃**、**回收**、**濒死瓦片**等调试信息。

- **伪无限模式**
数据边界扩展至完整 **int32** 范围，部分 Demo 利用算法与**确定性伪随机数生成器**实现数据生成以填充数据。

- **去边界看看**
一键跳转到数据边界的 8 个极端点，结合**伪无限模式**有奇效。

### 瓦片画板（TileView）

基于 **TileView（Canvas 自绘）** 的噪声纹理演示，是理解自绘范式的最小示例。

- 数据：`PerlinNoise2D` 固定种子采样，`ColorGenerator` 映射为 24 色渐变；低噪区返回 `-1` 产生稀疏空瓦片
- 优化：瓦片内容录制为 `Picture` 后直接回放，避免重复绘制
- 交互：长按删除瓦片（局部刷新）、随机调整列宽/行高（尺寸动画）、切换色块/文本方案

![实机截图](screenshots/tileview.jpg)

### 瓦片布局（TileLayout）

与画板同数据源的 **TileLayout（ViewGroup）** 实现，每个瓦片是真实 `View`。

- 数据：与画板相同的噪声数据源
- 渲染：瓦片为独立 `TextView`，交由硬件加速管线；原生支持点击/长按
- 对照画板，展示同一引擎下 **Canvas 自绘** 与 **View 体系** 两种渲染范式的差异

![实机截图](screenshots/tilelayout.jpg)

### 数据表

基于 **TileLayout** 构建的大规模结构化数据表格，演示可变尺寸在数据可视化场景中的应用。

- 数据：内置 **80+ 种编程语言 × 20 个维度** 的对比数据集（语言名称、发布年份、范式、类型系统、内存管理等）
- 尺寸：`MeasurableDimenProvider` 自动测量每列内容最大宽度；拖拽 `DragResizerView` 可交互调整列宽/行高
- 场景：伪无限滚动 + 动态尺寸 + 稀疏数据

![实机截图](screenshots/table.jpg)

### 自动瓦片

基于 **TileLayout** 的自动图块拼接系统，类似游戏引擎中的瓦片地图编辑器。

- 数据：从 `dirt_tileset.png` 切割的 47 种瓦片（最近邻放大，保持像素风）
- 算法：`SimpleConnectionRule` 四方向邻居检测，合成 4-bit mask 映射 16 种连接形态
- 交互：拖动绘制、瓦片破碎粒子动画、弹跳进入动画

![实机截图](screenshots/autotile.jpg)

### 迷宫生成

递归回溯（深度优先搜索）算法实时生成迷宫，展示 TileLayout 的动态更新能力。

- 算法：51x51 网格，步长 2 扩展、步长 1 打通墙，随机方向递归回溯
- 数据：与自动瓦片相同的瓦片集和连接规则
- 场景：逐步可视化生成过程（前进/回溯变速）、摄像机平滑跟随、`updateRange` 局部刷新

![实机截图](screenshots/maze.jpg)

### 无限迷宫

分块（Chunk）驱动的无限迷宫系统，展示 Tile2D 在海量坐标空间下的承载能力。

- 算法：递归分割法生成墙壁；`SplitMix64` 混合种子与区块坐标，保证同一区块可复现
- 数据：32x32 区块，4 线程异步生成，区块池回收复用
- 场景：随视窗滚动动态加载/回收区块，支持约 1.34 亿个区块

![实机截图](screenshots/max_maze.jpg)

### 扫雷

基于 **TileView（Canvas 绘制）** 的完整扫雷游戏。

- 算法：`SplitMix64` 哈希确定性布雷（密度约 15.6%）、BFS 展开空白区、15 种状态位图预渲染
- 数据：首次点击 3x3 安全区；默认边界 61x61，伪无限模式完整 int32
- 场景：标记/翻开、存档读档、AI 自动求解、摄像机跟随

![实机截图](screenshots/minesweeper.jpg)

### 五子棋

基于 **TileView（Canvas 绘制）** 的完整五子棋游戏。

- 棋盘：有限模式 201×201（九星位），伪无限模式完整 int32，两种模式均判定五连胜负
- AI：经典棋型评分 + α-β 剪枝，会主动堵截威胁；思考耗时恒定，不随棋局变长而变慢；应对带随机性，同一路线不会走出固定的对局
- 场景：双人同屏、人机对战；「AI 自动下棋」开启后双方全部由 AI 行棋；摄像机平滑跟随最新落子；支持一键复制对局日志

![实机截图](screenshots/gomoku.jpg)

### 性能测试

直接操作 `LayoutEngine` 核心层的纯算法基准测试（**无任何 Android 视图开销**），验证布局引擎本身的性能上限。

- 同步（滚动）测试：随机位移向量循环执行 `engine.sync()`
- 定位（跳转）测试：全 int32 随机坐标循环执行 `engine.seek()`
- 极端边界跳转：随机跳转 8 个 int32 边界方向，报告耗时与 `in()/out()` 调用次数
- 指标：平均值、最小值、最大值、中位数、P95、P99、总耗时与吞吐量（ops/s）

1. 同步（滚动）测试 — 多次随机偏移后统计耗时分布，展示平均值、P95、P99 和吞吐量
![实机截图](screenshots/bench_sync.jpg)

2. 定位（跳转）测试 — 全 int32 空间随机 seek，测试远距离跳转性能
![实机截图](screenshots/bench_seek.jpg)

3. 极端边界跳转 — 从当前位置单次跳转到 int32 边界，记录耗时与 in/out 调用次数
![实机截图](screenshots/bench_end.jpg)

Debug 模式下的数据说明：
- **实际帧率**：真实的物理帧率（`Choreographer.FrameCallback` 每秒统计一次，单位 `Hz`）。
- **理论帧率**：根据绘制耗时（`Debug.threadCpuTimeNanos`）推算的最高可持续帧率，远高于屏幕刷新率表示渲染有余量。
- **同步耗时**：`LayoutEngine.sync` 视窗同步计算耗时（来自 `LayoutModel.syncTime`），单位**纳秒**(1ms = 1,000,000ns)。
- **业务耗时**：`onBindTileHolder` 瓦片绑定耗时（通过 `Callback.getBindTime()` 采集），单位**纳秒**。
- **布局耗时**：视窗瓦片布局和濒死区处理耗时（通过 `Callback.getLayoutTime()` 采集），单位**纳秒**。
- **活跃瓦片**：当前在视窗内可见的瓦片数。
- **回收瓦片**：在回收池中缓存等待复用的瓦片数。
- **濒死瓦片**：刚离开视窗、仍在濒死区缓冲的瓦片数。
- **布局范围**：当前可见区域的行列索引闭区间，截图中的极大值表示已处于 **int32** 的边界。
- **当前位置**：当前可见区域的像素级偏移（`offsetX`， `offsetY`），你会发现它总是很小。
- **内容尺寸**：从起始列到结束列的总像素宽度/高度（`totalWidth / totalHeight`）。

### 视窗范式

不接入真实引擎的**纯交互演示**，用两段可拖拽的色带直观对比传统容器与 Tile2D 在视窗定位上的核心差异。

- 传统范式：绝对坐标定位，视窗如**放大镜在尺子上滚动**，可动方是视窗；坐标范围 `[0, 内容总尺寸]`
- Tile2D 范式：逻辑坐标 + 像素偏移，视窗如**摄像机固定在原地**，可动方是内容；偏移范围 `[-瓦片尺寸, 0]`，与 `LayoutEngine` 的 `offsetX` 语义一致
- 场景：拖拽 + 惯性滚动，实时显示视窗偏移，直观理解两种定位范式的换算关系

![实机截图](screenshots/window_paradigm.jpg)

---

只有世界不会崩溃，我们才能在某处相遇。