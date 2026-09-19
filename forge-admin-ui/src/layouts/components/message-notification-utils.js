import { sanitizeHtml } from '@/utils/sanitize-html'

export function parseNotificationDate(value) {
  if (!value)
    return null
  if (Array.isArray(value)) {
    const [year, month, day, hour = 0, minute = 0, second = 0] = value
    return new Date(year, month - 1, day, hour, minute, second)
  }
  const date = new Date(typeof value === 'string' ? value.replace(' ', 'T') : value)
  return Number.isNaN(date.getTime()) ? null : date
}

export function mergeNotifications(messages, notices, isUnread) {
  const entries = messages.map(message => ({ ...message, source: 'message', key: `message:${message.id}`, readFlag: Number(message.readFlag) }))
  notices.forEach((notice) => {
    const document = new DOMParser().parseFromString(sanitizeHtml(notice.noticeContent), 'text/html')
    entries.push({
      source: 'notice', key: `notice:${notice.noticeId}`, id: notice.noticeId,
      title: notice.noticeTitle, content: document.body.textContent?.trim() || '',
      createTime: notice.publishTime, readFlag: isUnread(notice) ? 0 : 1,
      noticeType: notice.noticeType, isTop: notice.isTop,
    })
  })
  return entries.sort((a, b) => (parseNotificationDate(b.createTime)?.getTime() || 0) - (parseNotificationDate(a.createTime)?.getTime() || 0))
}

export function filterNotifications(entries, category, readState, rangeDays, now = Date.now()) {
  return entries.filter((entry) => {
    const kind = entry.source === 'notice' ? 'notice' : isFlowApprovalMessage(entry) ? 'approval' : 'other'
    if (category !== 'all' && category !== kind)
      return false
    if (readState !== 'all' && Number(entry.readFlag) !== Number(readState))
      return false
    return !rangeDays || (parseNotificationDate(entry.createTime)?.getTime() || 0) >= now - rangeDays * 86400000
  })
}

export function isFlowApprovalMessage(message) {
  return message?.bizType === 'FLOW_TODO'
}

export function isPendingFlowApprovalMessage(message) {
  return isFlowApprovalMessage(message) && Number(message?.readFlag) === 0
}

export function mergeMessageNavigationTarget(target, query = {}) {
  const extraQuery = Object.fromEntries(
    Object.entries(query).filter(([, value]) => value !== undefined && value !== null && value !== ''),
  )
  if (typeof target === 'string') {
    return Object.keys(extraQuery).length
      ? { path: target, query: extraQuery }
      : target
  }
  if (!target || typeof target !== 'object')
    return target
  return {
    ...target,
    query: {
      ...(target.query || {}),
      ...extraQuery,
    },
  }
}
