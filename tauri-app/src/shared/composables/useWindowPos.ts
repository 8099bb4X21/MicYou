import { onMounted, onUnmounted } from "vue";
import { currentMonitor } from "@tauri-apps/api/window";
import { getCurrentWebviewWindow } from "@tauri-apps/api/webviewWindow";
import type { UnlistenFn } from "@tauri-apps/api/event";
import { PhysicalPosition } from "@tauri-apps/api/dpi";

/**
 * 窗口位置记忆：关闭/移动时存 localStorage，打开时恢复并按当前显示器钳制，
 * 换分辨率后不会跑出屏幕。每窗口独立 key（主窗/悬浮窗各存各的）。
 */
export function useWindowPos(storageKey: string) {
  const appWindow = getCurrentWebviewWindow();
  let unlistenMove: UnlistenFn | null = null;

  async function clampToVisible(
    x: number,
    y: number,
  ): Promise<PhysicalPosition> {
    try {
      const [size, mon] = await Promise.all([
        appWindow.outerSize(),
        currentMonitor(),
      ]);
      const mw = mon?.size.width ?? 1920;
      const mh = mon?.size.height ?? 1080;
      const mx = mon?.position.x ?? 0;
      const my = mon?.position.y ?? 0;
      const cx = Math.min(
        Math.max(Math.round(x), mx),
        Math.max(mx, mx + mw - size.width),
      );
      const cy = Math.min(
        Math.max(Math.round(y), my),
        Math.max(my, my + mh - size.height),
      );
      return new PhysicalPosition(cx, cy);
    } catch {
      return new PhysicalPosition(Math.round(x), Math.round(y));
    }
  }

  onMounted(async () => {
    try {
      const raw = localStorage.getItem(storageKey);
      if (raw) {
        const p = JSON.parse(raw) as { x: number; y: number };
        if (Number.isFinite(p.x) && Number.isFinite(p.y)) {
          await appWindow.setPosition(await clampToVisible(p.x, p.y));
        }
      }
    } catch {
      /* 无存档或显示器不可用时保持默认位置 */
    }
    try {
      unlistenMove = await appWindow.onMoved(
        ({ payload }: { payload: PhysicalPosition }) => {
        try {
          localStorage.setItem(
            storageKey,
            JSON.stringify({ x: payload.x, y: payload.y }),
          );
        } catch {
          /* 忽略存储失败 */
        }
      });
    } catch {
      /* 监听失败不影响主流程 */
    }
  });

  onUnmounted(() => {
    if (unlistenMove) unlistenMove();
  });
}
