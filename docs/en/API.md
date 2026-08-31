# API Reference

## TileView / TileLayout

> Note: `TileView` (self-drawn) and `TileLayout` (native View) use different rendering paradigms, but their public APIs are essentially identical. Both are referred to as "the view" below; for touch-interaction differences see the "Touch Events" section.

### Basic Operations

- `void offset(float dx, float dy)`
Scrolls the content by a distance. Positive values scroll right, negative values scroll left.

- `void seek(int column, int row, float offsetX, float offsetY)`
Jumps to the given coordinate with an initial pixel offset (scroll distance).

- `void seek(int column, int row)`
Jumps to the given coordinate.

- `void snap()`
Call after the adapter bounds change; pulls the window back from an illegal range into the legal one (edge alignment).

- `boolean isHorizontalScrollEnabled()`
Checks whether horizontal scrolling is enabled.

- `boolean isVerticalScrollEnabled()`
Checks whether vertical scrolling is enabled.

- `void setHorizontalScrollEnabled(boolean horizontalScrollEnabled)`
Enables or disables horizontal scrolling.

- `void setVerticalScrollEnabled(boolean verticalScrollEnabled)`
Enables or disables vertical scrolling.

### Zooming

Zoom is a **render-layer** transformation; the engine always works in unscaled logical space: `scaleFactor` only appears as a multiplication at draw time and in the division `windowWidth = bounds.width() / scaleFactor`. The semantics of scrolling, seeking and size changes are identical at any zoom level.

- `void zoom(float scaleFactor)`
Zooms to the given factor (`focus = 0,0`, i.e. the window origin), automatically clamped between the min and max scale factors.

- `void zoom(float scaleFactor, float focusX, float focusY, float dx, float dy)`
Zooms to the given factor. `(focusX, focusY)` is the screen-space focal point (zoom center, in view coordinates); `dx/dy` is an extra screen-space translation. Semantics: first zoom to `scaleFactor` around the focus, then translate.

- `void zoomBy(float relativeScale, float focusX, float focusY)`
Relative zoom based on the current factor, e.g. `zoomBy(2f, cx, cy)` doubles the size. Uses `(focusX, focusY)` as the focus, takes effect immediately and does not go through the snapshot.

- `float getScaleFactor()`
Gets the current scale factor, default 1.

- `float getMinScaleFactor()` / `void setMinScaleFactor(float scale)`
Gets/sets the minimum scale factor, default 0.5, must be greater than 0.

- `float getMaxScaleFactor()` / `void setMaxScaleFactor(float scale)`
Gets/sets the maximum scale factor, default 2, must be greater than the minimum scale factor.

- `boolean isZooming()`
Whether the view is in two-finger pinch-zoom (snapshot) mode. During zooming the layout engine is completely frozen, and `getScaleFactor()` returns the value from when the zoom started.

- `boolean isZoomEnabled()` / `void setZoomEnabled(boolean enabled)`
Two-finger pinch-zoom switch, **disabled by default** (`zoom` / `zoomBy` manual zooming is not affected by this switch). Disabling immediately abandons any in-progress zoom session.

- `void cancelZoom()`
Abandons the in-progress zoom session: all accumulated input is discarded and the picture returns to the state at the start of the zoom. Window size changes, seeks and size changes trigger this automatically.

> **Two-finger gesture**: `EventHandler` has a built-in `ScaleGestureDetector`. The view enters snapshot zoom mode as soon as a second finger lands; while pinching the layout engine is not touched at all — the snapshot is merely panned and scaled; the result is settled in one shot after both fingers lift. After one finger lifts the view stays in zoom mode and the remaining fingers keep dragging. Zooming and dragging are not mutually exclusive; they share the same accumulation channel.

- `Adapter getAdapter()`
Gets the adapter.

- `void setAdapter(Adapter adapter)`
Sets the adapter. Replacing the adapter wipes all content, including the dying zone and other caches.

- `boolean isEmpty()`
Checks whether the adapter bounds are empty (left-top > right-bottom).

- `boolean isAtLeftBound()`
Checks whether the window is touching the adapter's left bound.

- `boolean isAtTopBound()`
Checks whether the window is touching the adapter's top bound.

- `boolean isAtRightBound()`
Checks whether the window is touching the adapter's right bound.

- `boolean isAtBottomBound()`
Checks whether the window is touching the adapter's bottom bound.

- `boolean isDebugMode()`
Whether the debug panel is enabled.

- `void setDebugMode(boolean enabled)`
Enables or disables the debug panel.

### Utility Methods

- `float getTileX(int column)`
Computes the starting X coordinate of the target column relative to the window's top-left corner (padding included).

- `float getTileY(int row)`
Computes the starting Y coordinate of the target row relative to the window's top-left corner (padding included).

