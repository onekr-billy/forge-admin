/**
 * 按流程级驳回策略，在 JSON→BPMN 前补齐缺失的驳回分支。
 *
 * 线性审批（审批人直连下一节点）默认没有 ${approvalResult == 'reject'} 出边，
 * 运行时点「驳回」会把令牌推给下一个审批人。本模块只在转换时注入，不改设计器内存图；
 * 保存后的 BPMN 会带回路，重新打开时设计器能看到补出来的节点。
 *
 * 策略：
 * - TO_INITIATOR_MODIFY：驳回到共享「发起人修改」节点，重提回首个审批人
 *   （设计器新建流程默认；存量未声明属性时按 MANUAL，避免静默改写 BPMN）
 * - TO_END：驳回到结束事件
 * - MANUAL：不自动补普通驳回；节点显式开启“退回发起人修改”时仍补专用回路
 *
 * “退回发起人修改”不复用普通驳回条件：它由 rejectToStart=true 独立命中，
 * 普通自动驳回分支则显式排除该标记，避免 TO_END 策略把专用动作直接结束。
 */

import { NODE_TYPE } from '../constants/node-types.js'

export const REJECT_STRATEGY = Object.freeze({
  TO_INITIATOR_MODIFY: 'TO_INITIATOR_MODIFY',
  TO_END: 'TO_END',
  MANUAL: 'MANUAL',
})

const REJECT_CONDITION = '$' + '{approvalResult == \'reject\' && rejectToStart != true}'
const REJECT_TO_START_CONDITION = '$' + '{approvalResult == \'reject\' && rejectToStart == true}'
const INITIATOR_MODIFY_ID = 'Forge_InitiatorModify'
const REJECT_END_ID = 'Forge_RejectEnd'

/**
 * @param {object} flowJson
 * @param {string} rejectStrategy
 * @returns {object} 新的 flowJson（nodes/edges 已复制），原对象不修改
 */
export function ensureRejectRoutes(flowJson, rejectStrategy = REJECT_STRATEGY.MANUAL) {
  const strategy = normalizeRejectStrategy(rejectStrategy)
  if (!flowJson || !Array.isArray(flowJson.nodes))
    return flowJson

  const requiresRejectToStartRoute = flowJson.nodes.some(node =>
    node.nodeType === NODE_TYPE.APPROVER
    && !node.config?.initiatorModify
    && !node.config?.systemRejectRoute
    && node.config?.allowRejectToStart === true)
  if (strategy === REJECT_STRATEGY.MANUAL && !requiresRejectToStartRoute)
    return flowJson

  const nodes = flowJson.nodes.map(node => ({ ...node, config: { ...(node.config || {}) } }))
  const edges = (Array.isArray(flowJson.edges) ? flowJson.edges : []).map(edge => ({ ...edge }))
  const graph = { nodes, edges }

  const approvers = nodes.filter(node =>
    node.nodeType === NODE_TYPE.APPROVER
    && !node.config?.initiatorModify
    && !node.config?.systemRejectRoute
    && (node.config?.allowReject !== false || node.config?.allowRejectToStart === true))

  for (const approver of approvers) {
    const allowRegularReject = approver.config?.allowReject !== false
    if (allowRegularReject && strategy !== REJECT_STRATEGY.MANUAL
      && !hasRegularRejectOutbound(graph, approver.id)) {
      if (strategy === REJECT_STRATEGY.TO_END)
        injectRejectToEnd(graph, approver)
      else
        injectRejectToInitiatorModify(graph, approver)
    }
    if (approver.config?.allowRejectToStart === true)
      injectExplicitRejectToInitiatorModify(graph, approver)
  }

  if (strategy === REJECT_STRATEGY.TO_INITIATOR_MODIFY || requiresRejectToStartRoute)
    ensureInitiatorModifyResubmit(graph)

  return { ...flowJson, nodes: graph.nodes, edges: graph.edges }
}

