# H5 Porting Guide (Tile2D)

> This document is the H5 sub-document of the "Cross-Platform Porting Guide". Target platform: browser (JavaScript). Rendering: **DOM** — tiles are real elements, no Canvas self-drawing. A complete runnable sample lives in the project root under `h5-demo/` (5 files). Code blocks in this document only show **key snippets**; for **complete code always refer to the files under `h5-demo/`**.

## Overview

- Environment: vanilla JavaScript (ES6), no framework, no build tools; drop-in via `<script>`
- Rendering: tiles are real `div`s positioned absolutely; the content layer carries the pixel offset via `transform`
- Modules map one-to-one onto the Java version: `LayoutModel` / `LayoutEngine` / `TileManager` / `DimenManager` / `TileCoreService` / view layer
- Demo gameplay matches the app's Tile Painter demo: **pseudo-infinite mode** (bounds extended to the full int32 space) and **Visit the Bounds** (random jumps to 8 extreme points); data is generated from fixed-seed Perlin noise
- Key differences from the Java version:
  - **No 64-bit integers**: JS bitwise operations are 32-bit only, so tile IDs use the string `"col,row"` as key
  - **No boxing problem**: active/dying tiles use `Map` directly, no custom hash table needed
  - `Math.min/max` replaces the overloads; `Number` is double-precision and exact within int32 range

## Step 1: Project Structure

The sample has 5 files:

- `h5-demo/index.html` — container and styles
- `h5-demo/noise.js` — Perlin noise + 24-color gradient (demo data source)
- `h5-demo/tile2d.js` — engine + managers + dispatch layer + DOM view
- `h5-demo/main.js` — adapter + demo logic (pseudo-infinite / visit the bounds)
- `h5-demo/test.js` — node unit tests (neither engine nor noise depends on DOM, verifiable outside the browser)

The container needs three mandatory styles: `position: relative` (reference for the absolutely positioned content layer), `overflow: hidden` (window clipping), `touch-action: none` (blocks default browser gestures and keeps dragging usable).

```js
#view {
    position: relative;
    width: 800px;
    height: 450px;
    overflow: hidden;
    touch-action: none;
    /* remaining styles and buttons: see h5-demo/index.html */
}
...
<script src="noise.js"></script>
<script src="tile2d.js"></script>
<script src="main.js"></script>
```

> For the full file see `h5-demo/index.html`.

## Step 2: File Skeleton (tile2d.js)

The top of the file is a single comment describing the file's purpose and the string-key scheme:

```js
/*
 * Tile2D H5 port sample - pure JS implementation, no framework dependencies
 * Tile keys use the string "col,row" (JS bitwise ops are 32-bit only; the long encoding can't be carried over)
 */
```

The bottom of the file is the Node export branch: in a browser `module` doesn't exist, so this branch never runs and all classes stay global; in node you can `require` it and unit-test the engine directly:

```js
if (typeof module !== 'undefined' && module.exports) {
    module.exports = {
        LayoutModel, LayoutEngine, TileManager, DimenManager, TileCoreService, Tile2DView,
    };
}
```

## Step 3: Layout Model (LayoutModel)

A pure data object with the same fields as the Java version (closed-interval semantics, `start > end` means an empty window), plus the three methods `copyTo/newInstance/reset`:

```js
class LayoutModel {
    constructor() {
        this.colStart = 0;
        this.rowStart = 0;
        this.colEnd = -1;
        this.rowEnd = -1;
        this.offsetX = 0;
        this.offsetY = 0;
        this.totalWidth = 0;
        this.totalHeight = 0;
    }
    // Also copyTo / newInstance / reset, see h5-demo/tile2d.js
}
```

## Step 4: Layout Engine (LayoutEngine), Fully Ported

The engine is the heart of the port and must be **fully implemented**: window calculation, pixel precision and boundary behavior all live here; changing any single boundary condition makes scrolling diverge from the Java version.

The engine depends on only two interfaces (bounds, window interaction); in JS duck typing suffices — no explicit interfaces needed. The alignment constants match the Java version: `DIMEN_GRAVITY_CENTER = 0`, `DIMEN_GRAVITY_START = -1`, `DIMEN_GRAVITY_END = 1`.

The `sync` flow (same as the pseudocode in the Cross-Platform Porting Guide): validity short-circuit → offset accumulation → horizontal sync (right-alignment / anchor moves left / anchor moves right / clamp / right-anchor extension / right-anchor shrink) → vertical sync → write snapshot → notify window calculated → diff on range change. Key constraint: `offsetX` always stays within `[-current column width, 0]`, `offsetY` within `[-current row height, 0]`.

Horizontal sync of `sync` (anchor movement + column width accumulation):

