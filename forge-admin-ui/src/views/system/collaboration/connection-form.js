/** The list returns a summary; GET /:id returns a composition VO. Accept both without mutating either. */
export function normalizeConnectionDetail(value) {
  if (!value)
    return value
  const data = { ...(value.connection || value) }
  if (data.status != null)
    data.status = String(data.status)
  if (data.defaultOrgId != null)
    data.defaultOrgId = String(data.defaultOrgId)
  for (const key of ['todoPushEnabled', 'ssoWorkbenchEnabled', 'syncScheduleEnabled'])
    data[key] = Number(data[key]) === 1 ? 1 : 0
  data.defaultRoleIds = Array.isArray(data.defaultRoleIds)
    ? data.defaultRoleIds.map(String)
    : String(data.defaultRoleIds || '').split(',').map(id => id.trim()).filter(Boolean)
  return data
}
