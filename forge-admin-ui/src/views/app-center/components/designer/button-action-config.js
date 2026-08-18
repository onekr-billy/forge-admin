export function normalizeButtonActionDraft(action = {}) {
  const type = String(action.type || '').trim().toLowerCase()
  const actionType = String(action.actionType || '').trim().toUpperCase()
  let behaviorType = 'submit'
  if (type === 'navigate' || actionType === 'NAVIGATE')
    behaviorType = 'navigate'
  else if (['process', 'start_process'].includes(type) || actionType === 'START_PROCESS')
    behaviorType = 'process'
  else if (type === 'action')
    behaviorType = 'custom'
  return {
    behaviorType,
    targetPageKey: behaviorType === 'navigate'
      ? firstNonBlank(action.targetPageKey, action.actionCode)
      : '',
    processCode: ['process', 'custom'].includes(behaviorType)
      ? firstNonBlank(action.processCode, action.actionCode)
      : '',
    processId: ['process', 'custom'].includes(behaviorType) ? firstNonBlank(action.processId) : '',
    permissionCode: firstNonBlank(action.permissionKey, action.permissionCode),
  }
}

export function buildBottomActionConfig(action = {}, draft = {}) {
  const behaviorType = ['submit', 'navigate', 'process', 'custom'].includes(draft.behaviorType)
    ? draft.behaviorType
    : 'submit'
  const next = {
    ...action,
    actionCode: '',
  }
  delete next.actionType
  delete next.targetPageKey
  delete next.processCode
  delete next.processId

  if (behaviorType === 'navigate') {
    const targetPageKey = firstNonBlank(draft.targetPageKey)
    return {
      ...next,
      type: 'navigate',
      actionType: 'NAVIGATE',
      actionCode: targetPageKey,
      targetPageKey,
    }
  }
  if (['process', 'custom'].includes(behaviorType)) {
    const processCode = firstNonBlank(draft.processCode)
    const processId = firstNonBlank(draft.processId)
    const permissionCode = firstNonBlank(draft.permissionCode)
    return {
      ...next,
      type: behaviorType === 'process' ? 'process' : 'action',
      actionType: behaviorType === 'process' ? 'START_PROCESS' : 'BUSINESS_PROCESS_ACTION',
      actionCode: processCode,
      processCode,
      processId,
      permissionKey: permissionCode,
      permissionCode,
    }
  }
  return {
    ...next,
    type: 'save',
  }
}

function firstNonBlank(...values) {
  for (const value of values) {
    const text = String(value ?? '').trim()
    if (text)
      return text
  }
  return ''
}
