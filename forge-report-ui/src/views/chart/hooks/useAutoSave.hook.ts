import { watch, ref, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { useChartEditStore } from '@/store/modules/chartEditStore/chartEditStore'
import { buildProjectPayload, updateProjectApi } from '@/api/project'

export type SaveStatus = 'idle' | 'saving' | 'saved' | 'error'

export function useAutoSave() {
  const chartEditStore = useChartEditStore()
  const route = useRoute()

  const saveStatus = ref<SaveStatus>('idle')
  const lastSaveTime = ref<string>('')
  const saveError = ref<string>('')

  let timer: ReturnType<typeof setTimeout> | null = null
  let enabled = true

  const doSave = async () => {
    const rawId = route.params.id
    const projectId = Array.isArray(rawId) ? rawId[0] : rawId as string
    if (!projectId || !enabled) return

    saveStatus.value = 'saving'
    saveError.value = ''
    try {
      const storageInfo = chartEditStore.getProjectStorageInfo()
      const payload = buildProjectPayload(projectId, storageInfo)
      await updateProjectApi(payload)
      saveStatus.value = 'saved'
      const now = new Date()
      lastSaveTime.value = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}:${String(now.getSeconds()).padStart(2, '0')}`
      // 3 秒后恢复 idle
      setTimeout(() => {
        if (saveStatus.value === 'saved') saveStatus.value = 'idle'
      }, 3000)
    } catch (e: any) {
      saveStatus.value = 'error'
      saveError.value = e?.message || '保存失败'
    }
  }

  const scheduleSave = () => {
    if (!enabled) return
    saveStatus.value = 'idle'
    if (timer) clearTimeout(timer)
    timer = setTimeout(doSave, 3000)
  }

  watch(
    () => [
      chartEditStore.getEditCanvasConfig.projectName,
      chartEditStore.getEditCanvasConfig.width,
      chartEditStore.getEditCanvasConfig.height,
      chartEditStore.getEditCanvasConfig.background,
      chartEditStore.getComponentList.length,
      chartEditStore.getProjectPages.length,
      chartEditStore.getProjectPages.map(item => `${item.id}:${item.name}:${item.sort}`).join('|'),
      chartEditStore.getActivePageId,
      chartEditStore.getHomePageId,
    ],
    () => {
      scheduleSave()
    }
  )

  watch(
    () => chartEditStore.getComponentList,
    () => {
      scheduleSave()
    },
    { deep: true }
  )

  onUnmounted(() => {
    enabled = false
    if (timer) clearTimeout(timer)
  })

  return { saveStatus, lastSaveTime, saveError }
}
