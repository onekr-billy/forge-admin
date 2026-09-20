import { describe, expect, it } from 'vitest'
import {
  auditDiffHeadings,
  auditFieldSummary,
  groupAuditFields,
  sortAuditFields,
  visibleAuditFields,
} from '../data-audit-display'

describe('data-audit-display', () => {
  const fields = [
    { id: 'child', fieldLabel: '明细数量', relationKey: 'items', sortOrder: 1 },
    { id: 'main-late', fieldLabel: '备注', sortOrder: 2 },
    { id: 'main-first', fieldLabel: '名称', sortOrder: 1 },
    { id: 'summary', fieldType: 'CHILD_SUMMARY', fieldCode: '__childRows', sortOrder: 0 },
    { id: 'main-last', fieldLabel: '状态', sortOrder: 3 },
  ]

  it('sorts main table fields before child changes', () => {
    expect(sortAuditFields(fields).map(item => item.id)).toEqual([
      'main-first',
      'main-late',
      'main-last',
      'summary',
      'child',
    ])
  })

  it('shows a compact preview before expanding all changes', () => {
    expect(visibleAuditFields(fields).map(item => item.id)).toEqual([
      'main-first',
      'main-late',
      'main-last',
      'summary',
    ])
    expect(visibleAuditFields(fields, true)).toHaveLength(5)
  })

  it('uses operation-specific before and after headings', () => {
    expect(auditDiffHeadings('UPDATE')).toEqual({ before: '修改前', after: '修改后' })
    expect(auditDiffHeadings('CREATE')).toEqual({ before: '新增前', after: '新增值' })
    expect(auditDiffHeadings('DELETE')).toEqual({ before: '删除前值', after: '删除后' })
  })

  it('groups main table changes separately and summarizes each location', () => {
    const groups = groupAuditFields(fields, true)
    expect(groups.map(group => [group.label, group.total])).toEqual([
      ['主表字段变化', 3],
      ['子表变化', 2],
    ])
    expect(groups[0].fields.map(field => field.id)).toEqual([
      'main-first',
      'main-late',
      'main-last',
    ])
    expect(auditFieldSummary(fields)).toBe('主表 3 项，子表 2 项')
    expect(auditFieldSummary([], 7)).toBe('7 项变化')
  })
})