export function normalizeRejectStrategy(value) {
  const text = String(value || '').trim().toUpperCase()
  if (!text)
    return REJECT_STRATEGY.MANUAL
  if (text === REJECT_STRATEGY.TO_END || text === 'END' || text === 'TERMINATE')
    return REJECT_STRATEGY.TO_END
  if (text === REJECT_STRATEGY.MANUAL || text === 'NONE' || text === 'OFF')
    return REJECT_STRATEGY.MANUAL
  if (text === REJECT_STRATEGY.TO_INITIATOR_MODIFY || text === 'TO_START' || text === 'MODIFY')
    return REJECT_STRATEGY.TO_INITIATOR_MODIFY
  return REJECT_STRATEGY.MANUAL
}

function hasRegularRejectOutbound(graph, nodeId) {
  const outs = graph.edges.filter(edge => edge.source === nodeId)
  for (const edge of outs) {
    if (isRegularRejectEdge(edge))
      return true
    const target = graph.nodes.find(node => node.id === edge.target)
    if (target?.nodeType !== NODE_TYPE.CONDITION)
      continue
    if (graph.edges.some(next => next.source === target.id && isRegularRejectEdge(next)))
      return true
  }
  return false
}

function isRegularRejectEdge(edge) {
  if (!edge)
    return false
  const condition = String(edge.condition || '')
  if (/rejectToStart\s*==\s*true/.test(condition))
    return false
  if (edge.approvalResult === 'reject')
    return true
  return /approvalResult\s*==\s*['"]reject['"]/.test(condition)
    || /approved\s*==\s*['"]?false['"]?/.test(condition)
}

function injectRejectToEnd(graph, approver) {
  const endId = ensureRejectEnd(graph)
  splitApproverWithRejectGateway(graph, approver, endId, `Forge_RejectGW_${approver.id}`)
}

function injectRejectToInitiatorModify(graph, approver) {
  const modifyId = ensureInitiatorModify(graph)
  splitApproverWithRejectGateway(graph, approver, modifyId, `Forge_RejectGW_${approver.id}`)
}

function injectExplicitRejectToInitiatorModify(graph, approver) {
  if (hasRejectToStartOutbound(graph, approver.id))
    return
  const modifyId = ensureInitiatorModify(graph)
  const gateway = ensureRejectGateway(graph, approver, `Forge_RejectGW_${approver.id}`)
  const alreadyExists = graph.edges.some(edge =>
    edge.source === gateway.id
    && /rejectToStart\s*==\s*true/.test(String(edge.condition || '')))
  if (!alreadyExists) {
    graph.edges.push({
      id: `Forge_RejectToStartEdge_${approver.id}`,
      source: gateway.id,
      target: modifyId,
      condition: REJECT_TO_START_CONDITION,
      approvalResult: 'reject',
      systemRejectRoute: true,
    })
  }

  // 旧版自动生成的普通驳回边只有 approvalResult 条件。补上互斥判断，
  // 避免点击“退回发起人修改”时普通分支和专用分支同时命中。
  for (const edge of graph.edges.filter(item => item.source === gateway.id)) {
    if (edge.systemRejectRoute === true && isRegularRejectEdge(edge))
      edge.condition = REJECT_CONDITION
  }
}

function hasRejectToStartOutbound(graph, nodeId) {
  const visited = new Set()
  const queue = [nodeId]
  while (queue.length) {
    const current = queue.shift()
    if (visited.has(current))
      continue
    visited.add(current)
    for (const edge of graph.edges.filter(item => item.source === current)) {
      if (/rejectToStart\s*==\s*true/.test(String(edge.condition || '')))
        return true
      const target = graph.nodes.find(node => node.id === edge.target)
      if (target?.nodeType === NODE_TYPE.CONDITION)
        queue.push(target.id)
    }
  }
  return false
}

/**
 * 把「审批人 → 下一节点」拆成「审批人 → 网关 → 原出边 / 驳回目标」。
 */
function splitApproverWithRejectGateway(graph, approver, rejectTargetId, gatewayId) {
  const gateway = ensureRejectGateway(graph, approver, gatewayId)
  if (graph.edges.some(edge => edge.source === gateway.id && isRegularRejectEdge(edge)))
    return
  graph.edges.push({
    id: `Forge_RejectEdge_${approver.id}`,
    source: gateway.id,
    target: rejectTargetId,
    condition: REJECT_CONDITION,
    approvalResult: 'reject',
    systemRejectRoute: true,
  })
}

/**
 * 在审批节点后插入一个系统排他网关，并把原出边整体迁到网关之后。
 * 这样既能给手工流程图补专用退回分支，也不会覆盖原有条件和目标。
 */
function ensureRejectGateway(graph, approver, gatewayId) {
  const existing = graph.nodes.find(node => node.id === gatewayId)
  if (existing)
    return existing

  const outgoing = graph.edges.filter(edge => edge.source === approver.id)
  const gateway = {
    id: gatewayId,
    name: '驳回分支',
    nodeType: NODE_TYPE.CONDITION,
    config: { systemRejectRoute: true },
  }
  graph.nodes.push(gateway)

  for (const edge of outgoing)
    edge.source = gatewayId

  graph.edges.push({
    id: `Forge_ToGW_${approver.id}`,
    source: approver.id,
    target: gatewayId,
    systemRejectRoute: true,
  })

  const defaultEdge = outgoing.find(edge => edge.isDefault === true)
    || (outgoing.length === 1 && !outgoing[0].condition ? outgoing[0] : null)
  if (defaultEdge) {
    defaultEdge.isDefault = true
    gateway.config.defaultFlowId = defaultEdge.id
  }
  if (outgoing.length === 0) {
    const endId = ensureRejectEnd(graph)
    const defaultEdgeId = `Forge_RejectDefault_${approver.id}`
    graph.edges.push({
      id: defaultEdgeId,
      source: gatewayId,
      target: endId,
      isDefault: true,
      systemRejectRoute: true,
    })
    gateway.config.defaultFlowId = defaultEdgeId
  }
  return gateway
}

function ensureInitiatorModify(graph) {
  const existing = graph.nodes.find(node =>
    node.id === INITIATOR_MODIFY_ID || node.config?.initiatorModify)
  if (existing)
    return existing.id

  graph.nodes.push({
    id: INITIATOR_MODIFY_ID,
    name: '发起人修改',
    nodeType: NODE_TYPE.APPROVER,
    config: {
      systemRejectRoute: true,
      initiatorModify: true,
      taskType: 'assignee',
      assignee: '$' + '{initiator}',
      allowApprove: true,
      allowReject: true,
      allowRejectToStart: false,
      requireComment: false,
    },
  })
  return INITIATOR_MODIFY_ID
}

function ensureRejectEnd(graph) {
  const existing = graph.nodes.find(node =>
    node.id === REJECT_END_ID
    || (node.nodeType === NODE_TYPE.END && node.config?.endType === 'reject'))
  if (existing)
    return existing.id

  graph.nodes.push({
    id: REJECT_END_ID,
    name: '驳回结束',
    nodeType: NODE_TYPE.END,
    config: { systemRejectRoute: true, endType: 'reject' },
  })
  return REJECT_END_ID
}

/**
 * 发起人修改后默认回到首个业务审批人；若已有出边则不改。
 */
function ensureInitiatorModifyResubmit(graph) {
  const modify = graph.nodes.find(node =>
    node.id === INITIATOR_MODIFY_ID || node.config?.initiatorModify)
  if (!modify)
    return
  if (graph.edges.some(edge => edge.source === modify.id))
    return

  const firstApprover = findFirstBusinessApprover(graph)
  if (!firstApprover || firstApprover.id === modify.id)
    return

  graph.edges.push({
    id: `Forge_Resubmit_${modify.id}`,
    source: modify.id,
    target: firstApprover.id,
    isDefault: true,
    systemRejectRoute: true,
  })
}

function findFirstBusinessApprover(graph) {
  const start = graph.nodes.find(node => node.nodeType === NODE_TYPE.START)
  if (!start) {
    return graph.nodes.find(node =>
      node.nodeType === NODE_TYPE.APPROVER && !node.config?.initiatorModify)
  }

  const visited = new Set()
  const queue = [start.id]
  while (queue.length) {
    const current = queue.shift()
    if (visited.has(current))
      continue
    visited.add(current)
    for (const edge of graph.edges.filter(item => item.source === current)) {
      const next = graph.nodes.find(node => node.id === edge.target)
      if (!next)
        continue
      if (next.nodeType === NODE_TYPE.APPROVER && !next.config?.initiatorModify)
        return next
      queue.push(next.id)
    }
  }
  return null
}
