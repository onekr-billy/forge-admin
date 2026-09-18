// 路由只携带来源标识；实际归属和记录权限由服务端重新核验。
const positive = value => /^[1-9]\d*$/.test(String(value || '')) ? String(value) : null
const text = value => typeof value === 'string' ? value : null
export function printSourceFromQuery(query) {
  const applicationId = positive(query.applicationId)
  const objectCode = text(query.objectCode)
  if (!applicationId || !objectCode || !['LOWCODE', 'CODE'].includes(query.sourceType))
    return null
  const source = { applicationId, objectCode, sourceType: query.sourceType, pageId: null, formKey: null }
  if (source.sourceType === 'LOWCODE')
    source.pageId = positive(query.pageId)
  else source.formKey = text(query.formKey)
  return source.pageId || source.formKey ? source : null
}
export function printRecordFromQuery(query) {
  const source = printSourceFromQuery(query)
  const recordId = text(query.recordId)
  if (!source || !recordId || !['LIST', 'DETAIL', 'FLOW_TODO', 'FLOW_DONE', 'FLOW_STARTED'].includes(query.scene))
    return null
  return { source, recordId, scene: query.scene, taskId: text(query.taskId), processInstanceId: text(query.processInstanceId), processRunId: positive(query.processRunId) }
}
