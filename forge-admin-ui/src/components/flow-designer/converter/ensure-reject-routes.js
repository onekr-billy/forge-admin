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
 * - MANUAL：不自动补，完全按设计器图走
 */

import { NODE_TYPE } from '../constants/node-types.js'

export const REJECT_STRATEGY = Object.freeze({
  TO_INITIATOR_MODIFY: 'TO_INITIATOR_MODIFY',
  TO_END: 'TO_END',
  MANUAL: 'MANUAL',
})

const REJECT_CONDITION = '$' + '{approvalResult == \'reject\'}'
const INITIATOR_MODIFY_ID = 'Forge_InitiatorModify'
const REJECT_END_ID = 'Forge_RejectEnd'

/**
 * @param {object} flowJson
 * @param {string} rejectStrategy
 * @returns {object} 新的 flowJson（nodes/edges 已复制），原对象不修改
 */
export function ensureRejectRoutes(flowJson, rejectStrategy = REJECT_STRATEGY.MANUAL) {
  const strategy = normalizeRejectStrategy(rejectStrategy)
  if (!flowJson || !Array.isArray(flowJson.nodes) || strategy === REJECT_STRATEGY.MANUAL)
    return flowJson

  const nodes = flowJson.nodes.map(node => ({ ...node, config: { ...(node.config || {}) } }))
  const edges = flowJson.edges.map(edge => ({ ...edge }))
  const graph = { nodes, edges }

  const approvers = nodes.filter(node =>
    node.nodeType === NODE_TYPE.APPROVER
    && !node.config?.initiatorModify
    && !node.config?.systemRejectRoute
    && node.config?.allowReject !== false)

  for (const approver of approvers) {
    if (hasRejectOutbound(graph, approver.id))
      continue
    if (strategy === REJECT_STRATEGY.TO_END)
      injectRejectToEnd(graph, approver)
    else
      injectRejectToInitiatorModify(graph, approver)
  }

  if (strategy === REJECT_STRATEGY.TO_INITIATOR_MODIFY)
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

function hasRejectOutbound(graph, nodeId) {
  const outs = graph.edges.filter(edge => edge.source === nodeId)
  for (const edge of outs) {
    if (isRejectEdge(edge))
      return true
    const target = graph.nodes.find(node => node.id === edge.target)
    if (target?.nodeType !== NODE_TYPE.CONDITION)
      continue
    if (graph.edges.some(next => next.source === target.id && isRejectEdge(next)))
      return true
  }
  return false
}

function isRejectEdge(edge) {
  if (!edge)
    return false
  if (edge.approvalResult === 'reject')
    return true
  const condition = String(edge.condition || '')
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

/**
 * 把「审批人 → 下一节点」拆成「审批人 → 网关 → 下一节点 / 驳回目标」。
 * 若审批人没有出边，则只补驳回边到目标。
 */
function splitApproverWithRejectGateway(graph, approver, rejectTargetId, gatewayId) {
  if (graph.nodes.some(node => node.id === gatewayId))
    return

  const forwardEdges = graph.edges.filter(edge => edge.source === approver.id && !isRejectEdge(edge))
  const gateway = {
    id: gatewayId,
    name: '驳回分支',
    nodeType: NODE_TYPE.CONDITION,
    config: { systemRejectRoute: true },
  }
  graph.nodes.push(gateway)

  if (forwardEdges.length === 0) {
    const rejectEdgeId = `Forge_RejectEdge_${approver.id}`
    graph.edges.push({
      id: rejectEdgeId,
      source: gatewayId,
      target: rejectTargetId,
      condition: REJECT_CONDITION,
      approvalResult: 'reject',
      systemRejectRoute: true,
    })
    graph.edges.push({
      id: `Forge_ToGW_${approver.id}`,
      source: approver.id,
      target: gatewayId,
      systemRejectRoute: true,
    })
    // 无前进边时，网关需要一条默认边，否则只有条件边；补一条到驳回结束/修改节点之外的兜底 end。
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
    return
  }

  // 保留原前进目标：把审批人出边改接到网关，再从网关默认接到原目标。
  const primary = forwardEdges[0]
  const originalTarget = primary.target
  primary.target = gatewayId

  const defaultEdgeId = `Forge_ApproveEdge_${approver.id}`
  graph.edges.push({
    id: defaultEdgeId,
    source: gatewayId,
    target: originalTarget,
    isDefault: true,
    systemRejectRoute: true,
  })
  gateway.config.defaultFlowId = defaultEdgeId

  graph.edges.push({
    id: `Forge_RejectEdge_${approver.id}`,
    source: gatewayId,
    target: rejectTargetId,
    condition: REJECT_CONDITION,
    approvalResult: 'reject',
    systemRejectRoute: true,
  })

  // 其余前进边也改接到网关，避免审批人仍有直连旁路。
  for (let i = 1; i < forwardEdges.length; i += 1) {
    forwardEdges[i].target = gatewayId
  }
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

  const plainEnd = graph.nodes.find(node => node.nodeType === NODE_TYPE.END)
  if (plainEnd)
    return plainEnd.id

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
