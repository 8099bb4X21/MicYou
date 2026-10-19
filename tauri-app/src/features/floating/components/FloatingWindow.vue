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
      <button @click="menuSwitchCli">{{ t('tray.switchCli') }}</button>
      <button @click="menuSwitchTui">{{ t('tray.switchTui') }}</button>
      <button @click="menuExit">{{ t('tray.exit') }}</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { appWindow, command, emitToWindow, onEvent } from '@/platform';
import { useTheme } from '@/features/theme/composables/useTheme';
import { useWindowPos } from '@/features/tray/useWindowPos';
import { useListeners } from '@/shared/lib/listeners';

// 主题同步 + 位置记忆（localStorage + 显示器钳制）
useTheme();
useWindowPos('micyou_floating_pos');

const { t } = useI18n();
const track = useListeners();

// 取证日志：写后端日志（设置页可导出），方便定位浮窗交互问题。
function ftrace(msg: string) {
  void command('log_floating', { msg }).catch(() => {});
  console.log('[floating]', msg);
}

const isMuted = ref(false);
const isStreaming = ref(false);

// 动作配置（设置页下发，默认单击发送、双击启停；单击/双击发送键可配不同按键）。
const clickAction = ref('send');
const dblclickAction = ref('toggle');
const sendClickKey = ref('165,32');
const sendDblclickKey = ref('165,32');

function applyPrefs(p: {
  floatingClick?: string;
  floatingDblclick?: string;
  floatingSend?: string;
  floatingSendClick?: string;
  floatingSendDblclick?: string;
}) {
  clickAction.value = p.floatingClick || 'send';
  dblclickAction.value = p.floatingDblclick || 'toggle';
  const legacy = p.floatingSend || '165,32';
  sendClickKey.value = p.floatingSendClick || legacy;
  sendDblclickKey.value = p.floatingSendDblclick || legacy;
}

async function loadFloatingPrefs() {
  try {
    applyPrefs(await command('get_floating_prefs'));
    ftrace(
      `floating prefs: click=${clickAction.value} dbl=${dblclickAction.value} clickKey=${sendClickKey.value} dblclickKey=${sendDblclickKey.value}`,
    );
  } catch (e) {
    console.error('get_floating_prefs failed:', e);
  }
}

// 右键菜单状态
const menuOpen = ref(false);
const menuX = ref(0);
const menuY = ref(0);

// 单击/双击消歧（250ms）
let clickTimer: ReturnType<typeof setTimeout> | null = null;

// 指针拖拽与点击处理
let isPointerDown = false;
let hasDragged = false;
let startScreenX = 0;
let startScreenY = 0;
let initialWinX = 0;
let initialWinY = 0;

async function handlePointerDown(e: PointerEvent) {
  if (e.button !== 0) return;
  // 菜单开着时：菜单内走按钮自身 click，菜单外只关菜单，都不计入单击。
  if (menuOpen.value) {
    if (!(e.target as HTMLElement).closest?.('.floating-menu')) {
      void closeMenu();
    }
    return;
  }
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
  } catch {
    /* 捕获失败不阻断交互 */
  }
}

async function handlePointerMove(e: PointerEvent) {
  if (!isPointerDown) return;
  const dx = e.screenX - startScreenX;
  const dy = e.screenY - startScreenY;

  if (!hasDragged && (Math.abs(dx) > 4 || Math.abs(dy) > 4)) {
    hasDragged = true;
    ftrace('drag start');
    // 先放掉指针捕获再交 OS 拖拽，否则 mouse-up 被吞，下一次点击被当成拖拽收尾。
    try {
      (e.currentTarget as HTMLElement).releasePointerCapture(e.pointerId);
    } catch {
      /* 未捕获时忽略 */
    }
    try {
      await appWindow.startDragging();
      ftrace('drag startDragging ok');
      return;
    } catch (err) {
      ftrace(`drag startDragging failed: ${String(err)}`);
      // 兜底：改用 setPosition 手动跟随
    }
  }

  // 兜底：若 OS 拖拽吞掉了 mouse-up，此后无按键的移动直接收尾，避免窗跟光标跑。
  if (hasDragged) {
    if (e.buttons === 0) {
      isPointerDown = false;
      hasDragged = false;
      ftrace('drag auto-release (no buttons)');
      return;
    }
    const scale = window.devicePixelRatio || 1;
    try {
      await appWindow.setPosition(
        initialWinX + dx * scale,
        initialWinY + dy * scale,
      );
    } catch {
      /* 位置设置失败忽略 */
    }
  }
}

