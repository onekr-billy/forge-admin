/**
 * 表单运行时上下文常用路径选项（共享常量）
 *
 * 与 views/ai/crud-page.vue 的 buildFormRuntimeContext 实际产出严格对齐：
 * 顶层只有 currentUser 对象与 row/record/formData 等业务对象，
 * tenantId / activeOrgId 等都在 currentUser 内部，没有顶层路径形态。
 *
 * FormInitPanel（初始化默认值）与 FieldEventRulesEditor（字段自动查询）
 * 的上下文选项统一引用本常量，避免两处漂移（历史 bug：userName / 顶层 tenantId 取不到值）。
 */

/** 自定义路径（高级）选项标识值 */
export const CUSTOM_PATH_OPTION = '__CUSTOM_PATH__'

/**
 * 常用上下文路径选项（value 为运行时真实可取值路径）
 * @type {Array<{label: string, value: string}>}
 */
export const RUNTIME_CONTEXT_PATH_OPTIONS = [
  { label: '当前登录用户 ID', value: 'currentUser.userId' },
  { label: '当前登录用户账号', value: 'currentUser.username' },
  { label: '当前登录用户姓名', value: 'currentUser.realName' },
  { label: '当前租户 ID', value: 'currentUser.tenantId' },
  { label: '当前组织 ID', value: 'currentUser.activeOrgId' },
  { label: '当前组织名称', value: 'currentUser.activeOrgName' },
  { label: '当前员工 ID', value: 'currentUser.staffId' },
  { label: '当前员工姓名', value: 'currentUser.staffName' },
]

/**
 * 字段自动查询（fieldEvents）特有的上下文选项：
 * SCAN_COMPLETE 触发时运行时会向上下文注入顶层 scan 对象（见 field-event-runtime.js）。
 * @type {Array<{label: string, value: string}>}
 */
export const FIELD_EVENT_CONTEXT_PATH_OPTIONS = [
  ...RUNTIME_CONTEXT_PATH_OPTIONS,
  { label: '扫码内容', value: 'scan.value' },
]

/** 统一在选项末尾追加「自定义路径（高级）」项 */
export function withCustomPathOption(options) {
  return [...options, { label: '自定义路径（高级）', value: CUSTOM_PATH_OPTION }]
}
