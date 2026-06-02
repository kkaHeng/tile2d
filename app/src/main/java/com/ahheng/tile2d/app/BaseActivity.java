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
    private static final int MENU_ID_PLAN = 2;

    public final static int PLAN_COLOR = 0;
    public final static int PLAN_TEXT = 1;
    
    private Toast toast;
    private boolean debugMode = true;
    private int plan = PLAN_TEXT;

    public boolean isDebugMode() {
        return debugMode;
    }

    public void setDebugMode(boolean enabled) {
        this.debugMode = enabled;
        invalidateOptionsMenu();
        onDebugModeChanged(enabled);
    }

    public int dp2px(float dp) {
        return (int) (getResources().getDisplayMetrics().density * dp + 0.5f);
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
        menu.add(Menu.NONE, MENU_ID_PLAN, Menu.NONE, "切换方案")
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem item = menu.findItem(MENU_ID_DEBUG);
        if (item != null) item.setChecked(debugMode);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        }
        if (id == MENU_ID_DEBUG) {
            setDebugMode(!debugMode);
            showToast("Debug模式: " + (debugMode ? "开启" : "关闭"));
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
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) actionBar.setDisplayHomeAsUpEnabled(true);
    }

    protected void onDebugModeChanged(boolean enabled) {
    }

    protected void onPlanChanged(int plan) {
    }
}
