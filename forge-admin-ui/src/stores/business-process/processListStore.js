import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { businessProcessPage, businessProcessRunPage } from '@/api/business-process'

// 流程列表与运行面板共享应用上下文和联动筛选，不跨面板透传状态。
export const useProcessListStore = defineStore('business-process-list', () => {
  const applicationId = ref('')
  const objects = ref([])
  const activeSection = ref('list')
  const runFilters = ref({})
  const runRecords = ref([])
  const runTotal = ref(0)
  const runPage = ref(1)
  const runPageSize = ref(10)
  const runLoading = ref(false)
  const runError = ref('')
  const processOptions = ref([])
  const optionsLoading = ref(false)
  const selectedProcess = ref(null)
  let runRequest = 0
  let optionsRequest = 0

  const objectOptions = computed(() => objects.value.map(item => ({
    label: item.objectName || item.objectCode,
    value: String(item.objectId || item.id || ''),
    code: item.objectCode,
    role: item.objectRole,
  })).filter(item => item.value))

  function syncContext(application, initialObjects) {
    const id = String(application?.id || '')
    if (id !== applicationId.value) {
      runRequest += 1
      optionsRequest += 1
      applicationId.value = id
      activeSection.value = 'list'
      runFilters.value = {}
      runRecords.value = []
      runTotal.value = 0
      runPage.value = 1
      runPageSize.value = 10
      runLoading.value = false
      runError.value = ''
      processOptions.value = []
      optionsLoading.value = false
      selectedProcess.value = null
    }
    objects.value = initialObjects || []
  }

  function viewRuns(process) {
    selectedProcess.value = { label: process.processName || process.processCode, value: String(process.id) }
    processOptions.value = [selectedProcess.value, ...processOptions.value.filter(item => item.value !== selectedProcess.value.value)]
    runFilters.value = { processId: String(process.id) }
    runPage.value = 1
    activeSection.value = 'runs'
  }

  function setRunFilters(values = {}) {
    const processId = values.processId == null ? undefined : String(values.processId)
    selectedProcess.value = processOptions.value.find(item => item.value === processId) || null
    runFilters.value = { processId, subjectObjectCode: values.subjectObjectCode, status: values.status }
  }

  async function loadRuns() {
    if (!applicationId.value)
      return
    const requestId = ++runRequest
    runLoading.value = true
    runError.value = ''
    try {
      const response = await businessProcessRunPage({
        applicationId: applicationId.value,
        processId: runFilters.value.processId || undefined,
        subjectObjectCode: runFilters.value.subjectObjectCode || undefined,
        status: runFilters.value.status || undefined,
        pageNum: runPage.value,
        pageSize: runPageSize.value,
      })
      if (requestId !== runRequest)
        return
      const data = response.data || {}
      runRecords.value = data.records || data.list || []
      runTotal.value = Number(data.total ?? runRecords.value.length)
    }
    catch (error) {
      if (requestId === runRequest) {
        runError.value = error?.response?.data?.message || error?.message || '运行记录加载失败'
        runRecords.value = []
        runTotal.value = 0
      }
    }
    finally {
      if (requestId === runRequest)
        runLoading.value = false
    }
  }

  async function searchProcesses(keyword = '') {
    if (!applicationId.value)
      return
    const requestId = ++optionsRequest
    optionsLoading.value = true
    try {
      const response = await businessProcessPage({ applicationId: applicationId.value, keyword: keyword || undefined, pageNum: 1, pageSize: 50 })
      if (requestId !== optionsRequest)
        return
      const options = (response.data?.records || response.data?.list || []).map(item => ({
        label: item.processName || item.processCode,
        value: String(item.id),
      }))
      if (selectedProcess.value && !options.some(item => item.value === selectedProcess.value.value))
        options.unshift(selectedProcess.value)
      processOptions.value = options
    }
    catch (error) {
      if (requestId === optionsRequest)
        window.$message?.error?.(error?.message || '流程选项加载失败，请重新搜索')
    }
    finally {
      if (requestId === optionsRequest)
        optionsLoading.value = false
    }
  }

  return {
    applicationId,
    activeSection,
    objectOptions,
    runFilters,
    runRecords,
    runTotal,
    runPage,
    runPageSize,
    runLoading,
    runError,
    processOptions,
    optionsLoading,
    syncContext,
    viewRuns,
    setRunFilters,
    loadRuns,
    searchProcesses,
  }
})
