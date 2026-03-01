# Magic Card Displayer (Android, Kotlin)

A foreground-service Android app that continuously listens for a spoken trigger phrase and reveals a playing card fullscreen (including over lock screen).

## Trigger format
A phrase is valid only if it includes all of:
1. Arming word: `magical`
2. Rank: `1..13` (number) or words (`one..thirteen`, plus `ace/jack/queen/king`)
3. Suit keyword (whole word):
   - `shuffles` => ♠
   - `times` => ♥
   - `cuts` => ♦
   - `tricks` => ♣

Examples:
- `magical seven shuffles` => `7♠`
- `magical 12 times` => `Q♥`

## Build & run (Android Studio)
1. Open this folder in Android Studio Ladybug+.
2. Let Gradle sync.
3. Run on a real device or emulator with Google app speech services available.
4. Grant microphone permission (`RECORD_AUDIO`) when prompted.
5. Tap **Listen** to arm continuous listening (foreground notification appears).
6. Speak trigger phrases; on match the phone vibrates and reveal screen opens fullscreen.
7. Tap **Stop** to disarm and stop background listening.

## Notes
- Min SDK: 26.
- Uses built-in Android `SpeechRecognizer` + `RecognizerIntent` (no third-party ASR).
- Foreground service (`foregroundServiceType="microphone"`) is used for background + lock-screen behavior on Android 8+.
