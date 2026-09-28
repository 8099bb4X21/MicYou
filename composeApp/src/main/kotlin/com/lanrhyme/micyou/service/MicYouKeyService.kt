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

package com.lanrhyme.micyou.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.lanrhyme.micyou.network.REMOTE_KEY_ALT_SPACE
import com.lanrhyme.micyou.util.Logger

/**
 * 跨组件按键总线：串流中由 AudioStreamViewModel 挂载发送器，断开时摘除。
 * MainActivity（前台）与 MicYouKeyService（无障碍，含灭屏/后台）共用。
 */
object RemoteKeyBus {
    @Volatile
    var sender: ((keyId: Int, pressed: Boolean) -> Unit)? = null
}

/**
 * 无障碍按键服务：Activity 收不到按键时（灭屏/后台）由系统把音量键转交此处。
 * 需用户在系统设置→无障碍中手动开启一次；未串流或开关关闭时原样放行。
 */
class MicYouKeyService : AccessibilityService() {

    override fun onServiceConnected() {
        Logger.i("MicYouKeyService", "Accessibility key filter connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    }

    override fun onInterrupt() {
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_VOLUME_UP &&
            event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN
        ) {
            return false
        }
        val prefs = try {
            getSharedPreferences("android_mic_prefs", Context.MODE_PRIVATE)
        } catch (_: Exception) {
            return false
        }
        if (!prefs.getBoolean("volume_keys_send_remote_key", true)) return false
        val send = RemoteKeyBus.sender ?: return false
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount != 0) return true
                send(REMOTE_KEY_ALT_SPACE, true)
            }
            KeyEvent.ACTION_UP -> send(REMOTE_KEY_ALT_SPACE, false)
            else -> return false
        }
        return true
    }
}
