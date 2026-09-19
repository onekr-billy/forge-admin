<script setup>
import { computed, ref, watch } from 'vue'
import { encodePrintCode } from '../renderers/codes'
import { printRenderers } from '../renderers/registry'
import { designerBindingText, designerBindingValue, fieldLabel } from './designerSample'
import PrintStaticTableDesigner from './PrintStaticTableDesigner.vue'

const props = defineProps({
  element: { type: Object, required: true },
  selected: Boolean,
  catalog: { type: Array, default: () => [] },
  context: { type: Object, default: () => ({}) },
  tableCellIds: { type: Array, default: () => [] },
  pageNumber: { type: Number, default: 1 },
  totalPages: { type: Number, default: 1 },
})
const emit = defineEmits(['tableCellSelect', 'tableCellChange'])
const codeSrc = ref('')
const text = computed(() => {
  if (props.element.type === 'PAGE_NUMBER')
    return props.element.pageNumberFormat === 'CURRENT' ? `${props.pageNumber}` : `${props.pageNumber} / ${props.totalPages}`
  return designerBindingText(props.element.binding, props.element.format, props.catalog, props.context)
})
const bindingName = computed(() => props.element.binding?.source === 'FIELD' ? fieldLabel(props.catalog, props.element.binding.path) : '')
const imageSrc = computed(() => {
  if (props.element.type !== 'IMAGE')
    return ''
  const value = designerBindingValue(props.element.binding, props.context)
  return typeof value === 'string' && value.startsWith('data:image/') ? value : ''
})
const node = computed(() => ({
  ...props.element,
  text: text.value,
  src: codeSrc.value || imageSrc.value,
  table: props.element.table
    ? { ...props.element.table, cells: props.element.table.cells.map(cell => ({ ...cell, text: designerBindingText(cell.binding, cell.format, props.catalog, props.context) })) }
    : undefined,
}))
const renderer = computed(() => printRenderers[props.element.type])
const style = computed(() => ({
  left: `${props.element.xMm}mm`,
  top: `${props.element.yMm}mm`,
  width: `${props.element.widthMm}mm`,
  height: `${props.element.heightMm}mm`,
  transform: props.element.rotationDeg || props.element.flipX || props.element.flipY ? `rotate(${props.element.rotationDeg || 0}deg) scaleX(${props.element.flipX ? -1 : 1}) scaleY(${props.element.flipY ? -1 : 1})` : undefined,
  transformOrigin: 'center center',
}))

watch(() => [props.element.type, props.element.barcodeFormat, props.element.widthMm, props.element.heightMm, text.value], async (_, __, onCleanup) => {
  codeSrc.value = ''
  if (!['BARCODE', 'QRCODE'].includes(props.element.type) || !text.value)
    return
  const controller = new AbortController()
  onCleanup(() => controller.abort())
  try {
    codeSrc.value = await encodePrintCode({ ...props.element, text: text.value }, controller.signal)
  }
  catch {
    codeSrc.value = ''
  }
}, { immediate: true })
</script>

<template>
  <div
    :style="style"
    class="canvas-element"
    :class="[{ selected }, element.type.toLowerCase()]"
    :data-element-id="element.id"
    role="button"
    :aria-label="`${element.type} ${text || bindingName}`"
    :title="bindingName ? `绑定字段：${bindingName}` : undefined"
    tabindex="-1"
  >
    <PrintStaticTableDesigner
      v-if="element.type === 'STATIC_TABLE' && selected"
      :node="node"
      :selected-ids="tableCellIds"
      :locked="element.locked"
      class="element-renderer interactive"
      @select="(id, additive) => emit('tableCellSelect', id, additive)"
      @change="(id, value) => emit('tableCellChange', id, value)"
    />
    <component :is="renderer" v-else-if="renderer" :node="node" class="element-renderer" />
    <div v-if="element.type === 'IMAGE' && !imageSrc" class="resource-placeholder">
      <span class="placeholder-icon">▧</span>
      <span>{{ bindingName || '图片' }}</span>
    </div>
    <div v-if="['BARCODE', 'QRCODE'].includes(element.type) && !codeSrc" class="resource-placeholder code-placeholder">
      <span>{{ element.type === 'QRCODE' ? '二维码' : '条形码' }}</span>
      <small>{{ text }}</small>
    </div>
    <span v-if="element.locked && selected" class="lock-indicator" aria-label="元素已锁定">锁</span>
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
.element-renderer {
  width: 100%;
  height: 100%;
  pointer-events: none;
}
.element-renderer.interactive {
  pointer-events: auto;
}
.resource-placeholder {
  position: absolute;
  inset: 0;
  display: grid;
  place-content: center;
  gap: 2px;
  color: #64748b;
  background: linear-gradient(45deg, #f8fafc 25%, #f1f5f9 25%, #f1f5f9 50%, #f8fafc 50%, #f8fafc 75%, #f1f5f9 75%);
  background-size: 8px 8px;
  font-size: 9pt;
  text-align: center;
  pointer-events: none;
}
.placeholder-icon {
  color: #94a3b8;
  font-size: 18px;
  line-height: 1;
}
.code-placeholder small {
  display: block;
  max-width: 100%;
  overflow: hidden;
  font-size: 7pt;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.lock-indicator {
  position: absolute;
  right: 1px;
  bottom: 1px;
  padding: 0 3px;
  border-radius: 2px;
  color: #fff;
  background: #475569;
  font-size: 7px;
  line-height: 12px;
  pointer-events: none;
}
</style>
