<script setup>
import { computed } from 'vue'
import { cellStyle } from './style'

const props = defineProps({ node: { type: Object, required: true } })
const tableStyle = computed(() => ({
  gridTemplateColumns: props.node.table.columns.map(column => `${column.widthMm}mm`).join(' '),
  gridTemplateRows: props.node.table.rows.map(row => `${row.heightMm}mm`).join(' '),
}))
function style(cell) {
  return {
    ...cellStyle({ borderWidthMm: 0.15, ...cell.style }),
    gridColumn: `${cell.column + 1} / span ${cell.colSpan}`,
    gridRow: `${cell.row + 1} / span ${cell.rowSpan}`,
    minWidth: 0,
    minHeight: 0,
    overflow: 'hidden',
  }
}
</script>

<template>
  <div class="static-table" role="table" :style="tableStyle">
    <div v-for="cell in node.table.cells" :key="cell.id" role="cell" :style="style(cell)">
      {{ cell.text }}
    </div>
  </div>
</template>

<style scoped>
.static-table {
  width: 100%;
  height: 100%;
  display: grid;
  box-sizing: border-box;
  background: #fff;
}
</style>
