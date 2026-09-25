# iTantra

**Offline, multilingual push-to-talk for emergency teams.** Speak into one phone; the words are
turned into text on the device, sent to a teammate's phone over Wi-Fi Direct or Bluetooth, and
shown there. No internet, SIM or server needed.

Smart India Hackathon 2026 · Problem Statement **26173** · Team **Rogue Devs** (ID 134696)

---

## What works today

| Feature | Status |
|---|---|
| Pair two phones with a 4-digit code | Working |
| Offline link: Wi-Fi Direct first, Bluetooth backup, moves back to Wi-Fi by itself | Working, tested on two phones |
| Hold to Talk: speech to text on the phone (English, Hindi) | Working |
| Send the text to the paired phone, with delivery status | Working |
| Profile, speech language, emergency numbers (saved on the phone) | Working |
| Link notification with live status and Disconnect | Working |
| Read incoming messages aloud (text-to-speech) | Next |
| QR pairing and encryption (X25519 + AES-256-GCM) | Planned |
| Team map, organisation feed | In [`backlog/`](backlog/README.md) |

Speech-to-text speed on a Samsung Galaxy A54 (4-second sentence): **English ~0.8 s, Hindi ~1.7 s**,
both transcribed correctly.

---

## How it works

```
Hold to Talk ──► mic (16 kHz) ──► Whisper STT (ONNX, on device) ──► text
                                                                     │
                            split into ≤240-byte packets, ACK + resend
                                                                     ▼
                       Wi-Fi Direct  ──(falls back to)──►  Bluetooth RFCOMM
                                                                     │
                                          teammate's phone: reassemble ──► shown on Home
```

- **Pairing:** one phone taps *Show my code*, the other *Enter a code*. The code finds the phone
  over Bluetooth and also sets up the Wi-Fi Direct network, so there is nothing else to configure.
- **Speech-to-text:** Whisper-tiny fine-tuned per language, INT8 ONNX (~42 MB per language), run
  with ONNX Runtime. The model for the language chosen in Settings is loaded when the app opens.
- **Offline only:** a Gradle check (`checkOfflineOnly`) fails the build if an HTTP client or a
  Google Play Services / Firebase / ML Kit library is added.

---

## Getting started

### Prerequisites

| Tool | Version |
|---|---|
| Android Studio | Meerkat 2024.3.1 or newer |
| JDK | 17 (bundled with Android Studio) |
| Android SDK | API 36 |
| Phones for testing | Android 10 or newer (Wi-Fi Direct link needs it) |

The Gradle wrapper is included; no global Gradle install is needed.

### Clone, build, install

```bash
git clone https://github.com/MOHAMMED-NOUMAN/RogueDevs.git
cd RogueDevs
./gradlew assembleDebug        # Windows: .\gradlew.bat assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/itantra-debug.apk`. Install it with
`./gradlew installDebug` (phone connected with USB debugging on) or by copying the APK to the phone.

In Android Studio: **File → Open** the folder, wait for Gradle sync, then **Run ▶**. If it offers
to upgrade the Android Gradle Plugin, decline; versions are pinned.

### Try it on two phones

1. Install the APK on both phones and sign up (name + language).
2. **Pair** tab: phone A taps **Show my code**, phone B taps **Enter a code** and types it.
3. **Home**: hold the mic, speak, let go. The text appears under the button with its delivery
   status, and on the other phone under the header.
4. Speech language: **Settings → Speech Language** (English or Hindi).

### Developer builds and tests

| Command | What it does |
|---|---|
| `./gradlew testDebugUnitTest` | JVM tests: transport engine, message splitting |
| `./gradlew assembleTransportDebug` | Debug APK plus the "iTantra transport debug" test screen (`src/transportDebug`) |
| `adb shell am instrument -w -e class com.itantra.stt.SttBenchmarkTest com.itantra.app.test/androidx.test.runner.AndroidJUnitRunner` | Speech-to-text speed and accuracy on a phone (install the app and test APKs first; report in `adb logcat -s SttBenchmark`) |

The STT benchmark is run with `am instrument` rather than `connectedAndroidTest`, because the
latter uninstalls the app (and its saved profile) afterwards.

---

## Project structure

```text
app/src/main/java/com/itantra/
├── app/
│   ├── core/
│   │   ├── audio/          # Push-to-talk microphone recording
│   │   ├── messaging/      # Text messages: packet format, splitting, send/receive
│   │   ├── ml/             # Speech-to-text service (loads the Whisper model)
│   │   ├── permissions/    # Runtime permissions for the link
│   │   ├── prefs/          # Saved profile, speech language, emergency numbers
│   │   └── transport/      # Offline link: pairing, Wi-Fi Direct, Bluetooth, SOS beacon, service
│   ├── feature/
│   │   ├── communication/  # Pair screen, push-to-talk state
│   │   ├── emergency/      # SOS screen
│   │   ├── home/           # Home screen and bottom navigation
│   │   ├── pairing/        # Pairing state and prompts
│   │   ├── settings/       # Settings screen
│   │   └── signup/         # Signup and language picker
│   ├── navigation/         # Signup → Home
│   └── ui/                 # Theme, logo
└── stt/                    # Whisper ONNX inference and benchmark

app/src/main/assets/        # whisper-en, whisper-hi models + sample clips for the benchmark
app/src/transportDebug/     # Two-phone link test screen (transportDebug build only)
backlog/                    # Screens kept for later, not built
```

Empty folders such as `data/`, `domain/`, `core/location/` and `feature/team/` are placeholders for
planned work.

### Toolchain (pinned; change only after team discussion)

| Tool | Version |
|---|---|
| Android Gradle Plugin | 8.9.1 |
| Gradle | 8.11.1 |
| Kotlin | 2.1.21 |
| KSP | 2.1.21-2.0.2 |
| Compose BOM | 2025.09.00 |
| Min SDK / Target SDK | 26 / 36 |

---

## Contributing

See [CONTRIBUTION.md](CONTRIBUTION.md). Keep changes focused on the iTantra requirements: no
unnecessary dependencies, abstractions or duplicate implementations.

## License

See [LICENSE](LICENSE).
