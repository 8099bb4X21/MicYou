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

//! Floating-window commands (fork 语音输入法适配版)。
//!
//! 悬浮窗创建与显隐都走免激活（WS_EX_NOACTIVATE），点击只收鼠标事件、
//! 不抢其它程序焦点；并附带一次性远程按键注入、唤醒包发送与 ui.json 偏好读写。

use micyou_core::config::{self, UiPrefs};
use micyou_core::server::lifecycle::ServerLifecyclePhase;
use micyou_core::server::service::StartRequest;
use micyou_core::server::{start_server, stop_server, ServerState};
use tauri::{AppHandle, Manager, Runtime, State};

pub const FLOATING_WINDOW_LABEL: &str = "floating-window";

/// 泛型版：供托盘/浮窗菜单等泛型上下文调用（具体命令转调它，避免运行时类型统一）。
pub fn ensure_floating_window<R: Runtime>(app: &AppHandle<R>) -> Result<(), String> {
    if app.get_webview_window(FLOATING_WINDOW_LABEL).is_some() {
        return Ok(());
    }
    show_floating_window_built(app)
}

fn show_floating_window_built<R: Runtime>(app: &AppHandle<R>) -> Result<(), String> {
    let _win = tauri::WebviewWindowBuilder::new(
        app,
        FLOATING_WINDOW_LABEL,
        tauri::WebviewUrl::App("#/floating-window".into()),
    )
    .title("MicYouFloating")
    .inner_size(80.0, 80.0)
    .resizable(false)
    .decorations(false)
    .transparent(true)
    .shadow(false)
    .always_on_top(true)
    .skip_taskbar(true)
    .focused(false)
    .build()
    .map_err(|e| e.to_string())?;
    apply_noactivate();
    #[cfg(target_os = "windows")]
    if let Ok(hwnd) = _win.hwnd() {
        // 标题定位可能竞态（窗口刚建），有句柄直接再断言一次样式。
        apply_noactivate_hwnd(hwnd);
    }
    Ok(())
}

/// 悬浮窗免激活（WS_EX_NOACTIVATE）：点击只收鼠标事件，不抢其他程序焦点。
/// 按键注入走 HID 层，本来就不依赖焦点。用标题定位窗口，避免猜 hwnd API。
#[cfg(target_os = "windows")]
fn apply_noactivate() {
    use windows::core::PCWSTR;
    use windows::Win32::UI::WindowsAndMessaging::FindWindowW;
    let title: Vec<u16> = "MicYouFloating\0".encode_utf16().collect();
    // windows 0.58 起 FindWindowW 返回 Result。
    let hwnd = match unsafe { FindWindowW(None, PCWSTR(title.as_ptr())) } {
        Ok(h) => h,
        Err(e) => {
            log::warn!("floating noactivate: FindWindowW failed: {e}");
            return;
        }
    };
    apply_noactivate_hwnd(hwnd);
}

#[cfg(not(target_os = "windows"))]
fn apply_noactivate() {}

/// 给定 HWND 补 WS_EX_NOACTIVATE。与 tauri 内部的 windows 同版本（0.62），
/// HWND 类型互通，直接用 windows crate 的 API，不再走 winapi 裸指针。
#[cfg(target_os = "windows")]
fn apply_noactivate_hwnd(hwnd: windows::Win32::Foundation::HWND) {
    use windows::Win32::UI::WindowsAndMessaging::{
        GetWindowLongPtrW, SetWindowLongPtrW, GWL_EXSTYLE, WS_EX_NOACTIVATE,
    };
    if hwnd.is_invalid() {
        log::warn!("floating noactivate: window not found");
        return;
    }
    let old = unsafe { GetWindowLongPtrW(hwnd, GWL_EXSTYLE) };
    let new = old | WS_EX_NOACTIVATE.0 as isize;
    if new != old {
        unsafe { SetWindowLongPtrW(hwnd, GWL_EXSTYLE, new) };
    }
    let verify = unsafe { GetWindowLongPtrW(hwnd, GWL_EXSTYLE) };
    log::info!("floating noactivate: exstyle {old:#x} -> {verify:#x}");
}