- `int findColumn(float x)`
Finds the column that contains the given coordinate (padding included).

- `int findRow(float y)`
Finds the row that contains the given coordinate (padding included).

- `LayoutModel getLayoutModel()`
Gets the current layout model. It is not a snapshot — do not modify it; call `newInstance` to create a snapshot.

- `void setTileEventListener(TileEventListener<TileHolder> tileEventListener)`
Sets the tile event listener.

### Cache Zones (Dying Zone & Prefetch Zone)

The two cache zones complement each other: **the dying zone holds what just left behind; the prefetch zone grabs what is about to arrive ahead**. The dying zone keeps tiles that just left the window so that scrolling back can skip re-binding; the prefetch zone loads tiles ahead of the motion direction in advance, so they are promoted directly when entering the window.

#### Dying Zone

The dying zone is a buffer ring expanding outward from the window: tiles leaving the window are parked here and may be recycled after a short stay.

- `int getDyingExpand()`
Gets the dying-zone expansion layer count.

- `void setDyingExpand(int expand)`
Sets the dying-zone expansion layer count, must be >= 1.

- `boolean isDyingEnabled()`
Checks whether the dying zone is enabled.

- `void setDyingEnabled(boolean enabled)`
Enables or disables the dying zone; disabling recycles all tiles immediately.

#### Prefetch Zone

The prefetch zone is the mirror complement of the dying zone: it **predicts by motion direction**, expanding a strip only toward the front of the window and preloading tiles in batches, which are promoted directly upon entering the window — avoiding one-shot loading stalls during scrolling. **Enabled by default**, consuming at most 8 prefetch tasks per frame.

- `boolean isPrefetchEnabled()`
Checks whether prefetching is enabled.

- `void setPrefetchEnabled(boolean enabled)`
Enables or disables prefetching (enabled by default); disabling clears the prefetch zone.

- `int getPrefetchExpand()`
Gets the prefetch expansion layer count.

- `void setPrefetchExpand(int expand)`
Sets the prefetch expansion layer count, must be > 0, default 1.

- `int getPrefetchPerFrame()`
Gets the per-frame prefetch limit (frame budget, held by the tile manager), default 8.

- `void setPrefetchPerFrame(int count)`
Sets the per-frame prefetch limit, must be > 0. Larger values prefetch faster but raise the per-frame create/bind cost; smaller values are more conservative — consider lowering on low-end devices.

- `int getPrefetchTileCount()`
Gets the number of prefetched tiles (for debugging).

- `int getPrefetchQueuePeak()`
Gets the historical peak of the prefetch queue (for debugging, resets to zero with the prefetch zone lifecycle).

### Update Operations

- `void update(int column, int row)`
Refreshes the tile of the given cell.

- `void updateRange(int left, int top, int right, int bottom)`
Refreshes all tiles within the rectangle (closed interval), automatically intersected with the dying zone.

- `void updateColumn(int column)`
Refreshes the entire column.

- `void updateRow(int row)`
Refreshes the entire row.

- `void updateAll()`
Refreshes all tiles, equivalent to re-running `seek` in place.

### Tile Operations

- `TileHolder getActiveTile(int column, int row)`
Gets the currently active (in-window) tile holder; returns `null` if not in the window.

#### Size Operations

- `int getTileWidth(int column)`
Queries the width of the given column.

- `int getTileHeight(int row)`
Queries the height of the given row.

- `void setTileWidth(int column, int width)`
Sets the width of the given column, must be > 0, triggers a layout disturbance.

- `void setTileWidth(int column, int width, int gravity)`
Sets the width of the given column (must be > 0) with an alignment direction.

- `void setTileHeight(int row, int height)`
Sets the height of the given row, must be > 0, triggers a layout disturbance.

- `void setTileHeight(int row, int height, int gravity)`
Sets the height of the given row (must be > 0) with an alignment direction.

- `void setTileSize(int column, int width, int row, int height)`
Sets column width and row height at once (both must be > 0); the layout disturbance happens only once.

- `void setTileSize(int column, int width, int hGravity, int row, int height, int vGravity)`
Sets column width and row height at once (both must be > 0) with separate horizontal/vertical alignment directions.

- `void deleteTileWidth(int column)`
Deletes the custom width of the given column, falling back to the default size.

- `void deleteTileWidth(int column, int gravity)`
Deletes the custom width of the given column with an alignment direction.

- `void deleteTileHeight(int row)`
Deletes the custom height of the given row, falling back to the default size.

- `void deleteTileHeight(int row, int gravity)`
Deletes the custom height of the given row with an alignment direction.

- `int getDefaultTileWidth()`
Gets the default column width.

- `int getDefaultTileHeight()`
Gets the default row height.

- `void setDefaultTileWidth(int width)`
Sets the default column width, must be > 0.

