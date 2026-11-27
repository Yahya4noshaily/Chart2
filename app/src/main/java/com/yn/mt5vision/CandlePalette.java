package com.yn.mt5vision;

/**
 * Pure-Java color segmentation for MetaTrader candlesticks.
 * Includes teal bullish candles used by many MT5 mobile themes.
 * Avoids white chart backgrounds, blue UI buttons and gray gridlines.
 */
public final class CandlePalette {
    private CandlePalette() {}
    public static int classify(int argb) {
        int r = (argb >>> 16) & 0xff;
        int g = (argb >>> 8) & 0xff;
        int b = argb & 0xff;
        // Many MT5 green candles are cyan/teal: G ~= B, not bright lime green.
        if (g > 92 && g > r * 1.28 && g >= b * 0.96
                && b > r * 1.10 && g - r > 40) return 1;
        if (r > 110 && r > g * 1.26 && r > b * 1.19 && r - g > 42) return -1;
        return 0;
    }
}
