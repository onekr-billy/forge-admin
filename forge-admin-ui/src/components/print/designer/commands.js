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
  element.widthMm = Math.max(0.1, Math.min(paperGeometry(document).contentWidthMm - element.xMm, Number((element.widthMm + dx).toFixed(3))))
  element.heightMm = Math.max(0.1, Math.min(surface.heightMm - element.yMm, Number((element.heightMm + dy).toFixed(3))))
}