function handlePointerUp(e: PointerEvent) {
  if (!isPointerDown) return;
  isPointerDown = false;

  const el = e.currentTarget as HTMLElement;
  try {
    el.releasePointerCapture(e.pointerId);
  } catch {
    /* 未捕获时忽略 */
  }

  if (!hasDragged) {
    // 延迟单击以区分双击。
    if (clickTimer) clearTimeout(clickTimer);
    clickTimer = setTimeout(() => {
      clickTimer = null;
      ftrace('single-click fire');
      void doClickAction();
    }, 250);
  } else {
    ftrace('drag end');
  }
  hasDragged = false;
}

async function doClickAction() {
  if (clickAction.value === 'show') {
    command('show_main_window').catch((err) =>
      console.error('show_main_window failed:', err),
    );
  } else if (clickAction.value === 'toggle') {
    doToggleStream();
  } else if (clickAction.value === 'nothing') {
    ftrace('click action: nothing');
  } else {
    await sendKeyOnce(sendClickKey.value);
  }
}

function doToggleStream() {
  command('toggle_streaming')
    .then((r) => {
      ftrace(`toggle_streaming ok: ${r}`);
      void emitToWindow('main', 'floating-toggled', null);
    })
    .catch((err) => {
      console.error('toggle_streaming failed:', err);
      ftrace(`toggle_streaming failed: ${String(err)}`);
    });
}

function handleDoubleClick() {
  ftrace('double-click fire');
  if (clickTimer) {
    clearTimeout(clickTimer);
    clickTimer = null;
  }
  void closeMenu();
  if (dblclickAction.value === 'show') {
    command('show_main_window').catch((err) =>
      console.error('show_main_window failed:', err),
    );
  } else if (dblclickAction.value === 'send') {
    void sendKeyOnce(sendDblclickKey.value);
  } else if (dblclickAction.value === 'nothing') {
    ftrace('dblclick action: nothing');
  } else {
    doToggleStream();
  }
}

/** 单击/双击的「发送按键」：点一次配置好的和弦。 */
async function sendKeyOnce(key: string) {
  try {
    await command('send_remote_key_once', { key });
    ftrace(`send key ok: ${key}`);
  } catch (e) {
    console.error('send_remote_key_once failed:', e);
    // 让主窗弹出驱动缺失引导。
    void emitToWindow('main', 'remote-key-driver-missing', null);
  }
}

async function handleContextMenu(e: MouseEvent) {
  // 双保险：修饰符 + 显式调用，确保原生窗口菜单被压住。
  e.preventDefault();
  e.stopPropagation();
  ftrace('contextmenu fire');
  // currentTarget 在 await 后会变 null，先同步取好矩形。
  const rect = (e.currentTarget as HTMLElement | null)?.getBoundingClientRect();
  const baseX = rect ? e.clientX - rect.left : e.clientX;
  const baseY = rect ? e.clientY - rect.top : e.clientY;
  if (clickTimer) {
    clearTimeout(clickTimer);
    clickTimer = null;
  }
  // 刷新串流标志，决定菜单里显示「开始」还是「停止」。
  try {
    const status = await command('get_streaming_status');
    isStreaming.value = status.isConnected || status.isServerRunning;
  } catch {
    /* 状态刷新失败沿用旧值 */
  }
  // 放大窗口容纳菜单，关闭时恢复 80x80。
  try {
    await appWindow.setLogicalSize(220, 300);
    const outer = await appWindow.outerSize();
    const inner = await appWindow.innerSize();
    ftrace(
      `menu resize ok outer=${outer.width}x${outer.height} inner=${inner.width}x${inner.height}`,
    );
  } catch (e) {
    ftrace(`menu resize failed: ${String(e)}`);
  }
  menuX.value = Math.min(Math.max(0, baseX), 220 - 180);
  menuY.value = Math.min(Math.max(0, baseY), 300 - 220);
  menuOpen.value = true;
  ftrace(`menu open at ${menuX.value},${menuY.value}`);
  // DOM 自检：菜单是否真的渲染出来了。
  requestAnimationFrame(() => {
    requestAnimationFrame(() => {
      const el = document.querySelector('.floating-menu') as HTMLElement | null;
      if (!el) {
        ftrace('menu el: null (not rendered)');
        return;
      }
      const r = el.getBoundingClientRect();
      const cs = getComputedStyle(el);
      ftrace(
        `menu el: ${r.width}x${r.height} at ${r.left},${r.top} display=${cs.display} visibility=${cs.visibility} opacity=${cs.opacity}`,
      );
    });
  });
}

