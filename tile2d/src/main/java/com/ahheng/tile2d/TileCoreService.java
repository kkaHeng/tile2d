package com.ahheng.tile2d;

import android.content.Context;
import android.graphics.Rect;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.Scroller;

import com.ahheng.tile2d.widget.TileAdapter;
import com.ahheng.tile2d.widget.TileDimenProvider;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

import java.util.ArrayDeque;
import java.util.Deque;

public class TileCoreService <T extends TileCoreService.BaseTileHolder> {

    private TileAdapter<T> adapter;
    private TileDimenProvider dimenProvider;
    private int defaultTileWidth;
    private int defaultTileHeight;

    private final Rect bounds = new Rect();
    private final CoreInterface coreInterface;
    private final TileLayoutService layoutService = new TileLayoutService(new PlatformService());

    private final Long2ObjectOpenHashMap<T> activeTiles = new Long2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<Deque<T>> recycledTiles = new Int2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<T> dyingTiles = new Long2ObjectOpenHashMap<>();
    private final Int2IntOpenHashMap widths = new Int2IntOpenHashMap();
    private final Int2IntOpenHashMap heights = new Int2IntOpenHashMap();

    // 0：无。1：在左边。-1：在右边。
    private int dyingVectorHorizontal;
    private int dyingVectorVertical;
    private int dyingColStart;
    private int dyingColEnd;
    private int dyingRowStart;
    private int dyingRowEnd;

    private final Scroller scroller;
    private final GestureDetector gestureDetector;
    private final int minVelocity;
    private final int maxVelocity;

    private T touchTarget;
    private final int[] touchTargetPos = new int[2];
    private final float[] touchTargetLoc = new float[2];
    private boolean disallowIntercept;
    private boolean isInteractingWithView;
    private int lastScrollerX;
    private int lastScrollerY;

    private boolean horizontalScrollEnabled = true;
    private boolean verticalScrollEnabled = true;

    private boolean debugMode;
    private long syncTime;
    private int recycledCount;

    public TileCoreService(Context context, CoreInterface coreInterface) {
        this.coreInterface = coreInterface;
        this.scroller = new Scroller(context);
        ViewConfiguration vc = ViewConfiguration.get(context);
        this.minVelocity = vc.getScaledMinimumFlingVelocity();
        this.maxVelocity = (int) (vc.getScaledMaximumFlingVelocity() * 0.8f);
        this.gestureDetector = new GestureDetector(context, new GestureListener());
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
            return adapter == null ? 0 : adapter.getLeftBound();
        }

        @Override
        public int getTopBound() {
            return adapter == null ? 0 : adapter.getTopBound();
        }

        @Override
        public int getRightBound() {
            return adapter == null ? -1 : adapter.getRightBound();
        }

        @Override
        public int getBottomBound() {
            return adapter == null ? -1 : adapter.getBottomBound();
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

    public boolean handleTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();

        if (action == MotionEvent.ACTION_DOWN) {
            disallowIntercept = false;
            isInteractingWithView = false;

            if (scroller.computeScrollOffset()) {
                scroller.abortAnimation();
            }

            float contentX = event.getX() - bounds.left;
            float contentY = event.getY() - bounds.top;
            touchTarget = findTileAt(contentX, contentY, touchTargetPos, touchTargetLoc);
            if (touchTarget != null) {
                MotionEvent tileEvent = toTileEvent(event);
                touchTarget.onTouchEvent(tileEvent);
                tileEvent.recycle();
            }

            gestureDetector.onTouchEvent(event);
            return true;
        }

        if (touchTarget != null) {
            boolean intercepted = !disallowIntercept && isInteractingWithView;
            MotionEvent tileEvent = toTileEvent(event);
            if (intercepted) {
                // 取消瓦片的触摸事件
                tileEvent.setAction(MotionEvent.ACTION_CANCEL);
                touchTarget.onTouchEvent(tileEvent);
                tileEvent.recycle();
                resetTouchTarget();
                gestureDetector.onTouchEvent(event);
            } else {
                touchTarget.onTouchEvent(tileEvent);
                tileEvent.recycle();
                if (!disallowIntercept) {
                    // 继续处理手势
                    gestureDetector.onTouchEvent(event);
                }
            }
            return true;
        }

        gestureDetector.onTouchEvent(event);
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
            }
            
