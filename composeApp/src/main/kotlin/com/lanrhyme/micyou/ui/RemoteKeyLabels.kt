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

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lanrhyme.micyou.R
import com.lanrhyme.micyou.network.VK_ENTER
import com.lanrhyme.micyou.network.VK_LALT
import com.lanrhyme.micyou.network.VK_LCTRL
import com.lanrhyme.micyou.network.VK_LWIN
import com.lanrhyme.micyou.network.VK_RALT
import com.lanrhyme.micyou.network.VK_RCTRL
import com.lanrhyme.micyou.network.VK_RWIN
import com.lanrhyme.micyou.network.VK_SPACE

/** VK 显示名（主页按钮与设置页共用）。 */
@Composable
fun chordVkName(vk: Int): String {
    return when (vk) {
        VK_LCTRL -> stringResource(R.string.chordKeyLCtrl)
        VK_RCTRL -> stringResource(R.string.chordKeyRCtrl)
        VK_LALT -> stringResource(R.string.chordKeyLAlt)
        VK_RALT -> stringResource(R.string.chordKeyRAlt)
        VK_LWIN -> stringResource(R.string.chordKeyLWin)
        VK_RWIN -> stringResource(R.string.chordKeyRWin)
        VK_SPACE -> stringResource(R.string.chordKeySpace)
        VK_ENTER -> stringResource(R.string.remoteKeyEnter)
        else -> "0x" + vk.toString(16).uppercase()
    }
}

/** 和弦显示文本，注意：for循环内直调，勿用joinToString/map lambda。 */
@Composable
fun chordSummary(vks: List<Int>): String {
    if (vks.isEmpty()) return stringResource(R.string.chordEmpty)
    val sb = StringBuilder()
    for ((i, vk) in vks.withIndex()) {
        if (i > 0) sb.append('+')
        sb.append(chordVkName(vk))
    }
    return sb.toString()
}
