<script setup>
import { ChevronDownOutline, ChevronUpOutline, EllipsisHorizontalOutline } from '@vicons/ionicons5'
import { NDropdown, NIcon } from 'naive-ui'
import { usePrintDesignerStore } from '@/stores/print/printDesignerStore'
import { newPrintId } from './commands'
import { cloneDocument } from './history'

const store = usePrintDesignerStore()
const rowOptions = [
  { label: '复制区块', key: 'duplicate' },
  { type: 'divider', key: 'divider' },
  { label: '删除区块', key: 'remove' },
]

function move(id, target) {
  store.execute((doc) => {
    const index = doc.body.findIndex(s => s.id === id)
    if (index < 0 || target < 0 || target >= doc.body.length)
      return
    const [section] = doc.body.splice(index, 1)
    doc.body.splice(target, 0, section)
  })
}

function duplicate(section) {
  const copy = cloneDocument(section)
  copy.id = newPrintId()
  for (const element of copy.elements || []) element.id = newPrintId()
  for (const column of copy.columns || []) column.id = newPrintId()
  if (store.execute(doc => doc.body.splice(doc.body.findIndex(s => s.id === section.id) + 1, 0, copy)))
    store.selectSurface(copy.id)
}

function remove(id) {
  store.execute((doc) => {
    doc.body = doc.body.filter(s => s.id !== id)
  })
}

function handleRowAction(key, section) {
  if (key === 'duplicate')
    duplicate(section)
  else if (key === 'remove')
    remove(section.id)
}
</script>

<template>
  <section class="designer-group section-list">
    <h3>页面结构</h3>
    <button type="button" class="fixed-row" :class="{ active: store.surfaceId === 'header' }" @click="store.selectSurface('header')">
      <span class="row-badge">H</span><span>页眉</span>
    </button>
    <div
      v-for="(section, index) in store.document.body"
      :key="section.id"
      class="section-row"
      :class="{ active: store.surfaceId === `section:${section.id}` }"
      draggable="true"
      @dragstart="$event.dataTransfer.setData('application/x-forge-print-section', section.id)"
      @dragover.prevent
      @drop.prevent="move($event.dataTransfer.getData('application/x-forge-print-section'), index)"
    >
      <button type="button" class="section-name" @click="store.selectSurface(`section:${section.id}`)">
        <span class="drag-handle">⠿</span>
        <span class="section-index">{{ index + 1 }}</span>
        <span class="section-label">{{ { FIXED: '固定区块', TEXT: '流式文本', TABLE: '明细表格' }[section.kind] }}</span>
      </button>
      <div class="section-actions">
        <button type="button" class="row-action" title="上移区块" aria-label="上移区块" :disabled="index === 0" @click="move(section.id, index - 1)">
          <NIcon :component="ChevronUpOutline" />
        </button>
        <button type="button" class="row-action" title="下移区块" aria-label="下移区块" :disabled="index === store.document.body.length - 1" @click="move(section.id, index + 1)">
          <NIcon :component="ChevronDownOutline" />
        </button>
        <NDropdown trigger="click" :options="rowOptions" @select="handleRowAction($event, section)">
          <button type="button" class="row-action" title="更多区块操作" aria-label="更多区块操作">
            <NIcon :component="EllipsisHorizontalOutline" />
          </button>
        </NDropdown>
      </div>
    </div>
    <button type="button" class="fixed-row" :class="{ active: store.surfaceId === 'footer' }" @click="store.selectSurface('footer')">
      <span class="row-badge">F</span><span>页脚</span>
    </button>
  </section>
</template>

<style scoped>
.section-list {
  padding-bottom: 12px !important;
}
.section-row,
.fixed-row {
  width: 100%;
  min-height: 34px;
  display: flex;
  align-items: center;
  box-sizing: border-box;
  margin: 3px 0;
  border: 1px solid transparent;
  border-radius: 5px;
  color: inherit;
  background: transparent;
}
.fixed-row {
  gap: 8px;
  padding: 0 8px;
  cursor: pointer;
  font-size: 12px;
}
.section-row:hover,
.fixed-row:hover {
  background: color-mix(in srgb, var(--primary-color, #356cde) 5%, transparent);
}
.section-row.active,
.fixed-row.active {
  border-color: color-mix(in srgb, var(--primary-color, #356cde) 28%, transparent);
  background: color-mix(in srgb, var(--primary-color, #356cde) 9%, transparent);
}
.row-badge,
.section-index {
  width: 20px;
  height: 20px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: none;
  border-radius: 4px;
  color: var(--text-tertiary, #64748b);
  background: color-mix(in srgb, var(--text-tertiary, #64748b) 9%, transparent);
  font-size: 10px;
}
.section-name {
  min-width: 0;
  flex: 1;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 5px 2px 5px 5px;
  border: 0;
  color: inherit;
  background: transparent;
  text-align: left;
  cursor: grab;
}
.drag-handle {
  color: var(--text-tertiary, #94a3b8);
  font-size: 14px;
}
.section-label {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
}
.section-actions {
  display: flex;
  align-items: center;
  padding-right: 3px;
  opacity: 0;
  transition: opacity 0.12s ease;
}
.section-row:hover .section-actions,
.section-row.active .section-actions,
.section-actions:focus-within {
  opacity: 1;
}
.row-action {
  width: 24px;
  height: 24px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 0;
  border-radius: 4px;
  color: var(--text-tertiary, #64748b);
  background: transparent;
  cursor: pointer;
  font-size: 14px;
}
.row-action:hover:not(:disabled) {
  color: var(--primary-color, #356cde);
  background: color-mix(in srgb, var(--primary-color, #356cde) 10%, transparent);
}
.row-action:disabled {
  opacity: 0.25;
  cursor: not-allowed;
}
</style>
