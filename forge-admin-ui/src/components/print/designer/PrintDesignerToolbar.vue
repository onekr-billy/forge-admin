<script setup>
import { NButton, NButtonGroup, NSelect } from 'naive-ui'
import { computed } from 'vue'
import { usePrintDesignerStore } from '@/stores/print/printDesignerStore'
import { paperGeometry } from '../protocol/units'

defineProps({ local: Boolean, externalDirty: Boolean })
defineEmits(['new', 'copy', 'save', 'restore', 'protocol'])
const store = usePrintDesignerStore()
const zooms = [0.5, 0.65, 0.8, 1, 1.25, 1.5].map(value => ({ label: `${value * 100}%`, value }))
const geometry = computed(() => paperGeometry(store.document))
const paperName = computed(() => {
  const { widthMm, heightMm } = store.document.paper
  if (widthMm === 210 && heightMm === 297)
    return 'A4'
  if (widthMm === 148 && heightMm === 210)
    return 'A5'
  return '自定义'
})

function preset(widthMm, heightMm) {
  store.execute((doc) => {
    doc.paper.widthMm = widthMm
    doc.paper.heightMm = heightMm
  })
}

function rotate() {
  store.execute((doc) => {
    doc.paper.orientation = doc.paper.orientation === 'PORTRAIT' ? 'LANDSCAPE' : 'PORTRAIT'
  })
}

function zoom(step) {
  const current = Math.max(0, zooms.findIndex(item => item.value === store.zoom))
  const next = Math.min(zooms.length - 1, Math.max(0, current + step))
  store.zoom = zooms[next].value
}
</script>

<template>
  <header class="designer-toolbar">
    <div class="toolbar-identity">
      <strong>打印模板设计</strong>
      <span class="save-state">{{ (store.dirty || externalDirty) ? '未保存' : '已保存' }}{{ local ? ' · 本地草稿' : '' }}</span>
    </div>
    <div class="toolbar-actions toolbar-paper">
      <NButtonGroup size="small">
        <NButton :type="paperName === 'A4' ? 'primary' : 'default'" secondary @click="preset(210, 297)">
          A4
        </NButton>
        <NButton :type="paperName === 'A5' ? 'primary' : 'default'" secondary @click="preset(148, 210)">
          A5
        </NButton>
      </NButtonGroup>
      <NButton size="small" :title="`当前纸张 ${geometry.widthMm} × ${geometry.heightMm} mm`" @click="rotate">
        {{ store.document.paper.orientation === 'PORTRAIT' ? '转为横向' : '转为纵向' }}
      </NButton>
      <NButton size="small" :type="store.showGrid ? 'primary' : 'default'" secondary @click="store.showGrid = !store.showGrid">
        网格
      </NButton>
    </div>
    <div class="toolbar-divider" />
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
      <NButton size="small" aria-label="缩小画布" :disabled="store.zoom === zooms[0].value" @click="zoom(-1)">
        −
      </NButton>
      <NSelect v-model:value="store.zoom" aria-label="画布缩放" :options="zooms" size="small" style="width: 82px" />
      <NButton size="small" aria-label="放大画布" :disabled="store.zoom === zooms.at(-1).value" @click="zoom(1)">
        +
      </NButton>
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
  gap: 7px;
  min-height: 44px;
  padding: 5px 10px;
  overflow-x: auto;
  border-bottom: 1px solid var(--border-light, #ddd);
  background: var(--bg-primary, #fff);
  scrollbar-width: thin;
}
.designer-toolbar strong {
  font-size: 14px;
  white-space: nowrap;
}
.toolbar-identity,
.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 5px;
  white-space: nowrap;
}
.toolbar-identity {
  gap: 7px;
  margin-right: 4px;
}
.save-state {
  color: var(--text-tertiary, #777);
  font-size: 12px;
  white-space: nowrap;
}
.toolbar-paper {
  padding: 0 2px;
}
.toolbar-divider {
  align-self: stretch;
  width: 1px;
  margin: 4px 1px;
  background: var(--border-light, #ddd);
}
.toolbar-actions:last-child {
  margin-left: auto;
}
</style>
