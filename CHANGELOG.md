# CHANGELOG

## [1.8.0] - 2026-09-19 - Phase 8: Final Release, Compatibility & Production Polish

### Added & Refined
- **Android Version Compatibility & API Guards**:
  - Full compatibility audit across Android API levels (API 24 to API 36).
  - Modern API 31+ `AudioManager.setCommunicationDevice` and `clearCommunicationDevice` with graceful API 24–30 legacy fallbacks (`MODE_IN_COMMUNICATION` / `isSpeakerphoneOn`).
  - Strict compile-time and runtime API guards for Android 10+ (`Build.VERSION_CODES.Q`) AudioPlaybackCapture capabilities and Android 12+ (`Build.VERSION_CODES.S`) Bluetooth connect permissions.
  - Replaced deprecated icon references with official `Icons.AutoMirrored.Filled.VolumeUp` and `Icons.AutoMirrored.Filled.ScreenShare`.
- **Audio Routing Reality Check & Transparency**:
  - Clarified Android system audio architecture boundaries in diagnostics and documentation: distinguishing SonoRoute-owned streams, communication routing, system-controlled routing, isolated third-party apps, and platform capture rules.
  - Transparent route states: `Active`, `Available`, `Switching`, `System controlled`, `Unavailable`, `Failed`, and `Adjusted by device`.
- **Privacy & Security Pass**:
  - Confirmed 100% on-device operation: no audio recording storage, no transmission to servers, no telemetry, no tracking, and no external networking.
  - Microphone monitoring disabled by default; audio buffers are discarded immediately in memory upon stream stoppage.
- **Battery & Idle Efficiency**:
  - Zero background audio monitoring, polling, or RMS calculation when idle or in background.
  - Teardown of all active AudioTrack, AudioRecord, and AudioFocus references on ViewModel destruction (`releaseAll()`).
- **Production Build & Test Audit**:
  - Version bump: `versionName = "1.8.0"`, `versionCode = 6`.
  - Full unit and Robolectric test suite execution passing with 0 failures.

## [1.7.0] - 2026-09-19 - Phase 7: Audio Stability, Error Recovery & Persistent Device Routing

### Added
- **Persistent Device Routing**:
  - Persistent storage in `AudioRoutingManager` via SharedPreferences (`"sonoroute_routing_prefs"`) saving preferred input/output endpoints (hardware type, device name, peripheral MAC address).
  - Automatic restoration of user-preferred routes upon device reconnection or application relaunch.
- **Buffer Instability Detection & Self-Healing**:
  - Added `outputUnderruns` and `isBufferUnstable` monitoring to `AudioSession`.
  - Active monitoring loop checks `AudioTrack.underrunCount` and flags buffer instability when underruns >= 2.
  - Interactive self-healing recovery banner prompting users to step up the audio buffer to the next preset (`increaseBuffer()`) with one tap.
- **Session Lifecycle Controls**:
  - Seamless lifecycle controls (`Start`, `Pause`, `Resume`, `Stop`) in `AudioSessionManager` with immediate atomic StateFlow transitions.
  - Audio profile change safety dialog preventing unintended disruption during active audio playback or capture.
  - Acoustic feedback warning protection when activating microphone monitoring over built-in phone speakers.

## [1.6.0] - 2026-09-19 - Phase 6: Audio Session Modes & Advanced Use Cases

### Added
- **Centralized Audio Session Management**:
  - `AudioSessionManager` orchestrating the Routing Engine (`AudioRoutingManager`), Performance Engine (`AudioPerformanceManager`), and Audio Focus Manager (`AudioFocusManager`).
  - Strict session lifecycle transitions: `IDLE` -> `STARTING` -> `CONFIGURING` -> `ACTIVE` -> `PAUSING` -> `PAUSED` -> `STOPPING` -> `IDLE` with complete resource teardown on release.
  - Resource ownership handling (`MicrophoneResourceState`, `OutputResourceState`) preventing conflicting microphone consumers or simultaneous diagnostic audio streams.
- **Six Specialized Audio Profiles & Session Modes**:
  - `GENERAL`: Standard balanced audio output and input routing.
  - `MEDIA`: High-fidelity audio playback stream with maximum stability buffers.
  - `GAMING`: Ultra-low latency audio pipeline optimizing fast route changes and device-preferred native sample rates.
  - `RECORDING`: Stable uncompressed microphone capture with local privacy guarantees.
  - `VOICE_CHAT`: Full-duplex communication routing utilizing Android's `setCommunicationDevice` with independent input selection.
  - `SCREEN_SHARING`: Capture-compatible audio configuration supporting platform-compliant playback capture.
- **Safe Microphone Monitoring**:
  - In-ear low-latency audio feedback loop for voice confidence and route verification.
  - Safe defaults: disabled by default with required `RECORD_AUDIO` permission checks.
  - Automatic acoustic feedback safeguard detecting built-in phone speaker output and warning users to use headphones.
  - Adjustable monitoring volume level slider (0%–100%) and estimated buffer latency badge.
- **Centralized Audio Focus Coordination**:
  - `AudioFocusManager` dynamically requesting focus according to active session types (Gain, Transient, Ducking) and automatically abandoning focus when sessions conclude.
