export function normalizeFlowUserTasks(list = []) {
  return (Array.isArray(list) ? list : [])
    .map(item => ({
      taskDefKey: normalizeText(item?.taskDefKey || item?.id),
      taskName: normalizeText(item?.taskName || item?.name),
      formKey: normalizeText(item?.formKey),
      assignee: normalizeText(item?.assignee || item?.assigneeName),
      candidateUsers: normalizeList(item?.candidateUsers || item?.userCandidates || item?.users),
      candidateGroups: normalizeList(item?.candidateGroups || item?.roleCandidates || item?.groups),
    }))
    .filter(item => item.taskDefKey)
}

export function buildFlowNodePermissionRows(userTasks = [], permissions = []) {
  const tasks = normalizeFlowUserTasks(userTasks)
  const permissionMap = new Map((Array.isArray(permissions) ? permissions : [])
    .map(permission => [normalizeText(permission?.nodeKey), normalizePermission(permission)])
    .filter(([nodeKey]) => nodeKey))
  const taskKeys = new Set(tasks.map(task => task.taskDefKey))

  return [
    ...tasks.map((task) => {
      const permission = permissionMap.get(task.taskDefKey) || normalizePermission({ nodeKey: task.taskDefKey })
      return {
        ...permission,
        nodeKey: task.taskDefKey,
        nodeName: task.taskName || task.taskDefKey,
        assigneeSummary: resolveFlowTaskAssignee(task),
        stale: false,
      }
    }),
    ...(Array.isArray(permissions) ? permissions : [])
      .map(normalizePermission)
      .filter(permission => permission.nodeKey && !taskKeys.has(permission.nodeKey))
      .map(permission => ({
        ...permission,
        nodeName: permission.nodeKey,
        assigneeSummary: '原流程节点',
        stale: true,
      })),
  ]
}

export function buildPageSectionOptions(pageSections = [], configuredIds = []) {
  const options = (Array.isArray(pageSections) ? pageSections : [])
    .map((section) => {
      const value = normalizeText(section?.sectionId || section?.id)
      return value
        ? { label: normalizeText(section?.title || section?.name) || value, value, invalid: false }
        : null
    })
    .filter(Boolean)
  const known = new Set(options.map(option => option.value))
  normalizeList(configuredIds).forEach((value) => {
    if (!known.has(value)) {
      options.push({ label: `${value}（已失效）`, value, invalid: true })
      known.add(value)
    }
  })
  return options
}

export function resolveFlowTaskAssignee(task = {}) {
  if (task.assignee)
    return `办理人：${task.assignee}`
  if (task.candidateGroups?.length)
    return `角色：${task.candidateGroups.join('、')}`
  if (task.candidateUsers?.length)
    return `用户：${task.candidateUsers.join('、')}`
  return '由流程设计器配置'
}

function normalizePermission(value = {}) {
  return {
    nodeKey: normalizeText(value.nodeKey || value.taskDefKey),
    visibleSectionIds: normalizeList(value.visibleSectionIds),
    readonlySectionIds: normalizeList(value.readonlySectionIds),
  }
}

function normalizeList(value = []) {
  if (Array.isArray(value))
    return value.map(normalizeText).filter(Boolean)
  const text = normalizeText(value)
  return text ? [text] : []
}

function normalizeText(value) {
  return String(value ?? '').trim()
}
