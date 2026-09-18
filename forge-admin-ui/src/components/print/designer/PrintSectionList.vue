<script setup>
import { NButton } from 'naive-ui'
import { usePrintDesignerStore } from '@/stores/print/printDesignerStore'
import { newPrintId } from './commands'
import { cloneDocument } from './history'

const store = usePrintDesignerStore()
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
</script>

<template>
  <section class="designer-group">
    <h3>区块顺序</h3>
    <NButton size="small" :type="store.surfaceId === 'header' ? 'primary' : 'default'" secondary @click="store.selectSurface('header')">
      页眉
    </NButton>
    <div v-for="(section, index) in store.document.body" :key="section.id" class="section-row" :class="{ active: store.surfaceId === `section:${section.id}` }" draggable="true" @dragstart="$event.dataTransfer.setData('application/x-forge-print-section', section.id)" @dragover.prevent @drop.prevent="move($event.dataTransfer.getData('application/x-forge-print-section'), index)">
      <button type="button" class="section-name" @click="store.selectSurface(`section:${section.id}`)">
        ⋮⋮ {{ index + 1 }}. {{ { FIXED: '固定区块', TEXT: '流式文本', TABLE: '明细表格' }[section.kind] }}
      </button>
      <div class="section-actions">
        <NButton text size="tiny" :disabled="index === 0" aria-label="区块上移" @click="move(section.id, index - 1)">
          上移
        </NButton>
        <NButton text size="tiny" :disabled="index === store.document.body.length - 1" aria-label="区块下移" @click="move(section.id, index + 1)">
          下移
        </NButton>
        <NButton text size="tiny" @click="duplicate(section)">
          复制
        </NButton>
        <NButton text size="tiny" type="error" @click="remove(section.id)">
          删除
        </NButton>
      </div>
    </div>
    <NButton size="small" :type="store.surfaceId === 'footer' ? 'primary' : 'default'" secondary @click="store.selectSurface('footer')">
      页脚
    </NButton>
  </section>
</template>

<style scoped>
.section-row {
  border: 1px solid var(--border-light, #ddd);
  border-radius: 4px;
  margin: 6px 0;
  padding: 4px;
}
.section-row.active {
  border-color: var(--primary-color, #356cde);
}
.section-name {
  border: 0;
  background: transparent;
  color: inherit;
  text-align: left;
  width: 100%;
  cursor: grab;
}
.section-actions {
  display: flex;
  gap: 10px;
  padding: 3px;
}
</style>
