/**
 * 应用中心页面「系统菜单挂载」配置读写。
 * 与页面发布面板、左侧导航「挂载系统菜单」弹窗共用同一套字段语义。
 */

export const PAGE_MOUNT_SETTINGS_FIELDS = [
  'systemMenuVisible',
  'mountTarget',
  'menuName',
  'menuParentId',
  'mobileMenuParentId',
  'menuSort',
]

export function resolvePageMountTarget(node = null) {
  if (!node)
    return 'ADMIN'
  return String(node.mountTarget ?? node.settings?.mountTarget ?? 'ADMIN').toUpperCase()
}

export function resolvePageMenuName(node = null) {
  if (!node)
    return ''
  return node.menuName ?? node.settings?.menuName ?? ''
}

export function resolvePageMenuParentId(node = null) {
  if (!node)
    return null
  const value = node.menuParentId ?? node.settings?.menuParentId
  return value != null && value !== '' ? String(value) : null
}

export function resolvePageMobileMenuParentId(node = null) {
  if (!node)
    return null
  const value = node.mobileMenuParentId ?? node.settings?.mobileMenuParentId
  return value != null && value !== '' ? String(value) : null
}

export function resolvePageMenuSort(node = null) {
  if (!node)
    return 0
  const value = node.menuSort ?? node.settings?.menuSort
  return typeof value === 'number' ? value : Number(value) || 0
}

export function isPageSystemMenuMounted(node = null) {
  return Boolean(node?.systemMenuVisible ?? node?.settings?.systemMenuVisible)
}

export function createPageMountDraft(node = null) {
  return {
    systemMenuVisible: isPageSystemMenuMounted(node),
    mountTarget: resolvePageMountTarget(node),
    menuName: resolvePageMenuName(node),
    menuParentId: resolvePageMenuParentId(node),
    mobileMenuParentId: resolvePageMobileMenuParentId(node),
    menuSort: resolvePageMenuSort(node),
  }
}

export function showAdminMenuMountConfig(mountTarget) {
  const target = String(mountTarget || 'ADMIN').toUpperCase()
  return target === 'ADMIN' || target === 'BOTH'
}

export function showMobileMenuMountConfig(mountTarget) {
  const target = String(mountTarget || 'ADMIN').toUpperCase()
  return target === 'MOBILE' || target === 'BOTH'
}

/**
 * 将挂载字段写入导航节点（顶层 + settings 镜像），与 patchCurrentPageNode 一致。
 */
export function applyPageMountFieldsToNode(node = {}, partial = {}) {
  const next = { ...node, ...partial }
  const settingsPatch = {}
  for (const key of PAGE_MOUNT_SETTINGS_FIELDS) {
    if (Object.prototype.hasOwnProperty.call(partial, key))
      settingsPatch[key] = partial[key]
  }
  if (Object.keys(settingsPatch).length) {
    next.settings = {
      ...(next.settings || node.settings || {}),
      ...settingsPatch,
    }
  }
  return next
}
