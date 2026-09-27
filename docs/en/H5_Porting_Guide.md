# H5 Porting Guide (Tile2D)

> This document is the H5 sub-document of the "Cross-Platform Porting Guide": **using the project's real, runnable `h5-demo/` as an example, it teaches you step by step how to port Tile2D to the browser (JavaScript)**.
> The rendering approach is **DOM** — tiles are real elements (mirroring the app-side `TileLayout`, which uses real child Views to carry tiles), not Canvas self-drawing.
> The code blocks in this document are **real code extracted from `h5-demo/`**; when it says "see file X for the full code", the file is authoritative.

## How to Use This Guide

- **Prerequisite**: first read the `LayoutEngine` chapter of the "Cross-Platform Porting Guide" (`sync`'s 3a–3g, `seek`, and the responsibilities of `diff`). This document won't repeat the theory.
- **How to read**: every step follows the pattern "what to port → what the real code looks like → self-check points".
- **Order**: engine (pure algorithm) first, then the container layer (DOM), and finally interaction and menus — matching the order in which this demo was actually written.
- **Running it**: just double-click `h5-demo/index.html` (local `file://` works, zero network dependencies), or serve it with `python3 -m http.server`.

## Step 0: Decide What to Replicate First

Porting is not "copying Java line by line into JS"; it's **first distinguishing which parts must align line by line and which parts can be replicated as needed**:

| Java side | H5 side | Degree of replication |
|---|---|---|
| `LayoutEngine` | `h5-demo/tile2d.js` | **Line-by-line alignment** (comments included); behavior must be identical |
| `LayoutModel` | `h5-demo/tile2d.js` | Line-by-line alignment |
| `TileLayout` (ViewGroup + real child Views) | `TileDomCore` (container + real DOM elements) | **Replicate as needed**: enter/leave, layout, scaling, recycling, hit-testing |
| `TileManager` (prefetch / dying zone) | —— | Not needed by the demo; drop it |
| `DimenManager` | The size table inside `TileDomCore` | As needed: per-column width / per-row height + defaults |
| `TileCoreService` (dispatch layer) | `main.js` + `TileDomCore` | As needed: keep only what the demo uses |
| Perlin noise + 24-color gradient | `h5-demo/noise.js` | **Bit-for-bit alignment** (same seed must produce the same image) |

In one sentence: **the engine and the RNG must be "exactly the same"; the container layer and the dispatch layer are "rewritten to intent".**

The sample has 4 files:

- `h5-demo/index.html` — page structure and styles (mobile-first, safe-area aware)
- `h5-demo/tile2d.js` — `LayoutModel` + `LayoutEngine` (line-by-line aligned) + `TileDomCore` (DOM container layer)
- `h5-demo/noise.js` — `java.util.Random` replica + Perlin noise + 24-color gradient
- `h5-demo/main.js` — adapter + DOM tile rendering + gestures (inertial scroll / pinch zoom) + menus

> **Unit tests do not go into the project**: this project requires test code to live only in a **temporary directory outside the project** (which also makes it easy to run isomorphic diffing against the Java version). `h5-demo/tile2d.js` keeps `module.exports` at the bottom precisely so external test scripts can `require` it.

## Step 1: Build the Page Skeleton (index.html)

Three key points for the container (mirroring the fact that `TileLayout` is a `ViewGroup`):

```css
#view {
    position: fixed;
    left: 0; top: 0;
    width: 100%;
    height: 100%;
    overflow: hidden;      /* Window clipping, equivalent to ViewGroup's clipChildren */
    touch-action: none;    /* Key: blocks default browser gestures (otherwise dragging is stolen by page scroll) */
    contain: strict;       /* Tells the browser: internal layout and paint don't overflow; more stable performance */
}

/* Tiles: real DOM elements (mirroring TileLayout.TileHolder.itemView) */
#view > .tile {
    position: absolute;
    left: 0; top: 0;       /* Position is entirely handled by transform */
    display: flex;
    align-items: center;   /* Corresponds to TextView's gravity=CENTER */
    justify-content: center;
    box-sizing: border-box;
    border: 0.5px solid #808080;   /* Corresponds to GradientDrawable.setStroke(0.5dp, GRAY) */
    will-change: transform;
    contain: layout paint style;
}
```

Three things for mobile adaptation: `<meta name="viewport" … viewport-fit=cover>`, using `env(safe-area-inset-*)` to leave a safe area for the top/bottom bars, and `html,body { overflow: hidden; overscroll-behavior: none; }` to prevent full-page rubber-banding.

Page structure (full version in `h5-demo/index.html`): `#view` (tile container) + `#topbar` (status pill + menu button) + `#hud` (debug panel) + `#toast` + `#sheet` (bottom drawer menu).

**Self-check**: the page opens on a phone, the page doesn't scroll along when dragging, and the tile container hugs all four screen edges (including the notch safe area).

## Step 2: Layout Model (LayoutModel)

The engine needs two models: `original` records the real state, `output` is the snapshot read from the outside. Compare with Java's `LayoutModel`:

```js
class LayoutModel {
    constructor() {
        this.colStart = 0; this.rowStart = 0;
        this.colEnd = -1; this.rowEnd = -1;      // Empty interval: [0,-1]
        this.offsetX = 0; this.offsetY = 0;
        this.contentWidth = 0; this.contentHeight = 0;
    }
    copyTo(m) { /* Copy all eight fields */ }
    newInstance() { const m = new LayoutModel(); this.copyTo(m); return m; }
    reset() { /* Back to the empty interval */ }
}
```

**Self-check**: `colEnd = -1`, `rowEnd = -1` mean "there is no content yet"; the engine's validity short-circuit relies on this convention.

## Step 3: Layout Engine (LayoutEngine) — Must Align Line by Line

The engine is the only part of the whole port where you "must not improvise". The ~500 lines in `h5-demo/tile2d.js` are an isomorphic translation of the Java version: not a single comment changed, not a single condition changed, only types swapped for JS `Number`. Below is the complete flow of `sync` (**consistent with the file**):

```text
sync(dx, dy):
    # 1. Validity short-circuit
    if window is out of bounds or window size <= 0:
        return false

    # 2. Horizontal sync (vertical is exactly analogous, swap columns for rows)
    if horizontalScrollEnabled:
        offsetX = original.offsetX + dx      # Offset accumulation lives inside this direction's branch

        # 3a. Content doesn't fill and right bound is reached: try right-alignment (tentative compensation)
        if contentWidth + offsetX < windowWidth and colEnd == rightBound:
            offsetX += windowWidth - (contentWidth + offsetX)

        # 3b. Dragging right, anchor moves left, bring in new columns
        while offsetX > 0 and colStart > leftBound:
            colStart--
            w = getColWidth(colStart)
            offsetX -= w
            contentWidth += w

        # 3c. Dragging left, anchor moves right, drop old columns
        startWidth = getColWidth(colStart)
        while offsetX < -startWidth and colStart < rightBound:
            offsetX += startWidth
            contentWidth -= startWidth
            colStart++
            startWidth = getColWidth(colStart)

        # 3c-2. Start leaps over the end anchor: let the end anchor follow
        if colStart > colEnd:
            colEnd = colStart
            contentWidth = startWidth

        # 3d. Left bound reached: clamp
        if offsetX > 0 and colStart == leftBound:
            offsetX = 0

        # 3e. Content doesn't fill: extend the right anchor
        while contentWidth + offsetX < windowWidth and colEnd < rightBound:
            colEnd++
            contentWidth += getColWidth(colEnd)

        # 3f. Content overflows too much: shrink the right anchor
        endWidth = getColWidth(colEnd)
        while contentWidth + offsetX - endWidth > windowWidth and colEnd > colStart:
            contentWidth -= endWidth
            colEnd--
            endWidth = getColWidth(colEnd)

        # 3g. The two loops above may leave a blank on the right: patch it (final-state compensation)
        if contentWidth > windowWidth and contentWidth + offsetX < windowWidth and colEnd == rightBound:
            offsetX += windowWidth - (contentWidth + offsetX)

        write back offsetX

    # 3. Vertical sync (same as above, swap columns for rows and offsetX for offsetY)

    # 4. Notify that the window has been calculated
    onWindowCalculated(colStart, rowStart, colEnd, rowEnd)

    # 5. When the range changes, update original and diff
    if range changed:
        update original
        diff(old range, new range)
```

The engine's remaining methods correspond one-to-one (full code in `h5-demo/tile2d.js`):

| Method | Purpose | Porting notes |
|---|---|---|
| `seek(column,row,offsetX,offsetY)` | Distance-independent jump | The accumulator **starts at `offsetX`**; subtract `Math.trunc(offsetX)` at the end (the two cancel out, leaving the pure sum of widths); treat the arguments as `dx/dy` and hand them to `sync` for fine-tuning |
| `diff` / `diffRegion` | Compute the difference between old and new windows; decide who goes `in` and who goes `out` | When completely disjoint, fall back to handling the whole block (extreme jumps rely on it) |
| `updateWidth/Height/Size` | Positional compensation after a size change | Go through the same path via `sync(dx,0)`; don't write a second implementation |
| `isAtLeftBound` etc. | Boundary checks | Exact comparison, **no tolerance** (reason below) |

**Five key constraints (do not change them while translating)**:

1. **Offset accumulation lives inside the direction branch**. `offsetX = original.offsetX + dx` must come before that direction's checks and loops, and take effect only in that direction. If you accumulate it at the top of the method, "the offsetX read in a check already contains dx", and the condition degenerates to always-true / always-false; also, when scrolling is disabled, dx gets written into the offset, breaking the invariant below.
2. **Invariant**: at the end of every frame, `offsetX ∈ [-current column width, 0]`, `offsetY ∈ [-current row height, 0]`. This is the basis of "pixel precision never degrades".
3. **3c-2 is not optional**. `3c` only moves the start anchor; when a single jump crosses the end anchor, `3e` will ask for the width of the just-crossed, already-subtracted columns again as "new columns" (nearly double traversal under large displacements). With this `if`, crossed columns are only ever asked once.
4. **The only thing you can save is the "second pass"**. The landing point is hidden in the sum of the widths of the crossed columns; with variable-width data there is no bypass (to skip N columns you must ask N columns), so "skip the traversal" cannot achieve O(1).
5. **3a and 3g are asymmetric for a reason**. `3a` is tentative compensation: the offset it produces has downstream backstops (`3b` consumes it by bringing in new columns; when there are no columns to bring in, `3d` forces it to zero); `3g` is final-state compensation: it runs and immediately writes the snapshot out, with no downstream that can undo it, so it must itself first prove the push is safe.

**Language difference checklist** (how the JS side handles them):

| Java | JS | Notes |
|---|---|---|
| `int` / `float` | `Number` | Double precision; exact within int32 range, sufficient for pixel scenarios |
| `(int) x` | `Math.trunc(x)` | Truncating cast; don't use `~~` (error-prone for negatives) |
| `Math.round` semantic difference | Not used | Java's `round` is "floor + 0.5", inconsistent across languages; this project has already removed its dependency on `LayoutEngine` |
| `TimeProvider` / `syncTime` | None | Debug code; delete the whole block when porting |
| Interfaces | Duck typing | It's enough that the object has methods like `getColWidth` / `in` / `out` |

**Self-check**: run the same set of inputs through Java and JS; the eight fields of `LayoutModel` must match bit for bit (see Step 7).

## Step 4: Container Layer (TileDomCore) — Tiles Are Real DOM Elements

This step corresponds to Java's `TileLayout` (ViewGroup). Four things:

1. **Entering/leaving the window = attaching/detaching elements**

```js
onTileIn(column, row) {
    const tile = this.obtain(type);           // Take from the recycling stack first; only create if none
    this.adapter.onBindTileHolder(tile, column, row);
    this.container.appendChild(tile.el);      // Corresponds to addViewInLayout
    this.active.set(key, tile);
}
onTileOut(column, row) {
    if (tile.el.parentNode === this.container) this.container.removeChild(tile.el);  // Corresponds to removeViewInLayout
    /* After detaching, push into the recycling stack for reuse by the next obtain */
}
```

2. **Layout = one pass over the visible range, writing only style**

```js
layoutTiles() {                       // Corresponds to TileLayout.layoutTiles
    let x = paddingLeft + this.scale(model.offsetX);
    for (let column = model.colStart; ; column++) {
        const width = this.scale(this.getTileWidth(column));
        let y = paddingTop + this.scale(model.offsetY);
        for (let row = model.rowStart; ; row++) {
            const height = this.scale(this.getTileHeight(row));
            const tile = this.getActiveTile(column, row);
            if (tile) {               // Only active tiles touch the DOM
                tile.el.style.transform = `translate3d(${x}px,${y}px,0)`;
                tile.el.style.width = width + 'px';
                tile.el.style.height = height + 'px';
                tile.el.style.fontSize = this.scale(14) + 'px';   // Font size scales too
            }
            y += height;
            if (row === model.rowEnd) break;
        }
        x += width;
        if (column === model.colEnd) break;
    }
}
```

> **Why `transform` instead of `left/top`**: `transform` only touches the compositor layer and doesn't trigger layout reflow; `left/top` makes the browser recompute layout every time.
> **Why write `width/height` and `fontSize` explicitly**: the DOM doesn't scale by itself — when `scaleFactor` changes, they must be recomputed (exactly the same as `TileLayout`'s `scale()`).

