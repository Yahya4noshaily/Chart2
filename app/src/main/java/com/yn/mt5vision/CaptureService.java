package com.yn.mt5vision;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.nio.ByteBuffer;
import java.util.Locale;

/**
 * Real on-device capture loop. Uses user-approved Android MediaProjection;
 * all image analysis and on-device OCR occur locally, without network services.
 */
public final class CaptureService extends Service {
    public static final String ACTION_START = "com.yn.mt5vision.START";
    public static final String ACTION_STATUS = "com.yn.mt5vision.STATUS";
    public static final String EXTRA_RESULT_CODE = "projection_result";
    public static final String EXTRA_CAPTURE_DATA = "projection_intent";
    private static final String SCAN_CHANNEL = "yn_mt5_vision_scanner_v1";
    private static final String ALERT_CHANNEL = "yn_mt5_vision_signals_v1";
    private static final int RUNNING_NOTIFICATION = 101, SIGNAL_NOTIFICATION = 102;

    private HandlerThread thread;
    private Handler worker;
    private final Handler main = new Handler(android.os.Looper.getMainLooper());
    private MediaProjection projection;
    private ImageReader reader;
    private VirtualDisplay virtualDisplay;
    private FloatingBadge floating;
    private TextRecognizer recognizer;
    private volatile PriceScale scale;
    private volatile long scaleAt = 0;
    private volatile boolean shuttingDown = false;
    private long lastScanAt = 0, lastOcrAt = 0, lastBroadcastAt = 0;
    private StrategyEngine.Candidate pending;
    private int pendingCount = 0;
    private long firstPendingAt = 0;
    private String emittedKey = "";
    private volatile long emittedAt = 0;

    @Override public void onCreate() {
        super.onCreate();
        createChannels();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || !ACTION_START.equals(intent.getAction())) return START_NOT_STICKY;
        if (projection != null) {
            publishStatus("هناك جلسة مسح تعمل بالفعل", null);
            return START_NOT_STICKY;
        }
        shuttingDown = false;
        startOngoingForeground();
        try {
            int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
            Intent captureData;
            if (Build.VERSION.SDK_INT >= 33) {
                captureData = intent.getParcelableExtra(EXTRA_CAPTURE_DATA, Intent.class);
            } else {
                captureData = intent.getParcelableExtra(EXTRA_CAPTURE_DATA);
            }
            if (captureData == null || resultCode == 0) throw new IllegalStateException("Capture permission missing");
            MediaProjectionManager manager = (MediaProjectionManager)
                    getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            if (manager == null) throw new IllegalStateException("Screen share unavailable");
            projection = manager.getMediaProjection(resultCode, captureData);
            if (projection == null) throw new IllegalStateException("Screen share denied");

            thread = new HandlerThread("YN-Vision-Scanner");
            thread.start();
            worker = new Handler(thread.getLooper());
            recognizer = TextRecognition.getClient(new TextRecognizerOptions.Builder().build());
            projection.registerCallback(new MediaProjection.Callback() {
                @Override public void onStop() {
                    if (!shuttingDown) {
                        publishStatus("توقفت مشاركة الشاشة؛ أعد تشغيل الماسح", null);
                        stopSelf();
                    }
                }
            }, main);

            DisplayMetrics metrics = new DisplayMetrics();
            WindowManager wm = (WindowManager)getSystemService(WINDOW_SERVICE);
            if (wm == null) throw new IllegalStateException("Display unavailable");
            wm.getDefaultDisplay().getRealMetrics(metrics);
            int w = Math.max(200, metrics.widthPixels), h = Math.max(350, metrics.heightPixels);
            reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2);
            reader.setOnImageAvailableListener(this::readFrame, worker);
            virtualDisplay = projection.createVirtualDisplay("YN MT5 Chart View", w, h,
                    metrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    reader.getSurface(), null, worker);

