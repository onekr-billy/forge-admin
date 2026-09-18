<script setup>
import { computed } from 'vue'
import { printStyle } from '../renderers/style'

const props = defineProps({ element: { type: Object, required: true }, selected: Boolean })
const label = computed(() => {
  const e = props.element
  if (e.type === 'PAGE_NUMBER')
    return '第 1 / 1 页'
  if (e.type === 'TEXT')
    return e.binding?.source === 'CONSTANT' ? String(e.binding.value ?? '') : `{{ ${e.binding?.path || '字段'} }}`
  return { IMAGE: '图片', BARCODE: '条形码', QRCODE: '二维码' }[e.type] || ''
})
const style = computed(() => ({
  ...printStyle(props.element.style),
  left: `${props.element.xMm}mm`,
  top: `${props.element.yMm}mm`,
  width: `${props.element.widthMm}mm`,
  height: `${props.element.heightMm}mm`,
}))
</script>

<template>
  <div :style="style" class="canvas-element" :class="[{ selected }, element.type.toLowerCase()]" :data-element-id="element.id" role="button" :aria-label="`${element.type} ${label}`" tabindex="-1">
    <span>{{ label }}</span>
  </div>
</template>

<style scoped>
.canvas-element {
  position: absolute;
  box-sizing: border-box;
  overflow: hidden;
  cursor: move;
  user-select: none;
  outline: 1px dashed #a8adb5;
  color: #111;
}
.canvas-element:hover,
.canvas-element.selected {
  outline: 1.5px solid var(--primary-color, #356cde);
}
.canvas-element.rectangle {
  border: 0.3mm solid #222;
}
.canvas-element.line {
  border-top: 0.3mm solid #222;
}
.canvas-element.image,
.canvas-element.barcode,
.canvas-element.qrcode {
  display: grid;
  place-items: center;
  background: #f4f5f7;
  font-size: 10pt;
  color: #555;
}
</style>
