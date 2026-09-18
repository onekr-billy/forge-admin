<script setup>
import PrintImage from './PrintImage.vue'

defineProps({ node: { type: Object, required: true } })
</script>

<template>
  <PrintImage :node="{ ...node, alt: '二维码' }" />
</template>
