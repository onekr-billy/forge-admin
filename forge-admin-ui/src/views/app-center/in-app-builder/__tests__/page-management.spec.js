import { describe, expect, it } from 'vitest'
import {
  PAGE_MANAGEMENT_SYSTEM_PAGES,
  resolvePageManagementSelection,
} from '../page-management'

describe('page management selection', () => {
  it('keeps system pages selectable and falls back to the first user page', () => {
    expect(PAGE_MANAGEMENT_SYSTEM_PAGES.map(item => item.title)).toEqual([
      '个人工作台',
      '我的待办',
      '我已办的',
      '我发送的',
      '抄送我的',
    ])
    expect(resolvePageManagementSelection(
      [{ id: 'orders', type: 'page' }],
      'system:todo',
      'orders',
    )).toBe('system:todo')
    expect(resolvePageManagementSelection(
      [{ id: 'orders', type: 'page' }],
      '',
      'orders',
    )).toBe('orders')
    expect(resolvePageManagementSelection([], 'missing', '')).toBe('system:workbench')
  })
})