- `void setDefaultTileHeight(int height)`
Sets the default row height, must be > 0.

> Size priority: individually set size > size from `TileDimenProvider` > default size. When a size changes, existing tiles inside the window are refreshed together, and the offset is automatically compensated along the alignment direction to keep the visuals stable.

#### Size Alignment Constants

- `DIMEN_GRAVITY_START` (-1): the left/top side stays put; the right/bottom side expands or shrinks.
- `DIMEN_GRAVITY_CENTER` (0): both sides expand or shrink evenly.
- `DIMEN_GRAVITY_END` (1): the right/bottom side stays put; the left/top side expands or shrinks.

#### Size Provider

- `TileDimenProvider getDimenProvider()`
Gets the size provider.

- `void setDimenProvider(TileDimenProvider dimenProvider)`
Sets the size provider. Size queries resolve above the default size but below individually set values.

### Container Replacement

- `void setActiveTiles(LongMap<TileHolder> map)`
Replaces the active-tile storage.

- `void setDyingTiles(LongMap<TileHolder> map)`
Replaces the dying-tile storage.

- `void setWidths(IntIntMap map)`
Replaces the column-width storage.

- `void setHeights(IntIntMap map)`
Replaces the row-height storage.

- `void setRecycledTiles(TileRecycledPool<TileHolder> pool)`
Replaces the tile recycling pool.

- `void setPrefetchTiles(LongMap<TileHolder> map)`
Replaces the prefetch-tile storage.

> When replacing, data migrates automatically into the new container and the old one is cleared.

### Touch Events

- `boolean isInteractingWithView()`
Checks whether the user is currently interacting with the view (scrolling).

- `void requestDisallowInterceptTouchEvent(boolean disallowIntercept)`
Asks the parent container not to intercept touch events.

- `void resetAnimator()`
Stops the current fling animation (usually called when leaving the page).

#### TileView

Touch events hit tiles by coordinate first, then are forwarded to the matching tile holder:

- `long getLongPressTimeout()`
Gets the long-press timeout (ms), default 400.

- `void setLongPressTimeout(long longPressTimeout)`
Sets the long-press timeout (ms).

## Adapter (TileAdapter)

- `int getLeftBound()`
Gets the left bound (minimum column), default `Integer.MIN_VALUE`.

- `int getTopBound()`
Gets the top bound (minimum row), default `Integer.MIN_VALUE`.

- `int getRightBound()`
Gets the right bound (maximum column), default `Integer.MAX_VALUE`.

- `int getBottomBound()`
Gets the bottom bound (maximum row), default `Integer.MAX_VALUE`.

- `abstract T onCreateTileHolder(int type)`
Creates a tile holder of the given type; returning `null` means a sparse tile (not created).

- `abstract void onBindTileHolder(T holder, int column, int row)`
Binds data to a tile.

- `int getTileType(int column, int row)`
Returns the tile type, default 0. Tiles of the same type are recycled and reused from the pool.

- `boolean isEmpty()`
Checks whether the bounds are empty (left > right or top > bottom).

## Tile Event Listener (TileEventListener)

- `void onBeforeLayout()`
Called before a layout cycle starts, before any tile is laid out.

- `void onAfterLayout()`
Called after a layout cycle ends, once tiles have been laid out.

- `void onTileIn(T holder, int column, int row)`
A tile entered the window and finished layout.

- `void onTileOut(T holder, int column, int row)`
A tile left the window and entered the dying zone (if enabled).

- `void onTileRecycled(T holder, int column, int row)`
A tile was recycled into the pool.

- `void onTilePrefetched(T holder, int column, int row)`
A tile was prefetched (created and bound, not yet in the window); do pre-entry preparation here (default no-op).

## Layout Model (LayoutModel)

- `int colStart` / `int rowStart` — first visible column/row (closed interval)
- `int colEnd` / `int rowEnd` — last visible column/row (closed interval)
- `float offsetX` / `float offsetY` — overall content offset of the window
- `int totalWidth` / `int totalHeight` — total content size in the window
- `long syncTime` — duration of the most recent sync (for debugging)

- `LayoutModel newInstance()`
Creates a copy of the current state.

- `void copyTo(LayoutModel model)`
Copies the state into the given model.

- `void reset()`
Resets to the initial state.

## Core Service (TileCoreService)

- `static long getTileId(int column, int row)`
Encodes a column/row coordinate into a unique tile ID.

- `static int getColumn(long id)`
Extracts the column index from a tile ID.

- `static int getRow(long id)`
Extracts the row index from a tile ID.

#### Dispatch Operations

- `void sync(float dx, float dy)`
Scrolls the content by a pixel offset.

- `void seek(int column, int row, float offsetX, float offsetY)`
Jumps to the given coordinate.

