package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.model.AudioDeviceModel
import com.example.audio.routing.AudioRoutingState
import com.example.audio.session.AudioSession
import com.example.audio.session.SessionLifecycleState
import com.example.ui.theme.IosBorderLight
import com.example.ui.theme.IosSystemAmber
import com.example.ui.theme.IosSystemBlue
import com.example.ui.theme.IosSystemGreen
import com.example.ui.theme.IosSystemGreenContainer
import com.example.ui.theme.IosSystemRed
import com.example.ui.theme.IosTextPrimary
import com.example.ui.theme.IosTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlPanelBottomSheet(
    onDismiss: () -> Unit,
    routingState: AudioRoutingState?,
    activeSession: AudioSession?,
    outputDevices: List<AudioDeviceModel>,
    inputDevices: List<AudioDeviceModel>,
    isMonitoringEnabled: Boolean,
    monitoringLevel: Float,
    monitoringFeedbackWarning: String?,
    onSelectOutput: (AudioDeviceModel) -> Unit,
    onSelectInput: (AudioDeviceModel) -> Unit,
    onResetOutputToDefault: () -> Unit,
    onResetInputToDefault: () -> Unit,
    onToggleMonitoring: (Boolean, Boolean) -> Unit,
    onSetMonitoringLevel: (Float) -> Unit,
    onTestOutput: () -> Unit,
    onTestMicrophone: () -> Unit,
    onStartSession: () -> Unit,
    onPauseSession: () -> Unit,
    onResumeSession: () -> Unit,
    onStopSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
        modifier = modifier.testTag("control_panel_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Control Panel",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        ),
                        color = IosTextPrimary
                    )
                    Text(
                        text = "Hardware Routing & Live Controls",
                        style = MaterialTheme.typography.bodySmall,
                        color = IosTextSecondary
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_control_panel_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Control Panel",
                        tint = IosTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 1: Output Routing
            Text(
                text = "AUDIO OUTPUT ROUTING",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp
                ),
                color = IosTextSecondary,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    val requestedOut = routingState?.requestedOutputDevice?.name ?: "System Default"
                    val actualOut = routingState?.actualOutputDevice?.name ?: requestedOut
                    val isOutVerified = routingState?.outputRouteVerified == true

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Requested: $requestedOut",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = IosTextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isOutVerified) Icons.Default.CheckCircle else Icons.Default.Warning,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = if (isOutVerified) IosSystemGreen else IosSystemAmber
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Actual: $actualOut",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isOutVerified) IosSystemGreen else IosSystemAmber
                                )
                            }
                        }

                        TextButton(
                            onClick = onResetOutputToDefault,
                            modifier = Modifier.testTag("reset_output_button")
                        ) {
                            Text("Reset Default", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Quick Switch Output:",
                        style = MaterialTheme.typography.labelSmall,
                        color = IosTextSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        outputDevices.forEach { dev ->
                            val isSelected = routingState?.requestedOutputDevice?.id == dev.id
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) IosSystemBlue else MaterialTheme.colorScheme.surface,
                                border = if (!isSelected) androidx.compose.foundation.BorderStroke(1.dp, IosBorderLight) else null,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onSelectOutput(dev) }
                                    .testTag("quick_output_${dev.name.replace(" ", "_").lowercase()}")
                            ) {
                                Text(
                                    text = dev.name,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal),
                                    color = if (isSelected) Color.White else IosTextPrimary,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 2: Input / Microphone Routing
            Text(
                text = "MICROPHONE INPUT ROUTING",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp
                ),
                color = IosTextSecondary,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    val requestedIn = routingState?.requestedInputDevice?.name ?: "System Default"
                    val actualIn = routingState?.actualInputDevice?.name ?: requestedIn
                    val isInVerified = routingState?.inputRouteVerified == true

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Requested: $requestedIn",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = IosTextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isInVerified) Icons.Default.CheckCircle else Icons.Default.Warning,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = if (isInVerified) IosSystemGreen else IosSystemAmber
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Actual: $actualIn",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isInVerified) IosSystemGreen else IosSystemAmber
                                )
                            }
                        }

                        TextButton(
                            onClick = onResetInputToDefault,
                            modifier = Modifier.testTag("reset_input_button")
                        ) {
                            Text("Reset Default", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Quick Switch Microphone:",
                        style = MaterialTheme.typography.labelSmall,
                        color = IosTextSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        inputDevices.forEach { dev ->
                            val isSelected = routingState?.requestedInputDevice?.id == dev.id
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) IosSystemBlue else MaterialTheme.colorScheme.surface,
                                border = if (!isSelected) androidx.compose.foundation.BorderStroke(1.dp, IosBorderLight) else null,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onSelectInput(dev) }
                                    .testTag("quick_input_${dev.name.replace(" ", "_").lowercase()}")
                            ) {
                                Text(
                                    text = dev.name,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal),
                                    color = if (isSelected) Color.White else IosTextPrimary,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 3: Live Microphone Monitoring
            Text(
                text = "MICROPHONE MONITORING",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp
                ),
                color = IosTextSecondary,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Live Monitoring Pass-Through",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = IosTextPrimary
                            )
                            Text(
                                text = if (isMonitoringEnabled) "Audio streaming to selected output" else "Monitoring stopped",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isMonitoringEnabled) IosSystemGreen else IosTextSecondary
                            )
                        }

                        Switch(
                            checked = isMonitoringEnabled,
                            onCheckedChange = { checked ->
                                onToggleMonitoring(checked, true)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = IosSystemGreen
                            ),
                            modifier = Modifier.testTag("control_panel_monitoring_switch")
                        )
                    }

                    if (monitoringFeedbackWarning != null && isMonitoringEnabled) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(IosSystemAmber.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                .padding(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = IosSystemAmber,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = monitoringFeedbackWarning,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = IosSystemAmber
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Monitoring Level: ${(monitoringLevel * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = IosTextSecondary
                    )
                    Slider(
                        value = monitoringLevel,
                        onValueChange = onSetMonitoringLevel,
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = IosSystemBlue,
                            activeTrackColor = IosSystemBlue
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("control_panel_monitoring_slider")
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 4: 3-Second Audio Diagnostics & Verification
            Text(
                text = "AUDIO ROUTE DIAGNOSTIC TESTS (3 SECONDS)",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp
                ),
                color = IosTextSecondary,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Test 1: Output 3-Second Tone
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Test Output (3s Tone)",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = IosTextPrimary
                            )
                            Text(
                                text = if (routingState?.isOutputTesting == true) {
                                    "Playing tone (${routingState.outputTestCountdown}s)..."
                                } else {
                                    routingState?.outputTestResult ?: "Plays 440Hz tone through selected output"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (routingState?.isOutputTesting == true) IosSystemBlue else IosTextSecondary
                            )
                        }

                        Button(
                            onClick = onTestOutput,
                            enabled = routingState?.isOutputTesting != true,
                            colors = ButtonDefaults.buttonColors(containerColor = IosSystemBlue),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("control_panel_test_output_button")
                        ) {
                            Text(
                                text = if (routingState?.isOutputTesting == true) "${routingState.outputTestCountdown}s" else "Test Output",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    if (routingState?.isOutputTesting == true) {
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { routingState.outputTestProgress },
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                            color = IosSystemBlue
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = IosBorderLight)
                    Spacer(modifier = Modifier.height(14.dp))

                    // Test 2: Microphone 3-Second Capture & Playback
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Test Mic (3s Record + Play)",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = IosTextPrimary
                            )
                            Text(
                                text = if (routingState?.isMicrophoneTesting == true) {
                                    val phaseText = if (routingState.microphoneTestPhase == "recording") "Recording (${routingState.microphoneTestCountdown}s)..." else "Playing back (${routingState.microphoneTestCountdown}s)..."
                                    phaseText
                                } else {
                                    routingState?.microphoneTestResult ?: "Records 3s via mic then plays back"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (routingState?.isMicrophoneTesting == true) IosSystemGreen else IosTextSecondary
                            )
                        }

                        Button(
                            onClick = onTestMicrophone,
                            enabled = routingState?.isMicrophoneTesting != true,
                            colors = ButtonDefaults.buttonColors(containerColor = IosSystemGreen),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("control_panel_test_mic_button")
                        ) {
                            Text(
                                text = if (routingState?.isMicrophoneTesting == true) "${routingState.microphoneTestCountdown}s" else "Test Mic",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    if (routingState?.isMicrophoneTesting == true) {
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = {
                                if (routingState.microphoneTestPhase == "recording") routingState.microphoneTestLevel else routingState.microphoneTestProgress
                            },
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                            color = IosSystemGreen
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 5: Session Controls
            Text(
                text = "ACTIVE SESSION STATE",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp
                ),
                color = IosTextSecondary,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    val lifecycle = activeSession?.lifecycleState ?: SessionLifecycleState.IDLE
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(
                                        when (lifecycle) {
                                            SessionLifecycleState.ACTIVE -> IosSystemGreen
                                            SessionLifecycleState.PAUSED -> IosSystemAmber
                                            else -> IosTextSecondary
                                        },
                                        CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Session: ${lifecycle.name}",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = IosTextPrimary
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (lifecycle == SessionLifecycleState.IDLE || lifecycle == SessionLifecycleState.STOPPING) {
                                Button(
                                    onClick = onStartSession,
                                    colors = ButtonDefaults.buttonColors(containerColor = IosSystemGreen),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.testTag("control_panel_start_session_button")
                                ) {
                                    Text("Start Session", style = MaterialTheme.typography.labelSmall)
                                }
                            } else if (lifecycle == SessionLifecycleState.ACTIVE) {
                                Button(
                                    onClick = onPauseSession,
                                    colors = ButtonDefaults.buttonColors(containerColor = IosSystemAmber),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.testTag("control_panel_pause_session_button")
                                ) {
                                    Text("Pause", style = MaterialTheme.typography.labelSmall)
                                }
                                Button(
                                    onClick = onStopSession,
                                    colors = ButtonDefaults.buttonColors(containerColor = IosSystemRed),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.testTag("control_panel_stop_session_button")
                                ) {
                                    Text("Stop", style = MaterialTheme.typography.labelSmall)
                                }
                            } else if (lifecycle == SessionLifecycleState.PAUSED) {
                                Button(
                                    onClick = onResumeSession,
                                    colors = ButtonDefaults.buttonColors(containerColor = IosSystemGreen),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.testTag("control_panel_resume_session_button")
                                ) {
                                    Text("Resume", style = MaterialTheme.typography.labelSmall)
                                }
                                Button(
                                    onClick = onStopSession,
                                    colors = ButtonDefaults.buttonColors(containerColor = IosSystemRed),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.testTag("control_panel_stop_session_button")
                                ) {
                                    Text("Stop", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
