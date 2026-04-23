package com.ahheng.tile2d.app;

import android.os.Bundle;
import android.view.ViewGroup;

import androidx.appcompat.app.AppCompatActivity;

import com.ahheng.tile2d.widget.TileView;

public class MainActivity extends AppCompatActivity {
    
    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        TileView view = new TileView(this);
        setContentView(view, new ViewGroup.LayoutParams(-1, -1));
        
    }
    
}
