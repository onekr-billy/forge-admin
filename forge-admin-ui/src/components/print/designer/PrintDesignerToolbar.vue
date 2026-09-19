<script setup>
import {
  AddOutline,
  ArrowRedoOutline,
  ArrowUndoOutline,
  CodeSlashOutline,
  EllipsisHorizontalOutline,
  EyeOutline,
  GridOutline,
  LayersOutline,
  OptionsOutline,
  RemoveOutline,
  SaveOutline,
  SwapHorizontalOutline,
} from '@vicons/ionicons5'
import { NButton, NDropdown, NIcon, NSelect } from 'naive-ui'
import { computed, h } from 'vue'
import { usePrintDesignerStore } from '@/stores/print/printDesignerStore'
import { paperGeometry } from '../protocol/units'

const props = defineProps({ local: Boolean, externalDirty: Boolean })
const emit = defineEmits(['new', 'copy', 'save', 'restore', 'protocol'])
const store = usePrintDesignerStore()
const zooms = [0.5, 0.65, 0.8, 1, 1.25, 1.5].map(value => ({ label: `${value * 100}%`, value }))
const geometry = computed(() => paperGeometry(store.document))
const paperOptions = [
  { label: 'A4 · 210 × 297 mm', value: 'A4' },
  { label: 'A5 · 148 × 210 mm', value: 'A5' },
]
const paperName = computed(() => {
  const { widthMm, heightMm } = store.document.paper
  if (widthMm === 210 && heightMm === 297)
    return 'A4'
  if (widthMm === 148 && heightMm === 210)
    return 'A5'
  return 'CUSTOM'
})
const paperSelectOptions = computed(() => {
  if (paperName.value === 'CUSTOM')
    return [{ label: `自定义 · ${geometry.value.widthMm} × ${geometry.value.heightMm} mm`, value: 'CUSTOM', disabled: true }, ...paperOptions]
  return paperOptions
})
const moreOptions = computed(() => {
  const localOptions = props.local
    ? [
        { label: '新建模板', key: 'new' },
        { label: '复制当前模板', key: 'copy' },
        { label: '恢复本地草稿', key: 'restore' },
        { type: 'divider', key: 'local-divider' },
      ]
    : []
  return [
    ...localOptions,
    { label: '导入 / 导出协议', key: 'protocol', icon: () => h(NIcon, null, { default: () => h(CodeSlashOutline) }) },
  ]
})

function setPaper(widthMm, heightMm) {
  store.execute((doc) => {
    doc.paper.widthMm = widthMm
    doc.paper.heightMm = heightMm
  })
}

