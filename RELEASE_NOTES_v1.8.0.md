# SonoRoute v1.8.0 Release

## Overview
SonoRoute v1.8.0 is a native Android audio input/output routing and performance control application featuring an iOS-inspired minimalist single-screen control center.

## Key Features in v1.8.0
- **Audio Routing Engine**: Independent input and output audio hardware endpoint selection leveraging modern Android 12+ `AudioManager.setCommunicationDevice` with graceful API 24–30 fallback.
- **Audio Profiles**: Six tailored presets (Standard, Gaming, Recording, Voice Chat, Screen Sharing, Media Playback).
- **Latency & Buffer Configuration**: Real-time buffer sizing (128 to 2048 frames) and native sample-rate optimizations.
- **Buffer Stability & Self-Healing**: Real-time tracking of `AudioTrack.underrunCount` with interactive single-tap recovery step-ups.
- **Microphone Monitoring**: Low-latency in-ear feedback loop with acoustic loop protection when phone speakers are active.
- **Route Persistence**: Remembers preferred input/output endpoints across app restarts and peripheral reconnections.
- **100% On-Device & Private**: Zero external telemetry, no background tracking, and no persistent audio capture storage.

## Release Artifacts
- **APK**: `release/SonoRoute-v1.8.0.apk` (Size: 23 MB, Package: `com.aistudio.audiorouter.rkxw`)
- **Version Name**: 1.8.0
- **Version Code**: 6
- **SDK Support**: minSdk 24 (Android 7.0) to targetSdk 36 (Android 16)
- **Test Suite**: 35/35 unit and Robolectric tests passing (100% success)
