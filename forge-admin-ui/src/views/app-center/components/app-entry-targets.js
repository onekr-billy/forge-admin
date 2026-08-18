const RUNTIME_TARGET_ENTRY_MODES = new Set(['RUNTIME', 'H5'])

export function supportsRuntimeTarget(entryMode) {
  return RUNTIME_TARGET_ENTRY_MODES.has(String(entryMode || '').trim().toUpperCase())
}

export function buildRuntimePageOptions({
  objectPages = [],
  applicationPages = [],
  currentTargetPageKey = '',
} = {}) {
  const options = []
  const seen = new Set()
  appendOptions(options, seen, objectPages
    .filter(page => page?.pageKey)
    .map(page => ({
      label: `${page.pageName || page.pageKey}${page.pageKey === 'list' ? '（默认）' : ''}`,
      value: String(page.pageKey),
    })))
  appendOptions(options, seen, applicationPages
    .filter(page => page?.type === 'page' && (page.id || page.pageKey))
    .map(page => ({
      label: `${page.title || page.pageName || page.pageKey || '未命名页面'}（自由编排）`,
      value: String(page.pageKey || page.id),
    })))

  if (!options.length)
    appendOptions(options, seen, [{ label: '列表页（默认）', value: 'list' }])
  const current = String(currentTargetPageKey || '').trim()
  if (current && !seen.has(current))
    options.push({ label: pageLabelFallback(current), value: current })
  return options
}

export function buildRuntimeTargetPreview({
  entryMode = 'RUNTIME',
  appType = 'WEB',
  appId = '',
  configKey = '',
  runtimeOpenMode = 'LIST',
  targetPageKey = 'list',
  targetFormKey = '',
} = {}) {
  // 与后端 BusinessAppOpenService#buildRuntimeTargetRoute 保持同一套参数，
  // 否则预览路径会指向不存在的路由。
  const mobile = String(entryMode || '').toUpperCase() === 'H5'
    || String(appType || '').toUpperCase() === 'MOBILE'

  if (mobile) {
    // H5 运行时页面（forge-h5-ui）以 configKey 加载完整运行时配置，
    // 同时透传 appId 和 mode，让 H5 lowcode-runtime 知道入口类型（列表/新增/详情）。
    // dev 下 H5 应用挂在根路径（/#/pages/...），生产由 nginx 代理 /forge-h5/ 前缀。
    const mode = String(runtimeOpenMode || 'LIST').toUpperCase()
    const query = new URLSearchParams()
    if (appId)
      query.set('appId', String(appId))
    if (configKey)
      query.set('configKey', configKey)
    if (mode === 'CREATE_FORM')
      query.set('mode', 'create')
    else if (mode === 'DETAIL')
      query.set('mode', 'detail')
    const path = '/#/pages/lowcode-runtime'
    const qs = query.toString()
    return { mobile, path, query: qs, value: qs ? `${path}?${qs}` : path }
  }

  const mode = String(runtimeOpenMode || 'LIST').toUpperCase()
  const query = new URLSearchParams()
  if (appId)
    query.set('appId', String(appId))
  if (configKey)
    query.set('configKey', configKey)
  query.set('runtimeOpenMode', mode)
  query.set('pageKey', targetPageKey || (mode === 'DETAIL' ? 'detail' : 'list'))
  if (targetFormKey)
    query.set('formKey', targetFormKey)
  if (mode === 'CREATE_FORM')
    query.set('mode', 'create')
  else if (mode === 'DETAIL')
    query.set('mode', 'detail')

  const path = `/ai/crud-page/${encodeURIComponent(configKey || ':configKey')}`
  return { mobile, path, query: query.toString(), value: `${path}?${query.toString()}` }
}

function appendOptions(target, seen, source) {
  source.forEach((item) => {
    if (!item.value || seen.has(item.value))
      return
    seen.add(item.value)
    target.push(item)
  })
}

function pageLabelFallback(value) {
  return {
    list: '列表页（默认）',
    detail: '详情页',
    create: '新增页',
    edit: '编辑页',
  }[value] || value || '列表页'
}

/**
 * 根据入口配置构建「打开入口」时跳转的目标路径。
 * - H5 / 移动入口 → /#/pages/lowcode-runtime?configKey=xxx
 * - WEB 运行时入口 → /ai/crud-page/:configKey?完整参数
 * - 外部页面 / 看板 → entry.entryUrl
 * - 兜底 → /app-center/app/:id（管理端应用路由）
 *
 * 返回值为相对路径（以 / 开头），调用方需自行拼 origin。
 * 返回空串表示入口尚未关联可运行配置。
 */
export function buildEntryOpenUrl(entry = {}) {
  const appType = String(entry.appType || '').toUpperCase()
  const entryMode = String(entry.entryMode || '').toUpperCase()
  const configKey = String(entry.configKey || '').trim()
  const options = parseOptionsString(entry.options)

  // 运行时入口（WEB + H5 / 移动）
  if (entryMode === 'RUNTIME' || entryMode === 'H5' || appType === 'MOBILE') {
    if (!configKey)
      return ''
    const preview = buildRuntimeTargetPreview({
      entryMode,
      appType,
      appId: entry.id,
      configKey,
      runtimeOpenMode: entry.runtimeOpenMode || options.runtimeOpenMode || 'LIST',
      targetPageKey: options.targetPageKey || '',
      targetFormKey: options.targetFormKey || '',
    })
    // 移动端 / H5 入口：用用户配置的 h5BaseUrl 拼完整 URL，默认 http://localhost:3001
    if (preview.mobile) {
      const h5BaseUrl = String(options.h5BaseUrl || '').trim() || 'http://localhost:3001'
      return `${h5BaseUrl}${preview.value}`
    }
    return preview.value
  }

  // 外部页面 / 看板 / 其他：直接打开入口 URL
  if (entry.entryUrl)
    return entry.entryUrl

  // 兜底：管理端应用路由
  return `/app-center/app/${entry.id}`
}

function parseOptionsString(options) {
  if (!options)
    return {}
  if (typeof options === 'object')
    return options
  try {
    const parsed = JSON.parse(options)
    return parsed && typeof parsed === 'object' ? parsed : {}
  }
  catch {
    return {}
  }
}
