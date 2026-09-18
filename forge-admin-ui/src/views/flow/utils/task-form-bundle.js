/**
 * Loads the business form context and reuses the Flow task-form snapshot embedded
 * by the backend. A direct Flow request is retained only for rolling upgrades.
 */
export async function loadTaskFormBundle(options = {}) {
  const {
    row = {},
    initialFormInfo = {},
    loadBusinessContext,
    loadFlowFormInfo,
    isConfiguredBusinessContext = () => false,
  } = options

  let businessContext = await loadBusinessContext(row, initialFormInfo)
  let formInfo = businessContext?.taskFormInfo || null

  if (!formInfo) {
    formInfo = await loadFlowFormInfo()
    if (!isConfiguredBusinessContext(businessContext) && formInfo)
      businessContext = await loadBusinessContext(row, formInfo)
  }

  return { businessContext, formInfo }
}
