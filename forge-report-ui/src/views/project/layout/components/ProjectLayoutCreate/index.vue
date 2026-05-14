<template>
  <div>
    <n-tooltip v-if="collapsed" placement="right" trigger="hover">
      <template #trigger>
        <n-button class="create-btn" size="small" @click="openModal">
          <template #icon>
            <n-icon size="16"><DuplicateOutlineIcon /></n-icon>
          </template>
        </n-button>
      </template>
      <span>{{ $t('project.create_btn') }}</span>
    </n-tooltip>
    <n-button v-else class="create-btn" block @click="openModal">
      <template #icon>
        <n-icon size="18"><DuplicateOutlineIcon /></n-icon>
      </template>
      <span>{{ $t('project.create_btn') }}</span>
    </n-button>
    <project-create-modal v-model:show="showCreateModal" />
  </div>
</template>
<script setup lang="ts">
import { ref } from 'vue'
import { icon } from '@/plugins'
import { ProjectCreateModal } from '@/views/project/components/ProjectCreateModal'

const { DuplicateOutlineIcon } = icon.ionicons5

defineProps({ collapsed: Boolean })

const showCreateModal = ref(false)

const openModal = () => { showCreateModal.value = true }
</script>

<style lang="scss" scoped>
.create-btn {
  background: rgba(var(--app-theme-rgb), 0.06);
  border: 1px solid rgba(var(--app-theme-rgb), 0.15);
  color: var(--app-theme, $--color-primary);
  border-radius: $--border-radius-sm;
  transition: all 0.25s ease;
  font-weight: 500;
  letter-spacing: 1px;

  &:hover {
    background: rgba(var(--app-theme-rgb), 0.12);
    border-color: rgba(var(--app-theme-rgb), 0.35);
    box-shadow: 0 0 16px rgba(var(--app-theme-rgb), 0.15);
  }
}
</style>
