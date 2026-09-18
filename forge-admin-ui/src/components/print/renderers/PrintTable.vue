<script setup>
import { cellStyle } from './style'

defineProps({ node: { type: Object, required: true } })
</script>

<template>
  <div role="table" :aria-label="node.id" :style="{ width: `${node.widthMm}mm` }">
    <div v-for="(row, index) in node.rows" :key="row.key ?? index" role="row" :data-print-row="row.key" :data-row-kind="row.kind" :style="{ display: 'flex', height: `${row.heightMm}mm` }">
      <div v-for="(cell, column) in row.cells" :key="column" :role="row.kind === 'header' ? 'columnheader' : 'cell'" :style="{ ...cellStyle(cell.style), width: `${cell.widthMm}mm`, flex: 'none' }">
        {{ cell.text }}
      </div>
    </div>
  </div>
</template>
