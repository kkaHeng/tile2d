# Cross-Platform Porting Guide

Tile2D's engine layer (`LayoutEngine`, `TileManager`, `DimenManager`) is pure algorithm: not a single line of Android code, and no JDK-specific features either. This document teaches you how to implement an identical engine in any language/platform, then plug it into the target platform's rendering ecosystem.

## Before Porting: Which Language to Use

Before starting, answer one question: **which language should this engine be written in on your target platform?** The most common wrong answer is "write one copy in C/C++ and call it via FFI from every platform". That answer splits into two cases with completely different conclusions.

### The Target Platform's Native Language Is C/C++

On PC desktop (Win32, Qt, etc.), embedded systems, game engines and similar platforms, C/C++ *is* the native language; implementing the engine directly in C/C++ is **entirely correct** — the "target language" is C/C++, and this guide applies as usual.

### The Target Platform Is Not C/C++

When targeting Java/Kotlin, C#, Go, Rust, JavaScript and similar ecosystems, writing one C/C++ core called through FFI everywhere is **not recommended**. Note: this is not "C/C++ can't do it" — it's that the scheme "one C/C++ copy for all languages" is flawed in itself:

- **Boundary overhead of high-frequency small calls**. `sync` may be called dozens of times per frame during fling scrolling; inside each `sync` it also calls back `getColWidth/getRowHeight` (once per visible tile) and `in/out` (once per tile entering/leaving). Every FFI call carries argument packing, thread-state switching and exception bridging overhead; these high-frequency small calls accumulate and eat the algorithm's own performance advantage.
- **Callbacks are bidirectional**. The engine calls back into the host language for bounds, sizes and tile enter/leave notifications. Calling from C back into the host language (JNI callbacks, PInvoke delegates, FFI closures) is more expensive and more error-prone than forward calls.
- **Split type and memory models**. Object lifecycles, allocators and GC differ completely between the C/C++ side and the host language. When tile objects cross the boundary, who frees them and how to avoid copies becomes a long-term mental burden.
- **Build and distribution complexity**. One C source tree must maintain multi-platform build configs, ABI compatibility and artifact distribution; a one-line algorithm change rebuilds every platform's glue layer.
- **Hard debugging**. Crashes and memory issues across a cross-language stack are an order of magnitude harder to locate than in a single-language stack.

| | One C/C++ + FFI | Target-language implementation |
|---|---|---|
| Call overhead | Every sync/callback crosses the boundary | No boundary |
| Callbacks | Reverse calls expensive and error-prone | Native calls |
| Memory model | Two sets, manually aligned | One set |
| Build & distribution | One build config per platform | Ships with the target language |
| Debugging | Cross-language stack | Single-language stack |
| Effort | Engine + N glue layers | Engine translated once |

The engine itself is pure logic: a few numbers in, a few numbers out, zero platform features. Implementing one copy in the target language costs far less than maintaining an FFI channel.

> Conclusion: **there is one algorithm; there can be many implementations**. Use C/C++ on C/C++-native platforms; on other platforms write one copy in each platform's own language. The three core modules total about 1300 lines (including comments and debug code), so translation is cheap — not worth trading for FFI.

## Where the Portability Comes From

Look at the source and the reason the three core classes are cross-platform is obvious:

- No `android.*` imports, not even `java.util.*` — they depend only on interfaces inside their own package
- Only primitive types (`int`/`long`/`float`/`boolean`), arrays and loops; no collection APIs, no lambdas
- `LayoutEngine`'s only object creation is initializing two `LayoutModel` state objects
- Debug code (`timeProvider`, `syncTime`) is explicitly marked in comments as "removable for cross-platform"

Dependency surface:

| Module | Dependencies |
|---|---|
| `LayoutEngine` | `BoundaryInterface`, `WindowInterface`, `LayoutModel` |
| `TileManager` | `LongMap<T>`, `TileRecycledPool<T>`, `LongQueue`, `Callback<T>` |
| `DimenManager` | `IntIntMap`, `TileDimenProvider`, `Callback` |

The reasons for and implementations of the custom data structures (`LongMap`/`IntIntMap`, etc.) are covered in the "Data Structures" section; implement them in the target language first, then translate the three core modules.

