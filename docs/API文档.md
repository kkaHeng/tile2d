# API 文档

## TileView / TileLayout

> 说明：`TileView`（自绘）与 `TileLayout`（原生 View）渲染范式不同，但对外的 API 基本一致，下文统称为「视图」；触摸交互的差异见「触摸事件」章节。

### 基本操作

- `void offset(float dx, float dy)`  
使内容滚动一段距离。正数向右，负数向左。

- `void seek(int column, int row, float offsetX, float offsetY)`  
跳转到指定坐标，并且指定初始偏移（滚动距离）。

- `void seek(int column, int row)`  
跳转到指定坐标。

- `void snap()`  
在适配器边界变化时调用，将视窗从非法范围拉回到合法范围里（贴边对齐）。

- `boolean isHorizontalScrollEnabled()`  
检查是否开启横向滚动。

- `boolean isVerticalScrollEnabled()`  
检查是否开启纵向滚动。

- `void setHorizontalScrollEnabled(boolean horizontalScrollEnabled)`  
设置开启横向滚动。

- `void setVerticalScrollEnabled(boolean verticalScrollEnabled)`  
设置开启纵向滚动。

### 缩放

缩放是**渲染层**的变换，引擎始终工作在未缩放的逻辑空间：`scaleFactor` 只出现在绘制时的乘法与 `windowWidth = bounds.width() / scaleFactor` 的除法里，滚动、跳转、尺寸修改的语义在任意缩放级别下完全一致。

- `void zoom(float scaleFactor)`  
缩放到指定因子（`focus = 0,0`，即视窗原点），自动夹取到最小/最大缩放因子之间。

- `void zoom(float scaleFactor, float focusX, float focusY, float dx, float dy)`  
缩放到指定因子，`(focusX, focusY)` 为屏幕焦点（缩放中心，参照视图坐标），`dx/dy` 为附加的屏幕平移。语义：先以焦点为中心缩放到 `scaleFactor`，再平移。

- `void zoomBy(float relativeScale, float focusX, float focusY)`  
以当前因子为基准做相对缩放，例如 `zoomBy(2f, cx, cy)` 放大一倍。同样以 `(focusX, focusY)` 为焦点，即时生效、不走快照。

- `float getScaleFactor()`  
获取当前缩放因子，默认 1。

- `float getMinScaleFactor()` / `void setMinScaleFactor(float scale)`  
获取/设置最小缩放因子，默认 0.5，必须大于 0。

- `float getMaxScaleFactor()` / `void setMaxScaleFactor(float scale)`  
获取/设置最大缩放因子，默认 2，必须大于最小缩放因子。

- `boolean isZooming()`  
是否处于双指缩放（快照）模式。缩放过程中布局引擎完全冻结，此时 `getScaleFactor()` 返回的是缩放开始时的值。

- `boolean isZoomEnabled()` / `void setZoomEnabled(boolean enabled)`  
双指缩放开关，默认开启。关闭会立即放弃进行中的缩放会话。

- `void cancelZoom()`  
放弃进行中的缩放会话：丢弃全部累积输入，画面回到缩放开始时的状态。视窗尺寸变化、跳转、尺寸修改会自动触发。

> **双指手势**：`EventHandler` 内置 `ScaleGestureDetector`。第二根手指落下即进入快照缩放模式；捏合期间不触发布局引擎，只对快照做平移与缩放；两指全部抬起后才一次性结算。抬起一根手指后仍停留在缩放模式，剩余手指可继续拖动。缩放与拖动不互斥，共用同一条累积通道。

- `Adapter getAdapter()`  
获取适配器。

- `void setAdapter(Adapter adapter)`  
设置适配器。替换适配器会彻底清空所有内容，包括濒死区等缓存。

- `boolean isEmpty()`  
检查适配器边界是否为空（左上＞右下）。

- `boolean isAtLeftBound()`  
检查视窗是否触及适配器左边界。

- `boolean isAtTopBound()`  
检查视窗是否触及适配器上边界。

- `boolean isAtRightBound()`  
检查视窗是否触及适配器右边界。

- `boolean isAtBottomBound()`  
检查视窗是否触及适配器下边界。

- `boolean isDebugMode()`  
是否已开启调试面板。

- `void setDebugMode(boolean enabled)`  
设置开启调试面板。

### 工具方法

- `float getTileX(int column)`  
计算目标列相对于视窗左上角的起始横坐标（含内边距）。

- `float getTileY(int row)`  
计算目标行相对于视窗左上角的起始纵坐标（含内边距）。

- `int findColumn(float x)`  
查找指定坐标（含内边距）的落点列号。

- `int findRow(float y)`  
查找指定坐标（含内边距）的落点行号。

