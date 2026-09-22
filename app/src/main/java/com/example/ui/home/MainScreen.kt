package com.example.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.diagnostics.AudioDiagnostics
import com.example.audio.model.AudioDeviceModel
import com.example.audio.performance.AudioFormatOption
import com.example.audio.performance.AudioPerformanceMode
import com.example.audio.performance.AudioPerformanceState
import com.example.audio.performance.BufferOption
import com.example.audio.performance.ChannelOption
import com.example.audio.performance.LatencyLevel
import com.example.audio.routing.AudioRoutingState
import com.example.audio.routing.RouteOperationState
import com.example.audio.session.SessionLifecycleState
import com.example.ui.AudioProfile
import com.example.ui.components.AudioPerformanceBottomSheet
import com.example.ui.components.ConnectionBanner
import com.example.ui.components.ControlPanelBottomSheet
import com.example.ui.components.DeviceSelectionBottomSheet
import com.example.ui.components.DeviceSelectorCard
import com.example.ui.components.DiagnosticsEntryCard
import com.example.ui.components.DiagnosticsSheet
import com.example.ui.components.LatencySelectorCard
import com.example.ui.components.MicrophonePermissionPrompt
import com.example.ui.components.ProfileSelectionBottomSheet
import com.example.ui.components.ProfileSelectorCard
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
fun MainScreen(
    outputDevices: List<AudioDeviceModel>,
    inputDevices: List<AudioDeviceModel>,
    activeOutput: AudioDeviceModel?,
    activeInput: AudioDeviceModel?,
    diagnostics: AudioDiagnostics?,
    currentProfile: AudioProfile,
    performanceState: AudioPerformanceState,
    isRefreshing: Boolean,
    bannerMessage: String?,
    hasMicPermission: Boolean,
    routeState: RouteOperationState,
    routingState: AudioRoutingState? = null,
    activeSession: com.example.audio.session.AudioSession? = null,
    eventLog: List<com.example.audio.session.SessionEvent> = emptyList(),
    isMonitoringEnabled: Boolean = false,
    monitoringLevel: Float = 0.8f,
    monitoringFeedbackWarning: String? = null,
    focusState: com.example.audio.focus.FocusState? = null,
    playbackCaptureStatus: String = "Supported (Android 10+ with target app permission)",
    onPullToRefresh: () -> Unit,
    onSelectOutput: (AudioDeviceModel) -> Unit,
    onSelectInput: (AudioDeviceModel) -> Unit,
    onResetOutputToDefault: () -> Unit,
    onResetInputToDefault: () -> Unit,
    onSelectProfile: (AudioProfile) -> Unit,
    onSelectLatencyLevel: (LatencyLevel) -> Unit,
    onSelectPerformanceMode: (AudioPerformanceMode) -> Unit,
    onSelectBufferOption: (BufferOption) -> Unit,
    onSelectCustomBuffer: (Int?) -> Unit,
    onSelectSampleRate: (Int) -> Unit,
    onSelectCustomSampleRate: (Int) -> Unit,
    onSelectChannels: (ChannelOption) -> Unit,
    onSelectFormat: (AudioFormatOption) -> Unit,
    onResetProfileDefaults: () -> Unit,
    onResetPerformanceDefaults: () -> Unit,
    onTestOutput: () -> Unit = {},
    onTestMicrophone: () -> Unit = {},
    onStartSession: () -> Unit = {},
    onPauseSession: () -> Unit = {},
    onResumeSession: () -> Unit = {},
    onStopSession: () -> Unit = {},
    onIncreaseBuffer: () -> Unit = {},
    onToggleMonitoring: (Boolean, Boolean) -> Unit = { _, _ -> },
    onSetMonitoringLevel: (Float) -> Unit = {},
    onClearDiagnosticsLog: () -> Unit = {},
    onDismissRouteError: () -> Unit,
    onDismissBanner: () -> Unit,
    onRequestMicPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showOutputSheet by rememberSaveable { mutableStateOf(false) }
    var showInputSheet by rememberSaveable { mutableStateOf(false) }
    var showProfileSheet by rememberSaveable { mutableStateOf(false) }
    var showLatencySheet by rememberSaveable { mutableStateOf(false) }
    var showDiagnosticsSheet by rememberSaveable { mutableStateOf(false) }
    var showControlPanelSheet by rememberSaveable { mutableStateOf(false) }
    var showFeedbackDialog by remember { mutableStateOf(false) }

    val isRoutingOutput = routeState is RouteOperationState.InProgress &&
            outputDevices.any { it.name == routeState.targetDeviceName }
    val isRoutingInput = routeState is RouteOperationState.InProgress &&
            inputDevices.any { it.name == routeState.targetDeviceName }
    val isRoutingOutputFailed = routeState is RouteOperationState.Error &&
            (outputDevices.any { it.name == routeState.deviceName } || activeOutput?.name == routeState.deviceName)
    val isRoutingInputFailed = routeState is RouteOperationState.Error &&
            (inputDevices.any { it.name == routeState.deviceName } || activeInput?.name == routeState.deviceName)

    if (showFeedbackDialog) {
        AlertDialog(
            onDismissRequest = { showFeedbackDialog = false },
            title = {
                Text("Audio Feedback Warning", fontWeight = FontWeight.Bold)
            },
            text = {
                Text("Enabling microphone monitoring while outputting to phone speakers may produce immediate acoustic feedback (loud howling). Use headphones for safe monitoring, or confirm to proceed.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showFeedbackDialog = false
                        onToggleMonitoring(true, true)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IosSystemAmber)
                ) {
                    Text("Enable Anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFeedbackDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("main_screen"),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "SonoRoute",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 19.sp
                            ),
                            color = IosTextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        // Subtle status indicator
                        val totalConnected = outputDevices.size + inputDevices.size
                        HeaderStatusPill(count = totalConnected)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                actions = {
                    IconButton(
                        onClick = { showControlPanelSheet = true },
                        modifier = Modifier.testTag("top_bar_control_panel_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Control Panel",
                            tint = IosSystemBlue,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    IconButton(
                        onClick = { showDiagnosticsSheet = true },
                        modifier = Modifier.testTag("top_bar_diagnostics_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Audio Diagnostics",
                            tint = IosSystemBlue,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onPullToRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("pull_to_refresh_box")
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("main_content_list"),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Connection notification banner if device connected/disconnected
                if (bannerMessage != null) {
                    item(key = "banner") {
                        ConnectionBanner(
                            message = bannerMessage,
                            onDismiss = onDismissBanner
                        )
                    }
                }

                // Buffer Instability Recovery Banner
                if (activeSession?.isBufferUnstable == true) {
                    item(key = "instability_banner") {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .testTag("buffer_instability_banner"),
                            shape = RoundedCornerShape(16.dp),
                            color = IosSystemAmber.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, IosSystemAmber.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Audio Buffer Underrun Detected",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        ),
                                        color = IosTextPrimary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${activeSession.outputUnderruns} glitch events recorded. Increase buffer size to stabilize audio playback.",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                        color = IosTextSecondary
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Button(
                                    onClick = onIncreaseBuffer,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = IosSystemAmber),
                                    modifier = Modifier.testTag("fix_buffer_button")
                                ) {
                                    Text("Fix Buffer", fontSize = 12.sp, color = Color.White)
                                }
                            }
                        }
                    }
                }

                // CONTROL PANEL QUICK ACCESS CARD
                item(key = "control_panel_card") {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { showControlPanelSheet = true }
                            .testTag("control_panel_quick_card"),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, IosBorderLight)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(IosSystemBlue.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Tune,
                                            contentDescription = null,
                                            tint = IosSystemBlue,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Control Panel",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = IosTextPrimary
                                        )
                                        Text(
                                            text = "Hardware Routing & Live Tests",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = IosTextSecondary
                                        )
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (routingState?.outputRouteVerified == true && routingState.inputRouteVerified) IosSystemGreen.copy(alpha = 0.15f) else IosSystemAmber.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = if (routingState?.outputRouteVerified == true && routingState.inputRouteVerified) "Verified" else "Routing Active",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
                                        color = if (routingState?.outputRouteVerified == true && routingState.inputRouteVerified) IosSystemGreen else IosSystemAmber,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = IosBorderLight)
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Out: ${routingState?.actualOutputDevice?.name ?: activeOutput?.name ?: "Default"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = IosTextPrimary,
                                    maxLines = 1
                                )
                                Text(
                                    text = "Mic: ${routingState?.actualInputDevice?.name ?: activeInput?.name ?: "Default"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = IosTextPrimary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                // 1. OUTPUT SECTION
                item(key = "output_section") {
                    SectionBlock(title = "OUTPUT") {
                        DeviceSelectorCard(
                            device = activeOutput,
                            isInput = false,
                            isRoutingInProgress = isRoutingOutput,
                            isRoutingFailed = isRoutingOutputFailed,
                            onClick = { showOutputSheet = true }
                        )
                    }
                }

                // 2. INPUT SECTION
                item(key = "input_section") {
                    SectionBlock(title = "INPUT") {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (!hasMicPermission) {
                                MicrophonePermissionPrompt(
                                    onRequestPermission = onRequestMicPermission
                                )
                            }
                            DeviceSelectorCard(
                                device = activeInput,
                                isInput = true,
                                isRoutingInProgress = isRoutingInput,
                                isRoutingFailed = isRoutingInputFailed,
                                onClick = { showInputSheet = true }
                            )
                        }
                    }
                }

                // 3. AUDIO PROFILE SECTION
                item(key = "profile_section") {
                    SectionBlock(title = "AUDIO PROFILE") {
                        ProfileSelectorCard(
                            currentProfile = currentProfile,
                            sessionState = activeSession?.lifecycleState ?: SessionLifecycleState.IDLE,
                            onClick = { showProfileSheet = true }
                        )
                    }
                }

                // 4. LATENCY SECTION
                item(key = "latency_section") {
                    SectionBlock(title = "LATENCY") {
                        LatencySelectorCard(
                            performanceState = performanceState,
                            onClick = { showLatencySheet = true }
                        )
                    }
                }

                // 5. DIAGNOSTICS ENTRY
                item(key = "diagnostics_entry") {
                    SectionBlock(title = "DIAGNOSTICS") {
                        DiagnosticsEntryCard(
                            onClick = { showDiagnosticsSheet = true }
                        )
                    }
                }

                item(key = "bottom_space") {
                    Spacer(modifier = Modifier.height(28.dp))
                }
            }
        }
    }

    // Modal Bottom Sheets for interactive selection
    if (showOutputSheet) {
        DeviceSelectionBottomSheet(
            title = "Select Output",
            isInput = false,
            devices = outputDevices,
            currentDevice = activeOutput,
            routeState = routeState,
            onSelectDevice = { device ->
                onSelectOutput(device)
                showOutputSheet = false
            },
            onResetToDefault = {
                onResetOutputToDefault()
                showOutputSheet = false
            },
            onDismissError = onDismissRouteError,
            onDismissRequest = { showOutputSheet = false }
        )
    }

    if (showInputSheet) {
        DeviceSelectionBottomSheet(
            title = "Select Microphone",
            isInput = true,
            devices = inputDevices,
            currentDevice = activeInput,
            routeState = routeState,
            onSelectDevice = { device ->
                onSelectInput(device)
                showInputSheet = false
            },
            onResetToDefault = {
                onResetInputToDefault()
                showInputSheet = false
            },
            onDismissError = onDismissRouteError,
            onDismissRequest = { showInputSheet = false }
        )
    }

    if (showProfileSheet) {
        ProfileSelectionBottomSheet(
            currentProfile = currentProfile,
            activeSession = activeSession,
            onStartSession = onStartSession,
            onPauseSession = onPauseSession,
            onResumeSession = onResumeSession,
            onStopSession = onStopSession,
            onSelectProfile = onSelectProfile,
            onDismissRequest = { showProfileSheet = false }
        )
    }

    if (showLatencySheet) {
        AudioPerformanceBottomSheet(
            state = performanceState,
            onSelectLatencyLevel = onSelectLatencyLevel,
            onSelectBuffer = onSelectBufferOption,
            onSelectCustomBuffer = onSelectCustomBuffer,
            onSelectSampleRate = onSelectSampleRate,
            onSelectCustomSampleRate = onSelectCustomSampleRate,
            onSelectChannels = onSelectChannels,
            onSelectFormat = onSelectFormat,
            onResetProfileDefaults = onResetProfileDefaults,
            onResetAllDefaults = onResetPerformanceDefaults,
            onDismissRequest = { showLatencySheet = false }
        )
    }

    if (showDiagnosticsSheet) {
        DiagnosticsSheet(
            diagnostics = diagnostics,
            activeOutput = activeOutput,
            activeInput = activeInput,
            performanceState = performanceState,
            routingState = routingState,
            activeSession = activeSession,
            eventLog = eventLog,
            isMonitoringEnabled = isMonitoringEnabled,
            monitoringLevel = monitoringLevel,
            monitoringFeedbackWarning = monitoringFeedbackWarning,
            focusState = focusState,
            playbackCaptureStatus = playbackCaptureStatus,
            onTestOutput = onTestOutput,
            onTestMicrophone = onTestMicrophone,
            onToggleMonitoring = { enabled ->
                val isSpeaker = activeOutput?.let {
                    it.typeLabel.contains("Speaker", ignoreCase = true) ||
                    it.name.contains("Speaker", ignoreCase = true) ||
                    it.type == 2 // AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                } ?: false

                if (enabled && isSpeaker) {
                    showFeedbackDialog = true
                } else {
                    onToggleMonitoring(enabled, false)
                }
            },
            onSetMonitoringLevel = onSetMonitoringLevel,
            onClearDiagnosticsLog = onClearDiagnosticsLog,
            onDismissRequest = { showDiagnosticsSheet = false }
        )
    }

    if (showControlPanelSheet) {
        ControlPanelBottomSheet(
            onDismiss = { showControlPanelSheet = false },
            routingState = routingState,
            activeSession = activeSession,
            outputDevices = outputDevices,
            inputDevices = inputDevices,
            isMonitoringEnabled = isMonitoringEnabled,
            monitoringLevel = monitoringLevel,
            monitoringFeedbackWarning = monitoringFeedbackWarning,
            onSelectOutput = onSelectOutput,
            onSelectInput = onSelectInput,
            onResetOutputToDefault = onResetOutputToDefault,
            onResetInputToDefault = onResetInputToDefault,
            onToggleMonitoring = onToggleMonitoring,
            onSetMonitoringLevel = onSetMonitoringLevel,
            onTestOutput = onTestOutput,
            onTestMicrophone = onTestMicrophone,
            onStartSession = onStartSession,
            onPauseSession = onPauseSession,
            onResumeSession = onResumeSession,
            onStopSession = onStopSession
        )
    }
}

@Composable
private fun SectionBlock(
    title: String,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                letterSpacing = 0.8.sp
            ),
            color = IosTextSecondary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        content()
    }
}

@Composable
private fun HeaderStatusPill(count: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(IosSystemGreenContainer)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(IosSystemGreen)
        )
        Text(
            text = if (count > 0) "$count Devices Ready" else "Ready",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 10.sp
            ),
            color = IosSystemGreen
        )
    }
}
