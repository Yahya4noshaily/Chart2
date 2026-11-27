package com.yn.mt5vision;

import java.util.ArrayList;
import java.util.List;

/** Deterministic checks for setup gating; NOT a backtest or accuracy validation. */
public final class StrategySmokeTest {
    public static void main(String[] args) {
        must(CandlePalette.classify(0xFF2CA68F) == 1,
                "MT5 teal bullish candle must be detected");
        must(CandlePalette.classify(0xFF29A496) == 1,
                "MT5 cyan bullish candle must be detected");
        must(CandlePalette.classify(0xFFEF534D) == -1,
                "MT5 red bearish candle must be detected");
        must(CandlePalette.classify(0xFFFFFFFF) == 0,
                "White chart background must not be detected");
        must(CandlePalette.classify(0xFF3275D8) == 0,
                "Blue trading UI must not be detected");
        List<StrategyEngine.Candle> candles = base();
        candles.add(new StrategyEngine.Candle(98.5, 100.7, 97.0, 99.9, 200));
        StrategyEngine.Candidate valid = StrategyEngine.analyze(candles, "متوازن");
        must(valid != null && valid.side == StrategyEngine.Side.BUY,
                "Liquidity sweep failed to produce BUY in balanced mode");
        must(valid.stop < valid.entry && valid.takeProfit > valid.entry,
                "Invalid position risk geometry");
        must(valid.strength >= 0.81 && valid.strength <= 1.0,
                "Strength must be a bounded confluence score");
        List<StrategyEngine.Candle> noSetup = base();
        noSetup.add(new StrategyEngine.Candle(100, 101, 99, 100.4, 200));
        must(StrategyEngine.analyze(noSetup, "هجومي") == null,
                "No qualified setup should never be forced into BUY/SELL");
        List<StrategyEngine.Candle> spike = base();
        spike.add(new StrategyEngine.Candle(98, 114, 96, 113, 200));
        must(StrategyEngine.analyze(spike, "هجومي") == null,
                "Chasing a massive unconfirmed price spike must be blocked");
        System.out.println("Strategy + chart color smoke tests passed.");
    }

    private static List<StrategyEngine.Candle> base() {
        List<StrategyEngine.Candle> list = new ArrayList<>();
        for (int i = 0; i < 35; i++) {
            list.add(new StrategyEngine.Candle(100, 101, 99, 100.4, i * 6));
        }
        return list;
    }
    private static void must(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
