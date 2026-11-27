# YN MT5 Vision — Android chart-scanning signal prototype

An independent experimental Android APK for *reading MT5 charts visually* after explicit Android screen-capture consent. It does not modify MT5, execute trades, need a broker login, or use an OpenAI API key.

## How it works
- Uses Android MediaProjection with a foreground notification.
- Processes local screenshots, extracts colored candle bodies and wicks.
- Applies multiple **price-action strategies** (liquidity sweep/reclaim, breakout/retest, mean reversion, compression breakout).
- Applies entry filters against overextension, invalid price scale, contradictory setups, and repeat alerts.
- Shows only BUY/SELL alerts **when qualified**, with a movable floating label and notification. No forced directions.
- Uses the bundled on-device ML Kit OCR to read *visible price-axis labels*. Numeric entry/SL/TP only when the axis is calibrated.
- Modes: Aggressive, Balanced, Conservative.

## Key limitations
- This is an **untested beta**, not verified to achieve a particular win rate. No bot can guarantee zero reversals or profits.
- Screenshot analysis is fundamentally less reliable than access to real OHLC/tick/bid/ask/spread data.
- Supported candle color themes: visually distinct red and green candles, with 30+ bars visible. If candles are white/black or grid overlaps heavily, readings may fail; no signal is a safer result.
- Price-axis OCR is probabilistic. Verify all levels against MT5 before placing a trade. Do **not** use this beta to trade real funds without demo testing.
- Android requests fresh screen-share consent for each capture session. It cannot covertly capture protected screens.
- Does not place orders and does not know trading spreads.

## Use
1. Install the debug APK on Android 8+ (prefer Android 14+).
2. Open MT5, select a clearly visible candlestick chart in M1; make green/red colors distinct and zoom to show at least 30 bars.
3. Open YN MT5 Vision, grant floating-window permission, choose signal strictness, press **بدء فحص MT5**.
4. Approve Android's capture prompt and select the MT5 app window if offered.
5. Return to MT5; a persistent notification confirms scanning. Tap Stop in YN Vision to end it.
6. Prefer demo-only testing and record outcome of every signal before assuming any profitability.

## Build
GitHub Actions uploads a **debug Android APK** as a workflow artifact. No app signing key is stored in the repo; debug signing is for testing only.