- `void snap()`
Snaps the window back into the legal range.

- `void update(int column, int row)`
Refreshes the given tile.

- `void updateRange(int left, int top, int right, int bottom)`
Refreshes tiles within a region.

- `void updateColumn(int column)`
Refreshes an entire column.

- `void updateRow(int row)`
Refreshes an entire row.

- `void updateAll()`
Refreshes all tiles.

- `void resetAnimator()`
Stops the fling animation.

- `void computeScroll()`
Drives fling scrolling.

- `void handleTouchEvent(MotionEvent event)`
Handles touch events.

- `void requestDisallowInterceptTouchEvent(boolean disallowIntercept)`
Asks the parent container not to intercept touch events.

- `boolean isInteractingWithView()`
Checks whether the user is interacting with the view.

- `void reset()`
Resets all state (called when the adapter is replaced).

#### Zooming

The zoom APIs mirror the view-level methods one to one; a session-level interface is additionally provided so custom renderers can plug into snapshot zooming:

- `void zoom(float scaleFactor, float focusX, float focusY, float dx, float dy)`
Zooms to the given factor (see the view-level section for semantics). Manual zooming abandons any in-progress gesture zoom session.

- `void zoomBy(float relativeScale, float focusX, float focusY)`
Relative zoom based on the current factor.

- `float getScaleFactor()`
Gets the current scale factor.

- `float getMinScaleFactor()` / `void setMinScaleFactor(float scale)`
Gets/sets the minimum scale factor.

- `float getMaxScaleFactor()` / `void setMaxScaleFactor(float scale)`
Gets/sets the maximum scale factor.

- `boolean isZooming()`
Whether the view is in snapshot zoom mode. While in this mode: `sync` intercepts input and accumulates it into the snapshot, `seek` abandons the zoom then jumps normally, `drainPrefetch` pauses tile creation.

- `boolean isZoomEnabled()` / `void setZoomEnabled(boolean enabled)`
Two-finger pinch-zoom switch (disabled by default; manual zooming is not affected).

- `void cancelZoom()`
Abandons the in-progress zoom session.

- `boolean beginZoom()`
Requests to enter snapshot zoom mode: captures a renderer snapshot and freezes the layout engine. Only succeeds then enters; returns `false` on failure (the caller falls back to immediate zoom or ignores the gesture).

- `void updateZoom(float relativeScale, float focusX, float focusY)`
Accumulates pinch input: `relativeScale` is the incremental factor relative to the previous call, `(focusX, focusY)` is the screen focal point.

- `void translateZoom(float dx, float dy)`
Accumulates screen-space pixel translation (the internal channel through which intercepted `sync` input flows).

- `void endZoom()`
Settles the zoom session: releases the snapshot, then applies all accumulated input to the engine in one shot.

- `float getZoomScale()`
Gets the snapshot's current relative scale (relative to the picture at zoom start).

- `float getZoomTranslateX()` / `float getZoomTranslateY()`
Gets the snapshot's current screen translation (pixels).

- `void setZoomInterface(TileCoreService.ZoomInterface zoomInterface)`
Sets the renderer snapshot interface. The renderer **must register** it to enable snapshot zooming; without registration the two-finger gesture never enters zoom mode.

> **ZoomInterface** (implemented by the renderer): `captureZoomSnapshot()` captures the current picture into a bitmap and switches to snapshot rendering; `onZoomUpdate(scale, translateX, translateY)` updates the snapshot transform (the render order is equivalent to `translate` first, then `scale`); `releaseZoomSnapshot()` releases the snapshot and restores normal rendering. The built-in `TileView`/`TileLayout` implement this via `ZoomSnapshot`; for custom renderers see `widget/zoom/ZoomSnapshot`.

#### Sizes & Coordinates

- `void setBounds(int left, int top, int right, int bottom)`
Sets the window bounds.

- `Rect getBounds()`
Gets the window bounds.

- `float getTileX(int column)`
Computes the X coordinate of the given column relative to the window.

- `float getTileY(int row)`
Computes the Y coordinate of the given row relative to the window.

- `int findColumn(float x)`
Finds the column containing the given X coordinate.

- `int findRow(float y)`
Finds the row containing the given Y coordinate.

- `int getTileWidth(int column)`
Queries the width of the given column.

- `int getTileHeight(int row)`
Queries the height of the given row.

- `void setTileWidth(int column, int width, int gravity)`
Sets the width of the given column.

- `void setTileHeight(int row, int height, int gravity)`
Sets the height of the given row.

- `void setTileSize(int column, int width, int hGravity, int row, int height, int vGravity)`
Sets column width and row height at once.

- `void deleteTileWidth(int column, int gravity)`
Deletes the custom width of the given column.

- `void deleteTileHeight(int row, int gravity)`
Deletes the custom height of the given row.

