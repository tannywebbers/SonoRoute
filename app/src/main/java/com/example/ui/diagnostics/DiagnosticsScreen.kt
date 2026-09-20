package com.example.ui.diagnostics

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HighlightOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.diagnostics.AudioDiagnostics
import com.example.ui.theme.AudioCyanPrimary
import com.example.ui.theme.AudioEmeraldActive
import com.example.ui.theme.AudioRoseError

@Composable
fun DiagnosticsScreen(
    diagnostics: AudioDiagnostics?,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("diagnostics_screen_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Analytics,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Audio Hardware Diagnostics",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Direct Android HAL and AudioManager queries",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Button(
                    onClick = onRefresh,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Refresh", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        if (diagnostics != null) {
            // Section 1: Device & OS
            item(key = "sec_device") {
                DiagnosticGroupCard(title = "DEVICE & PLATFORM") {
                    DiagRow("Manufacturer", diagnostics.manufacturer)
                    DiagRow("Model", diagnostics.model)
                    DiagRow("Android Version", "${diagnostics.androidVersion} (API ${diagnostics.sdkInt})")
                    DiagRow(
                        "Communication Device API",
                        if (diagnostics.communicationDeviceSupported) "Supported (Android 12+)" else "Legacy Mode"
                    )
                }
            }

            // Section 2: Hardware Capabilities
            item(key = "sec_caps") {
                DiagnosticGroupCard(title = "AUDIO SUBSYSTEM CAPABILITIES") {
                    FeatureRow("FEATURE_AUDIO_LOW_LATENCY", diagnostics.lowLatencyFeature)
                    FeatureRow("FEATURE_AUDIO_PRO", diagnostics.proAudioFeature)
                    FeatureRow("FEATURE_AUDIO_OUTPUT", diagnostics.outputFeature)
                    FeatureRow("FEATURE_MICROPHONE", diagnostics.microphoneFeature)
                }
            }

            // Section 3: Native Hardware Audio Configuration
            item(key = "sec_native") {
                DiagnosticGroupCard(title = "NATIVE HARDWARE BUFFERS") {
                    DiagRow(
                        "Hardware Sample Rate",
                        diagnostics.sampleRateProperty?.let { "$it Hz" } ?: "Not Exposed by HAL"
                    )
                    DiagRow(
                        "Frames Per Buffer",
                        diagnostics.bufferFramesProperty?.let { "$it frames" } ?: "Not Exposed by HAL"
                    )
                    DiagRow("Current Audio Mode", diagnostics.currentAudioMode)
                    DiagRow("Speakerphone Forced On", if (diagnostics.isSpeakerphoneOn) "Yes" else "No")
                    DiagRow("Bluetooth SCO Active", if (diagnostics.isBluetoothScoOn) "Yes" else "No")
                    if (diagnostics.communicationDeviceName != null) {
                        DiagRow("Active Comm Device", diagnostics.communicationDeviceName)
                    }
                }
            }

            // Section 4: Physical Connectivity
            item(key = "sec_connectivity") {
                DiagnosticGroupCard(title = "HARDWARE DETECTIONS") {
                    DiagRow("Total Output Sinks", "${diagnostics.totalOutputDevices}")
                    DiagRow("Total Input Sources", "${diagnostics.totalInputDevices}")
                    FeatureRow("Bluetooth Audio Devices Available", diagnostics.hasBluetoothAudio)
                    FeatureRow("USB Audio Hardware Connected", diagnostics.hasUsbAudio)
                    FeatureRow("Wired Headset / Headphones Connected", diagnostics.hasWiredAudio)
                }
            }
        }

        item(key = "bottom_spacer") {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun DiagnosticGroupCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = AudioCyanPrimary
            )
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun DiagRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun FeatureRow(label: String, isAvailable: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isAvailable) Icons.Default.CheckCircle else Icons.Default.HighlightOff,
                contentDescription = null,
                tint = if (isAvailable) AudioEmeraldActive else AudioRoseError.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (isAvailable) "Available" else "Not Present",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = if (isAvailable) AudioEmeraldActive else AudioRoseError.copy(alpha = 0.8f)
            )
        }
    }
}
