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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.session.AudioSession
import com.example.audio.session.SessionLifecycleState
import com.example.ui.AudioProfile
import com.example.ui.theme.IosBorderLight
import com.example.ui.theme.IosSystemAmber
import com.example.ui.theme.IosSystemBlue
import com.example.ui.theme.IosSystemGreen
import com.example.ui.theme.IosSystemGreenContainer
import com.example.ui.theme.IosSystemRed
import com.example.ui.theme.IosTextPrimary
import com.example.ui.theme.IosTextSecondary

@Composable
fun ProfileSelectorCard(
    currentProfile: AudioProfile,
    sessionState: SessionLifecycleState = SessionLifecycleState.IDLE,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(18.dp)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("audio_profile_card")
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
                    imageVector = getProfileIcon(currentProfile),
                    contentDescription = null,
                    tint = IosSystemBlue,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = currentProfile.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        ),
                        color = IosTextPrimary
                    )

                    when (sessionState) {
                        SessionLifecycleState.ACTIVE -> {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(IosSystemGreenContainer)
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(IosSystemGreen))
                                Text(
                                    text = "Active",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                                    color = IosSystemGreen
                                )
                            }
                        }
                        SessionLifecycleState.PAUSED -> {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(IosSystemAmber.copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(IosSystemAmber))
                                Text(
                                    text = "Paused",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                                    color = IosSystemAmber
                                )
                            }
                        }
                        SessionLifecycleState.STARTING, SessionLifecycleState.CONFIGURING -> {
                            Text(
                                text = "Configuring...",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = IosSystemBlue
                            )
                        }
                        else -> {}
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = currentProfile.subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = IosTextSecondary
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = "Change Profile",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSelectionBottomSheet(
    currentProfile: AudioProfile,
    activeSession: AudioSession? = null,
    onStartSession: () -> Unit = {},
    onPauseSession: () -> Unit = {},
    onResumeSession: () -> Unit = {},
    onStopSession: () -> Unit = {},
    onSelectProfile: (AudioProfile) -> Unit,
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pendingProfileToSwitch by remember { mutableStateOf<AudioProfile?>(null) }

    if (pendingProfileToSwitch != null) {
        AlertDialog(
            onDismissRequest = { pendingProfileToSwitch = null },
            title = {
                Text(
                    text = "Switch Audio Profile?",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text(
                    text = "Switching to ${pendingProfileToSwitch?.title} will reinitialize the active audio session with new latency and buffer parameters.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = pendingProfileToSwitch
                        pendingProfileToSwitch = null
                        if (target != null) {
                            onSelectProfile(target)
                            onDismissRequest()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IosSystemBlue)
                ) {
                    Text("Switch Profile")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingProfileToSwitch = null }) {
                    Text("Cancel")
                }
            }
        )
    }

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
                .padding(bottom = 32.dp)
                .testTag("profile_selection_bottom_sheet")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Audio Profile & Session",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        ),
                        color = IosTextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Select an audio routing usage profile",
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

            // Session Control Bar
            if (activeSession != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, IosBorderLight)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "SESSION STATUS",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    letterSpacing = 0.5.sp
                                ),
                                color = IosTextSecondary
                            )
                            Text(
                                text = activeSession.lifecycleState.name,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                ),
                                color = when (activeSession.lifecycleState) {
                                    SessionLifecycleState.ACTIVE -> IosSystemGreen
                                    SessionLifecycleState.PAUSED -> IosSystemAmber
                                    else -> IosTextPrimary
                                }
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            when (activeSession.lifecycleState) {
                                SessionLifecycleState.ACTIVE -> {
                                    OutlinedButton(
                                        onClick = onPauseSession,
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Pause", fontSize = 12.sp)
                                    }
                                    Button(
                                        onClick = onStopSession,
                                        modifier = Modifier.height(34.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = IosSystemRed)
                                    ) {
                                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Stop", fontSize = 12.sp)
                                    }
                                }
                                SessionLifecycleState.PAUSED -> {
                                    Button(
                                        onClick = onResumeSession,
                                        modifier = Modifier.height(34.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = IosSystemBlue)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Resume", fontSize = 12.sp)
                                    }
                                    OutlinedButton(
                                        onClick = onStopSession,
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Text("Stop", fontSize = 12.sp)
                                    }
                                }
                                else -> {
                                    Button(
                                        onClick = onStartSession,
                                        modifier = Modifier.height(34.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = IosSystemBlue)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Start Session", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(AudioProfile.values()) { profile ->
                    val isSelected = profile == currentProfile
                    val shape = RoundedCornerShape(16.dp)

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .border(
                                1.dp,
                                if (isSelected) IosSystemBlue.copy(alpha = 0.5f) else IosBorderLight,
                                shape
                            )
                            .clickable {
                                if (activeSession?.lifecycleState == SessionLifecycleState.ACTIVE && profile != currentProfile) {
                                    pendingProfileToSwitch = profile
                                } else {
                                    onSelectProfile(profile)
                                    onDismissRequest()
                                }
                            },
                        shape = shape,
                        color = if (isSelected) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .border(
                                        2.dp,
                                        if (isSelected) IosSystemBlue else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                        CircleShape
                                    )
                                    .background(if (isSelected) IosSystemBlue else Color.Transparent),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = getProfileIcon(profile),
                                    contentDescription = null,
                                    tint = if (isSelected) IosSystemBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = profile.title,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 15.sp
                                    ),
                                    color = IosTextPrimary
                                )
                                Text(
                                    text = profile.subtitle,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                    color = IosTextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun getProfileIcon(profile: AudioProfile): ImageVector {
    return when (profile) {
        AudioProfile.STANDARD -> Icons.Default.Headphones
        AudioProfile.GAMING -> Icons.Default.SportsEsports
        AudioProfile.VOICE_CHAT -> Icons.Default.RecordVoiceOver
        AudioProfile.RECORDING -> Icons.Default.Mic
        AudioProfile.SCREEN_SHARING -> Icons.Default.ScreenShare
        AudioProfile.MEDIA -> Icons.Default.MusicNote
    }
}
