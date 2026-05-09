<template>
  <div class="header-right-actions">
    <span class="action-label">输出</span>
    <n-button
      v-for="item in comBtnList"
      :key="item.title"
      class="action-btn"
      :class="{ publish: item.title === '发布' }"
      :type="item.type"
      ghost
      @click="item.event"
    >
      <template #icon>
        <component :is="item.icon"></component>
      </template>
      <span>{{ item.title }}</span>
    </n-button>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { renderIcon, goDialog, fetchPathByName, routerTurnByPath, setSessionStorage, getSessionStorage } from '@/utils'
import { captureProjectScreenshot } from '@/utils/capture'
import { PreviewEnum } from '@/enums/pageEnum'
import { StorageEnum } from '@/enums/storageEnum'
import { useRoute } from 'vue-router'
import { useChartEditStore } from '@/store/modules/chartEditStore/chartEditStore'
import { syncData } from '../../ContentEdit/components/EditTools/hooks/useSyncUpdate.hook'
import { icon } from '@/plugins'
import { cloneDeep } from 'lodash'
import { buildProjectPayload, publishProjectApi, updateProjectApi } from '@/api/project'

const { BrowsersOutlineIcon, SendIcon, AnalyticsIcon } = icon.ionicons5
const chartEditStore = useChartEditStore()

const routerParamsInfo = useRoute()

// 预览
const previewHandle = () => {
  const path = fetchPathByName(PreviewEnum.CHART_PREVIEW_NAME, 'href')
  if (!path) return
  const { id } = routerParamsInfo.params
  // id 标识
  const previewId = typeof id === 'string' ? id : id[0]
  const storageInfo = chartEditStore.getStorageInfo()
  const sessionStorageInfo = getSessionStorage(StorageEnum.GO_CHART_STORAGE_LIST) || []

  if (sessionStorageInfo?.length) {
    const repeateIndex = sessionStorageInfo.findIndex((e: { id: string }) => e.id === previewId)
    // 重复替换
    if (repeateIndex !== -1) {
      sessionStorageInfo.splice(repeateIndex, 1, { id: previewId, ...storageInfo })
      setSessionStorage(StorageEnum.GO_CHART_STORAGE_LIST, sessionStorageInfo)
    } else {
      sessionStorageInfo.push({
        id: previewId,
        ...storageInfo
      })
      setSessionStorage(StorageEnum.GO_CHART_STORAGE_LIST, sessionStorageInfo)
    }
  } else {
    setSessionStorage(StorageEnum.GO_CHART_STORAGE_LIST, [{ id: previewId, ...storageInfo }])
  }
  // 跳转
  routerTurnByPath(path, [previewId], undefined, true)
}

// 发布
const sendHandle = async () => {
  const { id } = routerParamsInfo.params
  const previewId = typeof id === 'string' ? id : id[0]
  const storageInfo = chartEditStore.getStorageInfo()

  try {
    window['$message'].loading('正在生成项目截图...')
    console.log('[Publish] 开始发布, projectId:', previewId)

    // 尝试生成截图 - 使用正确的画布元素选择器
    let indexImg: string | undefined = undefined
    const canvasElement = document.querySelector('.go-edit-range')
    console.log('[Screenshot] 画布元素:', canvasElement)
    if (canvasElement) {
      indexImg = await captureProjectScreenshot(previewId, canvasElement as HTMLElement)
      console.log('[Screenshot] 截图结果:', indexImg)
    } else {
      console.warn('[Screenshot] 未找到画布元素 .go-edit-range')
    }

    // 构建项目数据，包含截图
    const projectPayload = buildProjectPayload(previewId, storageInfo, indexImg)
    console.log('[Publish] 项目数据:', projectPayload)

    await updateProjectApi(projectPayload)

    const previewPath = fetchPathByName(PreviewEnum.CHART_PREVIEW_NAME, 'href')
    if (!previewPath) {
      window['$message'].error('获取预览路径失败')
      return
    }
    const previewUrl = `${window.location.origin}${previewPath}/${previewId}`
    await publishProjectApi(previewId, previewUrl)

    window['$message'].success('发布成功')

    goDialog({
      message: `发布成功！\n\n预览链接：\n${previewUrl}`,
      positiveText: '复制链接',
      negativeText: '直接预览',
      onPositiveCallback: () => {
        navigator.clipboard.writeText(previewUrl).then(() => {
          window['$message'].success('链接已复制到剪贴板')
        }).catch(() => {
          routerTurnByPath(previewPath, [previewId], undefined, true)
        })
      },
      onNegativeCallback: () => {
        routerTurnByPath(previewPath, [previewId], undefined, true)
      }
    })
  } catch (error: any) {
    console.error('[Publish] 发布失败:', error)
    window['$message'].error(error?.message || '发布失败')
  }
}

const btnList = [
  {
    select: true,
    title: '同步内容',
    type: 'primary',
    icon: renderIcon(AnalyticsIcon),
    event: syncData
  },
  {
    select: true,
    title: '预览',
    icon: renderIcon(BrowsersOutlineIcon),
    event: previewHandle
  },
  {
    select: true,
    title: '发布',
    icon: renderIcon(SendIcon),
    event: sendHandle
  }
]

const comBtnList = computed(() => {
  if (chartEditStore.getEditCanvas.isCodeEdit) {
    return btnList
  }
  const cloneList = cloneDeep(btnList)
  cloneList.shift()
  return cloneList
})
</script>

<style lang="scss" scoped>
.header-right-actions {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 4px;
  border-radius: 999px;
  border: 1px solid rgba(var(--app-theme-rgb), 0.12);
  background:
    linear-gradient(90deg, rgba(var(--app-theme-rgb), 0.08), transparent),
    rgba(2, 6, 23, 0.3);

  .action-label {
    padding: 0 7px 0 10px;
    font-size: 10px;
    letter-spacing: 1px;
    @include fetch-color(4);
  }

  :deep(.n-button) {
    height: 32px;
    border-radius: 999px;
    padding: 0 14px;
    border-color: rgba(var(--app-theme-rgb), 0.16);
    background: rgba(15, 23, 42, 0.36);
    font-size: 12px;
    letter-spacing: 0.4px;
    transition: all 0.22s ease;

    &:hover {
      border-color: rgba(var(--app-theme-rgb), 0.34);
      background: rgba(var(--app-theme-rgb), 0.1);
      box-shadow: 0 0 16px rgba(var(--app-theme-rgb), 0.16);
      transform: translateY(-1px);
    }
  }

  .publish {
    background:
      linear-gradient(135deg, rgba(var(--app-theme-rgb), 0.22), rgba(167, 139, 250, 0.16)),
      rgba(15, 23, 42, 0.5);
    box-shadow: 0 0 18px rgba(var(--app-theme-rgb), 0.14);
  }
}
</style>
