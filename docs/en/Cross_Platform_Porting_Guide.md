# Cross-Platform Porting Guide

Tile2D's engine layer (`LayoutEngine`, `TileManager`, `DimenManager`) is **pure algorithm**: not a line of Android code, and no dependency on any collection library. This document first walks the most easily-gotten-wrong part — `sync` — through block by block, then explains how to implement your own data containers and the rest of the modules.

## 1. Choose the Language First: Don't Build "One C/C++ Core + FFI Everywhere"

- **Implement one engine in whatever the target platform's native language is.**
- "Write one in C/C++ and FFI-call it from every platform" is not recommended — not because C/C++ is bad, but because the approach itself has problems:
  - **High-frequency small calls**: during fling scrolling `sync` can be called dozens of times per frame, and internally it calls back into `getColWidth`/`in`/`out` (once per visible / in-out tile). Packing arguments across the language boundary and switching thread state add up and eat the algorithm's own advantage.
  - **Callbacks are bidirectional**: the engine must call back into the host for sizes and to notify in/out; a reverse call is more expensive and more error-prone than a forward one.
  - **Two memory models**: object lifetime / GC must be manually aligned — a long-term mental burden.
  - Build & distribution must maintain N compile configs; a crash across a cross-language stack is hard to debug.

| | One C/C++ + FFI | One per target language |
|---|---|---|
| Call overhead | every sync/callback crosses a boundary | no boundary |
| Callback | reverse call, costly & error-prone | native call |
| Memory model | two | one |
| Build & distribution | one per platform | ships with the language |

> **The algorithm exists in one form; implementations can be many** — translating is far cheaper than maintaining an FFI channel.

## 2. Where the Portability Comes From

Read the source and it's obvious: it imports no platform package (not even `java.util.*`); it uses only `int/long/float/boolean` + arrays + loops — no collection API, no lambda; `LayoutEngine`'s only object creation is initializing two `LayoutModel`s; the debug code (`timeProvider`/`syncTime`) is marked "removable".

Externally it depends on just two interfaces:

```text
Boundary interface (closed interval, supports MIN/MAX):
    getLeftBound() / getTopBound() / getRightBound() / getBottomBound()

Window interface:
    in(col, row) / out(col, row)        a tile enters / leaves the window
    onWindowCalculated(colStart, rowStart, colEnd, rowEnd)   the new window is computed
    getColWidth(col) / getRowHeight(row)          column width / row height
```

## 3. The Complete Logic of `sync` (English Pseudocode)

Convention: `dx/dy` is the **visual displacement** — a positive `dx` means the **content moves right** (i.e. the window looks left). The pseudocode uses plain names; a "plain name ↔ code field" table follows.

