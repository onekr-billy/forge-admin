<script setup>
import { computed, ref, watch } from 'vue'
import { encodePrintCode } from '../renderers/codes'
import { printRenderers } from '../renderers/registry'
import { designerBindingText, designerBindingValue, fieldLabel } from './designerSample'

const props = defineProps({
  element: { type: Object, required: true },
  selected: Boolean,
  catalog: { type: Array, default: () => [] },
  context: { type: Object, default: () => ({}) },
})
const codeSrc = ref('')
const text = computed(() => {
  if (props.element.type === 'PAGE_NUMBER')
    return props.element.pageNumberFormat === 'CURRENT' ? '1' : '1 / 1'
  return designerBindingText(props.element.binding, props.element.format, props.catalog, props.context)
})
const bindingName = computed(() => props.element.binding?.source === 'FIELD' ? fieldLabel(props.catalog, props.element.binding.path) : '')
const imageSrc = computed(() => {
  if (props.element.type !== 'IMAGE')
    return ''
  const value = designerBindingValue(props.element.binding, props.context)
  return typeof value === 'string' && value.startsWith('data:image/') ? value : ''
})
const node = computed(() => ({ ...props.element, text: text.value, src: codeSrc.value || imageSrc.value }))
const renderer = computed(() => printRenderers[props.element.type])
const style = computed(() => ({
  left: `${props.element.xMm}mm`,
  top: `${props.element.yMm}mm`,
  width: `${props.element.widthMm}mm`,
  height: `${props.element.heightMm}mm`,
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
    <component :is="renderer" v-if="renderer" :node="node" class="element-renderer" />
    <div v-if="element.type === 'IMAGE' && !imageSrc" class="resource-placeholder">
      <span class="placeholder-icon">▧</span>
      <span>{{ bindingName || '图片' }}</span>
    </div>
    <div v-if="['BARCODE', 'QRCODE'].includes(element.type) && !codeSrc" class="resource-placeholder code-placeholder">
      <span>{{ element.type === 'QRCODE' ? '二维码' : '条形码' }}</span>
      <small>{{ text }}</small>
    </div>
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
</style>