- `LayoutModel getLayoutModel()`  
获取当前布局模型，不是快照，不要修改，调用`newInstance`可以创建快照。

- `void setTileEventListener(TileEventListener<TileHolder> tileEventListener)`  
设置瓦片事件监听器。

### 缓存区（濒死区与预取区）

两个缓存区互补：**濒死向后留，预取向前抢**。濒死区保存刚离开视窗的瓦片，回滚时跳过重新绑定；预取区提前加载运动方向前方的瓦片，进窗时直接转正。

#### 濒死区

濒死区是视窗向外扩展的缓冲环：瓦片离开视窗后暂存，短暂缓冲后可能被回收。

- `int getDyingExpand()`  
获取濒死区扩展层数。

- `void setDyingExpand(int expand)`  
设置濒死区扩展层数，必须大于等于1。

- `boolean isDyingEnabled()`  
检查是否启用濒死区。

- `void setDyingEnabled(boolean enabled)`  
设置启用濒死区，关闭时回收全部瓦片。

#### 预取区

预取区是濒死区的镜像补充：按运动**方向预测**，只朝视窗前方扩展条带并分批预加载瓦片，进入视窗时直接转正，避免滚动中一次性创建导致卡顿。**默认开启**，每帧最多消费 8 个预取任务。

- `boolean isPrefetchEnabled()`  
检查是否启用预取。

- `void setPrefetchEnabled(boolean enabled)`  
设置启用预取，默认开启；关闭时清空预取区。

- `int getPrefetchExpand()`  
获取预取扩展层数。

- `void setPrefetchExpand(int expand)`  
设置预取扩展层数，必须大于0，默认1。

- `int getPrefetchPerFrame()`  
获取每帧预取数量上限（帧预算，由瓦片管理器持有），默认 8。

- `void setPrefetchPerFrame(int count)`  
设置每帧预取数量上限，必须大于0。数值越大预取越快，但单帧创建绑定成本越高；数值越小越保守，低端机可适当调低。

- `int getPrefetchTileCount()`  
获取预取瓦片数（调试用）。

- `int getPrefetchQueuePeak()`  
获取预取队列历史峰值（调试用，随预取区生命周期归零）。

### 更新操作

- `void update(int column, int row)`  
刷新指定单元格的瓦片。

- `void updateRange(int left, int top, int right, int bottom)`  
刷新矩形区域（闭区间）内的所有瓦片，自动与濒死区求交集。

- `void updateColumn(int column)`  
刷新指定列的整列瓦片。

- `void updateRow(int row)`  
刷新指定行的整行瓦片。

- `void updateAll()`  
刷新全部瓦片，等价于在原地重新执行一次 `seek`。

### 瓦片操作

- `TileHolder getActiveTile(int column, int row)`  
获取当前活跃（在视窗内）的瓦片持有者，不在视窗内返回 `null`。

#### 尺寸操作

- `int getTileWidth(int column)`  
查询指定列宽。

- `int getTileHeight(int row)`  
查询指定行高。

- `void setTileWidth(int column, int width)`  
设置指定列宽，必须大于0，触发布局扰动。

- `void setTileWidth(int column, int width, int gravity)`  
设置指定列宽（必须大于0），并指定对齐方向。

- `void setTileHeight(int row, int height)`  
设置指定行高，必须大于0，触发布局扰动。

- `void setTileHeight(int row, int height, int gravity)`  
设置指定行高（必须大于0），并指定对齐方向。

- `void setTileSize(int column, int width, int row, int height)`  
同时设置列宽与行高（均必须大于0），布局扰动只发生一次。

- `void setTileSize(int column, int width, int hGravity, int row, int height, int vGravity)`  
同时设置列宽与行高（均必须大于0），并分别指定水平、垂直对齐方向。

- `void deleteTileWidth(int column)`  
删除指定列的自定义宽度，回退到默认尺寸。

- `void deleteTileWidth(int column, int gravity)`  
删除指定列的自定义宽度，并指定对齐方向。

- `void deleteTileHeight(int row)`  
删除指定行的自定义高度，回退到默认尺寸。

- `void deleteTileHeight(int row, int gravity)`  
删除指定行的自定义高度，并指定对齐方向。

- `int getDefaultTileWidth()`  
获取默认列宽。

- `int getDefaultTileHeight()`  
获取默认行高。

- `void setDefaultTileWidth(int width)`  
设置默认列宽，必须大于0。

- `void setDefaultTileHeight(int height)`  
设置默认行高，必须大于0。

> 尺寸优先级：单独设置的尺寸 > `TileDimenProvider` 提供的尺寸 > 默认尺寸。修改尺寸时，视窗内的已有瓦片会同步刷新，偏移会按对齐方向自动补偿，保证视觉稳定。

