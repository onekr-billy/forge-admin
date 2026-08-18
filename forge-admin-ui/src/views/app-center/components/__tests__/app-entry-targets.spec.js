import { describe, expect, it } from 'vitest'
import {
  buildEntryOpenUrl,
  buildRuntimePageOptions,
  buildRuntimeTargetPreview,
  supportsRuntimeTarget,
} from '../app-entry-targets'

describe('application entry targets', () => {
  it('merges object pages and application custom pages without duplicate keys', () => {
    expect(buildRuntimePageOptions({
      objectPages: [
        { pageKey: 'list', pageName: '订单列表' },
        { pageKey: 'detail', pageName: '订单详情' },
      ],
      applicationPages: [
        { id: 'page_home', title: '工作台首页', type: 'page' },
        { id: 'group_sales', title: '销售页面', type: 'group' },
      ],
      currentTargetPageKey: 'page_home',
    })).toEqual([
      { label: '订单列表（默认）', value: 'list' },
      { label: '订单详情', value: 'detail' },
      { label: '工作台首页（自由编排）', value: 'page_home' },
    ])
  })

  it('keeps unknown historical targets visible and supports runtime and H5 entries', () => {
    expect(buildRuntimePageOptions({ currentTargetPageKey: 'legacy_page' })).toEqual([
      { label: '列表页（默认）', value: 'list' },
      { label: 'legacy_page', value: 'legacy_page' },
    ])
    expect(supportsRuntimeTarget('RUNTIME')).toBe(true)
    expect(supportsRuntimeTarget('H5')).toBe(true)
    expect(supportsRuntimeTarget('ROUTE')).toBe(false)
  })

  it('builds a routable preview path aligned with the backend runtime route', () => {
    expect(buildRuntimeTargetPreview({
      entryMode: 'H5',
      appId: '9001',
      configKey: 'presale_order',
      targetPageKey: 'page_home',
      targetFormKey: 'main',
    })).toMatchObject({
      mobile: true,
      value: '/#/pages/lowcode-runtime?configKey=presale_order',
    })
    expect(buildRuntimeTargetPreview({
      entryMode: 'RUNTIME',
      appId: '9001',
      configKey: 'presale_order',
      runtimeOpenMode: 'CREATE_FORM',
      targetPageKey: 'create',
      targetFormKey: 'main',
    })).toMatchObject({
      mobile: false,
      path: '/ai/crud-page/presale_order',
      value: '/ai/crud-page/presale_order?appId=9001&configKey=presale_order&runtimeOpenMode=CREATE_FORM&pageKey=create&formKey=main&mode=create',
    })
  })

  it('builds the correct open URL for mobile and web runtime entries', () => {
    // 移动入口 → H5 运行时页面，用 options.h5BaseUrl 拼完整 URL
    expect(buildEntryOpenUrl({
      appType: 'MOBILE',
      entryMode: 'RUNTIME',
      id: '9001',
      configKey: 'ps_presale_order',
      options: JSON.stringify({ runtimeOpenMode: 'LIST', h5BaseUrl: 'http://localhost:3001' }),
    })).toBe('http://localhost:3001/#/pages/lowcode-runtime?configKey=ps_presale_order')

    // 移动入口无 h5BaseUrl → 默认 http://localhost:3001
    expect(buildEntryOpenUrl({
      appType: 'MOBILE',
      entryMode: 'RUNTIME',
      id: '9001',
      configKey: 'ps_presale_order',
      options: JSON.stringify({ runtimeOpenMode: 'LIST' }),
    })).toBe('http://localhost:3001/#/pages/lowcode-runtime?configKey=ps_presale_order')

    // WEB 运行时 → AiCrudPage 完整参数
    expect(buildEntryOpenUrl({
      appType: 'BUSINESS',
      entryMode: 'RUNTIME',
      id: '9001',
      configKey: 'presale_order',
      runtimeOpenMode: 'CREATE_FORM',
      options: JSON.stringify({ targetPageKey: 'create', targetFormKey: 'main' }),
    })).toBe('/ai/crud-page/presale_order?appId=9001&configKey=presale_order&runtimeOpenMode=CREATE_FORM&pageKey=create&formKey=main&mode=create')

    // 无 configKey → 空串（入口未关联可运行配置）
    expect(buildEntryOpenUrl({ appType: 'MOBILE', entryMode: 'RUNTIME', id: '9001' })).toBe('')

    // 外部页面 → entryUrl
    expect(buildEntryOpenUrl({ entryMode: 'EXTERNAL', entryUrl: 'https://example.com' })).toBe('https://example.com')
  })
})
