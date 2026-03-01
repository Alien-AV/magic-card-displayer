# Magic Card Displayer (Android, Kotlin)

This app continuously listens for speech when ARMED and reveals a fullscreen playing card when a valid phrase is recognized.

## Trigger phrase format
A phrase is valid only if the same recognition result contains:
1. The arming word: `magical`
2. A rank (1-13 as digit or word, e.g., `7`, `seven`, `twelve`)
3. A suit keyword (whole word):
   - `shuffles` => ♠ Spades
   - `times` => ♥ Hearts
   - `cuts` => ♦ Diamonds
   - `tricks` => ♣ Clubs

Examples:
- `magical seven shuffles` => `7♠`
- `magical 12 times` => `Q♥`

## Build & run (Android Studio)
1. Open this folder in Android Studio.
2. Let Gradle sync.
3. Run the `app` configuration on an Android device/emulator (API 26+).
4. Grant **Record audio** permission when prompted.
5. Tap **Listen** to ARM and start background/lockscreen listening.
6. Tap **Stop** to DISARM.

## Notes
- Uses built-in Android `SpeechRecognizer` (no third-party ASR).
- Uses a foreground service + persistent notification for long-running background microphone usage on Android 8+.
- On successful decode, the app vibrates and launches a fullscreen reveal activity that is shown over lockscreen and turns the screen on.