#### 尺寸对齐常量

- `DIMEN_GRAVITY_START`（-1）：左侧/上侧不动，右侧/下侧扩展或收缩。
- `DIMEN_GRAVITY_CENTER`（0）：两侧均匀扩展或收缩。
- `DIMEN_GRAVITY_END`（1）：右侧/下侧不动，左侧/上侧扩展或收缩。

#### 尺寸提供者

- `TileDimenProvider getDimenProvider()`  
获取尺寸提供者。

- `void setDimenProvider(TileDimenProvider dimenProvider)`  
设置尺寸提供者。尺寸查询优先级高于默认尺寸、低于单独设置的值。

### 容器替换

- `void setActiveTiles(LongMap<TileHolder> map)`  
替换活跃瓦片存储。

- `void setDyingTiles(LongMap<TileHolder> map)`  
替换濒死瓦片存储。

- `void setWidths(IntIntMap map)`  
替换列宽存储。

- `void setHeights(IntIntMap map)`  
替换行高存储。

- `void setRecycledTiles(TileRecycledPool<TileHolder> pool)`  
替换瓦片回收池。

- `void setPrefetchTiles(LongMap<TileHolder> map)`  
替换预取瓦片存储。

> 替换时数据会自动迁移到新容器，旧容器被清空。

### 触摸事件

- `boolean isInteractingWithView()`  
检查当前是否正在与视图交互（滚动中）。

- `void requestDisallowInterceptTouchEvent(boolean disallowIntercept)`  
请求父容器不要拦截触摸事件。

- `void resetAnimator()`  
停止当前的惯性滚动动画（通常在离开页面时调用）。

#### TileView

触摸事件会先按坐标命中瓦片，再转发给对应瓦片持有者处理：

- `long getLongPressTimeout()`  
获取长按判定时长（毫秒），默认400。

- `void setLongPressTimeout(long longPressTimeout)`  
设置长按判定时长（毫秒）。

## 适配器（TileAdapter）

- `int getLeftBound()`  
获取左边界（最小列），默认 `Integer.MIN_VALUE`。

- `int getTopBound()`  
获取上边界（最小行），默认 `Integer.MIN_VALUE`。

- `int getRightBound()`  
获取右边界（最大列），默认 `Integer.MAX_VALUE`。

- `int getBottomBound()`  
获取下边界（最大行），默认 `Integer.MAX_VALUE`。

- `abstract T onCreateTileHolder(int type)`  
创建指定类型的瓦片持有者，返回 `null` 表示稀疏瓦片（不创建）。

- `abstract void onBindTileHolder(T holder, int column, int row)`  
绑定数据到瓦片。

- `int getTileType(int column, int row)`  
返回瓦片类型，默认0。相同类型的瓦片会从回收池复用。

- `boolean isEmpty()`  
检查边界是否为空（左＞右或上＞下）。

## 瓦片事件监听器（TileEventListener）

- `void onBeforeLayout()`  
布局周期开始前调用，此时瓦片尚未开始布局。

- `void onAfterLayout()`  
布局周期结束后调用，瓦片已完成布局排版。

- `void onTileIn(T holder, int column, int row)`  
瓦片进入视窗并完成布局。

- `void onTileOut(T holder, int column, int row)`  
瓦片离开视窗，进入濒死区(如果启用)。

- `void onTileRecycled(T holder, int column, int row)`  
瓦片被回收到池中。

- `void onTilePrefetched(T holder, int column, int row)`  
瓦片被预取（已创建绑定，尚未进入视窗），可在此提前完成进窗前的准备工作（默认空实现）。

## 布局模型（LayoutModel）

- `int colStart` / `int rowStart` — 可见区域起始列/行（闭区间）
- `int colEnd` / `int rowEnd` — 可见区域结束列/行（闭区间）
- `float offsetX` / `float offsetY` — 视窗内容整体偏移
- `int totalWidth` / `int totalHeight` — 视窗内容总尺寸
- `long syncTime` — 最近一次同步耗时（调试用）

- `LayoutModel newInstance()`  
创建当前状态的副本。

- `void copyTo(LayoutModel model)`  
复制状态到指定模型。

- `void reset()`  
重置为初始状态。

## 核心服务（TileCoreService）

- `static long getTileId(int column, int row)`  
将行列坐标编码为唯一瓦片 ID。

- `static int getColumn(long id)`  
从瓦片 ID 提取列索引。

- `static int getRow(long id)`  
从瓦片 ID 提取行索引。

#### 调度操作

- `void sync(float dx, float dy)`  
按像素偏移滚动内容。

