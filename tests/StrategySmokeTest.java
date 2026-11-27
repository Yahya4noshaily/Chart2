package com.yn.mt5vision;

import java.util.ArrayList;
import java.util.List;

/** Deterministic checks for setup gating; NOT a backtest or accuracy validation. */
public final class StrategySmokeTest {
    public static void main(String[] args) {
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
        System.out.println("3 on-device strategy guard smoke tests passed.");
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
