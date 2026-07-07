package com.ahheng.tile2d;

import android.content.Context;
import android.graphics.Rect;
import android.os.Debug;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.Scroller;

import com.ahheng.tile2d.dimen.TileDimenProvider;
import com.ahheng.tile2d.tile.TileRecycledPool;
import com.ahheng.tile2d.util.IntIntMap;
import com.ahheng.tile2d.util.IntIntMapSparseArray;
import com.ahheng.tile2d.util.LongMap;
import com.ahheng.tile2d.util.LongMapSparseArray;

public class TileCoreService<T extends TileCoreService.BaseTileHolder> {

    private TileDimenProvider dimenProvider;
    private int defaultTileWidth;
    private int defaultTileHeight;

    private final Rect bounds = new Rect();
    private final CoreInterface<T> coreInterface;
    private final TileLayoutService layoutService = new TileLayoutService(new PlatformInterface());

    private LongMap<T> activeTiles;
    private LongMap<T> dyingTiles;
    private TileRecycledPool<T> recycledTiles;
    private IntIntMap widths;
    private IntIntMap heights;

    private int dyingColStart;
    private int dyingColEnd = -1;
    private int dyingRowStart;
    private int dyingRowEnd = -1;

    private final Scroller scroller;
    private final GestureDetector gestureDetector;
    private final int minVelocity;
    private final int maxVelocity;

    private boolean disallowIntercept;
    private boolean isInteractingWithView;
    private int lastScrollerX;
    private int lastScrollerY;

    private int recycledCount;
    private long startSyncTime;
    private long syncTime;
    private long startBindTime;
    private long bindTime;

    public TileCoreService(Context context, CoreInterface<T> coreInterface) {
        this.coreInterface = coreInterface;
        this.scroller = new Scroller(context);
        this.activeTiles = new LongMapSparseArray<>();
        this.dyingTiles = new LongMapSparseArray<>();
        this.widths = new IntIntMapSparseArray();
        this.heights = new IntIntMapSparseArray();
        this.recycledTiles = new TileRecycledPool<>();
        ViewConfiguration vc = ViewConfiguration.get(context);
        this.minVelocity = vc.getScaledMinimumFlingVelocity();
        this.maxVelocity = (int) (vc.getScaledMaximumFlingVelocity() * 0.8f);
        this.gestureDetector = new GestureDetector(context, new GestureListener());
        gestureDetector.setIsLongpressEnabled(false);
    }

    private class PlatformInterface implements TileLayoutService.PlatformInterface {

        @Override
        public int getTileWidth(int column) {
            return TileCoreService.this.getTileWidth(column);
        }

        @Override
        public int getTileHeight(int row) {
            return TileCoreService.this.getTileHeight(row);
        }

        @Override
        public void in(int column, int row) {
            TileCoreService.this.in(column, row);
        }

        @Override
        public void out(int column, int row) {
            TileCoreService.this.out(column, row);
        }

        @Override
        public void beforeDiff(int colStart, int rowStart, int colEnd, int rowEnd) {
            if (coreInterface.isDebugMode()) {
                syncTime = startSyncTime == 0 ? 0 : Debug.threadCpuTimeNanos() - startSyncTime;
                startBindTime = Debug.threadCpuTimeNanos();
            }
            if (dyingColStart != colStart || dyingColEnd != colEnd
                    || dyingRowStart != rowStart || dyingRowEnd != rowEnd) {
                // 边界变化了
                diffDying(colStart, rowStart, colEnd, rowEnd);
            }
        }

        @Override
        public int getLeftBound() {
            return coreInterface.getLeftBound();
        }

        @Override
        public int getTopBound() {
            return coreInterface.getTopBound();
        }

        @Override
        public int getRightBound() {
            return coreInterface.getRightBound();
        }

        @Override
        public int getBottomBound() {
            return coreInterface.getBottomBound();
        }
    }

    public void updateUI() {
        coreInterface.updateUI();
    }

    public boolean isHorizontalScrollEnabled() {
        return layoutService.isHorizontalScrollEnabled();
    }

    public boolean isVerticalScrollEnabled() {
        return layoutService.isVerticalScrollEnabled();
    }

    public void setHorizontalScrollEnabled(boolean enabled) {
        layoutService.setHorizontalScrollEnabled(enabled);
    }

