<script setup>
import { ArrowDownOutline, ArrowUpOutline, CheckmarkDoneOutline, ClipboardOutline, CopyOutline, DuplicateOutline, TrashOutline } from '@vicons/ionicons5'
import { NIcon } from 'naive-ui'
import { usePrintDesignerStore } from '@/stores/print/printDesignerStore'

const store = usePrintDesignerStore()
const actions = [
  { label: '全选当前区块', icon: CheckmarkDoneOutline, run: () => store.selectAll(), enabled: () => !!store.activeSurface?.elements?.length },
  { label: '复制', icon: CopyOutline, run: () => store.copySelection(), enabled: () => !!store.selectedIds.length },
  { label: '粘贴', icon: ClipboardOutline, run: () => store.pasteSelection(), enabled: () => !!store.clipboard.length && !!store.activeSurface?.elements },
  { label: '复制一份', icon: DuplicateOutline, run: () => store.duplicateSelection(), enabled: () => !!store.selectedIds.length },
  { label: '置于顶层', icon: ArrowUpOutline, run: () => store.moveSelectionLayer('front'), enabled: () => !!store.selectedIds.length },
  { label: '置于底层', icon: ArrowDownOutline, run: () => store.moveSelectionLayer('back'), enabled: () => !!store.selectedIds.length },
  { label: '删除', icon: TrashOutline, run: () => store.removeSelection(), enabled: () => !!store.selectedIds.length, danger: true },
]
</script>

<template>
  <div class="canvas-actionbar" aria-label="画布操作">
    <button
      v-for="action in actions"
      :key="action.label"
      type="button"
      :class="{ danger: action.danger }"
      :title="action.label"
      :aria-label="action.label"
      :disabled="!action.enabled()"
      @click="action.run"
    >
      <NIcon :component="action.icon" />
    </button>
    <span v-if="store.selectedIds.length" class="selection-count">{{ store.selectedIds.length }} 项</span>
  </div>
</template>

<style scoped>
.canvas-actionbar {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 1px;
}
.canvas-actionbar button {
  width: 26px;
  height: 24px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 1px solid transparent;
  border-radius: 4px;
  color: #475569;
  background: transparent;
  cursor: pointer;
  font-size: 15px;
}
.canvas-actionbar button:hover:not(:disabled) {
  color: var(--primary-color, #356cde);
  border-color: color-mix(in srgb, var(--primary-color, #356cde) 22%, transparent);
  background: color-mix(in srgb, var(--primary-color, #356cde) 8%, transparent);
}
.canvas-actionbar button.danger:hover:not(:disabled) {
  color: #dc2626;
  border-color: #fecaca;
  background: #fef2f2;
}
.canvas-actionbar button:disabled {
  opacity: 0.28;
  cursor: not-allowed;
}
.selection-count {
  padding-left: 4px;
  color: #64748b;
  font-size: 10px;
  white-space: nowrap;
}
</style>
