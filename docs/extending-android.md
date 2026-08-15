# 平台扩展指南

## 概述

Tile2D 的核心设计是分层架构:
- **视图层**(TileView / TileLayout)处理 Android 平台相关逻辑
- **中间层**封装了与平台无关的核心能力

这份文档讲解如何在不同的渲染生态中对接中间层实现自定义视图,以及中间层的详细用法。关于引擎层的说明另有文档。

---

## 中间层详解

视图层之下是四个子模块,由 `TileCoreService` 统一调度。自定义视图时需要通过 `CoreInterface` 与它们协作。

### TileCoreService

`TileCoreService` 是整个框架的中央调度器,对外暴露视图层所需的所有 API,对内编排 LayoutEngine、TileManager、DimenManager、EventHandler 四个子系统。

```
自定义视图(TileView / TileLayout / Compose / OpenGL ...)
       ↓
TileCoreService ← 子模块访问器 -> LayoutEngine
       │                              TileManager
       │        ← CoreInterface       DimenManager
       │                              EventHandler
       ↓
    TileAdapter(数据源)
```

构造时需传入 `CoreInterface` 实现:

```java
TileCoreService<MyHolder> coreService = new TileCoreService<>(context, coreInterface);
```

#### 子模块访问器

`TileCoreService` 提供四个 getter 可以直接访问子模块,用于需要绕过标准 API 直接操作引擎层的场景:

- `getLayoutEngine()` — 访问 LayoutEngine
- `getTileManager()` — 访问 TileManager
- `getDimenManager()` — 访问 DimenManager
- `getEventHandler()` — 访问 EventHandler

这些访问器在自定义视图层的特殊需求中使用,常规使用不需要。

#### CoreInterface 说明

自定义视图层时需要实现 `CoreInterface<T>`。它定义了视图层与调度层的契约:

```java
public interface CoreInterface<T extends BaseTileHolder> {

    // 布局回调
    void beforeLayout();     // 布局即将开始
    void updateUI();         // 布局已完成,触发视图层刷新

    // 瓦片生命周期回调
    void onTileIn(T holder, int column, int row);
    void onTileOut(T holder, int column, int row);
    void onTileRecycled(T holder, int column, int row);
    void onTileSizeChanged(T holder, int column, int row, int width, int height);

    // 边界查询(委托给 Adapter)
    int getLeftBound();
    int getTopBound();
    int getRightBound();
    int getBottomBound();

    // 瓦片创建与绑定(委托给 Adapter)
    T onCreateTileHolder(int type);
    void onBindTileHolder(T holder, int column, int row);
    int getTileType(int column, int row);

    // 调试
    boolean isDebugMode();
}
```

#### 公共 API

以下方法通过 `TileCoreService` 暴露给视图层,自定义视图时直接调用:

- `void sync(float dx, float dy)` — 按像素偏移滚动,驱动 LayoutEngine 重新计算视窗
- `void seek(int column, int row, float offsetX, float offsetY)` — 跳转到指定位置
- `void snap()` — 视窗吸附
- `void setBounds(int left, int top, int right, int bottom)` — 设置视图边界
- `void setDefaultTileWidth(int width)` / `setDefaultTileHeight(int height)`
- `void setTileWidth(int column, int width, int gravity)` 等尺寸方法
- `void update(int column, int row)` 等更新方法
- `void handleTouchEvent(MotionEvent event)` — 将触摸事件交给 EventHandler 处理
- `void computeScroll()` — 驱动惯性滚动
- `void resetAnimator()` — 停止动画
- `int getTileWidth(int column)` / `getTileHeight(int row)`
- `float getTileX(int column)` / `float getTileY(int row)`
- `int findColumn(float x)` / `int findRow(float y)`
- `LayoutModel getLayoutModel()`
- `T getActiveTile(int column, int row)`
- `boolean isEmpty()`
- `boolean isAtLeftBound()` 等边界检测
- `int getActiveTileCount()` / `getRecycledTileCount()` / `getDyingTileCount()`
- `LongMap<T> getDyingTiles()`
- `void setActiveTiles(LongMap<T> map)` 等映射替换方法
- `void setTimeProvider(TimeProvider provider)` — 设置时间提供器,布局引擎据此开启同步耗时统计(调试用)

