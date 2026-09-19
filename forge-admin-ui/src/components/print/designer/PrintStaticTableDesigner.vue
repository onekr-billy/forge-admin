<script setup>
import { computed, nextTick, ref } from 'vue'
import { cellStyle } from '../renderers/style'

const props = defineProps({ node: { type: Object, required: true }, selectedIds: { type: Array, default: () => [] }, locked: Boolean })
const emit = defineEmits(['select', 'change'])
const editing = ref('')
const draft = ref('')
const root = ref(null)
const tableStyle = computed(() => ({
  gridTemplateColumns: props.node.table.columns.map(column => `${column.widthMm}mm`).join(' '),
  gridTemplateRows: props.node.table.rows.map(row => `${row.heightMm}mm`).join(' '),
}))
function style(cell) {
  return {
    ...cellStyle({ borderWidthMm: 0.15, ...cell.style }),
    gridColumn: `${cell.column + 1} / span ${cell.colSpan}`,
    gridRow: `${cell.row + 1} / span ${cell.rowSpan}`,
  }
}
async function start(cell) {
  if (props.locked)
    return
  editing.value = cell.id
  draft.value = cell.text == null ? '' : String(cell.text)
  await nextTick()
  const editor = root.value?.querySelector('input')
  editor?.focus()
  editor?.select()
}
function finish(cell, save = true) {
  if (editing.value !== cell.id)
    return
  if (save)
    emit('change', cell.id, draft.value)
  editing.value = ''
}
</script>

<template>
  <div ref="root" class="static-table-designer" role="grid" :style="tableStyle">
    <div
      v-for="cell in node.table.cells"
      :key="cell.id"
      role="gridcell"
      class="static-cell"
      :class="{ selected: selectedIds.includes(cell.id) }"
      :style="style(cell)"
      :data-cell-id="cell.id"
      @pointerdown.stop
      @click.stop="emit('select', cell.id, $event.shiftKey || $event.metaKey || $event.ctrlKey)"
      @dblclick.stop="start(cell)"
    >
      <input
        v-if="editing === cell.id"
        v-model="draft"
        aria-label="单元格内容"
        @pointerdown.stop
        @click.stop
        @blur="finish(cell)"
        @keydown.enter.prevent="finish(cell)"
        @keydown.esc.prevent="finish(cell, false)"
      >
      <template v-else>
        {{ cell.text }}
      </template>
    </div>
  </div>
</template>

<style scoped>
.static-table-designer {
  width: 100%;
  height: 100%;
  display: grid;
  box-sizing: border-box;
  background: #fff;
  cursor: default;
}
.static-cell {
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  cursor: cell;
  user-select: none;
}
.static-cell.selected {
  box-shadow: inset 0 0 0 0.45mm var(--primary-color, #356cde);
  background-color: color-mix(in srgb, var(--primary-color, #356cde) 9%, #fff) !important;
}
.static-cell input {
  width: 100%;
  height: 100%;
  box-sizing: border-box;
  padding: 0;
  border: 0;
  outline: 0;
  color: inherit;
  background: #fff;
  font: inherit;
  text-align: inherit;
}
</style>
