<script setup>
import { computed } from 'vue'
import { usePrintDesignerStore } from '@/stores/print/printDesignerStore'
import { selectionBounds } from './commands'
import { usePrintResize } from './usePrintResize'

const store = usePrintDesignerStore()
const resize = usePrintResize(store)
const bounds = computed(() => selectionBounds(store.selectedElements))
</script>

<template>
  <div v-if="bounds" class="selection-overlay" :style="{ left: `${bounds.xMm}mm`, top: `${bounds.yMm}mm`, width: `${bounds.widthMm}mm`, height: `${bounds.heightMm}mm` }">
    <button v-if="store.selectedIds.length === 1" type="button" class="resize-handle" aria-label="调整元素尺寸" title="拖动调整尺寸" @pointerdown.stop="resize.start" />
  </div>
</template>

<style scoped>
.selection-overlay {
  position: absolute;
  outline: 1px solid var(--primary-color, #356cde);
  pointer-events: none;
  box-sizing: border-box;
}
.resize-handle {
  position: absolute;
  width: 9px;
  height: 9px;
  padding: 0;
  right: -5px;
  bottom: -5px;
  border: 1px solid var(--primary-color, #356cde);
  background: white;
  cursor: nwse-resize;
  pointer-events: auto;
  touch-action: none;
}
</style>