- `int getDefaultTileWidth()`
Gets the default column width.

- `int getDefaultTileHeight()`
Gets the default row height.

- `void setDefaultTileWidth(int width)`
Sets the default column width.

- `void setDefaultTileHeight(int height)`
Sets the default row height.

- `TileDimenProvider getDimenProvider()`
Gets the size provider.

- `void setDimenProvider(TileDimenProvider dimenProvider)`
Sets the size provider.

#### Queries

- `LayoutModel getLayoutModel()`
Gets the layout model.

- `T getActiveTile(int column, int row)`
Gets the active tile.

- `boolean isEmpty()`
Checks whether the bounds are empty.

- `boolean isAtLeftBound()`
Checks whether the window is touching the left bound.

- `boolean isAtTopBound()`
Checks whether the window is touching the top bound.

- `boolean isAtRightBound()`
Checks whether the window is touching the right bound.

- `boolean isAtBottomBound()`
Checks whether the window is touching the bottom bound.

- `int getActiveTileCount()`
Gets the active tile count.

- `int getRecycledTileCount()`
Gets the recycled tile count.

- `int getDyingTileCount()`
Gets the dying tile count.

- `LongMap<T> getDyingTiles()`
Gets the dying-tile map.

- `boolean isPrefetchEnabled()`
Checks whether prefetching is enabled.

- `void setPrefetchEnabled(boolean enabled)`
Enables or disables prefetching (enabled by default).

- `boolean hasPrefetchPending()`
Checks whether the prefetch queue still has coordinates pending.

- `int getPrefetchTileCount()`
Gets the prefetched tile count.

- `int getPrefetchQueuePeak()`
Gets the historical peak of the prefetch queue (for debugging).

- `LongMap<T> getPrefetchTiles()`
Gets the prefetch-tile map.

- `long getBindTime()`
Gets the tile binding duration (for debugging).

- `boolean isDebugMode()`
Checks whether debug mode is on.

- `void setDebugMode(boolean enabled)`
Sets the debug switch.

- `void setTimeProvider(TimeProvider provider)`
Sets the time provider (for debugging).

#### Storage Replacement

- `void setActiveTiles(LongMap<T> map)`
Replaces the active-tile storage.

- `void setDyingTiles(LongMap<T> map)`
Replaces the dying-tile storage.

- `void setWidths(IntIntMap map)`
Replaces the column-width storage.

- `void setHeights(IntIntMap map)`
Replaces the row-height storage.

- `void setRecycledTiles(TileRecycledPool<T> pool)`
Replaces the tile recycling pool.

- `void setPrefetchTiles(LongMap<T> map)`
Replaces the prefetch-tile storage.

#### Sub-module Accessors

- `LayoutEngine getLayoutEngine()`
Gets the layout engine.

- `TileManager<T> getTileManager()`
Gets the tile manager.

- `DimenManager getDimenManager()`
Gets the dimension manager.

- `EventHandler getEventHandler()`
Gets the event handler.

## Tile Recycling Pool (TileRecycledPool)

- `T get(int type)`
Gets a tile instance of the given type; returns `null` when none is available.

- `void recycle(int type, T tile)`
Recycles a tile by type.

- `void reset()`
Clears the entire cache.

- `void setRecycledTiles(IntMap<Deque<T>> map)`
Replaces the internal storage.

- `void moveTo(TileRecycledPool<T> pool)`
Migrates the entire cache into the target recycling pool.

## Layout Engine (LayoutEngine)

- `boolean sync(float dx, float dy)`
Scrolls the window by a pixel offset, returning whether the window changed. Positive values move the content right.

- `boolean seek(int column, int row, float offsetX, float offsetY)`
Jumps to the given coordinate and defines the origin.

- `void updateWidth(int column, int oldWidth, int newWidth, int gravity)`
Window compensation after a column width change.

- `void updateHeight(int row, int oldHeight, int newHeight, int gravity)`
Window compensation after a row height change.

- `void updateSize(int column, int oldWidth, int newWidth, int hGravity, int row, int oldHeight, int newHeight, int vGravity)`
Window compensation after a size change, both axes merged into one.

- `LayoutModel getLayoutModel()`
Gets the output layout model.

- `boolean checkLocationInBounds(int column, int row)`
Checks whether a coordinate is within the bounds.

- `boolean isEmpty()`
Checks whether the bounds are empty.

- `boolean isAtLeftBound()`
Checks whether the window is touching the left bound.

- `boolean isAtTopBound()`
Checks whether the window is touching the top bound.

- `boolean isAtRightBound()`
Checks whether the window is touching the right bound.

- `boolean isAtBottomBound()`
Checks whether the window is touching the bottom bound.

- `void reset()`
Resets the window state.

