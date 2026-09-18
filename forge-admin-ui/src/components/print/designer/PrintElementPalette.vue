<script setup>
import { NButton } from 'naive-ui'
import { usePrintDesignerStore } from '@/stores/print/printDesignerStore'
import { addElement, addSection, elementCatalog, startItemDrag } from './elementCatalog'

const store = usePrintDesignerStore()
</script>

<template>
  <section class="designer-group">
    <h3>添加元素</h3>
    <div class="palette-grid">
      <NButton v-for="item in elementCatalog" :key="item.type" size="small" draggable="true" :title="`拖入或点击添加${item.label}`" @dragstart="startItemDrag($event, { type: item.type })" @click="addElement(store, item.type)">
        {{ item.label }}
      </NButton>
    </div>
    <h3>添加区块</h3>
    <div class="palette-grid">
      <NButton size="small" @click="addSection(store, 'FIXED')">
        固定区块
      </NButton>
      <NButton size="small" @click="addSection(store, 'TEXT')">
        流式文本
      </NButton>
      <NButton size="small" @click="addSection(store, 'TABLE')">
        明细表格
      </NButton>
    </div>
  </section>
</template>

<style scoped>
.palette-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
}
</style>
