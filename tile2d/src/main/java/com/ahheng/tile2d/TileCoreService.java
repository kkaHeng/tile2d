package com.ahheng.tile2d;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.os.Debug;
import android.util.LongSparseArray;
import android.util.SparseIntArray;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.widget.Scroller;

import com.ahheng.tile2d.widget.TileDimenProvider;
import com.ahheng.tile2d.widget.tile.TileRecycledPool;

public class TileCoreService<T extends TileCoreService.BaseTileHolder> {

    private TileDimenProvider dimenProvider;
    private int defaultTileWidth;
    private int defaultTileHeight;

    private final Rect bounds = new Rect();
    private final CoreInterface<T> coreInterface;
    private final TileLayoutService layoutService = new TileLayoutService(new PlatformService());

    private final LongSparseArray<T> activeTiles = new LongSparseArray<>();
    private final LongSparseArray<T> dyingTiles = new LongSparseArray<>();
    private final TileRecycledPool<T> recycledTiles = new TileRecycledPool<>();
    private final SparseIntArray widths = new SparseIntArray();
    private final SparseIntArray heights = new SparseIntArray();

    private int dyingColStart;
    private int dyingColEnd = -1;
    private int dyingRowStart;
    private int dyingRowEnd = -1;

    private final Scroller scroller;
    private final GestureDetector gestureDetector;
    private ValueAnimator smoothAnimator;
    private final int minVelocity;
    private final int maxVelocity;

    private boolean disallowIntercept;
    private boolean isInteractingWithView;
    private int lastScrollerX;
    private int lastScrollerY;
    private float lastSmoothProgress;

    private boolean horizontalScrollEnabled = true;
    private boolean verticalScrollEnabled = true;

    private int recycledCount;
    private long startSyncTime;
    private long syncTime;
    private long startLayoutTime;
    private long layoutTime;

    public TileCoreService(Context context, CoreInterface<T> coreInterface) {
        this.coreInterface = coreInterface;
        this.scroller = new Scroller(context);
        ViewConfiguration vc = ViewConfiguration.get(context);
        this.minVelocity = vc.getScaledMinimumFlingVelocity();
        this.maxVelocity = (int) (vc.getScaledMaximumFlingVelocity() * 0.8f);
        this.gestureDetector = new GestureDetector(context, new GestureListener());
        gestureDetector.setIsLongpressEnabled(false);
    }

    private class PlatformService implements TileLayoutService.PlatformService {

        @Override
        public int getWindowWidth() {
            return bounds.width();
        }

        @Override
        public int getWindowHeight() {
            return bounds.height();
        }

        @Override
        public int getTileWidth(int column) {
            return TileCoreService.this.getTileWidth(column);
        }

        @Override
        public int getTileHeight(int row) {
            return TileCoreService.this.getTileHeight(row);
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

        @Override
        public boolean isHorizontalScrollEnabled() {
            return horizontalScrollEnabled;
        }

        @Override
        public boolean isVerticalScrollEnabled() {
            return verticalScrollEnabled;
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
            syncTime = Debug.threadCpuTimeNanos() - startSyncTime;
            startLayoutTime = Debug.threadCpuTimeNanos();
            if (dyingColStart != colStart || dyingColEnd != colEnd
                    || dyingRowStart != rowStart || dyingRowEnd != rowEnd) {
                // 边界变化了
                diffDying(colStart, rowStart, colEnd, rowEnd);
            }
        }
    }

    public boolean isHorizontalScrollEnabled() {
        return horizontalScrollEnabled;
    }

    public void setHorizontalScrollEnabled(boolean enabled) {
        this.horizontalScrollEnabled = enabled;
    }

    public boolean isVerticalScrollEnabled() {
        return verticalScrollEnabled;
    }

    public void setVerticalScrollEnabled(boolean enabled) {
        this.verticalScrollEnabled = enabled;
    }

    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        this.disallowIntercept = disallowIntercept;
    }

    public boolean isInteractingWithView() {
        return isInteractingWithView;
    }

    public boolean handleTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();

        if (action == MotionEvent.ACTION_DOWN) {
            disallowIntercept = false;
            isInteractingWithView = false;

            if (scroller.computeScrollOffset()) {
                scroller.abortAnimation();
            }
        }

