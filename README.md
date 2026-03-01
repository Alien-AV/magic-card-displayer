# Magic Card Displayer (Android)

An Android app (Kotlin, minSdk 26) that continuously listens for spoken trigger phrases while armed. When it hears a valid phrase containing `magical` + rank + suit keyword, it vibrates and shows a fullscreen card reveal, including when the screen is locked.

## Trigger format
A phrase is valid only if it contains all of:
1. Arming word: `magical`
2. Rank: integer 1-13 (digits or words one-thirteen)
3. Suit keyword (whole word):
   - `shuffles` -> ♠
   - `times` -> ♥
   - `cuts` -> ♦
   - `tricks` -> ♣

Examples:
- `magical seven shuffles` -> `7♠`
- `magical 12 times` -> `Q♥`

## Build & run (Android Studio)
1. Open this folder in Android Studio.
2. Let Gradle sync.
3. Run on a device/emulator with Google speech services.
4. Grant microphone permission (`RECORD_AUDIO`) when prompted.
5. Tap **Listen** to arm and start the foreground listening service.
6. Tap **Stop** to disarm and stop listening.

While armed, the app stays active in the background via a persistent foreground-service notification and keeps listening/restarting recognition loops.