            if (Build.VERSION.SDK_INT >= 23 && Settings.canDrawOverlays(this)) {
                floating = new FloatingBadge(this);
                main.post(() -> floating.show());
            }
            getSharedPreferences("yn", MODE_PRIVATE).edit().putBoolean("running", true).apply();
            publishStatus("الماسح يعمل ● انتقل إلى شارت MT5 الواضح", null);
        } catch (Exception ex) {
            publishStatus("تعذر تشغيل التقاط الشاشة: " + ex.getClass().getSimpleName(), null);
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void readFrame(ImageReader ir) {
        Image image = null;
        try {
            image = ir.acquireLatestImage();
            if (image == null || shuttingDown) return;
            long now = SystemClock.elapsedRealtime();
            if (now - lastScanAt < 1400) return;
            lastScanAt = now;

            int width = image.getWidth(), height = image.getHeight();
            Image.Plane plane = image.getPlanes()[0];
            ByteBuffer buffer = plane.getBuffer();
            int stride = plane.getRowStride(), pixelStride = plane.getPixelStride();
            if (pixelStride != 4 || stride < width * pixelStride) return;
            int paddedWidth = stride / pixelStride;
            Bitmap padded = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888);
            buffer.rewind();
            padded.copyPixelsFromBuffer(buffer);
            Bitmap bitmap = Bitmap.createBitmap(padded, 0, 0, width, height);
            padded.recycle();

            ChartScanner.Scan scan = ChartScanner.scan(bitmap);
            String mode = getSharedPreferences("yn", MODE_PRIVATE).getString("mode", "متوازن");
            StrategyEngine.Candidate candidate = StrategyEngine.analyze(scan.candles, mode);
            processCandidate(candidate, scan, now);

            if (now - lastBroadcastAt > 4800) {
                lastBroadcastAt = now;
                String msg = scan.message;
                if (scan.candles.size() >= 29 && (scale == null || now - scaleAt > 12000)) {
                    msg += "\nمقياس الأسعار غير مؤكد؛ راجع الأسعار من MT5";
                }
                publishStatus(msg, null);
            }
            if (now - lastOcrAt >= 4700 && recognizer != null) {
                lastOcrAt = now;
                try {
                    InputImage input = InputImage.fromBitmap(bitmap, 0);
                    recognizer.process(input)
                            .addOnSuccessListener(text -> {
                                PriceScale calibrated = PriceScale.fromText(text, width, height);
                                if (calibrated != null && calibrated.isValid()) {
                                    scale = calibrated;
                                    scaleAt = SystemClock.elapsedRealtime();
                                }
                            })
                            .addOnCompleteListener(task -> bitmap.recycle());
                } catch (Exception ex) {
                    bitmap.recycle();
                }
            } else {
                bitmap.recycle();
            }
        } catch (Exception ex) {
            if (SystemClock.elapsedRealtime() - lastBroadcastAt > 5000) {
                lastBroadcastAt = SystemClock.elapsedRealtime();
                publishStatus("تعذر قراءة إطار الشارت؛ تأكد من ظهور الشموع", null);
            }
        } finally {
            if (image != null) image.close();
        }
    }

    /** Suppress one-frame flicker and contradictory tick-by-tick BUY/SELL changes. */
    private void processCandidate(StrategyEngine.Candidate c, ChartScanner.Scan scan, long now) {
        if (c == null) {
            pending = null; pendingCount = 0; firstPendingAt = 0;
            return;
        }
        boolean same = pending != null && pending.identity().equals(c.identity())
                && Math.abs(pending.entry - c.entry) < Math.max(2, c.atr * 0.38);
        if (!same) {
            pending = c; pendingCount = 1; firstPendingAt = now;
            return;
        }
        pending = c; pendingCount++;
        if (pendingCount < 2 || now - firstPendingAt < 1150) return;

        // Keep a snapshot of one setup; do not re-alert identical setups every frame.
        String key = c.identity();
        if (key.equals(emittedKey) && now - emittedAt < 90000) return;
        if (now - emittedAt < 18000) return; // minimum separation between opposing alerts
        emittedKey = key;
        emittedAt = now;
        emitSignal(c, scan, now);
    }

    private void emitSignal(StrategyEngine.Candidate c, ChartScanner.Scan frame, long stamp) {
        String direction = c.side == StrategyEngine.Side.BUY ? "BUY  شراء" : "SELL  بيع";
        PriceScale priceMap = scale;
        boolean mapped = priceMap != null && SystemClock.elapsedRealtime() - scaleAt < 12000;
        String entry = "سعر الدخول: غير مؤكد بصريًا";
        String stop = "وقف الخسارة: راجع MT5";
        String target = "الهدف: راجع MT5";
        if (mapped) {
            double e = priceMap.priceAtRelative(c.entry, frame.height);
            double s = priceMap.priceAtRelative(c.stop, frame.height);
            double t = priceMap.priceAtRelative(c.takeProfit, frame.height);
            if (Double.isFinite(e) && Double.isFinite(s) && Double.isFinite(t)
                    && e > 0 && s > 0 && t > 0 &&
                    (c.side == StrategyEngine.Side.BUY ? s < e && t > e : s > e && t < e)) {
                entry = "الدخول التقريبي: " + formatPrice(e);
                stop = "SL تقريبي: " + formatPrice(s);
                target = "TP تقريبي: " + formatPrice(t);
            }
        }
        String text = direction + "\n" + c.strategy + "\n" +
                entry + "\n" + stop + "\n" + target +
                "\nتوافق الشروط: " + c.strengthLabel() + " (ليس معدل نجاح)" +
                "\nتجريبي — تحقق من الأسعار قبل التداول";
        getSharedPreferences("yn", MODE_PRIVATE).edit().putString("last", text).apply();
        publishStatus("إشارة استراتيجية جديدة — تحقق منها في MT5", text);
        main.post(() -> {
            if (floating != null) {
                floating.update((c.side == StrategyEngine.Side.BUY ? "▲ BUY" : "▼ SELL")
                                + "  |  " + c.strengthLabel() + "\n" + c.strategy,
                        c.side);
            }
            showAlert(direction, c.strategy, text);
        });
        main.postDelayed(() -> {
            if (emittedAt == stamp && floating != null) floating.hideSignal();
        }, 60000);
    }

    private String formatPrice(double p) {
        if (p >= 1000) return String.format(Locale.US, "%.2f", p);
        if (p >= 100) return String.format(Locale.US, "%.3f", p);
        return String.format(Locale.US, "%.5f", p);
    }

    private void publishStatus(String message, String signal) {
        getSharedPreferences("yn", MODE_PRIVATE).edit().putString("status", message).apply();
        Intent status = new Intent(ACTION_STATUS);
        status.setPackage(getPackageName());
        status.putExtra("message", message);
        if (signal != null) status.putExtra("signal", signal);
        sendBroadcast(status);
    }

    private void createChannels() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel scan = new NotificationChannel(SCAN_CHANNEL, "YN Vision • ماسح نشط",
                NotificationManager.IMPORTANCE_LOW);
        scan.setDescription("إشعار دائم أثناء التقاط الشارت");
        nm.createNotificationChannel(scan);
        NotificationChannel alerts = new NotificationChannel(ALERT_CHANNEL, "YN Vision • إشارات التداول",
                NotificationManager.IMPORTANCE_HIGH);
        alerts.enableVibration(true);
        alerts.setDescription("إشارات BUY وSELL المؤكدة حسب قواعد التحليل");
        nm.createNotificationChannel(alerts);
    }

    private PendingIntent openApp() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(this, 1, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void startOngoingForeground() {
        Notification notification = new Notification.Builder(this, SCAN_CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_search)
                .setContentTitle("YN MT5 Vision — فحص الشارت")
                .setContentText("يتم تحليل الشاشة محليًا، اضغط لعرض الماسح")
                .setContentIntent(openApp()).setOngoing(true).build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(RUNNING_NOTIFICATION, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else startForeground(RUNNING_NOTIFICATION, notification);
    }

    private void showAlert(String direction, String strategy, String fullText) {
        Notification n = new Notification.Builder(this, ALERT_CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("YN MT5 Vision • " + direction)
                .setContentText(strategy + " — افتح للتأكد من مستويات السعر")
                .setStyle(new Notification.BigTextStyle().bigText(fullText))
                .setAutoCancel(true).setContentIntent(openApp())
                .setPriority(Notification.PRIORITY_HIGH)
                .build();
        NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(SIGNAL_NOTIFICATION, n);
        try {
            Vibrator v = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) v.vibrate(VibrationEffect.createOneShot(350, 200));
        } catch (Exception ignored) {}
    }

    @Override public void onDestroy() {
        shuttingDown = true;
        getSharedPreferences("yn", MODE_PRIVATE).edit().putBoolean("running", false).apply();
        if (reader != null) try { reader.setOnImageAvailableListener(null, null); } catch (Exception ignored) {}
        if (virtualDisplay != null) { virtualDisplay.release(); virtualDisplay = null; }
        if (reader != null) { reader.close(); reader = null; }
        if (projection != null) {
            MediaProjection p = projection; projection = null;
            try { p.stop(); } catch (Exception ignored) {}
        }
        if (recognizer != null) try { recognizer.close(); } catch (Exception ignored) {}
        if (thread != null) { thread.quitSafely(); thread = null; }
        if (floating != null) {
            FloatingBadge f = floating; main.post(f::close); floating = null;
        }
        publishStatus("الماسح متوقف", null);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