- `void seek(int column, int row, float offsetX, float offsetY)`  
跳转到指定坐标。

- `void snap()`  
将视窗吸附回合法范围。

- `void update(int column, int row)`  
刷新指定瓦片。

- `void updateRange(int left, int top, int right, int bottom)`  
刷新区域内瓦片。

- `void updateColumn(int column)`  
刷新整列。

- `void updateRow(int row)`  
刷新整行。

- `void updateAll()`  
刷新全部瓦片。

- `void resetAnimator()`  
停止惯性滚动动画。

- `void computeScroll()`  
驱动惯性滚动。

- `void handleTouchEvent(MotionEvent event)`  
处理触摸事件。

- `void requestDisallowInterceptTouchEvent(boolean disallowIntercept)`  
请求父容器不要拦截触摸事件。

- `boolean isInteractingWithView()`  
检查是否正在与视图交互。

- `void reset()`  
重置全部状态（更换适配器时调用）。

#### 缩放

缩放 API 与视图层同名方法一一对应，另外提供会话级接口，供自定义渲染端接入快照缩放：

- `void zoom(float scaleFactor, float focusX, float focusY, float dx, float dy)`  
缩放到指定因子（语义见视图层说明）。手动缩放会放弃进行中的手势缩放会话。

- `void zoomBy(float relativeScale, float focusX, float focusY)`  
以当前因子为基准的相对缩放。

- `float getScaleFactor()`  
获取当前缩放因子。

- `float getMinScaleFactor()` / `void setMinScaleFactor(float scale)`  
获取/设置最小缩放因子。

- `float getMaxScaleFactor()` / `void setMaxScaleFactor(float scale)`  
获取/设置最大缩放因子。

- `boolean isZooming()`  
是否处于快照缩放模式。处于该模式时：`sync` 拦截输入累积到快照、`seek` 放弃缩放后正常跳转、`drainPrefetch` 暂停创建瓦片。

- `boolean isZoomEnabled()` / `void setZoomEnabled(boolean enabled)`  
双指缩放开关。

- `void cancelZoom()`  
放弃进行中的缩放会话。

- `boolean beginZoom()`  
请求进入快照缩放模式：截取渲染层快照并冻结布局引擎。成功才进入，失败返回 `false`（调用方退回即时缩放或忽略手势）。

- `void updateZoom(float relativeScale, float focusX, float focusY)`  
累积捏合输入：`relativeScale` 为相对上一次的增量因子，`(focusX, focusY)` 为屏幕焦点。

- `void translateZoom(float dx, float dy)`  
累积屏幕像素平移（`sync` 拦截的内部通道）。

- `void endZoom()`  
结算缩放会话：释放快照后一次性把累积输入应用到引擎。

- `float getZoomScale()`  
获取快照当前的相对缩放（相对缩放开始时的画面）。

- `float getZoomTranslateX()` / `float getZoomTranslateY()`  
获取快照当前的屏幕平移（像素）。

- `void setZoomInterface(TileCoreService.ZoomInterface zoomInterface)`  
设置渲染层快照接口。**必须由渲染端注册**才能启用快照缩放，未注册时双指手势不会进入缩放模式。

> **ZoomInterface**（渲染端实现）：`captureZoomSnapshot()` 截取当前画面为位图并切换到快照渲染；`onZoomUpdate(scale, translateX, translateY)` 更新快照变换（渲染顺序等价于先 `translate` 再 `scale`）；`releaseZoomSnapshot()` 释放快照恢复常规渲染。内置 `TileView`/`TileLayout` 已通过 `ZoomSnapshot` 实现，自定义渲染端参考 `widget/zoom/ZoomSnapshot`。

#### 尺寸与坐标

- `void setBounds(int left, int top, int right, int bottom)`  
设置视窗边界。

- `Rect getBounds()`  
获取视窗边界。

- `float getTileX(int column)`  
计算指定列相对视窗的 X 坐标。

- `float getTileY(int row)`  
计算指定行相对视窗的 Y 坐标。

- `int findColumn(float x)`  
查找指定 X 坐标的落点列号。

- `int findRow(float y)`  
查找指定 Y 坐标的落点行号。

- `int getTileWidth(int column)`  
查询指定列宽。

- `int getTileHeight(int row)`  
查询指定行高。

- `void setTileWidth(int column, int width, int gravity)`  
设置指定列宽。

- `void setTileHeight(int row, int height, int gravity)`  
设置指定行高。

- `void setTileSize(int column, int width, int hGravity, int row, int height, int vGravity)`  
同时设置列宽与行高。

- `void deleteTileWidth(int column, int gravity)`  
删除指定列自定义宽度。

