package com.yn.mt5vision;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

/** Minimal native Android UX; chart reading runs on-device, not through a web/API key. */
public final class MainActivity extends Activity {
    private static final int REQUEST_CAPTURE = 912;
    private TextView statusView, signalView;
    private Spinner modeSpinner;
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            String message = intent.getStringExtra("message");
            String signal = intent.getStringExtra("signal");
            if (message != null) statusView.setText(message);
            if (signal != null && !signal.isEmpty()) {
                signalView.setText(signal);
                signalView.setTextColor(signal.startsWith("BUY") ? Color.rgb(24,200,153) :
                        signal.startsWith("SELL") ? Color.rgb(255,117,110) : Color.WHITE);
            }
        }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(12,17,27));
        getWindow().setNavigationBarColor(Color.rgb(12,17,27));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(12,17,27));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(26), dp(22), dp(28));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        scroll.addView(root);
        setContentView(scroll);

        TextView brand = label("YN  /  MT5 VISION", 26, Color.rgb(239,244,255), true);
        brand.setGravity(Gravity.CENTER);
        root.addView(brand, fullWrap());

        TextView subtitle = label("ماسح الشارت المباشر • إشارة عند تحقق الاستراتيجية", 13,
                Color.rgb(163,179,197), false);
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle, marginWrap(10));

        LinearLayout panel = panel();
        panel.addView(label("حالة الماسح", 17, Color.WHITE, true), marginWrap(5));
        statusView = label("غير نشط • لم يبدأ التقاط الشاشة", 14,
                Color.rgb(187,207,226), false);
        panel.addView(statusView, marginWrap(9));
        root.addView(panel, marginWrap(25));

        LinearLayout signalPanel = panel();
        signalPanel.addView(label("آخر إشارة مؤكدة", 17, Color.WHITE, true), marginWrap(5));
        signalView = label("لا توجد إشارة مؤكدة بعد", 18, Color.rgb(175,195,213), true);
        signalPanel.addView(signalView, marginWrap(12));
        root.addView(signalPanel, marginWrap(12));

        root.addView(label("حساسية الاستراتيجيات", 16, Color.WHITE, true), marginWrap(25));
        modeSpinner = new Spinner(this);
        String[] modes = {"هجومي", "متوازن", "محافظ"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, modes);
        modeSpinner.setAdapter(adapter);
        SharedPreferences prefs = getSharedPreferences("yn", MODE_PRIVATE);
        String selected = prefs.getString("mode", "متوازن");
        modeSpinner.setSelection("هجومي".equals(selected) ? 0 : "محافظ".equals(selected) ? 2 : 1);
        modeSpinner.setBackground(background(Color.rgb(31,44,62), 12, 0));
        modeSpinner.setPadding(dp(12), dp(10), dp(12), dp(10));
        modeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int position, long id) {
                prefs.edit().putString("mode", modes[position]).apply();
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        root.addView(modeSpinner, marginWrap(9));

        Button overlay = button("① السماح بالبطاقة العائمة", Color.rgb(44,57,76));
        overlay.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } else {
                statusView.setText("البطاقة العائمة مسموح بها ✓");
            }
        });
        root.addView(overlay, marginWrap(18));

        Button start = button("▶ بدء فحص MT5", Color.rgb(29,136,115));
        start.setOnClickListener(v -> startCapture());
        root.addView(start, marginWrap(10));

        Button stop = button("■ إيقاف الماسح", Color.rgb(130,58,63));
        stop.setOnClickListener(v -> {
            stopService(new Intent(this, CaptureService.class));
            statusView.setText("تم إيقاف الماسح");
        });
        root.addView(stop, marginWrap(10));

        TextView guide = label("الطريقة: افتح شارت MT5 بشموع حمراء وخضراء، ويفضل M1 مع 30 شمعة على الأقل. اضغط بدء الفحص ثم اسمح بمشاركة شاشة MT5، وارجع للمنصة. لن يصدر التطبيق BUY أو SELL إلا عند اكتشاف شروط الاستراتيجية.", 14,
                Color.rgb(187,204,218), false);
        guide.setLineSpacing(dp(4), 1.0f);
        root.addView(guide, marginWrap(22));

        TextView caution = label("نسخة تجريبية للتداول الافتراضي. لا توجد استراتيجية تمنع جميع الانعكاسات. قوة الإشارة ليست نسبة ربح مثبتة. عندما يتعذر التحقق من أرقام الأسعار، لا تعتمد على مستويات الدخول بصريًا.", 13,
                Color.rgb(250,191,138), false);
        root.addView(caution, marginWrap(18));

        IntentFilter f = new IntentFilter(CaptureService.ACTION_STATUS);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(receiver, f);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 304);
        }
    }

    private void startCapture() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            statusView.setText("اسمح أولًا بظهور البطاقة العائمة ثم أعد تشغيل الفحص");
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
            return;
        }
        MediaProjectionManager projectionManager =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (projectionManager == null) {
            statusView.setText("نظام الجهاز لا يدعم التقاط الشاشة");
            return;
        }
        statusView.setText("بانتظار موافقتك على مشاركة شاشة MT5...");
        startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_CAPTURE);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_CAPTURE) return;
        if (resultCode != RESULT_OK || data == null) {
            statusView.setText("أُلغيت مشاركة الشاشة؛ لم يبدأ المسح");
            return;
        }
        Intent service = new Intent(this, CaptureService.class);
        service.setAction(CaptureService.ACTION_START);
        service.putExtra(CaptureService.EXTRA_RESULT_CODE, resultCode);
        service.putExtra(CaptureService.EXTRA_CAPTURE_DATA, data);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(service);
        else startService(service);
        statusView.setText("بدأ طلب التقاط الشارت. افتح MT5 واترك الشموع ظاهرة.");
    }

    @Override protected void onResume() {
        super.onResume();
        if (statusView == null) return;
        SharedPreferences p = getSharedPreferences("yn", MODE_PRIVATE);
        if (p.getBoolean("running", false)) statusView.setText(p.getString("status",
                "الماسح يعمل؛ انتقل إلى شارت MT5"));
        String last = p.getString("last", "");
        if (!last.isEmpty()) signalView.setText(last);
    }

    @Override protected void onDestroy() {
        try { unregisterReceiver(receiver); } catch (Exception ignored) {}
        super.onDestroy();
    }

    private int dp(float dp) { return (int)(dp * getResources().getDisplayMetrics().density + 0.5f); }
    private LinearLayout.LayoutParams fullWrap() {
        return new LinearLayout.LayoutParams(-1, -2);
    }
    private LinearLayout.LayoutParams marginWrap(int top) {
        LinearLayout.LayoutParams p = fullWrap();
        p.topMargin = dp(top);
        return p;
    }
    private GradientDrawable background(int color, int radius, int border) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        if (border != 0) d.setStroke(dp(1), border);
        return d;
    }
    private LinearLayout panel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(17),dp(17),dp(17),dp(17));
        panel.setBackground(background(Color.rgb(22,32,48), 17, Color.rgb(48,67,83)));
        return panel;
    }
    private TextView label(String value, int size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setGravity(Gravity.START);
        if (bold) v.setTypeface(null, 1);
        return v;
    }
    private Button button(String value, int bg) {
        Button b = new Button(this);
        b.setText(value);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(16);
        b.setBackground(background(bg, 13, 0));
        b.setPadding(dp(12),dp(14),dp(12),dp(14));
        return b;
    }
}
