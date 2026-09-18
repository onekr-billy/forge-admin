import { FIT_EPSILON } from './pageGeometry'

export function tableMinimumHeight(section) {
  return section.headers.reduce((sum, row) => sum + row.heightMm, 0) + (section.rows[0]?.heightMm || 0)
}

export function paginateTable(section, cursor) {
  const headerHeight = section.headers.reduce((sum, row) => sum + row.heightMm, 0)
  const sourceRows = [...section.rows, ...(section.footer ? [section.footer] : [])]
  let offset = 0
  let first = true
  while (offset < sourceRows.length) {
    const headers = first || section.repeatHeader ? section.headers : []
    const reservedMm = headers.length ? headerHeight : 0
    cursor.ensure(reservedMm + sourceRows[offset].heightMm, `${section.id}:${sourceRows[offset].key}`)
    const rows = [...headers]
    let heightMm = reservedMm
    while (offset < sourceRows.length && heightMm + sourceRows[offset].heightMm <= cursor.remainingMm + FIT_EPSILON) {
      rows.push(sourceRows[offset])
      heightMm += sourceRows[offset].heightMm
      offset++
    }
    cursor.place({ id: section.id, kind: 'TABLE', type: 'TABLE', widthMm: section.widthMm, rows, heightMm })
    first = false
    if (offset < sourceRows.length) {
      cursor.next()
    }
  }
}
