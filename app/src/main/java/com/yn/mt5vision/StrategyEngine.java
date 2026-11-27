package com.yn.mt5vision;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Price-action engine on reconstructed candle coordinates.
 * Prices are arbitrary y-inverted image units: they are NOT broker quotes.
 * Signal strength is rules confluence, NEVER calibrated win probability.
 */
public final class StrategyEngine {
    private StrategyEngine() {}

    public enum Side { BUY, SELL }

    public static final class Candle {
        public final double open, high, low, close;
        public final int x;
        public Candle(double open, double high, double low, double close, int x) {
            this.open = open;
            this.high = Math.max(high, Math.max(open, close));
            this.low = Math.min(low, Math.min(open, close));
            this.close = close;
            this.x = x;
        }
        public double range() { return Math.max(0.001, high - low); }
        public double body() { return Math.abs(close - open); }
        public double lowerWick() { return Math.max(0, Math.min(open, close) - low); }
        public double upperWick() { return Math.max(0, high - Math.max(open, close)); }
        public boolean bullish() { return close > open; }
        public boolean bearish() { return close < open; }
    }

    public static final class Candidate {
        public final Side side;
        public final String strategy;
        public final double strength, entry, stop, takeProfit, atr;
        public final int bars;
        public Candidate(Side side, String strategy, double strength, double entry,
                         double stop, double takeProfit, double atr, int bars) {
            this.side = side;
            this.strategy = strategy;
            this.strength = strength;
            this.entry = entry;
            this.stop = stop;
            this.takeProfit = takeProfit;
            this.atr = atr;
            this.bars = bars;
        }
        public String identity() { return side.name() + "|" + strategy; }
        public String strengthLabel() {
            return String.format(Locale.US, "%.0f/100", strength * 100.0);
        }
    }

    private static final class Setup {
        final Side side; final String name; final double score; final double stop;
        Setup(Side side, String name, double score, double stop) {
            this.side = side; this.name = name; this.score = score; this.stop = stop;
        }
    }

