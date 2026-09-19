<script setup>
import { cellStyle } from './style'

defineProps({ node: { type: Object, required: true } })
</script>

<template>
  <div role="table" :aria-label="node.id" :style="{ width: `${node.widthMm}mm` }">
    <div v-for="(row, index) in node.rows" :key="row.key ?? index" role="row" :data-print-row="row.key" :data-row-kind="row.kind" :style="{ display: 'flex', height: `${row.heightMm}mm` }">
      <div v-for="(cell, column) in row.cells" :key="column" :role="row.kind === 'header' ? 'columnheader' : 'cell'" :style="{ ...cellStyle(cell.style), width: `${cell.widthMm}mm`, flex: 'none' }">
        <img v-if="cell.type === 'IMAGE' && cell.src" :src="cell.src" alt="" :style="{ display: 'block', width: '100%', height: `${cell.imageHeightMm}mm`, objectFit: 'contain' }">
        <template v-else>
          {{ cell.text }}
        </template>
      </div>
    </div>
  </div>
</template>