- `void deleteTileHeight(int row, int gravity)`  
删除指定行自定义高度。

- `int getDefaultTileWidth()`  
获取默认列宽。

- `int getDefaultTileHeight()`  
获取默认行高。

- `void setDefaultTileWidth(int width)`  
设置默认列宽。

- `void setDefaultTileHeight(int height)`  
设置默认行高。

- `TileDimenProvider getDimenProvider()`  
获取尺寸提供者。

- `void setDimenProvider(TileDimenProvider dimenProvider)`  
设置尺寸提供者。

#### 查询

- `LayoutModel getLayoutModel()`  
获取布局模型。

- `T getActiveTile(int column, int row)`  
获取活跃瓦片。

- `boolean isEmpty()`  
检查边界是否为空。

- `boolean isAtLeftBound()`  
检查是否触及左边界。

- `boolean isAtTopBound()`  
检查是否触及上边界。

- `boolean isAtRightBound()`  
检查是否触及右边界。

- `boolean isAtBottomBound()`  
检查是否触及下边界。

- `int getActiveTileCount()`  
获取活跃瓦片数。

- `int getRecycledTileCount()`  
获取回收瓦片数。

- `int getDyingTileCount()`  
获取濒死瓦片数。

- `LongMap<T> getDyingTiles()`  
获取濒死瓦片映射。

- `boolean isPrefetchEnabled()`  
检查是否启用预取。

- `void setPrefetchEnabled(boolean enabled)`  
设置启用预取（默认开启）。

- `boolean hasPrefetchPending()`  
检查预取队列是否还有待消费的坐标。

- `int getPrefetchTileCount()`  
获取预取瓦片数。

- `int getPrefetchQueuePeak()`  
获取预取队列历史峰值（调试用）。

- `LongMap<T> getPrefetchTiles()`  
获取预取瓦片映射。

- `long getBindTime()`  
获取瓦片绑定耗时（调试用）。

- `boolean isDebugMode()`  
检查是否开启调试。

- `void setDebugMode(boolean enabled)`  
设置调试开关。

- `void setTimeProvider(TimeProvider provider)`  
设置时间提供器（调试用）。

#### 存储替换

- `void setActiveTiles(LongMap<T> map)`  
替换活跃瓦片存储。

- `void setDyingTiles(LongMap<T> map)`  
替换濒死瓦片存储。

- `void setWidths(IntIntMap map)`  
替换列宽存储。

- `void setHeights(IntIntMap map)`  
替换行高存储。

- `void setRecycledTiles(TileRecycledPool<T> pool)`  
替换瓦片回收池。

- `void setPrefetchTiles(LongMap<T> map)`  
替换预取瓦片存储。

#### 子模块访问器

- `LayoutEngine getLayoutEngine()`  
获取布局引擎。

- `TileManager<T> getTileManager()`  
获取瓦片管理器。

- `DimenManager getDimenManager()`  
获取尺寸管理器。

- `EventHandler getEventHandler()`  
获取事件处理器。

## 瓦片回收池（TileRecycledPool）

- `T get(int type)`  
按类型获取一个瓦片实例，无可用时返回 `null`。

- `void recycle(int type, T tile)`  
按类型回收瓦片。

- `void reset()`  
清空全部缓存。

- `void setRecycledTiles(IntMap<Deque<T>> map)`  
替换内部存储。

- `void moveTo(TileRecycledPool<T> pool)`  
将全部缓存迁移到目标回收池。

## 布局引擎（LayoutEngine）

- `boolean sync(float dx, float dy)`  
按像素偏移滚动视窗，返回视窗是否发生变化。正数使内容向右移动。

- `boolean seek(int column, int row, float offsetX, float offsetY)`  
跳转到指定坐标并定义原点。

- `void updateWidth(int column, int oldWidth, int newWidth, int gravity)`  
列宽变化后的视窗补偿。

- `void updateHeight(int row, int oldHeight, int newHeight, int gravity)`  
行高变化后的视窗补偿。

- `void updateSize(int column, int oldWidth, int newWidth, int hGravity, int row, int oldHeight, int newHeight, int vGravity)`  
尺寸变化后的视窗补偿，两个方向合并为一次。

- `LayoutModel getLayoutModel()`  
获取输出布局模型。

- `boolean checkLocationInBounds(int column, int row)`  
检查坐标是否在边界内。

- `boolean isEmpty()`  
检查边界是否为空。

- `boolean isAtLeftBound()`  
检查视窗是否触及左边界。

- `boolean isAtTopBound()`  
检查视窗是否触及上边界。

- `boolean isAtRightBound()`  
检查视窗是否触及右边界。

- `boolean isAtBottomBound()`  
检查视窗是否触及下边界。

