<template>
  <div class="go-chart-configurations-animations" v-if="targetData">
    <n-button
      class="clear-btn go-my-2"
      :disabled="!targetData.styles.animations.length"
      @click="clearAnimation"
    >
      清除动画
    </n-button>
    <collapse-item
      v-for="(item, index) in animations"
      :key="index"
      :name="item.label"
      :expanded="true"
    >
      <n-grid :x-gap="6" :y-gap="10" :cols="3">
        <n-grid-item
          class="animation-item go-transition-quick"
          :class="[
            activeIndex(childrenItem.value) && 'active',
            hoverPreviewAnimate === childrenItem.value &&
              `animate__animated  animate__${childrenItem.value}`
          ]"
          v-for="(childrenItem, index) in item.children"
          :key="index"
          @mouseover="hoverPreviewAnimate = childrenItem.value"
          @click="addAnimation(childrenItem)"
        >
          {{ childrenItem.label }}
        </n-grid-item>
      </n-grid>
    </collapse-item>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { animations } from '@/settings/animations/index'
import { CollapseItem } from '@/components/Pages/ChartItemSetting'
import { useDesignStore } from '@/store/modules/designStore/designStore'
import { useTargetData } from '../hooks/useTargetData.hook'

// 全局颜色
const designStore = useDesignStore()

const hoverPreviewAnimate = ref('')

const { targetData } = useTargetData()

// 颜色
const themeColor = computed(() => {
  return designStore.getAppTheme
})

// * 选中的动画样式
const activeIndex = (value: string) => {
  const selectValue = targetData.value.styles.animations
  if (!selectValue.length) return false
  return selectValue[0] === value
}

// * 清除动画
const clearAnimation = () => {
  targetData.value.styles.animations = []
}

// * 新增动画，现只支持一种
const addAnimation = (item: { label: string; value: string }) => {
  targetData.value.styles.animations = [item.value]
}
</script>

<style lang="scss" scoped>
@include go('chart-configurations-animations') {
  padding: 0;

  :deep(.n-collapse-item__header) {
    font-size: 13px;
    font-weight: 600;
    color: rgba(226, 232, 240, 0.84);
  }

  .clear-btn {
    width: 100%;
    border-radius: 8px;
    border: 1px solid rgba(var(--app-theme-rgb), 0.1);
    background: rgba(15, 23, 42, 0.24);
    color: rgba(203, 213, 225, 0.7);
    transition: all 0.2s ease;

    &:hover:not(:disabled) {
      border-color: rgba(var(--app-theme-rgb), 0.24);
      background: rgba(var(--app-theme-rgb), 0.08);
      color: #fff;
    }

    &:disabled {
      opacity: 0.35;
    }
  }

  :deep(.n-grid) {
    row-gap: 8px;
    column-gap: 8px;
  }

  .animation-item {
    position: relative;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 6px;
    height: 56px;
    padding: 6px 8px;
    cursor: pointer;
    font-size: 12px;
    letter-spacing: 0.3px;
    border-radius: 10px;
    border: 1px solid rgba(148, 163, 184, 0.08);
    background: rgba(15, 23, 42, 0.18);
    color: rgba(203, 213, 225, 0.68);
    transition: all 0.22s ease;
    overflow: hidden;

    &::before {
      content: '';
      width: 22px;
      height: 3px;
      border-radius: 2px;
      background: rgba(var(--app-theme-rgb), 0.18);
      transition: all 0.22s ease;
    }

    &:hover {
      color: #fff;
      border-color: rgba(var(--app-theme-rgb), 0.28);
      background: rgba(var(--app-theme-rgb), 0.08);
      transform: translateY(-2px);

      &::before {
        width: 30px;
        background: rgba(var(--app-theme-rgb), 0.5);
      }
    }

    &.active {
      color: #fff;
      border-color: rgba(var(--app-theme-rgb), 0.4);
      background:
        linear-gradient(135deg, rgba(var(--app-theme-rgb), 0.24), rgba(var(--app-theme-rgb), 0.06)),
        rgba(15, 23, 42, 0.44);
      box-shadow: 0 6px 20px rgba(var(--app-theme-rgb), 0.12);

      &::before {
        width: 36px;
        background: var(--app-theme, #3b82f6);
        box-shadow: 0 0 10px rgba(var(--app-theme-rgb), 0.4);
      }
    }
  }
}
</style>
