import { describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import { openPlatformPages, openPlatformRoutes } from '../../open-platform-routes'
import { canAccessRoute } from '../permission-guard'

describe('open platform menu routing', () => {
  const router = createRouter({ history: createMemoryHistory('/forge/'), routes: openPlatformRoutes })
  it.each(openPlatformPages)('$key menu and file URLs resolve the same real page', (page) => {
    for (const path of [page.path, page.legacyPath]) {
      const route = router.resolve({ path, query: { clientId: '9223372036854775801' } })
      expect(route.matched).toHaveLength(1)
      expect(route.matched[0].components.default).toBe(page.component)
      expect(route.query.clientId).toBe('9223372036854775801')
      expect(route.meta.title).toBe(page.label)
    }
  })
  it.each(openPlatformPages)('$key aliases preserve the original menu grant', (page) => {
    for (const menu of [page.path, page.legacyPath]) {
      for (const target of [page.path, page.legacyPath]) {
        expect(canAccessRoute({ path: target }, { accessRoutes: [{ path: menu }] })).toBe(true)
        expect(canAccessRoute({ path: target }, { accessRoutes: [] })).toBe(false)
      }
    }
  })
  it('catalog permission does not expose clients, grants or logs', () => {
    const menu = { accessRoutes: [{ path: '/open-platform/capability-catalog' }] }
    for (const page of openPlatformPages.filter(page => page.key !== 'catalog'))
      expect(canAccessRoute({ path: page.legacyPath }, menu)).toBe(false)
    expect(canAccessRoute({ path: '/open-platform/other' }, menu)).toBe(false)
  })
  it('permits hidden workbench tools only with their original platform read permission', () => {
    const menu = { accessRoutes: [] }
    const logUser = { permissions: ['ai:capability:invocation:query'] }
    for (const path of ['/open-platform/capability-invocation', '/ai/capability/invocation'])
      expect(canAccessRoute({ path }, menu, logUser)).toBe(true)
    expect(canAccessRoute({ path: '/open-platform/capability-grant' }, menu, logUser)).toBe(false)
    expect(canAccessRoute({ path: '/open-platform/capability-client' }, menu, logUser)).toBe(false)
    expect(canAccessRoute({ path: '/open-platform/capability-invocation' }, menu, { permissions: ['ai:businessApplication:edit'] })).toBe(false)
    expect(canAccessRoute({ path: '/open-platform/capability-invocation' }, menu, { isAdmin: true })).toBe(true)
    expect(canAccessRoute({ path: '/system/user' }, menu, { isAdmin: true })).toBe(false)
    expect(canAccessRoute({ path: '/open-platform/capability-grant' }, menu, { permissions: ['ai:capability:grant:query'] })).toBe(true)
  })
})