```text
sync(dx, dy):

    -- 0. legality short-circuit --
    if window range is empty (colStart > colEnd or rowStart > rowEnd) or windowWidth <= 0 or windowHeight <= 0:
        return "not computed"

    -- 1. horizontal sync (vertical is fully symmetric) --
    if horizontal scrolling is enabled:

        offsetX = original.offsetX + dx        <- accumulate the offset, only inside this branch

        // A  content narrower than window and already at the right data bound -> right-align
        if contentWidth + offsetX < windowWidth and colEnd == rightBound:
            offsetX = windowWidth - contentWidth

        // B  content moved right (offsetX > 0) -> move the start anchor left, absorb left columns
        loop offsetX > 0 and colStart > leftBound:
            colStart -= 1
            w = getColWidth(colStart)
            offsetX -= w
            contentWidth += w

        // C  content moved left (offsetX < -current first col width) -> move the start anchor right, drop left columns
        w = getColWidth(colStart)
        loop offsetX < -w and colStart < rightBound:
            offsetX += w
            contentWidth -= w
            colStart += 1
            w = getColWidth(colStart)

        // D  the start anchor jumped past the end anchor in one step -> bring the end anchor along
        if colStart > colEnd:
            colEnd = colStart
            contentWidth = w

        // E  already at the left data bound -> clamp
        if offsetX > 0 and colStart == leftBound:
            offsetX = 0

        // F  content cannot fill the window -> expand right, add columns
        loop contentWidth + offsetX < windowWidth and colEnd < rightBound:
            colEnd += 1
            contentWidth += getColWidth(colEnd)

        // G  content overflows the window too much -> shrink right, drop unused end columns
        wEnd = getColWidth(colEnd)
        loop contentWidth + offsetX - wEnd > windowWidth and colEnd > colStart:
            contentWidth -= wEnd
            colEnd -= 1
            wEnd = getColWidth(colEnd)

        // H  the loop above may have opened a new gap on the right -> patch once more
        if contentWidth > windowWidth and contentWidth + offsetX < windowWidth and colEnd == rightBound:
            offsetX = windowWidth - contentWidth

        write back offsetX

    -- 2. vertical sync --
    replace col->row, colStart->rowStart, colEnd->rowEnd, offsetX->offsetY,
    contentWidth->contentHeight, windowWidth->windowHeight,
    left/right data bound -> top/bottom data bound everywhere above; the logic is unchanged.
    Each direction's offset accumulates in its own branch, independent of the other.

    -- 3. wrap-up --
    notify host: window computed(colStart, rowStart, colEnd, rowEnd)
    if the window range (four anchors) changed:
        update "last state"
        diff: figure out which tiles enter and which leave (who in, who out)
    return "computed"
```

**Plain name ↔ code field**

| Pseudocode | Code | Pseudocode | Code |
|---|---|---|---|
| colStart / colEnd | `colStart` / `colEnd` | contentWidth | `contentWidth` |
| rowStart / rowEnd | `rowStart` / `rowEnd` | contentHeight | `contentHeight` |
| offsetX / offsetY | `offsetX` / `offsetY` | windowWidth / windowHeight | `windowWidth` / `windowHeight` |
| previous offsetX | `original.offsetX` | getColWidth / getRowHeight | `getColWidth` / `getRowHeight` |
| left/top/right/bottom bound | `leftBound`/`topBound`/`rightBound`/`bottomBound` | diff | `diff` |

## 4. Block-by-Block: What It Does, Why, and What Breaks Without It

### 0. Legality short-circuit

- **What it does**: returns immediately when the window isn't initialized (empty interval) or the window size is 0 — no work done.
- **Why**: the anchor range computed in these states is meaningless; passing it out would make the host create a pile of nonsensical tiles.
- **Without it**: before the container is measured (first frame) or when bounds are empty, an illegal range gets computed and propagates outward.

### 1. Accumulating the offset (inside the branch, before every test)

- **What it does**: folds this direction's displacement into the offset.
- **Why**: every test/loop below reads it, so it must be up to date; and it must take effect **in this direction only**.
- **Without it (or if accumulated once at the top of the method)**: the tests would read a value that already includes the displacement — e.g. A's `contentWidth + offsetX < windowWidth` would be combined with "the displacement is already in offsetX", degenerating the condition into always-true/always-false (flipping the sign won't save it); also, when scrolling is disabled for this direction, the displacement would still be written into the offset, corrupting the invariant below.

### A. Right-align (speculative compensation)

- **What it does**: right-aligns the content as a whole (equivalent to "faking one drag to the right"), giving B a chance to pull in the left columns and fill the window.
- **Why "speculative"**: the offset it pushes out has downstream cover — B consumes it by "absorbing a new column"; and if there's no left column left to absorb (colStart already at the left data bound), E force-zeros it.
- **Without it**: near the right end of the data, when the content doesn't fill a screen, the left columns aren't pulled in and a blank shows on one side.

### B. Move right: absorb left columns

- **What it does**: whenever the offset exceeds one full column's width, absorb the left column (colStart − 1), subtract that width from the offset and add it to contentWidth.
- **Why one column at a time, not one total displacement**: column widths **vary**, so the offset must always stay within "less than one column wide" — it can only be consumed one actual column at a time.
- **Without it**: dragging right never brings in new columns on the left → blank on the left, content discontinuity.

### C. Move left: drop columns scrolled past

