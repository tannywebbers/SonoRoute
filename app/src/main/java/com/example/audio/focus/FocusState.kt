package com.example.audio.focus

enum class FocusState(val label: String) {
    NONE("No Focus"),
    GAIN("Active (Gain)"),
    GAIN_TRANSIENT("Active (Transient)"),
    GAIN_TRANSIENT_MAY_DUCK("Active (Ducking)"),
    LOSS("Lost"),
    LOSS_TRANSIENT("Lost (Transient)"),
    LOSS_TRANSIENT_CAN_DUCK("Ducked (External Audio)")
}
