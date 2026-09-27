/** Menu URLs differ from file-generated URLs. Keep both on one page contract. */
export const openPlatformPages = [
  { key: 'catalog', label: '能力目录', path: '/open-platform/capability-catalog', legacyPath: '/ai/capability/catalog', permission: 'ai:capability:query', component: () => import('@/views/ai/capability/catalog.vue') },
  { key: 'client', label: '接入系统', path: '/open-platform/capability-client', legacyPath: '/ai/capability/client', permission: 'ai:capability:client:query', component: () => import('@/views/ai/capability/client.vue') },
  { key: 'invocation', label: '调用记录', path: '/open-platform/capability-invocation', legacyPath: '/ai/capability/invocation', permission: 'ai:capability:invocation:query', component: () => import('@/views/ai/capability/invocation.vue') },
  { key: 'grant', label: '能力授权', path: '/open-platform/capability-grant', legacyPath: '/ai/capability/grant', permission: 'ai:capability:grant:query', component: () => import('@/views/ai/capability/grant.vue') },
]

export const openPlatformRoutes = openPlatformPages.map(page => ({
  name: `OpenPlatform-${page.key}`,
  path: page.path,
  alias: page.legacyPath,
  component: page.component,
  meta: { title: page.label },
}))

// Only normalize these exact aliases. No prefix allowance or permission bypass.
export function normalizeOpenPlatformPath(path) {
  return openPlatformPages.find(page => page.legacyPath === path)?.path || path
}

/** These workbench tools are intentionally hidden by V1.0.82, so navigation menus omit them. */
export function canAccessHiddenOpenPlatformTool(path, user) {
  const page = openPlatformPages.find(page => page.path === normalizeOpenPlatformPath(path)
    && ['invocation', 'grant'].includes(page.key))
  return Boolean(page && (user?.isAdmin || user?.permissions?.includes('*:*:*') || user?.permissions?.includes(page.permission)))
}
