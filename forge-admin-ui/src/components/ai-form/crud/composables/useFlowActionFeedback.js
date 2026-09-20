import {
  mergeDocumentRuntimeSnapshot,
  scheduleFlowRuntimeRefresh,
} from '../../business-action-runtime'

/**
 * 流程动作的即时反馈与后台校准。该组合式只管理当前 CRUD 页内的状态，
 * 服务端仍是最终事实来源。
 */
export function useFlowActionFeedback({
  dataSource,
  currentRow,
  detailRuntime,
  formData,
  resolveRowKeyValue,
  resolveDocumentRuntime,
  resolveRuntimeObjectCode,
  refreshCurrentDetailRuntime,
  loadList,
  schedule = (task, delay) => globalThis.setTimeout(task, delay),
  clearSchedule = timerId => globalThis.clearTimeout(timerId),
  onRefreshError = error => console.warn('[AiCrudPage] 后台校准流程运行态失败:', error?.message || error),
} = {}) {
  let refreshTimer = null

  function applyDocumentRuntimeSnapshot(row, runtime, objectCode = '') {
    if (!row || !runtime || typeof runtime !== 'object')
      return
    const recordId = resolveRowKeyValue(row)
    const matchesRecord = candidate => String(resolveRowKeyValue(candidate)) === String(recordId)
    const mergeRuntime = candidate => mergeDocumentRuntimeSnapshot(candidate, runtime, objectCode)

    Object.assign(row, mergeRuntime(row))
    dataSource.value = dataSource.value.map(candidate => matchesRecord(candidate) ? mergeRuntime(candidate) : candidate)

    if (!matchesRecord(currentRow.value))
      return
    currentRow.value = mergeRuntime(currentRow.value)
    detailRuntime.value = { ...(detailRuntime.value || {}), ...runtime }
    formData.value = mergeRuntime(formData.value)
  }

  function applyApplicationProcessRuntimeSnapshot(row, processCode, processRun = {}) {
    if (!row || !String(processCode || '').trim())
      return
    const currentRuntime = resolveDocumentRuntime(row) || {}
    const status = String(processRun?.status || '').toUpperCase()
    const isActive = !['SUCCESS', 'FAILED', 'CANCELED'].includes(status)
    const activeProcessCodes = new Set(Array.isArray(currentRuntime.activeProcessCodes)
      ? currentRuntime.activeProcessCodes.map(String)
      : [])
    const startedProcessCodes = new Set(Array.isArray(currentRuntime.startedProcessCodes)
      ? currentRuntime.startedProcessCodes.map(String)
      : [])
    if (isActive)
      activeProcessCodes.add(String(processCode))
    else
      activeProcessCodes.delete(String(processCode))
    if (status !== 'FAILED')
      startedProcessCodes.add(String(processCode))
    applyDocumentRuntimeSnapshot(row, {
      ...currentRuntime,
      activeProcessCodes: [...activeProcessCodes],
      startedProcessCodes: [...startedProcessCodes],
    }, resolveRuntimeObjectCode({}, row))
  }

  function queueFlowRuntimeRefresh(row = {}) {
    disposeFlowRuntimeRefresh()
    refreshTimer = scheduleFlowRuntimeRefresh(async () => {
      refreshTimer = null
      await Promise.all([
        refreshCurrentDetailRuntime(row),
        loadList(),
      ])
    }, {
      schedule,
      onError: onRefreshError,
    })
  }

  function disposeFlowRuntimeRefresh() {
    if (refreshTimer === null)
      return
    clearSchedule(refreshTimer)
    refreshTimer = null
  }

  return {
    applyApplicationProcessRuntimeSnapshot,
    applyDocumentRuntimeSnapshot,
    disposeFlowRuntimeRefresh,
    queueFlowRuntimeRefresh,
  }
}
