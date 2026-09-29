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

package com.lanrhyme.micyou.network

import com.lanrhyme.micyou.util.Logger
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress

/**
 * 双向唤醒：UDP 魔数包（用户明确不要配对密钥，局域网信任）。
 * 与桌面端 tauri-app/src-tauri/src/wake.rs 对齐。
 *
 * - 手机点开始 → 先广播，PC 常驻监听收到后自动起服务。
 * - PC 点开始 → 发到本机，手机监听收到后自动推流。
 *
 * 线格式（8 字节固定）：`MicW` + version(1) + action(1=start) + reserved(2)。
 */

/** 唤醒 UDP 端口（与 8554 TCP / 8555 音频 UDP 错开）。 */
const val WAKE_UDP_PORT = 8553

private val WAKE_MAGIC_0: Byte = 0x4D
private val WAKE_MAGIC_1: Byte = 0x69
private val WAKE_MAGIC_2: Byte = 0x63
private val WAKE_MAGIC_3: Byte = 0x57
private const val WAKE_VERSION: Byte = 1
private const val WAKE_ACTION_START: Byte = 1

/** 构造唤醒包。 */
fun buildWakePacket(): ByteArray {
    return byteArrayOf(WAKE_MAGIC_0, WAKE_MAGIC_1, WAKE_MAGIC_2, WAKE_MAGIC_3, WAKE_VERSION, WAKE_ACTION_START, 0, 0)
}

/** 校验唤醒包。 */
fun isWakePacket(buf: ByteArray, length: Int): Boolean {
    if (length < 8) return false
    return buf[0] == WAKE_MAGIC_0 && buf[1] == WAKE_MAGIC_1 &&
        buf[2] == WAKE_MAGIC_2 && buf[3] == WAKE_MAGIC_3 &&
        buf[4] == WAKE_VERSION && buf[5] == WAKE_ACTION_START
}

/**
 * 发送唤醒包：先局域网广播，再向已配 IP 单播（尽力而为）。
 * 调用方应在 IO 线程执行。
 */
fun sendWakePacket(configuredIp: String?) {
    var sock: DatagramSocket? = null
    try {
        sock = DatagramSocket()
        sock.broadcast = true
        val pkt = buildWakePacket()
        val targets = mutableListOf("255.255.255.255")
        if (!configuredIp.isNullOrBlank()) {
            targets.add(configuredIp.trim())
        }
        for (t in targets.distinct()) {
            try {
                val addr = InetAddress.getByName(t)
                sock.send(DatagramPacket(pkt, pkt.size, InetSocketAddress(addr, WAKE_UDP_PORT)))
                Logger.i("Wake", "wake sent to $t:$WAKE_UDP_PORT")
            } catch (e: Exception) {
                Logger.w("Wake", "wake send to $t failed: ${e.message}")
            }
        }
    } catch (e: Exception) {
        Logger.w("Wake", "wake socket failed: ${e.message}")
    } finally {
        try {
            sock?.close()
        } catch (_: Exception) {
        }
    }
}
