<template>
  <div class="color-grid">
    <div
      class="color-chip"
      v-for="(item, index) in designColor"
      :key="index"
      @click="colorSelectHandle(item)"
      :style="{ '--chip-color': item.hex }"
    >
      <div class="chip-swatch" :style="{ background: item.hex }">
        <div class="chip-glow"></div>
      </div>
      <div class="chip-info">
        <span class="chip-name">{{ item.name }}</span>
        <span class="chip-hex">{{ item.hex }}</span>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { PropType } from 'vue'
import { AppThemeColorType } from '@/store/modules/designStore/designStore.d'

const emits = defineEmits(['colorSelectHandle'])
defineProps({
  designColor: {
    type: Object as PropType<AppThemeColorType[]>,
    required: true
  }
})

const colorSelectHandle = (color: AppThemeColorType) => {
  emits('colorSelectHandle', color)
}
</script>

<style lang="scss" scoped>
.color-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 8px;
}

.color-chip {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: $--border-radius-sm;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  border: 1px solid rgba(255, 255, 255, 0.03);
  background: rgba(255, 255, 255, 0.01);

  &:hover {
    background: rgba(var(--app-theme-rgb), 0.04);
    border-color: rgba(var(--app-theme-rgb), 0.15);
    transform: translateY(-2px);
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.3);

    .chip-swatch {
      transform: scale(1.1);
      box-shadow: 0 0 12px var(--chip-color);
    }

    .chip-name {
      color: var(--chip-color);
    }
  }

  .chip-swatch {
    width: 28px;
    height: 28px;
    border-radius: 4px;
    flex-shrink: 0;
    transition: all 0.2s ease;
    position: relative;
    overflow: hidden;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);

    .chip-glow {
      position: absolute;
      top: -50%;
      left: -50%;
      width: 200%;
      height: 200%;
      background: radial-gradient(circle at 30% 30%, rgba(255,255,255,0.3) 0%, transparent 60%);
    }
  }

  .chip-info {
    display: flex;
    flex-direction: column;
    gap: 2px;
    min-width: 0;
  }

  .chip-name {
    font-size: 12px;
    @include fetch-color(2);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    transition: color 0.2s;
  }

  .chip-hex {
    font-size: 10px;
    @include fetch-color(4);
    font-family: 'Courier New', monospace;
    letter-spacing: 0.5px;
  }
}
</style>
