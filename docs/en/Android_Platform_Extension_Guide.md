# Android Platform Extension Guide

If `TileView` or `TileLayout` doesn't suit you, you can build your own — this guide walks you through the wiring step by step. You can build a **custom View**, move to **Compose**, move to **OpenGL ES**, and so on.

## Custom View

This is the easiest path: you reuse all low-level components and the core service as-is, just `extends View` or `extends ViewGroup` and wire it to the core service, which already chains all low-level components together.

Stripped down, there are only four things to do: **create the core service**, **implement the interface**, **hook up the View lifecycle**, **attach an adapter**. The core service is the single entry point — `LayoutEngine`, `TileManager`, `DimenManager` and `EventHandler` are all dispatched by it, and you never touch them directly.

#### Step 1: Create the Core Service

The `TileCoreService` constructor takes two parameters: `Context` and `CoreInterface`. `CoreInterface` is the entire contract between the view layer and the core layer; simply have your custom View implement it:

```java
public class MyTileView extends View implements TileCoreService.CoreInterface<MyHolder> {

    private TileCoreService<MyHolder> coreService;
    private MyAdapter adapter;

    public MyTileView(Context context) {
        super(context);
        coreService = new TileCoreService<>(context, this);
        coreService.setDefaultTileWidth(80);   // default column width, tune to your needs
        coreService.setDefaultTileHeight(45);  // default row height
    }
}
```

#### Step 2: Implement CoreInterface

The interface has three groups of methods, all of which must be implemented: **layout callbacks**, **tile lifecycle**, **data queries**. Here is a skeleton you can copy verbatim:

```java
// ===== Layout callbacks =====

@Override
public void updateUI() {
    // Layout finished, trigger a redraw
    postInvalidateOnAnimation();
}

@Override
public void beforeLayout() {
    // Called before layout starts (optional, usually empty)
}

// ===== Tile lifecycle =====

@Override
public void onTileIn(MyHolder holder, int column, int row) {
    // Tile entered the window
}

@Override
public void onTileOut(MyHolder holder, int column, int row) {
    // Tile left the window
}

@Override
public void onTileRecycled(MyHolder holder, int column, int row) {
    // Tile was recycled, release resources
}

@Override
public void onTileSizeChanged(MyHolder holder, int column, int row, int width, int height) {
    // Tile size changed (optional)
}

// ===== Data queries (delegate to the adapter) =====

@Override
public int getLeftBound()   { return adapter == null ? 0 : adapter.getLeftBound(); }
@Override
public int getTopBound()    { return adapter == null ? 0 : adapter.getTopBound(); }
@Override
public int getRightBound()  { return adapter == null ? -1 : adapter.getRightBound(); }
@Override
public int getBottomBound() { return adapter == null ? -1 : adapter.getBottomBound(); }

@Override
public MyHolder onCreateTileHolder(int type) {
    return adapter.onCreateTileHolder(type);
}

@Override
public void onBindTileHolder(MyHolder holder, int column, int row) {
    adapter.onBindTileHolder(holder, column, row);
}

@Override
public int getTileType(int column, int row) {
    return adapter.getTileType(column, row);
}
```

> Empty-adapter convention: when there is no adapter, the left/top bounds return `0` and the right/bottom bounds return `-1`, so `left > right` immediately reads as empty and the core service produces no tiles at all.

#### Step 3: Hook Up the View Lifecycle

Four entry points — size, drawing, touch, scrolling — must be routed into the core service; missing any one breaks things:

```java
@Override
protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    // Window bounds = content area (padding included); must match the drawing coordinate system
    coreService.setBounds(getPaddingLeft(), getPaddingTop(),
            getWidth() - getPaddingRight(), getHeight() - getPaddingBottom());
    if (getWidth() != 0 && getHeight() != 0) {
        coreService.sync(0, 0); // force a window re-sync after a size change
    }
}

@Override
public boolean onTouchEvent(MotionEvent event) {
    if (coreService.isEmpty()) return super.onTouchEvent(event);
    coreService.handleTouchEvent(event); // gestures, scrolling, fling all delegated to the core service
    return true;
}

@Override
public void computeScroll() {
    super.computeScroll();
    coreService.computeScroll(); // drive fling scrolling frame by frame
}

@Override
protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    coreService.resetAnimator(); // stop animations immediately on detach to avoid leaks
}
```

