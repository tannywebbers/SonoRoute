package com.example

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.MainViewModel
import com.example.ui.home.MainScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Configure dark status bar with light icons and transparent navigation bar
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
            navigationBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        // Ensure status bar icons (clock, battery, Wi-Fi, cell) are light/white for contrast
        insetsController.isAppearanceLightStatusBars = false
        // Ensure navigation bar icons are dark against light background
        insetsController.isAppearanceLightNavigationBars = true

        setContent {
            MyApplicationTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    // Dedicated dark status bar strip: exactly matches WindowInsets.statusBars
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsTopHeight(WindowInsets.statusBars)
                            .background(Color(0xFF000000))
                    )
                    // Light SonoRoute application area
                    Box(modifier = Modifier.weight(1f)) {
                        SonoRouteApp(viewModel = viewModel)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Automatically sync hardware devices when application returns to foreground
        viewModel.refresh()
    }
}

@Composable
fun SonoRouteApp(viewModel: MainViewModel) {
    val outputDevices by viewModel.outputDevices.collectAsStateWithLifecycle()
    val inputDevices by viewModel.inputDevices.collectAsStateWithLifecycle()
    val activeOutput by viewModel.activeOutputDevice.collectAsStateWithLifecycle()
    val activeInput by viewModel.activeInputDevice.collectAsStateWithLifecycle()
    val diagnostics by viewModel.diagnostics.collectAsStateWithLifecycle()
    val bannerMessage by viewModel.bannerMessage.collectAsStateWithLifecycle()
    val hasMicPermission by viewModel.hasMicPermission.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val currentProfile by viewModel.currentProfile.collectAsStateWithLifecycle()
    val routeState by viewModel.routeState.collectAsStateWithLifecycle()
    val routingState by viewModel.routingState.collectAsStateWithLifecycle()
    val performanceState by viewModel.performanceState.collectAsStateWithLifecycle()
    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
    val eventLog by viewModel.eventLog.collectAsStateWithLifecycle()
    val isMonitoringEnabled by viewModel.isMonitoringEnabled.collectAsStateWithLifecycle()
    val monitoringLevel by viewModel.monitoringLevel.collectAsStateWithLifecycle()
    val monitoringFeedbackWarning by viewModel.monitoringFeedbackWarning.collectAsStateWithLifecycle()
    val focusState by viewModel.focusState.collectAsStateWithLifecycle()
    val playbackCaptureStatus = viewModel.playbackCaptureStatus

    // Contextual permission launcher for microphone detection
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.onPermissionResult(Manifest.permission.RECORD_AUDIO, isGranted)
    }

    MainScreen(
        outputDevices = outputDevices,
        inputDevices = inputDevices,
        activeOutput = activeOutput,
        activeInput = activeInput,
        diagnostics = diagnostics,
        currentProfile = currentProfile,
        performanceState = performanceState,
        isRefreshing = isRefreshing,
        bannerMessage = bannerMessage,
        hasMicPermission = hasMicPermission,
        routeState = routeState,
        routingState = routingState,
        activeSession = activeSession,
        eventLog = eventLog,
        isMonitoringEnabled = isMonitoringEnabled,
        monitoringLevel = monitoringLevel,
        monitoringFeedbackWarning = monitoringFeedbackWarning,
        focusState = focusState,
        playbackCaptureStatus = playbackCaptureStatus,
        onPullToRefresh = { viewModel.onPullToRefresh() },
        onSelectOutput = { device -> viewModel.selectOutput(device) },
        onSelectInput = { device -> viewModel.selectInput(device) },
        onResetOutputToDefault = { viewModel.resetOutputToSystemDefault() },
        onResetInputToDefault = { viewModel.resetInputToSystemDefault() },
        onSelectProfile = { profile -> viewModel.selectProfile(profile) },
        onSelectLatencyLevel = { level -> viewModel.setLatencyLevel(level) },
        onSelectPerformanceMode = { mode -> viewModel.setPerformanceMode(mode) },
        onSelectBufferOption = { option -> viewModel.setBufferOption(option) },
        onSelectCustomBuffer = { frames -> viewModel.setCustomBufferFrames(frames) },
        onSelectSampleRate = { rate -> viewModel.setSampleRatePreference(rate) },
        onSelectCustomSampleRate = { rate -> viewModel.setCustomSampleRate(rate) },
        onSelectChannels = { channels -> viewModel.setChannelOption(channels) },
        onSelectFormat = { format -> viewModel.setFormatOption(format) },
        onResetProfileDefaults = { viewModel.resetProfileToDefaults() },
        onResetPerformanceDefaults = { viewModel.resetPerformanceDefaults() },
        onTestOutput = { viewModel.testOutputRoute() },
        onTestMicrophone = { viewModel.testMicrophoneRoute({ /* live level captured in state */ }, { _, _ -> }) },
        onStartSession = { viewModel.startSession() },
        onPauseSession = { viewModel.pauseSession() },
        onResumeSession = { viewModel.resumeSession() },
        onStopSession = { viewModel.stopSession() },
        onIncreaseBuffer = { viewModel.increaseBuffer() },
        onToggleMonitoring = { enabled, overrideWarning ->
            if (enabled && !hasMicPermission) {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            } else {
                viewModel.setMicrophoneMonitoring(enabled, overrideWarning)
            }
        },
        onSetMonitoringLevel = { level -> viewModel.setMonitoringLevel(level) },
        onClearDiagnosticsLog = { viewModel.clearDiagnosticsLog() },
        onDismissRouteError = { viewModel.dismissRouteError() },
        onDismissBanner = { viewModel.dismissBanner() },
        onRequestMicPermission = {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    )
}