/// 显示悬浮窗但不激活（SW_SHOWNOACTIVATE）：单击/双击动作、设置页重显都不抢焦点。
/// win.show() 走 SW_SHOW 会激活窗口，之前只在创建时设 NOACTIVATE，一 show 就丢。
fn show_floating_noactivate<R: Runtime>(win: &tauri::WebviewWindow<R>) -> Result<(), String> {
    #[cfg(target_os = "windows")]
    {
        use windows::Win32::UI::WindowsAndMessaging::{ShowWindow, SW_SHOWNOACTIVATE};
        match win.hwnd() {
            Ok(hwnd) => {
                apply_noactivate_hwnd(hwnd);
                unsafe {
                    ShowWindow(hwnd, SW_SHOWNOACTIVATE);
                }
                // ShowWindow 可能改写扩展样式，显示后再断言一次。
                apply_noactivate_hwnd(hwnd);
                Ok(())
            }
            Err(e) => {
                log::warn!("floating show-noactivate: hwnd failed ({e}), fallback to show()");
                win.show().map_err(|e| e.to_string())
            }
        }
    }
    #[cfg(not(target_os = "windows"))]
    {
        win.show().map_err(|e| e.to_string())
    }
}
/// 泛型版显隐切换（显示走免激活，避免单击/双击后重显抢焦点）。
pub fn toggle_floating_window_for<R: Runtime>(app: &AppHandle<R>) -> Result<bool, String> {
    if let Some(win) = app.get_webview_window(FLOATING_WINDOW_LABEL) {
        let visible = win.is_visible().unwrap_or(false);
        if visible {
            win.hide().map_err(|e| e.to_string())?;
            Ok(false)
        } else {
            show_floating_noactivate(&win)?;
            Ok(true)
        }
    } else {
        ensure_floating_window(app)?;
        Ok(true)
    }
}

#[tauri::command]
pub fn show_floating_window(app: AppHandle) -> Result<(), String> {
    if let Some(win) = app.get_webview_window(FLOATING_WINDOW_LABEL) {
        show_floating_noactivate(&win)?;
        return Ok(());
    }
    show_floating_window_built(&app)
}

#[tauri::command]
pub fn hide_floating_window(app: AppHandle) -> Result<(), String> {
    if let Some(win) = app.get_webview_window(FLOATING_WINDOW_LABEL) {
        let _ = win.hide();
    }
    Ok(())
}

#[tauri::command]
pub fn toggle_floating_window(app: AppHandle) -> Result<bool, String> {
    toggle_floating_window_for(&app)
}

#[tauri::command]
pub fn is_floating_window_visible(app: AppHandle) -> Result<bool, String> {
    if let Some(win) = app.get_webview_window(FLOATING_WINDOW_LABEL) {
        Ok(win.is_visible().unwrap_or(false))
    } else {
        Ok(false)
    }
}

#[tauri::command]
pub fn move_floating_window_delta(app: AppHandle, delta_x: f64, delta_y: f64) -> Result<(), String> {
    if let Some(win) = app.get_webview_window(FLOATING_WINDOW_LABEL) {
        if let Ok(pos) = win.outer_position() {
            let _ = win.set_position(tauri::Position::Physical(tauri::PhysicalPosition::new(
                pos.x + delta_x as i32,
                pos.y + delta_y as i32,
            )));
        }
    }
    Ok(())
}

#[tauri::command]
pub fn import_winuhid_dll(path: String) -> Result<bool, String> {
    crate::remote_key::import_dll(&path)
}

/// 悬浮窗独立启停：不依赖主窗口，按 server.json 上次配置启停。
/// 注意：USB 模式的 adb 端口转发仍由主窗口流程负责，此处只起服务。
#[tauri::command]
pub async fn toggle_streaming(state: State<'_, ServerState>) -> Result<String, String> {
    let running = {
        let lifecycle = state.lifecycle.lock().await;
        matches!(lifecycle.phase(), ServerLifecyclePhase::Running)
    };
    if running {
        stop_server(&state).await
    } else {
        let result = start_with_saved_prefs(&state).await;
        // 显式启动顺带唤醒手机（尽力而为，不影响启动结果）。
        if result.is_ok() {
            crate::wake::send_wake_to_last();
        }
        result
    }
}

/// 按 server.json 存档配置起服务（唤醒监听与悬浮开关共用）。
pub async fn start_with_saved_prefs(state: &ServerState) -> Result<String, String> {
    let request = StartRequest::resolve(None, None::<&str>, None, None)?;
    start_server(state, request).await
}

/// 向手机发唤醒包（GUI 显式启动成功后调用；悬浮走后端已内置）。
#[tauri::command]
pub fn send_wake() -> Result<String, String> {
    let n = crate::wake::send_wake_to_last();
    if n == 0 {
        Err("wake send failed".into())
    } else {
        Ok(format!("wake sent to {n} target(s)"))
    }
}