> The four values passed to `setBounds` directly determine how large tiles can spread, and must match the coordinate system used in `onDraw`; otherwise clicking, drawing and scrolling all shift out of alignment.

#### Step 4: Draw the Tiles

Drawing flow for the self-drawn case: **get the model → iterate the visible range → draw each tile**:

```java
@Override
protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    LayoutModel model = coreService.getLayoutModel();
    if (model.colStart > model.colEnd || model.rowStart > model.rowEnd) {
        return; // empty window
    }

    canvas.save();
    canvas.translate(getPaddingLeft() + model.offsetX,
            getPaddingTop() + model.offsetY);

    for (int column = model.colStart; column <= model.colEnd; column++) {
        for (int row = model.rowStart; row <= model.rowEnd; row++) {
            MyHolder tile = coreService.getActiveTile(column, row);
            if (tile != null) {
                // At this point the tile's world coordinate is (0,0) and its size is
                // coreService.getTileWidth(column) x coreService.getTileHeight(row)
                // TODO draw the tile content at the current position
            }
            // TODO after finishing a row, move the canvas down one row's height and continue
        }
        // TODO after finishing a column, move the canvas right one column's width and continue
    }
    canvas.restore();
}
```

- Fetch the column width/row height per cell with `getTileWidth(column)` / `getTileHeight(row)`, because with variable sizes every cell may differ — don't multiply by a fixed value
- `getActiveTile(column, row)` returning `null` means a sparse cell; skip it
- The two TODOs are coordinate accumulation; refer to `TileView`'s `onDraw`

> Zoom: at draw time multiply `model.offsetX/offsetY` by `getScaleFactor()` to make tiles follow the zoom (see `TileView.scale()`). To enable two-finger snapshot zooming you must register a `ZoomInterface`, see "Step 7: Wiring Up Zoom".

#### Step 5: Adapter and Tile Holder

```java
public static abstract class MyAdapter extends TileAdapter<MyHolder> {
}

public static class MyHolder extends TileCoreService.BaseTileHolder {
    // Self-drawn case: just hold whatever data drawing needs, e.g.
    // public void draw(Canvas canvas) { /* TODO */ }
}
```

- `onCreateTileHolder` returning `null` marks that cell as sparse and the core service skips it
- Holders are created, reused and recycled as the user scrolls; don't cache position-dependent state inside a holder
- `BaseTileHolder` ships with `getColumn()/getRow()/getWidth()/getHeight()/getType()`; use them directly when drawing

#### Step 6: Attach the Adapter

```java
public void setAdapter(MyAdapter adapter) {
    if (this.adapter != adapter) {
        coreService.reset(); // replacing the adapter must reset all state
    }
    this.adapter = adapter;
    coreService.seek(adapter.getLeftBound(), adapter.getTopBound(), 0, 0);
}
```

At this point the minimal custom View is complete. The remaining `offset/seek/snap/update/size changes` are all pass-throughs of the core service; expose them as needed — the method list is in the API Reference.

#### Step 7: Wiring Up Zoom

Zoom comes in two layers: **basic zoom** (API calls) and **two-finger snapshot zoom** (gestures).

Basic zoom is free: the core service's `zoom/zoomBy` updates `scaleFactor` and re-layouts; on the drawing side just multiply the offset by `getScaleFactor()` (as explained in the previous step).

Two-finger snapshot zoom requires registering a `ZoomInterface`, otherwise `beginZoom()` returns false when `EventHandler` sees a second finger and the gesture is ignored. `ZoomInterface` is the zoom contract between the renderer and the core service; its three methods cover the snapshot's lifecycle:

