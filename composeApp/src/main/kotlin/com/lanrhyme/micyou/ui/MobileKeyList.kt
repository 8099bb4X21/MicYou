/*
 * MicYou — Turns your Android device into a high-quality PC microphone.
 * Copyright (C) 2026 LanRhyme <https://github.com/MicYou-Dev/MicYou>
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

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lanrhyme.micyou.R
import com.lanrhyme.micyou.settings.ButtonSlot
import com.lanrhyme.micyou.settings.DEFAULT_BUTTON_SLOTS
import com.lanrhyme.micyou.theme.isDarkThemeActive
import com.lanrhyme.micyou.ui.background.CustomBackground
import com.lanrhyme.micyou.ui.compose.haze.HazeState
import com.lanrhyme.micyou.viewmodel.MainViewModel
import com.lanrhyme.micyou.viewmodel.StreamState

/**
 * 按键列表页（fork）。
 *
 * 四个按键排成一列四行、铺满屏幕；左上角返回、右上角设置。
 * 连接成功后由主界面自动进入；返回即回到主菜单。
 */
@Composable
fun MobileKeyListPage(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    hazeState: HazeState? = null
) {
    val state by viewModel.uiState.collectAsState()
    val isDarkTheme = isDarkThemeActive(state.themeMode)
    val forcePureBlackBackground = state.oledPureBlack && isDarkTheme
    val streaming = state.streamState == StreamState.Streaming
    val slots = state.buttonSlots

    Box(modifier = Modifier.fillMaxSize()) {
        CustomBackground(
            settings = state.backgroundSettings,
            modifier = Modifier.fillMaxSize(),
            hazeState = hazeState,
            forcePureBlackBackground = forcePureBlackBackground
        )

        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 顶栏：返回（左） / 标题（中） / 设置（右）
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(40.dp).testTag("keylist_back")
                        .semantics { contentDescription = "keylist_back" }
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.backTitle),
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    stringResource(R.string.keyListTitle),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.size(40.dp).testTag("keylist_settings")
                        .semantics { contentDescription = "keylist_settings" }
                ) {
                    Icon(
                        Icons.Rounded.Settings,
                        contentDescription = stringResource(R.string.settingsTitle),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 四个按键：一列四行，等分铺满
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                for (i in 0..3) {
                    KeyPadButton(
                        index = i,
                        slots = slots,
                        streaming = streaming,
                        isMuted = state.isMuted,
                        onToggleMute = { viewModel.toggleMute() },
                        onChordDown = { viewModel.remoteChordDown(it) },
                        onChordUp = { viewModel.remoteChordUp(it) },
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    )
                }
            }
        }
    }
}
/**
 * 按键列表页的大号按键：静音键直接点击切换；和弦键按下发 down、松开发 up。
 * 未连接（且非静音键）时置灰禁用，与主界面槽位的行为一致。
 */
@Composable
private fun KeyPadButton(
    index: Int,
    slots: List<ButtonSlot>,
    streaming: Boolean,
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    onChordDown: (List<Int>) -> Unit,
    onChordUp: (List<Int>) -> Unit,
    modifier: Modifier = Modifier
) {
    val slot = slots.getOrElse(index) { DEFAULT_BUTTON_SLOTS[index] }
    val tag = "keylist_btn_" + (index + 1)
    val isMute = slot.type == "mute"
    val chordEnabled = !isMute && streaming
    val enabled = isMute || chordEnabled

    val bgColor by animateColorAsState(
        targetValue = if (enabled) MaterialTheme.colorScheme.surfaceContainerHighest
        else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f),
        animationSpec = tween(200)
    )
    val contentColor = if (enabled) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = bgColor,
        modifier = modifier.testTag(tag).semantics { contentDescription = tag }
            .then(
                if (isMute) {
                    Modifier.clickable { onToggleMute() }
                } else {
                    Modifier.pointerInput(chordEnabled) {
                        if (!chordEnabled) return@pointerInput
                        detectTapGestures(
                            onPress = {
                                onChordDown(slot.chord)
                                try {
                                    awaitRelease()
                                } finally {
                                    onChordUp(slot.chord)
                                }
                            }
                        )
                    }
                }
            )
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (isMute) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        if (isMuted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                        contentDescription = null,
                        modifier = Modifier.size(26.dp),
                        tint = contentColor
                    )
                    Text(
                        stringResource(if (isMuted) R.string.unmuteLabel else R.string.muteLabel),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = contentColor
                    )
                }
            } else {
                Text(
                    chordSummary(slot.chord),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
            }
        }
    }
}
