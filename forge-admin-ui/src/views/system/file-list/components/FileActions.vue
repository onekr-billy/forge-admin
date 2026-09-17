<template>
  <div class="file-actions" @click.stop>
    <button
      v-if="canPreviewFile(file)" type="button" class="action-link"
      :disabled="!!store.previewLoadingId"
      @click="store.preview(file)"
    >
      {{ store.previewLoadingId === file.fileId ? '加载中' : '预览' }}
    </button>
    <span v-if="canPreviewFile(file)" class="action-divider" aria-hidden="true" />
    <button type="button" class="action-link" @click="store.download(file)">
      下载
    </button>
    <span class="action-divider" aria-hidden="true" />
    <NDropdown trigger="click" :options="options" @select="handleAction">
      <button type="button" class="more-action" :aria-label="`${file.originalName}的更多操作`" title="更多操作">
        <i class="i-lucide:ellipsis" />
      </button>
    </NDropdown>
  </div>
</template>

<script setup>
import { NDropdown } from 'naive-ui'
import { useFileListStore } from '@/stores/system/fileListStore'
import { canPreviewFile } from '../utils'

const props = defineProps({ file: { type: Object, required: true } })
const store = useFileListStore()
const options = [
  { label: '重命名', key: 'rename' },
  { label: '复制链接', key: 'copy' },
  { label: '移动到分组', key: 'move' },
  { type: 'divider', key: 'divider' },
  { label: '删除', key: 'delete', props: { style: { color: 'var(--error-color, #ef4444)' } } },
]
function handleAction(key) {
  const actions = { rename: store.openRename, copy: store.copyLink, move: store.openMove, delete: store.deleteFile }
  actions[key]?.(props.file)
}
</script>

<style scoped>
.file-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 9px;
  white-space: nowrap;
}
.action-divider {
  width: 1px;
  height: 12px;
  background: var(--border-light);
}
.action-link,
.more-action {
  padding: 0;
  border: 0;
  background: transparent;
  cursor: pointer;
}
.action-link {
  color: var(--file-link-color, var(--primary-color));
  font-size: 12px;
  line-height: 24px;
  transition: color 150ms;
}
.action-link:hover {
  color: var(--file-link-color, var(--primary-color));
  text-decoration: underline;
}
.action-link:disabled {
  color: var(--text-disabled);
  cursor: wait;
  text-decoration: none;
}
.more-action {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 3px;
  color: var(--text-secondary);
}
.more-action:hover {
  color: var(--file-link-color, var(--primary-color));
  background: var(--bg-hover);
}
.more-action i {
  font-size: 16px;
}
.action-link:focus-visible,
.more-action:focus-visible {
  outline: 2px solid var(--primary-color);
  outline-offset: 2px;
}
</style>
