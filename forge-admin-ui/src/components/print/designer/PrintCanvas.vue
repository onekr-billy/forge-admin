<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'
import { usePrintDesignerStore } from '@/stores/print/printDesignerStore'
import { paperGeometry, screenDeltaToMm } from '../protocol/units'
import { cellStyle, printStyle } from '../renderers/style'
import { designerBindingText, designerTablePreview } from './designerSample'
import { addElement, addField, PRINT_DRAG_TYPE } from './elementCatalog'
import PrintCanvasActionBar from './PrintCanvasActionBar.vue'
import PrintCanvasElement from './PrintCanvasElement.vue'
import PrintRuler from './PrintRuler.vue'
import PrintSelectionOverlay from './PrintSelectionOverlay.vue'
import { usePrintDrag } from './usePrintDrag'
import { usePrintKeyboard } from './usePrintKeyboard'

const props = defineProps({ context: { type: Object, default: () => ({}) } })
const store = usePrintDesignerStore()
const drag = usePrintDrag(store)
const keyboard = usePrintKeyboard(store)
const geometry = computed(() => paperGeometry(store.document))
const surfaces = computed(() => [
  { id: 'header', label: '页眉', ...store.document.header },
  ...store.document.body.map((s, i) => ({ ...s, id: `section:${s.id}`, label: `${i + 1}. ${{ FIXED: '固定区块', TEXT: '流式文本', TABLE: '明细表格' }[s.kind]}` })),
  { id: 'footer', label: '页脚', ...store.document.footer },
])
const paperName = computed(() => {
  const { widthMm, heightMm } = store.document.paper
  if (widthMm === 297 && heightMm === 420)
    return 'A3'
  if (widthMm === 210 && heightMm === 297)
    return 'A4'
  if (widthMm === 148 && heightMm === 210)
    return 'A5'
  if (widthMm === 250 && heightMm === 353)
    return 'B4'
  if (widthMm === 176 && heightMm === 250)
    return 'B5'
  return '自定义'
})
const marquee = ref(null)
let clearMarquee = () => {}

function flowText(surface) {
  return designerBindingText(surface.binding, surface.format, store.catalog, props.context)
}

function tableRows(surface) {
  return designerTablePreview(surface, store.catalog, props.context)
}

