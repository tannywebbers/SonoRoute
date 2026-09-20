package com.example.ui.components

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.model.AudioDeviceModel
import com.example.audio.model.DeviceCategory
import com.example.ui.theme.IosBorderLight
import com.example.ui.theme.IosSystemBlue
import com.example.ui.theme.IosSystemGreen
import com.example.ui.theme.IosSystemGreenContainer
import com.example.ui.theme.IosTextPrimary
import com.example.ui.theme.IosTextSecondary

@Composable
fun DeviceSelectorCard(
    device: AudioDeviceModel?,
    isInput: Boolean,
    isRoutingInProgress: Boolean = false,
    isRoutingFailed: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(20.dp)
    val testTag = if (isInput) "input_device_selector" else "output_device_selector"

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag)
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
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon in soft tinted rounded box
            val icon = resolveDeviceIcon(device, isInput)
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (device != null) IosSystemBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Device Name & Category/Type
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = device?.name ?: if (isInput) "No Microphone Selected" else "No Output Selected",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    ),
                    color = IosTextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = device?.typeLabel ?: if (isInput) "Tap to select microphone" else "Tap to select output",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp
                    ),
                    color = IosTextSecondary
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Routing transition indicator or Active badge + Chevron
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isRoutingInProgress) {
                    SwitchingPill()
                } else if (isRoutingFailed) {
                    FailedPill()
                } else if (device != null) {
                    ActivePill()
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = "Select",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
fun SwitchingPill(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(IosSystemBlue.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(10.dp),
            strokeWidth = 1.5.dp,
            color = IosSystemBlue
        )
        Text(
            text = "Switching...",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp
            ),
            color = IosSystemBlue
        )
    }
}

@Composable
fun FailedPill(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(com.example.ui.theme.IosSystemAmber.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Unable to switch",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp
            ),
            color = com.example.ui.theme.IosSystemAmber
        )
    }
}

@Composable
fun ActivePill(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(IosSystemGreenContainer)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(IosSystemGreen)
        )
        Text(
            text = "Active",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            ),
            color = IosSystemGreen
        )
    }
}

fun resolveDeviceIcon(device: AudioDeviceModel?, isInput: Boolean): ImageVector {
    if (device == null) {
        return if (isInput) Icons.Default.Mic else Icons.Default.VolumeUp
    }
    return when (device.category) {
        DeviceCategory.BLUETOOTH -> Icons.Default.Bluetooth
        DeviceCategory.USB -> Icons.Default.Usb
        DeviceCategory.WIRED -> if (isInput) Icons.Default.Mic else Icons.Default.Headphones
        DeviceCategory.BUILT_IN -> if (isInput) Icons.Default.Mic else Icons.Default.VolumeUp
        else -> if (isInput) Icons.Default.Mic else Icons.Default.VolumeUp
    }
}
