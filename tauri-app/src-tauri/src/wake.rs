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

//! 双向唤醒：UDP 魔数包（用户明确不要配对密钥，局域网信任）。
//!
//! - 手机点开始 → 先广播唤醒包，PC 常驻监听收到后按 server.json 自动起服务。
//! - PC 点开始（显式）→ 向手机上次 IP 单播 + 广播唤醒包，手机收到后自动推流。
//!
//! 线格式（8 字节固定）：`MicW` + version(1) + action(1=start) + reserved(2)。

/// 唤醒 UDP 端口（与 8554 TCP / 8555 音频 UDP / 8443 Web 均错开）。
pub const WAKE_UDP_PORT: u16 = 8553;
const WAKE_MAGIC: [u8; 4] = [0x4D, 0x69, 0x63, 0x57];
const WAKE_VERSION: u8 = 1;
const WAKE_ACTION_START: u8 = 1;
const WAKE_PACKET_LEN: usize = 8;

/// 构造唤醒包。
pub fn build_wake_packet() -> [u8; WAKE_PACKET_LEN] {
    [
        WAKE_MAGIC[0],
        WAKE_MAGIC[1],
        WAKE_MAGIC[2],
        WAKE_MAGIC[3],
        WAKE_VERSION,
        WAKE_ACTION_START,
        0,
        0,
    ]
}

/// 校验唤醒包。
pub fn is_wake_packet(buf: &[u8]) -> bool {
    buf.len() >= WAKE_PACKET_LEN
        && buf[0..4] == WAKE_MAGIC
        && buf[4] == WAKE_VERSION
        && buf[5] == WAKE_ACTION_START
}

/// 向手机上次 IP 单播 + 局域网广播（尽力而为，调用方只记日志）。
/// 返回成功发出的目标数。
pub fn send_wake_to_last() -> usize {
    let prefs = crate::app_config::load_server_prefs();
    let pkt = build_wake_packet();
    let sock = match std::net::UdpSocket::bind("0.0.0.0:0") {
        Ok(s) => s,
        Err(e) => {
            log::warn!("wake send: bind failed: {e}");
            return 0;
        }
    };
    let _ = sock.set_broadcast(true);
    let mut targets = vec!["255.255.255.255".to_string()];
    if !prefs.last_client_ip.is_empty() {
        targets.push(prefs.last_client_ip.clone());
    }
    let mut ok = 0;
    for t in &targets {
        match sock.send_to(&pkt, (t.as_str(), WAKE_UDP_PORT)) {
            Ok(_) => {
                ok += 1;
                log::info!("wake sent to {t}:{WAKE_UDP_PORT}");
            }
            Err(e) => log::warn!("wake send to {t} failed: {e}"),
        }
    }
    ok
}

/// 常驻唤醒监听（服务停了也留着）。收到包且服务没跑 → 按存档配置起服务。
pub async fn run_wake_listener(app: tauri::AppHandle) {
    use tauri::Manager;
    let sock = match tokio::net::UdpSocket::bind(format!("0.0.0.0:{WAKE_UDP_PORT}")).await {
        Ok(s) => s,
        Err(e) => {
            log::warn!("wake listener: bind udp {WAKE_UDP_PORT} failed: {e}");
            return;
        }
    };
    log::info!("wake listener on udp {WAKE_UDP_PORT}");
    let mut buf = [0u8; 64];
    loop {
        let (n, src) = match sock.recv_from(&mut buf).await {
            Ok(v) => v,
            Err(e) => {
                log::warn!("wake listener recv failed: {e}");
                tokio::time::sleep(std::time::Duration::from_secs(5)).await;
                continue;
            }
        };
        if !is_wake_packet(&buf[..n]) {
            continue;
        }
        log::info!("wake packet from {src}");
        let state = app.state::<crate::server::ServerState>();
        match crate::commands::start_with_saved_prefs(&state, &app).await {
            Ok(m) => log::info!("wake start: {m}"),
            Err(e) => log::warn!("wake start failed: {e}"),
        }
    }
}
