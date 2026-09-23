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

/** 与后端默认策略对齐：未显式关闭时视为需要填写原因 */
export function isAuditReasonRequired(meta) {
  if (!meta || meta.enabled !== true)
    return false
  return meta.reasonRequired !== false && meta.reasonRequired !== 0
}

export function isAuditReasonRequiredError(error) {
  const payload = error?.response?.data || error?.data || error || {}
  const code = payload?.data?.errorCode || payload?.errorCode || payload?.code
  if (code === 'AUDIT_REASON_REQUIRED')
    return true
  const message = String(payload?.message || error?.message || '')
  return /请填写(删除|修改)原因/.test(message)
}

export function promptAuditReason(title = '请填写修改原因', emptyWarning = title) {
  const reason = ref('')
  return new Promise((resolve) => {
    const dialog = window.$dialog
    if (!dialog) {
      resolve(false)
      return
    }
    const open = typeof dialog.warning === 'function'
      ? dialog.warning.bind(dialog)
      : (typeof dialog.create === 'function' ? dialog.create.bind(dialog) : null)
    if (!open) {
      resolve(false)
      return
    }
    open({
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
          window.$message?.warning(emptyWarning || '请填写修改原因')
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
  if (isEdit && isAuditReasonRequired(meta)) {
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

/**
 * 解析删除侧审计计划。列表若缺少 _dataAudit，可通过 fetchRecordMeta 用详情补齐策略与 revision。
 */
export async function resolveDataAuditRemovePlan({ configKey, ids, rows, fetchRecordMeta } = {}) {
  const safeIds = (ids || []).map(id => String(id))
  let workingRows = Array.isArray(rows) ? rows.map(row => (row && typeof row === 'object' ? { ...row } : row)) : []
  let metas = workingRows.map(row => readDataAuditMeta(row)).filter(item => item?.enabled === true)

  if (!metas.length && configKey && typeof fetchRecordMeta === 'function' && safeIds.length) {
    const hydrated = []
    for (let i = 0; i < safeIds.length; i += 1) {
      const existing = readDataAuditMeta(workingRows[i])
      if (existing?.enabled) {
        hydrated.push(existing)
        continue
      }
      try {
        const meta = await fetchRecordMeta(safeIds[i], workingRows[i])
        if (meta?.enabled) {
          workingRows[i] = {
            ...(workingRows[i] && typeof workingRows[i] === 'object' ? workingRows[i] : { id: safeIds[i] }),
            [DATA_AUDIT_KEY]: {
              ...meta,
              recordId: safeIds[i],
              revision: meta.revision ?? 0,
            },
          }
          hydrated.push(meta)
        }
      }
      catch {
        // 详情补齐失败时继续尝试普通删除
      }
    }
    metas = workingRows.map(row => readDataAuditMeta(row)).filter(item => item?.enabled === true)
  }

  if (!metas.length) {
    return {
      mode: 'plain',
      configKey: configKey || '',
      ids: safeIds,
      rows: workingRows,
      reasonRequired: false,
      expectedRevisions: safeIds.map(() => 0),
    }
  }

  return {
    mode: 'audit',
    configKey: configKey || '',
    ids: safeIds,
    rows: workingRows,
    reasonRequired: metas.some(item => isAuditReasonRequired(item)),
    expectedRevisions: workingRows.map(row => readDataAuditMeta(row)?.revision ?? 0),
  }
}

export async function applyDataAuditRemove({
  configKey,
  ids,
  rows,
  requestRemove,
  fetchRecordMeta,
  reason: presetReason,
  skipPrompt = false,
} = {}) {
  const plan = await resolveDataAuditRemovePlan({ configKey, ids, rows, fetchRecordMeta })
  if (plan.mode !== 'audit')
    return null

  let reason = typeof presetReason === 'string' ? presetReason : ''
  if (!skipPrompt && plan.reasonRequired && !reason) {
    reason = await promptAuditReason('请填写删除原因', '请填写删除原因')
    if (reason === false)
      return false
  }

  return requestRemove(plan.configKey, {
    ids: plan.ids,
    expectedRevisions: plan.expectedRevisions,
    reason,
  })
}