- `boolean isHorizontalScrollEnabled()`
Checks whether horizontal scrolling is enabled.

- `boolean isVerticalScrollEnabled()`
Checks whether vertical scrolling is enabled.

- `void setHorizontalScrollEnabled(boolean enabled)`
Sets the horizontal scrolling switch.

- `void setVerticalScrollEnabled(boolean enabled)`
Sets the vertical scrolling switch.

- `int getWindowWidth()`
Gets the window width.

- `int getWindowHeight()`
Gets the window height.

- `void setWindowWidth(int width)`
Sets the window width.

- `void setWindowHeight(int height)`
Sets the window height.

- `void setTimeProvider(TimeProvider timeProvider)`
Sets the time provider (for debugging).

> The alignment constants `DIMEN_GRAVITY_*` are listed under "Tile Operations - Size Alignment Constants"; `BoundaryInterface`/`WindowInterface` are internal interfaces; `min`/`max` static overloads are also provided (to avoid boxing). See the Cross-Platform Porting Guide for details.

## Tile Manager (TileManager)

- `void in(int column, int row)`
A tile enters the window: reused from the dying pool first, otherwise created and bound.

- `void out(int column, int row)`
A tile leaves the window and moves into the dying pool (recycled directly when the dying zone is disabled).

- `T obtain(int type)`
Gets a tile of the given type from the recycling pool; creates one through the adapter when none is available.

- `void recycle(T tile)`
Recycles a tile into the pool.

- `void update(int column, int row)`
Refreshes the given tile.

- `void updateRange(int left, int top, int right, int bottom)`
Refreshes tiles within a region.

- `void updateColumn(int column)`
Refreshes an entire column.

- `void updateRow(int row)`
Refreshes an entire row.

- `void resizeTile(int column, int row, int width, int height)`
Updates the size record of an existing tile.

- `T getActiveTile(int column, int row)`
Gets the active tile.

- `int getActiveTileCount()`
Gets the active tile count.

- `int getRecycledTileCount()`
Gets the recycled-pool tile count.

- `int getDyingTileCount()`
Gets the dying tile count.

- `LongMap<T> getDyingTiles()`
Gets the dying-tile map.

- `void setActiveTiles(LongMap<T> map)`
Replaces the active-tile storage.

- `void setDyingTiles(LongMap<T> map)`
Replaces the dying-tile storage.

- `void setRecycledTiles(TileRecycledPool<T> pool)`
Replaces the tile recycling pool.

- `void clearAll()`
Clears all tiles and caches.

- `void clearActiveAndDying()`
Clears the active and dying tiles (called on seek).

#### Cache Zones (Dying Zone & Prefetch Zone)

##### Dying Zone

- `void diffDying(int colStart, int rowStart, int colEnd, int rowEnd)`
Clears tiles that fall outside the dying zone.

- `int getDyingLeft()`
Gets the left bound of the dying zone.

- `int getDyingTop()`
Gets the top bound of the dying zone.

- `int getDyingRight()`
Gets the right bound of the dying zone.

- `int getDyingBottom()`
Gets the bottom bound of the dying zone.

- `int getDyingExpand()`
Gets the dying-zone expansion layer count.

- `void setDyingExpand(int expand)`
Sets the dying-zone expansion layer count, must be > 0.

- `boolean isDyingEnabled()`
Checks whether the dying zone is enabled.

- `void setDyingEnabled(boolean enabled)`
Sets the dying-zone switch; disabling immediately recycles all dying tiles.

##### Prefetch Zone

- `void diffPrefetch(int colStart, int rowStart, int colEnd, int rowEnd)`
After window calculation, re-plans prefetching based on the motion direction: evicts prefetched tiles outside the direction rectangle and enqueues the strip of coordinates ahead (enqueued only when held by none of the three pools).

- `boolean drainPrefetch()`
Consumes the prefetch queue, creating at most `prefetchPerFrame` (frame budget) tiles per call; returns whether the queue still has leftovers.

- `int getPrefetchLeft()`
Gets the left bound of the prefetch zone (expands only toward the motion direction; the other three sides hug the window).

- `int getPrefetchTop()`
Gets the top bound of the prefetch zone.

- `int getPrefetchRight()`
Gets the right bound of the prefetch zone.

- `int getPrefetchBottom()`
Gets the bottom bound of the prefetch zone.

- `int getPrefetchExpand()`
Gets the prefetch expansion layer count.

- `void setPrefetchExpand(int expand)`
Sets the prefetch expansion layer count, must be > 0, default 1.

- `boolean isPrefetchEnabled()`
Checks whether prefetching is enabled.

- `void setPrefetchEnabled(boolean enabled)`
Enables or disables prefetching (enabled by default); disabling clears the prefetch zone and resets tracking state.

