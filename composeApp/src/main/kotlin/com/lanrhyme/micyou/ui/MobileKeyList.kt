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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
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
 * 四个按键一列四行（不再挤满屏幕，顶部留出返回/设置的位置）；每行左侧有拖拽手柄，
 * 按住拖动可上下排序（松手后持久化）。连接成功后由主界面自动进入；返回即回到主菜单。
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

    // 页面内的显示顺序（槽位下标）。拖拽只改这份顺序，松手后一次性持久化。
    val order = remember { mutableStateListOf(0, 1, 2, 3) }
    var dragging by remember { mutableStateOf(false) }
    var rowHeightPx by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(state.buttonSlots) {
        if (!dragging) {
            order.clear()
            order.addAll(0..3)
        }
    }

    fun persistOrder() {
        viewModel.setButtonSlots(
            order.map { idx -> state.buttonSlots.getOrElse(idx) { DEFAULT_BUTTON_SLOTS[idx] } }
        )
    }

    /** 松手时按累计位移把该槽位移到目标位置，并立即持久化。 */
    fun moveRelative(slotIndex: Int, delta: Int) {
        val from = order.indexOf(slotIndex)
        if (from < 0) return
        val to = (from + delta).coerceIn(0, order.size - 1)
        if (to == from) return
        order.removeAt(from)
        order.add(to, slotIndex)
        persistOrder()
    }

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
                .safeDrawingPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 顶栏：返回（左） / 标题（中） / 设置（右）
            Row(
                modifier = Modifier.fillMaxWidth().height(48.dp),
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

            // 四个按键：一列四行（底部留白，不再贴着屏幕底边）
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                order.forEach { slotIndex ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .onSizeChanged { if (rowHeightPx == 0f) rowHeightPx = it.height.toFloat() },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DragHandle(
                            slotIndex = slotIndex,
                            rowHeightPx = rowHeightPx,
                            onDragStateChange = { dragging = it },
                            onMoveRelative = ::moveRelative
                        )
                        KeyPadButton(
                            index = slotIndex,
                            slots = state.buttonSlots,
                            streaming = streaming,
                            isMuted = state.isMuted,
                            onToggleMute = { viewModel.toggleMute() },
                            onChordDown = { viewModel.remoteChordDown(it) },
                            onChordUp = { viewModel.remoteChordUp(it) },
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                }
            }
        }
    }
}

/**
 * 左侧拖拽手柄：按住上下拖，越过半行高就与相邻交换；松手后持久化顺序。
 * 手柄宽度固定 36dp，不占用按键的可点击区域。
 */
@Composable
private fun DragHandle(
    slotIndex: Int,
    rowHeightPx: Float,
    onDragStateChange: (Boolean) -> Unit,
    onMoveRelative: (Int, Int) -> Unit
) {
    // semantics 的 lambda 不是 @Composable，字符串需在外部取好。
    val dragDescription = stringResource(R.string.dragToReorder)
    Box(
        modifier = Modifier
            .width(36.dp)
            .fillMaxHeight()
            .testTag("keylist_drag_$slotIndex")
            .semantics { contentDescription = dragDescription }
            .pointerInput(slotIndex, rowHeightPx) {
                if (rowHeightPx <= 0f) return@pointerInput
                var acc = 0f
                detectDragGestures(
                    onDragStart = {
                        acc = 0f
                        onDragStateChange(true)
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        acc += amount.y
                    },
                    onDragEnd = {
                        onDragStateChange(false)
                        // 松手时按拖过的行数一次性排序，避免拖动途中被重组打断手势。
                        val delta = (acc / rowHeightPx).roundToInt()
                        if (delta != 0) onMoveRelative(slotIndex, delta)
                    },
                    onDragCancel = { onDragStateChange(false) }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Rounded.DragIndicator,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
        )
    }
}
/**
 * 按键底色分层：四个按钮使用不同的容器层级 + 透明度 + 边框强度，
 * 形成深浅层次，避免整页同一色块显得单调。
 */
@Composable
private fun slotLayerColor(index: Int, enabled: Boolean): Color {
    val scheme = MaterialTheme.colorScheme
    val base = when (index % 4) {
        0 -> scheme.surfaceContainerLowest
        1 -> scheme.surfaceContainerLow
        2 -> scheme.surfaceContainerHigh
        else -> scheme.surfaceContainerHighest
    }
    val layerAlpha = 0.82f + (index % 4) * 0.06f
    return if (enabled) base.copy(alpha = layerAlpha) else base.copy(alpha = 0.35f)
}

/**
 * 按键列表页的大号按键：静音键点击切换；和弦键按下发 down、松开发 up。
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
        targetValue = slotLayerColor(index, enabled),
        animationSpec = tween(200)
    )
    val contentColor = if (enabled) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    val borderAlpha = 0.16f + (index % 4) * 0.06f

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = bgColor,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = borderAlpha)
        ),
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
