<template>
  <div
    class="floating-container"
    @pointerdown="handlePointerDown"
    @pointermove="handlePointerMove"
    @pointerup="handlePointerUp"
    @pointercancel="handlePointerUp"
    @dblclick="handleDoubleClick"
    @contextmenu.prevent="handleContextMenu"
  >
    <svg
      viewBox="0 0 40 40"
      class="floating-svg"
      xmlns="http://www.w3.org/2000/svg"
    >
      <!-- Static square blue background -->
      <rect x="1" y="1" width="38" height="38" rx="10" fill="#2563eb" fill-opacity="0.92" />
      <!-- Thin white frame, brighter while streaming (static, no animation) -->
      <rect
        x="4.5"
        y="4.5"
        width="31"
        height="31"
        rx="7"
        fill="none"
        stroke="#ffffff"
        :stroke-opacity="isStreaming ? 0.9 : 0.35"
        stroke-width="1.6"
      />
      <!-- Minimalist white microphone -->
      <rect x="17" y="8" width="6" height="12" rx="3" fill="#ffffff" />
      <path
        d="M12.5 18.5 a7.5 7.5 0 0 0 15 0"
        fill="none"
        stroke="#ffffff"
        stroke-width="2.4"
        stroke-linecap="round"
      />
      <line x1="20" y1="26" x2="20" y2="30.5" stroke="#ffffff" stroke-width="2.4" stroke-linecap="round" />
      <line x1="16" y1="30.5" x2="24" y2="30.5" stroke="#ffffff" stroke-width="2.4" stroke-linecap="round" />
    </svg>
    <!-- Right-click menu (same items as tray) -->
    <div v-if="menuOpen" class="floating-menu" :style="{ left: menuX + 'px', top: menuY + 'px' }">
      <button @click="menuShow">{{ t('tray.show') }}</button>
      <button @click="menuToggleStream">{{ isStreaming ? t('tray.stop') : t('tray.start') }}</button>
      <button @click="menuSendKey">{{ t('tray.sendKey') }}</button>
      <button @click="menuSwitchCli">{{ t('tray.switchCli') }}</button>
      <button @click="menuSwitchTui">{{ t('tray.switchTui') }}</button>
      <button @click="menuExit">{{ t('tray.exit') }}</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue';
import { invoke } from '@tauri-apps/api/core';
import { listen, emit, type UnlistenFn } from '@tauri-apps/api/event';
import { getCurrentWebviewWindow } from '@tauri-apps/api/webviewWindow';
import { PhysicalPosition, LogicalSize } from '@tauri-apps/api/dpi';
import { useI18n } from 'vue-i18n';
import { useTheme } from '@/features/theme/composables/useTheme';

// Activate theme synchronization for the floating window webview
useTheme();

const { t } = useI18n();
const appWindow = getCurrentWebviewWindow();

// Fire-and-forget frontend trace (backend log, exportable via settings).
function ftrace(msg: string) {
  invoke('log_floating', { msg }).catch(() => {});
  console.log('[floating]', msg);
}

const isMuted = ref(false);
const isStreaming = ref(false);

// Right-click menu state
const menuOpen = ref(false);
const menuX = ref(0);
const menuY = ref(0);

// Single/double click disambiguation (250ms)
let clickTimer: ReturnType<typeof setTimeout> | null = null;

let unlistenMute: UnlistenFn | null = null;
let unlistenDeviceConnected: UnlistenFn | null = null;
let unlistenDeviceDisconnected: UnlistenFn | null = null;
let unlistenServerStopped: UnlistenFn | null = null;

// Pointer Dragging & Click Handling
let isPointerDown = false;
let hasDragged = false;
let startScreenX = 0;
let startScreenY = 0;
let initialWinX = 0;
let initialWinY = 0;