async function closeMenu() {
  if (!menuOpen.value) return;
  menuOpen.value = false;
  try {
    await appWindow.setLogicalSize(80, 80);
    ftrace('menu restore ok');
  } catch (e) {
    ftrace(`menu restore failed: ${String(e)}`);
  }
}

async function menuShow() {
  await closeMenu();
  command('show_main_window').catch((err) =>
    console.error('show_main_window failed:', err),
  );
}

async function menuToggleStream() {
  await closeMenu();
  command('toggle_streaming')
    .then((r) => {
      ftrace(`menu toggle_streaming ok: ${r}`);
      void emitToWindow('main', 'floating-toggled', null);
    })
    .catch((err) => {
      console.error('toggle_streaming failed:', err);
      ftrace(`menu toggle_streaming failed: ${String(err)}`);
    });
}

async function menuSwitchCli() {
  await closeMenu();
  emitToWindow('main', 'tray-action', 'switch_cli').catch((err) =>
    console.error('switch_cli failed:', err),
  );
}

async function menuSwitchTui() {
  await closeMenu();
  emitToWindow('main', 'tray-action', 'switch_tui').catch((err) =>
    console.error('switch_tui failed:', err),
  );
}

async function menuExit() {
  await closeMenu();
  command('exit_app').catch((err) => console.error('exit_app failed:', err));
}

function handleKeyDown(e: KeyboardEvent) {
  if (e.key === 'Escape') void closeMenu();
}

onMounted(async () => {
  await track(
    onEvent('mute-state-changed', (muted) => {
      isMuted.value = muted;
    }),
  );
  await track(
    onEvent('floating-prefs-changed', (prefs) => {
      applyPrefs(prefs);
      ftrace(
        `floating prefs live: click=${clickAction.value} dbl=${dblclickAction.value} clickKey=${sendClickKey.value} dblclickKey=${sendDblclickKey.value}`,
      );
    }),
  );
  await loadFloatingPrefs();
  await track(
    onEvent('device-connected', () => {
      isStreaming.value = true;
    }),
  );
  await track(
    onEvent('device-disconnected', () => {
      isStreaming.value = false;
    }),
  );
  await track(
    onEvent('server-stopped', () => {
      isStreaming.value = false;
    }),
  );
  try {
    const status = await command('get_streaming_status');
    isMuted.value = status.isMuted;
    isStreaming.value = status.isConnected || status.isServerRunning;
  } catch (e) {
    console.error('get_streaming_status failed:', e);
  }
  window.addEventListener('keydown', handleKeyDown);
  window.onerror = (message) => {
    ftrace(`window.onerror: ${String(message)}`);
  };
  window.onunhandledrejection = (event) => {
    ftrace(`unhandledrejection: ${String(event.reason)}`);
  };
  ftrace('floating mounted');
});

onUnmounted(() => {
  if (clickTimer) clearTimeout(clickTimer);
  window.removeEventListener('keydown', handleKeyDown);
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
  width: 52px;
  height: 52px;
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
