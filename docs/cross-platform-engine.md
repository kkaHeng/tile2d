# 布局引擎跨平台移植指南

## 前提

`LayoutEngine`、`TileManager`、`DimenManager` 三个类不依赖 Android SDK,只用了标准 Java 语法。这意味着它们已经可以直接移植到任何支持 Java 的平台。对于非 JVM 平台,需要用目标语言重新实现相同的算法和接口。

## 为什么不建议 C++/JNI

C++/JNI 方案的典型路径是:用 C++ 写一份核心逻辑,各平台通过 JNI/PInvoke/FFI 调用。这个方案在低频调用场景没问题,但 Tile2D 的 `sync` 方法在惯性滚动期间每帧可能被调用数十次,每次调用涉及:

- dx/dy 两个 float 参数的传递
- `getColWidth`/`getRowHeight` 的回调(视窗内有多少瓦片就调多少次)
- `in`/`out` 的回调(进出视窗的瓦片数量)
- `LayoutModel` 结构体的读写

这些高频的小数据交互在 JNI 边界上累积的开销会抹平 C++ 本身的性能优势。正确的做法是:**在目标平台语言中用纯算法实现一份**。

---

## 可移植的模块

### LayoutEngine(布局引擎)

核心职责:维护一个虚拟视窗,在滚动位移发生时重新计算可见范围,并通知哪些瓦片进入或离开视窗。

整个类只依赖两个接口和两个纯数据对象,没有任何平台代码。

#### 状态模型

`LayoutModel` 是视窗的完整状态描述:

```
colStart, rowStart  — 可见区域起始格(闭区间)
colEnd, rowEnd      — 可见区域结束格(闭区间)
offsetX, offsetY    — 像素级偏移(始终为负数或0,范围 [-tileWidth, 0])
totalWidth, totalHeight — 从 colStart/rowStart 到 colEnd/rowEnd 的总像素尺寸
```

初始状态:`colStart=0, rowStart=0, colEnd=-1, rowEnd=-1, offsetX=0, offsetY=0, totalWidth=0, totalHeight=0`。

#### sync 算法详解

`sync(dx, dy)` 是核心方法,按像素偏移滚动。**dx 和 dy 是视觉位移**:正数 dx 使内容向右移动(即视窗向左扩展),负数 dx 使内容向左移动(即视窗向右扩展)。

```
输入: dx(float), dy(float)
输出: 视窗是否发生变化

1. 保存旧视窗范围(lastColStart, lastRowStart, lastColEnd, lastRowEnd)
2. 检查当前视窗是否合法(起始格不越界)
3. 计算新偏移: offsetX += dx, offsetY += dy
4. 水平同步:
   a. 如果 offsetX <= 0 且内容未填满视窗且已到右边界:
      尝试右对齐(将 offsetX 向右推)
   b. 如果 offsetX > 0 且 colStart > leftBound:
      向左扩展(将左侧新的列加入范围)
   c. 如果 offsetX < -当前列宽 且 colStart < rightBound:
      向右收缩(将左侧列移出范围)
   d. 如果已到左边界且 offsetX > 0: 钳位为 0
   e. 如果内容未填满视窗且 colEnd < rightBound:
      向右扩展(将右侧新的列加入范围)
   f. 如果内容超出视窗太多且 colEnd > colStart:
      向左收缩(将右侧列移出范围)
5. 垂直同步: 同上逻辑
6. 写入 output 快照
7. 如果视窗范围发生变化:
    a. 更新 original 状态
    b. 调用 diff() 计算进出瓦片
```

**关键约束**:`offsetX` 始终维持在 `[-当前列宽, 0]` 范围内,`offsetY` 始终维持在 `[-当前行高, 0]` 范围内。

#### seek 算法详解

`seek(column, row, offsetX, offsetY)` 跳转到指定坐标:

