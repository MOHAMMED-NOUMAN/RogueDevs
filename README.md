## Getting Started

### Prerequisites

| Tool | Version | Notes |
|---|---|---|
| Android Studio | Meerkat 2024.3.1 or newer | [Download](https://developer.android.com/studio) |
| JDK | 17 | Bundled with Android Studio |
| Android SDK | API 36 | Install via SDK Manager in Android Studio |
| Git | Any recent | — |

> You do **not** need to install Gradle globally. The project uses the Gradle wrapper (`gradlew.bat` / `gradlew`).

---

### Clone the repo

```bash
git clone https://github.com/<your-org>/itantra.git
cd itantra
```

---

### Open in Android Studio

1. **File → Open** → select the `itantra` folder
2. Wait for Gradle sync to finish (first sync downloads dependencies — takes a few minutes)
3. If Android Studio asks to upgrade AGP, **decline** — the versions are already pinned correctly

---

### Build from the command line

**Windows:**
```powershell
.\gradlew.bat assembleDebug
```

**macOS / Linux:**
```bash
./gradlew assembleDebug
```

The debug APK will be at:
```
app/build/outputs/apk/debug/itantra-debug.apk
```

---

### Run on a device or emulator

**From Android Studio:**
- Select your device in the toolbar and click **Run ▶**

**From the command line** (device must be connected via USB with USB debugging on):
```powershell
# Windows
.\gradlew.bat installDebug

# macOS / Linux
./gradlew installDebug
```

---

### Clean build

```powershell
# Windows
.\gradlew.bat clean assembleDebug

# macOS / Linux
./gradlew clean assembleDebug
```

---

### Toolchain versions (already configured, do not change without team discussion)

| Tool | Version |
|---|---|
| Android Gradle Plugin | 8.9.1 |
| Gradle | 8.11.1 |
| Kotlin | 2.1.21 |
| KSP | 2.1.21-2.0.2 |
| Compose BOM | 2025.09.00 |
| Min SDK | 26 (Android 8.0) |
| Target / Compile SDK | 36 |

---

## Project Structure

```text
itantra/
│
├── app/
│   └── src/
│       └── main/
│           │
│           ├── java/com/itantra/app/
│           │
│           ├── core/
│           │   ├── audio/          # Audio recording, playback and audio processing
│           │   ├── location/       # GPS/location related functionality
│           │   ├── ml/              # Offline STT/TTS model integration
│           │   ├── permissions/    # Android runtime permission handling
│           │   ├── sensors/        # Device sensor access and sensor utilities
│           │   ├── utils/           # Shared utility/helper classes
│           │   └── transport/       # Offline peer link: Wi-Fi Direct, Bluetooth RFCOMM, BLE SOS beacon
│           │
│           ├── data/
│           │   ├── local/
│           │   │   ├── dao/         # Database access objects
│           │   │   ├── database/    # Local database configuration
│           │   │   └── entity/      # Local database entities
│           │   │
│           │   ├── remote/
│           │   │   └── api/         # Remote API definitions if required
│           │   │
│           │   └── repository/      # Repository implementations
│           │
│           ├── domain/
│           │   ├── model/           # Core business/domain models
│           │   ├── repository/     # Repository interfaces
│           │   └── usecase/        # Application business logic/use cases
│           │
│           ├── feature/
│           │   ├── communication/
│           │   │   ├── model/      # Communication-specific models
│           │   │   ├── ui/         # Communication screens/components
│           │   │   └── viewmodel/  # Communication screen state/logic
│           │   │
│           │   ├── emergency/
│           │   │   ├── model/      # Emergency/SOS models
│           │   │   ├── ui/         # Emergency and SOS UI
│           │   │   └── viewmodel/  # Emergency screen state/logic
│           │   │
│           │   ├── location/       # Location sharing and location UI
│           │   ├── pairing/        # QR/device pairing functionality
│           │   ├── settings/       # Application settings
│           │   └── team/           # Team communication and tracking
│           │
│           ├── navigation/         # App navigation and routes
│           │
│           └── ui/
│               └── theme/           # Shared Jetpack Compose theme
│
├── backlog/                        # Screens kept for later, not built (see backlog/README.md)
│
├── ml-models/
│   ├── stt/                        # Offline Speech-to-Text models
│   └── tts/                        # Offline Text-to-Speech models
│
├── gradle/                         # Gradle configuration
│
├── .gradle/                        # Gradle generated files
│
└── .idea/                          # Android Studio project configuration
```

---

# TODO

## Core Communication — MVP

* [x] Android application setup
* [ ] Push-to-Talk communication
* [ ] Voice recording
* [ ] Offline Speech-to-Text (STT)
* [ ] Detect speech pauses/stops
* [ ] Convert speech into complete sentences
* [ ] Convert text into low-bandwidth message
* [x] Device-to-device text transmission
* [x] Bluetooth communication
* [x] Wi-Fi Direct communication
* [ ] Offline Text-to-Speech (TTS)
* [ ] Audio playback
* [ ] End-to-end voice → text → transmission → text → voice flow
* [ ] Multilingual communication
* [ ] Support Hindi
* [ ] Support Gujarati
* [ ] Support Marathi
* [ ] Support Kannada
* [ ] Support Malayalam
* [ ] Support Tamil
* [ ] Support Telugu
* [ ] Support Odia
* [ ] Support Bengali
* [ ] Support English
* [ ] No conventional internet dependency

---

## Performance & Reliability

* [ ] Low-latency communication
* [ ] Reduce STT processing latency
* [ ] Reduce TTS processing latency
* [ ] Optimize model size
* [ ] Optimize RAM usage
* [ ] Optimize CPU usage
* [ ] Optimize battery usage
* [ ] Test on low/mid-range Android devices
* [x] Handle Bluetooth disconnection/reconnection
* [x] Handle Wi-Fi Direct connection failures
* [ ] Handle poor/unstable connections
* [ ] Handle offline operation reliably

---

# Emergency & Differentiating Features

## Location-Aware Communication

* [ ] Attach sender latitude/longitude to message
* [ ] Attach location timestamp
* [ ] Receive and display sender location
* [ ] Display message location on map
* [ ] Show last-known location when required
* [ ] Show distance between users if feasible

---

## Emergency Priority Messages

* [ ] Normal message type
* [ ] Emergency/Priority message type
* [ ] Prioritize emergency messages during bandwidth constraints
* [ ] Strong emergency notification
* [ ] Message acknowledgement
* [ ] Handle emergency messages separately from normal messages

---

## SOS Emergency Alert

* [ ] Configure emergency contacts during setup
* [ ] SOS button
* [ ] Send emergency alert
* [ ] Include user identity
* [ ] Include current location
* [ ] Include timestamp
* [ ] Include battery level if feasible
* [ ] Emergency alert acknowledgement

---

## Emergency Team Tracking

* [ ] Create/join emergency team
* [ ] Share team member locations
* [ ] Periodic location updates
* [ ] Team map
* [ ] Show member last-known location
* [ ] Show location update timestamp
* [ ] Show distance between team members if feasible
* [ ] Team member status
* [ ] Battery-efficient location updates

---

## QR-Based Pairing & Team Joining

* [ ] Generate pairing/session QR code
* [ ] Scan QR code
* [ ] Join communication session using QR
* [ ] Reduce manual device discovery/pairing
* [ ] QR-based emergency team joining

---

# Advanced / Optional

## Indoor Positioning

* [ ] Building-level positioning
* [ ] Floor-level positioning
* [ ] Room-level positioning
* [ ] BLE beacon support
* [ ] Sensor-based positioning
* [ ] Wi-Fi fingerprinting if feasible
* [ ] Sensor fusion
* [ ] Indoor emergency location display

> Indoor positioning is an advanced feature and should only be implemented if the required infrastructure and development time are available.

---

# Testing

* [ ] Test STT accuracy
* [ ] Test TTS intelligibility
* [ ] Test end-to-end latency
* [ ] Test Bluetooth communication
* [ ] Test Wi-Fi Direct communication
* [ ] Test multilingual communication
* [ ] Test offline operation
* [ ] Test low-end Android devices
* [ ] Test emergency messages
* [ ] Test SOS flow
* [ ] Test location sharing
* [ ] Test team tracking
* [ ] Test QR pairing
* [ ] Test reconnection/failure scenarios

---

# Development Rule

Keep the implementation focused on the actual iTantra requirements.

Avoid unnecessary dependencies, unnecessary abstractions, duplicate implementations, and features that are not part of the project requirements.
