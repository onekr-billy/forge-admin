<template>
  <div class="go-edit-shortcut">
    <shortcut-key-modal v-model:modelShow="modelShow"></shortcut-key-modal>
    <n-tooltip trigger="hover">
      <template #trigger>
        <n-button
          class="scale-btn"
          quaternary
          size="small"
          @click="modelShow = true"
        >
          <n-icon size="21" :depth="3">
            <dicom-overlay-icon></dicom-overlay-icon>
          </n-icon>
        </n-button>
      </template>
      <span>快捷键</span>
    </n-tooltip>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { icon } from '@/plugins'
import ShortcutKeyModal from './ShortcutKeyModal.vue'

const { DicomOverlayIcon } = icon.carbon
const modelShow = ref<boolean>(false)
</script>

<style lang="scss" scoped>
@include go('edit-shortcut') {
  .scale-btn {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 30px;
    height: 30px;
    border-radius: 999px;
    border: 1px solid rgba(var(--app-theme-rgb), 0.1);
    background: rgba(15, 23, 42, 0.32);

    &:hover {
      border-color: rgba(var(--app-theme-rgb), 0.28);
      background: rgba(var(--app-theme-rgb), 0.1);
      box-shadow: 0 0 12px rgba(var(--app-theme-rgb), 0.16);
    }

    :deep(.n-button__icon) {
      margin: 0;
      display: inline-flex;
      align-items: center;
      justify-content: center;
    }

    :deep(.n-button__content),
    :deep(.n-icon) {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      line-height: 1;
    }

    :deep(.n-icon) {
      margin: 0;
    }

    .btn-text {
      font-size: 12px;
      margin-right: 3px;
    }
  }
}
</style>