## Step 1: Layout Engine (LayoutEngine)

The engine is the most independent, easiest-to-translate module: it depends on only two interfaces and one pure data object, and the algorithm body is loops and arithmetic. Port it first; continue with the other modules once its unit tests pass.

### State and Interfaces

The engine keeps only: two `LayoutModel`s (`original` records the real state, `output` is the output snapshot), the window width/height, and two scroll switches. It depends on two external interfaces:

```text
BoundaryInterface (bounds, closed interval, supports MIN/MAX):
    getLeftBound() / getTopBound() / getRightBound() / getBottomBound()

WindowInterface (window interaction):
    in(column, row)                  tile enters the window
    out(column, row)                 tile leaves the window
    onWindowCalculated(cs, rs, ce, re) new window calculated
    getColWidth(column)              column width
    getRowHeight(row)                row height
```

Express them with the target language's interface/protocol/trait; the implementers are the tile manager and the dimension manager respectively.

### sync: the Scrolling Core

`sync(dx, dy)` scrolls by a pixel displacement. dx/dy are **visual displacements**: positive dx moves the content right (the window extends leftward). Full flow:

```text
sync(dx, dy):
    # 1. Validity short-circuit
    if window out of bounds or window size <= 0:
        return false

    # 2. Offset accumulation
    offsetX += dx
    offsetY += dy

    # 3. Horizontal sync (vertical is exactly analogous, swap columns for rows)

    # 3a. Content doesn't fill the window and the right bound is reached: try right-alignment
    if totalWidth + offsetX < windowWidth and colEnd == rightBound:
        offsetX += windowWidth - (totalWidth + offsetX)

    # 3b. Dragging right: anchor moves left, new columns come in
    while offsetX > 0 and colStart > leftBound:
        colStart--
        offsetX -= getColWidth(colStart)
        totalWidth += getColWidth(colStart)

    # 3c. Dragging left: anchor moves right, old columns go out
    while offsetX < -getColWidth(colStart) and colStart < rightBound:
        offsetX += getColWidth(colStart)
        totalWidth -= getColWidth(colStart)
        colStart++

    # 3d. Left bound reached: clamp
    if offsetX > 0 and colStart == leftBound:
        offsetX = 0

    # 3e. Content doesn't fill the window: extend the right anchor rightward
    while totalWidth + offsetX < windowWidth and colEnd < rightBound:
        colEnd++
        totalWidth += getColWidth(colEnd)

    # 3f. Content overshoots: shrink the right anchor
    while totalWidth + offsetX - getColWidth(colEnd) > windowWidth and colEnd > colStart:
        totalWidth -= getColWidth(colEnd)
        colEnd--

    # 4. Vertical sync (same as above)

    # 5. Notify that the window has been calculated (the tile manager cleans the dying zone based on this)
    onWindowCalculated(colStart, rowStart, colEnd, rowEnd)

    # 6. On range change, update original and diff
    if range changed:
        update original
        diff(old range, new range)
```

> Key constraint: `offsetX` always stays within `[-current column width, 0]`, `offsetY` within `[-current row height, 0]`. This is the foundation of "pixel precision never degrades" — do not alter any boundary conditions when translating.

### seek: Defining the Origin

`seek(column, row, offsetX, offsetY)` jumps to the given coordinate:

```text
seek(column, row, offsetX, offsetY):
    if bounds empty or target out of bounds:
        return false

    Expand right/down from (column, row):
        accumulate row heights line by line until the window height is filled or the bottom bound is hit
            (calling in() to preload along the way)
        accumulate column widths column by column until the window width is filled or the right bound is hit

    Write original (anchor = column,row, offsets set to 0)
    Call sync(offsetX, offsetY) for fine-tuning
```

### diff: Region Difference

Computes the difference between the old and new window rectangles to decide which tiles go `out` and which go `in`:

```text
if the new range and old range are completely disjoint:
    out() everything in the old range
    in() everything in the new range
else:
    take the union, decompose it into the four regions "top, right, bottom, left"
    for each cell: only in the old range → out(); only in the new range → in()
```