```js
// 3b. Dragging right: anchor moves left, new columns come in
while (offsetX > 0 && colStart > leftBound) {
    colStart--;
    const w = this.window.getColWidth(colStart);
    offsetX -= w;
    totalWidth += w;
}
// 3c. Dragging left: anchor moves right, old columns go out
let startWidth = this.window.getColWidth(colStart);
while (offsetX < -startWidth && colStart < rightBound) {
    offsetX += startWidth;
    totalWidth -= startWidth;
    colStart++;
    startWidth = this.window.getColWidth(colStart);
}
```

The "completely disjoint" branch of `diff`:

```js
if (newColStart > oldColEnd || newRowStart > oldRowEnd ||
    newColEnd < oldColStart || newRowEnd < oldRowStart) {
    // Completely disjoint: everything in the old range goes out, everything in the new range comes in
    for (let x = oldColStart; x <= oldColEnd; x++) {
        for (let y = oldRowStart; y <= oldRowEnd; y++) this.window.out(x, y);
    }
    for (let x = newColStart; x <= newColEnd; x++) {
        for (let y = newRowStart; y <= newRowEnd; y++) this.window.in(x, y);
    }
    return;
}
```

> For the full implementation see `h5-demo/tile2d.js` (sync / seek / diff / size compensation, etc., behavior identical to the Java version, guaranteed by 25 node unit tests).

## Step 5: Tile Manager (TileManager)

Three-state pools (active/dying/recycled) + lifecycle + update operations, structurally identical to the Java version. Two JS-specific implementation points:

- Tile keys use the string `column + ',' + row` (see Overview)
- Dying-zone bounds use **cell-by-cell walking** instead of subtraction, avoiding overflow near `Integer.MIN_VALUE` (JS numbers are double-precision, but the boundary semantics must stay consistent with Java)

```js
class TileManager {
    // Tile key: string "col,row"
    static key(column, row) { return column + ',' + row; }

    in(column, row) {
        const key = TileManager.key(column, row);
        let tile = this.dying.get(key);
        if (tile) {
            // Reuse from the dying pool, skip binding
            this.dying.delete(key);
        } else {
            const type = this.callback.getTileType(column, row);
            tile = this.obtain(type);
            if (tile) {
                tile.column = column; tile.row = row;
                tile.width = this.callback.getTileWidth(column);
                tile.height = this.callback.getTileHeight(row);
                this.callback.onBindTileHolder(tile, column, row);
            }
        }
        if (tile) {
            this.active.set(key, tile);
            if (tile.onInWindow) tile.onInWindow();
            this.callback.onTileIn(tile, column, row);
        }
    }
    // Dying-zone bounds walk cell by cell, update operations, etc.: see h5-demo/tile2d.js
}
```

> For the full implementation see `h5-demo/tile2d.js`.

## Step 6: Dimension Manager (DimenManager)

Three-level priority: individually set > size provider > default. Size changes sync tiles inside the dying zone, compensate the window, and trigger a refresh:

```js
// Three-level priority: individually set > size provider > default
getTileWidth(column) {
    if (this.widths.has(column)) return this.widths.get(column);
    if (this.dimenProvider) return this.dimenProvider.getTileWidth(column);
    return this.defaultTileWidth;
}
```

> For the full implementation see `h5-demo/tile2d.js`.

## Step 7: Core Dispatch Layer (TileCoreService)

Wires the three modules together and implements the four interface groups (bounds, window, tile callbacks, size callbacks), exposing a unified API. The view layer deals only with it:

```js
class TileCoreService {
    constructor(coreInterface) {
        this.core = coreInterface;
        this.layoutEngine = new LayoutEngine(this, this);
        this.tileManager = new TileManager(this);
        this.dimenManager = new DimenManager(this);
        this.bounds = { left: 0, top: 0, right: 0, bottom: 0 };
    }
    // ---- Layout engine's boundary interface ----
    getLeftBound() { return this.core.getLeftBound(); }
    // ---- Layout engine's window interface ----
    in(column, row) { this.tileManager.in(column, row); }
    getColWidth(column) { return this.dimenManager.getTileWidth(column); }
    // Remaining interfaces and the unified API: see h5-demo/tile2d.js
}
```

> For the full implementation see `h5-demo/tile2d.js`.

## Step 8: DOM Rendering Layer (Tile2DView)

Why DOM instead of Canvas: `div`s naturally support click, hover, styles and animations; tiles are real elements, so content-type pages (text, images, forms) can be dropped straight into tiles with no hit-testing to implement.

The structure has two layers:

```text
Container #view (relative + overflow: hidden)
└─ Content div (absolute, transform: translate(offsetX, offsetY))
    └─ Tile divs (absolute, left/top = accumulated coordinates)
```

- **Offset via `transform`**: doesn't trigger reflow, and since `offset` is a pixel-level float, `transform` expresses it exactly
- **Tile positioning via `left/top`**: accumulate column widths/row heights cell by cell — variable sizes work naturally
- `onTileIn` attaches the tile element into the content layer; `onTileOut/onTileRecycled` removes it
- Input: pointer dragging → `sync(dx, dy)` (finger moves right → content moves right → offset increases); wheel → `sync(-deltaX, -deltaY)`

