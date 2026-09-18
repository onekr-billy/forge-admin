<script setup>
import { NButton, NFormItem, NInput, NInputNumber, NSelect, NSwitch } from 'naive-ui'
import { computed } from 'vue'
import { usePrintDesignerStore } from '@/stores/print/printDesignerStore'
import { findSurface } from '../commands'
import TableBandsPanel from './TableBandsPanel.vue'

const store = usePrintDesignerStore()
const table = computed(() => store.activeSurface?.kind === 'TABLE' ? store.activeSurface : null)
const fields = computed(() => store.catalog.filter(f => f.type !== 'COLLECTION' && f.path.startsWith(`${table.value?.collectionPath}.`)).map(f => ({ label: f.label || f.path, value: f.path.slice(table.value.collectionPath.length + 1) })))
const formats = [{ label: '文本', value: 'TEXT' }, { label: '金额', value: 'MONEY' }, { label: '数字', value: 'NUMBER' }, { label: '日期', value: 'DATE' }, { label: '布尔', value: 'BOOLEAN' }]
function column(id, patch) {
  store.execute(doc => Object.assign(findSurface(doc, store.surfaceId).columns.find(c => c.id === id), patch))
}
function reorder(index, offset) {
  store.execute((doc) => {
    const item = findSurface(doc, store.surfaceId)
    if (item.headerRows || item.footer)
      throw new Error('请先清除合并表头/表尾配置，再调整列顺序')
    const [value] = item.columns.splice(index, 1)
    item.columns.splice(index + offset, 0, value)
  })
}
function remove(id) {
  store.execute((doc) => {
    const item = findSurface(doc, store.surfaceId)
    if (item.headerRows || item.footer)
      throw new Error('请先清除合并表头/表尾配置，再删除列')
    item.columns = item.columns.filter(c => c.id !== id)
  })
}
</script>

<template>
  <section v-if="table" class="designer-group">
    <h3>明细表格</h3>
    <p class="muted">
      {{ table.collectionPath }} · 从左侧字段区添加列
    </p>
    <NFormItem label="续页重复表头" size="small">
      <NSwitch :value="table.repeatHeader" @update:value="store.patchSurface({ repeatHeader: $event })" />
    </NFormItem>
    <NFormItem label="无明细文案" size="small">
      <NInput :value="table.emptyText || ''" @update:value="store.patchSurface({ emptyText: $event })" />
    </NFormItem>
    <div v-for="(item, index) in table.columns" :key="item.id" class="column-editor">
      <NFormItem :label="`第 ${index + 1} 列标题`" size="small">
        <NInput :value="item.title" @update:value="column(item.id, { title: $event })" />
      </NFormItem>
      <NFormItem label="明细字段" size="small">
        <NSelect :value="item.field" :options="fields" filterable @update:value="column(item.id, { field: $event })" />
      </NFormItem>
      <NFormItem label="宽度 mm" size="small">
        <NInputNumber :value="item.widthMm" :min="1" @update:value="$event !== null && column(item.id, { widthMm: $event })" />
      </NFormItem>
      <NFormItem label="格式" size="small">
        <NSelect :value="item.format?.type || 'TEXT'" :options="formats" @update:value="column(item.id, { format: { type: $event } })" />
      </NFormItem>
      <div class="panel-row">
        <NButton size="tiny" :disabled="index === 0" @click="reorder(index, -1)">
          前移
        </NButton><NButton size="tiny" :disabled="index === table.columns.length - 1" @click="reorder(index, 1)">
          后移
        </NButton><NButton size="tiny" :disabled="table.columns.length === 1" type="error" secondary @click="remove(item.id)">
          删除列
        </NButton>
      </div>
    </div>
    <TableBandsPanel />
    <p v-if="table.headerRows || table.footer" class="muted">
      当前模板含合并表头/表尾；内容保留并在预览输出。
    </p>
    <NButton v-if="table.headerRows || table.footer" size="small" @click="store.execute(doc => { const item = findSurface(doc, store.surfaceId); delete item.headerRows; delete item.footer })">
      清除合并表头/表尾
    </NButton>
  </section>
</template>

<style scoped>
.column-editor {
  padding: 8px 0;
  border-top: 1px solid var(--border-light, #ddd);
}
</style>
