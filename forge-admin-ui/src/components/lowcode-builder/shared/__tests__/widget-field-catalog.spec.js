import { describe, expect, it } from 'vitest'
import { isChildListField } from '@/components/lowcode-builder/page/page-schema'
import {
  buildWidgetFieldCatalog,
  isComposeDisplayEnabled,
  resolveComposeDisplayValue,
} from '../widget-field-catalog'

describe('widget field catalog', () => {
  it('groups main and child fields like print catalog', () => {
    const catalog = buildWidgetFieldCatalog([
      { field: 'customerName', fieldCode: 'customerName', label: '客户名称' },
      {
        field: 'items__productName',
        fieldCode: 'items__productName',
        sourceField: 'productName',
        label: '产品名称',
        modelCode: 'items',
        modelName: '明细',
        fieldScope: 'child',
      },
    ])
    expect(isChildListField(catalog.find(() => false) || {})).toBe(false)
    expect(catalog).toEqual(expect.arrayContaining([
      expect.objectContaining({ path: 'customerName', label: '客户名称' }),
      expect.objectContaining({ path: 'items', type: 'COLLECTION', label: '明细' }),
      expect.objectContaining({ path: 'items.productName', label: '明细 · 产品名称' }),
    ]))
  })

  it('reads subTable columns from form designer schema', () => {
    const catalog = buildWidgetFieldCatalog([], {
      formDesignerSchema: {
        components: [{
          componentKey: 'subTable',
          props: {
            relationKey: 'lines',
            header: '明细行',
            columns: [
              { fieldCode: 'sku', label: 'SKU' },
              { fieldCode: 'qty', label: '数量' },
            ],
          },
        }],
      },
    })
    expect(catalog).toEqual(expect.arrayContaining([
      expect.objectContaining({ path: 'lines', type: 'COLLECTION' }),
      expect.objectContaining({ path: 'lines.sku', label: '明细行 · SKU' }),
      expect.objectContaining({ path: 'lines.qty', label: '明细行 · 数量' }),
    ]))
  })

  it('composes display text from fields or template', () => {
    const data = { customerName: '张三', orderNo: 'SO-1' }
    expect(isComposeDisplayEnabled({ displayFields: ['customerName'] })).toBe(true)
    expect(resolveComposeDisplayValue(data, {
      displayFields: ['customerName', 'orderNo'],
      displaySeparator: ' / ',
    })).toBe('张三 / SO-1')
    expect(resolveComposeDisplayValue(data, {
      displayTemplate: '{customerName}（{orderNo}）',
    })).toBe('张三（SO-1）')
  })
})
