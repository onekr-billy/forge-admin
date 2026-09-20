import { NInput } from 'naive-ui'
import { h, ref } from 'vue'

export const DATA_AUDIT_KEY = '_dataAudit'

export function readDataAuditMeta(source) {
  if (!source || typeof source !== 'object')
    return null
  const meta = source[DATA_AUDIT_KEY] || source.main?.[DATA_AUDIT_KEY]
  return meta && typeof meta === 'object' ? meta : null
}

export function stripDataAuditMeta(payload) {
  if (!payload || typeof payload !== 'object')
    return payload
  const next = { ...payload }
  delete next[DATA_AUDIT_KEY]
  delete next.auditContext
  if (next.main && typeof next.main === 'object') {
    const main = { ...next.main }
    delete main[DATA_AUDIT_KEY]
    delete main.auditContext
    next.main = main
  }
  return next
}

export function isDataAuditHistoryAvailable(meta) {
  return meta?.enabled === true || meta?.historyAvailable === true || Number(meta?.revision || 0) > 0
}

export function shouldShowDataAuditHistory(meta, legacyConfigured = false) {
  if (meta?.configured === true)
    return meta.showInDetail === true && isDataAuditHistoryAvailable(meta)
  return legacyConfigured === true
}

export function promptAuditReason(title = '请填写修改原因') {
  const reason = ref('')
  return new Promise((resolve) => {
    if (!window.$dialog) {
      resolve(false)
      return
    }
    window.$dialog.create({
      title,
      content: () => h(NInput, {
        'value': reason.value,
        'type': 'textarea',
        'maxlength': 500,
        'showCount': true,
        'placeholder': '请说明本次修改或删除的原因，不要填写无关个人信息',
        'onUpdate:value': (value) => {
          reason.value = value
        },
      }),
      positiveText: '确定',
      negativeText: '取消',
      onPositiveClick: () => {
        const text = String(reason.value || '').trim()
        if (!text) {
          window.$message?.warning('请填写修改原因')
          return false
        }
        resolve(text)
        return true
      },
      onNegativeClick: () => {
        resolve(false)
      },
      onClose: () => {
        resolve(false)
      },
    })
  })
}

export async function applyDataAuditSubmit(payload, { formData, isEdit } = {}) {
  const meta = readDataAuditMeta(formData) || readDataAuditMeta(payload)
  const stripped = stripDataAuditMeta(payload)
  if (!meta?.enabled)
    return stripped
  let reason = ''
  if (isEdit && meta.reasonRequired) {
    reason = await promptAuditReason('请填写修改原因')
    if (reason === false)
      return false
  }
  stripped[DATA_AUDIT_KEY] = {
    reason,
    expectedRevision: meta.revision ?? 0,
  }
  return stripped
}

export async function applyDataAuditRemove({ configKey, ids, rows, requestRemove }) {
  const metas = (rows || []).map(row => readDataAuditMeta(row)).filter(item => item?.enabled)
  if (!metas.length)
    return null
  let reason = ''
  if (metas.some(item => item.reasonRequired)) {
    reason = await promptAuditReason('请填写删除原因')
    if (reason === false)
      return false
  }
  return requestRemove(configKey, {
    ids: ids.map(id => String(id)),
    expectedRevisions: (rows || []).map(row => readDataAuditMeta(row)?.revision ?? 0),
    reason,
  })
}
