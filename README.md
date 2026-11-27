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
- Supported candle colors: red and bright green/MT5 teal candles; at least 24 bars visible. The scanner now reads more of the chart viewport and exposes detected bar counts in the floating panel. If candles are white/black or grid overlaps heavily, readings may fail; no signal is a safer result.
- Price-axis OCR is probabilistic. Verify all levels against MT5 before placing a trade. Do **not** use this beta to trade real funds without demo testing.
- Android requests fresh screen-share consent for each capture session. It cannot covertly capture protected screens.
- Does not place orders and does not know trading spreads.

## Use
1. Install the debug APK on Android 8+ (prefer Android 14+).
2. Open MT5, select a clearly visible candlestick chart in M1; make green/red colors distinct and zoom to show at least 24 bars.
3. Open YN MT5 Vision, grant floating-window permission, choose signal strictness, press **بدء فحص MT5**.
4. Approve Android's capture prompt and select the MT5 app window if offered.
5. Return to MT5; a persistent notification confirms scanning. Tap Stop in YN Vision to end it.
6. Prefer demo-only testing and record outcome of every signal before assuming any profitability.

## Build
GitHub Actions uploads a **debug Android APK** as a workflow artifact. No app signing key is stored in the repo; debug signing is for testing only.

## v0.2.0 beta — screenshot-based fix
Identified a real bug on an XAUUSD M1 screenshot: old segmentation classified ~15 bearish-only groups and rejected teal bullish candle pixels. Corrected pure-Java candle palette and confirmed 33 reconstructed candles on the provided screenshot in a separate offline pixel-geometry check. A candle being recognized does not guarantee a qualified entry; rules remain in place to avoid forced trades.


## v0.3.0-beta — stricter entry timing (not validated profitability)
- Strategy patterns are now detected **exclusively on completed candles**, never on the still-forming bar.
- The live candle acts as a strict execution gate: reject late chase, broken zone, strong opposite pressure, prior SL/TP touch, inconsistent candle geometry, and excess risk.
- On-screen Arabic rejection reason displayed rather than a silent SCANNING label.
- Three consecutive consistent snapshots needed before an alert; minimum spacing between opposite alerts.
- Countertrend mean reversion now requires an approximately ranging structure.
- Candlestick wick extraction excludes opposite-color pixels sharing the same x-column.
- No historical MT5 data, spread, tick or order-book feeds are available to this offline screenshot scanner; no claims of 90%/99% hit rate or zero reversal. Demo trade evaluation is essential.

## v0.3.1 beta — lightweight Android ARM64 package
- Produces a *single, signed* APK for arm64-v8a Android handsets (e.g. Infinix GT 30 Pro). Not for x86 or 32-bit-only phones.
- Keeps bundled OCR and the existing strategy logic unchanged.
- GitHub Actions validates the ZIP internal structure, Android APK signing, ABI, and SHA-256 before distribution.
- A phone showing "problem parsing package" with a download smaller than the published size almost certainly has an incomplete/corrupted download. Compare actual file size and SHA-256.
