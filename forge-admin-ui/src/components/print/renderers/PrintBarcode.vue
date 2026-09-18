<script setup>
import PrintImage from './PrintImage.vue'

defineProps({ node: { type: Object, required: true } })
</script>

<template>
  <PrintImage :node="{ ...node, alt: `条码 ${node.text}` }" />
</template>
