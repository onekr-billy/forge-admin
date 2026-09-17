<template>
  <div class="file-grid">
    <article v-for="file in store.files" :key="file.id" class="file-card">
      <button
        v-if="canPreviewFile(file)" type="button" class="preview-button"
        :aria-label="`预览${file.originalName}`" @click="store.preview(file)"
      >
        <FileThumbnail :file="file" large />
      </button>
      <FileThumbnail v-else :file="file" large />
      <div class="file-info">
        <div class="file-name" :title="file.originalName">
          {{ file.originalName }}
        </div>
        <div class="file-meta">
          <span>{{ formatFileSize(file.fileSize) }}</span>
          <span>{{ formatFileDate(file.uploadTime, true) }}</span>
        </div>
      </div>
      <footer class="file-footer">
        <FileActions :file="file" />
      </footer>
    </article>
  </div>
</template>

<script setup>
import { useFileListStore } from '@/stores/system/fileListStore'
import { canPreviewFile, formatFileDate, formatFileSize } from '../utils'
import FileActions from './FileActions.vue'
import FileThumbnail from './FileThumbnail.vue'

const store = useFileListStore()
</script>

<style scoped>
.file-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(210px, 1fr));
  gap: 16px;
  padding: 0 20px 20px;
  align-content: start;
}
.file-card {
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--border-light);
  border-radius: 4px;
  background: var(--bg-primary);
  transition: border-color 150ms;
}
.file-card:hover {
  border-color: var(--border-default);
}
.preview-button {
  display: block;
  width: 100%;
  padding: 0;
  border: 0;
  cursor: pointer;
}
.preview-button:focus-visible {
  outline: 2px solid var(--primary-color);
  outline-offset: -2px;
}
.file-info {
  padding: 12px 14px;
}
.file-name {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 500;
}
.file-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 6px;
  font-size: 11px;
  color: var(--text-tertiary);
}
.file-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding: 7px 12px;
  border-top: 1px solid var(--border-light);
  background: var(--bg-secondary);
}
@media (max-width: 600px) {
  .file-grid {
    grid-template-columns: minmax(0, 1fr);
    padding: 0 12px 12px;
  }
}
</style>