#### 静态工具方法

- `static long getTileId(int column, int row)` — 将行列编码为唯一 long ID
- `static int getColumn(long id)` — 从 ID 解码列
- `static int getRow(long id)` — 从 ID 解码行

### TileManager

`TileManager` 负责瓦片的完整生命周期管理,维护三个状态池:

- **活跃瓦片**(activeTiles):当前在视窗内可见的瓦片
- **濒死瓦片**(dyingTiles):刚从视窗离开,即将被回收的瓦片(保留一个格子的缓冲)
- **回收池**(recycledPool):已回收的瓦片,供后续复用

```
进入视窗 → 活跃池
离开视窗 → 濒死池
离开濒死区 → 回收池
复用 → 活跃池
```

通过 `TileCoreService` 的子模块访问器获取:

```java
TileManager<MyHolder> manager = coreService.getTileManager();
```

#### 濒死区策略

`TileManager` 维护一个濒死区域,范围是当前视窗向外扩展一圈。当 LayoutEngine 计算出新视窗后,`diffDying` 方法清理超出扩展区的濒死瓦片。

濒死区边界计算方法:

- `getDyingLeft()` = `colStart - 1`(不越出左边界)
- `getDyingTop()` = `rowStart - 1`(不越出上边界)
- `getDyingRight()` = `colEnd + 1`(不越出右边界)
- `getDyingBottom()` = `rowEnd + 1`(不越出下边界)

#### 核心方法

- `void in(int column, int row)` — 瓦片进入视窗。先从濒死池查找,找到则直接移入活跃池,否则创建新瓦片。
- `void out(int column, int row)` — 瓦片离开视窗,移入濒死池。
- `T obtain(int type)` — 从回收池获取一个指定类型的瓦片实例。
- `void recycle(T tile)` — 将瓦片回收到池中。
- `void clearAll()` — 清理所有活跃、濒死、回收池。
- `void clearActiveAndDying()` — 清理活跃和濒死瓦片(seek 时调用)。
- `void diffDying(int colStart, int rowStart, int colEnd, int rowEnd)` — 清理超出濒死区的瓦片。
- `void resizeTile(int column, int row, int width, int height)` — 更新已有瓦片的尺寸记录。
- `void update(int column, int row)` — 刷新指定瓦片。
- `void updateRange(int left, int top, int right, int bottom)` — 刷新区域内瓦片。
- `T getActiveTile(int column, int row)` — 获取活跃瓦片。
- `void setActiveTiles(LongMap<T> map)` — 替换活跃池存储。
- `void setDyingTiles(LongMap<T> map)` — 替换濒死池存储。
- `void setRecycledTiles(TileRecycledPool<T> pool)` — 替换回收池。

#### TileManager.Callback

TileManager 通过回调接口与 CoreService 通信,自定义视图层一般不需要直接实现:

```java
public interface Callback<T extends BaseTileHolder> {
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

### DimenManager

`DimenManager` 负责尺寸的存储、查询、修改与布局扰动。尺寸优先级:

1. `setTileWidth(column, width)` 等单独设置的值
2. `TileDimenProvider` 提供的值
3. `setDefaultTileWidth` 设置的默认值

通过 `TileCoreService` 的子模块访问器获取:

```java
DimenManager dimen = coreService.getDimenManager();
```

#### 核心方法

- `int getTileWidth(int column)` / `getTileHeight(int row)` — 查询尺寸,按优先级查找。
- `void setTileWidth(int column, int width, int gravity)` — 修改列宽,触发布局扰动。
- `void setTileHeight(int row, int height, int gravity)` — 修改行高,触发布局扰动。
- `void setTileSize(...)` — 同时修改,扰动一次性完成。
- `void deleteTileWidth(int column, int gravity)` / `deleteTileHeight(int row, int gravity)` — 删除自定义尺寸。
- `void setDefaultTileWidth(int width)` / `setDefaultTileHeight(int height)` — 设置默认尺寸。
- `void setDimenProvider(TileDimenProvider provider)` — 设置尺寸提供者。
- `void setWidths(IntIntMap map)` / `setHeights(IntIntMap map)` — 替换内部存储。

#### 尺寸修改的副作用

修改尺寸时,DimenManager 会:

1. 更新尺寸记录
2. 遍历濒死区内与该列/行关联的所有瓦片,调用 `resizeTile` 通知瓦片尺寸变化
3. 调用 LayoutEngine 的 `updateWidth`/`updateHeight`/`updateSize` 调整视窗偏移
4. 触发 UI 刷新

对齐常量(`gravity`)决定了尺寸变化时视窗偏移的补偿方向。详见主文档的"尺寸对齐常量"章节。

#### DimenManager.Callback

```java
public interface Callback {
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

### EventHandler

`EventHandler` 处理 Android 平台相关的触摸事件、手势检测和惯性滚动。通过 `TileCoreService` 的子模块访问器获取:

```java
EventHandler handler = coreService.getEventHandler();
```

#### 事件流转

```
TouchEvent → handleTouchEvent → GestureDetector.onTouchEvent
                                   ↓ onScroll / onFling
                                 coreService.sync(dx, dy)
                                   ↓
                                 LayoutEngine.sync → diff 进出瓦片
                                   ↓
                                 updateUI(刷新视图)
```

惯性滚动通过 Android `Scroller` 实现,在 `computeScroll` 中驱动。

#### 核心方法

- `void handleTouchEvent(MotionEvent event)` — 处理触摸事件。`ACTION_DOWN` 时自动调用 `resetAnimator` 停止惯性滚动。
- `void computeScroll()` — 在 View 的 `computeScroll` 中调用,驱动惯性滚动。
- `void resetAnimator()` — 停止 Scroller。
- `void requestDisallowInterceptTouchEvent(boolean disallowIntercept)` — 控制触摸拦截。
- `boolean isInteractingWithView()` — 是否正在交互(滚动中)。

#### EventHandler.Callback

```java
public interface Callback {
    boolean isHorizontalScrollEnabled();
    boolean isVerticalScrollEnabled();
    void sync(float dx, float dy);
    void updateUI();
}
```

### LayoutModel

`LayoutModel` 是视窗布局的状态快照,包含以下字段:

- `colStart` / `rowStart` — 可见区域起点(闭区间)
- `colEnd` / `rowEnd` — 可见区域终点(闭区间)
- `offsetX` / `offsetY` — 像素级偏移
- `totalWidth` / `totalHeight` — 视窗内内容总尺寸

通过 `getLayoutModel()` 获取快照。如果需要独立副本,调用 `newInstance()`。

---

## 自定义存储容器

框架内部使用稀疏映射存储活跃瓦片、濒死瓦片、列宽和行高。默认实现基于 `SparseArray`,你可以替换为自己的实现。

### 存储接口说明

**活跃/濒死瓦片**使用 `LongMap<T>`:

```java
public interface LongMap<T> {
    T put(long key, T value);
    T get(long key);
    T remove(long key);
    void clear();
    int size();
    Iterator<T> iterator();
    Iterator<T> iterator(boolean ascending);

    interface Iterator<T> {
        boolean next();
        long key();
        T value();
        void remove();
    }
}
```

内置实现:
- `LongMapSparseArray`(默认) — 基于 `LongSparseArray`,插入有序,适合小数据量
- `LongMapHashMap` — 基于 `HashMap<Long, T>`,插入无序,大批量删除时性能更好

**列宽/行高**使用 `IntIntMap`:

```java
public interface IntIntMap {
    int put(int key, int value);
    int get(int key);
    boolean containsKey(int key);
    int remove(int key);
    void clear();
    int size();
    Iterator iterator();

    interface Iterator {
        boolean next();
        int key();
        int value();
        void remove();
    }
}
```

内置实现:
- `IntIntMapSparseArray`(默认) — 基于 `SparseIntArray`
- `IntIntMapHashMap` — 基于 `HashMap<Integer, Integer>`

**回收池**使用 `TileRecycledPool<T>`,内部存储为 `IntMap<Deque<T>>`。

### 自定义实现

实现对应接口后通过 `set` 方法替换即可:

```java
// 自定义活跃瓦片存储
tileView.setActiveTiles(new LongMapHashMap<>());
// 自定义列宽存储
tileView.setWidths(new IntIntMapHashMap());
// 自定义回收池
tileView.setRecycledTiles(new TileRecycledPool<>());
```

替换时机:`setAdapter` 之前或之后均可。数据会自动迁移到新映射,旧映射被清空。

也可以通过 `TileCoreService` 的子模块访问器直接替换:

```java
coreService.getTileManager().setActiveTiles(new LongMapHashMap<>());
coreService.getDimenManager().setWidths(new IntIntMapHashMap());
```

---

## 在不同生态实现自定义渲染层

Tile2D 的视图层与核心调度层是解耦的。渲染层只负责"把可见瓦片画出来",所有坐标计算、视窗同步、生命周期都由 TileCoreService 管理。这意味着可以在任何渲染体系下复用核心能力。

### 共通步骤

不管用哪种渲染体系,接入流程都一样:

1. 创建 `TileCoreService` 实例,传入 `CoreInterface` 实现
2. 在 CoreInterface 的 `updateUI()` 中触发渲染刷新
3. 在 `onTileIn` / `onTileOut` 等回调中处理瓦片进入/离开事件
4. 响应尺寸变化和触摸事件

### Jetpack Compose

在 Compose 中实现自定义渲染层,核心思路是绕过 `AndroidView` 封装好的 TileView,直接由 Compose 管理渲染逻辑。

```kotlin
@Composable
fun Tile2DComposable(
    modifier: Modifier = Modifier,
    adapter: TileAdapter<*>,
    defaultTileWidth: Int = 80,
    defaultTileHeight: Int = 45,
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    // 持有 CoreService 引用
    val coreService = remember {
        TileCoreService<ComposeTileHolder>(context, object : TileCoreService.CoreInterface<ComposeTileHolder> {
            override fun updateUI() {
                // 触发 Compose 重组
                invalidate()
            }
            override fun beforeLayout() {}
            // ... 其余 CoreInterface 方法委托给 adapter
        }).apply {
            setDefaultTileWidth(defaultTileWidth)
            setDefaultTileHeight(defaultTileHeight)
            seek(adapter.getLeftBound(), adapter.getTopBound(), 0f, 0f)
        }
    }
}
```

Compose 的 `Canvas` 绘制:

```kotlin
Canvas(modifier = modifier
    .onSizeChanged { size ->
        coreService.setBounds(0, 0, size.width, size.height)
        coreService.sync(0f, 0f)
    }
    .pointerInput(Unit) {
        detectDragGestures { change, dragAmount ->
            change.consume()
            coreService.sync(-dragAmount.x, -dragAmount.y)
        }
    }
) {
    val model = coreService.layoutModel
    val column = model.colStart
    while (column <= model.colEnd) {
        val row = model.rowStart
        while (row <= model.rowEnd) {
            val tile = coreService.getActiveTile(column, row) as? ComposeTileHolder
            if (tile != null) {
                // 在对应位置绘制瓦片内容
                tile.draw(drawContext.canvas)
            }
        }
    }
}
```

如果你的需求仍是直接嵌入 TileView/TileLayout,`AndroidView` 仍然可用:

```kotlin
@Composable
fun Tile2DEmbedded(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            TileView(context).apply {
                setAdapter(object : TileView.Adapter() { /* ... */ })
            }
        }
    )
}
```

### OpenGL ES

在 OpenGL 中渲染 Tile2D,核心调度层完全不变,只替换渲染循环:

```java
public class GLRenderer implements GLSurfaceView.Renderer,
        TileCoreService.CoreInterface<GLTileHolder> {

    private TileCoreService<GLTileHolder> coreService;
    private GLSurfaceView glSurfaceView;

    // 瓦片数据
    private float[] vertexData;
    private int textureId;

    @Override
    public void updateUI() {
        // 触发 GLSurfaceView 重绘
        glSurfaceView.requestRender();
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        // 清屏
        gl.glClear(GL10.GL_COLOR_BUFFER_BIT);

        LayoutModel model = coreService.getLayoutModel();
        // 遍历活跃瓦片,在对应位置提交绘制指令
        for (int col = model.colStart; col <= model.colEnd; col++) {
            for (int row = model.rowStart; row <= model.rowEnd; row++) {
                GLTileHolder tile = (GLTileHolder) coreService.getActiveTile(col, row);
                if (tile != null) {
                    float x = coreService.getTileX(col) - getPaddingLeft();
                    float y = coreService.getTileY(row) - getPaddingTop();
                    int w = coreService.getTileWidth(col);
                    int h = coreService.getTileHeight(row);
                    tile.render(x, y, w, h);
                }
            }
        }
    }

    // 触摸事件需要手动将 MotionEvent 传给 EventHandler
    public boolean onTouchEvent(MotionEvent event) {
        coreService.handleTouchEvent(event);
        return true;
    }
}
```

瓦片持有者需要持有 OpenGL 渲染资源:

```java
public static class GLTileHolder extends TileCoreService.BaseTileHolder {
    private FloatBuffer vertexBuffer;
    private int textureId;

    public void render(float x, float y, int width, int height) {
        // 更新顶点缓冲区并提交绘制
        float[] vertices = {
            x, y,
            x + width, y,
            x, y + height,
            x + width, y + height
        };
        // ... GL 绘制指令
    }

    @Override
    public void onRecycled() {
        // 释放纹理、缓冲区等 GL 资源
        // glDeleteTextures(...)
    }
}
```

### 自定义 View(原生的 Canvas 绘制)

如果 TileView 的功能不满足需求(例如需要特殊的绘制顺序、额外的触摸交互层),可以从 View 直接对接 TileCoreService:

```java
public class CustomTileView extends View
        implements TileCoreService.CoreInterface<CustomHolder> {

    private TileCoreService<CustomHolder> coreService;
    private CustomAdapter adapter;
    private boolean debugMode;

    public CustomTileView(Context context) {
        super(context);
        coreService = new TileCoreService<>(context, this);
        coreService.setDefaultTileWidth(80);
        coreService.setDefaultTileHeight(45);
    }

    public void setAdapter(CustomAdapter adapter) {
        if (this.adapter != adapter) {
            coreService.reset();
        }
        this.adapter = adapter;
        seek(adapter.getLeftBound(), adapter.getTopBound(), 0, 0);
    }

    // ========== CoreInterface ==========

    @Override
    public void updateUI() {
        postInvalidateOnAnimation();
    }

    @Override
    public void beforeLayout() {}

    @Override
    public void onTileIn(CustomHolder holder, int column, int row) {
        // 瓦片进入视窗,可以准备资源
    }

    @Override
    public void onTileOut(CustomHolder holder, int column, int row) {
        // 瓦片离开视窗
    }

    @Override
    public void onTileRecycled(CustomHolder holder, int column, int row) {
        // 瓦片被回收
    }

    @Override
    public void onTileSizeChanged(CustomHolder holder, int column, int row, int width, int height) {}

    @Override
    public int getLeftBound() { return adapter == null ? 0 : adapter.getLeftBound(); }
    @Override
    public int getTopBound() { return adapter == null ? 0 : adapter.getTopBound(); }
    @Override
    public int getRightBound() { return adapter == null ? -1 : adapter.getRightBound(); }
    @Override
    public int getBottomBound() { return adapter == null ? -1 : adapter.getBottomBound(); }

    @Override
    public CustomHolder onCreateTileHolder(int type) { return adapter.onCreateTileHolder(type); }
    @Override
    public void onBindTileHolder(CustomHolder holder, int column, int row) { adapter.onBindTileHolder(holder, column, row); }
    @Override
    public int getTileType(int column, int row) { return adapter.getTileType(column, row); }
    @Override
    public boolean isDebugMode() { return debugMode; }

    // ========== View 生命周期 ==========

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        coreService.setBounds(
            getPaddingLeft(), getPaddingTop(),
            getWidth() - getPaddingRight(), getHeight() - getPaddingBottom()
        );
        if (getWidth() != 0 && getHeight() != 0) {
            coreService.sync(0, 0);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        LayoutModel model = coreService.getLayoutModel();
        if (model.colStart > model.colEnd || model.rowStart > model.rowEnd) return;

        canvas.save();
        canvas.translate(getPaddingLeft(), getPaddingTop());
        canvas.translate(model.offsetX, model.offsetY);

        float baseX = 0;
        for (int col = model.colStart; col <= model.colEnd; col++) {
            float baseY = 0;
            for (int row = model.rowStart; row <= model.rowEnd; row++) {
                CustomHolder tile = coreService.getActiveTile(col, row);
                if (tile != null) {
                    canvas.save();
                    canvas.translate(baseX, baseY);
                    tile.draw(canvas);
                    canvas.restore();
                }
                if (row == model.rowEnd) break;
                baseY += coreService.getTileHeight(row);
            }
            if (col == model.colEnd) break;
            baseX += coreService.getTileWidth(col);
        }
        canvas.restore();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (coreService.isEmpty()) return super.onTouchEvent(event);
        coreService.handleTouchEvent(event);
        return true;
    }

    @Override
    public void computeScroll() {
        super.computeScroll();
        coreService.computeScroll();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        coreService.resetAnimator();
    }

    // ========== 快速方法 ==========

    public void offset(float dx, float dy) {
        if (coreService.isEmpty()) return;
        coreService.sync(dx, dy);
    }

    public void seek(int column, int row, float offsetX, float offsetY) {
        if (coreService.isEmpty()) return;
        coreService.seek(column, row, offsetX, offsetY);
    }

    // ========== 适配器与 Holder ==========

    public static abstract class CustomAdapter extends TileAdapter<CustomHolder> {}

    public static class CustomHolder extends TileCoreService.BaseTileHolder {
        public void draw(Canvas canvas) {}
    }
}
```

### DebugLayer 集成

调试面板是独立的 `DebugLayer` 类,可以在自定义视图中复用:

```java
DebugLayer debugLayer = new DebugLayer(getContext(), new DebugLayer.Callback() {
    @Override public int getActiveTileCount() { return coreService.getActiveTileCount(); }
    @Override public int getRecycledTileCount() { return coreService.getRecycledTileCount(); }
    @Override public int getDyingTileCount() { return coreService.getDyingTileCount(); }
    @Override public Rect getBounds() { return coreService.getBounds(); }
    @Override public LayoutModel getLayoutModel() { return coreService.getLayoutModel(); }
    @Override public void postInvalidateOnAnimation() { CustomTileView.this.postInvalidateOnAnimation(); }
    @Override public long getBindTime() { return coreService.getBindTime(); }
    @Override public long getLayoutTime() { return layoutTime; }
});
```

在 `onDraw` 的瓦片绘制前后调用 `debugLayer.startDraw()` 和 `debugLayer.draw(canvas)`。在视图 `onAttachedToWindow` / `onDetachedFromWindow` 时调用 `start()` / `end()`。