- **What it does**: when the offset exceeds the current first column's width, move colStart right by one, add its width back to the offset and subtract it from contentWidth.
- **Why it must be done**: a column scrolled out of the window is pointless to keep.
- **Without it**: the range grows without bound — memory, draw count and traversal count all grow; and `contentWidth` is inflated, so every later test is off.

### D. End anchor follows (3c-2)

- **What it does**: when one displacement makes **colStart jump past colEnd in one step** (the interval becomes empty), bring colEnd over to align with colStart and reset contentWidth to "one column".
- **Why it must be done**: C only moves colStart; colEnd is still at its old position and contentWidth has already been shrunk a lot. If you went straight into F's fill loop, it would treat **the columns just jumped past and already subtracted** as "new columns" and ask their widths again (close to double traversal on a big displacement).
- **Without it**: on a big displacement (fling, jump) the traversal count nearly doubles, and the intermediate state has a "contentWidth vs anchor range" inconsistency.

### E. Left-bound clamp

- **What it does**: when the offset is positive (content moves right, the left edge would go blank) and colStart is already at the left data bound, zero the offset.
- **Why**: there's no content left on the left and B has no column to absorb — left-align is the only way to end.
- **Without it**: the left edge goes blank and the offset leaves its invariant range (the next frame's tests then chain-fail).

### F. Expand right: fill the window

- **What it does**: while contentWidth + offset is still less than the window width (can't fill), keep adding colEnd to the right until it fills or hits the right data bound.
- **Why it must be done**: the host renders only the "colStart..colEnd" range; without expanding, the right side is empty.
- **Without it**: blank on the right side of the window, especially when jumping/zooming near the bound.

### G. Shrink right: drop extra end columns

- **What it does**: if **it still fills after removing colEnd** (so colEnd is redundant), drop it and check the new colEnd again.
- **Why the test is "still fills after removing colEnd"**: this guarantees colEnd is "the last column still needed", leaving the content at most one column over. Extra columns are pure wasted compute and draw.
- **Without it**: a whole rank of invisible columns hangs off the right; on a big displacement (many columns crossed), draw/traversal volume is clearly inflated.

### H. Final-state compensation (3g)

- **What it does**: after the G shrink a gap may have been opened on the right again; right-align once more to patch it.
- **Why it's asymmetric with A (this is where many people think "can these be merged?")**:
  - A is **speculative** — the offset it pushes out has downstream cover (B consumes, E zeros), so it can "push first, ask later".
  - H is **final-state** — right after it runs the snapshot is written out and **nothing downstream can undo it**, so it must itself first prove the push is safe: `contentWidth > windowWidth` guarantees the left edge won't go blank after the right shift, and `contentWidth + offsetX < windowWidth` guarantees there really is a gap on the right.
- **Without it**: an occasional gap is left near the right bound (typically the frame right after a G shrink).

### 9. Write back offset / vertical symmetry / wrap-up

- **Write back offset**: the offset is **relative to colStart**, so when B/C move colStart the offset must be adjusted in step, or the picture jumps. At the end of each frame, write the final value back to state.
- **Vertical symmetry**: just copy the whole thing with column→row and width→height. The horizontal and vertical offsets accumulate independently — don't be tempted to factor out a shared function; that would either pass a pile of parameters or wrap a closure, making it harder to read and slower.
- **onWindowCalculated must come before diff**: tell the host the *complete new range* first (the host uses it to clean up the dying zone and plan prefetch), then do `diff` (which only handles tile in/out). If the order is reversed, the host plans its buffers on the old range.
- **Invariant**: at the end of each frame `offsetX ∈ [-current first col width, 0]` and `offsetY ∈ [-current first row height, 0]` must hold. The offset is a pixel-level float; once it leaves this range, the definition of "colStart" has drifted and the next frame's tests chain-fail. This is the basis of "pixel precision doesn't degrade".

## 5. Other Core Methods

### `seek(col, row, offsetX, offsetY)`: define the origin (distance-independent jump)

```text
seek(col, row, offsetX, offsetY):
    if bounds are empty or the target is out of range: return "not computed"
    prefill one screen to the bottom-right from (col, row) (call in() at every grid point to preload, avoiding a hollow first frame)
    set the anchors to (col, row), zero the offsets, and force-sync into the output snapshot
    call sync(offsetX, offsetY) to fine-tune
```

Three key points:

1. **Accumulator initial value**: when prefilling, accumulate `contentWidth` starting from `trunc(offsetX)`, and subtract `trunc(offsetX)` at the end — the two cancel to yield a pure "sum of widths". **Do not** start from 0 while still subtracting `offsetX` — that subtracts one whole offset too many, making `contentWidth` too small/too large and shifting the expand loop's terminating column by one.
2. `seek` passes its arguments to `sync` as `dx/dy` (with the offset zeroed first), letting the in-branch accumulation do the fine-tune. So **when scrolling is disabled for a direction, that direction's argument has no effect** — this is intentional.
3. **The caller is responsible for clearing**: a jump is a "window reset"; the container implementing the render layer (`TileManager` / a self-drawing container) must first clear all currently active tiles before `seek`; otherwise tiles in the old range not covered by the new one will **remain forever** (manifesting as the pre-jump picture frozen under the new one, with the DOM / cache growing without bound).

### `diff(oldRange, newRange)`: region difference

```text
if the new range and old range don't overlap at all:
    all of the old range out(); all of the new range in()
else:
    take the union, split into "top, right, bottom, left" four strips
    for each cell decide: only in old range -> out(); only in new range -> in()
```

Splitting into four strips is to traverse only the ring "union − intersection", avoiding double traversal of a big rectangle.

### Size-change compensation `updateWidth / updateHeight / updateSize`

| gravity | meaning | offset compensation |
|---|---|---|
| `START`(-1) | expand/shrink to the right only | `offset` unchanged |
| `CENTER`(0) | evenly on both sides | `offset += (oldSize − newSize) / 2` |
| `END`(1) | expand/shrink to the left only | `offset += oldSize − newSize` |

Only the columns/rows currently inside the window need the offset perturbed; columns/rows outside only need `contentWidth/Height` updated.

### Boundary checks

- Left/top: `anchor == bound and offset == 0` (exact).
- Right/bottom: `colEnd == bound and contentWidth + offset == windowWidth` (**exact comparison**; the meaning is "pixel-level alignment").

> The reason right/bottom dare to use `==`: the compensation writes `offset = windowWidth − contentWidth`, whose right side is **two integers subtracted** — an exactly representable result; assigned to a float, `contentWidth + offset == windowWidth` then holds by construction. If you want "within half a pixel counts as at the bound", you must add an explicit tolerance — that's different semantics; don't mix it in.

## 6. Implementing Your Own Data Containers

The tile manager needs a few small containers keyed by integers. **Implement them first, then translate the three core modules.**

### Long map (`LongMap`, keyed by tile id)

Needs: `get/put/remove/size/containsKey/clear`; the iterator must support **delete while iterating**.

| Language | Approach |
|---|---|
| Java / Kotlin | In-package custom impl (open-addressing hash, no boxing), or `fastutil`/`trove` |
| C# | `Dictionary<long, T>` (long is a value type, no boxing) |
| Go | `map[int64]T` |
| Rust | `HashMap<i64, T>` |
| C++ | `unordered_map<int64_t, T>` |
| JS/TS | `Map<number, T>` |
| Swift | `Dictionary<Int64, T>` |

> Why not just use `HashMap<Long,T>` in Java: the key gets boxed into a `Long` object, and tiles enter/leave the window very frequently, so boxing/unboxing and hashing overhead get amplified. The interface is tiny — a few hundred lines to implement yourself. Other languages have no boxing problem; use the standard library directly.

### Int maps (`IntIntMap` / `IntMap`)

- `IntIntMap`: column width / row height use `int -> int`, with one extra `get(key, default)` over `LongMap`.
- `IntMap`: the recycle pool groups by type using `int -> queue`, isomorphic to `LongMap` with the value swapped for a queue.

### Tile recycle pool

Caches tiles grouped by type: `get(type)` takes one (returns null if empty), `recycle(type, tile)` puts it back, `reset()` clears, `moveTo()` migrates wholesale. Use the language's own queue (Java `ArrayDeque`, C# `Queue<T>`, Go slice, Rust `VecDeque`, JS array).

### Tile id encoding

```text
id = (col << 32) | (row & 0xFFFFFFFF)      // high 32 bits: col, low 32 bits: row
col = id >> 32
row = id & 0xFFFFFFFF
```

- Languages with 64-bit integers copy it directly.
- **JavaScript's bitwise ops are only 32-bit.** Two alternatives: use a `"col,row"` string as the key (simplest), or a 32-bit encoding `(col << 16) | row` (col/row each limited to 16 bits), or `BigInt`.
- Other languages can use a string key too, at the cost of being a bit slower.

### Tile holder

Fields: `col/row/width/height/type`; optional lifecycle hooks: `onRecycled` (recycled), `onInWindow`/`onOutWindow` (enter/leave window), `onSizeChanged` (size change). Express with a base class / interface / trait in the target language.

## 7. Tile Manager (`TileManager`)

### Four-state pools

| Pool | Storage | Meaning |
|---|---|---|
| active | `LongMap` | currently visible inside the window |
| dying | `LongMap` | just left the window (a one-ring buffer outside it, kept briefly) |
| prefetch | `LongMap` | loaded early in the direction of motion (created & bound, not yet in the window) |
| recycle | recycle pool | recycled, reusable |

```text
enter window -> active pool (if from prefetch, promote directly, skip create+bind)
leave window -> dying pool (if the dying zone is disabled, recycle directly)
leave dying zone -> recycle pool
direction reverses -> prefetch pool evicted (recycled directly, not into the dying pool)
```

```text
in(col, row):
    check the dying pool first: hit -> move into active pool (skip binding)
    miss -> obtain(type) create or reuse -> bind -> into active pool
    callback onInWindow + onTileIn
out(col, row):
    remove from active pool -> callback onOutWindow + onTileOut
    dying zone enabled -> into dying pool; else recycle directly
obtain(type):   reuse from recycle pool if any; else onCreateTileHolder(type)
recycle(tile):  into recycle pool + callback onRecycled + onTileRecycled
```

### Dying zone

Dying zone = window expanded outward by `dyingExpand` rings (default 1). **Don't compute the bound with subtraction:**

```text
Wrong:   left = colStart - dyingRings          // subtraction overflows when the bound is Integer.MIN_VALUE
Right:   walk cell by cell
getDyingLeft():
    left = colStart
    repeat dyingRings times:
        if left <= leftBound: break
        left -= 1
    return left
```

The mathematical distance exceeds int32, so a subtraction result is unreliable; walking cell by cell just checks whether the bound is reached at each step. Call `diffDying(range)` after each window computation to clear tiles beyond the dying zone into the recycle pool.

### Prefetch zone

Prefetch is symmetric to the dying zone, opposite in direction: **the dying zone keeps behind, prefetch grabs ahead**. A prefetch tile is created & bound but doesn't enter the active area (on `in()` it's lifted straight out of the prefetch pool and promoted, without a window-enter callback). Enabled by default.

- **Direction prediction**: record the previous window anchor, compare it with the current anchor three-way to get the direction. First frame / after a jump there's no previous record → direction unknown, no expansion.
- **Asymmetric rectangle**: expand only `prefetchExpand` rings in the direction of motion, the other three sides hug the window body. When the window hasn't moved, keep already-prefetched tiles, no re-layout.
- **Strip enqueue**: clear and rebuild the queue each frame, enqueue only the front strip of "direction rectangle − window rectangle" (only if none of the three pools holds it). No count cap; the strip width naturally constrains it.
- **Per-frame budget consumption**: the render side calls `drainPrefetch()` each frame, consuming `prefetchPerFrame` (default 8). Any remainder continues to the next frame; when empty it stops naturally.
- **Eviction**: on direction reversal, prefetch tiles falling outside the new direction rectangle are **recycled directly, not into the dying pool** — they never entered the window, have no visible lifecycle, and need no buffer.

The queue uses `LongQueue` (ring array, capacity a power of 2, auto-growing).

### Update operations

`update/updateRange/updateColumn/updateRow` refresh a given range: those in the active area are **recycled then `in` again** (re-bound); those in the dying area only have their cached data refreshed. `updateAll` is equivalent to an in-place `seek`.

## 8. Dimension Manager (`DimenManager`)

Three-level priority (`getTileWidth(col)` looks up in this order):

```text
1. an individually set size (IntIntMap)
2. a dynamic value from TileDimenProvider (optional; each platform implements as needed, e.g. content measurement)
3. the default size set by setDefault
```

Modification flow `setTileWidth(col, width, gravity)`:

```text
1. width <= 0 -> reject (setters require > 0)
2. bounds empty or col out of range -> reject
3. same as the old value -> return directly
4. sync the size of all tiles in that column in the dying zone
5. call the engine's updateWidth to compensate the window
6. trigger a refresh
```

To delete a custom size use `deleteTileWidth(col, gravity)` (= `widths.remove` + the same sync flow), **don't express deletion by passing 0**. `setTileSize` merges both directions into one perturbation.

## 9. Composition Dispatch Layer (`TileCoreService`)

Combines the three modules into a unified entry point; it is itself pure logic and portable. **`EventHandler` (`GestureDetector`, `Scroller`) is Android-specific — don't port it**: each platform uses its own input system (touch, mouse, gamepad) to turn displacement into `sync(dx, dy)`, and uses the target platform's own animation mechanism for fling.

Dispatch-layer responsibilities:

- Forward boundary/size/tile lifecycle callbacks, wiring the engine and the tile manager together;
- Provide a unified API: `sync/seek/snap/update/setTileWidth/...`;
- After `onWindowCalculated`, call `diffPrefetch` to plan prefetch, consumed by the render side each frame via `drainPrefetch()`;
- Hold debug statistics (optional).

## 10. Plug Into the Target Platform's Rendering Layer

The engine only outputs "which tiles are visible, at what coordinate, how big"; the render layer draws them:

1. Implement the boundary interface / window interface / callbacks (data source and tile holder);
2. Wire "window changed" into the target platform's refresh mechanism (redraw / recompose / submit render commands);
3. Drive `sync` with the target platform's input system.

## 11. Testing Strategy

All three modules are pure algorithm and very testable: mock the interfaces, verify the output. `LayoutEngine` verifies `LayoutModel` under scroll/jump/size-change; `TileManager` verifies in/out/recycle counts and prefetch; `DimenManager` verifies size lookup and modification perturbation.

**Cross-implementation alignment (isomorphic differential test)**: put both implementations through **the same random scenario sequence** (same PRNG, same batch of column widths / row heights / windows / displacements) and compare `LayoutModel`'s full output (anchors, `contentWidth`, `offset`) scenario by scenario. This project uses it to align Java and H5: 600 scenarios (`sync`/`seek`/`updateWidth` mixed, 2-D data) with zero difference before porting is considered done.

> The PRNG's **multiplier must be small**: when the product exceeds 2^53 JS loses precision and the two sequences quietly drift apart.

Boundary scenario list:

- Empty bounds (left > right)
- Single column / single row
- Starting near `Integer.MIN_VALUE`
- High-frequency jitter (scrolling back and forth at the same column bound)
- Repeated size changes (big to small to big)
- All tiles returning null (sparse)
- Content smaller than the window (can't fill)
- Extreme jump (crossing the whole int32 space at once)
- First prefetch frame (no previous record, direction unknown)
- Prefetch direction reversal (behind prefetch evicted & recycled)

## Sub-document

- [H5 Porting Guide (Canvas self-drawing)](H5_Porting_Guide.md) — a complete porting tutorial with a runnable sample (h5-demo/).

---

> This document was generated by AI.