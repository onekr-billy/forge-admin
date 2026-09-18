import { useUserStore } from '@/store'

/**
 * 构建表单运行时上下文（登录用户信息）。
 *
 * 产出结构与 runtime-context-options.js 声明的可选路径严格对齐：
 * tenantId / activeOrgId / staffId 等都在 currentUser 内部，不存在顶层形态。
 * 设计器「初始化默认值 / 字段事件」里配置的 currentUser.* 路径均从此对象取值。
 */
export function buildFormRuntimeContext() {
  const userStore = useUserStore()
  return {
    currentUser: {
      userId: userStore.userId,
      username: userStore.username,
      realName: userStore.realName,
      tenantId: userStore.tenantId,
      activeOrgId: userStore.activeOrgId,
      activeOrgName: userStore.activeOrgName,
      staffId: userStore.staffId,
      staffName: userStore.staffName,
    },
  }
}
