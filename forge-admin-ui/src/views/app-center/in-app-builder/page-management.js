export const PAGE_MANAGEMENT_SYSTEM_PAGES = Object.freeze([
  { id: 'system:workbench', title: '个人工作台', view: 'workbench', icon: 'grid' },
  { id: 'system:todo', title: '我的待办', view: 'todo', icon: 'checkbox' },
  { id: 'system:done', title: '我已办的', view: 'done', icon: 'checkmark-done' },
  { id: 'system:sent', title: '我发送的', view: 'sent', icon: 'paper-plane' },
  { id: 'system:cc', title: '抄送我的', view: 'cc', icon: 'people' },
])

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