The Java implementation splits the union into four region blocks and judges cell by cell, avoiding a double traversal of the whole large rectangle; other languages can copy it directly.

### Size-Change Compensation

`updateWidth/updateHeight/updateSize` adjust the offset when a tile size changes, keeping the visuals stable. The compensation direction is decided by gravity:

| gravity | Column width change | Offset compensation |
|---|---|---|
| `START` (-1) | Right side expands/shrinks | `offsetX` unchanged |
| `CENTER` (0) | Both sides evenly | `offsetX += (oldWidth - newWidth) / 2` |
| `END` (1) | Left side expands/shrinks | `offsetX += oldWidth - newWidth` |

Disturbance only applies to columns/rows "currently inside the window"; for anything out of range just update `totalWidth/totalHeight` directly.

### Boundary Checks and Helpers

- `isAtLeftBound/isAtTopBound`: `anchor == bound && offset == 0`
- `isAtRightBound/isAtBottomBound`: `colEnd == bound && totalWidth + offsetX == windowWidth`
- `min/max`: the Java implementation uses int/float overloads (to avoid boxing); other languages use native `min/max` or direct comparison

### Debug Code

`timeProvider` and `syncTime` exist only to measure `sync` duration; delete the whole section when porting — no behavior is affected.

## Step 2: Data Structures

The tile manager depends on three custom data structures. Implement them in the target language first (or use an existing library); the reasons and options follow.

### Long Map (LongMap)

Active/dying tiles use a `long` key storing `T`. Requirements:

- `get/put/remove/size/containsKey/clear`
- Iterator: `next/key/value/remove`, supporting delete mode (remove while iterating)

Options per language:

| Language | Option |
|---|---|
| Java | In-package implementations: `LongMapOpenHashMap` (open-addressing hash, no boxing, default), `LongMapSparseArray`, `LongMapHashMap`; or `fastutil`, `trove` |
| Kotlin/JVM | Same as above |
| C# | `Dictionary<long, T>` (long is a value type, no boxing) |
| Go | `map[int64]T` |
| Rust | `std::collections::HashMap<i64, T>` |
| C++ | `std::unordered_map<int64_t, T>` |
| JS/TS | `Map<number, T>` |
| Swift | `Dictionary<Int64, T>` |

> Why not `HashMap<Long,T>` in Java: keys box into `Long` objects, and tiles enter/leave the window constantly, amplifying boxing/unboxing and hashing overhead. The interface is tiny and a custom implementation is only a few hundred lines. Other languages have no boxing problem — use the standard library directly.

### Int-Int Map (IntIntMap)

Column widths/row heights use `int -> int`, one extra `get(key, defaultValue)` over LongMap. Options as above: Java can use `fastutil`'s `Int2IntOpenHashMap` or `SparseIntArray`; C# `Dictionary<int, int>`, Go `map[int]int`, Rust `HashMap<i32, i32>`, C++ `std::unordered_map<int, int>`.

### Int Map (IntMap)

The recycling pool groups by type and needs an `int -> queue` map; the interface is isomorphic to LongMap (key becomes int) with the target language's queue type as the value.

### Tile Recycling Pool (TileRecycledPool)

Type-grouped tile cache: `get(type)` takes the queue head (null when empty), `recycle(type, tile)` puts one back, `reset()` clears, `moveTo()` migrates in bulk. Use each language's own queue: Java `ArrayDeque`, C# `Queue<T>`, Go slices, Rust `VecDeque<T>`, JS arrays.

### Tile ID Encoding

Active/dying tiles use a `long` key: high 32 bits column, low 32 bits row:

```text
id = (column << 32) | (row & 0xFFFFFFFF)
column = id >> 32
row = id & 0xFFFFFFFF
```

- Languages with 64-bit integers can copy it directly
- JavaScript's bitwise operations are only 32 bits; two alternatives: use the string `"col,row"` as key (simplest), or the 32-bit encoding `(col << 16) | row` (col/row each within a 16-bit range), or `BigInt`
- Other languages can also use string keys at some speed cost

### Tile Holder (BaseTileHolder)

Fields `column/row/width/height/type` plus optional lifecycle hooks `onRecycled/onInWindow/onOutWindow/onSizeChanged`. Express with the target language's base class/interface/trait.