3. **Scaling = scaleFactor** (mirrors `TileLayout.scale(n) = n * getScaleFactor()`): both geometry and font size are multiplied by it. The window size the container hands to the engine is still computed from the container size; zoom only affects "how many pixels one cell occupies on screen".

4. **Hit-testing = findColumn / findRow**: convert screen coordinates back to content coordinates (subtract padding first, then divide by scale), and accumulate widths along the column/row.

The size table follows a simplified version of `DimenManager`'s three-level priority: **individually set > default** (the demo doesn't need `TileDimenProvider`).

**Self-check**: the number of child elements in the container equals the number of active tiles; while scrolling, `transform` is rewritten and elements scrolled off-screen are detached (you can watch the DOM tree in DevTools).

## Step 5: Data Source (noise.js)

The demo data is **generated deterministically**: fixed-seed Perlin noise + 24-color gradient, the same as the app's tile demo (seed `123456789`, scale `0.03`, sparse where noise `< 0.3`). This is also the premise on which "pseudo-infinite mode" stands — data can be computed from any coordinate, no pre-generation needed.

**Key: `java.util.Random` must be replicated bit for bit**. The Perlin noise's permutation array comes from shuffling with the seed (Fisher-Yates); the shuffle result determines the noise value at every coordinate, and being off by one bit changes the entire image. JS has no 48-bit integer, so use `BigInt`:

