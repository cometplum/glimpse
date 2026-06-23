package com.cometplum.glimpse;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

public class ImageViewerActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window w = getWindow();
        if (Build.VERSION.SDK_INT >= 21) {
            w.setStatusBarColor(Color.TRANSPARENT);
            w.setNavigationBarColor(Color.BLACK);
        }
        if (Build.VERSION.SDK_INT >= 23) {
            w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        String uri = getIntent().getStringExtra("uri");
        if (uri != null) image.setImageURI(Uri.parse(uri));
        root.addView(image, frame(-1, -1, Gravity.CENTER));

        TextView close = new TextView(this);
        close.setText("Close");
        close.setTextColor(Color.WHITE);
        close.setTextSize(14);
        close.setTypeface(Typeface.DEFAULT_BOLD);
        close.setGravity(Gravity.CENTER);
        close.setPadding(dp(16), 0, dp(16), 0);
        close.setBackground(round(0x66000000, dp(22), 0x44FFFFFF));
        close.setOnClickListener(v -> finish());
        FrameLayout.LayoutParams cp = frame(-2, dp(44), Gravity.TOP | Gravity.START);
        cp.setMargins(dp(18), dp(42), 0, 0);
        root.addView(close, cp);

        setContentView(root);
    }

    private FrameLayout.LayoutParams frame(int w, int h, int gravity) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(w, h);
        p.gravity = gravity;
        return p;
    }

    private GradientDrawable round(int color, int radius, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        if ((stroke >>> 24) != 0) d.setStroke(dp(1), stroke);
        return d;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
