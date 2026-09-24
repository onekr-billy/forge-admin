/**
 * 同一个应用运行页的路由状态只加载一次。
 *
 * Vue Router 在首次进入、query 归一化或 KeepAlive 激活时可能连续通知相同状态。
 * coordinator 会合并相同 key，并在加载期间只保留最后一个不同 key，避免旧请求覆盖新路由。
 */
export function createApplicationRuntimeLoadCoordinator(loadFn) {
  let activePromise = null
  let queuedKey = ''
  let loadedKey = ''

  async function drain() {
    let executed = false
    while (queuedKey && queuedKey !== loadedKey) {
      const nextKey = queuedKey
      queuedKey = ''
      await loadFn(nextKey)
      loadedKey = nextKey
      executed = true
    }
    return executed
  }

  function run(key) {
    const normalizedKey = String(key || '')
    if (!normalizedKey || normalizedKey === loadedKey)
      return Promise.resolve(false)

    queuedKey = normalizedKey
    if (activePromise)
      return activePromise

    const task = drain()
    const wrapped = task.finally(() => {
      if (activePromise === wrapped)
        activePromise = null
    })
    activePromise = wrapped
    return activePromise
  }

  function invalidate() {
    loadedKey = ''
  }

  return { run, invalidate }
}

export function resolveApplicationRuntimeLoadKey(route = {}, canEditApplication = false) {
  const params = route.params || {}
  const query = route.query || {}
  // 只按「应用 + 是否读工作台草稿」区分加载源。
  // edit/draft 已折叠进 workspace：有编辑权限时进出表单/页面设计（切 edit）不该整页重载。
  // 不要把 edit 单独放进 key：新建页进入编辑态时 edit 从无到有，会重拉服务端草稿，
  // 冲掉本地刚创建、尚未写入服务端的页面。
  return JSON.stringify({
    applicationCode: String(params.applicationCode || ''),
    // 有编辑权限时页面管理也读草稿；key 需区分，避免权限晚到时仍停留在已发布快照
    workspace: shouldUseApplicationWorkspaceLoad(route, canEditApplication),
    // 无编辑权限的访客用 draft 预览时仍要区分已发布快照
    draftPreview: !canEditApplication && query.draft === '1',
  })
}

/**
 * 编辑态 / 草稿预览 / 有编辑权限的页面管理：读工作台草稿（含未发布页面）。
 * 普通运行用户：读已发布快照。
 */
export function shouldUseApplicationWorkspaceLoad(route = {}, canEditApplication = false) {
  const query = route.query || {}
  if (query.edit === '1' || query.draft === '1')
    return true
  return Boolean(canEditApplication)
}
