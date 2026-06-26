package com.ahheng.tile2d.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

import com.ahheng.tile2d.app.auto.AutoTileActivity;
import com.ahheng.tile2d.app.maze.MazeActivity;
import com.ahheng.tile2d.app.maze.MaxMazeActivity;
import com.google.android.material.button.MaterialButton;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        MaterialButton tileViewButton = new MaterialButton(this);
        tileViewButton.setText("瓦片画板 Demo");
        layout.addView(tileViewButton, -2, -2);

        MaterialButton tileLayoutButton = new MaterialButton(this);
        tileLayoutButton.setText("瓦片布局 Demo");
        layout.addView(tileLayoutButton, -2, -2);

        MaterialButton tableButton = new MaterialButton(this);
        tableButton.setText("数据表 Demo");
        layout.addView(tableButton, -2, -2);
        
        MaterialButton autoTileButton = new MaterialButton(this);
        autoTileButton.setText("自动瓦片 Demo");
        layout.addView(autoTileButton, -2, -2);

        MaterialButton mazeButton = new MaterialButton(this);
        mazeButton.setText("迷宫 Demo");
        layout.addView(mazeButton, -2, -2);
        
        MaterialButton maxMazeButton = new MaterialButton(this);
        maxMazeButton.setText("迷宫 Max Demo");
        layout.addView(maxMazeButton, -2, -2);

        setContentView(layout, new ViewGroup.LayoutParams(-1, -1));

        tileViewButton.setOnClickListener(v -> startActivity(new Intent(this, TileViewActivity.class)));
        tileLayoutButton.setOnClickListener(v -> startActivity(new Intent(this, TileLayoutActivity.class)));
        tableButton.setOnClickListener(v -> startActivity(new Intent(this, TableActivity.class)));
        autoTileButton.setOnClickListener(v -> startActivity(new Intent(this, AutoTileActivity.class)));
        mazeButton.setOnClickListener(v -> startActivity(new Intent(this, MazeActivity.class)));
        maxMazeButton.setOnClickListener(v -> startActivity(new Intent(this, MaxMazeActivity.class)));

        // 默认启动
        // startActivity(new Intent(this, TileViewActivity.class));
        // startActivity(new Intent(this, TileLayoutActivity.class));
        // startActivity(new Intent(this, TableActivity.class));
        // startActivity(new Intent(this, AutoTileActivity.class));
        // startActivity(new Intent(this, MazeActivity.class));
        startActivity(new Intent(this, MaxMazeActivity.class));
    }

}
