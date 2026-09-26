import { describe, expect, it } from 'vitest'
import {
  applyPageMountFieldsToNode,
  createPageMountDraft,
  isPageSystemMenuMounted,
  resolvePageMountTarget,
  showAdminMenuMountConfig,
  showMobileMenuMountConfig,
} from '../page-system-menu-mount'

describe('page-system-menu-mount', () => {
  it('reads mount fields from top-level or settings mirror', () => {
    const node = {
      systemMenuVisible: true,
      settings: {
        mountTarget: 'BOTH',
        menuParentId: 12,
        mobileMenuParentId: '34',
        menuName: '客户',
        menuSort: 8,
      },
    }
    expect(isPageSystemMenuMounted(node)).toBe(true)
    expect(resolvePageMountTarget(node)).toBe('BOTH')
    expect(createPageMountDraft(node)).toEqual({
      systemMenuVisible: true,
      mountTarget: 'BOTH',
      menuName: '客户',
      menuParentId: '12',
      mobileMenuParentId: '34',
      menuSort: 8,
    })
  })

  it('mirrors mount fields into settings when applying', () => {
    const next = applyPageMountFieldsToNode(
      { id: 'page_1', title: '订单', settings: { printWatermark: true } },
      {
        systemMenuVisible: true,
        mountTarget: 'ADMIN',
        menuName: '订单管理',
        menuParentId: '99',
        mobileMenuParentId: null,
        menuSort: 3,
      },
    )
    expect(next).toMatchObject({
      systemMenuVisible: true,
      mountTarget: 'ADMIN',
      menuName: '订单管理',
      menuParentId: '99',
      menuSort: 3,
      settings: {
        printWatermark: true,
        systemMenuVisible: true,
        mountTarget: 'ADMIN',
        menuName: '订单管理',
        menuParentId: '99',
        mobileMenuParentId: null,
        menuSort: 3,
      },
    })
  })

  it('resolves admin/mobile config visibility by mountTarget', () => {
    expect(showAdminMenuMountConfig('ADMIN')).toBe(true)
    expect(showMobileMenuMountConfig('ADMIN')).toBe(false)
    expect(showAdminMenuMountConfig('MOBILE')).toBe(false)
    expect(showMobileMenuMountConfig('MOBILE')).toBe(true)
    expect(showAdminMenuMountConfig('BOTH')).toBe(true)
    expect(showMobileMenuMountConfig('BOTH')).toBe(true)
  })
})
