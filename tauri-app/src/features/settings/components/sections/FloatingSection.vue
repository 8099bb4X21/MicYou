<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { command, emitToWindow } from '@/platform';
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/shared/components/ui/select';
import MD3Switch from '@/shared/components/ui/switch/MD3Switch.vue';
import SettingRow from '../SettingRow.vue';

const { t } = useI18n();

// 悬浮窗偏好（后端 ui.json）
const floatingVisible = ref(true);
const floatingClick = ref('send');
const floatingDblclick = ref('toggle');
const floatingSendClick = ref('165,32');
const floatingSendDblclick = ref('165,32');
const floatingSendClickChord = ref<number[]>([165, 32]);
const floatingSendDblclickChord = ref<number[]>([165, 32]);
let floatingLoaded = false;

const CHORD_OPTIONS = [
  { vk: 162, labelKey: 'settings.floating.keyLCtrl' },
  { vk: 163, labelKey: 'settings.floating.keyRCtrl' },
  { vk: 164, labelKey: 'settings.floating.keyLAlt' },
  { vk: 165, labelKey: 'settings.floating.keyRAlt' },
  { vk: 91, labelKey: 'settings.floating.keyLWin' },
  { vk: 92, labelKey: 'settings.floating.keyRWin' },
  { vk: 32, labelKey: 'settings.floating.keySpace' },
  { vk: 13, labelKey: 'settings.floating.keyEnter' },
];

function parseChord(raw: string): number[] {
  const valid = new Set(CHORD_OPTIONS.map((o) => o.vk));
  return raw
    .split(',')
    .map((s) => Number(s.trim()))
    .filter((n) => Number.isInteger(n) && valid.has(n))
    .filter((n, i, a) => a.indexOf(n) === i)
    .slice(0, 8);
}

function toggleChordVk(vk: number, which: 'click' | 'dblclick') {
  const target =
    which === 'click' ? floatingSendClickChord : floatingSendDblclickChord;
  const cur = target.value.slice();
  const i = cur.indexOf(vk);
  if (i >= 0) {
    if (cur.length <= 1) return; // 至少保留一个键
    cur.splice(i, 1);
  } else {
    cur.push(vk);
  }
  target.value = cur;
  if (which === 'click') {
    floatingSendClick.value = cur.join(',');
  } else {
    floatingSendDblclick.value = cur.join(',');
  }
  void saveFloatingPrefs();
}

async function loadFloatingPrefs() {
  try {
    const p = await command('get_floating_prefs');
    floatingVisible.value = p.floatingVisible;
    floatingClick.value = p.floatingClick || 'send';
    floatingDblclick.value = p.floatingDblclick || 'toggle';
    // 老版本只有共用 floatingSend，新版 click/dblclick 独立，缺失时从共用键迁移。
    const legacy = p.floatingSend || '165,32';
    floatingSendClick.value = p.floatingSendClick || legacy;
    floatingSendDblclick.value = p.floatingSendDblclick || legacy;
    floatingSendClickChord.value = parseChord(floatingSendClick.value);
    floatingSendDblclickChord.value = parseChord(floatingSendDblclick.value);
    if (floatingSendClickChord.value.length === 0) {
      floatingSendClickChord.value = [165, 32];
      floatingSendClick.value = '165,32';
    }
    if (floatingSendDblclickChord.value.length === 0) {
      floatingSendDblclickChord.value = [165, 32];
      floatingSendDblclick.value = '165,32';
    }
    floatingLoaded = true;
  } catch (e) {
    console.error('get_floating_prefs failed:', e);
  }
}

async function saveFloatingPrefs() {
  if (!floatingLoaded) return;
  try {
    await command('set_floating_prefs', {
      visible: floatingVisible.value,
      click: floatingClick.value,
      dblclick: floatingDblclick.value,
      send: floatingSendClick.value,
      sendClick: floatingSendClick.value,
      sendDblclick: floatingSendDblclick.value,
    });
    await emitToWindow('floating-window', 'floating-prefs-changed', {
      floatingVisible: floatingVisible.value,
      floatingClick: floatingClick.value,
      floatingDblclick: floatingDblclick.value,
      floatingSend: floatingSendClick.value,
      floatingSendClick: floatingSendClick.value,
      floatingSendDblclick: floatingSendDblclick.value,
    });
    if (!floatingVisible.value) {
      await command('hide_floating_window').catch(() => {});
    } else {
      // 关掉再打开只能走托盘；设置里打开必须立即生效。
      await command('show_floating_window').catch(() => {});
    }
  } catch (e) {
    console.error('set_floating_prefs failed:', e);
  }
}

