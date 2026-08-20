/**
 * 旧应用工作台已废弃。保留命名路由，按 section 映射到页面管理、设置或发布页。
 */
export function resolveDeprecatedWorkspaceLocation(to = {}) {
  const applicationCode = String(to.params?.applicationCode || '').trim()
  const query = { ...(to.query || {}) }
  const section = String(query.section || '').trim()
  delete query.section

  if (!applicationCode)
    return { path: '/app-center' }

  if (section === 'releases' || query.publish === '1') {
    delete query.publish
    return {
      name: 'BusinessApplicationPublish',
      params: { applicationCode },
      query,
    }
  }

  if (section === 'permissions') {
    return {
      name: 'BusinessApplicationSettings',
      params: { applicationCode },
      query: { ...query, section: 'permission' },
    }
  }

  const runtimeQuery = { ...query }
  if (section === 'automation')
    runtimeQuery.edit = runtimeQuery.edit || '1'
  if (section && !['overview', 'objects', 'entries'].includes(section))
    runtimeQuery.designSection = runtimeQuery.designSection || section

  return {
    name: 'BusinessApplicationRuntime',
    params: { applicationCode },
    query: runtimeQuery,
  }
}