```js
class JavaRandom {
    constructor(seed) { this.seed = (BigInt(seed) ^ 0x5DEECE66Dn) & 0xFFFFFFFFFFFFn; }
    next(bits) {
        this.seed = (this.seed * 0x5DEECE66Dn + 0xBn) & 0xFFFFFFFFFFFFn;
        return Number(this.seed >> BigInt(48 - bits));
    }
    nextInt(bound) {
        if ((bound & -bound) === bound) return Number((BigInt(bound) * BigInt(this.next(31))) >> 31n);
        let bits, val;
        do { bits = this.next(31); val = bits % bound; } while (bits - val + (bound - 1) < 0);
        return val;
    }
}
```

`PerlinNoise2D` (fade / lerp / 8-direction gradients) and `ColorGenerator` (24-color linear interpolation + relative luminance to pick the text color) can be translated algorithm by algorithm.

**Self-check**: under the same seed, `perm[0..15]`, the `noiseNormalized` at several coordinates, the colors, and the text must all match the app side exactly (see Step 7).

## Step 6: Adapter and Interaction (main.js)

The **adapter** only needs four things (mirroring `TileLayout.Adapter`):

```js
const adapter = {
    getLeftBound / getTopBound / getRightBound / getBottomBound,   // In pseudo-infinite mode, return the int32 extremes
    getTileType: (c, r) => removed.has(c + ',' + r) || noiseAt(c, r) < 0.3 ? -1 : 0,
    onCreateTileHolder: (type) => type === -1 ? null : { type, el: makeTileDiv() },
    onBindTileHolder: (holder, c, r) => { /* backgroundColor + textColor + text = noise/0.03 with %.2f */ },
};
```

