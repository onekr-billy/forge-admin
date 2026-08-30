export function normalizeInstanceStatus(status) {
  return String(status || '').trim().toLowerCase()
}

export function canInterveneInstance(status) {
  const value = normalizeInstanceStatus(status)
  return value === 'running' || value === 'active' || value === 'suspended'
}

export function canMutateRunningInstance(status) {
  const value = normalizeInstanceStatus(status)
  return value === 'running' || value === 'active'
}

export function isSuspendedInstance(status) {
  return normalizeInstanceStatus(status) === 'suspended'
}

export function compactParams(source = {}) {
  const result = {}
  Object.entries(source).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '')
      result[key] = value
  })
  return result
}

export function buildMonitorFormQuery(row = {}) {
  return compactParams({
    taskId: row.taskId,
    businessKey: row.businessKey,
    processInstanceId: row.processInstanceId || row.id,
    processDefKey: row.processDefKey || row.processDefinitionKey,
    taskDefKey: row.taskDefKey || row.taskDefinitionKey,
    objectCode: row.objectCode,
    recordId: row.recordId,
    formKey: row.formKey,
  })
}

export function buildCurrentTaskOptions(tasks = []) {
  return (Array.isArray(tasks) ? tasks : []).map(task => ({
    label: [task.name || task.taskName || '当前任务', task.assigneeName || task.assignee || '待认领']
      .filter(Boolean)
      .join(' · '),
    value: String(task.id || task.taskId || ''),
    task,
  })).filter(item => item.value)
}
