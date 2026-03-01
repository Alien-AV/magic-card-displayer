# Magic Card Displayer (2-app Wi-Fi architecture)

This project now contains **two Android apps**:

1. **Phone app (`:app`)** – voice controller with ASR + card decode.
2. **TV app (`:tvapp`)** – network display target (no ASR).

## Voice phrase format (phone app)
A phrase decodes only when it contains all three in one recognition result:
1. Arming word: `magical`
2. Rank: `1-13` or word (`one` ... `thirteen`)
3. Suit keyword:
   - `shuffles` => ♠ Spades
   - `times` => ♥ Hearts
   - `cuts` => ♦ Diamonds
   - `tricks` => ♣ Clubs

Example: `magical seven shuffles` => `7♠`

## Network protocol
The phone discovers the TV over UDP broadcast, then sends commands by WebSocket.

- UDP discovery request: `MCD_DISCOVER` on port `41234`
- TV responds with JSON: `{ "host": "<ip>", "wsPort": 41235 }`
- WS messages:
  - `INIT`: pairing token + idle background image URL
  - `REVEAL`: rank/suit payload for fullscreen card render
  - `CLEAR`: dismiss card to idle screen

## Build and run
1. Open this folder in Android Studio.
2. Let Gradle sync for both modules.
3. Install **`tvapp`** on Android TV / TV device on local Wi-Fi.
4. Install **`app`** on Android phone on the same Wi-Fi.

## TV app behavior
- Starts UDP discovery responder + WS server on launch.
- Shows cached idle background fullscreen when no card is revealed.
- On `INIT`, updates pairing token and attempts to download new idle image.
  - If download fails, previous cached background stays visible.
- On `REVEAL`, displays a fullscreen rendered playing-card graphic.
- Tap or press D-pad center/OK to dismiss card back to idle.
- `CLEAR` command also dismisses the card.

## Phone app usage
1. Open phone app.
2. (Optional) Enter idle image URL.
3. Tap **Discover + Connect TV** (sends `INIT`).
4. Tap **Listen** to ARM continuous listening.
5. Speak valid magical phrase.
6. On decode: phone vibrates once, shows local reveal for debugging, and sends `REVEAL` to TV.
7. Tap **Clear TV card** to send `CLEAR`.
8. Tap **Stop** to DISARM.

## Notes
- Uses built-in Android `SpeechRecognizer`.
- Phone keeps foreground service for continuous listening (Android 8+).
- TV app performs no speech recognition.
