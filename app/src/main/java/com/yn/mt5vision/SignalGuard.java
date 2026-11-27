package com.yn.mt5vision;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Entry quality gate.
 * All strategies must first qualify on completed candles. The still-forming
 * candle is ONLY an execution-timing/entry-distance check, never the basis of
 * a new pattern. No guarantee of future price direction is implied.
 */
public final class SignalGuard {
    private SignalGuard() {}

    public static final class Decision {
        public final StrategyEngine.Candidate candidate;
        public final String reason;
        private Decision(StrategyEngine.Candidate candidate, String reason) {
            this.candidate = candidate;
            this.reason = reason;
        }
        public static Decision reject(String reason) { return new Decision(null, reason); }
        public static Decision pass(StrategyEngine.Candidate candidate) {
            return new Decision(candidate, "فرصة استوفت شروط الدخول");
        }
    }

    public static Decision evaluate(List<StrategyEngine.Candle> bars, String mode) {
        if (bars == null || bars.size() < 29) {
            return Decision.reject("لا توجد شموع كافية (29+)");
        }
        if (!geometryTrusted(bars)) {
            return Decision.reject("قراءة الشموع غير موثوقة؛ اضبط التكبير");
        }
        int n = bars.size();
        StrategyEngine.Candle live = bars.get(n - 1);
        List<StrategyEngine.Candle> closed = bars.subList(0, n - 1);
        StrategyEngine.Candidate setup = StrategyEngine.analyze(closed, mode);
        if (setup == null) return Decision.reject("لا يوجد نموذج مكتمل مطابق للشروط");
        return checkEntry(setup, live, mode);
    }

    public static Decision checkEntry(StrategyEngine.Candidate setup, StrategyEngine.Candle live,
                                      String mode) {
        if (setup == null || live == null || !Double.isFinite(setup.atr) || setup.atr < 1) {
            return Decision.reject("نموذج غير صالح");
        }
        final double atr = setup.atr;
        final int sign = setup.side == StrategyEngine.Side.BUY ? 1 : -1;
        final boolean aggressive = "هجومي".equals(mode);
        final boolean conservative = "محافظ".equals(mode);
        final double maxChase = (aggressive ? 0.38 : conservative ? 0.19 : 0.27) * atr;
        final double maxAgainst = (aggressive ? 0.28 : conservative ? 0.12 : 0.19) * atr;
        final double travel = (live.close - setup.entry) * sign;

        // A closed-pattern alert would be old news if price already left its entry zone.
        if (travel > maxChase) return Decision.reject("الدخول متأخر: ابتعد السعر عن منطقة الإشارة");
        if (travel < -maxAgainst) return Decision.reject("انكسرت منطقة الدخول قبل التنبيه");
        if (live.range() > (aggressive ? 1.85 : conservative ? 1.35 : 1.55) * atr) {
            return Decision.reject("شمعة غير مستقرة أو حركة عنيفة");
        }
        // Reject an actual stop / target touch before the alert arrives.
        if (sign == 1 && (live.low <= setup.stop + 0.05 * atr
                || live.high >= setup.takeProfit - 0.05 * atr)) {
            return Decision.reject("انتهت صلاحية الإشارة: لمست وقفها أو هدفها");
        }
        if (sign == -1 && (live.high >= setup.stop - 0.05 * atr
                || live.low <= setup.takeProfit + 0.05 * atr)) {
            return Decision.reject("انتهت صلاحية الإشارة: لمست وقفها أو هدفها");
        }
        if ((live.close - live.open) * sign < -atr * (aggressive ? 0.29 : 0.19)) {
            return Decision.reject("ضغط سعري عكسي؛ تم إيقاف الدخول");
        }
        // Reject strong shooting-star/hanging-man wicks against the intended side.
        if (sign == 1 && live.upperWick() > 0.60 * live.range()
                && live.upperWick() > 0.22 * atr) {
            return Decision.reject("ذيل علوي عكسي يحذر من رفض السعر");
        }
        if (sign == -1 && live.lowerWick() > 0.60 * live.range()
                && live.lowerWick() > 0.22 * atr) {
            return Decision.reject("ذيل سفلي عكسي يحذر من رفض السعر");
        }
        double entry = live.close;
        double risk = Math.abs(entry - setup.stop);
        if ((entry - setup.stop) * sign <= 0 || risk < atr * 0.38 || risk > atr * 2.2) {
            return Decision.reject("وقف الخسارة بعيد أو غير منطقي قياسًا بالتذبذب");
        }
        double target = entry + sign * 1.5 * risk;
        if (!Double.isFinite(entry) || !Double.isFinite(target)) {
            return Decision.reject("سعر غير صالح");
        }
        StrategyEngine.Candidate checked = new StrategyEngine.Candidate(
                setup.side, setup.strategy, setup.strength,
                entry, setup.stop, target, atr, setup.bars);
        return Decision.pass(checked);
    }

    static boolean geometryTrusted(List<StrategyEngine.Candle> bars) {
        if (bars == null || bars.size() < 29) return false;
        ArrayList<Integer> gaps = new ArrayList<>();
        int n = bars.size();
        for (int i = 1; i < n; i++) {
            int gap = bars.get(i).x - bars.get(i - 1).x;
            if (gap <= 0) return false;
            gaps.add(gap);
        }
        Collections.sort(gaps);
        double median = gaps.get(gaps.size() / 2);
        if (median < 3 || median > 90) return false;
        int aligned = 0;
        for (int d : gaps) {
            if (d >= median * 0.55 && d <= median * 1.7) aligned++;
        }
        // Detect missing/merged candle columns and UI contamination.
        return aligned >= (int)Math.ceil(gaps.size() * 0.71);
    }
}
