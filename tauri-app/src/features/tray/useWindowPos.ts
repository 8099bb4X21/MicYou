import { onMounted, onUnmounted } from "vue";
import type { UnlistenFn } from "@/platform";
import { currentMonitor, onMoved, outerSize, setPosition } from "@/platform";

/**
 * 窗口位置记忆：关闭/移动时存 localStorage，打开时恢复并按当前显示器钳制，
 * 换分辨率后不会跑出屏幕。每窗口独立 key（主窗/悬浮窗各存各的）。
 */
export function useWindowPos(storageKey: string) {
  let unlistenMove: UnlistenFn | null = null;

  /** 把坐标钳制到当前显示器可视范围内（换分辨率/拔显示器后不跑出屏幕）。 */
  async function clampToVisible(
    x: number,
    y: number,
  ): Promise<{ x: number; y: number }> {
    try {
      const [size, mon] = await Promise.all([outerSize(), currentMonitor()]);
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
      return { x: cx, y: cy };
    } catch {
      return { x: Math.round(x), y: Math.round(y) };
    }
  }

  onMounted(async () => {
    try {
      const raw = localStorage.getItem(storageKey);
      if (raw) {
        const p = JSON.parse(raw) as { x: number; y: number };
        if (Number.isFinite(p.x) && Number.isFinite(p.y)) {
          const pos = await clampToVisible(p.x, p.y);
          await setPosition(pos.x, pos.y);
        }
      }
    } catch {
      /* 无存档或显示器不可用时保持默认位置 */
    }
    try {
      unlistenMove = await onMoved((pos) => {
        try {
          localStorage.setItem(
            storageKey,
            JSON.stringify({ x: pos.x, y: pos.y }),
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