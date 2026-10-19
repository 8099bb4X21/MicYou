import { emitTo, listen, type UnlistenFn } from '@tauri-apps/api/event';
import type { AecStatus, AudioMetrics, DeviceInfo, PluginDownloadProgress, SpectrumPayload } from './types';
import type { FloatingPrefs } from './commands';

export type { UnlistenFn };

/** Events the backend (or another window) emits, with their payloads. */
export interface AppEvents {
  'device-connected': DeviceInfo;
  'device-disconnected': unknown;
  'server-stopped': unknown;
  'audio-level': number;
  'audio-metrics': AudioMetrics;
  'audio-spectrum': SpectrumPayload;
  'aec-status-changed': AecStatus;
  'mute-state-changed': boolean;
  'monitoring-enabled-changed': boolean;
  'web-client-count': number;
  udp_audio_warning: unknown;
  'vbcable-install-progress': string;
  'plugin-download-progress': PluginDownloadProgress;
  'tray-action': string;
  /** Backend → settings window when the hidden window is shown again. */
  'settings-window-shown': unknown;
  /** Settings window → main window when the output device changes. */
  'output-device-changed': string;
  /** fork：设置页保存后广播给悬浮窗的偏好。 */
  'floating-prefs-changed': FloatingPrefs;
  /** fork：悬浮窗/远程按键链路发现驱动缺失，通知主窗弹引导。 */
  'remote-key-driver-missing': unknown;
  /** fork：悬浮窗触发启停后的通知（主窗/设置刷新状态）。 */
  'floating-toggled': unknown;
}

export type EventName = keyof AppEvents;

export function onEvent<K extends EventName>(
  name: K,
  handler: (payload: AppEvents[K]) => void,
): Promise<UnlistenFn> {
  return listen<AppEvents[K]>(name, (event) => handler(event.payload));
}

export function emitToWindow<K extends EventName>(
  label: string,
  name: K,
  payload: AppEvents[K],
): Promise<void> {
  return emitTo(label, name, payload);
}
