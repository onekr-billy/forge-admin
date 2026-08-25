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
