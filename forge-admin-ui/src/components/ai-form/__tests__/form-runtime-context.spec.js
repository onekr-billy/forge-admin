import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'
import { useUserStore } from '@/store'
import { buildFormRuntimeContext } from '../form-runtime-context'

describe('form-runtime-context', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('builds currentUser context aligned with runtime-context-options paths', () => {
    const userStore = useUserStore()
    userStore.userInfo = {
      userId: 45,
      username: 'zhangsan',
      realName: '张三',
      tenantId: 1,
      activeOrgId: 9,
      activeOrgName: '总部',
    }
    userStore.staffInfo = { staffId: 'S001', staffName: '张三' }

    expect(buildFormRuntimeContext()).toEqual({
      currentUser: {
        userId: 45,
        username: 'zhangsan',
        realName: '张三',
        tenantId: 1,
        activeOrgId: 9,
        activeOrgName: '总部',
        staffId: 'S001',
        staffName: '张三',
      },
    })
  })

  it('falls back to userInfo.id when userId is absent', () => {
    const userStore = useUserStore()
    userStore.userInfo = { id: 46, username: 'lisi' }

    const context = buildFormRuntimeContext()
    expect(context.currentUser.userId).toBe(46)
    expect(context.currentUser.realName).toBeUndefined()
    expect(context.currentUser.tenantId).toBeUndefined()
  })

  it('keeps the context shape stable when the user has not logged in yet', () => {
    const context = buildFormRuntimeContext()

    expect(Object.keys(context)).toEqual(['currentUser'])
    expect(context.currentUser.userId).toBeUndefined()
    expect(context.currentUser.username).toBeUndefined()
  })
})
