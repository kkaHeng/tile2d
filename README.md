# Tile2D

在屏幕的方寸之间,铺展无限可能。

---

[![](https://jitpack.io/v/kkaHeng/tile2d.svg)](https://jitpack.io/#kkaHeng/tile2d)


## 简介

Tile2D 是一个支持**伪无限**索引空间的 Android 二维虚拟容器。

## 特性

### 视窗裁剪

视窗外的瓦片**不加载**、**不渲染**，使用`RecyclerViee`风格的**适配器模式**降低学习成本。内容占用取决于视窗且多数情况下保持稳定。

### 双端渲染

提供`TileLayout`与`TileView`两种渲染方案，其中`TileView`使用`Canvas`渲染，而`TileView`则支持**原生View**。很少有UI框架会兼容不同的UI生态。

### 范围支持

适配器的**逻辑索引范围**支持**int32**的完整空间，单轴约**42亿**左右。通常很多传统项目不支持**int32**的**最大值**，甚至连**负数**都不支持。

### 可变尺寸

支持**低成本**修改指定行、指定列的尺寸，窗口外修改时成本会进一步降低。在修改尺寸时，支持自定义**对齐方向**。

### 精度安全

窗口位于**int32**边界附近或离**0**很远时，像素精度不会退化。除非单个瓦片的尺寸直接撞精度墙，不过绝大多数UI系统都不支持这么大的纹理尺寸。一些传统算法会在滚动到很远的距离时出现精度退化，导致内容**视觉跳动**。

### 容器替换

支持替换底层**数据容器**，追求更高的性能上限。包括**瓦片池**、**尺寸表**、**回收池**等等。

### 稀疏存储

不会尝试申请用不上的内存空间(数组)，全链路支持**稀疏数据**，例如`onCreateTileHolder`可以返回`null`。

---

## 阅读指南

文档各章节相互独立，只想跑通看「快速开始」，日常开发翻「API 文档」，改造框架看「架构」与「扩展阅读」。下面按不同需求与阶段给出导航。

### 第一次接触

- 「快速开始」的依赖接入与两段基础示例，先让一个 `TileView` 或 `TileLayout` 跑起来
- 「简介」+「特性」判断场景是否匹配：伪无限索引、稀疏数据、可变尺寸、双端渲染
- 其余章节暂不需要，「性能报告」留到想验证效果时再看

### 日常集成

- 「TileView 与 TileLayout」：两个渲染方案的 API 差异，选定适合你的一套
- 「TileAdapter」「TileHolder」：边界定义、创建/绑定、四个生命周期钩子，是日常开发的核心
- 「导航」「坐标转换」「更新瓦片」：滚动、跳转、局部刷新，遇到问题回来查
- 「瓦片生命周期」：一张图理清瓦片从创建到复用的流转

### 性能与定制

- 「尺寸管理」+「尺寸提供者」：动态尺寸、内容自适应列宽(`MeasurableDimenProvider`)
- 「数据映射替换」：替换瓦片池/尺寸表/回收池，换取特定场景的性能上限
- 「调试」面板 + 「性能报告」的基准测试：先看懂指标，再谈优化
- 「高级 API」：生命周期监听器与布局监听器，用于埋点、联动与状态观察

### 深度改造

- 「架构」：先建立视图层 → 调度层 → 引擎层 → 接入层的分层认知
- [平台扩展指南](docs/extending-android.md)：`CoreInterface` 与中间层详解，自定义渲染层(Compose/OpenGL)、存储容器、DebugLayer
- [布局引擎跨平台移植指南](docs/cross-platform-engine.md)：`LayoutEngine`/`TileManager`/`DimenManager` 算法与接口，移植到其他平台

### Demo 对照

「性能报告」实为完整示例合集，按场景对号入座：

- 渲染方案对比：瓦片画板(`TileView`) vs 瓦片布局(`TileLayout`)
- 可变尺寸：数据表(`MeasurableDimenProvider` + 拖拽调宽)
- 海量坐标与多线程：伪无限迷宫、扫雷
- 交互与动画：自动瓦片、迷宫生成
- 纯算法基准：性能测试(无任何 Android 视图开销)

## 快速开始

### 添加依赖(Gradle)

在你的根目录 `settings.gradle` 文件的 `repositories` 末尾添加它:
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

引入依赖:
```gradle
dependencies {
    implementation 'com.github.kkaHeng:tile2d:26.7.2'
}
```

查看最新版本: [Jitpack](https://jitpack.io/#kkaHeng/tile2d),或者点击页面顶部的徽章
> 版本号规则: 年份.月份.大版本.小版本

### 基础使用

#### 使用 TileView(自定义绘制)

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

#### 使用 TileLayout(标准 View)

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

---

## 架构

Tile2D 采用分层设计,以 **TileCoreService** 为调度中枢,协调四个子系统,对接两种视图实现。

### 视图层

- **TileView**  
  基于 Canvas 自定义绘制,性能开销小。
- **TileLayout**  
  基于 ViewGroup,瓦片为独立 View。硬件加速下性能更优,交互支持更好。

> 理论上 TileView 性能更高,但我实测发现 TileLayout 开了硬件加速的性能更高,且交互支持好,代价是内存占用较高。

### 调度层

- **TileCoreService**  
  中央调度器,对外提供统一 API,对内编排各子系统协作。

### 引擎层

LayoutEngine、TileManager、DimenManager 不依赖 Android,可跨平台移植。

- **LayoutEngine**  
  视窗同步,计算可见行列与偏移,滚动时 diff 进出瓦片。
- **TileManager**  
  瓦片生命周期管理,维护活跃/濒死/回收三个状态池。
- **DimenManager**  
  尺寸存储与查询,支持重力对齐,按需扰动布局。
- **EventHandler**  
  事件处理器(Android),处理触摸、手势、惯性滚动。

### 接入层

- **TileAdapter**  
  数据源适配器,定义边界、创建与绑定。
- **TileDimenProvider**  
  尺寸提供者接口,自定义行列尺寸策略。
- **BaseTileHolder**  
  瓦片持有者基类,提供 `onInWindow`、`onOutWindow`、`onRecycled`、`onSizeChanged` 四个生命周期钩子。

---

## API 文档

### 尺寸对齐常量

TileView 和 TileLayout 都定义了三个对齐常量,用于 `setTileWidth`、`setTileHeight`、`setTileSize` 等方法:

- `DIMEN_GRAVITY_START`(-1)  
  对齐到起始侧。列宽变化时右侧扩展或收缩,行高变化时下侧扩展或收缩。
- `DIMEN_GRAVITY_CENTER`(0)  
  居中对齐。尺寸变化时两侧均匀扩展或收缩。
- `DIMEN_GRAVITY_END`(1)  
  对齐到结束侧。列宽变化时左侧扩展或收缩,行高变化时上侧扩展或收缩。

无 `gravity` 参数的重载方法(如 `setTileWidth(column, width)`、`deleteTileWidth(column)`、`setTileHeight(row, height)`、`deleteTileHeight(row)`)默认使用 `DIMEN_GRAVITY_START`。

### TileView 与 TileLayout

下列方法 TileView 和 TileLayout 均可用,差异会特别说明。

#### 导航

- `void offset(float dx, float dy)`  
  按像素偏移滚动,即对内容位置应用 (dx, dy) 的像素位移。  
  正数 dx 使内容向右移动(视窗向左扩展),正数 dy 使内容向下移动(视窗向上扩展)。  
  如果视窗已到达边界且无更多内容,偏移会被自动裁剪。  
  单位: **像素**。  
  适配器边界为空时无效果。

- `void seek(int column, int row)`  
  `void seek(int column, int row, float offsetX, float offsetY)`  
  跳转到指定坐标位置。`column` 和 `row` 为目标可视区域的起始格。  
  `offsetX` 和 `offsetY` 为像素级微调,正数使内容向右/下偏移。  
  跳转会立即清空所有活跃瓦片并重新加载当前视窗,惯性滚动被打断。  
  第二个方法在不传入 offset 时等价于 `seek(column, row, 0, 0)`。  
  适配器边界为空时无效果。

- `void snap()`  
  在适配器边界发生变化后调用,使视窗重新回到合法范围内。  
  例如适配器缩窄了左边界而当前视窗在其左侧,调用后视窗会吸附到最近的合法位置。  
  如果视窗已经在合法范围内则无效果。  
  适配器边界为空时无效果。

#### 坐标转换

- `float getTileX(int column)`  
  获取指定列左上角 X 坐标,基于 View 原点(含 padding)。  
  当前不可见的列也能计算。

- `float getTileY(int row)`  
  获取指定行左上角 Y 坐标,基于 View 原点(含 padding)。  
  当前不可见的行也能计算。

- `int findColumn(float x)`  
  根据 View 坐标查找对应的列索引。  
  用于触摸事件的坐标反查。坐标超出边界时返回最近的边界列。

- `int findRow(float y)`  
  根据 View 坐标查找对应的行索引。  
  坐标超出边界时返回最近的边界行。

#### 布局模型

- `LayoutModel getLayoutModel()`  
  返回当前视窗布局模型的快照,不建议长期持有返回的引用。  
  需要独立副本时调用 `model.newInstance()` 浅拷贝一份。  
  `LayoutModel` 包含 `colStart`、`rowStart`、`colEnd`、`rowEnd`(闭区间)、`offsetX`、`offsetY`、`totalWidth`、`totalHeight` 等字段。

#### 触摸与事件

- `void requestDisallowInterceptTouchEvent(boolean disallowIntercept)`  
  控制 TileView/TileLayout 是否将当前触摸序列交给瓦片处理而非滚动。  
  瓦片调用此方法并传入 `true` 后,当前触摸序列不再触发视图层的滚动行为,事件完全交给瓦片。  
  抬手后自动重置为 `false`。

- `boolean isInteractingWithView()`  
  检查用户是否正在与视图交互(如正在滚动、惯性滑动中)。  
  瓦片可以根据此状态决定是否响应点击等操作。

- `void resetAnimator()`  
  停止正在运行的惯性滚动动画。触摸按下时自动调用,通常不需要手动调用。

- `long getLongPressTimeout()`  
  `void setLongPressTimeout(long longPressTimeout)`  
  获取或设置长按触发时长。仅在 TileView 有效(TileLayout 的长按由原生 View 机制处理)。  
  默认 400 毫秒。单位: **毫秒**。

#### 滚动开关

- `boolean isHorizontalScrollEnabled()`  
  `void setHorizontalScrollEnabled(boolean enabled)`  
  获取或设置横向滚动是否开启。默认开启。  
  关闭后该方向上的触摸滚动和惯性滚动无效果。

- `boolean isVerticalScrollEnabled()`  
  `void setVerticalScrollEnabled(boolean enabled)`  
  获取或设置纵向滚动是否开启。默认开启。

#### 尺寸管理

- `int getTileWidth(int column)`  
  `int getTileHeight(int row)`  
  获取指定列/行的像素宽度/高度。  
  如果该列/行未被自定义尺寸覆盖且没有设置 `TileDimenProvider`,返回默认尺寸。  
  单位: **像素**。

- `void setTileWidth(int column, int width)`  
  `void setTileWidth(int column, int width, int gravity)`  
  设置指定列的宽度。width 为 0 时删除自定义尺寸恢复默认。  
  gravity 控制对齐方式,无 gravity 参数时默认 `DIMEN_GRAVITY_START`。  
  修改后视窗内关联瓦片立即重新布局并调整偏移。  
  如果 column 超出适配器边界,抛出 `IndexOutOfBoundsException`。  
  适配器边界为空时无效果。  
  单位: **像素**。

- `void deleteTileWidth(int column)`  
  `void deleteTileWidth(int column, int gravity)`  
  删除指定列的自定义宽度,恢复默认宽度。  
  gravity 控制恢复过程中偏移的补偿方式,无参数时默认 `DIMEN_GRAVITY_START`。

- `void setTileHeight(int row, int height)`  
  `void setTileHeight(int row, int height, int gravity)`  
  设置指定行的高度。行为与 `setTileWidth` 一致,gravity 默认 `DIMEN_GRAVITY_START`。  
  单位: **像素**。

- `void deleteTileHeight(int row)`  
  `void deleteTileHeight(int row, int gravity)`  
  删除指定行的自定义高度,恢复默认高度。  
  gravity 默认 `DIMEN_GRAVITY_START`。

- `void setTileSize(int column, int width, int row, int height)`  
  `void setTileSize(int column, int width, int hGravity, int row, int height, int vGravity)`  
  同时设置指定列宽和行高。等价于分别调用 `setTileWidth` 和 `setTileHeight`,但布局扰动一次性完成。  
  单位: **像素**。

- `int getDefaultTileWidth()`  
  `int getDefaultTileHeight()`  
  `void setDefaultTileWidth(int width)`  
  `void setDefaultTileHeight(int height)`  
  获取或设置默认瓦片尺寸。默认宽度 80dp,默认高度 45dp。  
  当某个列/行没有自定义尺寸且未设置 `TileDimenProvider` 时使用此值。  
  单位: dp 在构造时自动转换为**像素**。传入的值必须大于 0,否则抛出 `IllegalArgumentException`。

- `TileDimenProvider getDimenProvider()`  
  `void setDimenProvider(TileDimenProvider provider)`  
  获取或设置自定义尺寸提供者。设置后优先于默认尺寸,允许动态计算列宽行高。  
  如果同时使用 `setTileWidth` 单独设置了某列的宽度,`setTileWidth` 的设置值优先。  
  传入 `null` 会清除提供者,回退到默认尺寸。

#### 濒死区

- `int getDyingExpand()`  
  `void setDyingExpand(int expand)`  
  获取或设置濒死区扩展范围。濒死区是视窗外围的缓存带,瓦片滚出视窗后先进入濒死区缓冲,短时间内滚回可直接复用,避免频繁创建销毁。  
  扩展范围是每个方向的圈数,默认 1。传入的值必须大于 0,否则抛出 `IllegalArgumentException`。

- `boolean isDyingEnabled()`  
  `void setDyingEnabled(boolean enabled)`  
  获取或设置濒死区开关。默认开启。关闭后瓦片离开视窗立即回收,并立即清空濒死区内已缓存的瓦片,不做缓冲。

#### 更新瓦片

- `void update(int column, int row)`  
  刷新指定位置的瓦片: 解绑旧瓦片、重新绑定。  
  如果该位置在视窗或濒死区内则立即生效,否则无效果。

- `void updateColumn(int column)`  
  刷新指定列的所有可见瓦片。该列在视窗或濒死区外则无效果。

- `void updateRow(int row)`  
  刷新指定行的所有可见瓦片。

- `void updateRange(int left, int top, int right, int bottom)`  
  刷新指定矩形范围内的所有瓦片。闭区间。  
  只对与视窗或濒死区有交集的部分生效。

- `void updateAll()`  
  重新加载视窗内所有瓦片。相当于重新 seek 到当前位置。

#### 边界状态

- `boolean isEmpty()`  
  检查适配器边界是否为空(bound 违反值,如 left > right)。  
  为空时视图不会渲染任何内容,滚动和 seek 都无效果。

- `boolean isAtLeftBound()`  
  `boolean isAtTopBound()`  
  `boolean isAtRightBound()`  
  `boolean isAtBottomBound()`  
  检查视窗是否已触及对应方向的边界。  
  左/上边界要求起始格在边界且偏移为 0。  
  右/下边界要求结束格在边界且内容恰好填满视窗。

#### 适配器

- `Adapter getAdapter()`  
  `void setAdapter(Adapter adapter)`  
  获取或设置适配器。切换适配器时自动清空所有瓦片状态、回收池和动画。  
  设置适配器后会触发一次从起始位置的 seek。

#### 调试

- `boolean isDebugMode()`  
  `void setDebugMode(boolean enabled)`  
  获取或设置调试面板开关。开启后显示 FPS、同步耗时、布局耗时、活跃/回收/濒死瓦片数、布局范围等信息。  
  调试面板仅在视图附加到窗口时工作。  
  在 TileLayout 上启用时会自动关闭 `clipToPadding`。

#### 瓦片查询

- `TileHolder getActiveTile(int column, int row)`  
  获取当前活跃(可见)的瓦片。不可见时返回 `null`。  
  不要长期持有返回的引用,瓦片可能随时被回收。

#### 数据映射替换

- `void setActiveTiles(LongMap<TileHolder> map)`  
  `void setDyingTiles(LongMap<TileHolder> map)`  
  `void setWidths(IntIntMap map)`  
  `void setHeights(IntIntMap map)`  
  `void setRecycledTiles(TileRecycledPool<TileHolder> pool)`  
  替换内部存储映射,用于自定义底层数据结构。  
  例如替换为 `LongMapHashMap` 或 `IntMapHashMap` 以提升特定场景下的性能。  
  传入 `null` 抛出 `IllegalArgumentException`。重复设置相同对象无效果。  
  调用后旧数据自动迁移到新映射。

### TileAdapter

`TileAdapter<T>` 是数据源适配器的抽象基类。TileView 和 TileLayout 各自继承了它:

- `TileView.Adapter extends TileAdapter<TileView.TileHolder>` — 用于 TileView
- `TileLayout.Adapter extends TileAdapter<TileLayout.TileHolder>` — 用于 TileLayout

以下方法对两个子类均适用:

- `int getLeftBound()`  
  `int getTopBound()`  
  `int getRightBound()`  
  `int getBottomBound()`  
  定义数据范围的边界,闭区间。默认左/上为 `Integer.MIN_VALUE`,右/下为 `Integer.MAX_VALUE`。  
  支持负索引和完整的 int32 范围。当 left > right 或 top > bottom 时视为空范围。

- `T onCreateTileHolder(int type)`  
  根据瓦片类型创建对应的 TileHolder 实例。  
  `type` 来自 `getTileType` 的返回值,默认为 0。  
  允许返回 `null`,对应位置将留空不渲染,适用于稀疏数据场景。  
  TileView 的 Adapter 在此创建 `TileView.TileHolder`,TileLayout 的 Adapter 在此创建 `TileLayout.TileHolder(itemView)`。

- `void onBindTileHolder(T holder, int column, int row)`  
  绑定数据到瓦片。`holder` 是之前 `onCreateTileHolder` 创建的实例,回收复用时直接复用。  
  `column` 和 `row` 是当前瓦片的坐标。  
  此方法在瓦片进入视窗或调用更新方法时被调用。

- `int getTileType(int column, int row)`  
  返回指定坐标的瓦片类型,用于区分多种瓦片样式。  
  默认返回 0。返回值传给 `onCreateTileHolder`。

- `boolean isEmpty()`  
  检查边界是否为空。默认实现: `getLeftBound() > getRightBound() || getTopBound() > getBottomBound()`。  
  返回 `true` 时视图不会渲染任何内容。

### TileHolder

TileHolder 的继承体系:

```
TileCoreService.BaseTileHolder
  ├── TileView.TileHolder — 用于 Canvas 自定义绘制
  └── TileLayout.TileHolder — 用于 ViewGroup 原生布局
```

#### BaseTileHolder

所有 TileHolder 的基类,提供以下生命周期钩子,子类可重写:

- `void onRecycled()`  
  瓦片被回收时调用。适合释放大对象、取消订阅等清理操作。

- `void onInWindow()`  
  瓦片进入视窗时调用。适合启动动画、开始数据监听等。

- `void onOutWindow()`  
  瓦片离开视窗时调用。适合暂停动画、移除监听等。

- `void onSizeChanged(int width, int height)`  
  瓦片尺寸变化时调用。`width` 和 `height` 为新的像素尺寸。

基类提供以下字段访问器:

- `int getColumn()` — 当前瓦片所在列
- `int getRow()` — 当前瓦片所在行
- `int getWidth()` — 当前瓦片像素宽度
- `int getHeight()` — 当前瓦片像素高度
- `int getType()` — 当前瓦片类型

#### TileView.TileHolder

专用于 TileView(Canvas 绘制),在基类基础上额外提供:

- `void draw(Canvas canvas)`  
  在该瓦片上绘制内容。canvas 已经过坐标变换,原点为该瓦片左上角。  
  绘制的内容将被限制在该瓦片区域内。

- `boolean onTouchEvent(MotionEvent event)`  
  处理该瓦片上的触摸事件。event 坐标已转换到瓦片局部空间。  
  返回 `true` 表示消费事件。

- `boolean onClick()`  
  点击事件,在 `ACTION_UP` 且本次触摸未发生滑动时触发。  
  返回 `true` 表示消费点击。

- `void onLongClick()`  
  长按事件,触摸按下约 `longPressTimeout` 毫秒后且未发生滑动时触发。

- `void postInvalidate()`  
  `void postInvalidateOnAnimation()`  
  请求该瓦片在下一帧重绘。

- `void requestDisallowInterceptTouchEvent(boolean disallowIntercept)`  
  阻止 TileView 拦截当前触摸序列,使后续事件直接交给瓦片。

#### TileLayout.TileHolder

专用于 TileLayout(ViewGroup 布局),包含一个 `itemView` 字段即实际的 View 实例:

- `public final View itemView`  
  瓦片对应的 View 对象。在 `onCreateTileHolder` 中构造时传入,后续对 `itemView` 的操作与普通 View 一致(如设置文本、点击监听器等)。

### 高级 API

#### 瓦片事件监听器

`TileEventListener<T>` 是统一的监听接口,用于观察每次布局周期的开始/结束,以及瓦片在视窗内的进出与回收事件。通过 `setTileEventListener` 设置,TileView 和 TileLayout 均支持。

```java
tileView.setTileEventListener(new TileEventListener<TileView.TileHolder>() {
    @Override
    public void onBeforeLayout() {
        // 布局即将开始,瓦片尚未更新
    }

    @Override
    public void onAfterLayout() {
        // 布局已完成,瓦片已更新与渲染
    }

    @Override
    public void onTileIn(TileView.TileHolder holder, int column, int row) {
        // 瓦片进入视窗
    }

    @Override
    public void onTileOut(TileView.TileHolder holder, int column, int row) {
        // 瓦片离开视窗
    }

    @Override
    public void onTileRecycled(TileView.TileHolder holder, int column, int row) {
        // 瓦片被回收
    }
});
```

方法说明:

- `void onBeforeLayout()`  
  每次布局周期开始前调用,此时瓦片尚未更新。

- `void onAfterLayout()`  
  布局周期结束后调用,瓦片已完成更新和渲染。

- `void onTileIn(T holder, int column, int row)`  
  瓦片进入视窗并完成布局后调用。适合设置可见状态、启动动画等。

- `void onTileOut(T holder, int column, int row)`  
  瓦片离开视窗后调用。瓦片离开后进入濒死区,短暂缓冲后可能被回收。适合暂停动画等。

- `void onTileRecycled(T holder, int column, int row)`  
  瓦片被回收到池中后调用。瓦片已离开濒死区,即将被复用或释放。适合释放大对象、取消订阅等。

瓦片进出/回收三个回调的调用时机可结合 BaseTileHolder 的 `onInWindow`、`onOutWindow`、`onRecycled` 理解,区别在于监听器由视图层提供,而 BaseTileHolder 的方法由瓦片自身实现。

#### 尺寸提供者

`TileDimenProvider` 是一个接口,允许在默认尺寸和 `setTileWidth`/`setTileHeight` 之外提供第三种尺寸来源。通过 `setDimenProvider` 设置,TileView 和 TileLayout 均支持。

```java
tileView.setDimenProvider(new TileDimenProvider() {
    @Override
    public int getTileWidth(int column) {
        // 根据列号动态返回宽度
        return column == 0 ? 120 : 80;
    }

    @Override
    public int getTileHeight(int row) {
        return 45;
    }
});
```

尺寸优先级(从高到低):

1. `setTileWidth(column, width)` 和 `setTileHeight(row, height)` 单独设置的值
2. `TileDimenProvider` 动态提供的值
3. `setDefaultTileWidth`/`setDefaultTileHeight` 设置的默认值

`TileDimenProvider` 接口完整方法:

- `int getTileWidth(int column)`  
  获取指定列的宽度。单位: **像素**。

- `int getTileHeight(int row)`  
  获取指定行的高度。单位: **像素**。

- `void setTileWidth(int column, int width)`  
  `void setTileHeight(int row, int height)`  
  `void deleteTileWidth(int column)`  
  `void deleteTileHeight(int row)`  
  用于支持在 Provider 内部管理尺寸映射。通常不需要实现,由 `MeasurableDimenProvider` 使用。

##### MeasurableDimenProvider

`MeasurableDimenProvider` 是 `TileDimenProvider` 的一个实现,可根据瓦片内容自动测量并记录最大尺寸。适合表格、列表等需要根据内容自适应列宽行高的场景。

```java
MeasurableDimenProvider provider = new MeasurableDimenProvider(tileAdapter);
provider.setDefaultTileWidth(80);
provider.setDefaultTileHeight(45);
provider.measure(0, 0, 100, 100); // 测量 0-100 范围
tileView.setDimenProvider(provider);
```

方法说明:

- `void setDefaultTileWidth(int width)`  
  `void setDefaultTileHeight(int height)`  
  设置默认尺寸。当内容测量结果小于默认尺寸时,默认尺寸作为保底值(取决于 `setMinDefault` 设置)。

- `void setMinDefault(boolean minDefault)`  
  是否使用默认尺寸保底。开启后测量到的宽高小于默认尺寸时使用默认尺寸。

- `void full()`  
  测量适配器边界内的全部瓦片。不建议在大数据量时使用,应优先使用 `measure` 指定范围。

- `void measure(int colStart, int rowStart, int colEnd, int rowEnd)`  
  测量指定范围内的所有瓦片,按列/行记录最大宽高。测量时瓦片会经历创建→绑定→测量→回收的完整流程。

- `void reset()`  
  清空所有已测量的尺寸结果。

- `void clearRecycledTiles()`  
  清空内部回收池,释放临时瓦片实例。

##### Measurable

`Measurable` 接口由 TileView.TileHolder 实现(可选),用于在测量期间获取瓦片的期望尺寸:

```java
public interface Measurable {
    void measure(int widthMeasureSpec, int heightMeasureSpec, int[] out);
}
```

- `void measure(int widthMeasureSpec, int heightMeasureSpec, int[] out)`  
  测量瓦片并将测量结果的宽高写入 `out` 数组(`out[0]` 宽度,`out[1]` 高度)。  
  `widthMeasureSpec` 和 `heightMeasureSpec` 为 Android 标准的 MeasureSpec,通常使用 `UNSPECIFIED` 让瓦片自行决定期望尺寸。

## 瓦片生命周期

```
创建 → 绑定 → 进入窗口 → 离开窗口 → 濒死 → 回收 → 复用
```

## 许可证

本项目采用 [MIT License](LICENSE) 开源协议。

---

## 扩展阅读

- [平台扩展指南](docs/extending-android.md) — 中间层详解、自定义渲染层(Compose/OpenGL/自定义 View)、自定义存储容器、DebugLayer 集成
- [布局引擎跨平台移植指南](docs/cross-platform-engine.md) — LayoutEngine/TileManager/DimenManager 算法详解、接口定义、移植步骤与测试策略

---

## 联系方式

- 作者: 阿恒
- 邮箱: kkaheng163@163.com
- GitHub: [https://github.com/kkaHeng](https://github.com/kkaHeng)

---

## 性能报告

正在贪婪的使用 `int32` 的全部索引空间中:

### 瓦片画板(TileView)

基于 **TileView(Canvas 自定义绘制)** 的 Perlin 噪声渲染演示。

- 使用 `PerlinNoise2D` 在瓦片坐标上采样,通过 `ColorGenerator` 将归一化噪声值(`0~1`)映射为 24 色渐变色板(亮蓝→青→绿→黄→橙→红→暖白→纯白)
- 内置 **Picture 缓存**: 瓦片进入视窗后,将边框、颜色块、文本一次性录制到 `Picture` 对象中,后续帧直接回放(`canvas.drawPicture`),避免重复绘制开销。尺寸或数据变化时(`needReplay`)重新录制
- 支持两种展示方案: **彩色色块**(40dp 方块,显示噪声纹理)和**文本表格**(80x45dp,显示噪声数值)。可通过菜单切换,切换时 `setAdapter` 完整重置并保持视窗位置
- 每隔 `0.3%` 的低噪区用 `getTileType` 返回 `-1` 使 `onCreateTileHolder` 返回 `null`,产生稀疏空瓦片,展示 null 安全特性
- 长按瓦片可将其移除(写入 `removedTiles` 集合并通过 `update` 局部刷新生效)
- 菜单支持**随机宽度/高度动画**: 定位视窗中央的列/行,用 `ValueAnimator` 配合 `OvershootInterpolator` 在 `2` 秒内以 `DIMEN_GRAVITY_CENTER` 方式平滑变化尺寸
- 数据边界默认 `-50~50`(横向 101 格)和 `-100~100`(纵向 201 格),启用极限模式后完整覆盖 int32

![实机截图](screenshots/tileview.jpg)

### 瓦片布局(TileLayout)

与画板使用相同 `PerlinNoise2D` + `ColorGenerator` 数据源,但基于 **TileLayout(ViewGroup)** 实现。

- 每个瓦片是独立的 `TextView`,通过 `GradientDrawable` 设置背景色和边框
- 原生支持点击/长按交互: 单击弹出坐标 Toast,长按移除瓦片并调用 `requestDisallowInterceptTouchEvent(true)` 阻止滚动
- 同样支持彩色色块/文本数值两种方案切换,以及随机宽度/高度动画
- 在硬件加速下,TileLayout 通常比 TileView 性能更高,因为 View 的渲染由 GPU 硬件加速管线接管
- 与画板形成对比,展示同一引擎下两种视图实现的异同

![实机截图](screenshots/tilelayout.jpg)

### 数据表

基于 **TileLayout** 构建的大规模结构化数据表格,演示可变列宽在实际场景中的应用。

- 使用 `MeasurableDimenProvider` 自动测量每列的内容最大宽度,确保数据完整展示
- 拖拽表格边角的 `DragResizerView` 可交互式调整列宽/行高
- 展示 Tile2D 在数据可视化场景下的能力: 伪无限滚动 + 动态尺寸 + 稀疏数据

![实机截图](screenshots/table.jpg)

### 自动瓦片

基于 **TileLayout** 的自动图块拼接系统,类似游戏引擎中的瓦片地图编辑器。

- 从 `assets/dirt_tileset.png`(176x80px) 加载瓦片集,16x16 原始网格通过最近邻算法放大到目标尺寸,保持像素风锐利边缘
- `SimpleConnectionRule` 实现 4 方向邻居检测: 检测上/右/下/左四个方向的已放置瓦片,合成 4-bit mask,映射为 16 种连接形态(从全孤立到全邻居)
- `TileSet` 管理器自动跳过空单元格,按行优先分配 type ID(`1~47`)
- **拖动绘制**: 通过覆写外层 `FrameLayout.dispatchTouchEvent`,在拖动模式下跨 TileLayout 的触摸拦截直接处理触摸轨迹。手指滑过的每个格子自动放置瓦片,实现"画"的效果
- **瓦片破碎动画**: 移除瓦片时从活跃瓦片获取 `Bitmap`,均匀切割为 4x4=16 碎片,每片赋予随机水平速度和向上初速度,在 `ValueAnimator` 驱动下模拟抛物线运动(含重力加速度),同时透明度逐渐衰减,动画结束后回收 Bitmap 并移除 View
- **弹跳进入动画**: 新放置的瓦片在 `onBindTileHolder` 中检测 `pendingEnterAnimations`,触发 `scale(0→1)` + `alpha(0→1)` 的 `OvershootInterpolator` 动画
- 预置 4 格初始方块,seek 到 `(-4, -8)` 起始位置

![实机截图](screenshots/autotile.jpg)

### 迷宫生成

递归回溯(深度优先搜索)算法实时生成迷宫,摄像机平滑跟随,展示 TileLayout 的动态更新能力。

- 使用 `TileSet` + `SimpleConnectionRule` 与自动瓦片 Demo 相同的瓦片集和连接规则
- 迷宫网格 51x51(奇数维度,确保路径宽度为 1),从 `(0,0)` 开始以步长 `2` 逐步扩展
- 每一步随机打乱四个方向(上/下/左/右),发现未访问格时打通中间墙(步长 `1` 的瓦片),将与当前格和新区格一起放置
- **逐步可视化**: 快速前进(20ms/步)时瓦片依次出现,回溯(5ms/步)时摄像机跟随,可直观观察算法执行过程
- **摄像机平滑跟随**: 通过 `Choreographer.FrameCallback` 每帧计算目标格与视窗中心的像素偏差,应用 `CAMERA_LERP = 0.05` 的指数平滑系数实现跟随效果。跟随期间不影响用户手动滚动(滚动时停止跟随)
- 使用 `updateRange` 按需刷新局部区域(当前位置向外扩展一圈),避免全量更新
- **弹跳进入动画**: 每块新瓦片进入时应用 `OvershootInterpolator(1.8)` 的 scale+alpha 动画
- 进度条显示当前已访问的路径格数占总格数(51x51=2601)的比例
- 支持菜单切换"停止生成/重新开始"

![实机截图](screenshots/maze.jpg)

### 伪无限迷宫

分块(Chunk)驱动的无限迷宫系统,展示 Tile2D 在海量坐标空间下的承载能力。

- 每块(Chunk)大小为 32x32,区块坐标通过 `col >> CHUNK_SHIFT` 从瓦片坐标映射而来
- `activeChunks` 使用 `LongSparseArray` 管理当前可见区块(理论最多 `3x3=9` 块),超出范围的区块立即回收
- **区块池回收**: `chunkPool` 复用 `Chunk` 对象,减少 GC。回收时 `cancel` 仍在执行中的 `Future`
- **多线程异步生成**: 4 线程 `ExecutorService` 执行 `recursiveDivision`(递归分割法)生成迷宫墙壁,生成完毕后 `mainHandler.post` 通知主线程通过 `updateRange` 刷新对应区域
- 递归分割算法: 在区块内用偶数坐标砌墙,奇数坐标开洞,`50%` 概率额外多开一个洞,递归分割至子区域小于 `3x3`
- **区块种子一致性**: 配合全局 seed 和 `SplitMix64` 混合器,确保同一区块坐标始终生成相同迷宫(可复现)
- `Chunk.MIN_CHUNK` = `Integer.MIN_VALUE >> 5`,`MAX_CHUNK` = `Integer.MAX_VALUE >> 5`,理论上支持约 `1.34` 亿个区块
- 右下角实时显示可见区块数、回收池大小、当前区块坐标和种子
- 正在生成的区块内的瓦片 `getTileType` 返回 `TYPE_LOADING`(对应 `onCreateTileHolder` 返回 `null` 留空),生成完成后自动刷新填充
- 墙壁使用 `bricks.png` 纹理瓦片,空白区域不创建 View

![实机截图](screenshots/max_maze.jpg)

### 扫雷

基于 **TileView(Canvas 绘制)** 的完整扫雷游戏实现,支持标记、存档、AI 自动求解与摄像机跟随。

- **雷的确定性哈希分布**: 使用 `worldSeed` + `SplitMix64` 混合列行 ID 后取低 `8` 位与密度阈值(`40/256≈15.6%`)比较判定是否为雷。**首次点击 `3x3` 安全区**确保开局的公平性
- **15 种瓦片状态预渲染**: 从普通→翻开空白/数字(`1~8`)→旗标→雷→踩爆→正确旗→错误旗,全部在 `generateTileBitmaps` 中通过 Canvas 绘制为 `Bitmap` 并缓存。TileHolder 的 `draw` 方法直接 `drawBitmap`,零计算开销
- **BFS 展开**: 翻开空白格时通过 BFS 队列递归展开所有相邻空白格和数字边界格,`updateRange` 批量刷新
- **存档/读档**: 通过 `DataOutputStream` 将 `worldSeed`、`safeZone`、`revealed`、`flagged`、`exploded`、`gameWon` 等状态持久化到 `minesweeper.dat`。Activity `onPause` 自动存档,启动时自动读档
- **AI 自动求解**: `MinesweeperSolver` 基于已翻开数字推演必然安全/必然雷的位置,执行标旗/取消标旗/翻开动作。遇到无法推理的局面时遍历全盘找最近未翻开格直接试探(非伪无限模式),或向随机方向跳跃继续探索(伪无限模式)。AI 探索失败超过 `8` 次后投降
- **摄像机跟随**: AI 每执行一步,`ValueAnimator` 驱动视窗平滑移动到目标格。跟随期间用户触摸滚动会取消跟随
- **胜利判定**: 非伪无限模式下,全盘无未翻开的非雷格即为胜利。胜利时自动标旗所有未标旗的雷(`autoFlagAllMines`),但**不暴露雷的具体位置**(仅显示旗标)
- 默认边界 `-30~30`(61x61=3721 格),极限模式完整 int32
- 使用 Material 3 持久对话框展示胜利/失败/AI投降信息

![实机截图](screenshots/minesweeper.jpg)

### 性能测试

纯算法基准测试,直接操作 `LayoutEngine` 核心层(**无任何 Android 视图开销**)。

- **同步(滚动)测试**: 使用 `randomStepPx`(0/8/16/32 候选值) 乘以随机方向(`-1`/`0`/`1`)生成随机位移向量,循环执行 `engine.sync()`。测试次数可配置(默认 1000,上限 100000)
- **定位(跳转)测试**: 生成全 int32 随机的列/行坐标,循环执行 `engine.seek(col, row, 0, 0)`,模拟远距离跳转性能
- **极端边界跳转**: 随机选择 8 个极端方向(左上角/上边/右上角/右边/右下角/下边/左下角/左边),执行单次 seek 并报告耗时和 `in()/out()` 调用次数
- **统计指标**: 平均值、最小值、最大值、中位数、P95、P99(排序后剔除最大最小值再平均),总耗时和吞吐量(ops/s)。结果可一键复制为纯文本

1. 同步(滚动)测试 — 多次随机偏移后统计耗时分布,展示平均值、P95、P99 和吞吐量
![实机截图](screenshots/bench_sync.jpg)

2. 定位(跳转)测试 — 全 int32 空间随机 seek,测试远距离跳转性能
![实机截图](screenshots/bench_seek.jpg)

3. 极端边界跳转 — 从当前位置单次跳转到 int32 边界,记录耗时与 in/out 调用次数
![实机截图](screenshots/bench_end.jpg)

截图中的数据说明:
- **实际帧率**: 真实的物理帧率(`Choreographer.FrameCallback` 每秒统计一次,单位 `Hz`)。
- **理论帧率**: 根据绘制耗时(`Debug.threadCpuTimeNanos`)推算的最高可持续帧率,远高于屏幕刷新率表示渲染有余量。
- **同步耗时**: `LayoutEngine.sync` 视窗同步计算耗时(来自 `LayoutModel.syncTime`),单位**纳秒**(1ms = 1,000,000ns)。
- **业务耗时**: `onBindTileHolder` 瓦片绑定耗时(通过 `Callback.getBindTime()` 采集),单位**纳秒**。
- **布局耗时**: 视窗瓦片布局和濒死区处理耗时(通过 `Callback.getLayoutTime()` 采集),单位**纳秒**。
- **活跃瓦片**: 当前在视窗内可见的瓦片数。
- **回收瓦片**: 在回收池中缓存等待复用的瓦片数。
- **濒死瓦片**: 刚离开视窗、仍在濒死区缓冲的瓦片数。
- **布局范围**: 当前可见区域的行列索引闭区间,截图中的极大值表示已处于 **int32** 的边界。
- **当前位置**: 当前可见区域的像素级偏移(`offsetX, offsetY`),你会发现它总是很小。
- **内容尺寸**: 从起始列到结束列的总像素宽度/高度(`totalWidth / totalHeight`)。
