/*
 * MicYou — Turns your Android device into a high-quality PC microphone.
 * Copyright (C) 2026 LanRhyme <https://github.com/LanRhyme/MicYou>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version, with the MicYou Plugin Exception.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 */

package com.lanrhyme.micyou.ui

import com.lanrhyme.micyou.R
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColor
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lanrhyme.micyou.animation.EasingFunctions
import com.lanrhyme.micyou.animation.rememberBreathAnimation
import com.lanrhyme.micyou.animation.rememberGlowAnimation
import com.lanrhyme.micyou.animation.rememberPulseAnimation
import com.lanrhyme.micyou.animation.rememberRotationAnimation
import com.lanrhyme.micyou.animation.rememberWaveAnimation
import com.lanrhyme.micyou.ui.compose.material3.*
import com.lanrhyme.micyou.ui.compose.haze.HazeState
import com.lanrhyme.micyou.ui.compose.haze.rememberHazeState
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import androidx.compose.ui.res.stringResource
import com.lanrhyme.micyou.settings.Settings
import com.lanrhyme.micyou.theme.isDarkThemeActive
import com.lanrhyme.micyou.ui.visualizer.AudioVisualizer
import com.lanrhyme.micyou.ui.background.CustomBackground
import com.lanrhyme.micyou.ui.background.HazeSurface
import com.lanrhyme.micyou.ui.MobileHome
import com.lanrhyme.micyou.ui.ShardTextField
import com.lanrhyme.micyou.ui.visualizer.ConnectingAnimation
import com.lanrhyme.micyou.viewmodel.AppUiState
import com.lanrhyme.micyou.viewmodel.ConnectionMode
import com.lanrhyme.micyou.viewmodel.MainViewModel
import com.lanrhyme.micyou.viewmodel.StreamState
import com.lanrhyme.micyou.viewmodel.VisualizerStyle
import com.lanrhyme.micyou.network.REMOTE_KEY_ALT_SPACE
import com.lanrhyme.micyou.network.REMOTE_KEY_ENTER
import com.lanrhyme.micyou.network.REMOTE_KEY_RALT

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileHome(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsState()
    // 静态界面：不再订阅每块音频的电平流，避免高频重组刷新与耗电。
    
    val snackbarHostState = remember { SnackbarHostState() }
    val isDarkTheme = isDarkThemeActive(state.themeMode)
    val forcePureBlackBackground = state.oledPureBlack && isDarkTheme
    
    var showSettings by remember { mutableStateOf(false) }
    var contentVisible by remember { mutableStateOf(false) }

    // Handle Android system back gesture to close settings page (no-op on desktop)
    BackHandler(enabled = showSettings) {
        showSettings = false
    }
    val hazeState = if (state.backgroundSettings.enableHazeEffect && state.backgroundSettings.hasCustomBackground) {
        rememberHazeState()
    } else null
    
    LaunchedEffect(Unit) {
        delay(100)
        contentVisible = true
    }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    val focusManager = LocalFocusManager.current
    LaunchedEffect(showSettings) {
        if (showSettings) {
            focusManager.clearFocus()
        }
    }

    // settings overlay handled below via AnimatedVisibility so exit animation can run

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.surfaceContainer) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            CustomBackground(
                settings = state.backgroundSettings,
                modifier = Modifier.fillMaxSize(),
                hazeState = hazeState,
                forcePureBlackBackground = forcePureBlackBackground
            )
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header
                AnimatedCardVisibility(
                    visible = contentVisible,
                    delayMillis = 50
                ) {
                    MobileHeaderSection(
                                                state = state,
                        onOpenSettings = { showSettings = true },
                                                cardOpacity = state.backgroundSettings.cardOpacity,
                        hazeState = hazeState
                    )
                }

                // Connection config
                AnimatedCardVisibility(
                    visible = contentVisible,
                    delayMillis = 150
                ) {
                    ConnectionConfigCard(
                        state = state,
                        viewModel = viewModel,
                                                cardOpacity = state.backgroundSettings.cardOpacity,
                        hazeState = hazeState
                    )
                }

                // Compact static status: text + button only, no card, no icon
                AnimatedCardVisibility(
                    visible = contentVisible,
                    delayMillis = 250,
                    modifier = Modifier.weight(1f)
                ) {
                    MobileCompactStatus(
                        state = state,
                        viewModel = viewModel
                    )
                }

                // Bottom bar (mute + settings)
                AnimatedCardVisibility(
                    visible = contentVisible,
                    delayMillis = 350
                ) {
                    MobileBottomBar(
                        state = state,
                        viewModel = viewModel,
                                                cardOpacity = state.backgroundSettings.cardOpacity,
                        hazeState = hazeState
                    )
                }
            }
            // Settings page overlay
            AnimatedVisibility(
                visible = showSettings,
                enter = slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(360, easing = EasingFunctions.EaseOutExpo)
                ) + fadeIn(animationSpec = tween(240)),
                exit = slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(300, easing = EasingFunctions.EaseInOutExpo)
                ) + fadeOut(animationSpec = tween(200))
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shadowElevation = 0.dp,
                    tonalElevation = 0.dp
                ) {
                    MobileSettingsPage(viewModel = viewModel, onClose = { showSettings = false }, hazeState = hazeState)
                }
            }
        }
    }
}