```js
// Content layer: absolutely positioned + transform carrying the offset; tiles absolutely positioned at accumulated coordinates
this.content = document.createElement('div');
this.content.style.position = 'absolute';
this.content.style.left = this.paddingLeft + 'px';
this.content.style.top = this.paddingTop + 'px';
this.content.style.transformOrigin = '0 0';
container.appendChild(this.content);
// Tile enter/leave callbacks (onTileIn / onTileOut), see h5-demo/tile2d.js
onTileIn: (holder) => { this.content.appendChild(holder.el); },
onTileOut: (holder) => { holder.el.remove(); },
```

> For the full implementation see `h5-demo/tile2d.js`.

## Step 9: Noise Texture (noise.js)

The demo data is **deterministically generated** with no server dependency: fixed-seed Perlin noise + 24-color gradient mapping, same as the app's Tile Painter demo (seed `123456789`, scale `0.03`, sparse where noise `< 0.3`). This is exactly the premise on which "pseudo-infinite mode" stands — data can be computed from any coordinate, no pre-generation needed.

`createRandom` is a 32-bit SplitMix64, guaranteeing the same seed shuffles reproducibly; `PerlinNoise2D` corresponds line-by-line to the Java version (8-direction gradients, fade interpolation); `colorFromNoise` interpolates linearly across the 24 colors, and `luminance` picks the foreground text color:

```js
// Deterministic RNG (SplitMix64, 32-bit edition): same seed, same shuffle, every time
function createRandom(seed) {
    let x = seed >>> 0;
    return function () {
        x = (x + 0x9E3779B9) >>> 0;
        let z = x;
        z = Math.imul(z ^ (z >>> 16), 0x21F0AAAD);
        z = Math.imul(z ^ (z >>> 15), 0x735A2D97);
        return (z ^ (z >>> 15)) >>> 0;
    };
}
// COLOR_TABLE (24-color gradient) and colorFromNoise / luminance: see h5-demo/noise.js
```

> For the full file see `h5-demo/noise.js`.

## Step 10: Adapter and Demo Logic (main.js)

The adapter has the same shape as the Java version: bounds, create, bind, type. Three demo highlights:

- **Perlin noise mapping**: `getTileType` marks cells sparse where noise `< 0.3`; `onBindTileHolder` maps `(noise - 0.3) / 0.7` onto the 24-color gradient
- **Pseudo-infinite mode**: bounds toggle between a finite range and `±2147483647`; after switching, call `snap()` to pull the window back into the legal range
- **Visit the bounds**: jumps to a randomly chosen one of the 8 extreme points (same as the app demo: topmost / top-right / rightmost / bottom-right / bottommost / bottom-left / leftmost / top-left)

```js
const adapter = {
    getLeftBound: () => maxMode ? MIN_INT : -50,
    getTopBound: () => maxMode ? MIN_INT : -100,
    getRightBound: () => maxMode ? MAX_INT : 50,
    getBottomBound: () => maxMode ? MAX_INT : 100,
    getTileType: (column, row) =>
        perlin.noiseNormalized(column * 0.03, row * 0.03) < 0.3 ? -1 : 0, // sparse in low-noise areas
    ...
};
// Pseudo-infinite mode: bounds toggle between the full int32 range and the finite range; snap pulls the window back
bind('btn-max', () => {
    maxMode = !maxMode;
    view.snap();
    ...
});
// Visit-the-bounds and other button logic: see h5-demo/main.js
```

> For the full file see `h5-demo/main.js`.

## Step 11: Running and Verifying

- Browser: just open `h5-demo/index.html` (local `file://` works, zero network dependencies), or serve with `python3 -m http.server`
- Node unit tests: `node h5-demo/test.js` — neither engine nor noise depends on DOM, verifiable outside the browser

The unit tests use fixed sizes (column width 80, row height 45) and a fixed window (800x450), so every expected value can be computed by hand — exactly the benefit of a pure-algorithm engine:

```js
// Fixed sizes: column width 80, row height 45; window 800x450
engine.seek(0, 0, 0, 0);
let m = engine.getLayoutModel();
assert(m.colStart === 0 && m.rowStart === 0, 'seek(0,0) anchors at the origin');
engine.sync(-400, -225);
m = engine.getLayoutModel();
assert(m.colStart === 4 && m.rowStart === 4, '400px left, anchor becomes (4,4)');
assert(m.offsetX === -80 && m.offsetY === -45, 'offset remainder (-80,-45)');
```

> For the full tests see `h5-demo/test.js` (25 assertions, including pseudo-infinite regression and noise boundaries). Test highlights: 400px left is exactly 5 columns, anchor becomes 4 with an offset remainder of -80; `seek(99,99)` leaves the content unable to fill the window, the anchor auto-refills to 90; widening column 0 automatically drops one column on the right; noise is reproducible for the same seed and samples correctly at int32 boundaries. This behavior is identical to the Java version.