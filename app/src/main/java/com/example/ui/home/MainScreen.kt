package com.example.ui.home

import android.os.Build
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audio.performance.AudioPerformanceState
import com.example.audio.performance.LatencyLevel
import com.example.audio.routing.RouteOperationState
import com.example.ui.MainViewModel
import com.example.ui.components.ActiveRouteCard
import com.example.ui.components.ConnectionBanner
import com.example.ui.components.DeviceSelectionBottomSheet
import com.example.ui.components.DeviceSelectorCard
import com.example.ui.components.LatencySelectorCard
import com.example.ui.components.MicrophonePermissionPrompt
import com.example.ui.components.PermissionCard
import com.example.ui.components.ProfileSelectorCard
import com.example.ui.components.ProfileSelectionBottomSheet
import com.example.ui.theme.DarkCardSurface
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.NeonCyan
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onRequestRuntimePermissions: () -> Unit
) {
    val outputDevices by viewModel.outputDevices.collectAsStateWithLifecycle()
    val inputDevices by viewModel.inputDevices.collectAsStateWithLifecycle()
    val activeOutput by viewModel.activeOutputDevice.collectAsStateWithLifecycle()
    val activeInput by viewModel.activeInputDevice.collectAsStateWithLifecycle()
    val hasMicPermission by viewModel.hasMicPermission.collectAsStateWithLifecycle()
    val hasBluetoothPermission by viewModel.hasBluetoothPermission.collectAsStateWithLifecycle()
    val bannerMessage by viewModel.bannerMessage.collectAsStateWithLifecycle()
    val routeState by viewModel.routeState.collectAsStateWithLifecycle()
    val routingState by viewModel.routingState.collectAsStateWithLifecycle()
    val currentProfile by viewModel.currentProfile.collectAsStateWithLifecycle()
    val performanceState by viewModel.performanceState.collectAsStateWithLifecycle()
    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()

    var showOutputSheet by remember { mutableStateOf(false) }
    var showInputSheet by remember { mutableStateOf(false) }
    var showProfileSheet by remember { mutableStateOf(false) }
    var showLatencySheet by remember { mutableStateOf(false) }

    LaunchedEffect(bannerMessage) {
        val message = bannerMessage
        if (message != null) {
            delay(4000)
            if (viewModel.bannerMessage.value == message) {
                viewModel.dismissBanner()
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "SonoRoute",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = "Audio Router & Performance",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            ConnectionBanner(
                message = bannerMessage,
                onDismiss = viewModel::dismissBanner
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    ActiveRouteCard(
                        activeOutput = activeOutput,
                        activeInput = activeInput,
                        isRouteForced = activeOutput != null || activeInput != null
                    )
                }

                if (!hasMicPermission) {
                    item {
                        MicrophonePermissionPrompt(
                            onRequestPermission = onRequestRuntimePermissions
                        )
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasBluetoothPermission) {
                    item {
                        PermissionCard(
                            title = "Bluetooth Permission Required",
                            description = "Grant Bluetooth access to detect and route audio to Bluetooth headphones, speakers and headsets.",
                            buttonText = "Grant Bluetooth Access",
                            icon = Icons.Default.Bluetooth,
                            onRequestPermission = onRequestRuntimePermissions
                        )
                    }
                }

                item {
                    DeviceSelectorCard(
                        device = activeOutput,
                        isInput = false,
                        isRoutingInProgress = routeState is RouteOperationState.InProgress,
                        isRoutingFailed = routeState is RouteOperationState.Error,
                        onClick = { showOutputSheet = true }
                    )
                }

                item {
                    DeviceSelectorCard(
                        device = activeInput,
                        isInput = true,
                        isRoutingInProgress = routeState is RouteOperationState.InProgress,
                        isRoutingFailed = routeState is RouteOperationState.Error,
                        onClick = { showInputSheet = true }
                    )
                }

                item {
                    ProfileSelectorCard(
                        currentProfile = currentProfile,
                        sessionState = activeSession.lifecycleState,
                        onClick = { showProfileSheet = true }
                    )
                }

                item {
                    LatencySelectorCard(
                        performanceState = performanceState,
                        onClick = { showLatencySheet = true }
                    )
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.testOutputRoute(targetDevice = null) { } },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("test_output_button"),
                            enabled = !routingState.isOutputTesting,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (routingState.isOutputTesting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(text = "Test Output")
                        }
                        OutlinedButton(
                            onClick = {
                                viewModel.testMicrophoneRoute({ }, { _, _ -> })
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("test_input_button"),
                            enabled = !routingState.isMicrophoneTesting,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (routingState.isMicrophoneTesting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(text = "Test Mic")
                        }
                    }
                }

                val latestTestResult = routingState.microphoneTestResult ?: routingState.outputTestResult
                if (latestTestResult != null) {
                    item {
                        Text(
                            text = latestTestResult,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(12.dp)) }
            }
        }
    }

    if (showOutputSheet) {
        DeviceSelectionBottomSheet(
            title = "Select Output Device",
            isInput = false,
            devices = outputDevices,
            currentDevice = activeOutput,
            routeState = routeState,
            onSelectDevice = viewModel::selectOutput,
            onResetToDefault = viewModel::resetOutputToSystemDefault,
            onDismissError = viewModel::dismissRouteError,
            onDismissRequest = { showOutputSheet = false }
        )
    }

    if (showInputSheet) {
        DeviceSelectionBottomSheet(
            title = "Select Input Device",
            isInput = true,
            devices = inputDevices,
            currentDevice = activeInput,
            routeState = routeState,
            onSelectDevice = viewModel::selectInput,
            onResetToDefault = viewModel::resetInputToSystemDefault,
            onDismissError = viewModel::dismissRouteError,
            onDismissRequest = { showInputSheet = false }
        )
    }

    if (showProfileSheet) {
        ProfileSelectionBottomSheet(
            currentProfile = currentProfile,
            activeSession = activeSession,
            onStartSession = { viewModel.startSession() },
            onPauseSession = viewModel::pauseSession,
            onResumeSession = viewModel::resumeSession,
            onStopSession = viewModel::stopSession,
            onSelectProfile = viewModel::selectProfile,
            onDismissRequest = { showProfileSheet = false }
        )
    }

    if (showLatencySheet) {
        LatencySelectionSheet(
            performanceState = performanceState,
            onSelectLatency = viewModel::setLatencyLevel,
            onReset = {
                viewModel.resetProfileToDefaults()
                viewModel.resetPerformanceDefaults()
            },
            onDismissRequest = { showLatencySheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LatencySelectionSheet(
    performanceState: AudioPerformanceState,
    onSelectLatency: (LatencyLevel) -> Unit,
    onReset: () -> Unit,
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = DarkSurface,
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(40.dp)
                    .height(4.dp),
                shape = RoundedCornerShape(2.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            ) {}
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .testTag("latency_selection_sheet")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Latency & Performance",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                OutlinedButton(
                    onClick = {
                        onReset()
                        onDismissRequest()
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("reset_latency_button")
                ) {
                    Text(text = "Reset", style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "${performanceState.effectiveSampleRate} Hz • ${performanceState.effectiveBufferFrames} frames (~${String.format("%.1f", performanceState.calculatedLatencyMs)} ms)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            LatencyLevel.values().forEach { level ->
                val isSelected = level == performanceState.latencyLevel
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .border(
                            1.dp,
                            if (isSelected) NeonCyan.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                            RoundedCornerShape(14.dp)
                        )
                        .clickable { onSelectLatency(level) },
                    color = if (isSelected) NeonCyan.copy(alpha = 0.08f) else DarkCardSurface,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color = if (isSelected) NeonCyan.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Spacer(modifier = Modifier.size(40.dp))
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = level.title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = level.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (isSelected) {
                            Surface(
                                shape = CircleShape,
                                color = NeonCyan,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = DarkSurface,
                                    modifier = Modifier.padding(4.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}