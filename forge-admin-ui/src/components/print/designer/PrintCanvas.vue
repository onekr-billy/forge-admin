<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'
import { usePrintDesignerStore } from '@/stores/print/printDesignerStore'
import { paperGeometry, screenDeltaToMm } from '../protocol/units'
import { addElement, addField, PRINT_DRAG_TYPE } from './elementCatalog'
import PrintCanvasElement from './PrintCanvasElement.vue'
import PrintSelectionOverlay from './PrintSelectionOverlay.vue'
import { usePrintDrag } from './usePrintDrag'
import { usePrintKeyboard } from './usePrintKeyboard'

const store = usePrintDesignerStore()
const drag = usePrintDrag(store)
const keyboard = usePrintKeyboard(store)
const geometry = computed(() => paperGeometry(store.document))
const surfaces = computed(() => [
  { id: 'header', label: '页眉', ...store.document.header },
  ...store.document.body.map((s, i) => ({ ...s, id: `section:${s.id}`, label: `${i + 1}. ${{ FIXED: '固定区块', TEXT: '流式文本', TABLE: '明细表格' }[s.kind]}` })),
  { id: 'footer', label: '页脚', ...store.document.footer },
])
const marquee = ref(null)
let clearMarquee = () => {}

function elementDown(event, surfaceId, id) {
  store.selectSurface(surfaceId)
  event.currentTarget.closest('.print-canvas').focus({ preventScroll: true })
  if (event.shiftKey || event.metaKey || event.ctrlKey) {
    store.selectElement(id, true)
    return
  }
  if (!store.selectedIds.includes(id))
    store.selectElement(id)
  drag.start(event)
}

function surfaceDown(event, surface) {
  if (event.button !== 0)
    return
  clearMarquee()
  store.selectSurface(surface.id)
  event.currentTarget.closest('.print-canvas').focus({ preventScroll: true })
  const previous = event.shiftKey ? [...store.selectedIds] : []
  store.selectedIds = previous
  if (!surface.elements)
    return
  const rect = event.currentTarget.getBoundingClientRect()
  const x = screenDeltaToMm(event.clientX - rect.left, store.zoom)
  const y = screenDeltaToMm(event.clientY - rect.top, store.zoom)
  const move = (next) => {
    const dx = screenDeltaToMm(next.clientX - rect.left, store.zoom)
    const dy = screenDeltaToMm(next.clientY - rect.top, store.zoom)
    const box = { x: Math.min(x, dx), y: Math.min(y, dy), w: Math.abs(dx - x), h: Math.abs(dy - y), id: surface.id }
    marquee.value = box
    store.selectedIds = [...new Set([...previous, ...surface.elements.filter(e => e.xMm < box.x + box.w && e.xMm + e.widthMm > box.x && e.yMm < box.y + box.h && e.yMm + e.heightMm > box.y).map(e => e.id)])]
  }
  const finish = () => clearMarquee()
  window.addEventListener('pointermove', move)
  window.addEventListener('pointerup', finish)
  window.addEventListener('pointercancel', finish)
  window.addEventListener('blur', finish)
  clearMarquee = () => {
    window.removeEventListener('pointermove', move)
    window.removeEventListener('pointerup', finish)
    window.removeEventListener('pointercancel', finish)
    window.removeEventListener('blur', finish)
    marquee.value = null
    clearMarquee = () => {}
  }
}

function drop(event, surface) {
  store.selectSurface(surface.id)
  try {
    const item = JSON.parse(event.dataTransfer.getData(PRINT_DRAG_TYPE))
    const rect = event.currentTarget.getBoundingClientRect()
    const position = { xMm: screenDeltaToMm(event.clientX - rect.left, store.zoom), yMm: screenDeltaToMm(event.clientY - rect.top, store.zoom) }
    if (item.field)
      addField(store, item.field, position)
    else if (item.type)
      addElement(store, item.type, undefined, position)
  }
  catch {
    store.error = '请从左侧物料或字段区拖入内容'
  }
}
onBeforeUnmount(() => clearMarquee())
</script>

