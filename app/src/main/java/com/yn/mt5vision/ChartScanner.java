package com.yn.mt5vision;

import android.graphics.Bitmap;
import java.util.ArrayList;
import java.util.List;

/** Converts explicitly visible red/green chart candles into relative price geometry. */
public final class ChartScanner {
    private ChartScanner() {}

    public static final class Scan {
        public final List<StrategyEngine.Candle> candles;
        public final int width, height;
        public final String message;
        Scan(List<StrategyEngine.Candle> candles, int width, int height, String message) {
            this.candles = candles; this.width = width; this.height = height; this.message = message;
        }
    }

    // Saturated pixels only. White text, yellow price markers and chart grids are ignored.
    public static Scan scan(Bitmap bitmap) {
        if (bitmap == null || bitmap.getWidth() < 200 || bitmap.getHeight() < 350) {
            return new Scan(new ArrayList<>(), 0, 0, "دقة الشاشة غير كافية");
        }
        int w = bitmap.getWidth(), h = bitmap.getHeight();
        int sx = (int)(w * 0.01), ex = (int)(w * 0.84);
        int sy = (int)(h * 0.15), ey = (int)(h * 0.885);
        int[] pixels = new int[w * h];
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h);

        int[] counts = new int[ex - sx], green = new int[ex - sx], red = new int[ex - sx];
        final int yStep = h > 1600 ? 3 : 2;
        for (int y = sy; y < ey; y += yStep) {
            // Detect colored full-width lines (crosshair or markup), not candles.
            int sampledColored = 0;
            for (int x = sx; x < ex; x += 5) {
                if (CandlePalette.classify(pixels[y * w + x]) != 0) sampledColored++;
            }
            if (sampledColored > (ex - sx) / 12) continue;
            int offset = y * w;
            for (int x = sx; x < ex; x++) {
                int kind = CandlePalette.classify(pixels[offset + x]);
                if (kind == 0) continue;
                int j = x - sx; counts[j]++;
                if (kind > 0) green[j]++; else red[j]++;
            }
        }

        List<StrategyEngine.Candle> candles = new ArrayList<>();
        int threshold = Math.max(3, (ey - sy) / (200 * yStep));
        int j = 0;
        final int maxGroupWidth = Math.max(24, (ex - sx) / 22);
        while (j < counts.length) {
            if (counts[j] < threshold) { j++; continue; }
            int start = j, end = j;
            // A wick/body may have a one-pixel gap due to antialiasing.
            while (end + 1 < counts.length) {
                if (counts[end + 1] >= threshold) { end++; continue; }
                if (end + 2 < counts.length && counts[end + 2] >= threshold) {
                    end += 2; continue;
                }
                break;
            }
            j = end + 1;
            int groupWidth = end - start + 1;
            if (groupWidth < 2 || groupWidth > maxGroupWidth) continue;
            int up = 0, down = 0;
            for (int k = start; k <= end; k++) { up += green[k]; down += red[k]; }
            if (up + down < 10 || Math.max(up, down) * 1.0 / (up + down) < 0.85) continue;
            boolean bull = up > down;
            int highY = Integer.MAX_VALUE, lowY = -1, bodyTop = Integer.MAX_VALUE, bodyBottom = -1;
            int minHits = Math.max(2, (int)Math.ceil(groupWidth * 0.55));
            for (int y = sy; y < ey; y++) {
                int hits = 0; int colored = 0;
                for (int x = sx + start; x <= sx + end; x++) {
                    int kind = CandlePalette.classify(pixels[y * w + x]);
                    if (kind != 0) colored++;
                    if ((bull && kind > 0) || (!bull && kind < 0)) hits++;
                }
                if (colored > 0) { highY = Math.min(highY, y); lowY = Math.max(lowY, y); }
                if (hits >= minHits) {
                    bodyTop = Math.min(bodyTop, y);
                    bodyBottom = Math.max(bodyBottom, y);
                }
            }
            if (highY == Integer.MAX_VALUE || bodyTop == Integer.MAX_VALUE
                    || lowY - highY < 5 || bodyBottom < bodyTop) continue;
            // Invert image Y so higher price is a greater value.
            double hi = h - highY, lo = h - lowY;
            double bodyHi = h - bodyTop, bodyLo = h - bodyBottom;
            if (bodyHi > hi || bodyLo < lo) continue;
            double open = bull ? bodyLo : bodyHi;
            double close = bull ? bodyHi : bodyLo;
            if (Math.abs(close - open) < 1.0) continue;
            candles.add(new StrategyEngine.Candle(open, hi, lo, close,
                    sx + (start + end) / 2));
        }
        String msg;
        if (candles.size() < 24) {
            msg = "تم رصد " + candles.size() + " شمعة فقط؛ افتح M1 وأظهر 24+ شمعة بألوان أحمر/أخضر أو تركوازي واضحة";
        } else {
            msg = "تم تمييز " + candles.size() + " شمعة وتحليل فرص الدخول";
        }
        return new Scan(candles, w, h, msg);
    }
}
