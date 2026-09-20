package com.example.ui.home

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.diagnostics.AudioDiagnostics
import com.example.audio.model.AudioDeviceModel
import com.example.ui.components.ActiveRouteCard
import com.example.ui.components.AudioDeviceCard
import com.example.ui.components.ConnectionBanner
import com.example.ui.components.PermissionCard
import com.example.ui.theme.AudioCyanPrimary
import com.example.ui.theme.AudioEmeraldActive
import com.example.ui.theme.AudioVioletSecondary

@Composable
fun HomeScreen(
    outputDevices: List<AudioDeviceModel>,
    inputDevices: List<AudioDeviceModel>,
    activeOutput: AudioDeviceModel?,
    activeInput: AudioDeviceModel?,
    diagnostics: AudioDiagnostics?,
    bannerMessage: String?,
    hasMicPermission: Boolean,
    onDismissBanner: () -> Unit,
    onRequestMicPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("home_screen_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Dynamic connection banner
        if (bannerMessage != null) {
            item(key = "banner") {
                ConnectionBanner(
                    message = bannerMessage,
                    onDismiss = onDismissBanner
                )
            }
        }

        // Hero Active Route Card
        item(key = "active_route") {
            ActiveRouteCard(
                activeOutput = activeOutput,
                activeInput = activeInput
            )
        }

        // Audio Profile & Latency Status Overview Card
        item(key = "status_overview") {
            ProfileSummaryCard(diagnostics = diagnostics)
        }

        // Mic permission prompt if needed
        if (!hasMicPermission) {
            item(key = "mic_permission") {
                PermissionCard(
                    title = "Microphone Access Required",
                    description = "Android requires RECORD_AUDIO permission to detect and route hardware audio input devices (built-in mic, headset mics, USB mics).",
                    buttonText = "Grant Microphone Permission",
                    onRequestPermission = onRequestMicPermission
                )
            }
        }

        // Connected Devices Quick Section: Output
        item(key = "outputs_header") {
            SectionHeader(
                title = "OUTPUT DEVICES",
                count = outputDevices.size,
                icon = Icons.Default.VolumeUp
            )
        }

        if (outputDevices.isEmpty()) {
            item(key = "empty_outputs") {
                EmptyStateCard(message = "No output devices detected by Android framework.")
            }
        } else {
            items(outputDevices, key = { "out_${it.id}" }) { device ->
                AudioDeviceCard(
                    device = device,
                    isActive = device.id == activeOutput?.id
                )
            }
        }

        // Connected Devices Quick Section: Input
        item(key = "inputs_header") {
            SectionHeader(
                title = "INPUT DEVICES (MICROPHONES)",
                count = inputDevices.size,
                icon = Icons.Default.Mic
            )
        }

        if (inputDevices.isEmpty()) {
            item(key = "empty_inputs") {
                EmptyStateCard(
                    message = if (!hasMicPermission) {
                        "Microphone permission is required to discover input devices."
                    } else {
                        "No audio input devices exposed by Android hardware."
                    }
                )
            }
        } else {
            items(inputDevices, key = { "in_${it.id}" }) { device ->
                AudioDeviceCard(
                    device = device,
                    isActive = device.id == activeInput?.id
                )
            }
        }

        item(key = "bottom_spacer") {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ProfileSummaryCard(diagnostics: AudioDiagnostics?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("profile_summary_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profile
            Column {
                Text(
                    text = "PROFILE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Hearing,
                        contentDescription = null,
                        tint = AudioVioletSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Standard Audio",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Latency / Native Sample Rate
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "HARDWARE ENGINE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = if (diagnostics?.lowLatencyFeature == true) AudioEmeraldActive else AudioCyanPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (diagnostics?.lowLatencyFeature == true) "Low Latency" else "Standard Latency",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                text = "$count DETECTED",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyStateCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