- `int getPrefetchPerFrame()`
Gets the per-frame prefetch limit (frame budget), default 8.

- `void setPrefetchPerFrame(int count)`
Sets the per-frame prefetch limit, must be > 0; illegal values are ignored.

- `int getPrefetchTileCount()`
Gets the prefetched tile count.

- `int getPrefetchQueuePeak()`
Gets the historical peak of the prefetch queue (for debugging, resets to zero with the prefetch zone lifecycle).

- `boolean hasPrefetchPending()`
Checks whether the prefetch queue still has coordinates pending.

- `void setPrefetchTiles(LongMap<T> map)`
Replaces the prefetch-tile storage.

> Tile ID encoding and the lifecycle flow are described in the Cross-Platform Porting Guide.

## Dimension Manager (DimenManager)

- `int getTileWidth(int column)`
Queries the column width by priority.

- `int getTileHeight(int row)`
Queries the row height by priority.

- `void setTileWidth(int column, int width, int gravity)`
Sets the column width and triggers a layout disturbance.

- `void setTileHeight(int row, int height, int gravity)`
Sets the row height and triggers a layout disturbance.

- `void setTileSize(int column, int width, int hGravity, int row, int height, int vGravity)`
Sets column width and row height at once.

- `void deleteTileWidth(int column, int gravity)`
Deletes the column's custom width.

- `void deleteTileHeight(int row, int gravity)`
Deletes the row's custom height.

- `int getDefaultTileWidth()`
Gets the default column width.

- `int getDefaultTileHeight()`
Gets the default row height.

- `void setDefaultTileWidth(int width)`
Sets the default column width, must be > 0.

- `void setDefaultTileHeight(int height)`
Sets the default row height, must be > 0.

- `TileDimenProvider getDimenProvider()`
Gets the size provider.

- `void setDimenProvider(TileDimenProvider dimenProvider)`
Sets the size provider.

- `void setWidths(IntIntMap map)`
Replaces the column-width storage.

- `void setHeights(IntIntMap map)`
Replaces the row-height storage.

- `void clear()`
Clears all custom sizes.

> Size priority and the modification flow are described under "Tile Operations - Size Operations".

## Event Handler (EventHandler)

- `void handleTouchEvent(MotionEvent event)`
Handles touch events; `ACTION_DOWN` automatically stops fling scrolling. A built-in `GestureDetector` and `ScaleGestureDetector` are included: the view enters snapshot zoom mode as soon as a second finger lands, and the result is settled after both fingers lift (see "Zooming").

