package com.yn.mt5vision;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

/** Optional draggable overlay. It only displays confirmed events, never trades. */
final class FloatingBadge {
    private final Context context;
    private final WindowManager wm;
    private FrameLayout frame;
    private TextView label;
    private WindowManager.LayoutParams params;
    private int sideColor = Color.rgb(32, 62, 78);\n    private boolean showingSignal = false;

    FloatingBadge(Context context) {
        this.context = context;
        this.wm = (WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
    }

    void show() {
        if (wm == null || frame != null || Build.VERSION.SDK_INT < 26) return;
        frame = new FrameLayout(context);
        frame.setPadding(dp(13), dp(10), dp(13), dp(10));
        label = new TextView(context);
        label.setText("YN MT5 VISION  ●  SCANNING");
        label.setTextColor(Color.WHITE);
        label.setTextSize(12);
        label.setTypeface(null, 1);
        label.setGravity(Gravity.CENTER_VERTICAL);
        frame.setBackground(drawable(sideColor));
        frame.addView(label, new FrameLayout.LayoutParams(-2, -2));
        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.RIGHT;
        params.x = dp(14); params.y = dp(130);
        frame.setOnTouchListener(new View.OnTouchListener() {
            private float startRawX, startRawY; private int initialX, initialY;
            @Override public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    startRawX = event.getRawX(); startRawY = event.getRawY();
                    initialX = params.x; initialY = params.y;
                    return true;
                }
                if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    params.x = Math.max(0, initialX - Math.round(event.getRawX() - startRawX));
                    params.y = Math.max(0, initialY + Math.round(event.getRawY() - startRawY));
                    try { wm.updateViewLayout(frame, params); } catch (Exception ignored) {}
                    return true;
                }
                return event.getAction() == MotionEvent.ACTION_UP;
            }
        });
        try { wm.addView(frame, params); }
        catch (Exception ignored) { frame = null; }
    }

    void update(String info, StrategyEngine.Side side) {
        if (frame == null || label == null) return;
        if (side == StrategyEngine.Side.BUY) sideColor = Color.rgb(19,98,83);
        else if (side == StrategyEngine.Side.SELL) sideColor = Color.rgb(123,54,64);
        else sideColor = Color.rgb(32,62,78);
        label.setText(info);
        frame.setBackground(drawable(sideColor));
    }
    void updateScanning(String message) {\n        if (!showingSignal) update(message, null);\n    }\n    void hideSignal() { update("YN MT5  ●  SCANNING", null); }
    void close() {
        if (frame != null) {
            try { wm.removeView(frame); } catch (Exception ignored) {}
            frame = null; label = null;
        }
    }
    private int dp(int v) { return (int)(v * context.getResources().getDisplayMetrics().density + 0.5f); }
    private GradientDrawable drawable(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color); d.setCornerRadius(dp(15));
        d.setStroke(dp(1), Color.rgb(82,109,125));
        return d;
    }
}
