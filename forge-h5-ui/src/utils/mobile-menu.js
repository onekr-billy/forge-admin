const TAB_ROUTES = new Set([
  '/pages/index/index',
  '/pages/todo',
  '/pages/message/index',
  '/pages/mine/index',
])

const MENU_ACCENTS = ['#f43f5e', '#10b981', '#6366f1', '#3b82f6']
const GENERIC_MENU_ICONS = new Set([
  '', '-1', 'apps', 'apps-outline', 'appsoutline', 'appstore', 'appstore-outline',
  'grid', 'grid-outline', 'menu', 'menu-outline', 'application', 'applications',
])
const FALLBACK_MENU_ICONS = [
  'layout', 'box', 'briefcase', 'file-text', 'database', 'layers', 'package', 'tool',
]
const MENU_ICON_RULES = [
  [/(印章|用印|签章|合同)/, 'edit-3'],
  [/(店铺|门店|商城|商店)/, 'shopping-bag'],
  [/(资产|物料|库存|领用|产品)/, 'package'],
  [/(采购|购物|订单)/, 'shopping-cart'],
  [/(报销|费用|财务|付款|收款)/, 'credit-card'],
  [/(请假|日程|排班|考勤)/, 'calendar'],
  [/(用户|人员|员工|客户|成员)/, 'users'],
  [/(供应商|物流|运输)/, 'truck'],
  [/(审批|流程|工单|任务)/, 'check-square'],
  [/(消息|通知|公告)/, 'message-square'],
  [/(报表|统计|分析|看板)/, 'bar-chart-2'],
  [/(文件|文档|资料|档案)/, 'file-text'],
  [/(测试|调试|工具)/, 'tool'],
]

function visible(menu) {
  return menu && menu.visible !== 0 && menu.menuStatus !== 0
}

function isMobileMenu(menu) {
  // 与 PC 的 UserResourceTreeVO 一致：目录 1、菜单 2；按钮/API 不是导航。
  if (menu.resourceType !== undefined && Number(menu.resourceType) !== 2) return false
  return !menu.clientCode || String(menu.clientCode).toLowerCase() === 'h5'
}

export function resolveMobileMenuTarget(menu = {}) {
  for (const candidate of [menu.path, menu.component]) {
    const raw = String(candidate || '').trim()
    if (!raw || raw.includes('://')) continue
    const path = raw.startsWith('/') ? raw : `/${raw}`
    const pathname = path.split('?')[0]
    if (TAB_ROUTES.has(pathname)) return { url: path, tab: true }
    if (pathname === '/pages/lowcode-runtime') {
      const query = path.split('?')[1] || ''
      if (/(?:^|&)configKey=[^&]+/.test(query)) return { url: path, tab: false }
      continue
    }
    if (pathname === '/pages/app-entry') {
      const query = path.split('?')[1] || ''
      if (/(?:^|&)(?:configKey|runtimeConfigKey|pageConfigKey)=[^&]+/.test(query)) return { url: path, tab: false }
      continue
    }
    const configKey = raw.match(/(?:crud-page|crud)\/([^/?]+)/)?.[1]
    if (configKey) {
      return { url: `/pages/lowcode-runtime?configKey=${encodeURIComponent(configKey)}`, tab: false }
    }
  }
  return null
}

function sorted(list = []) {
  return [...list].sort((a, b) => Number(a?.sort || 0) - Number(b?.sort || 0))
}

export function resolveMobileMenuIcon(menu = {}) {
  const icon = String(menu.icon || '').trim()
  const normalized = icon
    .replace(/^ionicons5:/i, '')
    .replace(/^i-[^:]+:/i, '')
    .replace(/[^a-z0-9-]/gi, '')
    .toLowerCase()
  // 接口配置了明确业务图标时仍原样使用；Apps/Grid 等通用占位图标
  // 无法区分功能，按真实菜单语义生成稳定的移动端图标。
  if (icon && !GENERIC_MENU_ICONS.has(normalized)) return icon

  const identity = [menu.resourceName, menu.title, menu.name, menu.path, menu.component]
    .filter(Boolean)
    .join(' ')
  const semanticIcon = MENU_ICON_RULES.find(([pattern]) => pattern.test(identity))?.[1]
  if (semanticIcon) return `/static/icons/ai-icon/${semanticIcon}.svg`
  const stableKey = String(menu.id || identity || 'menu')
  const hash = [...stableKey].reduce((total, char) => ((total * 31) + char.charCodeAt(0)) >>> 0, 0)
  return `/static/icons/ai-icon/${FALLBACK_MENU_ICONS[hash % FALLBACK_MENU_ICONS.length]}.svg`
}

function collectEntries(menus, offset = 0) {
  const entries = []
  function walk(list) {
    sorted(list).filter(visible).forEach((menu) => {
      const children = Array.isArray(menu.children) ? menu.children : []
      if (isMobileMenu(menu)) {
        const target = resolveMobileMenuTarget(menu)
        // 四个主导航已有固定 Tab，不在“常用应用”里重复占位。
        if (target && !target.tab) {
          entries.push({
            key: String(menu.id || target.url),
            label: menu.resourceName || menu.title || menu.name || '未命名应用',
            // 明确业务图标原样保留；通用占位图标按真实菜单语义稳定映射。
            icon: resolveMobileMenuIcon(menu),
            color: MENU_ACCENTS[(offset + entries.length) % MENU_ACCENTS.length],
            path: menu.path || '',
            component: menu.component || '',
            target,
            order: offset + entries.length,
          })
        }
      }
      if (children.length) walk(children)
    })
  }
  walk(menus)
  return entries
}

export function buildMobileMenuGroups(menus = []) {
  const groups = []
  const rootItems = []
  sorted(menus).filter(visible).forEach((menu) => {
    const children = Array.isArray(menu.children) ? menu.children : []
    if (children.length) {
      // 菜单节点可能还带按钮权限子节点，本身仍须作为可访问入口。
      rootItems.push(...collectEntries([{ ...menu, children: [] }], rootItems.length))
      const items = collectEntries(children, groups.length)
      if (items.length) {
        groups.push({
          key: `group-${menu.id || groups.length}`,
          label: menu.resourceName || menu.title || menu.name || '其他应用',
          items,
        })
      }
    }
    else rootItems.push(...collectEntries([menu], rootItems.length))
  })
  if (rootItems.length) groups.unshift({ key: 'root', label: '应用', items: rootItems })
  return groups
}

export function flattenMobileMenus(menus = []) {
  return buildMobileMenuGroups(menus).flatMap(group => group.items)
}
