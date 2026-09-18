<script setup>
defineProps({ node: { type: Object, required: true } })
</script>

<template>
  <div :style="{ boxSizing: 'border-box', width: '100%', height: node.type === 'LINE' ? '0' : '100%', border: `${node.style?.borderWidthMm || 0.2}mm solid ${node.style?.borderColor || '#000000'}`, borderWidth: node.type === 'LINE' ? `${node.style?.borderWidthMm || 0.2}mm 0 0` : undefined, backgroundColor: node.style?.backgroundColor || 'transparent' }" />
</template>