/// 悬浮窗发送键：按配置点一次自定义和弦。key 为逗号分隔十进制 VK（如 "165,32"），
/// 兼容旧值 ralt | ralt_space | enter；缺省右Alt+空格。无驱动返回 Err。
#[tauri::command]
pub fn send_remote_key_once(key: Option<String>) -> Result<(), String> {
    let vks: Vec<u16> = match key.as_deref() {
        Some("ralt") => vec![0xA5],
        Some("enter") => vec![0x0D],
        Some("ralt_space") | None => vec![0xA5, 0x20],
        Some(custom) => custom
            .split(',')
            .filter_map(|s| s.trim().parse::<u16>().ok())
            .filter(|vk| *vk <= 0xFF)
            .take(8)
            .collect(),
    };
    if vks.is_empty() {
        return Err("empty chord".into());
    }
    crate::remote_key::tap_vks(&vks)
}

/// 悬浮窗取证日志：前端交互事件直写应用日志（设置页可导出）。
#[tauri::command]
pub fn log_floating(msg: String) -> Result<(), String> {
    log::info!(target: "floating", "{msg}");
    Ok(())
}

/// 悬浮窗设置读取（设置页 + 悬浮窗启动）。
/// 老 ui.json 只有共用 floatingSend 时，click/dblclick 独立键从它迁移。
#[tauri::command]
pub fn get_floating_prefs() -> UiPrefs {
    let mut prefs = config::load_ui_prefs();
    // 兼容旧三段别名：先把 legacy 归一化，再给空的新字段迁移。
    let legacy_norm = normalize_chord(&prefs.floating_send);
    if prefs.floating_send_click.is_empty() || prefs.floating_send_dblclick.is_empty() {
        if prefs.floating_send_click.is_empty() {
            prefs.floating_send_click = legacy_norm.clone();
        }
        if prefs.floating_send_dblclick.is_empty() {
            prefs.floating_send_dblclick = legacy_norm.clone();
        }
    } else if prefs.floating_send_click == "165,32"
        && prefs.floating_send_dblclick == "165,32"
        && legacy_norm != "165,32"
    {
        // 老文件只有共用键被用户改过（如改成回车/右Alt），新字段还是默认值：整体迁移。
        prefs.floating_send_click = legacy_norm.clone();
        prefs.floating_send_dblclick = legacy_norm.clone();
    }
    prefs.floating_send = legacy_norm;
    prefs
}

/// 和弦归一化：兼容旧值 ralt | ralt_space | enter，其余按逗号分隔十进制 VK 解析。
fn normalize_chord(send: &str) -> String {
    match send {
        "ralt" => "165".to_string(),
        "ralt_space" => "165,32".to_string(),
        "enter" => "13".to_string(),
        custom => {
            let vks: Vec<String> = custom
                .split(',')
                .filter_map(|s| s.trim().parse::<u16>().ok())
                .filter(|vk| *vk <= 0xFF)
                .take(8)
                .map(|vk| vk.to_string())
                .collect();
            if vks.is_empty() {
                "165,32".to_string()
            } else {
                vks.join(",")
            }
        }
    }
}

/// 悬浮窗设置保存（action 取值 send | show | toggle | nothing，
/// send 键取值 ralt | ralt_space | enter 或逗号分隔 VK，非法回退默认）。
/// 单击/双击发送键独立：send_click / send_dblclick 优先，老客户端只传 send 时双键共用它。
#[tauri::command]
pub fn set_floating_prefs(
    visible: bool,
    click: String,
    dblclick: String,
    send: Option<String>,
    send_click: Option<String>,
    send_dblclick: Option<String>,
) -> Result<(), String> {
    fn norm(v: &str, fallback: &str) -> String {
        match v {
            "send" | "show" | "toggle" | "nothing" => v.to_string(),
            _ => fallback.to_string(),
        }
    }
    let legacy = send.as_deref().unwrap_or("165,32");
    let click_raw = send_click.as_deref().unwrap_or(legacy);
    let dblclick_raw = send_dblclick.as_deref().unwrap_or(legacy);
    let click_norm = normalize_chord(click_raw);
    let dblclick_norm = normalize_chord(dblclick_raw);
    let mut prefs = config::load_ui_prefs();
    prefs.floating_visible = visible;
    prefs.floating_click = norm(&click, "send");
    prefs.floating_dblclick = norm(&dblclick, "toggle");
    prefs.floating_send = click_norm.clone();
    prefs.floating_send_click = click_norm;
    prefs.floating_send_dblclick = dblclick_norm;
    config::save_ui_prefs(&prefs)
}