    public void setVerticalScrollEnabled(boolean enabled) {
        layoutService.setVerticalScrollEnabled(enabled);
    }

    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        this.disallowIntercept = disallowIntercept;
    }

    public boolean isInteractingWithView() {
        return isInteractingWithView;
    }

    public void handleTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();

        if (action == MotionEvent.ACTION_DOWN) {
            disallowIntercept = false;
            isInteractingWithView = false;

            resetAnimator();
        }

        if (!disallowIntercept) {
            gestureDetector.onTouchEvent(event);
        }
        
        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
            disallowIntercept = false;
            isInteractingWithView = false;
        }
    }

    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            int currX = scroller.getCurrX();
            int currY = scroller.getCurrY();
            float dx = currX - lastScrollerX;
            float dy = currY - lastScrollerY;
            lastScrollerX = currX;
            lastScrollerY = currY;

            boolean scrolled = dx != 0 && layoutService.isHorizontalScrollEnabled();
            if (dy != 0 && layoutService.isVerticalScrollEnabled()) scrolled = true;

            if (scrolled) {
                sync(dx, dy);
            } else {
                updateUI();
            }
        }
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            boolean scrolled = false;
            float dx = 0, dy = 0;
            if (layoutService.isHorizontalScrollEnabled()) {
                dx = -distanceX;
                scrolled = true;
            }
            if (layoutService.isVerticalScrollEnabled()) {
                dy = -distanceY;
                scrolled = true;
            }
            isInteractingWithView = scrolled;
            if (scrolled) {
                sync(dx, dy);
                return true;
            }
            return false;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            boolean flingX = false, flingY = false;

            if (layoutService.isHorizontalScrollEnabled() && Math.abs(velocityX) >= minVelocity) {
                velocityX = velocityX < 0 ? Math.max(velocityX, -maxVelocity) : Math.min(velocityX, maxVelocity);
                flingX = true;
            } else {
                velocityX = 0;
            }

            if (layoutService.isVerticalScrollEnabled() && Math.abs(velocityY) >= minVelocity) {
                velocityY = velocityY < 0 ? Math.max(velocityY, -maxVelocity) : Math.min(velocityY, maxVelocity);
                flingY = true;
            } else {
                velocityY = 0;
            }

            if (!flingX && !flingY) {
                return false;
            }

            lastScrollerX = 0;
            lastScrollerY = 0;
            scroller.fling(0, 0, (int) velocityX, (int) velocityY,
                    layoutService.isHorizontalScrollEnabled() ? Integer.MIN_VALUE : 0,
                    layoutService.isHorizontalScrollEnabled() ? Integer.MAX_VALUE : 0,
                    layoutService.isVerticalScrollEnabled() ? Integer.MIN_VALUE : 0,
                    layoutService.isVerticalScrollEnabled() ? Integer.MAX_VALUE : 0);
            updateUI();
            return true;
        }
    }

    private void diffDying(int colStart, int rowStart, int colEnd, int rowEnd) {
        dyingColStart = colStart;
        dyingColEnd = colEnd;
        dyingRowStart = rowStart;
        dyingRowEnd = rowEnd;

        int left = getDyingLeft();
        int top = getDyingTop();
        int right = getDyingRight();
        int bottom = getDyingBottom();

        LongMap.Iterator<T> it = dyingTiles.iterator(true);
        while (it.next()) {
            long id = it.key();
            int c = getColumn(id);
            int r = getRow(id);
            if (c < left || c > right || r < top || r > bottom) {
                T tile = it.value();
                it.remove();
                recycle(tile);
            }
        }
    }

    public void reset() {
        // 清理活跃瓦片
        LongMap.Iterator<T> itActive = activeTiles.iterator();
        while (itActive.next()) {
            long id = itActive.key();
            T tile = itActive.value();
            coreInterface.onTileOut(tile, getColumn(id), getRow(id));
            if (tile != null) tile.onOutWindow();
            recycle(tile);
        }
        activeTiles.clear();
    
        // 清理濒死瓦片
        LongMap.Iterator<T> itDying = dyingTiles.iterator();
        while (itDying.next()) {
            recycle(itDying.value());
        }
        dyingTiles.clear();

        // 清理缓存
        recycledTiles.reset();
        widths.clear();
        heights.clear();

        // 清理状态
        dyingColStart =
        dyingRowStart = 0;
        dyingColEnd =
        dyingRowEnd = -1;
        disallowIntercept = false;
        isInteractingWithView = false;
        lastScrollerX = lastScrollerY = 0;
        recycledCount = 0;

        layoutService.reset();
        resetAnimator();
    }

    public void resetAnimator() {
        if (!scroller.isFinished()) scroller.abortAnimation();
    }

    public void sync(float dx, float dy) {
        boolean debugMode = coreInterface.isDebugMode();
        if (debugMode) startSyncTime = Debug.threadCpuTimeNanos();
        coreInterface.beforeLayout();
        layoutService.sync(dx, dy);
        if (debugMode) {
            bindTime = startBindTime == 0 ? 0 : Debug.threadCpuTimeNanos() - startBindTime;
        }
        updateUI();
    }

    public void seek(int column, int row, float offsetX, float offsetY) {
        if (isEmpty()) return;
        // 清理瓦片
        LongMap.Iterator<T> itActive = activeTiles.iterator();
        while (itActive.next()) {
            T tile = itActive.value();
            long id = itActive.key();
            tile.onOutWindow();
            coreInterface.onTileOut(tile, getColumn(id), getRow(id));
            recycle(tile);
        }
        activeTiles.clear();
        LongMap.Iterator<T> itDying = dyingTiles.iterator();
        while (itDying.next()) {
            recycle(itDying.value());
        }
        dyingTiles.clear();
        
        coreInterface.beforeLayout();
        layoutService.seek(column, row, offsetX, offsetY);
        updateUI();
    }

    public void snap() {
        if (isEmpty()) {
            return;
        }
        TileLayoutModel model = getLayoutModel();
        int left = coreInterface.getLeftBound();
        int top = coreInterface.getTopBound();
        int right = coreInterface.getRightBound();
        int bottom = coreInterface.getBottomBound();

        if (model.colStart >= left &&
                model.colEnd <= right &&
                model.rowStart >= top &&
                model.rowEnd <= bottom) {
            return;
        }
        int column = Math.max(left, Math.min(model.colStart, right));
        int row = Math.max(top, Math.min(model.rowStart, bottom));
        seek(column, row, 0, 0); // 让 seek 和 sync 完成窗口填充
    }

    public void in(int column, int row) {
        long id = getTileId(column, row);
        T tile = dyingTiles.remove(id);
        if (tile == null) {
            int type = coreInterface.getTileType(column, row);
            tile = obtain(type);
            if (tile != null) {
                int width = getTileWidth(column);
                int height = getTileHeight(row);
                ((BaseTileHolder) tile).column = column;
                ((BaseTileHolder) tile).row = row;
                ((BaseTileHolder) tile).width = width;
                ((BaseTileHolder) tile).height = height;
                coreInterface.onBindTileHolder(tile, column, row);
                tile.onSizeChanged(width, height);
                coreInterface.onTileSizeChanged(tile, column, row, width, height);
            }
        }
        if (tile != null) {
            activeTiles.put(id, tile);
            tile.onInWindow();
            coreInterface.onTileIn(tile, column, row);
        }
    }

    public void out(int column, int row) {
        long id = getTileId(column, row);
        T tile = activeTiles.remove(id);
        if (tile != null) {
            tile.onOutWindow();
            coreInterface.onTileOut(tile, column, row);
            dyingTiles.put(id, tile);
        }
    }

    public T obtain(int type) {
        T tile = recycledTiles.get(type);
        if (tile != null) {
            recycledCount--;
            return tile;
        }
        tile = coreInterface.onCreateTileHolder(type);
        if (tile != null) ((BaseTileHolder) tile).type = type;
        return tile;
    }

    public void recycle(T tile) {
        if (tile == null) return;
        recycledTiles.recycle(((BaseTileHolder) tile).type, tile);
        tile.onRecycled();
        coreInterface.onTileRecycled(tile, ((BaseTileHolder) tile).column, ((BaseTileHolder) tile).row);
        recycledCount++;
    }

    public T getActiveTile(int column, int row) {
        long id = getTileId(column, row);
        return activeTiles.get(id);
    }

    public int getTileWidth(int column) {
        if (widths.containsKey(column)) {
            return widths.get(column);
        }
        if (dimenProvider != null) {
            return dimenProvider.getTileWidth(column);
        }
        return defaultTileWidth;
    }

    public int getTileHeight(int row) {
        if (heights.containsKey(row)) {
            return heights.get(row);
        }
        if (dimenProvider != null) {
            return dimenProvider.getTileHeight(row);
        }
        return defaultTileHeight;
    }

    public void setTileSize(int column, int width, int horizontalGravity, int row, int height, int verticalGravity) {
        if (isEmpty()) return;
        if (column > coreInterface.getRightBound() || column < coreInterface.getLeftBound())
            throw new IndexOutOfBoundsException("列索引 " + column + " 不在 [" + coreInterface.getLeftBound() + "," + coreInterface.getRightBound() + "] 范围内");
        if (row > coreInterface.getBottomBound() || row < coreInterface.getTopBound())
            throw new IndexOutOfBoundsException("行索引 " + row + " 不在 [" + coreInterface.getTopBound() + "," + coreInterface.getBottomBound() + "] 范围内");
        int oldWidth = getTileWidth(column);
        int oldHeight = getTileHeight(row);
    
        if (width == 0) {
            widths.remove(column);
            width = getTileWidth(column);
        } else {
            widths.put(column, width);
        }
        boolean widthChanged = (width != oldWidth);
    
        if (height == 0) {
            heights.remove(row);
            height = getTileHeight(row);
        } else {
            heights.put(row, height);
        }
        boolean heightChanged = (height != oldHeight);
    
        if (!widthChanged && !heightChanged) return;

        if (widthChanged) {
            int dyingLeft = getDyingLeft();
            int dyingRight = getDyingRight();
            if (column >= dyingLeft && column <= dyingRight) {
                int r = getDyingTop();
                int end = getDyingBottom();
                while (r <= end) {
                    if (r != row) resizeTile(column, r, width, getTileHeight(r));
                    if (r == end) break;
                    r++;
                }
            }
        }
        if (heightChanged) {
            int dyingTop = getDyingTop();
            int dyingBottom = getDyingBottom();
            if (row >= dyingTop && row <= dyingBottom) {
                int c = getDyingLeft();
                int end = getDyingRight();
                while (c <= end) {
                    if (c != column) resizeTile(c, row, getTileWidth(c), height);
                    if (c == end) break;
                    c++;
                }
            }
        }
        resizeTile(column, row, width, height);
    
        if (coreInterface.isDebugMode()) startSyncTime = Debug.threadCpuTimeNanos();
        coreInterface.beforeLayout();
        layoutService.updateSize(
            column, oldWidth, width, horizontalGravity, 
            row, oldHeight, height, verticalGravity
        );
        updateUI();
    }

    public void deleteTileWidth(int column, int gravity) {
        setTileWidth(column, 0, gravity);
    }

    public void setTileWidth(int column, int width, int gravity) {
        if (isEmpty()) return;
        if (column > coreInterface.getRightBound() || column < coreInterface.getLeftBound())
            throw new IndexOutOfBoundsException("列索引 " + column + " 不在 [" + coreInterface.getLeftBound() + "," + coreInterface.getRightBound() + "] 范围内");
        int old = getTileWidth(column);
        if (width == 0) {
            widths.remove(column);
            width = getTileWidth(column);
        } else {
            widths.put(column, width);
        }
        if (width == old) return;

        int dyingLeft = getDyingLeft();
        int dyingRight = getDyingRight();
        if (column >= dyingLeft && column <= dyingRight) {
            int row = getDyingTop();
            int end = getDyingBottom();
            while (row <= end) {
                resizeTile(column, row, width, getTileHeight(row));
                if (row == end) break;
                row++;
            }
        }

        if (coreInterface.isDebugMode()) startSyncTime = Debug.threadCpuTimeNanos();
        coreInterface.beforeLayout();
        layoutService.updateWidth(column, old, width, gravity);
        updateUI();
    }

    public void deleteTileHeight(int row, int gravity) {
        setTileHeight(row, 0, gravity);
    }

    public void setTileHeight(int row, int height, int gravity) {
        if (isEmpty()) return;
        if (row > coreInterface.getBottomBound() || row < coreInterface.getTopBound())
            throw new IndexOutOfBoundsException("行索引 " + row + " 不在 [" + coreInterface.getTopBound() + "," + coreInterface.getBottomBound() + "] 范围内");
        int old = getTileHeight(row);
        if (height == 0) {
            heights.remove(row);
            height = getTileHeight(row);
        } else {
            heights.put(row, height);
        }
        if (height == old) return;

        int dyingTop = getDyingTop();
        int dyingBottom = getDyingBottom();
        if (row >= dyingTop && row <= dyingBottom) {
            int column = getDyingLeft();
            int end = getDyingRight();
            while (column <= end) {
                resizeTile(column, row, getTileWidth(column), height);
                if (column == end) break;
                column++;
            }
        }

        if (coreInterface.isDebugMode()) startSyncTime = Debug.threadCpuTimeNanos();
        coreInterface.beforeLayout();
        layoutService.updateHeight(row, old, height, gravity);
        updateUI();
    }

    public float getTileX(int column) {
        TileLayoutModel model = layoutService.getLayoutModel();
        float x = bounds.left + model.offsetX;
        int c = model.colStart;
        while (c < column) {
            x += getTileWidth(c);
            c++;
        }
        while (c > column) {
            c--;
            x -= getTileWidth(c);
        }
        return x;
    }

    public float getTileY(int row) {
        TileLayoutModel model = layoutService.getLayoutModel();
        float y = bounds.top + model.offsetY;
        int r = model.rowStart;
        while (r < row) {
            y += getTileHeight(r);
            r++;
        }
        while (r > row) {
            r--;
            y -= getTileHeight(r);
        }
        return y;
    }

    public int findColumn(float x) {
        TileLayoutModel model = layoutService.getLayoutModel();
        int leftBound = coreInterface.getLeftBound();
        int rightBound = coreInterface.getRightBound();
        int col = model.colStart;

        if (col > rightBound) return leftBound;

        float currX = bounds.left + model.offsetX;
        while (col > leftBound && x < currX) {
            col--;
            currX -= getTileWidth(col);
        }
        while (col < rightBound && x >= currX + getTileWidth(col)) {
            currX += getTileWidth(col);
            col++;
        }
        return col;
    }

    public int findRow(float y) {
        TileLayoutModel model = layoutService.getLayoutModel();
        int topBound = coreInterface.getTopBound();
        int bottomBound = coreInterface.getBottomBound();
        int row = model.rowStart;

        if (row > bottomBound) return topBound;

        float currY = bounds.top + model.offsetY;
        while (row > topBound && y < currY) {
            row--;
            currY -= getTileHeight(row);
        }
        while (row < bottomBound && y >= currY + getTileHeight(row)) {
            currY += getTileHeight(row);
            row++;
        }
        return row;
    }

    public void update(int column, int row) {
        if (column >= getDyingLeft() &&
                column <= getDyingRight() &&
                row >= getDyingTop() &&
                row <= getDyingBottom()) {
            coreInterface.beforeLayout();
            rebuildTile(column, row);
            updateUI();
        }
    }

    public void updateRange(int left, int top, int right, int bottom) {
        if (left > right || top > bottom) {
            return;
        }
        int dl = getDyingLeft();
        int dr = getDyingRight();
        int dt = getDyingTop();
        int db = getDyingBottom();
    
        int intersectLeft   = Math.max(left, dl);
        int intersectRight  = Math.min(right, dr);
        int intersectTop    = Math.max(top, dt);
        int intersectBottom = Math.min(bottom, db);
    
        if (intersectLeft > intersectRight || intersectTop > intersectBottom) {
            return;
        }
        coreInterface.beforeLayout();

        int c = intersectLeft;
        while (c <= intersectRight) {
            int r = intersectTop;
            while (r <= intersectBottom) {
                rebuildTile(c, r);
                if (r == intersectBottom) break;
                r++;
            }
            if (c == intersectRight) break;
            c++;
        }
    
        updateUI();
    }

    public void updateColumn(int column) {
        if (column >= getDyingLeft() && column <= getDyingRight()) {
            coreInterface.beforeLayout();
            int row = getDyingTop();
            int end = getDyingBottom();
            while (row <= end) {
                rebuildTile(column, row);
                if (row == end) break;
                row++;
            }
            updateUI();
        }
    }

    public void updateRow(int row) {
        if (row >= getDyingTop() && row <= getDyingBottom()) {
            coreInterface.beforeLayout();
            int column = getDyingLeft();
            int end = getDyingRight();
            while (column <= end) {
                rebuildTile(column, row);
                if (column == end) break;
                column++;
            }
            updateUI();
        }
    }

    public void updateAll() {
        TileLayoutModel model = layoutService.getLayoutModel();
        seek(model.colStart, model.rowStart, model.offsetX, model.offsetY);
    }

    private void rebuildTile(int column, int row) {
        long id = getTileId(column, row);
        TileLayoutModel model = layoutService.getLayoutModel();
        T tile;
        if (column >= model.colStart && column <= model.colEnd &&
                row >= model.rowStart && row <= model.rowEnd) {
            // 在活跃区
            tile = activeTiles.get(id);
            if (tile != null) {
                activeTiles.remove(id);
                tile.onOutWindow();
                coreInterface.onTileOut(tile, column, row);
                recycle(tile);
            }
            in(column, row);
        } else {
            // 在濒死区里
            tile = dyingTiles.get(id);
            if (tile != null) {
                dyingTiles.remove(id);
                recycle(tile);
            }
            int type = coreInterface.getTileType(column, row);
            T newTile = obtain(type);
            if (newTile != null) {
                int width = getTileWidth(column);
                int height = getTileHeight(row);
                ((BaseTileHolder) newTile).column = column;
                ((BaseTileHolder) newTile).row = row;
                ((BaseTileHolder) newTile).width = width;
                ((BaseTileHolder) newTile).height = height;
                coreInterface.onBindTileHolder(newTile, column, row);
                newTile.onSizeChanged(width, height);
                coreInterface.onTileSizeChanged(newTile, column, row, width, height);
                dyingTiles.put(id, newTile);
            }
        }
    }

    private void resizeTile(int column, int row, int width, int height) {
        long id = getTileId(column, row);
        T tile = activeTiles.get(id);
        if (tile == null) {
            tile = dyingTiles.get(id);
        }
        if (tile != null) {
            if (width == ((BaseTileHolder) tile).width &&
                height == ((BaseTileHolder) tile).height) {
                return;
            }
            ((BaseTileHolder) tile).width = width;
            ((BaseTileHolder) tile).height = height;
            tile.onSizeChanged(width, height);
            coreInterface.onTileSizeChanged(tile, column, row, width, height);
        }
    }

    public TileDimenProvider getDimenProvider() {
        return dimenProvider;
    }

    public void setDimenProvider(TileDimenProvider dimenProvider) {
        this.dimenProvider = dimenProvider;
    }

    public Rect getBounds() {
        return bounds;
    }

    public void setBounds(int left, int top, int right, int bottom) {
        bounds.set(left, top, right, bottom);
        layoutService.setWindowWidth(bounds.width());
        layoutService.setWindowHeight(bounds.height());
    }

    public int getDefaultTileWidth() {
        return defaultTileWidth;
    }

    public int getDefaultTileHeight() {
        return defaultTileHeight;
    }

    public void setDefaultTileWidth(int width) {
        if (width <= 0) throw new IllegalArgumentException("宽度必须大于 0");
        this.defaultTileWidth = width;
    }

    public void setDefaultTileHeight(int height) {
        if (height <= 0) throw new IllegalArgumentException("高度必须大于 0");
        this.defaultTileHeight = height;
    }

    public TileLayoutModel getLayoutModel() {
        return layoutService.getLayoutModel();
    }

    public TileLayoutService getLayoutService() {
        return layoutService;
    }

    public LongMap<T> getDyingTiles() {
        return dyingTiles;
    }

    public int getActiveTileCount() {
        return activeTiles.size();
    }

    public int getRecycledTileCount() {
        return recycledCount;
    }

    public long getSyncTime() {
        return syncTime;
    }

    public long getBindTime() {
        return bindTime;
    }

    public boolean isEmpty() {
        return coreInterface.getLeftBound() > coreInterface.getRightBound() || coreInterface.getTopBound() > coreInterface.getBottomBound();
    }

    public boolean isAtLeftBound() {
        return !isEmpty() && layoutService.isAtLeftBound();
    }

    public boolean isAtTopBound() {
        return !isEmpty() && layoutService.isAtTopBound();
    }

    public boolean isAtRightBound() {
        return !isEmpty() && layoutService.isAtRightBound();
    }

    public boolean isAtBottomBound() {
        return !isEmpty() && layoutService.isAtBottomBound();
    }

    public int getDyingLeft() {
        return dyingColStart > coreInterface.getLeftBound() ? dyingColStart - 1 : coreInterface.getLeftBound();
    }

    public int getDyingTop() {
        return dyingRowStart > coreInterface.getTopBound() ? dyingRowStart - 1 : coreInterface.getTopBound();
    }

    public int getDyingRight() {
        return dyingColEnd < coreInterface.getRightBound() ? dyingColEnd + 1 : coreInterface.getRightBound();
    }

    public int getDyingBottom() {
        return dyingRowEnd < coreInterface.getBottomBound() ? dyingRowEnd + 1 : coreInterface.getBottomBound();
    }

    public void setActiveTiles(LongMap<T> map) {
        if (map == null) throw new IllegalArgumentException("activeTiles map cannot be null");
        if (map == activeTiles) return;
        map.clear();
        for (LongMap.Iterator<T> it = activeTiles.iterator(); it.next(); ) {
            map.put(it.key(), it.value());
        }
        activeTiles.clear();
        activeTiles = map;
    }
    
    public void setDyingTiles(LongMap<T> map) {
        if (map == null) throw new IllegalArgumentException("dyingTiles map cannot be null");
        if (map == dyingTiles) return;
        map.clear();
        for (LongMap.Iterator<T> it = dyingTiles.iterator(); it.next(); ) {
            map.put(it.key(), it.value());
        }
        dyingTiles.clear();
        dyingTiles = map;
    }
    
    public void setWidths(IntIntMap map) {
        if (map == null) throw new IllegalArgumentException("widths map cannot be null");
        if (map == widths) return;
        map.clear();
        for (IntIntMap.Iterator it = widths.iterator(); it.next(); ) {
            map.put(it.key(), it.value());
        }
        widths.clear();
        widths = map;
    }
    
    public void setHeights(IntIntMap map) {
        if (map == null) throw new IllegalArgumentException("heights map cannot be null");
        if (map == heights) return;
        map.clear();
        for (IntIntMap.Iterator it = heights.iterator(); it.next(); ) {
            map.put(it.key(), it.value());
        }
        heights.clear();
        heights = map;
    }
    
    public void setRecycledTiles(TileRecycledPool<T> map) {
        if (map == null) throw new IllegalArgumentException("recycledTiles map cannot be null");
        if (map == recycledTiles) return;
        map.reset();
        recycledTiles.moveTo(map);
        recycledTiles = map;
    }

    public static long getTileId(int column, int row) {
        return ((long) column << 32) | (row & 0xFFFFFFFFL);
    }

    public static int getColumn(long id) {
        return (int) (id >> 32);
    }

    public static int getRow(long id) {
        return (int) (id & 0xFFFFFFFFL);
    }

    public static class BaseTileHolder {

        private int type;
        private int column;
        private int row;
        private int width;
        private int height;

        public void onRecycled() {
        }

        public void onInWindow() {
        }

        public void onOutWindow() {
        }
        
        public void onSizeChanged(int width, int height) {
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }

        public int getColumn() {
            return column;
        }

        public int getRow() {
            return row;
        }

        public int getType() {
            return type;
        }

    }

    public interface CoreInterface<T extends BaseTileHolder> {

        void beforeLayout();

        void updateUI();

        void onTileIn(T holder, int column, int row);

        void onTileOut(T holder, int column, int row);

        void onTileRecycled(T holder, int column, int row);

        void onTileSizeChanged(T holder, int column, int row, int width, int height);

        int getLeftBound();

        int getTopBound();

        int getRightBound();

        int getBottomBound();

        T onCreateTileHolder(int type);

        void onBindTileHolder(T holder, int column, int row);

        int getTileType(int column, int row);

        boolean isDebugMode();

    }

}
