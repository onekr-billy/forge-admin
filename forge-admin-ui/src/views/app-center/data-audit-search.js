const USER_LABEL_FIELDS = ['actorIdName', 'actorName', 'realName', 'nickname']

export function normalizeDataAuditFilterOptions(payload = {}) {
  return (Array.isArray(payload?.applications) ? payload.applications : []).map(application => ({
    applicationId: String(application.applicationId || ''),
    applicationName: application.applicationName || '未命名应用',
    pages: (Array.isArray(application.pages) ? application.pages : []).map(page => ({
      pageId: String(page.pageId || ''),
      pageName: page.pageName || '未命名页面',
      objectId: String(page.objectId || ''),
      objectName: page.objectName || '',
      fields: (Array.isArray(page.fields) ? page.fields : []).map(field => ({
        fieldCode: String(field.fieldCode || ''),
        fieldLabel: field.fieldLabel || '未命名字段',
      })).filter(field => field.fieldCode),
    })).filter(page => page.pageId && page.objectId),
  })).filter(application => application.applicationId && application.pages.length)
}

export function buildDataAuditApplicationOptions(applications = []) {
  return applications.map(application => ({
    label: application.applicationName,
    value: application.applicationId,
  }))
}

export function findDataAuditPages(applications = [], applicationId) {
  return applications.find(item => item.applicationId === String(applicationId || ''))?.pages || []
}

export function findDataAuditPage(applications = [], applicationId, pageId) {
  return findDataAuditPages(applications, applicationId)
    .find(item => item.pageId === String(pageId || '')) || null
}

export function buildDataAuditPageOptions(applications = [], applicationId) {
  return findDataAuditPages(applications, applicationId).map(page => ({
    label: page.pageName,
    value: page.pageId,
  }))
}

export function buildDataAuditFieldOptions(applications = [], applicationId, pageId) {
  const page = findDataAuditPage(applications, applicationId, pageId)
  return (page?.fields || []).map(field => ({
    label: field.fieldLabel,
    value: field.fieldCode,
  }))
}

export function buildDataAuditSearchParams(
  params = {},
  applications = [],
  formatDateTime = value => value,
) {
  const result = { ...params, accessMode: 'AUDIT' }
  USER_LABEL_FIELDS.forEach(field => delete result[field])
  const page = findDataAuditPage(applications, params.applicationId, params.pageId)
  if (page)
    result.objectId = page.objectId
  else
    delete result.fieldCode
  if (Array.isArray(params.timeRange) && params.timeRange.length === 2) {
    result.startTime = formatDateTime(params.timeRange[0])
    result.endTime = formatDateTime(params.timeRange[1])
  }
  delete result.applicationId
  delete result.pageId
  delete result.timeRange
  return result
}