- `void reset()`  
重置视窗状态。

- `boolean isHorizontalScrollEnabled()`  
检查是否开启横向滚动。

- `boolean isVerticalScrollEnabled()`  
检查是否开启纵向滚动。

- `void setHorizontalScrollEnabled(boolean enabled)`  
设置横向滚动开关。

- `void setVerticalScrollEnabled(boolean enabled)`  
设置纵向滚动开关。

- `int getWindowWidth()`  
获取视窗宽度。

- `int getWindowHeight()`  
获取视窗高度。

- `void setWindowWidth(int width)`  
设置视窗宽度。

- `void setWindowHeight(int height)`  
设置视窗高度。

- `void setTimeProvider(TimeProvider timeProvider)`  
设置时间提供器（调试用）。

> 对齐常量 `DIMEN_GRAVITY_*` 见「瓦片操作-尺寸对齐常量」；`BoundaryInterface`/`WindowInterface` 为内部接口；另提供 `min`/`max` 静态重载（避免装箱）。详见跨平台指南。

## 瓦片管理器（TileManager）

- `void in(int column, int row)`  
瓦片进入视窗，先从濒死池复用，否则创建并绑定。

- `void out(int column, int row)`  
瓦片离开视窗，移入濒死池（未启用濒死区时直接回收）。

- `T obtain(int type)`  
从回收池获取指定类型瓦片，无可用时通过适配器创建。

- `void recycle(T tile)`  
回收瓦片到回收池。

- `void update(int column, int row)`  
刷新指定瓦片。

- `void updateRange(int left, int top, int right, int bottom)`  
刷新区域内瓦片。

- `void updateColumn(int column)`  
刷新整列。

- `void updateRow(int row)`  
刷新整行。

- `void resizeTile(int column, int row, int width, int height)`  
更新已有瓦片的尺寸记录。

- `T getActiveTile(int column, int row)`  
获取活跃瓦片。

- `int getActiveTileCount()`  
获取活跃瓦片数。

- `int getRecycledTileCount()`  
获取回收池瓦片数。

- `int getDyingTileCount()`  
获取濒死瓦片数。

- `LongMap<T> getDyingTiles()`  
获取濒死瓦片映射。

- `void setActiveTiles(LongMap<T> map)`  
替换活跃瓦片存储。

- `void setDyingTiles(LongMap<T> map)`  
替换濒死瓦片存储。

- `void setRecycledTiles(TileRecycledPool<T> pool)`  
替换瓦片回收池。

- `void clearAll()`  
清理全部瓦片与缓存。

- `void clearActiveAndDying()`  
清理活跃与濒死瓦片（跳转时调用）。

#### 缓存区（濒死区与预取区）

##### 濒死区

- `void diffDying(int colStart, int rowStart, int colEnd, int rowEnd)`  
清理超出濒死区的瓦片。

- `int getDyingLeft()`  
获取濒死区左边界。

- `int getDyingTop()`  
获取濒死区上边界。

- `int getDyingRight()`  
获取濒死区右边界。

- `int getDyingBottom()`  
获取濒死区下边界。

- `int getDyingExpand()`  
获取濒死区扩展层数。

- `void setDyingExpand(int expand)`  
设置濒死区扩展层数，必须大于0。

- `boolean isDyingEnabled()`  
检查是否启用濒死区。

- `void setDyingEnabled(boolean enabled)`  
设置濒死区开关，关闭时立即回收全部濒死瓦片。

##### 预取区

- `void diffPrefetch(int colStart, int rowStart, int colEnd, int rowEnd)`  
视窗计算完毕后基于运动方向重新规划预取：淘汰方向矩形外的预取瓦片，并把前方条带坐标入队（三池均未持有才入队）。

- `boolean drainPrefetch()`  
消费预取队列，单次最多创建 `prefetchPerFrame`（帧预算）个瓦片，返回队列是否仍有剩余。

- `int getPrefetchLeft()`  
获取预取区左边界（仅朝运动方向扩展，其余三边贴合视窗）。

- `int getPrefetchTop()`  
获取预取区上边界。

- `int getPrefetchRight()`  
获取预取区右边界。

- `int getPrefetchBottom()`  
获取预取区下边界。

- `int getPrefetchExpand()`  
获取预取扩展层数。

- `void setPrefetchExpand(int expand)`  
设置预取扩展层数，必须大于0，默认1。

- `boolean isPrefetchEnabled()`  
检查是否启用预取。

- `void setPrefetchEnabled(boolean enabled)`  
设置启用预取（默认开启），关闭时清空预取区并重置跟踪状态。

- `int getPrefetchPerFrame()`  
获取每帧预取数量上限（帧预算），默认 8。

