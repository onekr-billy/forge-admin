<template>
  <div class="go-edit-range go-transition" :style="rangeStyle" @mousedown="mousedownBoxSelect($event, undefined)">
    <slot></slot>
    <!-- 水印 -->
    <edit-watermark></edit-watermark>
    <!-- 拖拽时的辅助线 -->
    <edit-align-line></edit-align-line>
    <!-- 框选时的样式框 -->
    <edit-select></edit-select>
    <!-- 拖拽时的遮罩 -->
    <div class="go-edit-range-model" :style="rangeModelStyle"></div>
  </div>
</template>

<script setup lang="ts">
import { toRefs, computed } from 'vue'
import { useSizeStyle } from '../../hooks/useStyle.hook'
import { canvasModelIndex } from '@/settings/designSetting'
import { mousedownBoxSelect } from '../../hooks/useDrag.hook'
import { useChartEditStore } from '@/store/modules/chartEditStore/chartEditStore'
import { EditAlignLine } from '../EditAlignLine'
import { EditWatermark } from '../EditWatermark'
import { EditSelect } from '../EditSelect'

const chartEditStore = useChartEditStore()

const { getEditCanvasConfig, getEditCanvas } = toRefs(chartEditStore)

const size = computed(() => {
  return {
    w: getEditCanvasConfig.value.width,
    h: getEditCanvasConfig.value.height
  }
})

const rangeStyle = computed(() => {
  // 缩放
  const scale = {
    transform: `scale(${getEditCanvas.value.scale})`
  }
  // @ts-ignore
  return { ...useSizeStyle(size.value), ...scale }
})

// 模态层
const rangeModelStyle = computed(() => {
  const dragStyle = getEditCanvas.value.isCreate && { 'z-index': 99999 }
  // @ts-ignore
  return { ...useSizeStyle(size.value), ...dragStyle }
})
</script>

<style lang="scss" scoped>
@include go(edit-range) {
  position: relative;
  transform-origin: left top;
  background-size: cover;
  overflow: hidden;
  border: 1px solid rgba(var(--app-theme-rgb), 0.22);
  box-shadow:
    0 24px 60px rgba(0, 0, 0, 0.42),
    0 0 0 1px rgba(255, 255, 255, 0.025),
    0 0 28px rgba(var(--app-theme-rgb), 0.08);
  @include fetch-bg-color('background-color2');

  &::before {
    content: '';
    position: absolute;
    inset: 0;
    pointer-events: none;
    z-index: 0;
    background:
      linear-gradient(90deg, rgba(var(--app-theme-rgb), 0.12), transparent 18%, transparent 82%, rgba(var(--app-theme-rgb), 0.12)),
      linear-gradient(180deg, rgba(255, 255, 255, 0.05), transparent 16%);
    mix-blend-mode: screen;
  }

  @include go(edit-range-model) {
    z-index: -1;
    position: absolute;
    left: 0;
    top: 0;
  }
}
</style>
