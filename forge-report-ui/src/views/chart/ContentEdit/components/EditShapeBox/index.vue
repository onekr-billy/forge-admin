<template>
  <div class="go-shape-box" :class="{ lock, hide }">
    <slot></slot>
    <!-- 锚点 -->
    <template v-if="!hiddenPoint">
      <div
        :class="`shape-point ${point}`"
        v-for="(point, index) in select ? pointList : []"
        :key="index"
        :style="usePointStyle(point, index, item.attr, cursorResize)"
        @mousedown="useMousePointHandle($event, point, item.attr)"
      ></div>
    </template>

    <!-- 选中 -->
    <div class="shape-modal" :style="useSizeStyle(item.attr)">
      <div class="shape-modal-select" :class="{ active: select }"></div>
      <div class="shape-modal-change" :class="{ selectActive: select, hoverActive: hover }"></div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, PropType } from 'vue'
import { useChartEditStore } from '@/store/modules/chartEditStore/chartEditStore'
import { EditCanvasTypeEnum } from '@/store/modules/chartEditStore/chartEditStore.d'

import { useDesignStore } from '@/store/modules/designStore/designStore'
import { CreateComponentType, CreateComponentGroupType } from '@/packages/index.d'
import { useSizeStyle, usePointStyle } from '../../hooks/useStyle.hook'
import { useMousePointHandle } from '../../hooks/useDrag.hook'

const props = defineProps({
  item: {
    type: Object as PropType<CreateComponentType | CreateComponentGroupType>,
    required: true
  },
  hiddenPoint: {
    type: Boolean,
    required: false
  }
})

const designStore = useDesignStore()
const chartEditStore = useChartEditStore()

// 锚点
const pointList = ['t', 'r', 'b', 'l', 'lt', 'rt', 'lb', 'rb']

// 光标朝向
const cursorResize = ['n', 'e', 's', 'w', 'nw', 'ne', 'sw', 'se']

// 颜色
const themeColor = computed(() => {
  return designStore.getAppTheme
})

// 计算当前选中目标
const hover = computed(() => {
  const isDrag = chartEditStore.getEditCanvas[EditCanvasTypeEnum.IS_DRAG]
  if (isDrag) return false

  if (props.item.status.lock) return false
  return props.item.id === chartEditStore.getTargetChart.hoverId
})

// 兼容多值场景
const select = computed(() => {
  const id = props.item.id
  if (props.item.status.lock) return false
  return chartEditStore.getTargetChart.selectId.find((e: string) => e === id)
})

// 锁定
const lock = computed(() => {
  return props.item.status.lock
})

// 隐藏
const hide = computed(() => {
  return props.item.status.hide
})
</script>

<style lang="scss" scoped>
@include go(shape-box) {
  position: absolute;
  cursor: move;

  &.lock {
    cursor: default !important;
  }

  &.hide {
    display: none;
  }

  /* 锚点 */
  .shape-point {
    z-index: 1;
    position: absolute;
    width: 9px;
    height: 9px;
    border-radius: 50%;
    background-color: var(--app-theme, #3b82f6);
    box-shadow: 0 0 0 2px rgba(0, 0, 0, 0.5), 0 0 6px rgba(var(--app-theme-rgb), 0.5);
    transform: translate(-50%, -50%);
    transition: box-shadow 0.15s ease;

    &:hover {
      box-shadow: 0 0 0 2px rgba(0, 0, 0, 0.6), 0 0 12px rgba(var(--app-theme-rgb), 0.7);
    }

    &.t,
    &.b {
      width: 32px;
      height: 5px;
      border-radius: 3px;
      transform: translate(-50%, -50%);
    }

    &.l,
    &.r {
      width: 5px;
      height: 32px;
      border-radius: 3px;
      transform: translate(-50%, -50%);
    }

    &.lt,
    &.lb,
    &.rt,
    &.rb {
      width: 10px;
      height: 10px;
      border: 2px solid var(--app-theme, #3b82f6);
      background-color: #fff;
      box-shadow: 0 0 0 2px rgba(0, 0, 0, 0.5);
    }
  }
  /* 选中 */
  .shape-modal {
    position: absolute;
    top: 0;
    left: 0;

    .shape-modal-select,
    .shape-modal-change {
      position: absolute;
      width: 100%;
      height: 100%;
      border-radius: 4px;
    }

    .shape-modal-select {
      opacity: 0.08;
      top: 2px;
      left: 2px;
      &.active {
        background-color: var(--app-theme, #3b82f6);
      }
    }
    .shape-modal-change {
      border: 2px solid rgba(0, 0, 0, 0);
      border-radius: 4px;
      &.selectActive,
      &.hoverActive {
        border-color: var(--app-theme, #3b82f6);
        border-width: 2px;
        box-shadow: 0 0 8px rgba(var(--app-theme-rgb), 0.15);
      }
      &.hoverActive {
        border-style: dashed;
      }
      &.selectActive {
        border-style: solid;
      }
    }
  }
}
</style>