```
1. 检查边界是否为空,检查目标是否在合法范围内
2. 从 (column, row) 开始向右下遍历瓦片:
   a. 逐行累加行高,直到填满视窗高度或到达底部边界
   b. 逐列累加列宽,直到填满视窗宽度或到达右边界
   c. 遍历过程中调用 in() 通知瓦片进入
3. 设置 original 状态
4. 调用 sync(offsetX, offsetY) 完成微调
```

#### diff 算法详解

`diff(oldStart, oldEnd, newStart, newEnd)` 计算两个矩形范围的差异:

```
如果新范围与旧范围完全不相交:
  旧范围全部 out → 新范围全部 in
否则:
  计算两个范围的并集和交集
  将并集按"上、右、下、左"四个区域分解
  对每个区域中的每个格点:
    判断它在旧范围内还是新范围内
    只在旧 + 不在新 → out
    不在旧 + 在新 → in
```

#### 尺寸变更的 gravity 补偿

当瓦片尺寸变化时,LayoutEngine 通过 `updateWidth`/`updateHeight`/`updateSize` 调整偏移以保持视觉稳定:

```
DIMEN_GRAVITY_START(-1):
  列宽变化时右侧扩展/收缩,offsetX 不变
  行高变化时下侧扩展/收缩,offsetY 不变
DIMEN_GRAVITY_CENTER(0):
  两侧均匀变化,offsetX/Y 补偿 (oldWidth - newWidth) / 2
DIMEN_GRAVITY_END(1):
  列宽变化时左侧扩展/收缩,offsetX 补偿 (oldWidth - newWidth)
  行高变化时上侧扩展/收缩,offsetY 补偿 (oldHeight - newHeight)
```

#### 接口定义

**BoundaryInterface**:提供数据范围的边界

```java
interface BoundaryInterface {
    int getLeftBound();   // 最小列(支持 MIN_VALUE)
    int getTopBound();    // 最小行(支持 MIN_VALUE)
    int getRightBound();  // 最大列(支持 MAX_VALUE)
    int getBottomBound(); // 最大行(支持 MAX_VALUE)
}
```

**WindowInterface**:定义视窗交互

```java
interface WindowInterface {
    void in(int column, int row);                      // 瓦片进入视窗
    void out(int column, int row);                     // 瓦片离开视窗
    void onWindowCalculated(int colStart, int rowStart, int colEnd, int rowEnd);  // 新视窗计算完毕
    int getColWidth(int column);                       // 获取列宽
    int getRowHeight(int row);                         // 获取行高
}
```

#### 边界条件

- **空范围**:`getLeftBound() > getRightBound()` 或 `getTopBound() > getBottomBound()` 视为空
- **isAtLeftBound/isAtTopBound**:`colStart == leftBound && offsetX == 0`
- **isAtRightBound/isAtBottomBound**:`colEnd == rightBound && totalWidth + offsetX == windowWidth`
- **合法性检查**:`colStart > rightBound || rowStart > bottomBound || colEnd < leftBound || rowEnd < topBound` 时 sync 直接短路返回 false
- **seek 目标检查**:`checkLocationInBounds(column, row)` 确保目标在边界内

### TileManager(瓦片管理器)

职责:管理活跃瓦片、濒死瓦片和回收池三个状态的生命周期。

不依赖 Android SDK,但依赖一个 `LongMap<T>` 接口和 `TileRecycledPool<T>`。

#### 三状态池

```
activeTiles(LongMap<T>)       — 当前在视窗内可见
dyingTiles(LongMap<T>)        — 刚离开视窗(保留一个瓦片的缓冲)
recycledTiles(TileRecycledPool<T>) — 已回收可复用
```

#### 瓦片 ID 编码

```
long id = ((long) column << 32) | (row & 0xFFFFFFFFL);
int column = (int) (id >> 32);
int row = (int) (id & 0xFFFFFFFFL);
```

