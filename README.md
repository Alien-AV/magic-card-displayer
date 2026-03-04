# Magic Card Displayer (Phone + TV Android apps)

This project now contains **two Android Studio app modules** that work together over local Wi‑Fi:

- `app` = **Phone controller app** (speech recognition + command sender)
- `tvapp` = **TV display app** (idle background + card reveal display)

## Architecture

### Phone app (`app`)
- Continuous listening when armed.
- Arming word is still `magical`.
- Rank support: `1..13` (digits or words).
- Suit keywords:
  - `shuffles` => ♠
  - `times` => ♥
  - `cuts` => ♦
  - `tricks` => ♣
- On successful decode:
  1. Vibrates once.
  2. Sends `REVEAL` over Wi‑Fi to TV app.
  3. Also shows local phone reveal activity (for debugging).
- On connect/open, sends `INIT` with:
  - pairing token/shared secret
  - idle background image URL for the TV.

### TV app (`tvapp`)
- No ASR/speech features.
- Runs a local **WebSocket server** and **UDP autodiscovery responder**.
- Handles commands:
  - `INIT`: token + idle background URL (download/cache and display while idle)
  - `REVEAL`: show card image fullscreen
  - `CLEAR`: hide card and return to idle image
- Idle behavior:
  - always shows cached background image
  - if download fails, keeps last cached image
- Dismiss behavior:
  - tap screen or remote OK to clear reveal and return to idle.

## Card visuals
The TV app expects a full 52-card PNG set in `tvapp/src/main/res/drawable-nodpi` named like `card_as.png`, `card_10h.png`, etc., and displays cards with `fitCenter` scaling for crisp, non-distorted TV rendering.

> Binary card assets are intentionally **not committed** in this repo.

### Generate card assets (Internet-enabled/local environment)
1. Install Pillow once: `pip install pillow`
2. Run generator: `python tvapp/tools/generate_cards.py`
3. Verify output: 52 files under `tvapp/src/main/res/drawable-nodpi/`

If images are missing, TV `REVEAL` messages are still received but no card image will render until assets are generated.

## Build/run (Android Studio)
1. Open this root folder in Android Studio.
2. Let Gradle sync.
3. Build both modules:
   - Run configuration `app` on your phone.
   - Run configuration `tvapp` on Android TV device/emulator (same LAN).

## Verification flow
1. Launch **TV app** first; it starts listening for UDP discovery and WS commands.
2. Launch **phone app** and tap **Discover/Connect TV**.
3. Arm with **ARM Listening**.
4. Speak a valid phrase such as: `magical seven shuffles`.
5. Verify:
   - phone vibrates and shows local reveal
   - TV shows the corresponding playing card image fullscreen.
6. Tap **Send CLEAR to TV** on phone (or tap TV screen / remote OK) and verify TV returns to idle background.
7. Update idle background URL in code if needed; on reconnect/init TV downloads and caches it.
