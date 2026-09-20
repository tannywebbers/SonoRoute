package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.performance.AudioFormatOption
import com.example.audio.performance.AudioPerformanceMode
import com.example.audio.performance.AudioPerformanceState
import com.example.audio.performance.BufferOption
import com.example.audio.performance.ChannelOption
import com.example.audio.performance.ConfigurationStatus
import com.example.audio.performance.LatencyLevel
import com.example.ui.theme.IosBorderLight
import com.example.ui.theme.IosSurfaceVariantLight
import com.example.ui.theme.IosSystemAmber
import com.example.ui.theme.IosSystemBlue
import com.example.ui.theme.IosSystemBlueContainer
import com.example.ui.theme.IosSystemGreen
import com.example.ui.theme.IosSystemGreenContainer
import com.example.ui.theme.IosTextPrimary
import com.example.ui.theme.IosTextSecondary

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AudioPerformanceBottomSheet(
    state: AudioPerformanceState,
    onSelectLatencyLevel: (LatencyLevel) -> Unit,
    onSelectBuffer: (BufferOption) -> Unit,
    onSelectCustomBuffer: (Int?) -> Unit,
    onSelectSampleRate: (Int) -> Unit,
    onSelectCustomSampleRate: (Int) -> Unit,
    onSelectChannels: (ChannelOption) -> Unit,
    onSelectFormat: (AudioFormatOption) -> Unit,
    onResetProfileDefaults: () -> Unit,
    onResetAllDefaults: () -> Unit,
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusManager = LocalFocusManager.current

    var isAdvancedExpanded by remember { mutableStateOf(false) }

    // Custom buffer input state
    var isCustomBufferActive by remember(state.requestedCustomBufferFrames) {
        mutableStateOf(state.requestedCustomBufferFrames != null)
    }
    var customBufferText by remember(state.requestedCustomBufferFrames) {
        mutableStateOf(state.requestedCustomBufferFrames?.toString() ?: "256")
    }
    var customBufferError by remember { mutableStateOf<String?>(null) }

    // Custom sample rate input state
    var isCustomRateActive by remember(state.customSampleRateInput) {
        mutableStateOf(state.customSampleRateInput != null)
    }
    var customRateText by remember(state.customSampleRateInput) {
        mutableStateOf(state.customSampleRateInput?.toString() ?: "48000")
    }
    var customRateError by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = Color.Black.copy(alpha = 0.45f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 36.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(IosSystemBlueContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = IosSystemBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Audio Performance",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            ),
                            color = IosTextPrimary
                        )
                        Text(
                            text = "Profile: ${state.activeProfile.title} • ${state.activeOutputDeviceName}",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = IosTextSecondary
                        )
                    }
                }

                IconButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.testTag("dismiss_perf_sheet")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = IosTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Configuration Status Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                when (state.configurationStatus) {
                                    ConfigurationStatus.APPLIED -> IosSystemGreen
                                    ConfigurationStatus.ADJUSTED_BY_DEVICE -> IosSystemAmber
                                    ConfigurationStatus.SYSTEM_CONTROLLED -> IosSystemBlue
                                    ConfigurationStatus.NOT_SUPPORTED -> Color.Red
                                    ConfigurationStatus.REQUIRES_RESTART -> IosSystemAmber
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = state.configurationStatus.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        ),
                        color = when (state.configurationStatus) {
                            ConfigurationStatus.APPLIED -> IosSystemGreen
                            ConfigurationStatus.ADJUSTED_BY_DEVICE -> IosSystemAmber
                            ConfigurationStatus.SYSTEM_CONTROLLED -> IosSystemBlue
                            ConfigurationStatus.NOT_SUPPORTED -> Color.Red
                            ConfigurationStatus.REQUIRES_RESTART -> IosSystemAmber
                        }
                    )
                }

                Text(
                    text = "HAL Engine: ${state.audioEngineName}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = IosTextSecondary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Android System Scope Limitation Note
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = IosSurfaceVariantLight,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = IosSystemBlue,
                        modifier = Modifier
                            .size(16.dp)
                            .padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Applies to SonoRoute-controlled audio sessions. Android manages third-party application buffers independently.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp, lineHeight = 16.sp),
                        color = IosTextSecondary
                    )
                }
            }

            // Warning or Device Adjustment Banner
            if (state.warningMessage != null || (state.isAdjustedForDevice && state.adjustmentReason != null)) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = IosSystemAmber.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, IosSystemAmber.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = IosSystemAmber,
                            modifier = Modifier
                                .size(16.dp)
                                .padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = state.warningMessage ?: state.adjustmentReason.orEmpty(),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = IosTextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // SECTION 1: LATENCY LEVEL (Fully Editable)
            Text(
                text = "LATENCY LEVEL",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    fontSize = 11.sp
                ),
                color = IosTextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, IosBorderLight, RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                LatencyLevel.values().forEachIndexed { index, level ->
                    val isSelected = state.requestedLatencyLevel == level
                    val isHardwareLimited = (level == LatencyLevel.VERY_LOW) && !state.supportsLowLatency

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectLatencyLevel(level)
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = level.title,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        fontSize = 14.5.sp
                                    ),
                                    color = IosTextPrimary
                                )
                                if (isHardwareLimited) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Limited by HAL",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = IosSystemAmber
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = level.description,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                color = IosTextSecondary
                            )
                        }

                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(IosSystemBlue),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }

                    if (index < LatencyLevel.values().size - 1) {
                        HorizontalDivider(color = IosBorderLight, thickness = 0.8.dp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // SECTION 2: BUFFER SIZE (Fully Editable with Presets and Custom Input)
            Text(
                text = "BUFFER SIZE",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    fontSize = 11.sp
                ),
                color = IosTextSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))

            // Requested vs Actual verification line
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Requested: ${
                        if (state.requestedCustomBufferFrames != null) "${state.requestedCustomBufferFrames} frames"
                        else state.requestedBufferOption.title
                    }",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = IosTextSecondary
                )
                Text(
                    text = "Actual: ${state.actualBufferSizeFrames} frames",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    ),
                    color = IosSystemBlue
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Presets Flow Row
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Auto Option
                val isAutoSelected = state.requestedBufferOption == BufferOption.AUTO && !isCustomBufferActive
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            isCustomBufferActive = false
                            customBufferError = null
                            onSelectCustomBuffer(null)
                            onSelectBuffer(BufferOption.AUTO)
                        },
                    color = if (isAutoSelected) IosSystemBlue else IosSurfaceVariantLight,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "Auto (${state.nativeBufferSizeFrames})",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (isAutoSelected) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize = 12.5.sp
                        ),
                        color = if (isAutoSelected) Color.White else IosTextPrimary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }

                // Supported buffer presets
                listOf(64, 128, 192, 256, 384, 512, 1024, 2048).forEach { presetFrames ->
                    val isPresetSelected = !isCustomBufferActive &&
                            (state.requestedCustomBufferFrames == presetFrames || (state.requestedBufferOption != BufferOption.AUTO && state.actualBufferSizeFrames == presetFrames))

                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                isCustomBufferActive = false
                                customBufferError = null
                                onSelectCustomBuffer(presetFrames)
                            },
                        color = if (isPresetSelected) IosSystemBlue else IosSurfaceVariantLight,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "$presetFrames",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (isPresetSelected) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize = 12.5.sp
                            ),
                            color = if (isPresetSelected) Color.White else IosTextPrimary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }

                // Custom option pill
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            isCustomBufferActive = true
                        },
                    color = if (isCustomBufferActive) IosSystemBlue else IosSurfaceVariantLight,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "Custom",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (isCustomBufferActive) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize = 12.5.sp
                        ),
                        color = if (isCustomBufferActive) Color.White else IosTextPrimary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            // Custom Buffer Input field when active
            AnimatedVisibility(visible = isCustomBufferActive) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(IosSurfaceVariantLight)
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Enter Custom Buffer Size (32 - 8192 frames):",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = IosTextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = customBufferText,
                            onValueChange = {
                                customBufferText = it.filter { char -> char.isDigit() }
                                customBufferError = null
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    focusManager.clearFocus()
                                    val frames = customBufferText.toIntOrNull()
                                    if (frames != null && frames in 32..8192) {
                                        onSelectCustomBuffer(frames)
                                    } else {
                                        customBufferError = "Must be between 32 and 8192 frames"
                                    }
                                }
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("custom_buffer_input"),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedBorderColor = IosSystemBlue,
                                unfocusedBorderColor = IosBorderLight
                            )
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                val frames = customBufferText.toIntOrNull()
                                if (frames != null && frames in 32..8192) {
                                    onSelectCustomBuffer(frames)
                                } else {
                                    customBufferError = "Must be between 32 and 8192 frames"
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = IosSystemBlue),
                            modifier = Modifier.testTag("apply_custom_buffer")
                        ) {
                            Text("Apply", fontSize = 13.sp)
                        }
                    }

                    if (customBufferError != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = customBufferError!!,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Red
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // SECTION 3: SAMPLE RATE (Fully Editable, Hardware-Validated)
            Text(
                text = "SAMPLE RATE",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    fontSize = 11.sp
                ),
                color = IosTextSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))

            // Requested vs Actual verification line
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Requested: ${if (state.requestedSampleRate == 0) "Auto" else "${state.requestedSampleRate} Hz"}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = IosTextSecondary
                )
                Text(
                    text = "Actual: ${state.actualSampleRate / 1000.0} kHz",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    ),
                    color = IosSystemBlue
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Supported Sample Rate Chips
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Auto Option
                val isAutoRate = state.requestedSampleRate == 0 && !isCustomRateActive
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            isCustomRateActive = false
                            customRateError = null
                            onSelectSampleRate(0)
                        },
                    color = if (isAutoRate) IosSystemBlue else IosSurfaceVariantLight,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "Auto (${state.nativeSampleRate / 1000.0}k)",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (isAutoRate) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize = 12.5.sp
                        ),
                        color = if (isAutoRate) Color.White else IosTextPrimary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }

                // Supported sample rates exposed by hardware
                state.supportedSampleRates.forEach { rate ->
                    val isSelected = !isCustomRateActive && state.requestedSampleRate == rate
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                isCustomRateActive = false
                                customRateError = null
                                onSelectSampleRate(rate)
                            },
                        color = if (isSelected) IosSystemBlue else IosSurfaceVariantLight,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "${rate / 1000.0} kHz",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize = 12.5.sp
                            ),
                            color = if (isSelected) Color.White else IosTextPrimary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }

                // Custom sample rate option
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            isCustomRateActive = true
                        },
                    color = if (isCustomRateActive) IosSystemBlue else IosSurfaceVariantLight,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "Custom",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (isCustomRateActive) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize = 12.5.sp
                        ),
                        color = if (isCustomRateActive) Color.White else IosTextPrimary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            // Custom Sample Rate Input field when active
            AnimatedVisibility(visible = isCustomRateActive) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(IosSurfaceVariantLight)
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Enter Custom Sample Rate (8000 - 192000 Hz):",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = IosTextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = customRateText,
                            onValueChange = {
                                customRateText = it.filter { char -> char.isDigit() }
                                customRateError = null
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    focusManager.clearFocus()
                                    val rate = customRateText.toIntOrNull()
                                    if (rate != null && rate in 8000..192000) {
                                        onSelectCustomSampleRate(rate)
                                    } else {
                                        customRateError = "Must be between 8000 and 192000 Hz"
                                    }
                                }
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("custom_rate_input"),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedBorderColor = IosSystemBlue,
                                unfocusedBorderColor = IosBorderLight
                            )
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                val rate = customRateText.toIntOrNull()
                                if (rate != null && rate in 8000..192000) {
                                    onSelectCustomSampleRate(rate)
                                } else {
                                    customRateError = "Must be between 8000 and 192000 Hz"
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = IosSystemBlue),
                            modifier = Modifier.testTag("apply_custom_rate")
                        ) {
                            Text("Apply", fontSize = 13.sp)
                        }
                    }

                    if (customRateError != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = customRateError!!,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Red
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // SECTION 4: ESTIMATED BUFFER LATENCY CARD
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, IosBorderLight, RoundedCornerShape(16.dp)),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Buffer Latency",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            ),
                            color = IosTextPrimary
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(IosSystemGreenContainer)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = String.format("≈ %.2f ms", state.estimatedBufferLatencyMs),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                ),
                                color = IosSystemGreen
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Calculated from buffer frames / sample rate (${state.actualBufferSizeFrames} frames / ${state.actualSampleRate} Hz). This represents the audio buffer latency, not the total end-to-end device/acoustic latency.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp, lineHeight = 16.sp),
                        color = IosTextSecondary
                    )

                    if (state.measuredLatencyMs != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = IosBorderLight, thickness = 0.6.dp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Measured Round-Trip Latency",
                                style = MaterialTheme.typography.bodySmall,
                                color = IosTextSecondary
                            )
                            Text(
                                text = "${state.measuredLatencyMs} ms",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = IosSystemBlue
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // SECTION 5: ADVANCED SETTINGS (Collapsible)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { isAdvancedExpanded = !isAdvancedExpanded },
                color = IosSurfaceVariantLight,
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = IosSystemBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Advanced Hardware & Stream Settings",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.5.sp
                            ),
                            color = IosTextPrimary
                        )
                    }

                    Icon(
                        imageVector = if (isAdvancedExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isAdvancedExpanded) "Collapse" else "Expand",
                        tint = IosTextSecondary
                    )
                }
            }

            AnimatedVisibility(
                visible = isAdvancedExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, IosBorderLight, RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp)
                ) {
                    // Channel Selection
                    Text(
                        text = "Channel Configuration",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = IosTextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ChannelOption.values().forEach { option ->
                            val isSelected = state.requestedChannelOption == option
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onSelectChannels(option) },
                                color = if (isSelected) IosSystemBlue else IosSurfaceVariantLight,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = option.title,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                            fontSize = 11.5.sp
                                        ),
                                        color = if (isSelected) Color.White else IosTextPrimary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = IosBorderLight, thickness = 0.6.dp)
                    Spacer(modifier = Modifier.height(14.dp))

                    // Audio Format Selection
                    Text(
                        text = "Audio Encoding Format",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = IosTextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AudioFormatOption.values().forEach { option ->
                            val isSelected = state.requestedFormatOption == option
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onSelectFormat(option) },
                                color = if (isSelected) IosSystemBlue else IosSurfaceVariantLight,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = option.title,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                            fontSize = 11.5.sp
                                        ),
                                        color = if (isSelected) Color.White else IosTextPrimary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = IosBorderLight, thickness = 0.6.dp)
                    Spacer(modifier = Modifier.height(14.dp))

                    // Audio Stability & Underruns
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Audio Stability",
                            style = MaterialTheme.typography.bodySmall,
                            color = IosTextSecondary
                        )
                        Text(
                            text = state.stabilityStatus,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = if (state.underrunCount > 0) IosSystemAmber else IosSystemGreen
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = IosBorderLight, thickness = 0.6.dp)
                    Spacer(modifier = Modifier.height(10.dp))

                    // Hardware Capabilities
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Low Latency (HAL Feature)",
                            style = MaterialTheme.typography.bodySmall,
                            color = IosTextSecondary
                        )
                        Text(
                            text = if (state.supportsLowLatency) "Available" else "Unavailable",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = if (state.supportsLowLatency) IosSystemGreen else IosTextSecondary
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Pro Audio (HAL Feature)",
                            style = MaterialTheme.typography.bodySmall,
                            color = IosTextSecondary
                        )
                        Text(
                            text = if (state.supportsProAudio) "Available" else "Unavailable",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = if (state.supportsProAudio) IosSystemGreen else IosTextSecondary
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // SECTION 6: RESET CONTROLS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TextButton(
                    onClick = onResetProfileDefaults,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = IosSystemBlue
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Reset Profile Defaults",
                        color = IosSystemBlue,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                TextButton(
                    onClick = onResetAllDefaults,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Reset All",
                        color = IosTextSecondary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
        }
    }
}
