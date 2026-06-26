package com.ahheng.tile2d.app;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

public abstract class BaseActivity extends AppCompatActivity {

    private static final int MENU_ID_DEBUG = 1;
    private static final int MENU_ID_MAX = 2;
    private static final int MENU_ID_PLAN = 3;
    private static final int MENU_ID_TO_END = 4;

    public final static int PLAN_COLOR = 0;
    public final static int PLAN_TEXT = 1;
    
    private Toast toast;
    private boolean debugMode = true;
    private boolean maxMode = false;
    private int plan = PLAN_TEXT;
    private ToTheEnd toTheEnd;

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
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        if (hasMaxMode()) {
            menu.add(Menu.NONE, MENU_ID_MAX, Menu.NONE, "伪无限模式")
                    .setCheckable(true)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        }
        if (hasPlanMode()) {
            menu.add(Menu.NONE, MENU_ID_PLAN, Menu.NONE, "切换方案")
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        }
        if (toTheEnd != null) {
            menu.add(Menu.NONE, MENU_ID_TO_END, Menu.NONE, "去边界看看")
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        }
        return true;
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
        switch (id) {
            case MENU_ID_DEBUG -> {
                setDebugMode(!debugMode);
                showToast("Debug模式: " + (debugMode ? "开启" : "关闭"));
                return true;
            }
            case MENU_ID_MAX -> {
                setMaxMode(!maxMode);
                showToast("伪无限模式: " + (maxMode ? "开启" : "关闭"));
                return true;
            }
            case MENU_ID_PLAN -> {
                switch (plan) {
                    case PLAN_COLOR -> plan = PLAN_TEXT;
                    case PLAN_TEXT -> plan = PLAN_COLOR;
                }
                showToast("切换方案：" + plan);
                onPlanChanged(plan);
                return true;
            }
            case MENU_ID_TO_END -> {
                if (toTheEnd != null) {
                    toTheEnd();
                }
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) actionBar.setDisplayHomeAsUpEnabled(true);
        
        toTheEnd = onInitToTheEnd();
    }

    public void toTheEnd() {
        int i = (int) (Math.random() * 8);
        int l = toTheEnd.getLeftBound();
        int t = toTheEnd.getTopBound();
        int r = toTheEnd.getRightBound();
        int b = toTheEnd.getBottomBound();
        int x;
        int y;
        switch (i) {
            case 0 -> { x = 0;  y = t; showToast("去幽都(北)喽"); }
            case 1 -> { x = r;  y = t; showToast("去榑木(东北)喽"); }
            case 2 -> { x = r;  y = 0; showToast("去旸谷(东)喽"); }
            case 3 -> { x = r;  y = b; showToast("去苍梧(东南)喽"); }
            case 4 -> { x = 0;  y = b; showToast("去南溟(南)喽"); }
            case 5 -> { x = l;  y = b; showToast("去偏句(西南)喽"); }
            case 6 -> { x = l;  y = 0; showToast("去昧谷(西)喽"); }
            default -> { x = l;  y = t; showToast("去不周(西北)喽"); }
        }
        toTheEnd.gogogo(x, y);
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

    public interface ToTheEnd {
        
        int getLeftBound();
        
        int getTopBound();
        
        int getRightBound();
        
        int getBottomBound();
        
        void gogogo(int column, int row);
        
    }

}
