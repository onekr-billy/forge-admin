import { computed, ref } from 'vue'
import { useFlowHistory } from '../flow-designer/composables/useFlowHistory.js'
import {
  BUSINESS_PROCESS_NODE_TYPE,
  createBusinessProcessNodeTemplate,
  getBusinessProcessNodeDefinition,
  isBusinessProcessStartType,
} from './business-process-node-types.js'
import {
  businessProcessHashInput,
  cloneBusinessProcessSchema,
  normalizeBusinessProcessSchema,
  synchronizeBusinessProcessDependencies,
} from './business-process-schema.js'

export function useBusinessProcessDesigner(initialSchema, options = {}) {
  const schema = ref(normalizeBusinessProcessSchema(initialSchema))
  const selectedNodeId = ref(null)
  const savedHashInput = ref(businessProcessHashInput(schema.value))
  const history = useFlowHistory(schema, { maxStack: options.maxHistory || 50 })
  let idSequence = 0

  const isDirty = computed(() => businessProcessHashInput(schema.value) !== savedHashInput.value)

  function getNode(nodeId) {
    return schema.value.nodes.find(node => node.id === nodeId) || null
  }

  function getEdge(edgeId) {
    return schema.value.edges.find(edge => edge.id === edgeId) || null
  }

  function getOutgoingEdges(nodeId) {
    return schema.value.edges.filter(edge => edge.source === nodeId)
  }

  function getIncomingEdges(nodeId) {
    return schema.value.edges.filter(edge => edge.target === nodeId)
  }

  function selectNode(nodeId) {
    selectedNodeId.value = getNode(nodeId) ? nodeId : null
  }

  function addNode(afterNodeId, type, overrides = {}) {
    const current = getNode(afterNodeId)
    if (!current)
      throw new Error('找不到插入位置对应的业务流程节点')
    if (current.type === BUSINESS_PROCESS_NODE_TYPE.END)
      throw new Error('结束节点之后不能继续添加节点')

    const outgoing = getOutgoingEdges(afterNodeId)
    if (outgoing.length !== 1)
      throw new Error('请在唯一后继连线上插入节点；分支节点需先选择具体出口')

    return insertNodeOnEdge(outgoing[0].id, type, overrides)
  }

  function insertNodeOnEdge(edgeId, type, overrides = {}) {
    const currentEdge = getEdge(edgeId)
    if (!currentEdge)
      throw new Error('找不到插入位置对应的业务流程连线')

    const template = createBusinessProcessNodeTemplate(type)
    if (isBusinessProcessStartType(template.type))
      throw new Error('每个业务流程只能保留一个开始节点')
    if (template.type === BUSINESS_PROCESS_NODE_TYPE.END)
      throw new Error('流程结束节点由系统维护，不能插入第二个结束节点')

    const next = cloneBusinessProcessSchema(schema.value)
    const newId = overrides.id == null
      ? nextGraphId(template.type.toLowerCase(), next)
      : requireNewNodeId(overrides.id, next)
    const newNode = {
      id: newId,
      type: template.type,
      name: typeof overrides.name === 'string' && overrides.name.trim()
        ? overrides.name.trim()
        : template.name,
      ports: Array.isArray(overrides.ports) ? deepClone(overrides.ports) : template.ports,
      config: {
        ...deepClone(template.config),
        ...deepClone(overrides.config || {}),
      },
    }

    const predecessorEdge = next.edges.find(edge => edge.id === currentEdge.id)
    const originalTarget = predecessorEdge.target
    predecessorEdge.target = newId
    next.nodes.push(newNode)
    appendSuccessorEdges(next, newNode, originalTarget)
    replaceSchema(next)
    selectedNodeId.value = newId
    return newId
  }

  function copyNode(nodeId) {
    const source = getNode(nodeId)
    if (!source)
      throw new Error('找不到要复制的业务流程节点')
    if (isBusinessProcessStartType(source.type) || source.type === BUSINESS_PROCESS_NODE_TYPE.END)
      throw new Error('开始和结束节点不能复制')
    if (getOutgoingEdges(nodeId).length !== 1)
      throw new Error('带多个结果出口的节点不能直接复制')

    return addNode(nodeId, source.type, {
      name: `${source.name} 副本`,
      ports: source.ports,
      config: source.config,
    })
  }

  function updateNode(nodeId, patch, options = {}) {
    const next = cloneBusinessProcessSchema(schema.value)
    const index = next.nodes.findIndex(node => node.id === nodeId)
    if (index < 0)
      throw new Error('找不到要更新的业务流程节点')
    const current = next.nodes[index]
    const updated = {
      ...current,
      ...deepClone(patch || {}),
      id: current.id,
      type: current.type,
      config: patch?.config == null
        ? current.config
        : options.replaceConfig
          ? deepClone(patch.config)
          : { ...current.config, ...deepClone(patch.config) },
    }
    next.nodes[index] = updated
    if (current.type === BUSINESS_PROCESS_NODE_TYPE.CONDITION
      && Array.isArray(patch?.config?.branches)) {
      synchronizeConditionEdges(next, current, updated)
    }
    replaceSchema(next)
    return getNode(nodeId)
  }

  function changeStartType(nodeId, type, overrides = {}) {
    const source = getNode(nodeId)
    if (!source || !isBusinessProcessStartType(source.type))
      throw new Error('找不到要切换的开始节点')
    const template = createBusinessProcessNodeTemplate(type)
    if (!isBusinessProcessStartType(template.type))
      throw new Error('开始节点只能切换为手动、事件或定时触发')

    const next = cloneBusinessProcessSchema(schema.value)
    const index = next.nodes.findIndex(node => node.id === nodeId)
    next.nodes[index] = {
      ...next.nodes[index],
      type: template.type,
      name: overrides.name || next.nodes[index].name || template.name,
      ports: template.ports,
      config: {
        ...deepClone(template.config),
        ...deepClone(overrides.config || {}),
      },
    }
    next.subject.recordIdSource = overrides.recordIdSource || recordIdSource(template.type)
    replaceSchema(next)
    return getNode(nodeId)
  }

  function deleteNode(nodeId) {
    const current = getNode(nodeId)
    if (!current)
      return false
    if (isBusinessProcessStartType(current.type) || current.type === BUSINESS_PROCESS_NODE_TYPE.END)
      throw new Error('开始和结束节点不能删除')

    const incoming = getIncomingEdges(nodeId)
    const outgoing = getOutgoingEdges(nodeId)
    if (!outgoing.length)
      throw new Error('当前节点没有可恢复的后继节点，无法安全删除')

    const successorIds = new Set(outgoing.map(edge => edge.target))
    if (successorIds.size !== 1)
      throw new Error('当前节点的多个分支尚未汇合，请先让分支连接到同一后继节点再删除')

    const successorId = outgoing[0].target
    if (incoming.some(edge => edge.source === successorId))
      throw new Error('删除节点会形成循环，请先调整前后节点')
    const incomingIds = new Set(incoming.map(edge => edge.id))
    const connectedIds = new Set([...incomingIds, ...outgoing.map(edge => edge.id)])
    const next = cloneBusinessProcessSchema(schema.value)
    next.nodes = next.nodes.filter(node => node.id !== nodeId)
    next.edges = next.edges
      .filter(edge => !connectedIds.has(edge.id))
      .concat(incoming.map(edge => ({
        ...deepClone(edge),
        target: successorId,
      })))
    replaceSchema(next)
    if (selectedNodeId.value === nodeId)
      selectedNodeId.value = null
    return true
  }

  function addEdge(edge) {
    const next = cloneBusinessProcessSchema(schema.value)
    if (!getNode(edge?.source) || !getNode(edge?.target))
      throw new Error('新增连线的来源或目标节点不存在')
    const id = edge.id == null ? nextGraphId('edge', next) : requireNewEdgeId(edge.id, next)
    next.edges.push({
      id,
      source: edge.source,
      target: edge.target,
      sourcePort: edge.sourcePort || 'NEXT',
      condition: deepClone(edge.condition || {}),
      isDefault: edge.isDefault ?? null,
    })
    replaceSchema(next)
    return id
  }

  function updateEdge(edgeId, patch) {
    const next = cloneBusinessProcessSchema(schema.value)
    const index = next.edges.findIndex(edge => edge.id === edgeId)
    if (index < 0)
      throw new Error('找不到要更新的业务流程连线')
    next.edges[index] = {
      ...next.edges[index],
      ...deepClone(patch || {}),
      id: edgeId,
    }
    replaceSchema(next)
    return getEdge(edgeId)
  }

  function deleteEdge(edgeId) {
    if (!getEdge(edgeId))
      return false
    const next = cloneBusinessProcessSchema(schema.value)
    next.edges = next.edges.filter(edge => edge.id !== edgeId)
    replaceSchema(next)
    return true
  }

  function setSchema(nextSchema, { markSaved = false } = {}) {
    const normalized = normalizeBusinessProcessSchema(nextSchema)
    history.snapshot()
    schema.value = normalized
    selectedNodeId.value = getNode(selectedNodeId.value)?.id || null
    if (markSaved)
      savedHashInput.value = businessProcessHashInput(schema.value)
  }

  function markSaved() {
    savedHashInput.value = businessProcessHashInput(schema.value)
  }

  function exportSchema() {
    return cloneBusinessProcessSchema(schema.value)
  }

  function undo() {
    const changed = history.undo()
    clearMissingSelection()
    return changed
  }

  function redo() {
    const changed = history.redo()
    clearMissingSelection()
    return changed
  }

  function replaceSchema(next) {
    const normalized = synchronizeBusinessProcessDependencies(next)
    history.snapshot()
    schema.value = normalized
  }

  function clearMissingSelection() {
    if (selectedNodeId.value && !getNode(selectedNodeId.value))
      selectedNodeId.value = null
  }

  function appendSuccessorEdges(next, node, target) {
    const item = getBusinessProcessNodeDefinition(node.type)
    const branches = Array.isArray(node.config?.branches) ? node.config.branches : []
    const ports = Array.isArray(node.ports) && node.ports.length ? node.ports : item.ports
    for (const port of ports) {
      const branch = branches.find(candidate => candidate?.port === port)
      next.edges.push({
        id: nextGraphId('edge', next),
        source: node.id,
        target,
        sourcePort: port,
        condition: deepClone(branch?.condition || {}),
        isDefault: branch?.isDefault === true ? true : null,
      })
    }
  }

  function synchronizeConditionEdges(next, previousNode, updatedNode) {
    const previousBranches = Array.isArray(previousNode.config?.branches)
      ? previousNode.config.branches
      : []
    const updatedBranches = updatedNode.config.branches
    const ports = updatedBranches.map((branch, index) => normalizeConditionPort(branch?.port, index))
    if (new Set(ports).size !== ports.length)
      throw new Error('条件分支标识重复，请删除重复分支后重试')

    updatedBranches.forEach((branch, index) => {
      branch.port = ports[index]
    })
    updatedNode.ports = ports

    const outgoing = next.edges.filter(edge => edge.source === previousNode.id)
    if (!outgoing.length)
      throw new Error('条件节点没有可用连线，请撤销后重新添加条件节点')
    const outgoingByPort = new Map(outgoing.map(edge => [edge.sourcePort, edge]))
    const usedEdgeIds = new Set()
    const orderedEdges = []
    const fallbackTarget = conditionFallbackTarget(previousBranches, outgoing)

    updatedBranches.forEach((branch, index) => {
      const previousPort = previousBranches[index]?.port
      const edge = outgoingByPort.get(branch.port)
        || (previousPort ? outgoingByPort.get(previousPort) : null)
      if (edge && !usedEdgeIds.has(edge.id)) {
        edge.sourcePort = branch.port
        edge.condition = deepClone(branch.condition || {})
        edge.isDefault = branch.isDefault === true ? true : null
        usedEdgeIds.add(edge.id)
        orderedEdges.push(edge)
        return
      }
      orderedEdges.push({
        id: nextGraphId('edge', next),
        source: previousNode.id,
        target: fallbackTarget,
        sourcePort: branch.port,
        condition: deepClone(branch.condition || {}),
        isDefault: branch.isDefault === true ? true : null,
      })
    })

    const removedEdges = outgoing.filter(edge => !usedEdgeIds.has(edge.id))
    assertRemovedBranchesHaveNoExclusiveNodes(next, removedEdges, usedEdgeIds)
    next.edges = next.edges
      .filter(edge => edge.source !== previousNode.id)
      .concat(orderedEdges)
  }

  function conditionFallbackTarget(previousBranches, outgoing) {
    const defaultPort = previousBranches.find(branch => branch?.isDefault)?.port
    const defaultEdge = outgoing.find(edge => edge.sourcePort === defaultPort)
    return defaultEdge?.target || outgoing[0].target
  }

  function assertRemovedBranchesHaveNoExclusiveNodes(next, removedEdges, usedEdgeIds) {
    for (const removed of removedEdges) {
      const stillConnected = next.edges.some(edge => edge.id !== removed.id
        && (!usedEdgeIds.size || usedEdgeIds.has(edge.id))
        && edge.target === removed.target)
      if (!stillConnected) {
        throw new Error('该分支已有独立下游节点，请先删除分支线上的节点再删除分支')
      }
    }
  }

  function nextGraphId(prefix, nextSchema) {
    const ids = new Set([
      ...nextSchema.nodes.map(node => node.id),
      ...nextSchema.edges.map(edge => edge.id),
    ])
    let candidate
    do {
      idSequence += 1
      candidate = `${prefix}_${Date.now().toString(36)}_${idSequence.toString(36)}`
    } while (ids.has(candidate))
    return candidate
  }

  return {
    schema,
    selectedNodeId,
    isDirty,
    canUndo: history.canUndo,
    canRedo: history.canRedo,
    getNode,
    getEdge,
    getOutgoingEdges,
    getIncomingEdges,
    selectNode,
    addNode,
    insertNodeOnEdge,
    copyNode,
    updateNode,
    changeStartType,
    deleteNode,
    addEdge,
    updateEdge,
    deleteEdge,
    setSchema,
    markSaved,
    exportSchema,
    undo,
    redo,
    clearHistory: history.clear,
    bindHistoryKeyboard: history.bindKeyboard,
  }
}

function recordIdSource(type) {
  return {
    START_MANUAL: 'RUNTIME_RECORD',
    START_EVENT: 'EVENT_RECORD',
    START_SCHEDULE: 'SCHEDULE_SCAN_RECORD',
  }[type]
}

function requireNewNodeId(nodeId, schema) {
  if (typeof nodeId !== 'string' || !nodeId.trim())
    throw new Error('节点 ID 必须使用非空字符串')
  const normalized = nodeId.trim()
  if (schema.nodes.some(node => node.id === normalized))
    throw new Error('节点 ID 已存在')
  return normalized
}

function requireNewEdgeId(edgeId, schema) {
  if (typeof edgeId !== 'string' || !edgeId.trim())
    throw new Error('连线 ID 必须使用非空字符串')
  const normalized = edgeId.trim()
  if (schema.edges.some(edge => edge.id === normalized))
    throw new Error('连线 ID 已存在')
  return normalized
}

function normalizeConditionPort(value, index) {
  const normalized = String(value || '')
    .trim()
    .toUpperCase()
    .replace(/[^A-Z0-9_]/g, '_')
  return normalized || `BRANCH_${index + 1}`
}

function deepClone(value) {
  return value == null ? value : JSON.parse(JSON.stringify(value))
}
