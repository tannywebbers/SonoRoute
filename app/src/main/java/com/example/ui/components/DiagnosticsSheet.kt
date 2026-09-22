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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.HighlightOff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.diagnostics.AudioDiagnostics
import com.example.audio.focus.FocusState
import com.example.audio.model.AudioDeviceModel
import com.example.audio.performance.AudioPerformanceState
import com.example.audio.routing.AudioRoutingState
import com.example.audio.session.AudioSession
import com.example.audio.session.SessionEvent
import com.example.audio.session.SessionLifecycleState
import com.example.ui.theme.AudioEmeraldActive
import com.example.ui.theme.IosBorderLight
import com.example.ui.theme.IosSystemBlue
import com.example.ui.theme.IosSystemGreen
import com.example.ui.theme.IosSystemRed
import com.example.ui.theme.IosTextPrimary
import com.example.ui.theme.IosTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DiagnosticsEntryCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(18.dp)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("diagnostics_entry_card")
            .clip(shape)
            .border(1.dp, IosBorderLight, shape)
            .clickable(onClick = onClick),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = IosSystemBlue,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Diagnostics",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    ),
                    color = IosTextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Audio routing verification & subsystem tests",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = IosTextSecondary
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = "Open Diagnostics",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsSheet(
    diagnostics: AudioDiagnostics?,
    activeOutput: AudioDeviceModel?,
    activeInput: AudioDeviceModel?,
    performanceState: AudioPerformanceState? = null,
    routingState: AudioRoutingState? = null,
    activeSession: AudioSession? = null,
    eventLog: List<SessionEvent> = emptyList(),
    isMonitoringEnabled: Boolean = false,
    monitoringLevel: Float = 0.8f,
    monitoringFeedbackWarning: String? = null,
    focusState: FocusState? = null,
    playbackCaptureStatus: String = "Supported (Android 10+ with target app permission)",
    onTestOutput: () -> Unit = {},
    onTestMicrophone: () -> Unit = {},
    onToggleMonitoring: (Boolean) -> Unit = {},
    onSetMonitoringLevel: (Float) -> Unit = {},
    onClearDiagnosticsLog: () -> Unit = {},
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    val timeFormatter = rememberSaveable { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    val sampleRate = performanceState?.actualSampleRate ?: 48000
    val bufferFrames = performanceState?.actualBufferSizeFrames ?: 256
    val estimatedBufferLatencyMs = if (sampleRate > 0) {
        "%.1f".format((bufferFrames.toFloat() / sampleRate) * 1000f)
    } else "0.0"

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
                .testTag("diagnostics_sheet_content")
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Diagnostics & Session Control",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp
                        ),
                        color = IosTextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Real-time session state, low-latency monitoring, and HAL verification",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = IosTextSecondary
                    )
                }

                IconButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (diagnostics == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Loading system audio diagnostics...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = IosTextSecondary
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Section 1: Active Audio Session (Requirement 33)
                    item {
                        TechGroup(title = "ACTIVE AUDIO SESSION") {
                            val sessionTitle = activeSession?.sessionType?.title ?: "Standard Audio"
                            val sessionState = activeSession?.lifecycleState ?: SessionLifecycleState.ACTIVE

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Session Type",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                    color = IosTextSecondary
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = IosSystemBlue.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = sessionTitle,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 11.sp
                                        ),
                                        color = IosSystemBlue
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Lifecycle State",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                    color = IosTextSecondary
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = when (sessionState) {
                                        SessionLifecycleState.ACTIVE -> AudioEmeraldActive.copy(alpha = 0.12f)
                                        SessionLifecycleState.CONFIGURING, SessionLifecycleState.STARTING -> IosSystemBlue.copy(alpha = 0.12f)
                                        SessionLifecycleState.ERROR -> IosSystemRed.copy(alpha = 0.12f)
                                        SessionLifecycleState.PAUSING, SessionLifecycleState.PAUSED, SessionLifecycleState.STOPPING, SessionLifecycleState.IDLE -> MaterialTheme.colorScheme.surfaceVariant
                                    }
                                ) {
                                    Text(
                                        text = sessionState.label,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 11.sp
                                        ),
                                        color = when (sessionState) {
                                            SessionLifecycleState.ACTIVE -> AudioEmeraldActive
                                            SessionLifecycleState.CONFIGURING, SessionLifecycleState.STARTING -> IosSystemBlue
                                            SessionLifecycleState.ERROR -> IosSystemRed
                                            SessionLifecycleState.PAUSING, SessionLifecycleState.PAUSED, SessionLifecycleState.STOPPING, SessionLifecycleState.IDLE -> IosTextSecondary
                                        }
                                    )
                                }
                            }

                            TechRow("Active Output", activeOutput?.name ?: "System Default")
                            TechRow("Active Input", activeInput?.name ?: "System Default")
                            TechRow(
                                "Audio Mode",
                                if (activeSession?.sessionType == com.example.audio.session.SessionType.VOICE_CHAT)
                                    "MODE_IN_COMMUNICATION"
                                else "MODE_NORMAL"
                            )
                            TechRow("Audio Focus Status", focusState?.label ?: "Active (Exclusive Gain)")
                            TechRow("Latency Level", performanceState?.actualLatencyLevel?.title ?: "Balanced")
                            TechRow("Buffer Configuration", "$bufferFrames frames @ $sampleRate Hz")
                            TechRow("Estimated Buffer Latency", "$estimatedBufferLatencyMs ms")
                        }
                    }

                    // Section 2: Safe Microphone Monitoring (Requirements 9 & 10)
                    item {
                        TechGroup(title = "MICROPHONE MONITORING") {
                            Text(
                                text = "Low-latency in-ear feedback loop for audio testing and voice confidence.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = IosTextSecondary
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            // Feedback Warning Banner if monitoring on speaker
                            if (monitoringFeedbackWarning != null || (activeOutput?.isBuiltIn == true && isMonitoringEnabled)) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, IosSystemRed.copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.WarningAmber,
                                            contentDescription = null,
                                            tint = IosSystemRed,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = monitoringFeedbackWarning ?: "Use headphones to avoid audio feedback.",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            ),
                                            color = IosSystemRed
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                            }

                            // Toggle Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Hearing,
                                        contentDescription = null,
                                        tint = if (isMonitoringEnabled) IosSystemBlue else IosTextSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Microphone Passthrough",
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp
                                            ),
                                            color = IosTextPrimary
                                        )
                                        Text(
                                            text = if (isMonitoringEnabled) "Monitoring active ($estimatedBufferLatencyMs ms)" else "Disabled",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                            color = if (isMonitoringEnabled) IosSystemGreen else IosTextSecondary
                                        )
                                    }
                                }

                                Switch(
                                    checked = isMonitoringEnabled,
                                    onCheckedChange = onToggleMonitoring,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = MaterialTheme.colorScheme.surface,
                                        checkedTrackColor = IosSystemGreen
                                    )
                                )
                            }

                            // Volume Slider
                            if (isMonitoringEnabled) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Monitoring Level",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                        color = IosTextSecondary
                                    )
                                    Text(
                                        text = "${(monitoringLevel * 100).toInt()}%",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp
                                        ),
                                        color = IosTextPrimary
                                    )
                                }
                                Slider(
                                    value = monitoringLevel,
                                    onValueChange = onSetMonitoringLevel,
                                    valueRange = 0f..1f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = IosSystemBlue,
                                        activeTrackColor = IosSystemBlue
                                    )
                                )
                            }
                        }
                    }

                    // Section 3: Diagnostic Route Testing (Requirements 35, 36, 37)
                    item {
                        TechGroup(title = "ROUTE DIAGNOSTIC TESTS") {
                            Text(
                                text = "Run safe on-device tests to confirm independent hardware routing.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = IosTextSecondary
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            // Test Output Button
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = androidx.compose.foundation.BorderStroke(1.dp, IosBorderLight),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                                contentDescription = null,
                                                tint = IosSystemBlue,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = "Test Output Route",
                                                style = MaterialTheme.typography.titleSmall.copy(
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 13.sp
                                                ),
                                                color = IosTextPrimary
                                            )
                                        }

                                        Button(
                                            onClick = onTestOutput,
                                            enabled = routingState?.isOutputTesting != true,
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = IosSystemBlue
                                            )
                                        ) {
                                            if (routingState?.isOutputTesting == true) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(14.dp),
                                                    strokeWidth = 2.dp,
                                                    color = MaterialTheme.colorScheme.onPrimary
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Playing (${routingState.outputTestCountdown}s)", fontSize = 12.sp)
                                            } else {
                                                Text("Play Tone (3s)", fontSize = 12.sp)
                                            }
                                        }
                                    }

                                    if (routingState?.outputTestResult != null) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = routingState.outputTestResult,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium
                                            ),
                                            color = if (routingState.outputTestResult.contains("complete")) IosSystemGreen else IosSystemBlue
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Test Microphone Button
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = androidx.compose.foundation.BorderStroke(1.dp, IosBorderLight),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Mic,
                                                contentDescription = null,
                                                tint = IosSystemBlue,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = "Test Microphone Input",
                                                style = MaterialTheme.typography.titleSmall.copy(
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 13.sp
                                                ),
                                                color = IosTextPrimary
                                            )
                                        }

                                        Button(
                                            onClick = onTestMicrophone,
                                            enabled = routingState?.isMicrophoneTesting != true,
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = IosSystemBlue
                                            )
                                        ) {
                                            if (routingState?.isMicrophoneTesting == true) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(14.dp),
                                                    strokeWidth = 2.dp,
                                                    color = MaterialTheme.colorScheme.onPrimary
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                val phaseLabel = if (routingState.microphoneTestPhase == "recording") "Rec (${routingState.microphoneTestCountdown}s)" else "Play (${routingState.microphoneTestCountdown}s)"
                                                Text(phaseLabel, fontSize = 12.sp)
                                            } else {
                                                Text("Test Mic (3s)", fontSize = 12.sp)
                                            }
                                        }
                                    }

                                    if (routingState?.isMicrophoneTesting == true) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            LinearProgressIndicator(
                                                progress = { routingState.microphoneTestLevel },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(6.dp)
                                                    .clip(RoundedCornerShape(3.dp)),
                                                color = IosSystemGreen,
                                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                                            )
                                            Text(
                                                text = "${(routingState.microphoneTestLevel * 100).toInt()}%",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                color = IosTextSecondary
                                            )
                                        }
                                    }

                                    if (routingState?.microphoneTestResult != null) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = routingState.microphoneTestResult,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium
                                            ),
                                            color = if (routingState.microphoneTestResult.contains("complete")) IosSystemGreen else IosSystemBlue
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Privacy Note: Test audio is checked locally in-memory. Zero audio is stored, uploaded, or transmitted.",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = IosTextSecondary.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }

                    // Section 4: Session Event Log (Requirements 34 & 35)
                    item {
                        TechGroup(title = "SESSION EVENT LOG") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Recent audio routing & hardware events (${eventLog.size})",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                    color = IosTextSecondary
                                )

                                OutlinedButton(
                                    onClick = onClearDiagnosticsLog,
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Clear Event Log",
                                        modifier = Modifier.size(14.dp),
                                        tint = IosTextSecondary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Clear Log", fontSize = 11.sp, color = IosTextSecondary)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            if (eventLog.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No events logged yet.",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                        color = IosTextSecondary
                                    )
                                }
                            } else {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    eventLog.take(10).forEach { event ->
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            border = androidx.compose.foundation.BorderStroke(0.5.dp, IosBorderLight),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = event.timestamp,
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = 10.sp,
                                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                                    ),
                                                    color = IosTextSecondary
                                                )

                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = when (event.category) {
                                                        "SESSION" -> IosSystemBlue.copy(alpha = 0.15f)
                                                        "HARDWARE" -> AudioEmeraldActive.copy(alpha = 0.15f)
                                                        "ROUTING" -> IosSystemBlue.copy(alpha = 0.15f)
                                                        "PERFORMANCE" -> MaterialTheme.colorScheme.primaryContainer
                                                        "FOCUS" -> MaterialTheme.colorScheme.tertiaryContainer
                                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                                    }
                                                ) {
                                                    Text(
                                                        text = event.category,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold
                                                        ),
                                                        color = when (event.category) {
                                                            "SESSION" -> IosSystemBlue
                                                            "HARDWARE" -> AudioEmeraldActive
                                                            "ROUTING" -> IosSystemBlue
                                                            else -> IosTextPrimary
                                                        }
                                                    )
                                                }

                                                Text(
                                                    text = event.message,
                                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                                    color = IosTextPrimary,
                                                    maxLines = 2,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Section 5: Subsystems & Hardware Specifics
                    item {
                        TechGroup(title = "HARDWARE & SUBSYSTEM DETAILS") {
                            TechRow("Playback Capture (API 29+)", playbackCaptureStatus)
                            TechRow("Bluetooth Audio Codec", "SBC / AAC (Controlled by Android BT stack)")
                            TechRow("Bluetooth Latency Impact", "Adds device/codec-dependent buffer delay")
                            TechRow("USB Audio Support", if (diagnostics.hasUsbAudio) "Connected & Verified" else "Not Connected")
                            TechRow("Wired Audio Support", if (diagnostics.hasWiredAudio) "Headset / Jack Connected" else "Not Connected")
                            TechRow("Audio Routing Engine", if (diagnostics.communicationDeviceSupported) "Modern API 31+ (setCommunicationDevice)" else "Legacy Audio Mode")
                            TechRow("Low Latency Hardware Flag", if (diagnostics.lowLatencyFeature) "Present" else "Absent")
                            TechRow("Pro Audio Hardware Flag", if (diagnostics.proAudioFeature) "Present" else "Absent")
                        }
                    }

                    // Section 6: Device & Platform Information
                    item {
                        TechGroup(title = "DEVICE & PLATFORM") {
                            TechRow("Manufacturer", diagnostics.manufacturer)
                            TechRow("Model", diagnostics.model)
                            TechRow("Android Version", "${diagnostics.androidVersion} (API ${diagnostics.sdkInt})")
                            TechRow("Total Output Sinks", "${diagnostics.totalOutputDevices}")
                            TechRow("Total Input Sources", "${diagnostics.totalInputDevices}")
                        }
                    }

                    // Section 7: Android System Boundaries Notice (Requirement 2)
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, IosBorderLight),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = IosSystemBlue,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "ANDROID SYSTEM BOUNDARIES",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            letterSpacing = 0.6.sp
                                        ),
                                        color = IosSystemBlue
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "• SonoRoute Sessions: Full application-level control over buffer size, sample rate, latency presets, and direct AudioTrack/AudioRecord streams.\n" +
                                           "• Communication Routing: Directly routes communication streams via AudioManager.setCommunicationDevice (API 31+) or MODE_IN_COMMUNICATION.\n" +
                                           "• System Controlled: When set to System Default, Android OS independently selects the active audio sink/source based on priorities.\n" +
                                           "• Third-Party Apps: Sandboxed applications (e.g., Spotify, YouTube) manage their own audio players. Android does not grant third-party apps arbitrary control over external processes.\n" +
                                           "• AudioPlaybackCapture: Requires API 29+ and explicit target application capture permissions or system MediaProjection consent.",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 11.sp,
                                        lineHeight = 16.sp
                                    ),
                                    color = IosTextSecondary
                                )
                            }
                        }
                    }

                    // Section 8: Expandable Advanced HAL Properties
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { showAdvanced = !showAdvanced },
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, IosBorderLight)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "ADVANCED HAL PROPERTIES",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            letterSpacing = 0.6.sp
                                        ),
                                        color = IosSystemBlue
                                    )
                                    Icon(
                                        imageVector = if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = if (showAdvanced) "Collapse" else "Expand",
                                        tint = IosSystemBlue,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                AnimatedVisibility(visible = showAdvanced) {
                                    Column(modifier = Modifier.padding(top = 10.dp)) {
                                        HorizontalDivider(color = IosBorderLight.copy(alpha = 0.6f))
                                        Spacer(modifier = Modifier.height(8.dp))

                                        TechRow("Active Communication Device", routingState?.activeCommunicationDeviceName ?: "None")
                                        TechRow("Preferred Output ID", activeOutput?.id?.toString() ?: "None")
                                        TechRow("Preferred Input ID", activeInput?.id?.toString() ?: "None")
                                        TechRow("Native HAL Sample Rate", diagnostics.sampleRateProperty?.let { "$it Hz" } ?: "Not Exposed")
                                        TechRow("Native Frames Per Buffer", diagnostics.bufferFramesProperty?.let { "$it frames" } ?: "Not Exposed")
                                        TechRow("Audio Focus Policy", "Dynamic AudioAttributes request with abandonment on release")
                                        TechRow("Playback Capture Policy", "ALLOW_CAPTURE_BY_ALL allowed")
                                        BoolRow("Bluetooth Connected", diagnostics.hasBluetoothAudio)
                                        BoolRow("USB Connected", diagnostics.hasUsbAudio)
                                        BoolRow("Wired Connected", diagnostics.hasWiredAudio)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TechGroup(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(1.dp, IosBorderLight)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 0.6.sp
                ),
                color = IosSystemBlue
            )
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = IosBorderLight.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun TechRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = IosTextSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp
            ),
            color = IosTextPrimary
        )
    }
}

@Composable
private fun BoolRow(label: String, value: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = IosTextSecondary
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = if (value) Icons.Default.CheckCircle else Icons.Default.HighlightOff,
                contentDescription = if (value) "Supported" else "Not supported",
                tint = if (value) IosSystemGreen else IosSystemRed,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = if (value) "Yes" else "No",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp
                ),
                color = if (value) IosSystemGreen else IosSystemRed
            )
        }
    }
}