**Gestures** (all implemented on `#view` with Pointer Events):

| Gesture | Implementation notes |
|---|---|
| Single-finger drag | `dx = (current x - previous x) / scaleFactor` — dividing by scale is what makes it "follow the finger" |
| Inertial scroll | Record the velocity of the last 100ms (content px/ms); after release decay with `v *= 0.94^(dt/16.7)`; stop below the threshold |
| Two-finger pinch | Compute the new `scaleFactor` from the ratio of the two-finger distance; in `zoomTo(s, fx, fy)`, use `dx = (fx - padding) * (1/sNew - 1/sOld)` to pin the content point under the focus |
| Double tap | Two taps within 300ms → 1x ↔ 2x (same `zoomTo`, focus = tap point) |
| Long-press delete | 500ms timer + movement-threshold check → `removed.add(key)` + `core.update(c,r)` (the element is detached and recycled) |
| Single tap | Convert coordinates with `findColumn/findRow` → Toast |
| Desktop | `wheel` scroll; `Ctrl/⌘ + wheel` zoom (`{ passive: false }` is required for `preventDefault`) |

**Menus** mirror the app side's `BaseActivity` set: Debug mode, pseudo-infinite mode, random size adjustment (width/height, 2-second overshoot animation applied to the column/row at the center of the window), visit the bounds (eight directions + return to origin), view (zoom in / zoom out / reset zoom). Debug mode additionally provides a HUD: window size, window range, offset, content width/height, scale, active/recycled tile counts, DOM child count.

## Step 7: Aligning with the Java Version (How to Prove the Port Is Correct)

Three things, all done in a **temporary directory outside the project**:

