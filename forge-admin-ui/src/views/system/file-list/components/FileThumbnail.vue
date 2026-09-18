<template>
  <div class="file-thumbnail" :class="{ large, previewable: canPreview }" :title="canPreview ? '点击放大预览' : undefined">
    <i :class="icon" aria-hidden="true" />
    <AuthImage
      v-if="isImageFile(file)" :src="String(file.fileId)" :alt="file.originalName"
      class="thumbnail-image" :img-style="{ width: '100%', height: '100%', objectFit: large ? 'contain' : 'cover' }"
      :preview="preview"
    />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import AuthImage from '@/components/common/AuthImage.vue'
import { isImageFile } from '../utils'

const props = defineProps({ file: { type: Object, required: true }, large: Boolean, preview: Boolean })
// 图片缩略图在列表中支持点击放大，仅对已成功加载的图片生效（由 AuthImage 内部判断）。
const canPreview = computed(() => props.preview && isImageFile(props.file))
// 图标类放在 SFC 中，让 UnoCSS 静态扫描可发现全部文件类型。
const icon = computed(() => {
  const file = props.file
  if (isImageFile(file))
    return 'i-lucide:image'
  if (file.mimeType?.startsWith('video/'))
    return 'i-lucide:film'
  if (file.mimeType?.startsWith('audio/'))
    return 'i-lucide:music-2'
  const extension = file.extension?.replace(/^\./, '').toLowerCase()
  if (['pdf', 'doc', 'docx', 'txt'].includes(extension))
    return 'i-lucide:file-text'
  if (['xls', 'xlsx', 'csv'].includes(extension))
    return 'i-lucide:sheet'
  if (['ppt', 'pptx'].includes(extension))
    return 'i-lucide:presentation'
  if (['zip', 'rar', '7z'].includes(extension))
    return 'i-lucide:file-archive'
  return 'i-lucide:file'
})
</script>

<style scoped>
.file-thumbnail {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 36px;
  height: 40px;
  overflow: hidden;
  border-radius: 3px;
  color: var(--text-tertiary);
  background: var(--bg-secondary);
}
.file-thumbnail > i {
  font-size: 22px;
}
.thumbnail-image {
  position: absolute;
  inset: 0;
}
.file-thumbnail.previewable .thumbnail-image {
  transition: filter 150ms;
}
.file-thumbnail.previewable:hover .thumbnail-image {
  filter: brightness(0.92);
}
.large {
  width: 100%;
  height: 144px;
  border-radius: 0;
}
.large > i {
  font-size: 38px;
}
.large .thumbnail-image {
  inset: 12px;
}
</style>
