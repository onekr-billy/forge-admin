/**
 * Forge UI Document 协议（轻量运行时契约）
 *
 * 分层：
 * - 设计态（重）：formDesignerSchema —— 只给设计器编辑
 * - 运行态（轻）：uiDocument —— PC / H5 / 审批 / 业务页共用渲染契约
 * - 数据与权限：fields / recordData / fieldPermissions 作 overlay，不塞进布局树
 *
 * 扩展原则：新增能力走可选字段（props / extensions），不改必填形状，保证适配与演进。
 */

export const UI_DOCUMENT_PROTOCOL_VERSION = '1'

export const UI_DOCUMENT_UI_TYPES = Object.freeze({
  BUSINESS_OBJECT: 'business-object',
  LOWCODE_FORM: 'lowcode-form',
  TASK_FORM: 'task-form',
})