        if (!disallowIntercept) {
            gestureDetector.onTouchEvent(event);
        }
        return true;
    }

    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            int currX = scroller.getCurrX();
            int currY = scroller.getCurrY();
            float dx = currX - lastScrollerX;
            float dy = currY - lastScrollerY;
            lastScrollerX = currX;
            lastScrollerY = currY;

            boolean scrolled = dx != 0 && horizontalScrollEnabled;
            if (dy != 0 && verticalScrollEnabled) scrolled = true;

            if (scrolled) {
                sync(dx, dy);
            } else {
                coreInterface.updateUI();
            }
        }
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            boolean scrolled = false;
            float dx = 0, dy = 0;
            if (horizontalScrollEnabled) {
                dx = -distanceX;
                scrolled = true;
            }
            if (verticalScrollEnabled) {
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

            if (horizontalScrollEnabled && Math.abs(velocityX) >= minVelocity) {
                velocityX = velocityX < 0 ? Math.max(velocityX, -maxVelocity) : Math.min(velocityX, maxVelocity);
                flingX = true;
            } else {
                velocityX = 0;
            }

            if (verticalScrollEnabled && Math.abs(velocityY) >= minVelocity) {
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
                    horizontalScrollEnabled ? Integer.MIN_VALUE : 0,
                    horizontalScrollEnabled ? Integer.MAX_VALUE : 0,
                    verticalScrollEnabled ? Integer.MIN_VALUE : 0,
                    verticalScrollEnabled ? Integer.MAX_VALUE : 0);
            coreInterface.updateUI();
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

        for (int i = dyingTiles.size() - 1; i >= 0; i--) {
            long id = dyingTiles.keyAt(i);
            int c = getColumn(id);
            int r = getRow(id);
            if (c < left || c > right || r < top || r > bottom) {
                // 离开濒死区了
                T tile = dyingTiles.valueAt(i);
                dyingTiles.removeAt(i);
                recycle(tile);
            }
        }
    }

    public void reset() {
        // 清理活跃瓦片
        for (int i = 0; i < activeTiles.size(); i++) {
            long id = activeTiles.keyAt(i);
            T tile = activeTiles.valueAt(i);
            coreInterface.onTileOut(tile, getColumn(id), getRow(id));
            if (tile != null) tile.onOutWindow();
            recycle(tile);
        }
        activeTiles.clear();

        // 清理濒死瓦片
        for (int i = 0; i < dyingTiles.size(); i++) {
            recycle(dyingTiles.valueAt(i));
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
        if (smoothAnimator != null && smoothAnimator.isRunning()) {
            smoothAnimator.cancel();
        }
        smoothAnimator = null;
        lastSmoothProgress = 0f;
    }

    public void smoothSync(float dx, float dy) {
        float distance = (float) Math.hypot(dx, dy);
        long duration = (long) (distance / 1.5f);
        duration = Math.min(Math.max(duration, 150), 600);
        smoothSync(dx, dy, duration);
    }

    public void smoothSync(float dx, float dy, long duration) {
        if (!scroller.isFinished()) {
            scroller.abortAnimation();
        }
        if (smoothAnimator != null && smoothAnimator.isRunning()) {
            smoothAnimator.cancel();
        }

        lastSmoothProgress = 0f;

        smoothAnimator = ValueAnimator.ofFloat(0f, 1f);
        smoothAnimator.setDuration(duration);
        smoothAnimator.setInterpolator(new DecelerateInterpolator(1.5f));

        smoothAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float progress = animation.getAnimatedFraction();
                float stepProgress = progress - lastSmoothProgress;
                float stepX = dx * stepProgress;
                float stepY = dy * stepProgress;
                lastSmoothProgress = progress;
                sync(stepX, stepY);
            }
        });

        smoothAnimator.start();
    }

    public void sync(float dx, float dy) {
        startSyncTime = Debug.threadCpuTimeNanos();
        layoutService.sync(dx, dy);
        layoutTime = Debug.threadCpuTimeNanos() - startLayoutTime;
        coreInterface.updateUI();
    }

    public void seek(int column, int row, float offsetX, float offsetY) {
        if (isEmpty()) return;
        // 清理活跃瓦片

        for (int i = 0; i < activeTiles.size(); i++) {
            T tile = activeTiles.valueAt(i);
            long id = activeTiles.keyAt(i);
            coreInterface.onTileOut(tile, getColumn(id), getRow(id));
            if (tile != null) tile.onOutWindow();
            recycle(tile);
        }
        activeTiles.clear();

        // 清理濒死瓦片
        for (int i = 0; i < dyingTiles.size(); i++) {
            recycle(dyingTiles.valueAt(i));
        }
        dyingTiles.clear();
        layoutService.seek(column, row, offsetX, offsetY);
        coreInterface.updateUI();
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

        seek(column, row, 0, 0);
    }

    public void in(int column, int row) {
        long id = getTileId(column, row);
        T tile = dyingTiles.get(id);
        if (tile == null) {
            int type = coreInterface.getTileType(column, row);
            tile = obtain(type);
            if (tile != null) {
                ((BaseTileHolder) tile).column = column;
                ((BaseTileHolder) tile).row = row;
                ((BaseTileHolder) tile).width = getTileWidth(column);
                ((BaseTileHolder) tile).height = getTileHeight(row);
                coreInterface.onBindTileHolder(tile, column, row);
                coreInterface.onTileBind(tile, column, row);
            }
        } else {
            dyingTiles.remove(id);
        }
        if (tile != null) {
            activeTiles.put(id, tile);
            coreInterface.onTileIn(tile, column, row);
            tile.onInWindow();
        }
    }

    public void out(int column, int row) {
        long id = getTileId(column, row);
        T tile = activeTiles.get(id);
        if (tile != null) {
            activeTiles.remove(id);
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
        int i = widths.indexOfKey(column);
        return i >= 0 ? widths.get(column) : (dimenProvider == null ? defaultTileWidth : dimenProvider.getTileWidth(column));
    }

    public int getTileHeight(int row) {
        int i = heights.indexOfKey(row);
        return i >= 0 ? heights.get(row) : (dimenProvider == null ? defaultTileHeight : dimenProvider.getTileHeight(row));
    }

    public void setTileWidth(int column, int width) {
        if (isEmpty()) return;
        if (column > coreInterface.getRightBound() || column < coreInterface.getLeftBound())
            throw new IndexOutOfBoundsException("列索引 " + column + " 不在 [" + coreInterface.getLeftBound() + "," + coreInterface.getRightBound() + "] 范围内");
        int old = getTileWidth(column);
        if (width == 0) {
            width = getTileWidth(column);
            widths.delete(column);
        } else {
            widths.put(column, width);
        }
        layoutService.updateWidth(column, old, width);

        int dyingLeft = getDyingLeft();
        int dyingRight = getDyingRight();
        if (column >= dyingLeft && column <= dyingRight) {
            int row = getDyingTop();
            int end = getDyingBottom();
            while (row <= end) {
                reloadTile(column, row);
                if (row == end) break;
                row++;
            }
            coreInterface.updateUI();
        }
    }

    public void setTileHeight(int row, int height) {
        if (isEmpty()) return;
        if (row > coreInterface.getBottomBound() || row < coreInterface.getTopBound())
            throw new IndexOutOfBoundsException("行索引 " + row + " 不在 [" + coreInterface.getTopBound() + "," + coreInterface.getBottomBound() + "] 范围内");
        int old = getTileHeight(row);
        if (height == 0) {
            height = getTileHeight(row);
            heights.delete(row);
        } else {
            heights.put(row, height);
        }
        layoutService.updateHeight(row, old, height);

        int dyingTop = getDyingTop();
        int dyingBottom = getDyingBottom();
        if (row >= dyingTop && row <= dyingBottom) {
            int column = getDyingLeft();
            int end = getDyingRight();
            while (column <= end) {
                reloadTile(column, row);
                if (column == end) break;
                column++;
            }
            coreInterface.updateUI();
        }
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
            rebuildTile(column, row);
            coreInterface.updateUI();
        }
    }

    public void updateRange(int left, int top, int right, int bottom) {
    	if (left >= getDyingLeft() &&
                right <= getDyingRight() &&
                top >= getDyingTop() &&
                bottom <= getDyingBottom() &&
                left <= right && top <= bottom) {
            int c = left;
            while (c <= right) {
                int r = top;
                while (r <= bottom) {
                    rebuildTile(c, r);
                    if (r == right) break;
                    r++;
                }
                if (c == right) break;
                c++;
            }
            coreInterface.updateUI();
        }
    }

    public void updateColumn(int column) {
        if (column >= getDyingLeft() && column <= getDyingRight()) {
            int row = getDyingTop();
            int end = getDyingBottom();
            while (row <= end) {
                rebuildTile(column, row);
                if (row == end) break;
                row++;
            }
            coreInterface.updateUI();
        }
    }

    public void updateRow(int row) {
        if (row >= getDyingTop() && row <= getDyingBottom()) {
            int column = getDyingLeft();
            int end = getDyingRight();
            while (column <= end) {
                rebuildTile(column, row);
                if (column == end) break;
                column++;
            }
            coreInterface.updateUI();
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
                ((BaseTileHolder) newTile).column = column;
                ((BaseTileHolder) newTile).row = row;
                ((BaseTileHolder) newTile).width = getTileWidth(column);
                ((BaseTileHolder) newTile).height = getTileHeight(row);
                coreInterface.onBindTileHolder(newTile, column, row);
                coreInterface.onTileBind(newTile, column, row);
                dyingTiles.put(id, newTile);
            }
        }
    }

    private void reloadTile(int column, int row) {
        long id = getTileId(column, row);
        T tile = activeTiles.get(id);
        if (tile == null) {
            tile = dyingTiles.get(id);
        }
        if (tile != null) {
            ((BaseTileHolder) tile).width = getTileWidth(column);
            ((BaseTileHolder) tile).height = getTileHeight(row);
            coreInterface.onBindTileHolder(tile, column, row);
            coreInterface.onTileBind(tile, column, row);
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

    public LongSparseArray<T> getDyingTiles() {
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

    public long getLayoutTime() {
        return layoutTime;
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

        void updateUI();

        void onTileIn(T holder, int column, int row);

        void onTileOut(T holder, int column, int row);

        void onTileRecycled(T holder, int column, int row);

        void onTileBind(T holder, int column, int row);

        int getLeftBound();

        int getTopBound();

        int getRightBound();

        int getBottomBound();

        T onCreateTileHolder(int type);

        void onBindTileHolder(T holder, int column, int row);

        int getTileType(int column, int row);

    }

}