            coreInterface.updateUI();
        }
    }

    private T findTileAt(float contentX, float contentY, int[] outPos, float[] outLoc) {
        TileLayoutModel model = layoutService.getLayoutModel();
        float x = model.offsetX;
        int col = model.colStart;
        while (col <= model.colEnd) {
            int width = getTileWidth(col);
            if (contentX >= x && contentX < x + width) {
                float y = model.offsetY;
                int row = model.rowStart;
                while (row <= model.rowEnd) {
                    int height = getTileHeight(row);
                    if (contentY >= y && contentY < y + height) {
                        if (outPos != null) {
                            outPos[0] = col;
                            outPos[1] = row;
                        }
                        if (outLoc != null) {
                            outLoc[0] = x;
                            outLoc[1] = y;
                        }
                        return activeTiles.get(getTileId(col, row));
                    }
                    y += height;
                    if (row == model.rowEnd) break;
                    row++;
                }
            }
            x += width;
            if (col == model.colEnd) break;
            col++;
        }
        return null;
    }

    private MotionEvent toTileEvent(MotionEvent viewEvent) {
        MotionEvent tileEvent = MotionEvent.obtain(viewEvent);
        float offsetX = bounds.left + touchTargetLoc[0];
        float offsetY = bounds.top + touchTargetLoc[1];
        tileEvent.offsetLocation(-offsetX, -offsetY);
        return tileEvent;
    }

    private void resetTouchTarget() {
        touchTarget = null;
        touchTargetPos[0] = 0;
        touchTargetPos[1] = 0;
        touchTargetLoc[0] = 0;
        touchTargetLoc[1] = 0;
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

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            if (touchTarget != null && !isInteractingWithView) {
                return touchTarget.onClick();
            }
            return false;
        }

        @Override
        public void onLongPress(MotionEvent e) {
            if (touchTarget != null && !isInteractingWithView) {
                touchTarget.onLongClick();
            }
        }
    }

    private void diffDying(int newColStart, int newColEnd, int newRowStart, int newRowEnd) {
        // 快速退出：新旧范围完全重合，说明本次滚动未产生新的出屏区域
        if (dyingColStart == newColStart && dyingColEnd == newColEnd
                && dyingRowStart == newRowStart && dyingRowEnd == newRowEnd) {
            return;
        }
    
        ObjectIterator<Long2ObjectOpenHashMap.Entry<T>> it = dyingTiles.long2ObjectEntrySet().iterator();
        while (it.hasNext()) {
            Long2ObjectOpenHashMap.Entry<T> entry = it.next();
            long id = entry.getLongKey();
            int c = getColumn(id);
            int r = getRow(id);
            if (c < newColStart || c > newColEnd || r < newRowStart || r > newRowEnd) {
                T tile = entry.getValue();
                it.remove();
                recycle(tile);
                tile.onRecycled();
            }
        }
    }

    public void reset() {
        // 丢弃全部旧数据
        dyingVectorHorizontal = 0;
        dyingVectorVertical = 0;
        dyingColStart = 0;
        dyingColEnd = 0;
        dyingRowStart = 0;
        dyingRowEnd = 0;
        lastScrollerX = 0;
        lastScrollerY = 0;
        disallowIntercept = false;
        isInteractingWithView = false;
        syncTime = 0;
        recycledCount = 0;
        activeTiles.clear();
        recycledTiles.clear();
        dyingTiles.clear();
        widths.clear();
        heights.clear();
        layoutService.seek(0, 0, 0, 0);
    }

    public void sync(float dx, float dy) {
        long t0 = System.nanoTime();
        int vectorHorizontal = dx == 0 ? 0 : (dx > 0 ? 1 : -1);
        int vectorVertical = dy == 0 ? 0 : (dy > 0 ? 1 : -1);
        
        TileLayoutModel model = layoutService.getLayoutModel();
        int lastColStart = model.colStart;
        int lastColEnd = model.colEnd;
        int lastRowStart = model.rowStart;
        int lastRowEnd = model.rowEnd;
        
        layoutService.sync(dx, dy);
        syncTime = System.nanoTime() - t0;
        coreInterface.updateUI();
        
        int colStart = model.colStart;
        int colEnd = model.colEnd;
        int rowStart = model.rowStart;
        int rowEnd = model.rowEnd;
        
        // 处理濒死区
        if (vectorHorizontal != 0 || vectorVertical != 0) {
            if (vectorHorizontal == dyingVectorHorizontal || vectorVertical == dyingVectorVertical) {
                // 顺方向滚动，回收当前濒死区不在新濒死区范围内的瓦片
                if (lastColStart != colStart || lastColEnd != colEnd || lastRowStart != rowStart || lastRowEnd != rowEnd) {
                    diffDying(lastColStart, lastColEnd, lastRowStart, lastRowEnd);
                    dyingColStart = lastColStart;
                    dyingColEnd = lastColEnd;
                    dyingRowStart = lastRowStart;
                    dyingRowEnd = lastRowEnd;
                }
            }
        }
        dyingVectorHorizontal = vectorHorizontal;
        dyingVectorVertical = vectorVertical;
    }

    public void seek(int column, int row, float offsetX, float offsetY) {
        if (isEmpty()) return;
        // 回收所有活跃瓦片，并通知外部
        Long2ObjectOpenHashMap.FastEntrySet<T> entrySet = activeTiles.long2ObjectEntrySet();
        for (Long2ObjectOpenHashMap.Entry<T> entry : entrySet) {
            T tile = entry.getValue();
            long id = entry.getLongKey();
            int c = (int) (id >> 32);
            int r = (int) id;
            tile.onOutWindow();
            coreInterface.onTileOut(tile, c, r);
            recycle(tile);
            tile.onRecycled();
        }
        activeTiles.clear();

        layoutService.seek(column, row, offsetX, offsetY);
        coreInterface.updateUI();
    }

    public void in(int column, int row) {
        long id = getTileId(column, row);
        T tile = dyingTiles.remove(id);
        if (tile == null) {
            int type = adapter.getTileType(column, row);
            tile = obtain(type);
            ((BaseTileHolder) tile).column = column;
            ((BaseTileHolder) tile).row = row;
            ((BaseTileHolder) tile).width = getTileWidth(column);
            ((BaseTileHolder) tile).height = getTileHeight(row);
            adapter.onBindTileHolder(tile, column, row);
            tile.onInWindow();
        }
        activeTiles.put(id, tile);
        coreInterface.onTileIn(tile, column, row);
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
        Deque<T> tiles = recycledTiles.get(type);
        if (tiles != null && !tiles.isEmpty()) {
            if (debugMode) recycledCount--;
            return tiles.poll();
        }
        T tile = adapter.onCreateTileHolder(type);
        ((BaseTileHolder) tile).type = type;
        return tile;
    }

    public void recycle(T tile) {
        if (tile == touchTarget) {
            resetTouchTarget();
        }
        Deque<T> tiles = recycledTiles.computeIfAbsent(((BaseTileHolder) tile).type, k -> new ArrayDeque<>());
        tiles.offer(tile);
        if (debugMode) recycledCount++;
    }

    public T getActiveTile(int column, int row) {
        long id = getTileId(column, row);
        return activeTiles.get(id);
    }

    public int getTileWidth(int column) {
        return widths.getOrDefault(column, dimenProvider == null ? defaultTileWidth : dimenProvider.getTileWidth(column));
    }

    public int getTileHeight(int row) {
        return heights.getOrDefault(row, dimenProvider == null ? defaultTileHeight : dimenProvider.getTileHeight(row));
    }

    public void setTileWidth(int column, int width) {
        if (width < 0) throw new IllegalArgumentException("宽度必须大于 0");
        int old = getTileWidth(column);
        widths.put(column, width);

        TileLayoutModel model = layoutService.getLayoutModel();
        if (column >= model.colStart && column <= model.colEnd) {
            float newOffsetX = model.offsetX;

            if (column == model.colStart) {
                newOffsetX = model.offsetX + old - width;
                newOffsetX = Math.min(0, Math.max(-width, newOffsetX));
            }
            seek(model.colStart, model.rowStart, newOffsetX, model.offsetY);
        }
        coreInterface.updateUI();
    }

    public void setTileHeight(int row, int height) {
        if (height < 0) throw new IllegalArgumentException("高度必须大于 0");

        int old = getTileHeight(row);  // 同理
        heights.put(row, height);

        TileLayoutModel model = layoutService.getLayoutModel();
        if (row >= model.rowStart && row <= model.rowEnd) {
            float newOffsetY = model.offsetY;

            if (row == model.rowStart) {
                newOffsetY = model.offsetY + old - height;
                newOffsetY = Math.min(0, Math.max(-height, newOffsetY));
            }

            seek(model.colStart, model.rowStart, model.offsetX, newOffsetY);
        }
        coreInterface.updateUI();
    }



    public TileAdapter<T> getAdapter() {
        return adapter;
    }

    public void setAdapter(TileAdapter<T> adapter) {
        if (this.adapter != null) {
            this.adapter = null;
            reset();
        }
        this.adapter = adapter;
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
        this.defaultTileWidth = width;
    }

    public void setDefaultTileHeight(int height) {
        this.defaultTileHeight = height;
    }

    public TileLayoutModel getLayoutModel() {
        return layoutService.getLayoutModel();
    }

    public TileLayoutService getLayoutService() {
        return layoutService;
    }

    public boolean isDebugMode() {
    	return debugMode;
    }

    public void setDebugMode(boolean enabled) {
    	this.debugMode = enabled;
    }

    public long getSyncTime() {
    	return syncTime;
    }

    public int getActiveTileCount() {
        return activeTiles.size();
    }

    public int getRecycledTileCount() {
        return recycledCount;
    }

    public int getDyingTileCount() {
    	return dyingTiles.size();
    }

    public Long2ObjectOpenHashMap<T> getDyingTiles() {
    	return dyingTiles;
    }

    public boolean isEmpty() {
        return adapter == null || adapter.isEmpty();
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

        public void onRecycled() {}

        public void onInWindow() {}

        public void onOutWindow() {}

        public boolean onTouchEvent(MotionEvent event) {
            return false;
        }

        public boolean onClick() {
            return false;
        }

        public void onLongClick() {}

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

    public interface CoreInterface {

        void updateUI();

        void onTileIn(BaseTileHolder holder, int column, int row);

        void onTileOut(BaseTileHolder holder, int column, int row);
    }

}
