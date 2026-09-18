<script setup>
import { NButton, NFormItem, NInputNumber, NSwitch } from 'naive-ui'
import { usePrintDesignerStore } from '@/stores/print/printDesignerStore'

const store = usePrintDesignerStore()
</script>

<template>
  <section class="designer-group">
    <h3>{{ store.selectedIds.length ? `选中 ${store.selectedIds.length} 个元素` : '当前区块' }}</h3>
    <div v-if="store.activeElement" class="panel-grid">
      <NFormItem v-for="(label, key) in { xMm: '横坐标 mm', yMm: '纵坐标 mm', widthMm: '宽度 mm', heightMm: '高度 mm' }" :key="key" :label="label" size="small">
        <NInputNumber :value="store.activeElement[key]" :min="key.includes('Mm') && ['widthMm', 'heightMm'].includes(key) ? 0.1 : 0" :precision="2" :show-button="false" @update:value="$event !== null && store.patchSelected({ [key]: $event })" />
      </NFormItem>
    </div>
    <div v-else-if="store.selectedIds.length > 1" class="panel-row">
      <NButton size="small" @click="store.alignSelection('left')">
        左对齐
      </NButton><NButton size="small" @click="store.alignSelection('top')">
        顶对齐
      </NButton>
    </div>
    <div class="panel-row">
      <NButton size="small" :disabled="!store.selectedIds.length" @click="store.copySelection()">
        复制元素
      </NButton>
      <NButton size="small" :disabled="!store.clipboard.length || !store.activeSurface?.elements" @click="store.pasteSelection()">
        粘贴元素
      </NButton>
      <NButton size="small" type="error" secondary :disabled="!store.selectedIds.length" @click="store.removeSelection()">
        删除元素
      </NButton>
    </div>
    <template v-if="!store.selectedIds.length && store.activeSurface?.kind">
      <NFormItem v-if="store.activeSurface.kind === 'FIXED'" label="区块高度 mm" size="small">
        <NInputNumber :value="store.activeSurface.heightMm" :min="1" @update:value="$event !== null && store.patchSurface({ heightMm: $event })" />
      </NFormItem>
      <NFormItem label="后间距 mm" size="small">
        <NInputNumber :value="store.activeSurface.gapAfterMm || 0" :min="0" @update:value="$event !== null && store.patchSurface({ gapAfterMm: $event })" />
      </NFormItem>
      <NFormItem label="与下一区块同页" size="small">
        <NSwitch :value="store.activeSurface.keepWithNext || false" @update:value="store.patchSurface({ keepWithNext: $event })" />
      </NFormItem>
    </template>
  </section>
</template>