```java
// Capture the picture and switch to snapshot rendering when the zoom starts
@Override
public boolean captureZoomSnapshot() {
    // 1. Create a Bitmap (size = view size)
    // 2. Draw the current picture into it in screen coordinates (reuse onDraw logic, without the zoom transform)
    // 3. Set the snapshot flag; subsequent onDraw calls render only this bitmap
    return true; // return false on failure and the gesture won't enter zoom mode
}

// During zooming, only update the snapshot transform: translate first, then scale (same as the core service's settle formula)
@Override
public void onZoomUpdate(float scale, float translateX, float translateY) {
    // in onDraw: canvas.translate(translateX, translateY); canvas.scale(scale, scale); drawBitmap(...)
    postInvalidateOnAnimation();
}

// Zoom ends: resume normal rendering
@Override
public void releaseZoomSnapshot() {
    // Clear the snapshot flag, bitmap.recycle()
    postInvalidateOnAnimation();
}
```

Register with `coreService.setZoomInterface(zoomInterface)` (call after creating the core service).

> Reuse the ready-made implementation: `widget/zoom/ZoomSnapshot` encapsulates bitmap creation, reuse and transformed drawing; both `TileView` and `TileLayout` plug in through it. A custom View can copy it directly by implementing the four methods of `ZoomSnapshot.Renderer` (snapshot width/height, draw content, request redraw).

**Lifecycle contract**: `TileCoreService` owns the session state machine — a failed `beginZoom` never enters a session; during zooming `sync` accumulates translation into the snapshot and `seek`/`setBounds`/size changes call `cancelZoom()` first (any case where the snapshot would drift out of sync with the picture is abandoned); in `onDetachedFromWindow` make sure the snapshot bitmap is released (`TileView` calls `coreService.cancelZoom()` on detach).

#### ViewGroup Case

If tiles need to be real child Views (like `TileLayout`), only these points differ:

- Add the child View to the tree in `onTileIn`, remove it in `onTileOut`:

```java
@Override
public void onTileIn(MyHolder holder, int column, int row) {
    addViewInLayout(holder.itemView, -1, holder.itemView.getLayoutParams(), false);
}

@Override
public void onTileOut(MyHolder holder, int column, int row) {
    removeViewInLayout(holder.itemView);
}
```

- In `onLayout`, iterate the model and position each child View by `getTileWidth/getTileHeight`:

```java
@Override
protected void onLayout(boolean changed, int l, int t, int r, int b) {
    LayoutModel model = coreService.getLayoutModel();
    // TODO double loop over colStart..colEnd / rowStart..rowEnd
    // For each active tile: holder.itemView.layout(x, y, x + w, y + h)
    // Start coordinate = getPaddingLeft() + model.offsetX (same idea vertically)
}
```

- Touch events must be delivered twice: one copy flows through system dispatch to the child Views, one goes manually to the core service (which needs touch input to stop fling scrolling):

```java
@Override
public boolean dispatchTouchEvent(MotionEvent event) {
    if (!coreService.isEmpty()) {
        coreService.handleTouchEvent(event);
    }
    return super.dispatchTouchEvent(event);
}
```

> Measuring child Views: `TileLayout` measures with `MeasureSpec.EXACTLY` at the tile size inside the bind callback; just copy its `onBindTileHolder`.

### Compose

There are two ways. One is to **reuse** the core service and low-level components, since Kotlin can call Java. The other is a full Kotlin rewrite, which is much harder; this guide teaches the first.

Start with the simplest: wrap a `TileView` in `AndroidView` — one-shot integration:

```kotlin
@Composable
fun Tile2DEmbedded(adapter: TileView.Adapter, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context -> TileView(context).apply { setAdapter(adapter) } }
    )
}
```

The cost is a traditional View embedded in the Compose tree, so measuring and drawing go through the View system; interaction is identical to using `TileView` directly.

The more thorough approach is to **bypass Views entirely** and let `TileCoreService` drive rendering inside a Composable. The idea matches the custom View path exactly, just swapping the View lifecycle for the Composable lifecycle:

```kotlin
@Composable
fun Tile2DComposable(modifier: Modifier = Modifier, adapter: TileAdapter<*>) {
    val context = LocalContext.current
    val coreService = remember {
        TileCoreService<ComposeTileHolder>(context, object : TileCoreService.CoreInterface<ComposeTileHolder> {
            override fun updateUI() {
                // TODO trigger Compose recomposition: increment a mutableStateOf counter
            }
            // TODO the remaining CoreInterface methods are identical to the custom View path, delegate to the adapter
        })
    }

    DisposableEffect(Unit) {
        onDispose { coreService.resetAnimator() } // equivalent of onDetachedFromWindow
    }

    // TODO Modifier.onSizeChanged: setBounds(...) + sync(0f, 0f)
    // TODO Canvas: iterate the LayoutModel and draw tiles, same logic as the custom View's onDraw
}
```

