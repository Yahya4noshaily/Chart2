package com.yn.mt5vision;

import java.util.ArrayList;
import java.util.List;

/** Synthetic regression tests; they are NOT a backtest or observed win rate. */
public final class StrategySmokeTest {
    public static void main(String[] args) {
        must(CandlePalette.classify(0xFF2CA68F) == 1, "Teal bullish candle");
        must(CandlePalette.classify(0xFF29A496) == 1, "Cyan bullish candle");
        must(CandlePalette.classify(0xFFEF534D) == -1, "Red bearish candle");
        must(CandlePalette.classify(0xFFFFFFFF) == 0, "White background");
        must(CandlePalette.classify(0xFF3275D8) == 0, "Blue UI");

        List<StrategyEngine.Candle> base = base();
        must(StrategyEngine.analyze(base, "هجومي") == null, "No setup => no forced direction");
        List<StrategyEngine.Candle> closed = base();
        closed.add(new StrategyEngine.Candle(98.5, 100.7, 97.0, 99.9, 210));
        StrategyEngine.Candidate pattern = StrategyEngine.analyze(closed, "متوازن");
        must(pattern != null && pattern.side == StrategyEngine.Side.BUY, "Sweep closed BUY");
        // Test that the live candle is an ENTRY gate, not a pattern generator.
        List<StrategyEngine.Candle> liveBars = new ArrayList<>(closed);
        liveBars.add(new StrategyEngine.Candle(99.9, 100.3, 99.8, 100.05, 216));
        SignalGuard.Decision accepted = SignalGuard.evaluate(liveBars, "متوازن");
        must(accepted.candidate != null && accepted.candidate.side == StrategyEngine.Side.BUY,
                "Near-entry signal must be allowed");
        must(accepted.candidate.stop < accepted.candidate.entry
                        && accepted.candidate.takeProfit > accepted.candidate.entry,
                "BUY risk geometry must be valid");

        List<StrategyEngine.Candle> chased = new ArrayList<>(closed);
        chased.add(new StrategyEngine.Candle(99.9, 102.3, 99.8, 101.9, 216));
        must(SignalGuard.evaluate(chased, "هجومي").candidate == null,
                "Late chase must be rejected");
        List<StrategyEngine.Candle> violated = new ArrayList<>(closed);
        violated.add(new StrategyEngine.Candle(99.9, 100.0, 96.0, 99.8, 216));
        must(SignalGuard.evaluate(violated, "هجومي").candidate == null,
                "Touched stop must be rejected");
        List<StrategyEngine.Candle> flipped = new ArrayList<>(closed);
        flipped.add(new StrategyEngine.Candle(99.9, 100.0, 99.25, 99.3, 216));
        must(SignalGuard.evaluate(flipped, "متوازن").candidate == null,
                "Strong opposite impulse must be rejected");
        List<StrategyEngine.Candle> noClosedSetup = base();
        noClosedSetup.add(new StrategyEngine.Candle(98.5, 100.7, 97.0, 99.9, 210));
        must(SignalGuard.evaluate(noClosedSetup, "هجومي").candidate == null,
                "Live-only setup must never trigger");
        List<StrategyEngine.Candle> malformed = new ArrayList<>(liveBars);
        malformed.set(15, new StrategyEngine.Candle(100, 101, 99, 100.4, 600));
        must(SignalGuard.evaluate(malformed, "متوازن").candidate == null,
                "Invalid candle geometry must be rejected");
        System.out.println("Visual signal quality gates and 9 regression tests passed.");
    }
    private static List<StrategyEngine.Candle> base() {
        List<StrategyEngine.Candle> list = new ArrayList<>();
        for (int i = 0; i < 35; i++)
            list.add(new StrategyEngine.Candle(100, 101, 99, 100.4, i * 6));
        return list;
    }
    private static void must(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
