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
      <!-- Base Background Contrast Disc -->
      <circle cx="20" cy="20" r="18" fill="#0f172a" fill-opacity="0.92" />

      <!-- 1. Muted State: Error Red Ring & Diagonal Slash -->
      <g v-if="isMuted">
        <circle
          cx="20"
          cy="20"
          r="15"
          fill="none"
          stroke="#ef4444"
          stroke-width="3"
          stroke-opacity="0.95"
        />
        <line
          x1="9.5"
          y1="9.5"
          x2="30.5"
          y2="30.5"
          stroke="#ef4444"
          stroke-width="3"
          stroke-linecap="round"
        />
      </g>

      <!-- 2. Active Volume Ring (Clean ring, no extra dots or wave bars) -->
      <g v-else>
        <!-- Background Track Ring (Theme Primary 25% Opacity) -->
        <circle
          cx="20"
          cy="20"
          r="15"
          fill="none"
          stroke="currentColor"
          class="text-primary"
          stroke-width="3"
          stroke-opacity="0.25"
        />
        <!-- Dynamic Volume Arc (Starts at top -90deg, sweeps clockwise) -->
        <circle
          v-if="safeAudioLevel > 0.005"
          cx="20"
          cy="20"
          r="15"
          fill="none"
          stroke="currentColor"
          class="text-primary"
          stroke-width="3"
          stroke-linecap="round"
          :stroke-dasharray="94.25"
          :stroke-dashoffset="94.25 * (1 - safeAudioLevel)"
          transform="rotate(-90 20 20)"
        />
      </g>
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
import { ref, computed, onMounted, onUnmounted } from 'vue';
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

const targetAudioLevel = ref(0);
const smoothAudioLevel = ref(0);
const isMuted = ref(false);
const isStreaming = ref(false);

// Right-click menu state
const menuOpen = ref(false);
const menuX = ref(0);
const menuY = ref(0);

// Single/double click disambiguation (250ms)
let clickTimer: ReturnType<typeof setTimeout> | null = null;

const safeAudioLevel = computed(() => Math.max(0, Math.min(1, smoothAudioLevel.value)));

let unlistenAudioLevel: UnlistenFn | null = null;
let unlistenMute: UnlistenFn | null = null;
let unlistenDeviceConnected: UnlistenFn | null = null;
let unlistenDeviceDisconnected: UnlistenFn | null = null;
let unlistenServerStopped: UnlistenFn | null = null;

let animationId = 0;

// Pointer Dragging & Click Handling
let isPointerDown = false;
let hasDragged = false;
let startScreenX = 0;
let startScreenY = 0;
let initialWinX = 0;
let initialWinY = 0;

async function handlePointerDown(e: PointerEvent) {
  if (e.button !== 0) return;
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
    try {
      await appWindow.startDragging();
      return;
    } catch {
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
      void sendKeyOnce();
    }, 250);
  }
  hasDragged = false;
}

function handleDoubleClick() {
  if (clickTimer) {
    clearTimeout(clickTimer);
    clickTimer = null;
  }
  closeMenu();
  // Reuse main-window toggle logic (settings context lives there).
  emit('tray-action', 'toggle_stream').catch((err) => console.error('toggle_stream failed:', err));
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
  } catch {}
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
  emit('tray-action', 'toggle_stream').catch((err) => console.error('toggle_stream failed:', err));
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

function animate() {
  // Smooth lerp tracking
  const diff = targetAudioLevel.value - smoothAudioLevel.value;
  smoothAudioLevel.value += diff * 0.2;
  animationId = requestAnimationFrame(animate);
}

interface StreamingStatus {
  isServerRunning: boolean;
  isConnected: boolean;
  isMuted: boolean;
}

onMounted(async () => {
  unlistenAudioLevel = await listen<number>('audio-level', (event) => {
    targetAudioLevel.value = Math.min(1, Math.max(0, event.payload / 100));
  });

  unlistenMute = await listen<boolean>('mute-state-changed', (event) => {
    isMuted.value = event.payload;
  });

  unlistenDeviceConnected = await listen('device-connected', () => {
    isStreaming.value = true;
  });

  unlistenDeviceDisconnected = await listen('device-disconnected', () => {
    isStreaming.value = false;
    targetAudioLevel.value = 0;
    smoothAudioLevel.value = 0;
  });

  unlistenServerStopped = await listen('server-stopped', () => {
    isStreaming.value = false;
    targetAudioLevel.value = 0;
    smoothAudioLevel.value = 0;
  });

  try {
    const status = await invoke<StreamingStatus>('get_streaming_status');
    isMuted.value = status.isMuted;
    isStreaming.value = status.isConnected || status.isServerRunning;
  } catch (e) {
    console.error('get_streaming_status failed:', e);
  }

  window.addEventListener('keydown', handleKeyDown);
  animationId = requestAnimationFrame(animate);
});

onUnmounted(() => {
  if (animationId) cancelAnimationFrame(animationId);
  if (clickTimer) clearTimeout(clickTimer);
  window.removeEventListener('keydown', handleKeyDown);
  if (unlistenAudioLevel) unlistenAudioLevel();
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
