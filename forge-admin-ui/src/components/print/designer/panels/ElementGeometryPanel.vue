<script setup>
import { ClipboardOutline, CopyOutline, SwapHorizontalOutline, SwapVerticalOutline, TrashOutline } from '@vicons/ionicons5'
import { NFormItem, NIcon, NInputNumber, NSwitch } from 'naive-ui'
import { usePrintDesignerStore } from '@/stores/print/printDesignerStore'

const store = usePrintDesignerStore()
</script>

<template>
  <section class="designer-group">
    <h3>{{ store.selectedIds.length ? `选中 ${store.selectedIds.length} 个元素` : '当前区块' }}</h3>
    <div v-if="store.activeElement" class="panel-grid">
      <NFormItem v-for="(label, key) in { xMm: '横坐标 mm', yMm: '纵坐标 mm', widthMm: '宽度 mm', heightMm: '高度 mm' }" :key="key" :label="label" size="small">
        <NInputNumber :value="store.activeElement[key]" :min="key.includes('Mm') && ['widthMm', 'heightMm'].includes(key) ? 0.1 : 0" :precision="2" :show-button="false" :disabled="store.activeElement.locked" @update:value="$event !== null && store.patchSelected({ [key]: $event })" />
      </NFormItem>
    </div>
    <div v-else-if="store.selectedIds.length > 1" class="geometry-commandbar alignment-bar">
      <button v-for="item in [{ key: 'left', label: '左对齐' }, { key: 'center', label: '水平居中' }, { key: 'right', label: '右对齐' }, { key: 'top', label: '顶对齐' }, { key: 'middle', label: '垂直居中' }, { key: 'bottom', label: '底对齐' }]" :key="item.key" type="button" class="geometry-tool" :title="item.label" :aria-label="item.label" :disabled="store.hasLockedSelection" @click="store.alignSelection(item.key)">
        <span class="align-glyph" :class="item.key"><i /><i /><i /></span>
      </button>
      <button type="button" class="geometry-tool" title="水平等距分布" aria-label="水平等距分布" :disabled="store.selectedIds.length < 3 || store.hasLockedSelection" @click="store.distributeSelection('horizontal')">
        <NIcon :component="SwapHorizontalOutline" />
      </button>
      <button type="button" class="geometry-tool" title="垂直等距分布" aria-label="垂直等距分布" :disabled="store.selectedIds.length < 3 || store.hasLockedSelection" @click="store.distributeSelection('vertical')">
        <NIcon :component="SwapVerticalOutline" />
      </button>
    </div>
    <p v-if="store.activeSurface?.kind === 'PAGE_BREAK'" class="page-break-help">
      预览和打印会从这里开始新的一页。分页符不占用纸张高度。
    </p>
    <div v-else class="geometry-commandbar edit-commandbar">
      <button type="button" class="geometry-tool" title="复制元素" aria-label="复制元素" :disabled="!store.selectedIds.length" @click="store.copySelection()">
        <NIcon :component="CopyOutline" />
      </button>
      <button type="button" class="geometry-tool" title="粘贴元素" aria-label="粘贴元素" :disabled="!store.clipboard.length || !store.activeSurface?.elements" @click="store.pasteSelection()">
        <NIcon :component="ClipboardOutline" />
      </button>
      <button type="button" class="geometry-tool danger" title="删除元素" aria-label="删除元素" :disabled="!store.selectedIds.length || store.hasLockedSelection" @click="store.removeSelection()">
        <NIcon :component="TrashOutline" />
      </button>
    </div>
    <template v-if="!store.selectedIds.length && store.activeSurface?.kind && store.activeSurface.kind !== 'PAGE_BREAK'">
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

<style scoped>
.geometry-commandbar {
  display: flex;
  align-items: center;
  gap: 3px;
  padding: 3px;
  border: 1px solid var(--border-light, #ddd);
  border-radius: 6px;
  background: color-mix(in srgb, var(--text-tertiary, #64748b) 4%, transparent);
}
.page-break-help {
  margin: 0;
  padding: 8px 9px;
  border: 1px solid color-mix(in srgb, var(--primary-color, #356cde) 20%, transparent);
  border-radius: 5px;
  color: var(--text-secondary, #64748b);
  background: color-mix(in srgb, var(--primary-color, #356cde) 5%, transparent);
  font-size: 12px;
  line-height: 1.6;
}
.alignment-bar {
  margin-bottom: 7px;
}
.edit-commandbar {
  width: max-content;
}
.geometry-tool {
  width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 1px solid transparent;
  border-radius: 4px;
  color: var(--text-primary, #334155);
  background: transparent;
  cursor: pointer;
  font-size: 15px;
}
.geometry-tool:hover:not(:disabled) {
  color: var(--primary-color, #356cde);
  border-color: color-mix(in srgb, var(--primary-color, #356cde) 22%, transparent);
  background: color-mix(in srgb, var(--primary-color, #356cde) 9%, transparent);
}
.geometry-tool.danger:hover:not(:disabled) {
  color: #ef4444;
  border-color: rgb(239 68 68 / 20%);
  background: rgb(239 68 68 / 8%);
}
.geometry-tool:disabled {
  opacity: 0.28;
  cursor: not-allowed;
}
.align-glyph {
  position: relative;
  width: 16px;
  height: 16px;
  display: flex;
  flex-direction: column;
  justify-content: space-around;
}
.align-glyph i {
  display: block;
  height: 2px;
  border-radius: 1px;
  background: currentColor;
}
.align-glyph i:nth-child(1) {
  width: 12px;
}
.align-glyph i:nth-child(2) {
  width: 8px;
}
.align-glyph i:nth-child(3) {
  width: 14px;
}
.align-glyph.center i,
.align-glyph.middle i {
  align-self: center;
}
.align-glyph.right i {
  align-self: flex-end;
}
.align-glyph.top,
.align-glyph.middle,
.align-glyph.bottom {
  flex-direction: row;
  align-items: flex-start;
}
.align-glyph.top i,
.align-glyph.middle i,
.align-glyph.bottom i {
  width: 2px;
  height: 12px;
}
.align-glyph.top i:nth-child(2),
.align-glyph.middle i:nth-child(2),
.align-glyph.bottom i:nth-child(2) {
  height: 8px;
}
.align-glyph.top i:nth-child(3),
.align-glyph.middle i:nth-child(3),
.align-glyph.bottom i:nth-child(3) {
  height: 14px;
}
.align-glyph.middle i {
  align-self: center;
}
.align-glyph.bottom i {
  align-self: flex-end;
}
</style>
