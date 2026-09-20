# SonoRoute

SonoRoute is a native Android audio input/output routing and audio performance control utility designed with an iOS-inspired minimalist interface. It allows users to inspect connected audio hardware endpoints, manage communication-device routing, configure low-latency audio sessions, monitor buffer stability with self-healing recovery, and diagnose system audio HAL properties directly on device.

## Features

- **Independent Output & Input Routing**: Route communication audio to available output endpoints (built-in speaker, earpiece, wired headphones, Bluetooth headsets, USB DACs) and select input microphones independently where supported by Android.
- **Audio Profiles**: Switch between specialized operational modes including Standard Audio, Gaming (ultra-low latency), Recording (uncompressed mic capture), Voice Chat (communication routing), Screen Sharing (capture-compatible), and Media Playback.
- **Latency Control & Presets**: Select optimized latency modes (Native Low-Latency, Balanced, Safe Stability) with real-time buffer sizing.
- **Buffer Configuration**: Customize AudioTrack buffer sizes from low-latency frames (128–256) up to high-stability buffers (1024–2048).
- **Sample-Rate Configuration**: Supports 44.1 kHz, 48.0 kHz, and hardware-native sample rates with channel configuration options (Mono/Stereo).
- **Audio Session Management**: Centralized `AudioSessionManager` orchestrating full-duplex session states (`IDLE`, `ACTIVE`, `PAUSED`) with strict audio focus management (`AUDIOFOCUS_GAIN`, `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`).
- **Buffer Stability & Self-Healing**: Real-time `AudioTrack.underrunCount` tracking that detects audio glitches and provides one-tap buffer step-up recovery.
- **Microphone Monitoring**: Low-latency in-ear feedback loop with volume controls and built-in acoustic feedback prevention when phone speakers are active.
- **Diagnostics & HAL Inspection**: Comprehensive technical panel displaying native buffer frames, primary sample rates, AAudio/OpenSL ES HAL flags, AudioDeviceInfo attributes, and a chronological session event log.
- **Route Persistence**: Local preference storage remembering preferred input/output endpoints across app launches and hardware reconnections.
- **Dynamic Device Detection**: Real-time `AudioDeviceCallback` registering additions and removals for Bluetooth, Wired, USB, and Built-in hardware.
- **Local & On-Device**: 100% on-device operation with zero network dependencies, analytics, or telemetry.

## Current Version

- **Version Name**: 1.8.0
- **Version Code**: 6
- **Release Status**: Production Release Candidate

## Android Compatibility

- **Minimum SDK**: Android 7.0 (API 24)
- **Target SDK**: Android 16 (API 36)
- **Compile SDK**: Android 16 (API 36)

### Important Android Platform Boundaries & Limitations

1. **Third-Party Application Sandboxing**: On Android, third-party sandboxed applications (e.g. Spotify, YouTube, WhatsApp) manage their own internal audio players. Third-party apps cannot arbitrarily hijack or force the output routing of external apps. SonoRoute manages its own audio streams, communication-device routing, and exposes system routing intents transparently.
2. **Communication-Device Routing**: Device routing is implemented via modern `AudioManager.setCommunicationDevice` (API 31+) with fallback to `MODE_IN_COMMUNICATION`. Communication routes govern VoIP calls and telephony audio streams, while global media streams remain managed by the Android audio policy service.
3. **AudioPlaybackCapture**: Internal playback capture requires Android 10+ (API 29+) and target applications must allow audio capture via `android:allowAudioPlaybackCapture="true"` or explicit user `MediaProjection` consent.
4. **Bluetooth Hardware Latency**: Standard Bluetooth profiles (A2DP, SCO, BLE Audio) incur unavoidable hardware buffer and codec transmission latency (typically 40ms–180ms) irrespective of low-latency software buffer configurations.
5. **Hardware Availability**: Available audio routes and supported sample rates strictly reflect the connected physical peripherals detected by the device HAL.

## Build

To clone the repository and build the APK using Gradle:

```bash
# Clone the repository
git clone https://github.com/tannywebbers/SonoRoute.git
cd SonoRoute

# Build the debug/testable APK
./gradlew :app:assembleDebug

# The generated APK will be located at:
# app/build/outputs/apk/debug/app-debug.apk
```

To build a signed release APK (requires setting up your upload keystore in your environment):

```bash
KEYSTORE_PATH="/path/to/upload-key.jks" \
STORE_PASSWORD="your-store-password" \
KEY_PASSWORD="your-key-password" \
./gradlew :app:assembleRelease
```

## Testing

The project includes an extensive suite of JVM unit and Robolectric tests covering audio device mapping, routing state machines, performance presets, session lifecycle arbitration, and UI components.

To run the complete test suite:

```bash
./gradlew :app:testDebugUnitTest
```

**Current Test Status**: 35 / 35 tests passing (0 failures, 0 skipped, 100% success rate).

## Privacy

SonoRoute is designed for strictly on-device, private operation:
- **Zero Network Transmission**: The application contains no network tracking, no third-party telemetry, no crash-reporting SDKs, and no external advertising.
- **No Audio Recording Storage**: The microphone is accessed only for real-time user-initiated monitoring and testing. Audio buffers are never saved to persistent storage or disk.
- **Safe Defaults**: Microphone monitoring is disabled by default and requires explicit runtime permission consent. When a session terminates, audio streams and memory buffers are immediately purged.
- **Permissions**: Only the minimal permissions required for audio routing (`MODIFY_AUDIO_SETTINGS`, `RECORD_AUDIO`, and `BLUETOOTH_CONNECT`) are requested.
