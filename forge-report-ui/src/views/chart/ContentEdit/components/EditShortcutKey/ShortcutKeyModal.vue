<template>
  <n-modal v-model:show="modelShowRef" :mask-closable="true" @afterLeave="closeHandle">
    <div class="shortcut-modal">
      <div class="shortcut-head">
        <div>
          <span class="shortcut-kicker">SHORTCUTS</span>
          <h3>快捷键</h3>
        </div>
        <n-icon size="20" class="go-cursor-pointer close-btn" @click="closeHandle">
          <close-icon></close-icon>
        </n-icon>
      </div>
      <div class="shortcut-grid">
        <div class="shortcut-row shortcut-row-head">
          <span>功能</span>
          <span>Win</span>
          <span>Mac</span>
        </div>
        <div class="shortcut-row" v-for="(item, index) in shortcutKeyOptions" :key="index">
          <span class="shortcut-label">{{ item.label }}</span>
          <kbd>{{ item.win }}</kbd>
          <kbd v-if="item.macSource">{{ item.mac }}</kbd>
          <kbd v-else>{{ item.mac }}</kbd>
        </div>
      </div>
    </div>
  </n-modal>
</template>

<script setup lang="ts">
import { watch, ref } from 'vue'
import { icon } from '@/plugins'
import { WinKeyboard, MacKeyboard } from '@/enums/editPageEnum'

const { CloseIcon } = icon.ionicons5
const modelShowRef = ref(false)

const emit = defineEmits(['update:modelShow'])

const props = defineProps({
  modelShow: Boolean
})


watch(() => props.modelShow, (newValue) => {
  modelShowRef.value = newValue
})

// 快捷键
const shortcutKeyOptions = [
  {
    label: '拖拽画布',
    win: `${WinKeyboard.SPACE.toUpperCase()} + 🖱️ `,
    mac: `${MacKeyboard.SPACE.toUpperCase()} + 🖱️ `,
    macSource: true
  },
  {
    label: '向 上/右/下/左 移动',
    win: `${WinKeyboard.CTRL.toUpperCase()} + ↑ 或 → 或 ↓ 或 ←`,
    mac: `${MacKeyboard.CTRL.toUpperCase()} + ↑ `
  },
  {
    label: '锁定',
    win: `${WinKeyboard.CTRL.toUpperCase()} + L `,
    mac: `${MacKeyboard.CTRL.toUpperCase()} + L `
  },
  {
    label: '解锁',
    win: `${WinKeyboard.CTRL.toUpperCase()} + ${WinKeyboard.SHIFT.toUpperCase()}+ L `,
    mac: `${MacKeyboard.CTRL.toUpperCase()} + ${MacKeyboard.SHIFT.toUpperCase()} + L `
  },
  {
    label: '展示',
    win: `${WinKeyboard.CTRL.toUpperCase()} + H `,
    mac: `${MacKeyboard.CTRL.toUpperCase()} + H `
  },
  {
    label: '隐藏',
    win: `${WinKeyboard.CTRL.toUpperCase()} + ${WinKeyboard.SHIFT.toUpperCase()} + H `,
    mac: `${MacKeyboard.CTRL.toUpperCase()} + ${MacKeyboard.SHIFT.toUpperCase()} + H `
  },
  {
    label: '删除',
    win: 'Delete'.toUpperCase(),
    mac: `${MacKeyboard.CTRL.toUpperCase()} + Backspace `
  },
  {
    label: '复制',
    win: `${WinKeyboard.CTRL.toUpperCase()} + C `,
    mac: `${MacKeyboard.CTRL.toUpperCase()} + C `
  },
  {
    label: '剪切',
    win: `${WinKeyboard.CTRL.toUpperCase()} + X `,
    mac: `${MacKeyboard.CTRL.toUpperCase()} + X `
  },
  {
    label: '粘贴',
    win: `${WinKeyboard.CTRL.toUpperCase()} + V `,
    mac: `${MacKeyboard.CTRL.toUpperCase()} + V `
  },
  {
    label: '后退',
    win: `${WinKeyboard.CTRL.toUpperCase()} + Z `,
    mac: `${MacKeyboard.CTRL.toUpperCase()} + Z `
  },
  {
    label: '前进',
    win: `${WinKeyboard.CTRL.toUpperCase()} + ${WinKeyboard.SHIFT.toUpperCase()} + Z `,
    mac: `${MacKeyboard.CTRL.toUpperCase()} + ${MacKeyboard.SHIFT.toUpperCase()} + Z `
  },
  {
    label: '多选',
    win: `${WinKeyboard.CTRL.toUpperCase()} + 🖱️ `,
    mac: `${MacKeyboard.CTRL_SOURCE_KEY.toUpperCase()} + 🖱️ `
  },
  {
    label: '创建分组',
    win: `${WinKeyboard.CTRL.toUpperCase()} + G / 🖱️ `,
    mac: `${MacKeyboard.CTRL_SOURCE_KEY.toUpperCase()} + G / 🖱️`
  },
  {
    label: '解除分组',
    win: `${WinKeyboard.CTRL.toUpperCase()} + ${WinKeyboard.SHIFT.toUpperCase()} + G `,
    mac: `${MacKeyboard.CTRL_SOURCE_KEY.toUpperCase()} + ${WinKeyboard.SHIFT.toUpperCase()} + G `
  }
]

const closeHandle = () => {
  emit('update:modelShow', false)
}
</script>

<style lang="scss" scoped>
.shortcut-modal {
  width: min(760px, 72vw);
  max-height: 78vh;
  overflow: hidden;
  border-radius: 14px;
  border: 1px solid rgba(var(--app-theme-rgb), 0.14);
  background:
    radial-gradient(circle at 12% 0, rgba(var(--app-theme-rgb), 0.16), transparent 34%),
    rgba(10, 14, 23, 0.94);
  box-shadow: 0 30px 90px rgba(0, 0, 0, 0.48);
  backdrop-filter: blur(18px);
}

.shortcut-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 18px 20px 14px;
  border-bottom: 1px solid rgba(var(--app-theme-rgb), 0.1);

  h3 {
    margin: 2px 0 0;
    font-size: 18px;
    color: var(--app-theme, $--color-primary);
  }

  .shortcut-kicker {
    font-size: 10px;
    letter-spacing: 1.4px;
    @include fetch-color(4);
  }

  .close-btn {
    color: rgba(226, 232, 240, 0.76);
  }
}

.shortcut-grid {
  max-height: calc(78vh - 72px);
  overflow: auto;
  padding: 12px;
}

.shortcut-row {
  display: grid;
  grid-template-columns: 1fr 1.35fr 1.35fr;
  gap: 10px;
  align-items: center;
  padding: 8px 10px;
  border-radius: 9px;
  color: rgba(226, 232, 240, 0.86);

  &:nth-child(odd):not(.shortcut-row-head) {
    background: rgba(255, 255, 255, 0.025);
  }

  &.shortcut-row-head {
    position: sticky;
    top: 0;
    z-index: 1;
    background: rgba(10, 14, 23, 0.96);
    color: var(--app-theme, $--color-primary);
    font-size: 11px;
    letter-spacing: 1px;
  }

  .shortcut-label {
    font-weight: 600;
    font-size: 12px;
  }

  kbd {
    display: inline-flex;
    min-height: 26px;
    align-items: center;
    padding: 0 8px;
    border-radius: 7px;
    border: 1px solid rgba(var(--app-theme-rgb), 0.12);
    background: rgba(2, 6, 23, 0.42);
    color: rgba(226, 232, 240, 0.9);
    font-family: 'Courier New', monospace;
    font-size: 12px;
  }
}
</style>