- `void setPrefetchPerFrame(int count)`  
设置每帧预取数量上限，必须大于0，非法参数无响应。

- `int getPrefetchTileCount()`  
获取预取瓦片数。

- `int getPrefetchQueuePeak()`  
获取预取队列历史峰值（调试用，随预取区生命周期归零）。

- `boolean hasPrefetchPending()`  
检查预取队列是否还有待消费的坐标。

- `void setPrefetchTiles(LongMap<T> map)`  
替换预取瓦片存储。

> 瓦片 ID 编码与生命周期流程详见跨平台指南。

## 尺寸管理器（DimenManager）

- `int getTileWidth(int column)`  
按优先级查询列宽。

- `int getTileHeight(int row)`  
按优先级查询行高。

- `void setTileWidth(int column, int width, int gravity)`  
设置列宽并触发布局扰动。

- `void setTileHeight(int row, int height, int gravity)`  
设置行高并触发布局扰动。

- `void setTileSize(int column, int width, int hGravity, int row, int height, int vGravity)`  
同时设置列宽与行高。

- `void deleteTileWidth(int column, int gravity)`  
删除列的自定义宽度。

- `void deleteTileHeight(int row, int gravity)`  
删除行的自定义高度。

- `int getDefaultTileWidth()`  
获取默认列宽。

- `int getDefaultTileHeight()`  
获取默认行高。

- `void setDefaultTileWidth(int width)`  
设置默认列宽，必须大于0。

- `void setDefaultTileHeight(int height)`  
设置默认行高，必须大于0。

- `TileDimenProvider getDimenProvider()`  
获取尺寸提供者。

- `void setDimenProvider(TileDimenProvider dimenProvider)`  
设置尺寸提供者。

- `void setWidths(IntIntMap map)`  
替换列宽存储。

- `void setHeights(IntIntMap map)`  
替换行高存储。

- `void clear()`  
清空全部自定义尺寸。

> 尺寸优先级与修改流程见「瓦片操作-尺寸操作」。

## 事件处理器（EventHandler）

- `void handleTouchEvent(MotionEvent event)`  
处理触摸事件，`ACTION_DOWN` 时自动停止惯性滚动。内置 `GestureDetector` 与 `ScaleGestureDetector`：第二根手指落下即进入快照缩放模式，两指全部抬起后结算（见「缩放」章节）。

- `void computeScroll()`  
驱动惯性滚动（在视图 `computeScroll` 中调用）。

- `void resetAnimator()`  
停止滚动动画。

- `void requestDisallowInterceptTouchEvent(boolean disallowIntercept)`  
控制触摸拦截。

- `boolean isInteractingWithView()`  
检查是否正在交互（滚动/捏合中）。

- `void reset()`  
重置事件状态。

> `Callback` 接口额外声明缩放生命周期：`isZooming()` / `beginZoom()` / `updateZoom(relativeScale, focusX, focusY)` / `endZoom()`，由 `TileCoreService` 实现。

## 尺寸提供者（TileDimenProvider）

- `int getTileWidth(int column)`  
查询列宽。

- `int getTileHeight(int row)`  
查询行高。

- `void setTileWidth(int column, int width)`  
设置列宽。

- `void setTileHeight(int row, int height)`  
设置行高。

- `void deleteTileWidth(int column)`  
删除列宽。

- `void deleteTileHeight(int row)`  
删除行高。

## 可测量尺寸提供者（MeasurableDimenProvider）

- `boolean isMinDefault()`  
检查是否启用最小默认尺寸。

- `void setMinDefault(boolean minDefault)`  
设置最小默认尺寸，内容小于默认尺寸时使用默认尺寸。

- `void full()`  
测量适配器全范围并生成尺寸表。

- `void measure(int colStart, int rowStart, int colEnd, int rowEnd)`  
测量指定范围并生成尺寸表。

- `void reset()`  
清空尺寸表。

- `void clearRecycledTiles()`  
清空测量用回收池。

- `void setWidths(IntIntMap map)`  
替换列宽存储。

- `void setHeights(IntIntMap map)`  
替换行高存储。

- `void setRecycledTiles(TileRecycledPool<TileCoreService.BaseTileHolder> pool)`  
替换测量用回收池。

> 构造：`new MeasurableDimenProvider(adapter)` 或 `new MeasurableDimenProvider(width, height, adapter)`。简易测量工具，不建议大数据量场景使用。尺寸查询 API 同 `TileDimenProvider`。

## 可测量接口（Measurable）

- `void measure(int widthMeasureSpec, int heightMeasureSpec, int[] out)`  
测量内容尺寸，结果写入 `out[0]`（宽）与 `out[1]`（高）。

