import { paperGeometry } from '../protocol/units'

export function newPrintId() {
  return `p_${globalThis.crypto.randomUUID()}`
}

export function findSurface(document, id) {
  return id === 'header' || id === 'footer' ? document[id] : document.body.find(section => `section:${section.id}` === id || section.id === id)
}

export function selectionBounds(elements) {
  if (!elements.length) {
    return null
  }
  const left = Math.min(...elements.map(e => e.xMm))
  const top = Math.min(...elements.map(e => e.yMm))
  return {
    xMm: left,
    yMm: top,
    widthMm: Math.max(...elements.map(e => e.xMm + e.widthMm)) - left,
    heightMm: Math.max(...elements.map(e => e.yMm + e.heightMm)) - top,
  }
}

function axisAnchors(start, size) {
  return [start, start + size / 2, start + size]
}

function nearestSnap(moving, targets, thresholdMm) {
  let best = null
  for (const source of moving) {
    for (const target of targets) {
      const adjustment = target - source
      if (Math.abs(adjustment) <= thresholdMm && (!best || Math.abs(adjustment) < Math.abs(best.adjustment))) {
        best = { adjustment, target }
      }
    }
  }
  return best
}

function snapAxis(moving, targets, gridSource, thresholdMm) {
  const edge = nearestSnap(moving, targets, thresholdMm)
  const gridTarget = Math.round(gridSource)
  const gridAdjustment = gridTarget - gridSource
  const grid = Math.abs(gridAdjustment) <= Math.min(0.35, thresholdMm)
    ? { adjustment: gridAdjustment, target: gridTarget }
    : null
  if (!edge)
    return grid
  if (!grid || Math.abs(edge.adjustment) <= Math.abs(grid.adjustment))
    return edge
  return grid
}

function surfaceSnapTargets(document, surfaceId, excludedIds = []) {
  const surface = findSurface(document, surfaceId)
  const width = paperGeometry(document).contentWidthMm
  const x = [0, width / 2, width]
  const y = [0, surface?.heightMm / 2, surface?.heightMm].filter(Number.isFinite)
  for (const element of surface?.elements || []) {
    if (excludedIds.includes(element.id))
      continue
    x.push(...axisAnchors(element.xMm, element.widthMm))
    y.push(...axisAnchors(element.yMm, element.heightMm))
  }
  return { x, y }
}

export function snapTranslation(document, surfaceId, ids, dx, dy, thresholdMm = 0.8) {
  const surface = findSurface(document, surfaceId)
  const elements = surface?.elements?.filter(element => ids.includes(element.id)) || []
  const bounds = selectionBounds(elements)
  if (!bounds)
    return { dx, dy, guides: { x: [], y: [], position: null } }
  const targets = surfaceSnapTargets(document, surfaceId, ids)
  const nextX = bounds.xMm + dx
  const nextY = bounds.yMm + dy
  const xSnap = snapAxis(axisAnchors(nextX, bounds.widthMm), targets.x, nextX, thresholdMm)
  const ySnap = snapAxis(axisAnchors(nextY, bounds.heightMm), targets.y, nextY, thresholdMm)
  const snappedDx = dx + (xSnap?.adjustment || 0)
  const snappedDy = dy + (ySnap?.adjustment || 0)
  return {
    dx: snappedDx,
    dy: snappedDy,
    guides: {
      x: xSnap ? [Number(xSnap.target.toFixed(3))] : [],
      y: ySnap ? [Number(ySnap.target.toFixed(3))] : [],
      position: {
        xMm: Number((bounds.xMm + snappedDx).toFixed(3)),
        yMm: Number((bounds.yMm + snappedDy).toFixed(3)),
      },
    },
  }
}

export function snapResize(document, surfaceId, id, dx, dy, thresholdMm = 0.8) {
  const surface = findSurface(document, surfaceId)
  const element = surface?.elements?.find(item => item.id === id)
  if (!element)
    return { dx, dy, guides: { x: [], y: [], position: null } }
  const targets = surfaceSnapTargets(document, surfaceId, [id])
  const right = element.xMm + element.widthMm + dx
  const bottom = element.yMm + element.heightMm + dy
  const xSnap = snapAxis([right], targets.x, right, thresholdMm)
  const ySnap = snapAxis([bottom], targets.y, bottom, thresholdMm)
  const snappedDx = dx + (xSnap?.adjustment || 0)
  const snappedDy = dy + (ySnap?.adjustment || 0)
  return {
    dx: snappedDx,
    dy: snappedDy,
    guides: {
      x: xSnap ? [Number(xSnap.target.toFixed(3))] : [],
      y: ySnap ? [Number(ySnap.target.toFixed(3))] : [],
      position: {
        xMm: Number(element.xMm.toFixed(3)),
        yMm: Number(element.yMm.toFixed(3)),
        widthMm: Number((element.widthMm + snappedDx).toFixed(3)),
        heightMm: Number((element.heightMm + snappedDy).toFixed(3)),
      },
    },
  }
}

export function translateElements(document, surfaceId, ids, dx, dy) {
  const surface = findSurface(document, surfaceId)
  const elements = surface?.elements?.filter(e => ids.includes(e.id)) || []
  const bounds = selectionBounds(elements)
  if (!bounds || !Number.isFinite(dx) || !Number.isFinite(dy)) {
    return
  }
  const maxX = paperGeometry(document).contentWidthMm - bounds.xMm - bounds.widthMm
  const maxY = surface.heightMm - bounds.yMm - bounds.heightMm
  const x = Math.min(maxX, Math.max(-bounds.xMm, Number(dx.toFixed(3))))
  const y = Math.min(maxY, Math.max(-bounds.yMm, Number(dy.toFixed(3))))
  elements.forEach((e) => {
    e.xMm = e.xMm + x
    e.yMm = e.yMm + y
  })
}

export function resizeElement(document, surfaceId, id, dx, dy) {
  const surface = findSurface(document, surfaceId)
  const element = surface?.elements?.find(e => e.id === id)
  if (!element || !Number.isFinite(dx) || !Number.isFinite(dy)) {
    return
  }
  const previousWidth = element.widthMm
  const previousHeight = element.heightMm
  element.widthMm = Math.max(0.1, Math.min(paperGeometry(document).contentWidthMm - element.xMm, Number((element.widthMm + dx).toFixed(3))))
  element.heightMm = Math.max(0.1, Math.min(surface.heightMm - element.yMm, Number((element.heightMm + dy).toFixed(3))))
  if (element.type === 'STATIC_TABLE' && element.table) {
    const scale = (items, key, ratio, total) => {
      items.forEach(item => item[key] = Number((item[key] * ratio).toFixed(3)))
      const rest = Number((total - items.reduce((sum, item) => sum + item[key], 0)).toFixed(3))
      items.at(-1)[key] = Number((items.at(-1)[key] + rest).toFixed(3))
    }
    scale(element.table.columns, 'widthMm', element.widthMm / previousWidth, element.widthMm)
    scale(element.table.rows, 'heightMm', element.heightMm / previousHeight, element.heightMm)
  }
}