## Step 3: Tile Manager (TileManager)

### Four-State Pools

| Pool | Storage | Meaning |
|---|---|---|
| Active | `LongMap<T>` | Currently visible inside the window |
| Dying | `LongMap<T>` | Just left the window (a ring buffered outside the window) |
| Prefetch | `LongMap<T>` | Loaded ahead along the motion direction (created & bound, not yet in the window) |
| Recycled | `TileRecycledPool<T>` | Recycled and reusable |

Flow:

```text
Enter window   → active pool (prefetch-pool tiles are promoted directly, skipping creation & binding)
Leave window   → dying pool (recycled directly when the dying zone is disabled)
Leave dying zone → recycled pool
Direction reversal → prefetch pool evicted (recycled directly, not into the dying zone)
Reuse          → active pool
```

### in / out / obtain / recycle

```text
in(column, row):
    Look up the id in the dying pool first
    Hit     → move into the active pool (skip binding)
    Miss    → obtain(type) creates or reuses → bind → into the active pool
    Callbacks onInWindow + onTileIn

out(column, row):
    Remove from the active pool
    Callbacks onOutWindow + onTileOut
    Dying zone enabled → into the dying pool; otherwise recycle directly

obtain(type):
    Take from the recycling pool → reuse if present; otherwise onCreateTileHolder(type)

recycle(tile):
    Into the recycling pool + callbacks onRecycled + onTileRecycled
```

### Dying Zone

The dying zone = the window expanded outward by `dyingExpand` rings (default 1). **Do not compute the bounds by subtraction**: `colStart - leftBound` overflows when the bound is `Integer.MIN_VALUE` (the mathematical distance exceeds int32, so the subtraction result is untrustworthy). The correct approach is to walk cell by cell, checking at each step whether the bound has been reached:

```text
getDyingLeft():
    left = colStart
    for i in 0 until dyingExpand:
        if left <= leftBound: break
        left--
    return left
```

`diffDying(colStart, rowStart, colEnd, rowEnd)` is called after every window calculation and moves tiles that fell outside the dying zone into the recycling pool. When `setDyingEnabled(false)` turns the dying zone off, tiles leaving the window are recycled immediately and the dying pool is cleared.

### Prefetch Zone

Prefetch is symmetric to dying: **the dying zone holds what just left behind; the prefetch zone grabs what is about to arrive ahead**. Prefetched tiles are created and bound but never enter the active zone (`in()` promotes them straight out of the prefetch pool without firing enter-window callbacks). **Enabled by default**.