## 拖拽调整视图（DragResizerView）

- `int getIndicatorSize()`  
获取指示器尺寸。

- `void setIndicatorSize(int indicatorSize)`  
设置指示器尺寸。

- `int getDirection()`  
获取当前拖拽方向。

- `void setCallback(Callback callback)`  
设置拖拽回调。

> 方向常量：`DIRECTION_NONE`（-1）、`DIRECTION_START`（0，左上角）、`DIRECTION_END`（1，右下角）。`Callback`：`onDrag(direction, width, height, gravity)` / `getTileWidth()` / `getTileHeight()`。

## 时间提供器（TimeProvider）

- `long nanoTime()`  
获取墙钟时间（纳秒，单调）。

- `long cpuNanoTime()`  
获取当前线程累计 CPU 时间（纳秒），无 CPU 时钟时退化为墙钟。

> 内置实现：`DefaultTimeProvider`，`nanoTime` 使用 `System.nanoTime`，`cpuNanoTime` 使用 `Debug.threadCpuTimeNanos`。

## 调试层（DebugLayer）

- `void start()`  
开始统计，注册帧回调。

- `void end()`  
停止统计，移除帧回调。

- `void startDraw()`  
标记一帧绘制的开始。

- `void draw(Canvas canvas)`  
绘制调试面板，并结算本帧绘制耗时。

> `Callback`：`getActiveTileCount` / `getRecycledTileCount` / `getDyingTileCount` / `getPrefetchTileCount` / `getPrefetchQueuePeak` / `getBounds` / `getLayoutModel` / `postInvalidateOnAnimation` / `getBindTime` / `getLayoutTime`。集成方式见平台扩展指南。

## 长整型映射（LongMap）

- `V get(long key)`  
获取指定键的值。

- `void put(long key, V value)`  
存入键值对。

- `V remove(long key)`  
删除指定键并返回旧值。

- `int size()`  
获取元素个数。

- `boolean containsKey(long key)`  
检查是否包含指定键。

- `void clear()`  
清空全部元素。

- `Iterator<V> iterator()`  
获取迭代器（正向）。

- `Iterator<V> iterator(boolean deleteMode)`  
获取迭代器，`deleteMode` 为 true 时以删除模式遍历（便于边遍历边删除）。

> 内置实现：`LongMapOpenHashMap`（默认，开地址哈希表，无装箱，构造可传期望容量）、`LongMapSparseArray`（基于 `LongSparseArray`，有序存储）、`LongMapHashMap`（基于 `HashMap`，大批量删除更优）。活跃/濒死瓦片使用本接口存储。

## 长整型队列（LongQueue）

- `int size()`  
获取队列长度。

- `void clear()`  
清空全部元素。

- `void enqueue(long value)`  
队尾入队。

- `long dequeue()`  
队首出队（空队列调用会抛异常，出队前应先检查 `size()`）。

> 内置实现：`LongQueueArrayFIFO`（默认，环形数组，2 的幂容量，自动扩容）。预取队列使用本接口存储待创建坐标。

## 整型映射（IntMap）

- `V get(int key)`  
获取指定键的值。

- `void put(int key, V value)`  
存入键值对。

- `V remove(int key)`  
删除指定键并返回旧值。

- `int size()`  
获取元素个数。

- `boolean containsKey(int key)`  
检查是否包含指定键。

- `void clear()`  
清空全部元素。

- `Iterator<V> iterator()`  
获取迭代器（正向）。

- `Iterator<V> iterator(boolean deleteMode)`  
获取迭代器，`deleteMode` 为 true 时以删除模式遍历（便于边遍历边删除）。

> 内置实现：`IntMapOpenHashMap`（默认）、`IntMapSparseArray`、`IntMapHashMap`，特性同 `LongMap`。瓦片回收池使用本接口按类型分组存储。

## 整型键值映射（IntIntMap）

- `int get(int key)`  
获取指定键的值。

- `int get(int key, int defaultValue)`  
获取指定键的值，不存在时返回默认值。

- `void put(int key, int value)`  
存入键值对。

- `int remove(int key)`  
删除指定键并返回旧值。

- `int size()`  
获取元素个数。

- `boolean containsKey(int key)`  
检查是否包含指定键。

- `void clear()`  
清空全部元素。

- `Iterator iterator()`  
获取迭代器（正向）。

- `Iterator iterator(boolean deleteMode)`  
获取迭代器，`deleteMode` 为 true 时以删除模式遍历（便于边遍历边删除）。

> 内置实现：`IntIntMapOpenHashMap`（默认）、`IntIntMapSparseArray`、`IntIntMapHashMap`，特性同 `LongMap`。列宽/行高使用本接口存储。