<template>
  <div class="print-canvas" tabindex="0" aria-label="打印编辑画布" @keydown="keyboard">
    <div class="canvas-note">
      区块编辑视图 · 实际分页以预览为准 · Shift 多选 · 方向键移动 1 mm
    </div>
    <div class="paper-holder" :style="{ zoom: store.zoom }">
      <div class="design-paper" :style="{ width: `${geometry.widthMm}mm`, minHeight: `${geometry.heightMm}mm`, padding: `${store.document.paper.marginMm.top}mm ${store.document.paper.marginMm.right}mm ${store.document.paper.marginMm.bottom}mm ${store.document.paper.marginMm.left}mm` }">
        <section v-for="surface in surfaces" :key="surface.id" :data-surface-id="surface.id" class="design-surface" :class="{ active: store.surfaceId === surface.id }" :style="{ minHeight: `${Math.max(surface.heightMm || 0, surface.kind === 'TABLE' ? 24 : surface.kind === 'TEXT' ? 16 : 6)}mm`, marginBottom: `${surface.gapAfterMm || 0}mm` }" @pointerdown.self="surfaceDown($event, surface)" @dragover.prevent @drop.prevent.stop="drop($event, surface)">
          <span class="surface-label">{{ surface.label }}</span>
          <PrintCanvasElement v-for="element in surface.elements || []" :key="element.id" :element="element" :selected="store.surfaceId === surface.id && store.selectedIds.includes(element.id)" @pointerdown.stop="elementDown($event, surface.id, element.id)" />
          <div v-if="surface.kind === 'TEXT'" class="flow-text" @pointerdown="surfaceDown($event, surface)">
            {{ surface.binding?.source === 'CONSTANT' ? surface.binding.value : surface.binding?.path }}
          </div>
          <div v-if="surface.kind === 'TABLE'" class="table-sketch" @pointerdown="surfaceDown($event, surface)">
            <div v-for="column in surface.columns" :key="column.id" :style="{ width: `${column.widthMm}mm` }">
              <strong>{{ column.title }}</strong><span>{{ column.field }}</span>
            </div>
          </div>
          <PrintSelectionOverlay v-if="store.surfaceId === surface.id" />
          <div v-if="marquee?.id === surface.id" class="marquee" :style="{ left: `${marquee.x}mm`, top: `${marquee.y}mm`, width: `${marquee.w}mm`, height: `${marquee.h}mm` }" />
        </section>
      </div>
    </div>
  </div>
</template>

<style scoped>
.print-canvas {
  min-width: 0;
  min-height: 0;
  overflow: auto;
  background: var(--gray-100, #f0f2f5);
  padding: 12px;
  outline: none;
}
.canvas-note {
  color: var(--text-tertiary, #777);
  font-size: 11px;
  margin-bottom: 12px;
}
.paper-holder {
  width: max-content;
  margin: 0 auto;
  padding: 1px;
}
.design-paper {
  box-sizing: border-box;
  background: #fff;
  color: #111;
  border: 1px solid #cbd0d7;
}
.design-surface {
  position: relative;
  box-sizing: border-box;
  outline: 1px dashed #cbd0d7;
  touch-action: none;
}
.design-surface.active {
  outline-color: var(--primary-color, #356cde);
}
.surface-label {
  position: absolute;
  right: 1mm;
  top: 0;
  font-size: 8px;
  color: #787e87;
  pointer-events: none;
}
.flow-text {
  padding: 3mm 1mm;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  cursor: pointer;
}
.table-sketch {
  display: flex;
  padding-top: 4mm;
  cursor: pointer;
  font:
    10pt Arial,
    sans-serif;
}
.table-sketch > div {
  box-sizing: border-box;
  border: 1px solid #bfc4cc;
  flex-shrink: 0;
}
.table-sketch strong,
.table-sketch span {
  display: block;
  padding: 1mm;
  overflow-wrap: anywhere;
}
.table-sketch span {
  border-top: 1px solid #bfc4cc;
  color: #666;
  font-size: 9pt;
}
.marquee {
  position: absolute;
  pointer-events: none;
  border: 1px solid var(--primary-color, #356cde);
  background: #356cde18;
}
</style>