function tableCellStyle(cell) {
  return { ...cellStyle(cell.style), width: `${cell.widthMm}mm`, flex: 'none' }
}

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
      addElement(store, item.type, undefined, position, item.preset)
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
      <span>{{ paperName }} · {{ geometry.widthMm }} × {{ geometry.heightMm }} mm</span>
      <PrintCanvasActionBar />
      <span>Shift 多选 · ⌘/Ctrl A/C/V · 方向键移动</span>
    </div>
    <div class="canvas-viewport">
      <div class="paper-holder" :style="{ zoom: store.zoom }">
        <div
          class="ruler-frame"
          :style="{
            gridTemplateColumns: `7mm ${geometry.widthMm}mm`,
            gridTemplateRows: `7mm minmax(${geometry.heightMm}mm, max-content)`,
          }"
        >
          <div class="ruler-corner" />
          <PrintRuler :length-mm="geometry.widthMm" orientation="horizontal" />
          <PrintRuler :length-mm="geometry.heightMm" orientation="vertical" />
          <div
            class="design-paper"
            :class="{ 'paper-grid': store.showGrid }"
            :style="{
              width: `${geometry.widthMm}mm`,
              minHeight: `${geometry.heightMm}mm`,
              padding: `${store.document.paper.marginMm.top}mm ${store.document.paper.marginMm.right}mm ${store.document.paper.marginMm.bottom}mm ${store.document.paper.marginMm.left}mm`,
            }"
          >
            <div
              class="margin-guide"
              :style="{
                top: `${store.document.paper.marginMm.top}mm`,
                right: `${store.document.paper.marginMm.right}mm`,
                bottom: `${store.document.paper.marginMm.bottom}mm`,
                left: `${store.document.paper.marginMm.left}mm`,
              }"
            />
            <div class="paper-guide header-guide" :style="{ top: `${store.document.paper.marginMm.top + store.document.header.heightMm}mm` }">
              <span>页眉线</span>
            </div>
            <div class="paper-guide footer-guide" :style="{ top: `${geometry.footerTopMm}mm` }">
              <span>页脚线</span>
            </div>
            <section
              v-for="surface in surfaces"
              :key="surface.id"
              :data-surface-id="surface.id"
              class="design-surface"
              :class="{ active: store.surfaceId === surface.id }"
              :style="{
                minHeight: `${Math.max(surface.heightMm || 0, surface.kind === 'TABLE' ? 24 : surface.kind === 'TEXT' ? 16 : 6)}mm`,
                marginBottom: `${surface.gapAfterMm || 0}mm`,
              }"
              @pointerdown.self="surfaceDown($event, surface)"
              @dragover.prevent
              @drop.prevent.stop="drop($event, surface)"
            >
              <span class="surface-label">{{ surface.label }}</span>
              <PrintCanvasElement
                v-for="element in surface.elements || []"
                :key="element.id"
                :element="element"
                :selected="store.surfaceId === surface.id && store.selectedIds.includes(element.id)"
                :catalog="store.catalog"
                :context="context"
                @pointerdown.stop="elementDown($event, surface.id, element.id)"
              />
              <div v-if="surface.kind === 'TEXT'" class="flow-text" :style="printStyle(surface.style)" @pointerdown="surfaceDown($event, surface)">
                {{ flowText(surface) }}
              </div>
              <div v-if="surface.kind === 'TABLE'" class="table-sketch" @pointerdown="surfaceDown($event, surface)">
                <div v-for="(row, rowIndex) in tableRows(surface)" :key="row.key || rowIndex" class="table-sketch-row" :data-row-kind="row.kind">
                  <span v-for="cell in row.cells" :key="cell.key" :style="tableCellStyle(cell)">{{ cell.text }}</span>
                </div>
              </div>
              <template v-if="store.surfaceId === surface.id && store.gesture">
                <div v-for="x in store.alignmentGuides.x" :key="`x-${x}`" class="alignment-guide vertical" :style="{ left: `${x}mm` }" />
                <div v-for="y in store.alignmentGuides.y" :key="`y-${y}`" class="alignment-guide horizontal" :style="{ top: `${y}mm` }" />
                <span
                  v-if="store.alignmentGuides.position"
                  class="position-chip"
                  :style="{ left: `${store.alignmentGuides.position.xMm}mm`, top: `${store.alignmentGuides.position.yMm}mm` }"
                >
                  X {{ store.alignmentGuides.position.xMm.toFixed(1) }} · Y {{ store.alignmentGuides.position.yMm.toFixed(1) }} mm
                </span>
              </template>
              <PrintSelectionOverlay v-if="store.surfaceId === surface.id" />
              <div v-if="marquee?.id === surface.id" class="marquee" :style="{ left: `${marquee.x}mm`, top: `${marquee.y}mm`, width: `${marquee.w}mm`, height: `${marquee.h}mm` }" />
            </section>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.print-canvas {
  display: flex;
  min-width: 0;
  min-height: 0;
  flex-direction: column;
  overflow: hidden;
  background: #eef1f5;
  outline: none;
}
.canvas-note {
  display: flex;
  min-height: 30px;
  padding: 0 12px;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border-bottom: 1px solid #d9dee7;
  color: #667085;
  background: #f8fafc;
  font-size: 11px;
  white-space: nowrap;
}
.canvas-viewport {
  flex: 1;
  min-height: 0;
  padding: 18px 24px 32px;
  overflow: auto;
  scrollbar-gutter: stable;
}
.paper-holder {
  width: max-content;
  margin: 0 auto;
  transform-origin: top center;
}
.ruler-frame {
  display: grid;
  width: max-content;
  align-items: stretch;
  filter: drop-shadow(0 2px 5px rgb(15 23 42 / 10%));
}
.ruler-corner {
  grid-column: 1;
  grid-row: 1;
  border-right: 1px solid #cbd5e1;
  border-bottom: 1px solid #cbd5e1;
  background: #e9edf3;
}
.print-ruler.horizontal {
  grid-column: 2;
  grid-row: 1;
}
.print-ruler.vertical {
  grid-column: 1;
  grid-row: 2;
}
.design-paper {
  position: relative;
  z-index: 0;
  grid-column: 2;
  grid-row: 2;
  box-sizing: border-box;
  color: #111827;
  background-color: #fff;
  border: 1px solid #b8c0cc;
  overflow: visible;
}
.design-paper.paper-grid {
  background-image:
    linear-gradient(to right, rgb(148 163 184 / 14%) 1px, transparent 1px),
    linear-gradient(to bottom, rgb(148 163 184 / 14%) 1px, transparent 1px),
    linear-gradient(to right, rgb(100 116 139 / 22%) 1px, transparent 1px),
    linear-gradient(to bottom, rgb(100 116 139 / 22%) 1px, transparent 1px);
  background-size:
    1mm 1mm,
    1mm 1mm,
    5mm 5mm,
    5mm 5mm;
}
.margin-guide {
  position: absolute;
  z-index: 1;
  border: 1px dashed rgb(37 99 235 / 45%);
  pointer-events: none;
}
.paper-guide {
  position: absolute;
  z-index: 4;
  right: 0;
  left: 0;
  height: 0;
  border-top: 1px dashed rgb(239 68 68 / 70%);
  pointer-events: none;
}
.paper-guide span {
  position: absolute;
  top: -14px;
  right: 2px;
  padding: 0 3px;
  color: #dc2626;
  background: rgb(255 255 255 / 88%);
  font-size: 8px;
  line-height: 13px;
}
.design-surface {
  position: relative;
  z-index: 2;
  box-sizing: border-box;
  outline: 1px dashed rgb(100 116 139 / 45%);
  touch-action: none;
}
.design-surface.active {
  outline-color: var(--primary-color, #356cde);
  background: rgb(22 93 255 / 2%);
}
.surface-label {
  position: absolute;
  z-index: 3;
  top: 1px;
  right: 1mm;
  padding: 0 2px;
  color: #64748b;
  background: rgb(255 255 255 / 78%);
  font-size: 8px;
  pointer-events: none;
}
.flow-text {
  padding: 3mm 1mm;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  cursor: pointer;
}
.table-sketch {
  display: block;
  padding-top: 4mm;
  cursor: pointer;
  font:
    10pt Arial,
    sans-serif;
}
.table-sketch-row {
  display: flex;
}
.table-sketch-row > * {
  box-sizing: border-box;
  border: 1px solid #94a3b8;
  flex-shrink: 0;
  background: rgb(255 255 255 / 82%);
}
.table-sketch-row strong,
.table-sketch-row span {
  display: block;
  padding: 1mm;
  overflow-wrap: anywhere;
}
.table-sketch-row[data-row-kind='header'] span {
  background: rgb(241 245 249 / 92%);
  font-weight: 700;
}
.table-sketch-row span {
  color: #667085;
  font-size: 9pt;
}
.table-sketch-row[data-row-kind='footer'] span {
  background: rgb(248 250 252 / 92%);
}
.alignment-guide {
  position: absolute;
  z-index: 20;
  pointer-events: none;
}
.alignment-guide.vertical {
  top: 0;
  bottom: 0;
  border-left: 1px dashed #ef4444;
}
.alignment-guide.horizontal {
  right: 0;
  left: 0;
  border-top: 1px dashed #ef4444;
}
.position-chip {
  position: absolute;
  z-index: 21;
  padding: 1px 4px;
  color: #fff;
  background: #ef4444;
  border-radius: 2px;
  font:
    8px/13px Arial,
    sans-serif;
  transform: translate(2px, -15px);
  pointer-events: none;
  white-space: nowrap;
}
.marquee {
  position: absolute;
  z-index: 22;
  border: 1px solid var(--primary-color, #356cde);
  background: #356cde18;
  pointer-events: none;
}
</style>
