<template>
  <NModal v-model:show="store.groupModalVisible" preset="card" title="管理分组" :style="{ width: 'min(520px, 92vw)' }">
    <form class="group-form" @submit.prevent="store.addGroup">
      <NInput v-model:value="store.newGroupName" placeholder="分组名称" aria-label="分组名称" maxlength="50" />
      <NSelect v-model:value="store.newGroupType" :options="groupTypeOptions" placeholder="分组类型" aria-label="分组类型" class="group-type" />
      <NButton attr-type="submit" type="primary" :loading="store.groupSaving" :disabled="!store.newGroupName.trim()">
        添加
      </NButton>
    </form>
    <div class="group-list">
      <div v-for="group in store.groups" :key="group.id" class="group-row">
        <i class="i-lucide:folder" aria-hidden="true" />
        <span class="group-name" :title="group.groupName">{{ group.groupName }}</span>
        <span class="group-count">{{ group.fileCount || 0 }} 个文件</span>
        <NButton text type="error" size="small" :aria-label="`删除${group.groupName}分组`" @click="store.deleteGroup(group)">
          删除
        </NButton>
      </div>
      <NEmpty v-if="!store.groups.length" description="暂无自定义分组" class="modal-empty" />
    </div>
  </NModal>
  <NModal :show="!!store.moveFile" preset="card" title="移动到分组" :style="{ width: 'min(420px, 92vw)' }" @update:show="closeMove">
    <p class="dialog-filename" :title="store.moveFile?.originalName">
      {{ store.moveFile?.originalName }}
    </p>
    <NRadioGroup v-if="store.groups.length" v-model:value="store.selectedMoveGroup" class="move-groups" name="file-group">
      <NRadio v-for="group in store.groups" :key="group.id" :value="group.id" class="move-group">
        {{ group.groupName }}
      </NRadio>
    </NRadioGroup>
    <NEmpty v-else description="还没有可用分组" class="modal-empty">
      <template #extra>
        <NButton text type="primary" @click="store.groupModalVisible = true">
          新建分组
        </NButton>
      </template>
    </NEmpty>
    <template #footer>
      <div class="dialog-actions">
        <NButton :disabled="store.moveSaving" @click="store.moveFile = null">
          取消
        </NButton>
        <NButton type="primary" :loading="store.moveSaving" :disabled="!store.selectedMoveGroup" @click="store.confirmMove">
          移动
        </NButton>
      </div>
    </template>
  </NModal>
  <NModal :show="!!store.renameFile" preset="card" title="重命名" :style="{ width: 'min(420px, 92vw)' }" @update:show="closeRename">
    <NInput v-model:value="store.renameName" placeholder="请输入文件名" aria-label="新文件名" maxlength="255" @keyup.enter="store.confirmRename" />
    <template #footer>
      <div class="dialog-actions">
        <NButton :disabled="store.renameSaving" @click="store.renameFile = null">
          取消
        </NButton>
        <NButton type="primary" :loading="store.renameSaving" :disabled="!store.renameName.trim()" @click="store.confirmRename">
          保存
        </NButton>
      </div>
    </template>
  </NModal>
  <NModal :show="store.previewVisible" preset="card" :title="store.previewName" :style="{ width: 'min(900px, 92vw)' }" @update:show="closePreview">
    <div class="preview-content">
      <NImage v-if="store.previewType === 'image'" :src="store.previewUrl" :alt="store.previewName" object-fit="contain" class="preview-image" />
      <iframe v-else-if="store.previewType === 'pdf'" :src="store.previewUrl" title="PDF 预览" class="preview-pdf" />
    </div>
  </NModal>
</template>

<script setup>
import { NButton, NEmpty, NImage, NInput, NModal, NRadio, NRadioGroup, NSelect } from 'naive-ui'
import { computed } from 'vue'
import { useDict } from '@/composables/useDict'
import { useFileListStore } from '@/stores/system/fileListStore'

const store = useFileListStore()
const { dict } = useDict('sys_file_group_type')
const groupTypeOptions = computed(() => dict.value.sys_file_group_type || [])
function closeMove(show) {
  if (!show && !store.moveSaving)
    store.moveFile = null
}
function closeRename(show) {
  if (!show && !store.renameSaving)
    store.renameFile = null
}
function closePreview(show) {
  if (!show)
    store.closePreview()
}
</script>

<style scoped>
.group-form {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
}
.group-type {
  flex: 0 0 120px;
}
.group-list {
  max-height: 320px;
  overflow: auto;
}
.group-row {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 48px;
  border-bottom: 1px solid var(--border-light);
}
.group-row > i {
  flex-shrink: 0;
  color: var(--text-tertiary);
  font-size: 17px;
}
.group-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--text-primary);
}
.group-count {
  font-size: 12px;
  color: var(--text-tertiary);
  white-space: nowrap;
}
.modal-empty {
  padding: 28px 0;
}
.dialog-filename {
  margin: 0 0 16px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--text-secondary);
}
.move-groups {
  display: flex;
  flex-direction: column;
  gap: 14px;
  max-height: 300px;
  overflow: auto;
}
.move-group {
  overflow-wrap: anywhere;
}
.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
.preview-content {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 160px;
}
.preview-image :deep(img) {
  max-width: 100%;
  max-height: 70vh;
}
.preview-pdf {
  width: 100%;
  height: 70vh;
  border: 0;
}
@media (max-width: 600px) {
  .group-form {
    flex-wrap: wrap;
  }
  .group-form > .n-input {
    flex-basis: 100%;
  }
  .group-type {
    flex: 1;
  }
}
</style>