- **Direction prediction**: `diffPrefetch` records the previous window anchor and three-way compares it with the current anchor to derive one of eight direction displacements. On the first frame / after a seek the closed interval is empty (`start > end`), meaning no previous record and unknown direction — no expansion.
- **Asymmetric rectangle**: the prefetch zone expands by `prefetchExpand` rings (default 1) only toward the motion direction, with the other three sides hugging the window itself. When the window didn't move (`sameWindow`), already-prefetched tiles are kept, no reshuffle.
- **Strip enqueue**: the queue is cleared and re-planned each frame, enqueueing only the coordinates of the "direction rectangle − window rectangle" forward strip (`enqueueRegion`, only when held by none of the three pools). No count cap — strip width is naturally constrained.
- **Frame-budget consumption**: the renderer calls `drainPrefetch()` each frame, which consumes `prefetchPerFrame` tiles internally (default 8, adjustable via `setPrefetchPerFrame`; throughput for a 2D grid — RecyclerView's 1D is 4). If the queue still has leftovers it schedules the next frame callback; when empty it stops naturally.
- **Eviction**: on direction reversal, prefetched tiles falling outside the new direction rectangle are recycled directly, not into the dying zone — they never entered the window, have no visible lifecycle and need no buffering.
- **Peak observation**: `prefetchQueuePeak` records the queue's high-water mark and resets to zero with the prefetch zone lifecycle (cleared when prefetch is disabled / on clearAll / on seek); the debug panel shows the "prefetch peak".

The queue uses `LongQueue` (long FIFO, ring array, power-of-two capacity, auto-growing).

### Update Operations

`update/updateRange/updateColumn/updateRow` refresh tiles in the given range: tiles inside the active zone are recycled and re-`in`ed (re-bound); tiles inside the dying zone just refresh their cached data. `updateAll` is equivalent to an in-place `seek`.

### Storage Replacement

All three pools — active/dying/recycled — support wholesale replacement (swap the data structure implementation). Migration flow: clear the target container → move items in one by one → clear the old container.

## Step 4: Dimension Manager (DimenManager)

### Three-Level Priority

```text
1. Individually set size (IntIntMap)
2. Dynamic value from TileDimenProvider
3. Default size from setDefault
```

`getTileWidth(column)` queries in this order. `TileDimenProvider` is a custom interface (`getTileWidth/getTileHeight/set*/delete*`); each platform implements it as needed, e.g. returning measured-from-content values dynamically.

### Modification Flow

`setTileWidth(column, width, gravity)`:

```text
1. width <= 0 → reject (setters require values greater than 0)
2. Bounds empty or column out of range → reject
3. Same as old value → return directly
4. Traverse all tiles of that column inside the dying zone, calling resizeTile to sync sizes
5. Call the engine's updateWidth for window compensation
6. Trigger a UI refresh
```

To delete a custom size use `deleteTileWidth(column, gravity)` (`widths.remove` + the same sync flow) — **no longer express deletion by passing 0**. Same for `setTileHeight`; `setTileSize` merges the horizontal and vertical directions into one disturbance (both width and height must be > 0).

## Step 5: Composition Dispatch Layer (TileCoreService)

The dispatch layer composes the three modules into one entry point; it is also pure logic and can be ported. **EventHandler is Android-specific (GestureDetector, Scroller) and should NOT be ported** — each platform uses its own input system (touch, mouse wheel, gamepad, etc.) to convert movements into `sync(dx, dy)` calls, and fling scrolling is driven by the target platform's own animation mechanism.

The dispatch layer's responsibilities:

- Forward boundary/size/tile lifecycle callbacks (wiring the engine and the tile manager together)
- Provide a unified API: `sync/seek/snap/update/setTileWidth/...`
- After `onWindowCalculated`, call `diffPrefetch` to plan prefetch, consumed by the renderer's per-frame `drainPrefetch()` (the frame callback is driven by "debug enabled or queue non-empty"; with neither it stops naturally)
- Hold debug statistics (optional)

## Step 6: Plug Into the Target Platform's Rendering Layer

The engine only outputs "which tiles are visible, at which coordinate, how large"; the rendering layer is responsible for drawing them. For integration refer to the Android Platform Extension Guide:

1. Implement `BoundaryInterface`/`WindowInterface`/`Callback` (data source and tile holders)
2. Connect "window changed" to the target platform's refresh mechanism (redraw/recomposition/submit render commands)
3. Use the target platform's input system to drive `sync`

## Testing Strategy

All three modules are pure algorithms and ideal for unit testing: mock the interfaces, verify the outputs.

- **LayoutEngine**: mock the boundary and window interfaces, verify `LayoutModel` output across scrolling/seeking/size-change scenarios
- **TileManager**: mock the callbacks, verify tile enter/leave and recycling counts; in prefetch scenarios verify direction prediction, strip enqueue, queue consumption and direction-reversal eviction
- **DimenManager**: mock the callbacks, verify size queries and modification disturbance

Boundary scenario checklist:

- Empty bounds (left > right)
- Single column/row
- Starting near `Integer.MIN_VALUE`
- High-frequency jitter (repeatedly scrolling back and forth over the same column boundary)
- Repeated size changes (large → small → large)
- All tiles returning null (sparse case)
- Content smaller than the window (can't fill it)
- Extreme seek (crossing the entire int32 space in one call)
- Prefetch on the first frame (no previous record, unknown direction, no expansion)
- Prefetch direction reversal (tiles prefetched behind are evicted and recycled)
- Prefetch queue peak (fast repeated flailing, verifying it doesn't blow up)

## Sub-document

- [H5 Porting Guide (DOM)](H5_Porting_Guide.md) — a complete porting tutorial using the browser as the example, with a runnable sample (h5-demo/).