    public static Candidate analyze(List<Candle> candles, String mode) {
        if (candles == null || candles.size() < 24) return null;
        final int n = candles.size();
        final Candle c = candles.get(n - 1);
        final Candle p = candles.get(n - 2);
        final double atr = atr(candles, 14);
        if (!Double.isFinite(atr) || atr < 1.0) return null;

        // Reject malformed/in-progress doji, oversized chasing bars, and tiny unreadable charts.
        if (c.range() < atr * 0.22 || c.range() > atr * 2.7) return null;
        if (c.body() < c.range() * 0.12) return null;

        List<Setup> found = new ArrayList<>();
        final double support = minLow(candles, n - 11, n - 1);
        final double resistance = maxHigh(candles, n - 11, n - 1);
        final double bodyFraction = c.body() / c.range();

        // 1. Liquidity sweep + reclaim — not dependent on a directional trend.
        if (c.low < support - atr * 0.08 && c.close > support + atr * 0.10
                && c.bullish() && c.lowerWick() > Math.max(c.body() * 0.65, c.range() * 0.30)) {
            double bonus = c.close > p.high ? 0.07 : 0.0;
            found.add(new Setup(Side.BUY, bonus > 0 ? "Sweep + Micro Break" :
                    "Liquidity Sweep + Reclaim", 0.86 + bonus, c.low - atr * 0.14));
        }
        if (c.high > resistance + atr * 0.08 && c.close < resistance - atr * 0.10
                && c.bearish() && c.upperWick() > Math.max(c.body() * 0.65, c.range() * 0.30)) {
            double bonus = c.close < p.low ? 0.07 : 0.0;
            found.add(new Setup(Side.SELL, bonus > 0 ? "Liquidity Sweep + MSS" :
                    "Liquidity Sweep + Reclaim", 0.83 + bonus, c.high + atr * 0.14));
        }

        // 2. Two-candle sweep then micro structure shift.
        final double oldLow = minLow(candles, n - 12, n - 2);
        final double oldHigh = maxHigh(candles, n - 12, n - 2);
        if (p.low < oldLow - atr * 0.07 && p.close > oldLow && p.lowerWick() > p.body()
                && c.bullish() && c.close > p.high + atr * 0.03 && c.close <= p.high + 1.4 * atr) {
            found.add(new Setup(Side.BUY, "Sweep + Structure Shift", 0.91,
                    Math.min(p.low, c.low) - 0.12 * atr));
        }
        if (p.high > oldHigh + atr * 0.07 && p.close < oldHigh && p.upperWick() > p.body()
                && c.bearish() && c.close < p.low - atr * 0.03 && c.close >= p.low - 1.4 * atr) {
            found.add(new Setup(Side.SELL, "Sweep + Structure Shift", 0.91,
                    Math.max(p.high, c.high) + 0.12 * atr));
        }

        // 3. Breakout + retest: the previous bar breaks the earlier structure,
        // and the current bar retests that structure without losing it.
        final double preBreakHigh = maxHigh(candles, n - 19, n - 2);
        final double preBreakLow = minLow(candles, n - 19, n - 2);
        if (p.close > preBreakHigh + atr * 0.06 && c.low <= preBreakHigh + atr * 0.24
                && c.close > preBreakHigh + atr * 0.08 && c.bullish()
                && c.close < preBreakHigh + 1.9 * atr) {
            found.add(new Setup(Side.BUY, "Breakout + Retest", 0.86,
                    Math.min(c.low, preBreakHigh - atr * 0.23) - atr * 0.12));
        }
        if (p.close < preBreakLow - atr * 0.06 && c.high >= preBreakLow - atr * 0.24
                && c.close < preBreakLow - atr * 0.08 && c.bearish()
                && c.close > preBreakLow - 1.9 * atr) {
            found.add(new Setup(Side.SELL, "Breakout + Retest", 0.86,
                    Math.max(c.high, preBreakLow + atr * 0.23) + atr * 0.12));
        }

        // 4. Statistical mean reversion at a 20-candle extreme, with rejection.
        double mean = sma(candles, n - 21, n - 1);
        double sd = stdev(candles, n - 21, n - 1, mean);
        double z = sd > 0.001 ? (c.close - mean) / sd : 0;
        double rsi = rsi(candles, 14);
        boolean meanRevRange = Math.abs(ema(candles, 9) - ema(candles, 21)) < atr * 0.34;
        if (meanRevRange && z < -1.55 && rsi < 42 && c.bullish() && c.lowerWick() > c.body() * 0.45
                && c.close > p.close - atr * 0.14) {
            found.add(new Setup(Side.BUY, "Mean Reversion + Rejection",
                    0.84 + (rsi < 33 ? 0.045 : 0), c.low - atr * 0.20));
        }
        if (meanRevRange && z > 1.55 && rsi > 58 && c.bearish() && c.upperWick() > c.body() * 0.45
                && c.close < p.close + atr * 0.14) {
            found.add(new Setup(Side.SELL, "Mean Reversion + Rejection",
                    0.84 + (rsi > 67 ? 0.045 : 0), c.high + atr * 0.20));
        }

        // 5. Compression -> expansion breakout, using *relative* candle ranges.
        double recentRange = avgRange(candles, n - 9, n - 1);
        double olderRange = avgRange(candles, n - 23, n - 10);
        boolean compressed = olderRange > 0 && recentRange / olderRange < 0.77;
        double tightHigh = maxHigh(candles, n - 13, n - 1);
        double tightLow = minLow(candles, n - 13, n - 1);
        if (compressed && c.range() > recentRange * 1.25 && c.bullish()
                && c.close > tightHigh + 0.10 * atr && c.upperWick() / c.range() < 0.24) {
            found.add(new Setup(Side.BUY, "Compression Breakout", 0.85,
                    Math.min(c.low, tightHigh - atr * 0.19) - 0.14 * atr));
        }
        if (compressed && c.range() > recentRange * 1.25 && c.bearish()
                && c.close < tightLow - 0.10 * atr && c.lowerWick() / c.range() < 0.24) {
            found.add(new Setup(Side.SELL, "Compression Breakout", 0.85,
                    Math.max(c.high, tightLow + atr * 0.19) + 0.14 * atr));
        }

        // 6. Pullback momentum: a secondary setup, not the main decision rule.
        double ema9 = ema(candles, 9);
        double ema21 = ema(candles, 21);
        if (ema9 > ema21 + atr * 0.18 && c.bullish()
                && c.low <= ema9 + atr * 0.15 && c.close > ema9 + 0.14 * atr
                && c.close < ema9 + atr * 1.05 && c.lowerWick() > c.upperWick() * 0.9
                && bodyFraction > 0.30) {
            found.add(new Setup(Side.BUY, "Momentum Pullback", 0.81, c.low - 0.20 * atr));
        }
        if (ema9 < ema21 - atr * 0.18 && c.bearish()
                && c.high >= ema9 - atr * 0.15 && c.close < ema9 - 0.14 * atr
                && c.close > ema9 - atr * 1.05 && c.upperWick() > c.lowerWick() * 0.9
                && bodyFraction > 0.30) {
            found.add(new Setup(Side.SELL, "Momentum Pullback", 0.81, c.high + 0.20 * atr));
        }

        if (found.isEmpty()) return null;
        // Anti-whipsaw penalty for alternating large red/green bars.
        int flips = 0;
        for (int i = n - 6; i < n - 1; i++) {
            if (candles.get(i).bullish() != candles.get(i + 1).bullish()) flips++;
        }
        Setup best = null;
        double bestScore = -1;
        double rivalScore = -1;
        for (Setup s : found) {
            if (s.side == Side.BUY && (c.upperWick() > c.range() * 0.48
                    || c.close > c.high - 0.02 * atr && c.range() > 2.0 * atr)) continue;
            if (s.side == Side.SELL && (c.lowerWick() > c.range() * 0.48
                    || c.close < c.low + 0.02 * atr && c.range() > 2.0 * atr)) continue;
            double score = s.score - (flips >= 4 ? 0.06 : 0.0)
                    - (c.range() > 1.9 * atr ? 0.07 : 0.0);
            double risk = Math.abs(c.close - s.stop);
            if (risk < atr * 0.27 || risk > atr * 3.0) continue;
            if (s.side == Side.BUY && s.stop >= c.close) continue;
            if (s.side == Side.SELL && s.stop <= c.close) continue;
            if (score > bestScore) {
                if (best != null && s.side != best.side) rivalScore = Math.max(rivalScore, bestScore);
                bestScore = score; best = s;
            } else if (best != null && s.side != best.side) {
                rivalScore = Math.max(rivalScore, score);
            }
        }
        if (best == null || rivalScore >= bestScore - 0.12) return null;
        double threshold = "محافظ".equals(mode) ? 0.90 :
                ("هجومي".equals(mode) ? 0.80 : 0.85);
        if (bestScore < threshold) return null;
        double entry = c.close;
        double risk = Math.abs(entry - best.stop);
        double tp = entry + (best.side == Side.BUY ? 1 : -1) * 1.6 * risk;
        return new Candidate(best.side, best.name, bestScore, entry, best.stop, tp, atr, n);
    }

