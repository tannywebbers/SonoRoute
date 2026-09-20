package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Usb
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.model.AudioDeviceModel
import com.example.audio.model.DeviceCategory
import com.example.ui.theme.AudioAmberWarning
import com.example.ui.theme.AudioCyanPrimary
import com.example.ui.theme.AudioEmeraldActive
import com.example.ui.theme.AudioVioletSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AudioDeviceCard(
    device: AudioDeviceModel,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val borderColor = when {
        isActive -> AudioEmeraldActive
        device.isSelected -> AudioCyanPrimary
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    }

    val cardColor = if (isActive) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("device_card_${device.id}")
            .border(
                width = if (isActive || device.isSelected) 1.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isActive) 3.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Device Category Icon
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isActive -> AudioEmeraldActive.copy(alpha = 0.18f)
                                device.isBluetooth -> AudioVioletSecondary.copy(alpha = 0.18f)
                                device.isUsb -> AudioCyanPrimary.copy(alpha = 0.18f)
                                else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = resolveDeviceIcon(device),
                        contentDescription = device.name,
                        tint = when {
                            isActive -> AudioEmeraldActive
                            device.isBluetooth -> AudioVioletSecondary
                            device.isUsb -> AudioCyanPrimary
                            else -> MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = device.typeLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Status Pill
                if (isActive || device.isSelected) {
                    ActivePill(label = "Active")
                } else {
                    AvailablePill(label = "Available")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Specs / Tags
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Category Chip
                SpecTag(
                    text = device.category.label,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // ID Tag
                SpecTag(
                    text = "ID: #${device.id}",
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Direction Tag
                if (device.isOutput && device.isInput) {
                    SpecTag(text = "In + Out", containerColor = AudioVioletSecondary.copy(alpha = 0.12f), contentColor = AudioVioletSecondary)
                } else if (device.isOutput) {
                    SpecTag(text = "Output Sink", containerColor = AudioCyanPrimary.copy(alpha = 0.12f), contentColor = AudioCyanPrimary)
                } else {
                    SpecTag(text = "Input Mic", containerColor = AudioAmberWarning.copy(alpha = 0.12f), contentColor = AudioAmberWarning)
                }

                // Sample Rates
                if (device.sampleRates.isNotEmpty()) {
                    val rateText = device.sampleRates.take(3).joinToString("/") { "${it / 1000}k" }
                    SpecTag(
                        text = "$rateText Hz",
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Channels
                if (device.channelCounts.isNotEmpty()) {
                    val chText = device.channelCounts.joinToString("/") {
                        when (it) {
                            1 -> "Mono"
                            2 -> "Stereo"
                            else -> "${it}ch"
                        }
                    }
                    SpecTag(
                        text = chText,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun ActivePill(label: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = AudioEmeraldActive.copy(alpha = 0.15f),
        border = androidx.compose.foundation.BorderStroke(1.dp, AudioEmeraldActive.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = AudioEmeraldActive,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                ),
                color = AudioEmeraldActive
            )
        }
    }
}

@Composable
fun AvailablePill(label: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SpecTag(
    text: String,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = containerColor
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = contentColor
        )
    }
}

fun resolveDeviceIcon(device: AudioDeviceModel): ImageVector {
    return when {
        device.isBluetooth -> Icons.Default.Bluetooth
        device.isUsb -> Icons.Default.Usb
        device.isWired && device.isOutput -> Icons.Default.Headphones
        device.isInput -> Icons.Default.Mic
        device.category == DeviceCategory.HDMI -> Icons.Default.Tv
        device.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> Icons.Default.PhoneAndroid
        device.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> Icons.Default.Speaker
        else -> Icons.Default.VolumeUp
    }
}