port 时可以直接用 `column + "," + row` 作为字符串 key,但 long 编码更快。没有 64 位整数类型的语言可以用 `(col << 16) | row` 的 32 位编码,代价是 col/row 范围缩小到 16 位。

#### 濒死区策略

濒死区 = 当前视窗向外扩展一圈:

```
getDyingLeft()   = colStart > leftBound ? colStart - 1 : leftBound
getDyingTop()    = rowStart > topBound  ? rowStart - 1 : topBound
getDyingRight()  = colEnd   < rightBound ? colEnd   + 1 : rightBound
getDyingBottom() = rowEnd   < bottomBound ? rowEnd   + 1 : bottomBound
```

`diffDying(colStart, rowStart, colEnd, rowEnd)` 在每次视窗计算后被调用。它清理所有超出濒死区的瓦片到回收池。

#### 核心流程

```
in(column, row):
  1. 先从 dyingTiles 中查找(id)
  2. 找到 → 移入 activeTiles(跳过绑定)
  3. 没找到 → obtain(type) 获取或创建 → 绑定 → 存入 activeTiles
  4. 调用 onInWindow + callback.onTileIn

out(column, row):
  1. 从 activeTiles 中移除(id)
  2. 调用 onOutWindow + callback.onTileOut
  3. 放入 dyingTiles

obtain(type):
  1. 从 recycledTiles.get(type) 获取
  2. 有 → 复用,recycledCount--
  3. 无 → callback.onCreateTileHolder(type)

recycle(tile):
  1. recycledTiles.recycle(type, tile)
  2. tile.onRecycled() + callback.onTileRecycled
  3. recycledCount++
```

#### 接口定义

```java
interface Callback<T> {
    int getTileType(int column, int row);
    T onCreateTileHolder(int type);
    void onBindTileHolder(T holder, int column, int row);
    void onTileIn(T holder, int column, int row);
    void onTileOut(T holder, int column, int row);
    void onTileRecycled(T holder, int column, int row);
    void onTileSizeChanged(T holder, int column, int row, int width, int height);
    int getTileWidth(int column);
    int getTileHeight(int row);
    int getLeftBound();
    int getTopBound();
    int getRightBound();
    int getBottomBound();
    LayoutModel getLayoutModel();
    void beforeLayout();
    void updateUI();
}
```

### DimenManager(尺寸管理器)

职责:尺寸的存储、查询、修改,尺寸变更时同步更新已有瓦片和触发布局扰动。

不依赖 Android SDK,只依赖 `IntIntMap` 和 `TileDimenProvider`。

#### 尺寸优先级

```
1. setTileWidth(column, width) 单独设置的值(widths/heights 映射)
2. TileDimenProvider 提供的动态值
3. setDefaultTileWidth/setDefaultTileHeight 设置的默认值
```

#### 尺寸修改流程

```
setTileWidth(column, width, gravity):
  1. 检查边界是否为空,检查 column 是否在合法范围内
  2. width = 0 时删除自定义尺寸(回退到优先级链)
  3. 如果 width 未变化 → 直接返回
  4. 遍历濒死区内该列关联的所有瓦片,调用 resizeTile 更新尺寸
  5. 调用 beforeLayout
  6. 调用 LayoutEngine.updateWidth(column, oldWidth, newWidth, gravity)
  7. 调用 updateUI
```

`setTileHeight` 行为一致。`setTileSize` 合并两个方向的扰动为一次调用。

#### 接口定义

```java
interface Callback {
    int getLeftBound();
    int getTopBound();
    int getRightBound();
    int getBottomBound();
    int getDyingLeft();
    int getDyingTop();
    int getDyingRight();
    int getDyingBottom();
    void resizeTile(int column, int row, int width, int height);
    void updateWidth(int column, int oldWidth, int newWidth, int gravity);
    void updateHeight(int row, int oldHeight, int newHeight, int gravity);
    void updateSize(int column, int oldWidth, int newWidth, int hGravity,
                    int row, int oldHeight, int newHeight, int vGravity);
    void beforeLayout();
    void updateUI();
    boolean isEmpty();
}
```