async function handlePointerDown(e: PointerEvent) {
  if (e.button !== 0) return;
  ftrace(`pointerdown btn=0 x=${e.screenX} y=${e.screenY}`);
  isPointerDown = true;
  hasDragged = false;
  startScreenX = e.screenX;
  startScreenY = e.screenY;

  try {
    const pos = await appWindow.outerPosition();
    initialWinX = pos.x;
    initialWinY = pos.y;
  } catch (err) {
    console.error('get outerPosition failed:', err);
  }

  const el = e.currentTarget as HTMLElement;
  try {
    el.setPointerCapture(e.pointerId);
  } catch {}
}

async function handlePointerMove(e: PointerEvent) {
  if (!isPointerDown) return;
  const dx = e.screenX - startScreenX;
  const dy = e.screenY - startScreenY;

  if (!hasDragged && (Math.abs(dx) > 4 || Math.abs(dy) > 4)) {
    hasDragged = true;
    ftrace('drag start');
    try {
      await appWindow.startDragging();
      ftrace('drag startDragging ok');
      return;
    } catch (e) {
      ftrace(`drag startDragging failed: ${String(e)}`);
      // Fallback to manual setPosition if startDragging is not available
    }
  }

  if (hasDragged) {
    const scale = window.devicePixelRatio || 1;
    const targetX = Math.round(initialWinX + dx * scale);
    const targetY = Math.round(initialWinY + dy * scale);
    try {
      await appWindow.setPosition(new PhysicalPosition(targetX, targetY));
    } catch {}
  }
}

function handlePointerUp(e: PointerEvent) {
  if (!isPointerDown) return;
  isPointerDown = false;

  const el = e.currentTarget as HTMLElement;
  try {
    el.releasePointerCapture(e.pointerId);
  } catch {}

  if (!hasDragged) {
    // Delay single-click to distinguish from double-click.
    if (clickTimer) clearTimeout(clickTimer);
    clickTimer = setTimeout(() => {
      clickTimer = null;
      ftrace('single-click fire');
      void sendKeyOnce();
    }, 250);
  } else {
    ftrace('drag end');
  }
  hasDragged = false;
}

function handleDoubleClick() {
  ftrace('double-click fire');
  if (clickTimer) {
    clearTimeout(clickTimer);
    clickTimer = null;
  }
  closeMenu();
  // 独立启停，不依赖主窗口。
  invoke<string>('toggle_streaming')
    .then((r) => ftrace(`toggle_streaming ok: ${r}`))
    .catch((err) => {
      console.error('toggle_streaming failed:', err);
      ftrace(`toggle_streaming failed: ${String(err)}`);
    });
}

/** Single click = tap RAlt+Space once. */
async function sendKeyOnce() {
  try {
    await invoke('send_remote_key_once');
  } catch (e) {
    console.error('send_remote_key_once failed:', e);
    // Surface the existing driver-missing dialog in the main window.
    emit('remote-key-driver-missing', null).catch(() => {});
  }
}

async function handleContextMenu(e: MouseEvent) {
  // 双保险：修饰符 + 显式调用，确保原生窗口菜单被压住。
  e.preventDefault();
  e.stopPropagation();
  ftrace('contextmenu fire');
  if (clickTimer) {
    clearTimeout(clickTimer);
    clickTimer = null;
  }
  // Refresh streaming flag for the toggle label.
  try {
    const status = await invoke<StreamingStatus>('get_streaming_status');
    isStreaming.value = status.isConnected || status.isServerRunning;
  } catch {}
  // Enlarge the window so the menu fits; restore on close.
  try {
    await appWindow.setSize(new LogicalSize(220, 300));
    ftrace('menu resize ok');
  } catch (e) {
    ftrace(`menu resize failed: ${String(e)}`);
  }
  const rect = (e.currentTarget as HTMLElement).getBoundingClientRect();
  menuX.value = Math.min(e.clientX - rect.left, 220 - 180);
  menuY.value = Math.min(e.clientY - rect.top, 300 - 220);
  menuOpen.value = true;
}

