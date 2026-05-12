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
import { renderIcon, goDialog, fetchPathByName, routerTurnByPath, setSessionStorage, getSessionStorage, downloadTextFile } from '@/utils'
import { captureProjectScreenshot } from '@/utils/capture'
import { buildApiContractDocument, buildApiContractFileName } from '@/utils/apiContractExport'
import { PreviewEnum } from '@/enums/pageEnum'
import { StorageEnum } from '@/enums/storageEnum'
import { useRoute } from 'vue-router'
import { useChartEditStore } from '@/store/modules/chartEditStore/chartEditStore'
import { syncData } from '../../ContentEdit/components/EditTools/hooks/useSyncUpdate.hook'
import { icon } from '@/plugins'
import { cloneDeep } from 'lodash'
import { buildProjectPayload, getProjectDetailApi, publishProjectApi, updateProjectApi } from '@/api/project'

const { BrowsersOutlineIcon, SendIcon, AnalyticsIcon, DocumentTextIcon } = icon.ionicons5
const chartEditStore = useChartEditStore()

const routerParamsInfo = useRoute()

const syncProjectStorageToSession = (id: string, storageInfo: ReturnType<typeof chartEditStore.getStorageInfo>) => {
  const sessionStorageInfo = getSessionStorage(StorageEnum.GO_CHART_STORAGE_LIST) || []
  const saveData = { id, ...storageInfo }
  const repeateIndex = sessionStorageInfo.findIndex((e: { id: string }) => String(e.id) === String(id))

  if (repeateIndex !== -1) {
    sessionStorageInfo.splice(repeateIndex, 1, saveData)
  } else {
    sessionStorageInfo.push(saveData)
  }
  setSessionStorage(StorageEnum.GO_CHART_STORAGE_LIST, sessionStorageInfo)
}

const assertProjectComponentDataSaved = async (id: string, expectedComponentData?: string) => {
  if (!expectedComponentData) return
  const res = await getProjectDetailApi(id)
  const actualComponentData = res?.data?.componentData || ''
  if (actualComponentData !== expectedComponentData) {
    const expectedLength = JSON.parse(expectedComponentData).componentList?.length ?? 0
    const actualLength = actualComponentData ? JSON.parse(actualComponentData).componentList?.length ?? 0 : 0
    throw new Error(`项目配置保存校验失败，当前 ${actualLength} 个组件，预期 ${expectedLength} 个组件`)
  }
}

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
    await assertProjectComponentDataSaved(previewId, projectPayload.componentData)
    syncProjectStorageToSession(previewId, storageInfo)

    // 同步写入 sessionStorage，确保预览页优先读到最新数据
    const sessionStorageInfo = getSessionStorage(StorageEnum.GO_CHART_STORAGE_LIST) || []
    const idx = sessionStorageInfo.findIndex((e: { id: string }) => e.id === previewId)
    if (idx !== -1) {
      sessionStorageInfo.splice(idx, 1, { id: previewId, ...storageInfo })
    } else {
      sessionStorageInfo.push({ id: previewId, ...storageInfo })
    }
    setSessionStorage(StorageEnum.GO_CHART_STORAGE_LIST, sessionStorageInfo)

    const previewPath = fetchPathByName(PreviewEnum.CHART_PREVIEW_NAME, 'href')
    if (!previewPath) {
      window['$message'].error('获取预览路径失败')
      return
    }
    const previewUrl = `${window.location.origin}${previewPath}/${previewId}`
    await publishProjectApi(previewId, previewUrl)
    await assertProjectComponentDataSaved(previewId, projectPayload.componentData)

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

// 导出接口规范文档
const exportApiContractHandle = () => {
  const document = buildApiContractDocument({
    canvasConfig: chartEditStore.getEditCanvasConfig,
    requestGlobalConfig: chartEditStore.getRequestGlobalConfig,
    componentList: chartEditStore.getComponentList,
    includeStatic: true
  })

  if (!document.endpoints.length) {
    window['$message'].warning('当前画布没有可导出的组件数据规范')
    return
  }

  const fileName = buildApiContractFileName(document.meta.projectName)
  const warningText = document.warnings.length ? `\n\n检测到 ${document.warnings.length} 条提示，建议导出后检查 URL、dataset 和 filter 说明。` : ''

  goDialog({
    message: `将导出 ${document.endpoints.length} 个组件数据规范，后端可按文档中的 dataset 结构开发接口，接口地址可后续自行选择或配置。${warningText}`,
    positiveText: '下载 Markdown',
    negativeText: '下载 JSON',
    onPositiveCallback: () => {
      downloadTextFile(document.markdown, fileName, 'md')
      window['$message'].success('接口规范 Markdown 已导出')
    },
    onNegativeCallback: () => {
      downloadTextFile(document.json, fileName, 'json')
      window['$message'].success('接口规范 JSON 已导出')
    }
  })
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
    title: '接口文档',
    icon: renderIcon(DocumentTextIcon),
    event: exportApiContractHandle
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
