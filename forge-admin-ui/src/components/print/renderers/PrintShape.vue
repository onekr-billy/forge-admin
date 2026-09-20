<script setup>
import { computed } from 'vue'

const props = defineProps({ node: { type: Object, required: true } })
const vertical = computed(() => props.node.type === 'LINE' && props.node.heightMm > props.node.widthMm)
const line = computed(() => props.node.type === 'LINE')
const ellipse = computed(() => props.node.type === 'ELLIPSE')
const borderMm = computed(() => Math.max(props.node.style?.borderWidthMm ?? 0.4, line.value ? 0.2 : 0.35))
const borderColor = computed(() => props.node.style?.borderColor || '#000000')
const borderStyle = computed(() => props.node.style?.borderStyle || 'solid')
const fill = computed(() => props.node.style?.backgroundColor || 'transparent')
/** Approximate stroke width in SVG user units (viewBox 0..100). */
const strokeWidth = computed(() => Math.min(12, Math.max(0.8, borderMm.value * 2.2)))
</script>

<template>
  <svg
    v-if="ellipse"
    class="print-shape ellipse"
    viewBox="0 0 100 100"
    preserveAspectRatio="none"
    aria-hidden="true"
  >
    <ellipse
      cx="50"
      cy="50"
      :rx="50 - strokeWidth / 2"
      :ry="50 - strokeWidth / 2"
      :fill="fill === 'transparent' ? 'none' : fill"
      :stroke="borderColor"
      :stroke-width="strokeWidth"
      :stroke-dasharray="borderStyle === 'dashed' ? '8 4' : borderStyle === 'dotted' ? '2 3' : undefined"
    />
  </svg>
  <div
    v-else
    class="print-shape"
    :class="{ line, vertical, rectangle: node.type === 'RECTANGLE' }"
    :style="{
      boxSizing: 'border-box',
      width: vertical ? '0' : '100%',
      height: line && !vertical ? '0' : '100%',
      border: line ? undefined : `${borderMm}mm ${borderStyle} ${borderColor}`,
      borderWidth: line ? (vertical ? `0 0 0 ${borderMm}mm` : `${borderMm}mm 0 0`) : undefined,
      borderStyle: line ? borderStyle : undefined,
      borderColor: line ? borderColor : undefined,
      borderRadius: `${node.style?.borderRadiusMm || 0}mm`,
      backgroundColor: line ? 'transparent' : fill,
    }"
  />
</template>

<style scoped>
.print-shape.ellipse {
  display: block;
  width: 100%;
  height: 100%;
  overflow: visible;
}
.print-shape.rectangle {
  min-width: 100%;
  min-height: 100%;
}
</style>
