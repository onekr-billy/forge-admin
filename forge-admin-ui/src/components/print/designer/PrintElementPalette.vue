<script setup>
import {
  BarcodeOutline,
  DocumentTextOutline,
  EllipseOutline,
  GridOutline,
  ImageOutline,
  LayersOutline,
  ListOutline,
  QrCodeOutline,
  RemoveOutline,
  SquareOutline,
  TextOutline,
} from '@vicons/ionicons5'
import { NIcon } from 'naive-ui'
import { usePrintDesignerStore } from '@/stores/print/printDesignerStore'
import { addElement, addSection, elementCatalog, startItemDrag } from './elementCatalog'

const store = usePrintDesignerStore()
const elementIcons = {
  BARCODE: BarcodeOutline,
  IMAGE: ImageOutline,
  LINE: RemoveOutline,
  PAGE_NUMBER: DocumentTextOutline,
  QRCODE: QrCodeOutline,
  RECTANGLE: SquareOutline,
  ELLIPSE: EllipseOutline,
  TEXT: TextOutline,
  STATIC_TABLE: GridOutline,
}
const sections = [
  { type: 'FIXED', label: '固定区块', icon: LayersOutline },
  { type: 'TEXT', label: '流式文本', icon: ListOutline },
  { type: 'TABLE', label: '明细表格', icon: GridOutline },
]
</script>

<template>
  <section class="designer-group palette-group">
    <h3>基础元素</h3>
    <div class="palette-grid">
      <button
        v-for="item in elementCatalog"
        :key="item.key"
        type="button"
        class="palette-item"
        draggable="true"
        :title="`拖入或点击添加${item.label}`"
        @dragstart="startItemDrag($event, { type: item.type, preset: item.preset })"
        @click="addElement(store, item.type, undefined, undefined, item.preset)"
      >
        <NIcon :component="elementIcons[item.type]" size="21" :class="{ 'vertical-line-icon': item.preset === 'VERTICAL' }" />
        <span>{{ item.label }}</span>
      </button>
    </div>
    <h3>内容区块</h3>
    <div class="palette-grid section-palette">
      <button v-for="item in sections" :key="item.type" type="button" class="palette-item" @click="addSection(store, item.type)">
        <NIcon :component="item.icon" size="20" />
        <span>{{ item.label }}</span>
      </button>
    </div>
  </section>
</template>

<style scoped>
.palette-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px;
}
.palette-item {
  display: flex;
  min-width: 0;
  min-height: 58px;
  padding: 7px 4px 6px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  border: 1px solid var(--border-light, #e5e7eb);
  border-radius: 4px;
  color: var(--text-primary, #1f2329);
  background: var(--bg-primary, #fff);
  cursor: grab;
  transition:
    border-color 140ms,
    background 140ms,
    color 140ms;
}
.palette-item:hover {
  border-color: var(--primary-color, #165dff);
  color: var(--primary-color, #165dff);
  background: color-mix(in srgb, var(--primary-color, #165dff) 6%, var(--bg-primary, #fff));
}
.palette-item:active {
  cursor: grabbing;
}
.palette-item span {
  overflow: hidden;
  max-width: 100%;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.vertical-line-icon {
  transform: rotate(90deg);
}
.section-palette .palette-item:last-child {
  grid-column: 1 / -1;
  min-height: 50px;
  flex-direction: row;
}
</style>