---

## 数据结构依赖

这三个模块需要的数据结构没有平台特殊性,port 时在目标语言直接实现即可。

### LongMap<K,V>

活跃瓦片和濒死瓦片使用 `LongMap<T>`。接口需求:

- `put(long key, T value)` / `get(long key)` / `remove(long key)` / `clear()` / `size()`
- `iterator()` 遍历
- 建议用哈希表实现,O(1) 读写

Java 参考实现:
- `LongMapSparseArray`:基于 `LongSparseArray`,有序存储,适合小数据量
- `LongMapHashMap`:基于 `HashMap<Long, T>`,无序,大批量删除时性能更优

### IntIntMap

列宽和行高使用 `IntIntMap`。接口需求:

- `put(int key, int value)` / `get(int key)` / `containsKey(int key)` / `remove(int key)` / `clear()` / `size()`
- `iterator()` 遍历

Java 参考实现:
- `IntIntMapSparseArray`:基于 `SparseIntArray`
- `IntIntMapHashMap`:基于 `HashMap<Integer, Integer>`

### TileRecycledPool<T>

回收池接口:

- `T get(int type)` — 按类型获取一个瓦片实例,无可用时返回 null
- `void recycle(int type, T tile)` — 回收瓦片,按类型分组
- `void reset()` — 清空全部
- 内部用 `IntMap<Deque<T>>` 按类型分组存储

### BaseTileHolder

跨平台的瓦片持有者基类,包含:

```
int column, row;   // 当前坐标
int width, height; // 当前尺寸
int type;          // 瓦片类型
```

提供以下生命周期钩子(可选实现):

- `onRecycled()` — 回收时清理资源
- `onInWindow()` — 进入视窗
- `onOutWindow()` — 离开视窗
- `onSizeChanged(width, height)` — 尺寸变化

---

## 移植步骤

### 1. 实现数据结构

先实现 `LongMap<T>`、`IntIntMap`、`TileRecycledPool<T>`、`LayoutModel` 四个基础数据结构。

### 2. 实现 LayoutEngine

按照上文算法,用目标语言实现 `LayoutEngine`。注意:

- `min`/`max` 函数不要用标准库泛型版本,用专门处理 int 和 float 的重载版本,避免装箱开销
- 调试代码(`timeProvider`、`syncTime`)可以全部丢弃,计时只依赖时间提供器是否存在
- `BoundaryInterface` 和 `WindowInterface` 用目标语言的接口/协议 trait 表达

### 3. 实现 TileManager

实现三个池的管理和瓦片 ID 编码。`Callback<T>` 设计为接口 delegate。

### 4. 实现 DimenManager

实现尺寸三级优先级查询和修改扰动。`Callback` 设计为接口 delegate。

### 5. 实现调度层(TileCoreService)

将三个子模块组合起来,提供统一 API。这一步已经在平台扩展文档中详细说明,移植时只做语言翻译即可。

### 6. 实现渲染层

参照平台扩展文档中的「在不同生态实现自定义渲染层」部分,为你的目标平台实现视图层。

---

## 测试策略

移植完成后,用以下方式验证正确性:

### 单元测试

三个模块都可以独立单元测试:

- **LayoutEngine**:mock `BoundaryInterface` 和 `WindowInterface`,验证各种滚动/scseok/尺寸变更场景下的 `LayoutModel` 输出是否正确
- **TileManager**:mock `Callback<T>`,验证瓦片的进出和回收计数
- **DimenManager**:mock `Callback`,验证尺寸查询和修改扰动

### 边界场景

- 空边界(`left > right`)
- 单列/行场景
- 从负索引开始(`MIN_VALUE` 附近)
- 高频抖动(在同一列边界反复滚动)
- 尺寸反复修改(从大到小再到大)
- 全部瓦片返回 null(稀疏场景)