@Composable
private fun AnimatedCardVisibility(
    visible: Boolean,
    delayMillis: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val cardAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(400, delayMillis, easing = EasingFunctions.EaseOutExpo)
    )
    val cardOffsetY by animateFloatAsState(
        targetValue = if (visible) 0f else 30f,
        animationSpec = tween(500, delayMillis, easing = EasingFunctions.EaseOutExpo)
    )

    Box(
        modifier = modifier.graphicsLayer {
            this.alpha = cardAlpha
            translationY = cardOffsetY
        }
    ) {
        content()
    }
}

// ==================== Header ====================

@Composable
private fun MobileHeaderSection(
    state: AppUiState,
    onOpenSettings: () -> Unit,
    cardOpacity: Float = 1f,
    hazeState: HazeState? = null
) {
    HazeSurface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceBright.copy(alpha = cardOpacity),
        hazeColor = MaterialTheme.colorScheme.surfaceBright.copy(alpha = cardOpacity * 0.7f),
        modifier = Modifier.fillMaxWidth(),
        hazeState = hazeState,
        enabled = state.backgroundSettings.enableHazeEffect
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // App icon badge
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painterResource(R.drawable.icon_pip),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                
                Column {
                    // Static title (no animation)
                    Text(
                        stringResource(R.string.appName),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "${stringResource(R.string.ipLabel)}${"Client"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
    val settingsInteractionSource = remember { MutableInteractionSource() }
    val isSettingsPressed by settingsInteractionSource.collectIsPressedAsState()
    val settingsScale by animateFloatAsState(
                targetValue = if (isSettingsPressed) 0.85f else 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy)
            )
            
            IconButton(
                onClick = onOpenSettings,
                interactionSource = settingsInteractionSource,
                modifier = Modifier.size(32.dp).scale(settingsScale)
            ) {
                Icon(
                    Icons.Rounded.Settings,
                    contentDescription = stringResource(R.string.settingsTitle),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ==================== Connection Config ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectionConfigCard(
    state: AppUiState,
    viewModel: MainViewModel,
    cardOpacity: Float = 1f,
    hazeState: HazeState? = null
) {
    HazeSurface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceBright.copy(alpha = cardOpacity),
        hazeColor = MaterialTheme.colorScheme.surfaceBright.copy(alpha = cardOpacity * 0.7f),
        modifier = Modifier.fillMaxWidth(),
        hazeState = hazeState,
        enabled = state.backgroundSettings.enableHazeEffect
    ) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Mode label
            Text(
                stringResource(R.string.connectionModeLabel),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Mode selector
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val modes = listOf(
                    ConnectionMode.Wifi to (stringResource(R.string.modeWifi) to Icons.Rounded.Wifi),
                    ConnectionMode.Usb to (stringResource(R.string.modeUsb) to Icons.Rounded.Usb)
                )

                modes.forEach { (mode, info) ->
                    val (label, icon) = info
                    val isSelected = state.mode == mode

                    val bgColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHighest,
                        animationSpec = tween(200)
                    )
    val contentColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = tween(200)
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(bgColor)
                            .hoverable(interactionSource = remember { MutableInteractionSource() })
                            .clickable { viewModel.setMode(mode) }
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(icon, null, tint = contentColor, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.height(2.dp))
                            Text(
                                label,
                                style = MaterialTheme.typography.labelSmall,
                                color = contentColor,
                                maxLines = 1
                            )
                        }
                    }
                }
            }



            // Discovered devices list (WiFi mode only, hidden while streaming:
            // connected already, list churn only distracts)
            if (state.mode == ConnectionMode.Wifi && state.streamState != StreamState.Streaming) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.testTag("home_device_section")
                        .semantics { contentDescription = "home_device_section" }
                ) {
                    // Header row: label + refresh button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (state.isDiscovering && state.discoveredDevices.isEmpty()) {
                            Icon(
                                Icons.Rounded.Dns, null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                stringResource(R.string.scanningForDevices),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Text(
                                stringResource(R.string.discoveredDevicesLabel),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        IconButton(
                            onClick = { viewModel.restartDiscovery() },
                            modifier = Modifier.size(32.dp).testTag("home_refresh")
                                .semantics { contentDescription = "home_refresh" }
                        ) {
                            Icon(
                                Icons.Filled.Refresh, "home_refresh",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    // Device list
                    state.discoveredDevices.forEach { device ->
                        val isSelected = state.ipAddress == device.hostAddress &&
                                state.port == device.port.toString()
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth().testTag("home_device_row")
                                .semantics { contentDescription = "home_device_row" }.clickable {
                                viewModel.selectDiscoveredDevice(device)
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.Dns,
                                    null,
                                    modifier = Modifier.size(18.dp),
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(device.name, style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        "${device.hostAddress}:${device.port}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // IP/Port input
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(tween(300)) + expandVertically(),
                exit = fadeOut(tween(200)) + shrinkVertically()
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (state.mode != ConnectionMode.Usb) {
                        ShardTextField(
                            value = state.ipAddress,
                            onValueChange = { viewModel.setIp(it) },
                            label = stringResource(R.string.targetIpLabel),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall
                        )
                    }
                    ShardTextField(
                        value = state.port,
                        onValueChange = { viewModel.setPort(it) },
                        label = stringResource(R.string.portLabel),
                        modifier = if (state.mode != ConnectionMode.Usb)
                            Modifier.width(100.dp) else Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

// ==================== Compact Status ====================

@Composable
private fun MobileCompactStatus(
    state: AppUiState,
    viewModel: MainViewModel
) {
    val isRunning = state.streamState == StreamState.Streaming
    val isConnecting = state.streamState == StreamState.Connecting
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        MobileStatusText(state = state)
        MobileMainButton(
            isRunning = isRunning,
            isConnecting = isConnecting,
            viewModel = viewModel
        )
        if (state.streamState == StreamState.Error && state.errorMessage != null) {
            Text(
                state.errorMessage ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 24.dp),
                maxLines = 3,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ==================== Bottom Bar ====================

@Composable
private fun MobileBottomBar(
    state: AppUiState,
    viewModel: MainViewModel,
    cardOpacity: Float = 1f,
    hazeState: HazeState? = null
) {
    HazeSurface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceBright.copy(alpha = cardOpacity),
        hazeColor = MaterialTheme.colorScheme.surfaceBright.copy(alpha = cardOpacity * 0.7f),
        modifier = Modifier.fillMaxWidth(),
        hazeState = hazeState,
        enabled = state.backgroundSettings.enableHazeEffect
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MobileMuteButton(
                    isMuted = state.isMuted,
                    onToggle = { viewModel.toggleMute() }
                )
                MobileRemoteKeyButton(
                    label = stringResource(R.string.remoteKeyRalt),
                    testTag = "home_key_ralt",
                    enabled = state.streamState == StreamState.Streaming,
                    onDown = { viewModel.remoteKeyDown(REMOTE_KEY_RALT) },
                    onUp = { viewModel.remoteKeyUp(REMOTE_KEY_RALT) }
                )
                MobileRemoteKeyButton(
                    label = stringResource(R.string.remoteKeyAltSpace),
                    testTag = "home_key_altspace",
                    enabled = state.streamState == StreamState.Streaming,
                    onDown = { viewModel.remoteKeyDown(REMOTE_KEY_ALT_SPACE) },
                    onUp = { viewModel.remoteKeyUp(REMOTE_KEY_ALT_SPACE) }
                )
                MobileRemoteKeyButton(
                    label = stringResource(R.string.remoteKeyEnter),
                    testTag = "home_key_enter",
                    enabled = state.streamState == StreamState.Streaming,
                    onDown = { viewModel.remoteKeyDown(REMOTE_KEY_ENTER) },
                    onUp = { viewModel.remoteKeyUp(REMOTE_KEY_ENTER) }
                )
            }
            
            val dotColor = when (state.streamState) {
                StreamState.Idle -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                StreamState.Connecting -> MaterialTheme.colorScheme.tertiary
                StreamState.Streaming -> MaterialTheme.colorScheme.primary
                StreamState.Error -> MaterialTheme.colorScheme.error
            }

            Surface(
                shape = CircleShape,
                color = dotColor,
                modifier = Modifier.size(8.dp)
            ) {}
        }
    }
}

@Composable
private fun MobileRemoteKeyButton(
    label: String,
    testTag: String,
    enabled: Boolean,
    onDown: () -> Unit,
    onUp: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (enabled) MaterialTheme.colorScheme.surfaceContainerHighest
        else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f),
        animationSpec = tween(200)
    )
    val contentColor by animateColorAsState(
        targetValue = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        animationSpec = tween(200)
    )

    Surface(
        shape = MaterialTheme.shapes.small,
        color = bgColor,
        modifier = Modifier.testTag(testTag)
            .semantics { contentDescription = testTag }.pointerInput(enabled) {
            if (!enabled) return@pointerInput
            detectTapGestures(
                onPress = {
                    onDown()
                    try {
                        awaitRelease()
                    } finally {
                        onUp()
                    }
                }
            )
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor
            )
        }
    }
}

@Composable
private fun MobileMuteButton(
    isMuted: Boolean,
    onToggle: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy)
    )
    val bgColor by animateColorAsState(
        targetValue = if (isMuted) MaterialTheme.colorScheme.errorContainer
        else MaterialTheme.colorScheme.surfaceContainerHighest,
        animationSpec = tween(200)
    )
    val contentColor by animateColorAsState(
        targetValue = if (isMuted) MaterialTheme.colorScheme.onErrorContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(200)
    )
    
    Surface(
        shape = MaterialTheme.shapes.small,
        color = bgColor,
        modifier = Modifier.scale(scale).testTag("home_mute")
            .semantics { contentDescription = "home_mute" }.clickable(interactionSource, null) { onToggle() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                if (isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
            Text(
                if (isMuted) stringResource(R.string.unmuteLabel) else stringResource(R.string.muteLabel),
                style = MaterialTheme.typography.labelMedium,
                color = contentColor
            )
        }
    }
}

// ==================== Static Status Text ====================

@Composable
private fun MobileStatusText(state: AppUiState) {
    val text = when (state.streamState) {
        StreamState.Streaming -> stringResource(R.string.statusStreaming, state.ipAddress, state.port)
        StreamState.Connecting -> stringResource(R.string.statusConnecting)
        StreamState.Error -> stringResource(R.string.statusError)
        else -> stringResource(R.string.statusIdle)
    }
    val color = when (state.streamState) {
        StreamState.Streaming -> MaterialTheme.colorScheme.primary
        StreamState.Connecting -> MaterialTheme.colorScheme.tertiary
        StreamState.Error -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = color,
        modifier = Modifier.padding(vertical = 12.dp).testTag("home_status_text")
            .semantics { contentDescription = "home_status_text" },
        textAlign = TextAlign.Center
    )
}

// ==================== Audio Visualizers ====================

@Composable
private fun MobileAudioVisualizer(
    modifier: Modifier = Modifier,
    audioLevel: Float,
    color: Color,
    style: VisualizerStyle = VisualizerStyle.Ripple
) {
    AudioVisualizer(
        modifier = modifier,
        audioLevel = audioLevel,
        color = color,
        style = style
    )
}

// Visualizer implementations moved to AudioVisualizers.kt













// ==================== Main Button ====================

@Composable
private fun MobileMainButton(
    isRunning: Boolean,
    isConnecting: Boolean,
    viewModel: MainViewModel
) {
    // Text button: static, compact, no icon, no animation.
    val label = when {
        isRunning -> stringResource(R.string.stop)
        isConnecting -> stringResource(R.string.statusConnecting)
        else -> stringResource(R.string.start)
    }
    val containerColor = when {
        isRunning -> MaterialTheme.colorScheme.error
        isConnecting -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    Button(
        onClick = {
            if (isRunning || isConnecting) {
                viewModel.stopStream()
            } else {
                viewModel.startStream()
            }
        },
        colors = ButtonDefaults.buttonColors(containerColor = containerColor),
        modifier = Modifier.testTag("home_main_button")
            .semantics { contentDescription = "home_main_button" }
    ) {
        Text(label)
    }
}
