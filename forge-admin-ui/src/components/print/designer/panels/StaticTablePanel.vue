<script setup>
import { NButton, NColorPicker, NFormItem, NInput, NInputNumber, NSelect } from 'naive-ui'
import { computed } from 'vue'
import { usePrintDesignerStore } from '@/stores/print/printDesignerStore'

const store = usePrintDesignerStore()
const visible = computed(() => store.activeElement?.type === 'STATIC_TABLE')
const cell = computed(() => store.activeTableCell)
const fieldOptions = computed(() => store.catalog.filter(item => item.type !== 'COLLECTION').map(item => ({ label: item.label || item.path, value: item.path })))
const bindingSource = computed(() => cell.value?.binding?.source || 'CONSTANT')
const merged = computed(() => cell.value && (cell.value.rowSpan > 1 || cell.value.colSpan > 1))
function setSource(source) {
  store.patchSelectedTableCells({ binding: source === 'CONSTANT' ? { source, value: '' } : { source, path: fieldOptions.value[0]?.value || '' } })
}
function setBindingValue(value) {
  store.patchSelectedTableCells({ binding: { source: 'CONSTANT', value: value ?? '' } })
}
function setBindingPath(path) {
  store.patchSelectedTableCells({ binding: { source: 'FIELD', path } })
}
</script>

<template>
  <section v-if="visible" class="designer-group static-table-panel">
    <h3>空白表格</h3>
    <div class="panel-row table-actions">
      <NButton size="tiny" @click="store.addStaticTableRow()">
        + 行
      </NButton>
      <NButton size="tiny" @click="store.addStaticTableColumn()">
        + 列
      </NButton>
      <NButton size="tiny" :disabled="!cell || store.activeElement.table.rows.length <= 1" @click="store.deleteStaticTableRow()">
        删行
      </NButton>
      <NButton size="tiny" :disabled="!cell || store.activeElement.table.columns.length <= 1" @click="store.deleteStaticTableColumn()">
        删列
      </NButton>
      <NButton size="tiny" :disabled="store.selectedTableCells.length < 2" @click="store.mergeStaticTableSelection()">
        合并
      </NButton>
      <NButton size="tiny" :disabled="!merged" @click="store.splitStaticTableSelection()">
        拆分
      </NButton>
    </div>
    <p v-if="!store.tableCellIds.length" class="muted">
      单击表格单元格后编辑；Shift / ⌘ / Ctrl 可多选连续单元格，双击可直接输入。
    </p>
    <template v-else>
      <h3>单元格（{{ store.selectedTableCells.length }}）</h3>
      <template v-if="cell">
        <div class="panel-grid">
          <NFormItem label="列宽 mm" size="small">
            <NInputNumber :value="store.activeElement.table.columns[cell.column].widthMm" :min="1" :precision="2" :show-button="false" @update:value="$event !== null && store.patchStaticTableTrack('column', cell.column, $event)" />
          </NFormItem>
          <NFormItem label="行高 mm" size="small">
            <NInputNumber :value="store.activeElement.table.rows[cell.row].heightMm" :min="1" :precision="2" :show-button="false" @update:value="$event !== null && store.patchStaticTableTrack('row', cell.row, $event)" />
          </NFormItem>
        </div>
        <NFormItem label="内容来源" size="small">
          <NSelect :value="bindingSource" :options="[{ label: '固定文字', value: 'CONSTANT' }, { label: '业务字段', value: 'FIELD' }]" @update:value="setSource" />
        </NFormItem>
        <NFormItem v-if="bindingSource === 'CONSTANT'" label="内容" size="small">
          <NInput :value="String(cell.binding?.value ?? '')" @update:value="setBindingValue" />
        </NFormItem>
        <NFormItem v-else label="绑定字段" size="small">
          <NSelect filterable :value="cell.binding?.path" :options="fieldOptions" @update:value="setBindingPath" />
        </NFormItem>
      </template>
      <div class="panel-grid">
        <NFormItem label="字号 pt" size="small">
          <NInputNumber :value="cell?.style?.fontSizePt ?? 10" :min="6" :max="144" :show-button="false" @update:value="$event !== null && store.patchSelectedTableCellStyle({ fontSizePt: $event })" />
        </NFormItem>
        <NFormItem label="对齐" size="small">
          <NSelect :value="cell?.style?.textAlign || 'left'" :options="[{ label: '左', value: 'left' }, { label: '中', value: 'center' }, { label: '右', value: 'right' }]" @update:value="store.patchSelectedTableCellStyle({ textAlign: $event })" />
        </NFormItem>
        <NFormItem label="文字色" size="small">
          <NColorPicker :value="cell?.style?.color || '#000000'" :show-alpha="false" :modes="['hex']" @update:value="store.patchSelectedTableCellStyle({ color: $event })" />
        </NFormItem>
        <NFormItem label="背景色" size="small">
          <NColorPicker :value="cell?.style?.backgroundColor || '#ffffff'" :show-alpha="false" :modes="['hex']" @update:value="store.patchSelectedTableCellStyle({ backgroundColor: $event })" />
        </NFormItem>
        <NFormItem label="边框 mm" size="small">
          <NInputNumber :value="cell?.style?.borderWidthMm ?? 0.15" :min="0" :max="3" :step="0.05" :show-button="false" @update:value="$event !== null && store.patchSelectedTableCellStyle({ borderWidthMm: $event })" />
        </NFormItem>
        <NFormItem label="边框样式" size="small">
          <NSelect :value="cell?.style?.borderStyle || 'solid'" :options="[{ label: '实线', value: 'solid' }, { label: '虚线', value: 'dashed' }, { label: '点线', value: 'dotted' }]" @update:value="store.patchSelectedTableCellStyle({ borderStyle: $event })" />
        </NFormItem>
      </div>
    </template>
  </section>
</template>

<style scoped>
.table-actions {
  gap: 4px;
}
.static-table-panel .muted {
  margin: 8px 0 0;
}
</style>