- `void computeScroll()`
Drives fling scrolling (called from the view's `computeScroll`).

- `void resetAnimator()`
Stops the scroll animation.

- `void requestDisallowInterceptTouchEvent(boolean disallowIntercept)`
Controls touch interception.

- `boolean isInteractingWithView()`
Checks whether an interaction is in progress (scrolling/pinching).

- `void reset()`
Resets the event state.

> The `Callback` interface additionally declares the zoom lifecycle: `isZooming()` / `beginZoom()` / `updateZoom(relativeScale, focusX, focusY)` / `endZoom()`, implemented by `TileCoreService`.

## Size Provider (TileDimenProvider)

- `int getTileWidth(int column)`
Queries the column width.

- `int getTileHeight(int row)`
Queries the row height.

- `void setTileWidth(int column, int width)`
Sets the column width.

- `void setTileHeight(int row, int height)`
Sets the row height.

- `void deleteTileWidth(int column)`
Deletes the column width.

- `void deleteTileHeight(int row)`
Deletes the row height.

## Measurable Size Provider (MeasurableDimenProvider)

- `boolean isMinDefault()`
Checks whether the minimum default size is enabled.

- `void setMinDefault(boolean minDefault)`
Sets the minimum default size; used when content is smaller than the default size.

- `void full()`
Measures the adapter's full range and builds the size table.

- `void measure(int colStart, int rowStart, int colEnd, int rowEnd)`
Measures the given range and builds the size table.

- `void reset()`
Clears the size table.

- `void clearRecycledTiles()`
Clears the recycling pool used for measuring.

- `void setWidths(IntIntMap map)`
Replaces the column-width storage.

- `void setHeights(IntIntMap map)`
Replaces the row-height storage.

- `void setRecycledTiles(TileRecycledPool<TileCoreService.BaseTileHolder> pool)`
Replaces the recycling pool used for measuring.

> Constructor: `new MeasurableDimenProvider(adapter)` or `new MeasurableDimenProvider(width, height, adapter)`. A simple measuring tool, not recommended for large data volumes. Size query APIs are the same as `TileDimenProvider`.

## Measurable Interface (Measurable)

- `void measure(int widthMeasureSpec, int heightMeasureSpec, int[] out)`
Measures the content size, writing the result into `out[0]` (width) and `out[1]` (height).

## Drag Resizer View (DragResizerView)

- `int getIndicatorSize()`
Gets the indicator size.

- `void setIndicatorSize(int indicatorSize)`
Sets the indicator size.

- `int getDirection()`
Gets the current drag direction.

- `void setCallback(Callback callback)`
Sets the drag callback.

> Direction constants: `DIRECTION_NONE` (-1), `DIRECTION_START` (0, top-left corner), `DIRECTION_END` (1, bottom-right corner). `Callback`: `onDrag(direction, width, height, gravity)` / `getTileWidth()` / `getTileHeight()`.

## Time Provider (TimeProvider)

- `long nanoTime()`
Gets the wall-clock time (nanoseconds, monotonic).

- `long cpuNanoTime()`
Gets the current thread's accumulated CPU time (nanoseconds); falls back to wall-clock when no CPU clock is available.

> Built-in implementation: `DefaultTimeProvider`, where `nanoTime` uses `System.nanoTime` and `cpuNanoTime` uses `Debug.threadCpuTimeNanos`.

## Debug Layer (DebugLayer)

- `void start()`
Starts collecting statistics and registers the frame callback.

- `void end()`
Stops collecting statistics and removes the frame callback.

- `void startDraw()`
Marks the start of a frame's drawing.

- `void draw(Canvas canvas)`
Draws the debug panel and settles this frame's drawing duration.

> `Callback`: `getActiveTileCount` / `getRecycledTileCount` / `getDyingTileCount` / `getPrefetchTileCount` / `getPrefetchQueuePeak` / `getBounds` / `getLayoutModel` / `postInvalidateOnAnimation` / `getBindTime` / `getLayoutTime`. For integration see the Android Platform Extension Guide.

## Long Map (LongMap)

- `V get(long key)`
Gets the value of the given key.

- `void put(long key, V value)`
Puts a key-value pair.

- `V remove(long key)`
Removes the given key and returns the old value.

- `int size()`
Gets the element count.

- `boolean containsKey(long key)`
Checks whether the given key is contained.

- `void clear()`
Clears all elements.

- `Iterator<V> iterator()`
Gets an iterator (forward).

- `Iterator<V> iterator(boolean deleteMode)`
Gets an iterator; with `deleteMode` true it iterates in delete mode (handy for removing while iterating).

> Built-in implementations: `LongMapOpenHashMap` (default, open-addressing hash table, no boxing, expected capacity accepted in the constructor), `LongMapSparseArray` (backed by `LongSparseArray`, ordered storage), `LongMapHashMap` (backed by `HashMap`, better for bulk deletions). Active/dying tiles are stored through this interface.

## Long Queue (LongQueue)

- `int size()`
Gets the queue length.

- `void clear()`
Clears all elements.

- `void enqueue(long value)`
Enqueues at the tail.

- `long dequeue()`
Dequeues from the head (throws on an empty queue; check `size()` before dequeuing).

> Built-in implementation: `LongQueueArrayFIFO` (default, ring array, power-of-two capacity, auto-growing). The prefetch queue stores pending coordinates through this interface.

## Int Map (IntMap)

- `V get(int key)`
Gets the value of the given key.

- `void put(int key, V value)`
Puts a key-value pair.

- `V remove(int key)`
Removes the given key and returns the old value.

- `int size()`
Gets the element count.

- `boolean containsKey(int key)`
Checks whether the given key is contained.

- `void clear()`
Clears all elements.

- `Iterator<V> iterator()`
Gets an iterator (forward).

- `Iterator<V> iterator(boolean deleteMode)`
Gets an iterator; with `deleteMode` true it iterates in delete mode (handy for removing while iterating).

> Built-in implementations: `IntMapOpenHashMap` (default), `IntMapSparseArray`, `IntMapHashMap`, with the same characteristics as `LongMap`. The tile recycling pool groups tiles by type through this interface.

## Int-Int Map (IntIntMap)

- `int get(int key)`
Gets the value of the given key.

- `int get(int key, int defaultValue)`
Gets the value of the given key, returning the default when absent.

- `void put(int key, int value)`
Puts a key-value pair.

- `int remove(int key)`
Removes the given key and returns the old value.

- `int size()`
Gets the element count.

- `boolean containsKey(int key)`
Checks whether the given key is contained.

- `void clear()`
Clears all elements.

- `Iterator iterator()`
Gets an iterator (forward).

- `Iterator iterator(boolean deleteMode)`
Gets an iterator; with `deleteMode` true it iterates in delete mode (handy for removing while iterating).

> Built-in implementations: `IntIntMapOpenHashMap` (default), `IntIntMapSparseArray`, `IntIntMapHashMap`, with the same characteristics as `LongMap`. Column widths/row heights are stored through this interface.
