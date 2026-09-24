# Tile2D

眼中方寸之间，藏有无限可能。
不知身在何处，依旧脚踏实地。

---

[![](https://jitpack.io/v/kkaHeng/tile2d.svg)](https://jitpack.io/#kkaHeng/tile2d)

汉语 | [English](README.en.md)

## 简介

Tile2D 是一个支持**全 int32**索引空间的 Android 二维虚拟容器。

关键词：**瓦片**、**网格**、**表格**、**二维**。

## 特性

### 视窗裁剪

视窗外的瓦片**不加载**、**不渲染**，使用`RecyclerView`风格的**适配器模式**降低学习成本，因为`RecyclerView`是安卓开发绕不开的一座大山。

**虚拟化**渲染是保持高性能的关键，避免性能随数据总量线性下降，这是滚动容器的基本操作。

### 双端渲染

提供`TileLayout`与`TileView`两种**默认**渲染范式，其中`TileView`使用`Canvas`渲染，而`TileLayout`则支持**原生View**。

很少有UI框架会兼容不同的UI生态，要么自研UI底层，要么只支持原生View。支持**选择**与**扩展**渲染/交互范式可以免去**生态迁移成本**。

### 范围支持

适配器的**逻辑索引范围**支持**int32**的完整空间，单轴约**42亿**。

通常很多传统范式不支持**int32**的**最大值**，甚至连**负数**都不支持，这是因为**数组思维**和它的**左闭右开**常识，还存在淡化**逻辑索引/坐标**存在感的情况。

### 濒死预取

有**濒死区**和**预取区**两种缓存策略：**提前加载**即将进入视窗的瓦片，**保存最近**离开视窗的瓦片。

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

在你项目根目录的 `settings.gradle` 文件里，往 `repositories` 末尾添加它：
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
// 从 -10,-10 到 10,10 共 21 列、21 行，总计 441 个单元格
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

### 刷新语义

```java
// 适配器数据变化后直接调用，不需要重新 setAdapter
tileView.update(3, 5); // 单个瓦片
tileView.updateRange(-10, -10, 10, 10); // 矩形区域（闭区间）
tileView.updateColumn(0); // 整列
tileView.updateRow(0); // 整行
tileView.updateAll(); // 全部（等价于在原地重新执行一次 seek）
```

---

## 架构

### 示意图

```
┌────────────────────────────────────┐
│  上层 · 渲染与交互                 │
│  TileView · TileLayout             │
└──────────────────┬─────────────────┘
                  ↕
┌──────────────────┴─────────────────┐
│  中层 · 核心中枢                   │
│  TileCoreService                   │
└──────────────────┬─────────────────┘
                  ↕
┌──────────────────┴─────────────────┐
│  底层 · 引擎内核                   │
│  LayoutEngine · TileManager        │
│  DimenManager · EventHandler       │
└────────────────────────────────────┘
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
- [H5移植指南](docs/H5移植指南.md) — 浏览器端完整移植教程，附带可运行的示例（h5-demo/）。

---

## 联系方式

- 作者：阿恒
- 邮箱：kkaheng163@163.com
- GitHub：[https://github.com/kkaHeng](https://github.com/kkaHeng)

---

## 示例 Demo

App 模块内置 **11 个示例**，覆盖两种渲染范式（Canvas 自绘 / 标准 View）与多种应用场景。

### 通用菜单

- **Debug 模式**  
实时显示**帧率**、**活跃瓦片**、**回收瓦片**、**濒死瓦片**、**视窗状态**等调试信息。

- **伪无限模式**  
数据边界扩展至完整 **int32** 范围，部分 Demo 利用算法与**确定性伪随机数生成器**按需生成数据。

- **去边界看看**  
一键跳转到数据边界的 8 个极端点，结合**伪无限模式**有奇效。

- **随机调整尺寸**  
平滑地调整视窗中心的**列宽**或**行高**，展示引擎动态调整尺寸的效率，在视窗外调整尺寸效率更高。

> 以下 Demo 均由 AI 生成。

### 瓦片画板（TileView）

基于`TileView`实现的噪声纹理演示。使用`PerlinNoise2D`实时生成噪声并映射到颜色值作为瓦片的数据源。展示了引擎的基本能力。

![实机截图](screenshots/tileview.jpg)

### 瓦片布局（TileLayout）

与**瓦片画板 Demo**相同的数据源，但是基于`TileLayout`实现。

![实机截图](screenshots/tilelayout.jpg)

### 数据表

基于`TileLayout`实现的简易**CSV/TSV**表格编辑器，支持自动缓存、复制结果。展示了在**表格场景**下的稳定性。

![实机截图](screenshots/table.jpg)

### 自动瓦片

基于`TileLayout`实现的自动选择瓦片图块演示。采用 **47-Tile Blob** 瓦片连接规则，基于八方向邻居的状态自动选择合适的图块。展示了引擎的**多类型**瓦片渲染能力。

![实机截图](screenshots/autotile.jpg)

### 迷宫生成

基于`TileLayout`实现的**迷宫生成**演示。使用**深度优先搜索**算法，镜头会实时跟随。展示了引擎**频繁高速移动**的稳定性。

![实机截图](screenshots/maze.jpg)

### 无限迷宫

基于`TileLayout`实现的**区块化迷宫**演示。使用**递归分割法**在区块内生成迷宫，并且通过拼接区块的方式实现超大范围迷宫的高效生成。区块可回收复用，避免内存溢出。展示了引擎的**范围更新**与**子线程交互**能力。

![实机截图](screenshots/max_maze.jpg)

### 扫雷

基于`TileView`实现的完整扫雷游戏。使用 **SplitMix64** 确定性伪随机哈希生成函数进行布雷，内置简易 **AI算法**，可自动游玩。展示了引擎对**多类型动态变更**的瓦片的渲染能力。

![实机截图](screenshots/minesweeper.jpg)

### 五子棋

基于`TileView`实现的完整五子棋游戏。内置简易 **AI算法**，支持**人机对决**、**AI自动玩**等功能。展示了引擎的**游戏交互**能力。

![实机截图](screenshots/gomoku.jpg)

### 生命游戏

基于`TileView`实现的康威生命游戏，内置了经典图案。展示了引擎的**大范围状态更新**能力。

![实机截图](screenshots/life.jpg)

### 性能测试

直接操作`LayoutEngine`的纯算法基准测试（**无任何 Android 视图开销**），验证引擎的性能上限。

1. **同步（滚动）测试**  
多次随机偏移后统计耗时分布，展示平均值、P95、P99 和吞吐量。

![实机截图](screenshots/bench_sync.jpg)

2. **定位（跳转）测试**  
全 **int32** 索引空间随机传送，测试远距离跳转性能（距离无关）。

![实机截图](screenshots/bench_seek.jpg)

3. **极端边界跳转**  
从当前位置单次跳转到 **int32** 边界，记录耗时与瓦片进出次数。

![实机截图](screenshots/bench_end.jpg)

### Debug 面板数据

- **实际帧率**：真实的物理帧率，`Choreographer.FrameCallback`每秒统计一次，单位：**Hz**。
- **理论帧率**：根据瓦片绘制耗时（`Debug.threadCpuTimeNanos`）推算的最高可持续帧率，单位：**Hz**。
- **同步耗时**：`LayoutEngine.sync` 视窗同步耗时，单位：**纳秒**。
- **业务耗时**：适配器为瓦片绑定数据的耗时，单位：**纳秒**。
- **布局耗时**：处理濒死区、预取区与瓦片排版的耗时，单位：**纳秒**。
- **活跃瓦片**：当前在视窗内可见的瓦片数。
- **回收瓦片**：在回收池中缓存等待复用的瓦片数（所有类型）。
- **濒死瓦片**：刚离开视窗的瓦片数。
- **预取瓦片**：提前预加载的瓦片数。
- **预取峰值**：预取队列的历史最高排队数量。
- **布局范围**：视窗覆盖的逻辑坐标范围。
- **当前位置**：视窗的像素级偏移（你会发现它总是很小）。
- **内容尺寸**：视窗覆盖的瓦片总尺寸。

### 视窗范式

不接入真实引擎的 **纯交互演示**，用两段可拖拽的色带直观对比 **传统范式** 与 **Tile2D** 在视窗定位上的核心差异。

- **传统范式**：  
**绝对坐标**定位，视窗如**放大镜在尺子上滚动**，可动方是视窗；坐标范围`[0, 内容总尺寸]`。
- **Tile2D 范式**：  
**逻辑坐标 + 像素偏移**定位，视窗如**摄像机固定在原地**，可动方是内容；偏移范围 `[-瓦片尺寸, 0]`。

![实机截图](screenshots/window_paradigm.jpg)

---

唯有世间安稳，相逢便可发芽。
直至世界尽头，愿你依旧年轻。
