<script setup>
defineProps({ node: { type: Object, required: true } })
</script>

<template>
  <img v-if="node.src" :src="node.src" :alt="node.alt || ''" :style="{ display: 'block', width: '100%', height: '100%', objectFit: node.style?.objectFit || 'contain' }">
</template>
