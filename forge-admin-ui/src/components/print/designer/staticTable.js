function nestedId() {
  return `p_${globalThis.crypto.randomUUID()}`
}

function blankCell(row, column, createId = nestedId) {
  return {
    id: createId(),
    row,
    column,
    rowSpan: 1,
    colSpan: 1,
    binding: { source: 'CONSTANT', value: '' },
  }
}

export function createStaticTable(columnCount = 3, rowCount = 3, widthMm = 75, rowHeightMm = 9, createId = nestedId) {
  const columnWidth = Number((widthMm / columnCount).toFixed(3))
  const columns = Array.from({ length: columnCount }, () => ({ id: createId(), widthMm: columnWidth }))
  columns.at(-1).widthMm = Number((widthMm - columns.slice(0, -1).reduce((sum, column) => sum + column.widthMm, 0)).toFixed(3))
  const rows = Array.from({ length: rowCount }, () => ({ id: createId(), heightMm: rowHeightMm }))
  const cells = rows.flatMap((_, row) => columns.map((__, column) => blankCell(row, column, createId)))
  return { columns, rows, cells }
}

export function staticTableSize(table) {
  return {
    widthMm: Number(table.columns.reduce((sum, column) => sum + column.widthMm, 0).toFixed(3)),
    heightMm: Number(table.rows.reduce((sum, row) => sum + row.heightMm, 0).toFixed(3)),
  }
}

export function renewStaticTableIds(element, createId = nestedId) {
  if (element.type !== 'STATIC_TABLE' || !element.table)
    return element
  element.table.columns.forEach(column => column.id = createId())
  element.table.rows.forEach(row => row.id = createId())
  element.table.cells.forEach(cell => cell.id = createId())
  return element
}

export function appendStaticTableRow(table, createId = nestedId) {
  const row = table.rows.length
  const average = table.rows.reduce((sum, item) => sum + item.heightMm, 0) / table.rows.length || 9
  table.rows.push({ id: createId(), heightMm: Number(average.toFixed(3)) })
  const cells = table.columns.map((_, column) => blankCell(row, column, createId))
  table.cells.push(...cells)
  return cells.map(cell => cell.id)
}

export function appendStaticTableColumn(table, createId = nestedId) {
  const column = table.columns.length
  const average = table.columns.reduce((sum, item) => sum + item.widthMm, 0) / table.columns.length || 20
  table.columns.push({ id: createId(), widthMm: Number(average.toFixed(3)) })
  const cells = table.rows.map((_, row) => blankCell(row, column, createId))
  table.cells.push(...cells)
  return cells.map(cell => cell.id)
}

function intersectsRow(cell, row) {
  return cell.row <= row && row < cell.row + cell.rowSpan
}

function intersectsColumn(cell, column) {
  return cell.column <= column && column < cell.column + cell.colSpan
}

export function deleteStaticTableRow(table, row) {
  if (table.rows.length <= 1 || row < 0 || row >= table.rows.length)
    return false
  table.rows.splice(row, 1)
  table.cells = table.cells.flatMap((cell) => {
    if (!intersectsRow(cell, row)) {
      if (cell.row > row)
        cell.row--
      return [cell]
    }
    if (cell.rowSpan === 1)
      return []
    cell.rowSpan--
    if (cell.row === row && row >= table.rows.length)
      cell.row--
    return [cell]
  })
  return true
}

export function deleteStaticTableColumn(table, column) {
  if (table.columns.length <= 1 || column < 0 || column >= table.columns.length)
    return false
  table.columns.splice(column, 1)
  table.cells = table.cells.flatMap((cell) => {
    if (!intersectsColumn(cell, column)) {
      if (cell.column > column)
        cell.column--
      return [cell]
    }
    if (cell.colSpan === 1)
      return []
    cell.colSpan--
    if (cell.column === column && column >= table.columns.length)
      cell.column--
    return [cell]
  })
  return true
}

export function mergeStaticTableCells(table, selectedIds, createId = nestedId) {
  const selected = table.cells.filter(cell => selectedIds.includes(cell.id))
  if (selected.length < 2)
    return null
  const top = Math.min(...selected.map(cell => cell.row))
  const left = Math.min(...selected.map(cell => cell.column))
  const bottom = Math.max(...selected.map(cell => cell.row + cell.rowSpan))
  const right = Math.max(...selected.map(cell => cell.column + cell.colSpan))
  const area = (bottom - top) * (right - left)
  const covered = selected.reduce((sum, cell) => sum + cell.rowSpan * cell.colSpan, 0)
  const inside = selected.every(cell => cell.row >= top && cell.column >= left && cell.row + cell.rowSpan <= bottom && cell.column + cell.colSpan <= right)
  if (!inside || covered !== area)
    return null
  const first = selected.slice().sort((a, b) => a.row - b.row || a.column - b.column)[0]
  const merged = {
    ...first,
    id: createId(),
    row: top,
    column: left,
    rowSpan: bottom - top,
    colSpan: right - left,
  }
  table.cells = [...table.cells.filter(cell => !selectedIds.includes(cell.id)), merged]
  return merged.id
}

export function splitStaticTableCell(table, cellId, createId = nestedId) {
  const index = table.cells.findIndex(cell => cell.id === cellId)
  const cell = table.cells[index]
  if (!cell || (cell.rowSpan === 1 && cell.colSpan === 1))
    return []
  const cells = []
  for (let row = cell.row; row < cell.row + cell.rowSpan; row++) {
    for (let column = cell.column; column < cell.column + cell.colSpan; column++) {
      cells.push({
        ...blankCell(row, column, createId),
        ...(row === cell.row && column === cell.column ? { binding: cell.binding, format: cell.format, style: cell.style } : {}),
      })
    }
  }
  table.cells.splice(index, 1, ...cells)
  return cells.map(item => item.id)
}

export function staticTableCellAt(table, row, column) {
  return table.cells.find(cell => cell.row <= row && row < cell.row + cell.rowSpan && cell.column <= column && column < cell.column + cell.colSpan)
}
