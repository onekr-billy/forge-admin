<template>
  <div @click="clickHandle">
    <n-tooltip v-if="collapsed" placement="right" trigger="hover">
      <template #trigger>
        <n-button class="create-btn" size="small" :loading="creating">
          <template #icon>
            <n-icon size="16"><DuplicateOutlineIcon /></n-icon>
          </template>
        </n-button>
      </template>
      <span>{{ $t('project.create_btn') }}</span>
    </n-tooltip>
    <n-button v-else class="create-btn" block :loading="creating">
      <template #icon>
        <n-icon size="18"><DuplicateOutlineIcon /></n-icon>
      </template>
      <span>{{ $t('project.create_btn') }}</span>
    </n-button>
  </div>
</template>
<script setup lang="ts">
import { ref } from 'vue'
import { icon } from '@/plugins'
import { createProjectApi } from '@/api/project'
import { fetchPathByName, routerTurnByPath, getUUID } from '@/utils'
import { ChartEnum } from '@/enums/pageEnum'

const { DuplicateOutlineIcon } = icon.ionicons5

defineProps({ collapsed: Boolean })

const creating = ref(false)

const clickHandle = async () => {
  if (creating.value) return
  creating.value = true
  try {
    const res = await createProjectApi({
      projectName: '新项目',
      canvasWidth: 1920,
      canvasHeight: 1080,
      backgroundColor: '',
      componentData: JSON.stringify({
        editCanvasConfig: { projectName: '新项目', width: 1920, height: 1080 },
        requestGlobalConfig: {},
        componentList: []
      }),
      status: '0'
    })
    const id = res?.data?.id || getUUID()
    const path = fetchPathByName(ChartEnum.CHART_HOME_NAME, 'href')
    routerTurnByPath(path, [String(id)], undefined, true)
  } catch (error: any) {
    window.$message.error(error?.message || '创建项目失败')
  } finally {
    creating.value = false
  }
}
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
