import { onUnmounted, onMounted } from 'vue'
import { useChartEditStore } from '@/store/modules/chartEditStore/chartEditStore'
import { EditCanvasTypeEnum } from '@/store/modules/chartEditStore/chartEditStore.d'
import { getSessionStorage } from '@/utils'
import { StorageEnum } from '@/enums/storageEnum'
import { fetchRouteParamsLocation } from '@/utils'
import { getProjectDetailApi } from '@/api/project'
import { useSync } from '@/views/chart/hooks/useSync.hook'

const chartEditStore = useChartEditStore()

const applyProjectStorage = async (storage: any, updateComponent: (projectData: any, isReplace?: boolean) => Promise<void>, source: string) => {
  if (Array.isArray(storage?.componentList)) {
    console.log(`[useLayout] 从${source}恢复`, storage.componentList.length, '个组件')
    await updateComponent(storage, true)
    return true
  }
  return false
}

// 从后端或 sessionStorage 加载项目数据（在 DOM 就绪后调用）
async function loadProjectData() {
  const { updateComponent } = useSync()

  try {
    const id = fetchRouteParamsLocation()

    // 工作台以后台保存内容为准，避免 sessionStorage 旧缓存覆盖发布后的配置
    if (id) {
      const res = await getProjectDetailApi(String(id))
      const project = res?.data
      if (project?.componentData) {
        const parsed = JSON.parse(project.componentData)
        if (await applyProjectStorage(parsed, updateComponent, '后端')) return
      }
    }

    // 后台没有可用组件时，再从 sessionStorage 兜底恢复
    const storageList = getSessionStorage(StorageEnum.GO_CHART_STORAGE_LIST) as any[]
    if (storageList && id) {
      const found = storageList.find((item: any) => String(item.id) === String(id))
      await applyProjectStorage(found, updateComponent, 'SessionStorage')
    }
  } catch (e) {
    console.warn('[useLayout] 项目数据恢复失败:', e)
  }
}

// 布局处理
export const useLayout = (fn: () => Promise<void>) => {
  let removeScale: Function = () => { }
  onMounted(async () => {
    // 设置 Dom 值(ref 不生效先用 document)
    chartEditStore.setEditCanvas(
      EditCanvasTypeEnum.EDIT_LAYOUT_DOM,
      document.getElementById('go-chart-edit-layout')
    )
    chartEditStore.setEditCanvas(
      EditCanvasTypeEnum.EDIT_CONTENT_DOM,
      document.getElementById('go-chart-edit-content')
    )

    // 先从 sessionStorage / 后端加载项目数据（DOM 已就绪，computedScale 可以正确算缩放）
    await loadProjectData()

    // 获取数据（原有自定义回调，保持兼容）
    await fn()

    // 监听初始化（此时 componentList 已有数据，首次 listenerScale -> computedScale 能正确执行）
    removeScale = chartEditStore.listenerScale()
  })

  onUnmounted(() => {
    chartEditStore.setEditCanvas(EditCanvasTypeEnum.EDIT_LAYOUT_DOM, null)
    chartEditStore.setEditCanvas(EditCanvasTypeEnum.EDIT_CONTENT_DOM, null)
    removeScale()
  })
}