1. **Isomorphic diffing**: run **the same random scenario sequence** through both implementations (same pseudo-random generator, same batch of column widths/row heights/window/displacements), and compare the eight fields of `LayoutModel` scenario by scenario.
  Measured in this project: mixed `sync` / `seek` / `updateWidth`, two-dimensional data, 4 seeds × 150 scenarios = **600 scenarios, zero difference**.
  > Note the pseudo-random generator's **multiplier must be small**: when the product exceeds 2^53, JS loses precision and the two sequences quietly drift apart (this pit has been stepped on).
2. **Bit-for-bit comparison of the data source**: the Java side prints sampled `perm` + the `noise` at several coordinates (`%.17f`), colors, and text; the JS side prints the same things; compare line by line.
  Measured in this project: 19 sample points show a noise deviation of **0**; colors and text are all identical.
3. **Invariant + large-displacement self-healing**: random walk for 2000 frames; check that `contentWidth/Height` always equals the sum of the tile sizes within the anchor span; `offset` is allowed to briefly exceed the range when "one frame crosses a data end", but one extra `sync(0,0)` frame must heal it.

## Step 8: Common Pitfalls

1. **Where the offset accumulation goes**: put it at the top of the method and the `dx` inside the checks gets cancelled out — the condition degenerates to always-true/always-false (`>=` always false, `<` always true; flipping the sign doesn't save it).
2. **`seek`'s accumulator initial value**: it must be `let contentWidth = Math.trunc(offsetX);` and then `- Math.trunc(offsetX)` at the end — the initial value and the minus sign cancel out, leaving the pure sum of widths. Starting from 0 while still subtracting `offsetX` at the end subtracts a whole extra `offsetX`, making `contentWidth` too small (when `offsetX>0`) or too large (when `offsetX<0`), and the extension loop's terminating column off by one.
3. **Integer truncation**: Java's `(int)` maps to `Math.trunc`; using `~~` for negatives is wrong.
4. **Pseudo-random precision**: multiplications like `seed * 1103515245` lose precision in JS once they exceed 2^53, and the two sequences drift; use a smaller multiplier or `BigInt`.
5. **The fill loop's boundary**: `while (contentWidth + offsetX < windowWidth && …)` uses **strictly less than** — exactly filling would lay one extra cell (11 columns instead of 10); this is the engine's existing behavior, don't "fix it in passing".
6. **3c-2 must not be removed**: without it, large displacements nearly double the traversal, and the intermediate state shows `contentWidth` inconsistent with the anchor span.
7. **No 64-bit integers**: use the string `"col,row"` for tile keys; parse it back into two integers when you need to compare by numeric value.
8. **Touch details**: the container needs `touch-action: none`; `setPointerCapture` ensures move events still arrive when the finger slides off the element; the `wheel` listener must be `{ passive: false }` to `preventDefault`; disable `contextmenu` on `document` to avoid the long-press menu.
9. **Don't touch `left/top/width/height` during dragging**: only write `transform`; write sizes only when the size changes.

## Step 9: Running and the Self-Check List

How to open: `h5-demo/index.html` (works directly via `file://`), or serve with `python3 -m http.server`. In the console there is `window.tile2dDemo`, so you can inspect `core` / `adapter` / `scaleFactor` directly.

Check each item:

- [ ] The first screen is filled with tiles, no holes inside the window; sparse areas (low noise) are indeed empty
- [ ] Dragging follows the finger: moving the finger 100px moves the content 100px (same when scale ≠ 1)
- [ ] Releasing has inertia and eventually stops; after stopping it no longer changes
- [ ] The **focus** of two-finger pinch zoom doesn't drift (the tile under the focus stays between the fingers)
- [ ] Double tap 1x ↔ 2x; menu zoom in/out/reset all take effect
- [ ] After long-press deleting a tile it disappears, and the corresponding element is detached from the DOM (not `display:none`)
- [ ] Pseudo-infinite mode: can reach the far left / far right (int32 extremes) and still work after coming back
- [ ] Random width/height adjustment has a 2-second animation, and the anchor doesn't jump around during it
- [ ] Tile text matches the app side at the same coordinate (e.g. `(0,0)` is `16.67`)

## Related Documents

- [Cross-Platform Porting Guide](Cross_Platform_Porting_Guide.md) — the engine's algorithms and invariants (the prerequisite reading for this document)
- [Android Platform Extension Guide](Android_Platform_Extension_Guide.md) — integrating Tile2D on Android
- [H5 sample directory](../../h5-demo/) — the real source of all the code in this document

---

> The content of this document was generated by AI.
