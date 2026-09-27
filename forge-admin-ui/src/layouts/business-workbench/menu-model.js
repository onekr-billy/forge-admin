// 输入为权限仓库已授权的菜单，不为工作台附加任何示例菜单。
export function buildWorkbenchMenus(items = []) {
  return items.flatMap((item) => {
    if (item.meta?.hidden || item.visible === 0 || item.menuStatus === 0)
      return []
    const children = buildWorkbenchMenus(item.children || [])
    if (item.type === 'subapp')
      return children
    if (!children.length && (item.type === 'module' || !item.path))
      return []
    return [{ ...item, key: String(item.key ?? item.id), label: item.label || item.name || item.meta?.title || '', children }]
  })
}

export function findWorkbenchTrail(items, key) {
  if (key === null || key === undefined)
    return []
  for (const item of items) {
    if (item.key === String(key))
      return [item]
    const children = findWorkbenchTrail(item.children, key)
    if (children.length)
      return [item, ...children]
  }
  return []
}

export function buildMegaSections(menu) {
  return (menu?.children || []).map(child => ({
    key: child.key,
    title: child.label,
    icon: child.icon,
    entry: child,
    items: child.children,
    hasChildren: Boolean(child.children.length),
  }))
}

function includesKeyword(value, keyword) {
  return String(value || '').toLocaleLowerCase().includes(keyword)
}

function filterMenuItems(items, keyword) {
  return items.flatMap((item) => {
    if (includesKeyword(item.label, keyword))
      return [item]

    const children = filterMenuItems(item.children || [], keyword)
    return children.length ? [{ ...item, children }] : []
  })
}

export function filterMegaSections(sections = [], search = '') {
  const keyword = String(search || '').trim().toLocaleLowerCase()
  if (!keyword)
    return sections

  return sections.flatMap((section) => {
    if (includesKeyword(section.title, keyword))
      return [section]

    const items = filterMenuItems(section.items || [], keyword)
    return items.length ? [{ ...section, items }] : []
  })
}

export function countMenuLeaves(items = []) {
  return items.reduce((total, item) => total + (item.children?.length ? countMenuLeaves(item.children) : 1), 0)
}
