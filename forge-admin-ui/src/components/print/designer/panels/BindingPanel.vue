<script setup>
import { NFormItem, NInput, NSelect } from 'naive-ui'
import { computed } from 'vue'
import { usePrintDesignerStore } from '@/stores/print/printDesignerStore'

const store = usePrintDesignerStore()
const target = computed(() => store.activeElement || (store.activeSurface?.kind === 'TEXT' ? store.activeSurface : null))
const sources = [{ label: '固定内容', value: 'CONSTANT' }, { label: '数据字段', value: 'FIELD' }, { label: '生成时间', value: 'SYSTEM' }]
const fields = computed(() => store.catalog.filter(f => f.type !== 'COLLECTION' && !store.catalog.some(c => c.type === 'COLLECTION' && f.path.startsWith(`${c.path}.`))).map(f => ({ label: f.label || f.path, value: f.path })))
function patch(binding) {
  if (store.activeElement)
    store.patchSelected({ binding })
  else store.patchSurface({ binding })
}
function source(value) {
  if (value === 'CONSTANT')
    patch({ source: value, value: '' })
  else if (value === 'SYSTEM')
    patch({ source: value, path: 'system.generatedAt' })
  else if (fields.value.length)
    patch({ source: value, path: fields.value[0].value })
  else store.error = '当前字段目录没有可绑定的主字段'
}
</script>

<template>
  <section v-if="target?.binding" class="designer-group">
    <h3>内容绑定</h3>
    <NFormItem label="内容来源" size="small">
      <NSelect :value="target.binding.source" :options="sources" @update:value="source" />
    </NFormItem>
    <NFormItem v-if="target.binding.source === 'CONSTANT'" :label="target.type === 'IMAGE' ? '受控文件 ID' : '固定内容'" size="small">
      <NInput :value="String(target.binding.value ?? '')" type="textarea" :autosize="{ minRows: 2, maxRows: 5 }" @update:value="patch({ source: 'CONSTANT', value: $event })" />
    </NFormItem>
    <NFormItem v-if="target.binding.source === 'FIELD'" label="字段" size="small">
      <NSelect :value="target.binding.path" filterable :options="fields" @update:value="patch({ source: 'FIELD', path: $event })" />
    </NFormItem>
    <p v-if="target.type === 'IMAGE'" class="muted">
      图片在预览时鉴权加载。
    </p>
  </section>
</template>
