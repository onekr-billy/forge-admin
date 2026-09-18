<script setup>
import { NButton, NSelect } from 'naive-ui'
import { usePrintDesignerStore } from '@/stores/print/printDesignerStore'

defineProps({ local: Boolean, externalDirty: Boolean })
defineEmits(['new', 'copy', 'save', 'restore', 'protocol'])
const store = usePrintDesignerStore()
const zooms = [0.5, 0.65, 0.8, 1, 1.25, 1.5].map(value => ({ label: `${value * 100}%`, value }))
</script>

<template>
  <header class="designer-toolbar">
    <strong>打印模板设计</strong><span class="save-state">{{ (store.dirty || externalDirty) ? '未保存' : '已保存' }}{{ local ? ' · 本地草稿' : '' }}</span>
    <div class="toolbar-actions">
      <NButton v-if="local" size="small" :disabled="store.saving" @click="$emit('new')">
        新建
      </NButton>
      <NButton v-if="local" size="small" :disabled="store.saving" @click="$emit('copy')">
        复制模板
      </NButton>
      <NButton size="small" :disabled="!store.canUndo" @click="store.undo()">
        撤销
      </NButton>
      <NButton size="small" :disabled="!store.canRedo" @click="store.redo()">
        重做
      </NButton>
      <NSelect v-model:value="store.zoom" aria-label="画布缩放" :options="zooms" size="small" style="width: 88px" />
      <NButton v-if="local" size="small" :disabled="store.saving" @click="$emit('restore')">
        恢复本地草稿
      </NButton>
      <NButton size="small" @click="$emit('protocol')">
        导入 / 导出
      </NButton>
      <NButton size="small" :disabled="!!store.gesture || store.fieldIssues.length > 0" @click="store.previewOpen = true">
        预览
      </NButton>
      <NButton size="small" type="primary" :loading="store.saving" :disabled="!!store.gesture" @click="$emit('save')">
        {{ local ? '保存本地草稿' : '保存草稿' }}
      </NButton>
    </div>
  </header>
</template>

<style scoped>
.designer-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  flex-wrap: wrap;
  border-bottom: 1px solid var(--border-light, #ddd);
}
.designer-toolbar strong {
  font-size: 15px;
}
.save-state {
  font-size: 12px;
  color: var(--text-tertiary, #777);
}
.toolbar-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-left: auto;
}
</style>
