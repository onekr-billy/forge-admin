<script setup>
import { printStyle } from './style'

defineProps({ node: { type: Object, required: true } })
</script>

<template>
  <div :style="printStyle(node.style)">
    <template v-if="node.lines">
      <div v-for="(line, index) in node.lines" :key="index" :style="{ height: `${node.lineHeightMm}mm`, whiteSpace: 'pre' }">
        {{ line || '\u200b' }}
      </div>
    </template>
    <template v-else>
      {{ node.text }}
    </template>
  </div>
</template>
