export const PAGE_MANAGEMENT_SYSTEM_PAGES = Object.freeze([
  { id: 'system:workbench', title: '个人工作台', view: 'workbench', icon: 'grid', portalIcon: 'GridOutline' },
  { id: 'system:todo', title: '我的待办', view: 'todo', icon: 'checkbox', portalIcon: 'CheckboxOutline' },
  { id: 'system:done', title: '我已办的', view: 'done', icon: 'checkmark-done', portalIcon: 'CheckmarkDoneOutline' },
  { id: 'system:sent', title: '我发送的', view: 'sent', icon: 'paper-plane', portalIcon: 'PaperPlaneOutline' },
  { id: 'system:cc', title: '抄送我的', view: 'cc', icon: 'people', portalIcon: 'PeopleOutline' },
  { id: 'system:messages', title: '通知公告', view: 'messages', icon: 'notifications', portalIcon: 'NotificationsOutline' },
])

export function createPageManagementSystemNavigationNodes() {
  return PAGE_MANAGEMENT_SYSTEM_PAGES.map((page, index) => ({
    id: page.id,
    type: 'page',
    title: page.title,
    icon: page.portalIcon || page.icon,
    parentId: null,
    navigationVisible: true,
    systemView: page.view,
    // Published portal navigation sorts nodes numerically; keep the fixed
    // system block ahead of legacy/user nodes whose sort commonly starts at 0.
    sort: -1000 + index,
  }))
}

export function isPageManagementSystemPageId(pageId) {
  return String(pageId || '').startsWith('system:')
}

export function resolvePageManagementSystemPage(pageId) {
  return PAGE_MANAGEMENT_SYSTEM_PAGES.find(item => item.id === String(pageId || '')) || null
}

export function resolvePageManagementSelection(nodes = [], requestedId = '', homePageId = '') {
  const requested = String(requestedId || '').trim()
  if (resolvePageManagementSystemPage(requested))
    return requested
  const pages = (Array.isArray(nodes) ? nodes : []).filter(node => node?.type === 'page')
  if (pages.some(node => String(node.id) === requested))
    return requested
  const home = pages.find(node => String(node.id) === String(homePageId || ''))
  if (home)
    return String(home.id)
  if (pages[0])
    return String(pages[0].id)
  return PAGE_MANAGEMENT_SYSTEM_PAGES[0].id
}