    private static double atr(List<Candle> d, int window) {
        int n = d.size(); double sum = 0;
        for (int i = n - window; i < n; i++) {
            Candle c = d.get(i);
            double previousClose = d.get(i - 1).close;
            sum += Math.max(c.high - c.low,
                    Math.max(Math.abs(c.high - previousClose),
                             Math.abs(c.low - previousClose)));
        }
        return sum / window;
    }
    private static double maxHigh(List<Candle> d, int from, int toExclusive) {
        double v = -Double.MAX_VALUE;
        for (int i = from; i < toExclusive; i++) v = Math.max(v, d.get(i).high);
        return v;
    }
    private static double minLow(List<Candle> d, int from, int toExclusive) {
        double v = Double.MAX_VALUE;
        for (int i = from; i < toExclusive; i++) v = Math.min(v, d.get(i).low);
        return v;
    }
    private static double avgRange(List<Candle> d, int from, int toExclusive) {
        double s = 0;
        for (int i = from; i < toExclusive; i++) s += d.get(i).range();
        return s / Math.max(1, toExclusive - from);
    }
    private static double sma(List<Candle> d, int from, int toExclusive) {
        double s = 0;
        for (int i = from; i < toExclusive; i++) s += d.get(i).close;
        return s / Math.max(1, toExclusive - from);
    }
    private static double stdev(List<Candle> d, int from, int toExclusive, double mean) {
        double s = 0;
        for (int i = from; i < toExclusive; i++) {
            double k = d.get(i).close - mean; s += k * k;
        }
        return Math.sqrt(s / Math.max(1, toExclusive - from));
    }
    private static double ema(List<Candle> d, int period) {
        double alpha = 2.0 / (period + 1.0);
        double out = d.get(0).close;
        for (int i = 1; i < d.size(); i++) out += alpha * (d.get(i).close - out);
        return out;
    }
    private static double rsi(List<Candle> d, int window) {
        int n = d.size(); double gains = 0, losses = 0;
        for (int i = n - window; i < n; i++) {
            double delta = d.get(i).close - d.get(i - 1).close;
            if (delta > 0) gains += delta; else losses -= delta;
        }
        if (gains == 0 && losses == 0) return 50;
        if (losses == 0) return 100;
        return 100 - 100 / (1 + gains / losses);
    }
}
