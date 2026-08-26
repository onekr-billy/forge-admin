/**
 * Canvas 渲染层布局引擎（Task 10）
 *
 * 与 converter/layout-algorithm.js 的区别：
 * - converter/layout-algorithm 服务于 JSON→BPMN，输出 BPMNDiagram 坐标，dc:Bounds + di:waypoint
 * - canvas/layout-engine 服务于画布渲染，输出节点位置 + 边 polyline + 路径类型，便于 EdgeLayer 渲染
 *
 * 输出：
 *   {
 *     nodePositions: Map<nodeId, {x, y, width, height}>,
 *     edgePaths: Map<edgeId, {points: [{x,y}], type: 'straight' | 'orthogonal'}>,
 *     canvasBounds: { minX, minY, maxX, maxY }
 *   }
 *
 * 复用 converter 的 calculateLayout 计算节点位置 + 原始 waypoints；本层再做：
 * - 边类型识别（同 x → straight；不同 x → orthogonal）
 * - canvasBounds 整体范围（用于 fitToScreen）
 */

import { calculateLayout as baseLayout } from '../converter/layout-algorithm.js'

const DEFAULT_OPTS = {
  NODE_WIDTH: 224,
  NODE_HEIGHT: 82,
  GATEWAY_SIZE: 44,
  V_GAP: 56,
  H_GAP: 72,
  MARGIN_TOP: 56,
  MARGIN_LEFT: 240,
}

const GATEWAY_TYPES = new Set(['condition', 'parallel', 'inclusive'])

export function layoutFlow(flowJson, opts = {}) {
  const cfg = { ...DEFAULT_OPTS, ...opts }
  const result = {
    nodePositions: new Map(),
    edgePaths: new Map(),
    canvasBounds: { minX: 0, minY: 0, maxX: 0, maxY: 0 },
  }
  if (!flowJson || !Array.isArray(flowJson.nodes) || flowJson.nodes.length === 0)
    return result

  // 复用 converter 的几何计算
  const base = baseLayout(flowJson, cfg)

  // 节点位置直接复用；网关在画布上是菱形锚点，不占用普通任务卡尺寸。
  for (const [id, pos] of base.nodePositions.entries()) {
    const node = flowJson.nodes.find(item => item.id === id)
    if (GATEWAY_TYPES.has(node?.nodeType)) {
      const size = cfg.GATEWAY_SIZE
      result.nodePositions.set(id, {
        x: pos.x + (pos.width - size) / 2,
        y: pos.y + (pos.height - size) / 2,
        width: size,
        height: size,
      })
      continue
    }
    result.nodePositions.set(id, {
      x: pos.x,
      y: pos.y,
      width: pos.width || cfg.NODE_WIDTH,
      height: pos.height || cfg.NODE_HEIGHT,
    })
  }

  // 边 path：保留布局算法的中间折点，但把起止端点修正到真实节点边界。
  for (const edge of flowJson.edges) {
    const wp = base.edgeWaypoints.get(edge.id)
    if (!wp || wp.length < 2)
      continue
    const points = normalizeEdgePoints(edge, wp, result.nodePositions)
    // 起止 x 一致 → straight；否则按 orthogonal（折线）
    const type = points.length === 2 ? 'straight' : 'orthogonal'
    result.edgePaths.set(edge.id, { points, type })
  }

  // canvasBounds：聚合所有节点 + waypoint
  let minX = Infinity
  let minY = Infinity
  let maxX = -Infinity
  let maxY = -Infinity
  for (const pos of result.nodePositions.values()) {
    if (pos.x < minX)
      minX = pos.x
    if (pos.y < minY)
      minY = pos.y
    if (pos.x + pos.width > maxX)
      maxX = pos.x + pos.width
    if (pos.y + pos.height > maxY)
      maxY = pos.y + pos.height
  }
  for (const path of result.edgePaths.values()) {
    for (const p of path.points) {
      if (p.x < minX)
        minX = p.x
      if (p.y < minY)
        minY = p.y
      if (p.x > maxX)
        maxX = p.x
      if (p.y > maxY)
        maxY = p.y
    }
  }

  if (Number.isFinite(minX))
    result.canvasBounds = { minX, minY, maxX, maxY }

  return result
}

function normalizeEdgePoints(edge, points, nodePositions) {
  const source = nodePositions.get(edge.source)
  const target = nodePositions.get(edge.target)
  if (!source || !target)
    return points

  const sourceCenterX = source.x + source.width / 2
  const targetCenterX = target.x + target.width / 2
  const sourceAboveTarget = source.y <= target.y
  const start = {
    x: sourceCenterX,
    y: sourceAboveTarget ? source.y + source.height : source.y,
  }
  const end = {
    x: targetCenterX,
    y: sourceAboveTarget ? target.y : target.y + target.height,
  }

  if (points.length === 2)
    return [start, end]

  const next = points.map(point => ({ ...point }))
  next[0] = start
  next[next.length - 1] = end

  if (next[1]) {
    next[1].x = start.x
    if (Math.abs(next[1].y - points[0].y) < 1)
      next[1].y = start.y
  }
  if (next[next.length - 2]) {
    next[next.length - 2].x = end.x
    if (Math.abs(next[next.length - 2].y - points[points.length - 1].y) < 1)
      next[next.length - 2].y = end.y
  }

  return next
}
