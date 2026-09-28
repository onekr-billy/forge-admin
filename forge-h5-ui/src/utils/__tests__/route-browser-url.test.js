import assert from 'node:assert/strict'
import test from 'node:test'

/**
 * 与 src/utils/route.js#resolveBrowserRouteUrl 保持同步的契约测试。
 * 生产 H5 使用 /forge-h5 + hash 路由，禁止把 /pages/... 直接写成站点根路径。
 */
function resolveBrowserRouteUrl(url, env = {}) {
  const value = String(url || '').trim()
  if (!value) {
    return ''
  }
  if (/^(https?:|data:|blob:)/i.test(value)) {
    return value
  }

  const routePath = value.startsWith('/') ? value : `/${value}`
  const rawBase = env.BASE_URL || env.VITE_PUBLIC_PATH || '/'
  const base = String(rawBase || '/').replace(/\/+$/, '')
  const basePrefix = !base || base === '/' || base === '.' ? '' : base
  return `${basePrefix}/#${routePath}`
}

test('resolveBrowserRouteUrl prefixes forge-h5 hash route', () => {
  assert.equal(
    resolveBrowserRouteUrl('/pages/login/index?redirect=%2Fpages%2Ftodo', { BASE_URL: '/forge-h5/' }),
    '/forge-h5/#/pages/login/index?redirect=%2Fpages%2Ftodo',
  )
})

test('resolveBrowserRouteUrl keeps absolute http urls', () => {
  assert.equal(
    resolveBrowserRouteUrl('https://example.com/forge-h5/#/pages/todo', { BASE_URL: '/forge-h5/' }),
    'https://example.com/forge-h5/#/pages/todo',
  )
})

test('resolveBrowserRouteUrl uses root hash on local base', () => {
  assert.equal(
    resolveBrowserRouteUrl('/pages/todo', { BASE_URL: '/' }),
    '/#/pages/todo',
  )
})
