/** Shared table selection helpers (static + data table designers). */

export function selectionBoundsFromCells(cells = []) {
  if (!cells.length)
    return null
  return {
    top: Math.min(...cells.map(cell => cell.row)),
    left: Math.min(...cells.map(cell => cell.column)),
    bottom: Math.max(...cells.map(cell => cell.row + (cell.rowSpan || 1) - 1)),
    right: Math.max(...cells.map(cell => cell.column + (cell.colSpan || 1) - 1)),
  }
}

/** Which outer edges of a cell sit on the selection rectangle (Excel-style). */
export function selectionEdgeFlags(cell, bounds) {
  if (!bounds || !cell)
    return { top: false, right: false, bottom: false, left: false }
  const top = cell.row
  const left = cell.column
  const bottom = cell.row + (cell.rowSpan || 1) - 1
  const right = cell.column + (cell.colSpan || 1) - 1
  return {
    top: top === bounds.top,
    right: right === bounds.right,
    bottom: bottom === bounds.bottom,
    left: left === bounds.left,
  }
}

export function selectionEdgeClass(cell, bounds) {
  const edges = selectionEdgeFlags(cell, bounds)
  return {
    selected: true,
    'edge-t': edges.top,
    'edge-r': edges.right,
    'edge-b': edges.bottom,
    'edge-l': edges.left,
  }
}

/** Map a pointer position inside a CSS grid table to row/col indices. */
export function hitGridCell(root, clientX, clientY, rowCount, colCount) {
  if (!root || rowCount < 1 || colCount < 1)
    return null
  const rect = root.getBoundingClientRect()
  if (rect.width <= 0 || rect.height <= 0)
    return null
  const x = Math.min(rect.width - 0.001, Math.max(0, clientX - rect.left))
  const y = Math.min(rect.height - 0.001, Math.max(0, clientY - rect.top))
  const col = Math.min(colCount - 1, Math.floor((x / rect.width) * colCount))
  const row = Math.min(rowCount - 1, Math.floor((y / rect.height) * rowCount))
  return { row, column: col }
}
