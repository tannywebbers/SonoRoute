package com.example.ui

enum class AudioProfile(val title: String, val subtitle: String) {
    STANDARD("Standard Audio", "Balanced audio output and input"),
    GAMING("Gaming", "Ultra-low latency audio pipeline"),
    RECORDING("Recording", "Stable uncompressed microphone capture"),
    VOICE_CHAT("Voice Chat", "Communication-focused audio routing"),
    SCREEN_SHARING("Screen Sharing", "Capture-compatible audio configuration"),
    MEDIA("Media Playback", "High-fidelity audio playback stream")
}
