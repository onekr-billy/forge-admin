/** 编辑和详情共用的读取链路；只管理当前 CRUD 实例，不缓存业务状态或权限。 */
export function useRecordFormLoader({
  loadDetailOnEdit,
  loadDetail,
  callHook,
  applyDetailData,
  fetchRuntime,
  resolveRuntimeObjectCode,
  resolveRowKeyValue,
  detailRuntime,
  detailRuntimeLoading,
  showLoading,
  closeLoading,
  warn = console.warn,
}) {
  let runtimeSequence = 0

  async function loadDetailRuntime(row) {
    const sequence = ++runtimeSequence
    const objectCode = resolveRuntimeObjectCode({}, row)
    const recordId = resolveRowKeyValue(row)
    detailRuntime.value = row?._documentRuntime || null
    detailRuntimeLoading.value = false
    if (!objectCode || recordId == null || recordId === '')
      return
    detailRuntimeLoading.value = true
    try {
      const response = await fetchRuntime(objectCode, recordId)
      if (sequence === runtimeSequence)
        detailRuntime.value = response?.data || null
    }
    catch (error) {
      if (sequence === runtimeSequence)
        warn('[AiCrudPage] 加载审批流程运行态失败:', error?.message || error)
    }
    finally {
      if (sequence === runtimeSequence)
        detailRuntimeLoading.value = false
    }
  }

  async function loadRecordForm(row) {
    const needsDetail = loadDetailOnEdit()
    if (needsDetail)
      showLoading()
    try {
      const [loaded] = await Promise.all([
        needsDetail
          ? loadDetail(row)
          : callHook('beforeRenderDetail', row, data => data).then(applyDetailData),
        loadDetailRuntime(row),
      ])
      return loaded !== false
    }
    finally {
      if (needsDetail)
        closeLoading()
    }
  }

  return { loadRecordForm, loadDetailRuntime }
}
