package com.ahheng.tile2d.app;

import android.animation.ValueAnimator;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.animation.OvershootInterpolator;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class BaseActivity extends AppCompatActivity implements MenuItem.OnMenuItemClickListener {

    private static final AtomicInteger id = new AtomicInteger(0);

    private static final int MENU_ID_DEBUG = nextId();
    private static final int MENU_ID_MAX = nextId();
    private static final int MENU_ID_PLAN = nextId();
    private static final int MENU_ID_TO_END = nextId();
    private static final int MENU_ID_RANDOM_SIZE = nextId();
    private static final int MENU_ID_RANDOM_WIDTH = nextId();
    private static final int MENU_ID_RANDOM_HEIGHT = nextId();

    // 八方向去边界，按九宫格顺序排布（跳过中心），子菜单里逐个可选，不再随机
    private static final int MENU_ID_END_TOP_LEFT = nextId();
    private static final int MENU_ID_END_TOP = nextId();
    private static final int MENU_ID_END_TOP_RIGHT = nextId();
    private static final int MENU_ID_END_LEFT = nextId();
    private static final int MENU_ID_END_RIGHT = nextId();
    private static final int MENU_ID_END_BOTTOM_LEFT = nextId();
    private static final int MENU_ID_END_BOTTOM = nextId();
    private static final int MENU_ID_END_BOTTOM_RIGHT = nextId();

    public final static int PLAN_COLOR = 0;
    public final static int PLAN_TEXT = 1;

    // 尺寸动画时长
    private static final long SIZE_ANIM_DURATION = 2000L;

    private Toast toast;
    private boolean debugMode = true;
    private boolean maxMode = false;
    private int plan = PLAN_TEXT;
    private ToTheEnd toTheEnd;
    private RandomSize randomSize;

    private ValueAnimator widthAnimator;
    private ValueAnimator heightAnimator;

    public boolean hasMaxMode() {
        return true;
    }

    public boolean hasPlanMode() {
        return true;
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public boolean isMaxMode() {
        return maxMode;
    }

    public void setDebugMode(boolean enabled) {
        this.debugMode = enabled;
        invalidateOptionsMenu();
        onDebugModeChanged(enabled);
    }

    public void setMaxMode(boolean enabled) {
        this.maxMode = enabled;
        invalidateOptionsMenu();
        onMaxModeChanged(enabled);
    }

    public int dp2px(float dp) {
        return (int) (getResources().getDisplayMetrics().density * dp + 0.5f);
    }

    public float dpTopx(float dp) {
        return getResources().getDisplayMetrics().density * dp;
    }

    public void showToast(String text) {
        if (toast != null) toast.cancel();
        toast = Toast.makeText(this, text, Toast.LENGTH_SHORT);
        toast.show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, MENU_ID_DEBUG, Menu.NONE, "Debug模式")
                .setCheckable(true)
                .setOnMenuItemClickListener(this)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        if (hasMaxMode()) {
            menu.add(Menu.NONE, MENU_ID_MAX, Menu.NONE, "伪无限模式")
                    .setCheckable(true)
                    .setOnMenuItemClickListener(this)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        }
        if (hasPlanMode()) {
            menu.add(Menu.NONE, MENU_ID_PLAN, Menu.NONE, "切换方案")
                    .setOnMenuItemClickListener(this)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        }
        if (randomSize != null) {
            SubMenu sizeMenu = menu.addSubMenu(Menu.NONE, MENU_ID_RANDOM_SIZE, Menu.NONE, "随机调整尺寸");
            sizeMenu.getItem().setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
            addSubItem(sizeMenu, MENU_ID_RANDOM_WIDTH, "随机调整宽度");
            addSubItem(sizeMenu, MENU_ID_RANDOM_HEIGHT, "随机调整高度");
        }
        if (toTheEnd != null) {
            // 八方向全部列成子菜单，保证能逐个覆盖，不靠随机撞运气
            SubMenu endMenu = menu.addSubMenu(Menu.NONE, MENU_ID_TO_END, Menu.NONE, "去边界看看");
            endMenu.getItem().setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
            addSubItem(endMenu, MENU_ID_END_TOP_LEFT, "左上角");
            addSubItem(endMenu, MENU_ID_END_TOP, "最上边");
            addSubItem(endMenu, MENU_ID_END_TOP_RIGHT, "右上角");
            addSubItem(endMenu, MENU_ID_END_LEFT, "最左边");
            addSubItem(endMenu, MENU_ID_END_RIGHT, "最右边");
            addSubItem(endMenu, MENU_ID_END_BOTTOM_LEFT, "左下角");
            addSubItem(endMenu, MENU_ID_END_BOTTOM, "最下边");
            addSubItem(endMenu, MENU_ID_END_BOTTOM_RIGHT, "右下角");
        }
        return true;
    }

    private void addSubItem(SubMenu subMenu, int itemId, String title) {
        subMenu.add(Menu.NONE, itemId, Menu.NONE, title)
                .setOnMenuItemClickListener(this);
    }

    @Override
    public boolean onMenuItemClick(@NonNull MenuItem menuItem) {
        int id = menuItem.getItemId();
        if (id == MENU_ID_DEBUG) {
            setDebugMode(!debugMode);
            showToast("Debug模式: " + (debugMode ? "开启" : "关闭"));
            return true;
        }
        if (id == MENU_ID_MAX) {
            setMaxMode(!maxMode);
            showToast("伪无限模式: " + (maxMode ? "开启" : "关闭"));
            return true;
        }
        if (id == MENU_ID_PLAN) {
            switch (plan) {
                case PLAN_COLOR -> plan = PLAN_TEXT;
                case PLAN_TEXT -> plan = PLAN_COLOR;
            }
            showToast("切换方案：" + plan);
            onPlanChanged(plan);
            return true;
        }
        if (id == MENU_ID_RANDOM_WIDTH) {
            randomWidth();
            return true;
        }
        if (id == MENU_ID_RANDOM_HEIGHT) {
            randomHeight();
            return true;
        }
        if (toTheEnd != null) {
            int l = toTheEnd.getLeftBound();
            int t = toTheEnd.getTopBound();
            int r = toTheEnd.getRightBound();
            int b = toTheEnd.getBottomBound();
            if (id == MENU_ID_END_TOP_LEFT) return gogogo(l, t, "左上角");
            if (id == MENU_ID_END_TOP) return gogogo(0, t, "最上边");
            if (id == MENU_ID_END_TOP_RIGHT) return gogogo(r, t, "右上角");
            if (id == MENU_ID_END_LEFT) return gogogo(l, 0, "最左边");
            if (id == MENU_ID_END_RIGHT) return gogogo(r, 0, "最右边");
            if (id == MENU_ID_END_BOTTOM_LEFT) return gogogo(l, b, "左下角");
            if (id == MENU_ID_END_BOTTOM) return gogogo(0, b, "最下边");
            if (id == MENU_ID_END_BOTTOM_RIGHT) return gogogo(r, b, "右下角");
        }
        return false;
    }

    private boolean gogogo(int column, int row, String name) {
        showToast("到达" + name + "：" + column + "," + row);
        toTheEnd.gogogo(column, row);
        return true;
    }

    // 随机挑视窗中心所在的列，动画调整宽度
    private void randomWidth() {
        if (randomSize == null) return;
        if (widthAnimator != null) widthAnimator.cancel();
        int column = randomSize.getCenterColumn();
        int dp = (new Random().nextInt(19) + 4) * 10;
        widthAnimator = ValueAnimator.ofInt(randomSize.getTileWidth(column), dp2px(dp));
        widthAnimator.setDuration(SIZE_ANIM_DURATION);
        widthAnimator.setInterpolator(new OvershootInterpolator());
        widthAnimator.addUpdateListener(a -> randomSize.setTileWidth(column, (int) a.getAnimatedValue()));
        widthAnimator.start();
        showToast(String.format(Locale.getDefault(), "调整第 %d 列宽度到 %ddp", column, dp));
    }

    // 随机挑视窗中心所在的行，动画调整高度
    private void randomHeight() {
        if (randomSize == null) return;
        if (heightAnimator != null) heightAnimator.cancel();
        int row = randomSize.getCenterRow();
        int dp = (new Random().nextInt(7) + 3) * 10;
        heightAnimator = ValueAnimator.ofInt(randomSize.getTileHeight(row), dp2px(dp));
        heightAnimator.setDuration(SIZE_ANIM_DURATION);
        heightAnimator.setInterpolator(new OvershootInterpolator());
        heightAnimator.addUpdateListener(a -> randomSize.setTileHeight(row, (int) a.getAnimatedValue()));
        heightAnimator.start();
        showToast(String.format(Locale.getDefault(), "调整第 %d 行高度到 %ddp", row, dp));
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem item = menu.findItem(MENU_ID_DEBUG);
        if (item != null) item.setChecked(debugMode);
        if (hasMaxMode()) {
            item = menu.findItem(MENU_ID_MAX);
            if (item != null) item.setChecked(maxMode);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) actionBar.setDisplayHomeAsUpEnabled(true);

        toTheEnd = onInitToTheEnd();
        randomSize = onInitRandomSize();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (widthAnimator != null) widthAnimator.cancel();
        if (heightAnimator != null) heightAnimator.cancel();
    }

    protected void onDebugModeChanged(boolean enabled) {
    }

    protected void onMaxModeChanged(boolean maxMode) {
    }

    protected void onPlanChanged(int plan) {
    }

    protected ToTheEnd onInitToTheEnd() {
        return null;
    }

    protected RandomSize onInitRandomSize() {
        return null;
    }

    public interface ToTheEnd {

        int getLeftBound();

        int getTopBound();

        int getRightBound();

        int getBottomBound();

        void gogogo(int column, int row);

    }

    // 随机尺寸动画的宿主契约，由各 demo 把自己的容器接进来
    public interface RandomSize {

        int getCenterColumn(); // 视窗中心所在列

        int getCenterRow(); // 视窗中心所在行

        int getTileWidth(int column);

        int getTileHeight(int row);

        void setTileWidth(int column, int width);

        void setTileHeight(int row, int height);

    }

    public static int nextId() {
        return id.incrementAndGet(); // 先增加，再返回当前值
    }

}
