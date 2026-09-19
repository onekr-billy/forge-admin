import { describe, expect, it } from 'vitest'
import { appendStaticTableColumn, appendStaticTableRow, createStaticTable, deleteStaticTableColumn, deleteStaticTableRow, mergeStaticTableCells, splitStaticTableCell, staticTableCellAt, staticTableSize } from '../staticTable'

function ids() {
  let value = 0
  return () => `t_${++value}`
}

describe('static table editing', () => {
  it('creates a fully covered physical grid and appends rows and columns', () => {
    const table = createStaticTable(3, 2, 75, 8, ids())
    expect(staticTableSize(table)).toEqual({ widthMm: 75, heightMm: 16 })
    expect(table.cells).toHaveLength(6)
    expect(appendStaticTableRow(table, ids())).toHaveLength(3)
    expect(appendStaticTableColumn(table, ids())).toHaveLength(3)
    expect(staticTableSize(table)).toEqual({ widthMm: 100, heightMm: 24 })
  })

  it('merges and splits a rectangular selection without leaving coverage holes', () => {
    const table = createStaticTable(3, 3, 75, 9, ids())
    const selected = [staticTableCellAt(table, 0, 0).id, staticTableCellAt(table, 0, 1).id, staticTableCellAt(table, 1, 0).id, staticTableCellAt(table, 1, 1).id]
    const merged = mergeStaticTableCells(table, selected, ids())
    expect(table.cells.find(cell => cell.id === merged)).toMatchObject({ row: 0, column: 0, rowSpan: 2, colSpan: 2 })
    expect(splitStaticTableCell(table, merged, ids())).toHaveLength(4)
    expect(table.cells).toHaveLength(9)
    expect(staticTableCellAt(table, 1, 1)).toBeTruthy()
  })

  it('refuses disconnected merges and adjusts merged spans while deleting tracks', () => {
    const table = createStaticTable(3, 3, 75, 9, ids())
    expect(mergeStaticTableCells(table, [staticTableCellAt(table, 0, 0).id, staticTableCellAt(table, 2, 2).id], ids())).toBeNull()
    const selected = [0, 1, 3, 4].map(index => table.cells[index].id)
    const merged = mergeStaticTableCells(table, selected, ids())
    expect(merged).toBeTruthy()
    expect(deleteStaticTableRow(table, 0)).toBe(true)
    expect(deleteStaticTableColumn(table, 0)).toBe(true)
    expect(table.rows).toHaveLength(2)
    expect(table.columns).toHaveLength(2)
    for (let row = 0; row < 2; row++)
      for (let column = 0; column < 2; column++) expect(staticTableCellAt(table, row, column)).toBeTruthy()
  })
})
