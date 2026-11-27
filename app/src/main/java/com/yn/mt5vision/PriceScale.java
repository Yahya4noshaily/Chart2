package com.yn.mt5vision;

import android.graphics.Rect;
import com.google.mlkit.vision.text.Text;
import java.util.ArrayList;
import java.util.List;

/** On-device OCR calibration of the visible chart PRICE axis. */
public final class PriceScale {
    public final double a, b, fit;
    public final int anchors;
    private PriceScale(double a, double b, double fit, int anchors) {
        this.a = a; this.b = b; this.fit = fit; this.anchors = anchors;
    }
    public double priceAtY(double y) { return a + b * y; }
    public double priceAtRelative(double priceCoord, int imageHeight) {
        return priceAtY(imageHeight - priceCoord);
    }
    public boolean isValid() { return anchors >= 2 && b < 0 && fit >= 0.975; }

    private static final class Tick {
        final double value, y;
        Tick(double v, double y) { this.value = v; this.y = y; }
    }

    public static PriceScale fromText(Text text, int width, int height) {
        List<Tick> ticks = new ArrayList<>();
        for (Text.TextBlock block : text.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                for (Text.Element element : line.getElements()) {
                    Rect box = element.getBoundingBox();
                    if (box == null || box.centerX() < width * 0.82
                            || box.centerY() < height * 0.12
                            || box.centerY() > height * 0.83) continue;
                    Double v = parseAxisNumber(element.getText());
                    if (v != null && v > 0) ticks.add(new Tick(v, box.exactCenterY()));
                }
            }
        }
        if (ticks.size() < 2) return null;
        // Require distinct vertical positions. Avoid conflating duplicate OCR snippets.
        List<Tick> unique = new ArrayList<>();
        for (Tick t : ticks) {
            boolean duplicate = false;
            for (Tick u : unique) if (Math.abs(u.y - t.y) < 22) duplicate = true;
            if (!duplicate) unique.add(t);
        }
        if (unique.size() < 2) return null;
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        for (Tick t : unique) {
            sx += t.y; sy += t.value; sxx += t.y * t.y; sxy += t.y * t.value;
        }
        double n = unique.size();
        double denom = n * sxx - sx * sx;
        if (Math.abs(denom) < 0.001) return null;
        double b = (n * sxy - sx * sy) / denom;
        double a = (sy - b * sx) / n;
        if (!Double.isFinite(a) || !Double.isFinite(b) || b >= 0) return null;
        double average = sy / n, total = 0, errors = 0;
        for (Tick t : unique) {
            total += Math.pow(t.value - average, 2);
            errors += Math.pow(t.value - (a + b * t.y), 2);
        }
        double fit = total > 0 ? 1 - errors / total : 0;
        if (fit < 0.975) return null;
        double top = a + b * height * 0.12;
        double bottom = a + b * height * 0.83;
        if (bottom <= 0 || top <= bottom
                || (top - bottom) / Math.abs(average) > 0.42) return null;
        return new PriceScale(a, b, fit, unique.size());
    }

    private static Double parseAxisNumber(String raw) {
        if (raw == null) return null;
        String s = raw.trim().replace(" ", "");
        if (!s.matches("[0-9][0-9.,]{0,14}")) return null;
        if (s.contains(",") && s.contains(".")) {
            s = s.replace(",", "");
        } else if (s.contains(",")) {
            int comma = s.lastIndexOf(',');
            if (s.length() - comma - 1 == 3 && comma > 0) s = s.replace(",", "");
            else s = s.replace(',', '.');
        }
        try {
            double d = Double.parseDouble(s);
            if (d >= 0.00001 && Double.isFinite(d)) return d;
        } catch (Exception ignored) {}
        return null;
    }
}
