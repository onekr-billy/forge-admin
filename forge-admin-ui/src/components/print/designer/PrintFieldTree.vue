<script setup>
import { NInput } from 'naive-ui'
import { computed, ref } from 'vue'
import { usePrintDesignerStore } from '@/stores/print/printDesignerStore'
import { addField, startItemDrag } from './elementCatalog'

const store = usePrintDesignerStore()
const query = ref('')
const fields = computed(() => store.catalog.filter(f => `${f.label || ''} ${f.path}`.toLowerCase().includes(query.value.toLowerCase())))
</script>

<template>
  <section class="designer-group">
    <h3>数据字段</h3>
    <NInput v-model:value="query" size="small" placeholder="查找字段" clearable />
    <p v-if="!fields.length" class="muted">
      暂无可用字段，请先绑定表单数据。
    </p>
    <button v-for="field in fields" :key="field.path" type="button" class="field-row" draggable="true" :title="`拖入或点击添加 ${field.label || field.path}`" @dragstart="startItemDrag($event, { field: field.path })" @click="addField(store, field.path)">
      <span>{{ field.type === 'COLLECTION' ? '▤' : '⋮⋮' }} {{ field.label || field.path.split('.').at(-1) }}</span>
      <small>{{ field.path }}</small>
    </button>
  </section>
</template>

<style scoped>
.field-row {
  display: flex;
  flex-direction: column;
  width: 100%;
  padding: 6px;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 4px;
  color: inherit;
  text-align: left;
  cursor: grab;
}
.field-row:hover {
  border-color: var(--primary-color, #356cde);
  background: var(--gray-100, #f6f8fb);
}
.field-row small {
  color: var(--text-tertiary, #777);
  font-size: 10px;
  overflow-wrap: anywhere;
}
</style>
