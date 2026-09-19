<script setup>
import { computed } from 'vue'

const props = defineProps({ node: { type: Object, required: true } })
const vertical = computed(() => props.node.type === 'LINE' && props.node.heightMm > props.node.widthMm)
const line = computed(() => props.node.type === 'LINE')
</script>

<template>
  <div
    :style="{
      boxSizing: 'border-box',
      width: vertical ? '0' : '100%',
      height: line && !vertical ? '0' : '100%',
      border: `${node.style?.borderWidthMm || 0.2}mm ${node.style?.borderStyle || 'solid'} ${node.style?.borderColor || '#000000'}`,
      borderWidth: line ? (vertical ? `0 0 0 ${node.style?.borderWidthMm || 0.2}mm` : `${node.style?.borderWidthMm || 0.2}mm 0 0`) : undefined,
      borderRadius: node.type === 'ELLIPSE' ? '50%' : `${node.style?.borderRadiusMm || 0}mm`,
      backgroundColor: node.style?.backgroundColor || 'transparent',
    }"
  />
</template>
