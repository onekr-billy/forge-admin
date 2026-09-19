import { describe, expect, it } from 'vitest'
import { createDesignerSampleContext, designerBindingText, designerTablePreview, designerTableRows, hasDesignerData } from '../designerSample'

const catalog = [
  { path: 'main.number', label: '采购单号', type: 'TEXT' },
  { path: 'main.total', label: '含税金额', type: 'MONEY' },
  { path: 'main.approved', label: '是否通过', type: 'BOOLEAN' },
  { path: 'children.items', label: '采购明细', type: 'COLLECTION' },
  { path: 'children.items.name', label: '物料名称', type: 'TEXT' },
  { path: 'children.items.amount', label: '金额', type: 'MONEY' },
]

describe('designer sample context', () => {
  it('creates stable labelled values without exposing binding paths', () => {
    const context = createDesignerSampleContext(catalog)
    expect(context.main.number).toBe('采购单号示例')
    expect(context.main.total).toBe(128800)
    expect(context.children.items).toHaveLength(3)
    expect(context.children.items[0]).toMatchObject({ name: '物料名称1', amount: 128800 })
    expect(designerBindingText({ source: 'FIELD', path: 'main.number' }, undefined, catalog, context)).toBe('采购单号示例')
    expect(designerBindingText({ source: 'FIELD', path: 'main.total' }, { type: 'MONEY' }, catalog, context)).toBe('1288.00')
    expect(JSON.stringify(context)).not.toContain('main.number')
  })

  it('formats table example rows with the same column formats', () => {
    const context = createDesignerSampleContext(catalog)
    const section = {
      collectionPath: 'children.items',
      columns: [
        { field: 'name', widthMm: 40 },
        { field: 'amount', widthMm: 40, format: { type: 'MONEY' } },
      ],
    }
    expect(designerTableRows(section, catalog, context, 2)).toEqual([
      ['物料名称1', '1288.00'],
      ['物料名称2', '1524.00'],
    ])
    section.headerRows = [{ cells: [{ text: '采购明细', span: 2 }] }]
    section.footer = { cells: [{ binding: { source: 'CONSTANT', value: '合计' }, span: 1 }, { binding: { source: 'FIELD', path: 'main.total' }, span: 1, format: { type: 'MONEY' } }] }
    const preview = designerTablePreview(section, catalog, context, 1)
    expect(preview.map(row => row.kind)).toEqual(['header', 'data', 'footer'])
    expect(preview[0].cells[0]).toMatchObject({ text: '采购明细', widthMm: 80 })
    expect(preview[2].cells.map(cell => cell.text)).toEqual(['合计', '1288.00'])
  })

  it('only replaces an actually empty design context', () => {
    expect(hasDesignerData({ main: {}, children: {}, flow: {} })).toBe(false)
    expect(hasDesignerData({ main: { number: 'PO-001' } })).toBe(true)
  })
})
