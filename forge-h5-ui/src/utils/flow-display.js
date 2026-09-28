const statusLabels = {
  pending: '待处理',
  waiting: '待处理',
  not_started: '未开始',
  running: '处理中',
  active: '处理中',
  in_progress: '处理中',
  completed: '已完成',
  complete: '已完成',
  finished: '已完成',
  skipped: '已跳过',
  rejected: '已驳回',
  terminated: '已终结',
  cancelled: '已取消',
  canceled: '已取消',
  failed: '异常',
}

const actionLabels = {
  approve: '已通过', approved: '已通过', pass: '已通过', passed: '已通过',
  start: '提交申请', reject: '已驳回', rejected: '已驳回',
  return: '已退回', returned: '已退回', delegate: '已转办', delegated: '已转办',
  reassign: '已转派', rollback: '已回退', terminate: '已终结', terminated: '已终结',
  withdraw: '已撤回', claim: '已签收', pending: '待处理',
}

function code(value) {
  return String(value ?? '').trim().toLowerCase().replace(/[\s-]+/g, '_')
}

function readableUnknown(value, fallback) {
  const text = String(value ?? '').trim()
  return text && !/^[a-z][a-z\d_\s-]*$/i.test(text) ? text : fallback
}

export function formatFlowStatus(value) {
  return statusLabels[code(value)] || readableUnknown(value, '状态待确认')
}

export function formatFlowAction(value) {
  return actionLabels[code(value)] || readableUnknown(value, '其他操作')
}

export function flowStatusTone(value) {
  const normalized = code(value)
  if (['completed', 'complete', 'finished'].includes(normalized)) return 'completed'
  if (['running', 'active', 'in_progress'].includes(normalized)) return 'running'
  if (['failed', 'rejected', 'terminated', 'cancelled', 'canceled'].includes(normalized)) return 'exception'
  return 'pending'
}

function pad(value) { return String(value).padStart(2, '0') }

function formatDate(date) {
  if (Number.isNaN(date.getTime())) return '-'
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

export function formatFlowDateTime(value) {
  if (value === null || value === undefined || value === '') return '-'
  if (Array.isArray(value)) {
    const [year, month = 1, day = 1, hour = 0, minute = 0, second = 0] = value
    return `${year}-${pad(month)}-${pad(day)} ${pad(hour)}:${pad(minute)}:${pad(second)}`
  }
  if (value instanceof Date || typeof value === 'number') return formatDate(new Date(value))
  const text = String(value).trim()
  if (!text) return '-'
  const local = text.match(/^(\d{4})-(\d{1,2})-(\d{1,2})(?:[T\s](\d{1,2}):(\d{1,2})(?::(\d{1,2}))?)?(?:\.\d+)?(Z|[+-]\d{2}:?\d{2})?$/i)
  if (local) {
    if (local[7]) return formatDate(new Date(text))
    if (!local[4]) return `${local[1]}-${pad(local[2])}-${pad(local[3])}`
    return `${local[1]}-${pad(local[2])}-${pad(local[3])} ${pad(local[4])}:${pad(local[5])}:${pad(local[6] || 0)}`
  }
  if (/^\d{13}$/.test(text)) return formatDate(new Date(Number(text)))
  return text.replace(/T/g, ' ')
}