function preset(value) {
  if (value === 'A4')
    setPaper(210, 297)
  else if (value === 'A5')
    setPaper(148, 210)
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

function handleMore(key) {
  if (['new', 'copy', 'restore', 'protocol'].includes(key))
    emit(key)
}
</script>

<template>
  <header class="designer-toolbar">
    <div class="toolbar-identity" :title="local ? '打印模板设计 · 本地草稿' : '打印模板设计'">
      <strong>打印设计</strong>
      <i class="save-dot" :class="{ dirty: store.dirty || externalDirty }" />
      <span class="save-state">{{ (store.dirty || externalDirty) ? '未保存' : '已保存' }}</span>
    </div>

    <div class="command-group panel-commands">
      <button type="button" class="tool-button" :class="{ active: store.leftPanelOpen }" title="显示或隐藏组件面板" aria-label="显示或隐藏组件面板" @click="store.leftPanelOpen = !store.leftPanelOpen">
        <NIcon :component="LayersOutline" />
      </button>
      <button type="button" class="tool-button" :class="{ active: store.rightPanelOpen }" title="显示或隐藏属性面板" aria-label="显示或隐藏属性面板" @click="store.rightPanelOpen = !store.rightPanelOpen">
        <NIcon :component="OptionsOutline" />
      </button>
    </div>

    <div class="command-group history-commands">
      <button type="button" class="tool-button" title="撤销" aria-label="撤销" :disabled="!store.canUndo" @click="store.undo()">
        <NIcon :component="ArrowUndoOutline" />
      </button>
      <button type="button" class="tool-button" title="重做" aria-label="重做" :disabled="!store.canRedo" @click="store.redo()">
        <NIcon :component="ArrowRedoOutline" />
      </button>
    </div>

    <div class="command-group paper-commands">
      <NSelect :value="paperName" aria-label="纸张规格" :options="paperSelectOptions" size="small" class="paper-select" @update:value="preset" />
      <button type="button" class="tool-button" :title="`${store.document.paper.orientation === 'PORTRAIT' ? '转为横向' : '转为纵向'} · 当前 ${geometry.widthMm} × ${geometry.heightMm} mm`" :aria-label="store.document.paper.orientation === 'PORTRAIT' ? '转为横向' : '转为纵向'" @click="rotate">
        <NIcon :component="SwapHorizontalOutline" />
      </button>
      <button type="button" class="tool-button" :class="{ active: store.showGrid }" title="显示或隐藏毫米网格" aria-label="显示或隐藏毫米网格" @click="store.showGrid = !store.showGrid">
        <NIcon :component="GridOutline" />
      </button>
    </div>

    <div class="command-group zoom-commands">
      <button type="button" class="tool-button" title="缩小画布" aria-label="缩小画布" :disabled="store.zoom === zooms[0].value" @click="zoom(-1)">
        <NIcon :component="RemoveOutline" />
      </button>
      <NSelect v-model:value="store.zoom" aria-label="画布缩放" :options="zooms" size="small" class="zoom-select" />
      <button type="button" class="tool-button" title="放大画布" aria-label="放大画布" :disabled="store.zoom === zooms.at(-1).value" @click="zoom(1)">
        <NIcon :component="AddOutline" />
      </button>
    </div>

    <div class="toolbar-spacer" />
    <div class="command-group final-commands">
      <button type="button" class="tool-button" title="预览打印结果" aria-label="预览打印结果" :disabled="!!store.gesture || store.fieldIssues.length > 0" @click="store.previewOpen = true">
        <NIcon :component="EyeOutline" />
      </button>
      <NDropdown trigger="click" :options="moreOptions" @select="handleMore">
        <button type="button" class="tool-button" title="更多操作" aria-label="更多操作" :disabled="store.saving">
          <NIcon :component="EllipsisHorizontalOutline" />
        </button>
      </NDropdown>
      <NButton size="small" type="primary" :loading="store.saving" :disabled="!!store.gesture" class="save-button" @click="$emit('save')">
        <template #icon>
          <NIcon :component="SaveOutline" />
        </template>
        保存
      </NButton>
    </div>
  </header>
</template>

<style scoped>
.designer-toolbar {
  display: flex;
  align-items: center;
  gap: 6px;
  min-height: 44px;
  padding: 5px 8px;
  overflow-x: auto;
  border-bottom: 1px solid var(--border-light, #ddd);
  background: var(--bg-primary, #fff);
  scrollbar-width: thin;
}
.toolbar-identity,
.command-group {
  display: flex;
  align-items: center;
  flex: none;
}
.toolbar-identity {
  gap: 6px;
  padding: 0 5px 0 3px;
}
.toolbar-identity strong {
  font-size: 13px;
  white-space: nowrap;
}
.save-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #22c55e;
}
.save-dot.dirty {
  background: #f59e0b;
}
.save-state {
  color: var(--text-tertiary, #777);
  font-size: 11px;
  white-space: nowrap;
}
.command-group {
  gap: 2px;
  padding-left: 6px;
  border-left: 1px solid var(--border-light, #ddd);
}
.tool-button {
  width: 30px;
  height: 30px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 1px solid transparent;
  border-radius: 5px;
  color: var(--text-primary, #334155);
  background: transparent;
  cursor: pointer;
  font-size: 17px;
}
.tool-button:hover:not(:disabled),
.tool-button.active {
  color: var(--primary-color, #356cde);
  border-color: color-mix(in srgb, var(--primary-color, #356cde) 25%, transparent);
  background: color-mix(in srgb, var(--primary-color, #356cde) 9%, transparent);
}
.tool-button:disabled {
  opacity: 0.32;
  cursor: not-allowed;
}
.paper-select {
  width: 154px;
}
.zoom-select {
  width: 78px;
}
.toolbar-spacer {
  flex: 1 1 auto;
  min-width: 6px;
}
.save-button {
  min-width: 70px;
}
@media (max-width: 840px) {
  .toolbar-identity .save-state,
  .history-commands {
    display: none;
  }
  .paper-select {
    width: 120px;
  }
}
</style>