A few key differences:

- `remember` guarantees the core service is created exactly once; cleanup goes into `DisposableEffect`'s `onDispose`
- `updateUI()` cannot call `postInvalidate`; instead trigger a manually managed recomposition state
- Scrolling can use `pointerInput` + `detectDragGestures`, converting drag deltas into content movement:

```kotlin
.pointerInput(Unit) {
    detectDragGestures { change, dragAmount ->
        change.consume()
        coreService.sync(-dragAmount.x, -dragAmount.y) // same sign convention as View gestures
    }
}
```

> If tiles must receive full touch events (click, long press), you need to assemble `PointerInputChange` into a `MotionEvent` and forward it — significantly more work.

### OpenGL ES

Usually **Vulkan** is the better choice, but it has no official **Java API**, so this guide uses **OpenGL ES**, which can reuse the core service and all low-level components.

Overall structure: `GLSurfaceView` carries the view, the `Renderer` also implements `CoreInterface`. The core service runs on the UI thread while GL drawing runs on the GL thread; the two are bridged only through `requestRender()` — this is the crux of the whole design.

#### Step 1: GLSurfaceView

```java
public class TileGLView extends GLSurfaceView {

    private TileCoreService<GLTileHolder> coreService;

    public TileGLView(Context context) {
        super(context);
        setEGLContextClientVersion(2); // start from ES 2.0
        // TODO setRenderer(renderer); see the next step
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (coreService != null && !coreService.isEmpty()) {
            coreService.handleTouchEvent(event);
        }
        return super.onTouchEvent(event);
    }

    // TODO pass onPause()/onResume() through to GLSurfaceView, or the GL thread will crash
}
```

#### Step 2: Renderer + CoreInterface

```java
public class TileGLRenderer implements GLSurfaceView.Renderer,
        TileCoreService.CoreInterface<GLTileHolder> {

    private TileCoreService<GLTileHolder> coreService;

    @Override
    public void updateUI() {
        // UI-thread callback; only request a redraw, no GL operations here
        // TODO notify GLSurfaceView.requestRender()
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        // TODO 1. Clear the screen
        // TODO 2. Iterate the LayoutModel's visible range, same logic as the custom View
        // TODO 3. For each active tile: position with getTileX/getTileY, size with getTileWidth/getTileHeight
        // TODO 4. Submit the tile's vertex data and texture
    }

    // TODO the remaining CoreInterface methods are identical to the custom View path
}
```

#### Step 3: Tile Holder and GL Resources

```java
public static class GLTileHolder extends TileCoreService.BaseTileHolder {
    private int textureId;      // TODO upload texture
    private FloatBuffer vertex; // TODO bind vertex buffer

    @Override
    public void onRecycled() {
        // TODO release texture and buffers; note they can only be released on the GL thread
    }
}
```

A few easy pitfalls:

- **Coordinate conversion**: `getTileX(column)/getTileY(row)` already include the offset and padding — they are the tile's world coordinates directly; converting pixel coordinates to GL normalized coordinates requires your own projection matrix
- **Threading**: GL resources may only be created/released on the GL thread; callbacks like `onTileRecycled` fire on the UI thread and must not touch GL objects — the correct approach is to flag them for release and let the GL thread reclaim them on its next frame
- **Lifecycle**: `GLSurfaceView.onPause()/onResume()` must be passed through, otherwise the GL thread keeps running in the background and crashes
- **Debugging**: `DebugLayer` depends on `Canvas` drawing and doesn't fit the GL scenario; you need to build your own data panel

---

All three approaches share one thing: **the core service and low-level components are fully reused**; the only thing that changes is "how tiles get drawn". It is recommended to get the custom View working first, then migrate to Compose / OpenGL ES — the cost is low.