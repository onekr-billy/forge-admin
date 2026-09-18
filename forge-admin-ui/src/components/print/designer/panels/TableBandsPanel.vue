<script setup>
import { NButton, NFormItem, NInput, NSelect } from 'naive-ui'
import { computed } from 'vue'
import { usePrintDesignerStore } from '@/stores/print/printDesignerStore'
import { findSurface } from '../commands'

const store = usePrintDesignerStore()
const table = computed(() => store.activeSurface)
const fields = computed(() => store.catalog.filter(f => f.type !== 'COLLECTION' && !store.catalog.some(c => c.type === 'COLLECTION' && f.path.startsWith(`${c.path}.`))).map(f => ({ label: f.label || f.path, value: f.path })))
const formats = [{ label: '文本', value: 'TEXT' }, { label: '金额（分转元）', value: 'MONEY' }, { label: '数字', value: 'NUMBER' }]
function change(action) {
  store.execute(doc => action(findSurface(doc, store.surfaceId)))
}
function addHeader() {
  change((item) => {
    item.headerRows ||= [{ cells: item.columns.map(c => ({ text: c.title, span: 1 })) }]
    item.headerRows.unshift({ cells: [{ text: '明细表', span: item.columns.length }] })
  })
}
function removeHeader(index) {
  change((item) => {
    item.headerRows.splice(index, 1)
    if (!item.headerRows.length)
      delete item.headerRows
  })
}
function addFooter() {
  change((item) => {
    item.footer = { cells: item.columns.map((c, i) => ({ span: 1, binding: { source: 'CONSTANT', value: i === 0 ? '合计' : '' } })) }
  })
}
function rowOf(item, index) {
  return index === -1 ? item.footer : item.headerRows[index]
}
function patch(index, cell, patch) {
  change(item => Object.assign(rowOf(item, index).cells[cell], patch))
}
function merge(index, cell) {
  change((item) => {
    const cells = rowOf(item, index).cells
    cells[cell].span += cells[cell + 1].span
    cells.splice(cell + 1, 1)
  })
}
function split(index, cell) {
  change((item) => {
    const cells = rowOf(item, index).cells
    cells[cell].span--
    cells.splice(cell + 1, 0, index === -1 ? { span: 1, binding: { source: 'CONSTANT', value: '' } } : { span: 1, text: '' })
  })
}
</script>

<template>
  <section class="table-bands">
    <h3>表头与合计</h3>
    <NButton size="small" :disabled="(table.headerRows?.length || 0) >= 10" @click="addHeader">
      添加表头行
    </NButton>
    <div v-for="(row, rowIndex) in table.headerRows || []" :key="rowIndex" class="band-row">
      <div class="panel-row">
        <strong>表头第 {{ rowIndex + 1 }} 行</strong><NButton text type="error" size="tiny" @click="removeHeader(rowIndex)">
          删除行
        </NButton>
      </div>
      <div v-for="(cell, cellIndex) in row.cells" :key="cellIndex" class="band-cell">
        <NInput :value="cell.text" size="small" :placeholder="`跨 ${cell.span} 列的表头`" @update:value="patch(rowIndex, cellIndex, { text: $event })" />
        <div class="panel-row">
          <small>跨 {{ cell.span }} 列</small><NButton text size="tiny" :disabled="cellIndex === row.cells.length - 1" @click="merge(rowIndex, cellIndex)">
            合并右侧
          </NButton><NButton text size="tiny" :disabled="cell.span === 1" @click="split(rowIndex, cellIndex)">
            拆分一列
          </NButton>
        </div>
      </div>
    </div>
    <NButton v-if="!table.footer" size="small" @click="addFooter">
      添加合计行
    </NButton>
    <div v-else class="band-row">
      <div class="panel-row">
        <strong>合计行</strong><NButton text type="error" size="tiny" @click="change(item => { delete item.footer })">
          删除合计
        </NButton>
      </div>
      <p class="muted">
        选择平台提供的合计字段；模板不执行汇总脚本。
      </p>
      <div v-for="(cell, cellIndex) in table.footer.cells" :key="cellIndex" class="band-cell">
        <NFormItem label="合计内容" size="small">
          <NSelect :value="cell.binding.source" :options="[{ label: '固定文字', value: 'CONSTANT' }, { label: '数据字段', value: 'FIELD', disabled: !fields.length }]" @update:value="patch(-1, cellIndex, { binding: $event === 'CONSTANT' ? { source: 'CONSTANT', value: '' } : { source: 'FIELD', path: fields[0].value } })" />
        </NFormItem>
        <NInput v-if="cell.binding.source === 'CONSTANT'" :value="String(cell.binding.value ?? '')" size="small" @update:value="patch(-1, cellIndex, { binding: { source: 'CONSTANT', value: $event } })" />
        <NSelect v-else :value="cell.binding.path" :options="fields" size="small" filterable @update:value="patch(-1, cellIndex, { binding: { source: 'FIELD', path: $event } })" />
        <NFormItem label="合计格式" size="small">
          <NSelect :value="cell.format?.type || 'TEXT'" :options="formats" size="small" @update:value="patch(-1, cellIndex, { format: { type: $event } })" />
        </NFormItem>
        <div class="panel-row">
          <small>跨 {{ cell.span }} 列</small><NButton text size="tiny" :disabled="cellIndex === table.footer.cells.length - 1" @click="merge(-1, cellIndex)">
            合并右侧
          </NButton><NButton text size="tiny" :disabled="cell.span === 1" @click="split(-1, cellIndex)">
            拆分一列
          </NButton>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.table-bands {
  margin-top: 14px;
}
.band-row {
  border-top: 1px solid var(--border-light, #ddd);
  margin-top: 8px;
  padding: 8px 0;
}
.band-cell {
  padding: 6px 0;
}
</style>