- **Live Diagnostics & Session Event Log**:
  - Real-time Active Session status section displaying session type, lifecycle state, audio mode (`MODE_IN_COMMUNICATION` vs `MODE_NORMAL`), focus state, buffer configuration, and estimated buffer latency.
  - Lightweight in-memory circular Session Event Log capturing hardware additions/removals, route shifts, and performance updates with a dedicated "Clear Log" action.
  - Clear Android system boundaries notice detailing the isolation of third-party sandboxed apps.
  - Complete resource cleanup in `MainViewModel.onCleared()` releasing AudioRecord, AudioTrack, communication devices, and audio focus.
- **Unit Testing**:
  - Comprehensive unit test suite `AudioSessionManagerTest` verifying session lifecycle, resource arbitration, feedback warning logic, and clean teardown.
- **Version bump**:
  - Updated `versionCode` to 4 and `versionName` to 1.6.0.

## [1.2.0] - 2026-09-17 - Phase 2: Real Input/Output Routing & Unified Single-Screen UI

### Added
- **Real Hardware Audio Routing**:
  - Direct integration with modern Android 12+ (API 31+) `AudioManager.setCommunicationDevice` and `clearCommunicationDevice`
  - Canonical Android audio routing mode switching (`MODE_IN_COMMUNICATION` during active route, `MODE_NORMAL` on system default)
  - Pre-flight routing validation ensuring candidate device is available in system audio HAL
  - Post-routing hardware verification querying `audioManager.communicationDevice` to confirm Android framework accepted the route
  - Independent Input and Output selection: routing output to speakers/headphones does not alter or conflict with microphone selection
  - Graceful disconnection recovery: automatically restores system default speaker route if an active output is unplugged
  - Clear user-facing error reporting with actionable explanation if Android rejects a route request
- **Unified Single-Screen Control Center**:
  - Removed multi-tab bottom navigation in favor of a single, focused control center screen (`MainScreen`)
  - iOS-inspired clean minimalist visual design: soft light background (`#F8F9FB`), high-contrast slate typography, crisp card surfaces with 18-20dp rounded corners, subtle borders, and iOS-style System Blue and Emerald accents
  - Light mode configured as the default theme on launch
  - Replaced manual refresh action button with standard Jetpack Compose Pull-to-Refresh (`PullToRefreshBox`)
  - Prominent `DeviceSelectorCard` controls for Output and Input with large touch targets (48dp+) and active indicators
  - Interactive `ModalBottomSheet` device selectors displaying verified hardware devices, radio selection indicators, and "System Default" reset option
  - Contextual, compact microphone permission banner that cleanly disappears once granted
- **Audio Profile & Latency Architecture**:
  - Audio Profile selector with bottom sheet supporting Standard Audio, Gaming, Voice Chat, and Recording
  - Simplified latency display showing hardware pipeline status (Low Latency vs. Standard) without cluttered technical metrics
  - Dedicated subtle Diagnostics sheet entry moving technical HAL parameters, buffer sizes, and native sample rates into a non-intrusive troubleshooting pane
- **Robolectric & Unit Testing**:
  - Added unit test suite `AudioRoutingManagerTest` verifying route state machine, default resets, device removal fallbacks, and profile definitions
  - Updated screenshot tests verifying component rendering
- **Version bump**:
  - Updated `versionCode` to 2 and `versionName` to 1.2.0

## [1.0.0] - 2026-09-17 - Phase 1: Foundation + Real Device Discovery

### Added
- **Audio Device Discovery Engine**:
  - Direct integration with Android `AudioManager` and `AudioDeviceInfo`
  - Real-time `AudioDeviceCallback` for dynamic addition and removal of devices
  - Categorization of audio devices: Built-in, Wired, Bluetooth (SCO, A2DP, BLE), USB (Headset, DAC, Accessory), HDMI, and Other
  - Hardware specification extraction: real sample rates, channel counts, audio encodings, device addresses (API 28+)
  - Output and Input separation as independent audio pathways
- **Architecture & State Management**:
  - `AudioDeviceManager` service decoupling audio subsystem logic from the UI layer
  - `AudioDiagnosticsProvider` querying Android HAL flags, low latency features, native buffer sizes, and communication support
  - `MainViewModel` utilizing StateFlow and Coroutines for lifecycle-aware reactive UI updates
- **Material 3 Interface**:
  - Tech Slate and Cyan/Violet theme with light and dark mode support
  - Adaptive launcher icon with audio router glyph
  - Home dashboard with Current Route card, Profile overview, and live detected devices list
  - Dedicated Output Devices screen with comprehensive hardware specifications
  - Dedicated Input Devices screen with microphone status
  - Audio Hardware Diagnostics screen with real system property queries
  - Live connection/disconnection event banner
  - Runtime permissions handling for `RECORD_AUDIO` and `BLUETOOTH_CONNECT`
- **Application Metadata**:
  - Set application name to "Audio Router" across `metadata.json`, `strings.xml`, `settings.gradle.kts`, and `app/build.gradle.kts`
  - Configured unique application ID `com.aistudio.audiorouter.rkxw`