onMounted(loadFloatingPrefs);
</script>
<template>
  <div class="space-y-4">
    <SettingRow :title="t('settings.floating.title')" :desc="t('settings.floating.desc')">
      <MD3Switch v-model="floatingVisible" @update:model-value="saveFloatingPrefs()" />
    </SettingRow>

    <div class="bg-surface-bright/60 backdrop-blur-lg rounded-2xl p-4 shadow-xs border border-white/5">
      <div class="grid grid-cols-2 gap-3">
        <div>
          <p class="mb-1 text-xs font-semibold text-on-surface-variant">
            {{ t('settings.floating.clickLabel') }}
          </p>
          <Select v-model="floatingClick" @update:model-value="saveFloatingPrefs()">
            <SelectTrigger
              class="w-full bg-surface-container border-none shadow-none rounded-lg text-sm font-medium"
            >
              <SelectValue placeholder="Click" />
            </SelectTrigger>
            <SelectContent class="border-surface-variant/20 rounded-lg bg-surface shadow-lg">
              <SelectGroup>
                <SelectItem value="send">{{ t('settings.floating.actionSend') }}</SelectItem>
                <SelectItem value="toggle">{{ t('settings.floating.actionToggle') }}</SelectItem>
                <SelectItem value="show">{{ t('settings.floating.actionShow') }}</SelectItem>
                <SelectItem value="nothing">{{ t('settings.floating.actionNothing') }}</SelectItem>
              </SelectGroup>
            </SelectContent>
          </Select>
          <div v-if="floatingClick === 'send'" class="mt-2">
            <p class="mb-1 text-xs font-semibold text-on-surface-variant">
              {{ t('settings.floating.sendClickKeyLabel') }}
            </p>
            <div class="flex flex-wrap gap-1.5">
              <button
                v-for="opt in CHORD_OPTIONS"
                :key="'c' + opt.vk"
                class="rounded-lg px-2.5 py-1.5 text-xs font-semibold transition-all active:scale-95"
                :class="
                  floatingSendClickChord.includes(opt.vk)
                    ? 'bg-primary text-on-primary shadow'
                    : 'bg-surface-container text-on-surface-variant hover:bg-surface-variant'
                "
                @click="toggleChordVk(opt.vk, 'click')"
              >
                {{ t(opt.labelKey) }}
              </button>
            </div>
          </div>
        </div>

        <div>
          <p class="mb-1 text-xs font-semibold text-on-surface-variant">
            {{ t('settings.floating.dblclickLabel') }}
          </p>
          <Select v-model="floatingDblclick" @update:model-value="saveFloatingPrefs()">
            <SelectTrigger
              class="w-full bg-surface-container border-none shadow-none rounded-lg text-sm font-medium"
            >
              <SelectValue placeholder="Double-click" />
            </SelectTrigger>
            <SelectContent class="border-surface-variant/20 rounded-lg bg-surface shadow-lg">
              <SelectGroup>
                <SelectItem value="toggle">{{ t('settings.floating.actionToggle') }}</SelectItem>
                <SelectItem value="send">{{ t('settings.floating.actionSend') }}</SelectItem>
                <SelectItem value="show">{{ t('settings.floating.actionShow') }}</SelectItem>
                <SelectItem value="nothing">{{ t('settings.floating.actionNothing') }}</SelectItem>
              </SelectGroup>
            </SelectContent>
          </Select>
          <div v-if="floatingDblclick === 'send'" class="mt-2">
            <p class="mb-1 text-xs font-semibold text-on-surface-variant">
              {{ t('settings.floating.sendDblclickKeyLabel') }}
            </p>
            <div class="flex flex-wrap gap-1.5">
              <button
                v-for="opt in CHORD_OPTIONS"
                :key="'d' + opt.vk"
                class="rounded-lg px-2.5 py-1.5 text-xs font-semibold transition-all active:scale-95"
                :class="
                  floatingSendDblclickChord.includes(opt.vk)
                    ? 'bg-primary text-on-primary shadow'
                    : 'bg-surface-container text-on-surface-variant hover:bg-surface-variant'
                "
                @click="toggleChordVk(opt.vk, 'dblclick')"
              >
                {{ t(opt.labelKey) }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