async function closeMenu() {
  if (!menuOpen.value) return;
  menuOpen.value = false;
  try {
    await appWindow.setSize(new LogicalSize(64, 64));
  } catch {}
}

async function menuShow() {
  await closeMenu();
  invoke('show_main_window').catch((err) => console.error('show_main_window failed:', err));
}

async function menuToggleStream() {
  await closeMenu();
  invoke<string>('toggle_streaming').catch((err) => console.error('toggle_streaming failed:', err));
}

async function menuSendKey() {
  await closeMenu();
  await sendKeyOnce();
}

async function menuSwitchCli() {
  await closeMenu();
  emit('tray-action', 'switch_cli').catch((err) => console.error('switch_cli failed:', err));
}

async function menuSwitchTui() {
  await closeMenu();
  emit('tray-action', 'switch_tui').catch((err) => console.error('switch_tui failed:', err));
}

async function menuExit() {
  await closeMenu();
  invoke('exit_app').catch((err) => console.error('exit_app failed:', err));
}

function handleKeyDown(e: KeyboardEvent) {
  if (e.key === 'Escape') void closeMenu();
}

interface StreamingStatus {
  isServerRunning: boolean;
  isConnected: boolean;
  isMuted: boolean;
}

onMounted(async () => {
  unlistenMute = await listen<boolean>('mute-state-changed', (event) => {
    isMuted.value = event.payload;
  });

  unlistenDeviceConnected = await listen('device-connected', () => {
    isStreaming.value = true;
  });

  unlistenDeviceDisconnected = await listen('device-disconnected', () => {
    isStreaming.value = false;
  });

  unlistenServerStopped = await listen('server-stopped', () => {
    isStreaming.value = false;
  });

  try {
    const status = await invoke<StreamingStatus>('get_streaming_status');
    isMuted.value = status.isMuted;
    isStreaming.value = status.isConnected || status.isServerRunning;
  } catch (e) {
    console.error('get_streaming_status failed:', e);
  }

  window.addEventListener('keydown', handleKeyDown);
  window.onerror = (message) => {
    ftrace(`window.onerror: ${String(message)}`);
  };
  ftrace('floating mounted');
});

onUnmounted(() => {
  if (clickTimer) clearTimeout(clickTimer);
  window.removeEventListener('keydown', handleKeyDown);
  if (unlistenMute) unlistenMute();
  if (unlistenDeviceConnected) unlistenDeviceConnected();
  if (unlistenDeviceDisconnected) unlistenDeviceDisconnected();
  if (unlistenServerStopped) unlistenServerStopped();
});
</script>

<style>
/* Global resets for transparent floating window */
html, body, #app {
  background: transparent !important;
  background-color: transparent !important;
  margin: 0 !important;
  padding: 0 !important;
  width: 100% !important;
  height: 100% !important;
  overflow: hidden !important;
  user-select: none !important;
  touch-action: none !important;
}

.floating-container {
  width: 100vw;
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: grab;
  background: transparent;
  overflow: hidden;
  user-select: none;
  touch-action: none;
}

.floating-container:active {
  cursor: grabbing;
}

.floating-svg {
  width: 40px;
  height: 40px;
  display: block;
  user-select: none;
  pointer-events: none;
}

.floating-menu {
  position: absolute;
  z-index: 10;
  min-width: 170px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 6px;
  border-radius: 12px;
  background: rgba(15, 23, 42, 0.96);
  border: 1px solid rgba(255, 255, 255, 0.12);
  box-shadow: 0 8px 28px rgba(0, 0, 0, 0.5);
}

.floating-menu button {
  text-align: left;
  padding: 7px 10px;
  border-radius: 8px;
  font-size: 12px;
  color: #e2e8f0;
  background: transparent;
  border: none;
  cursor: pointer;
  white-space: nowrap;
}

.floating-menu button:hover {
  background: rgba(255, 255, 255, 0.12);
}
</style>
