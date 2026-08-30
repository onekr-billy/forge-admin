import { describe, expect, it } from 'vitest'
import { normalizeFlowFieldCatalog } from '../flow-form-loader.js'

describe('normalizeFlowFieldCatalog', () => {
  it('去重并保留字段标签与必填', () => {
    expect(normalizeFlowFieldCatalog([
      { field: 'amount', label: '金额', required: true },
      { field: 'amount', label: '重复金额' },
      { fieldCode: 'reason', title: '原因' },
      { field: '' },
    ])).toEqual([
      {
        field: 'amount',
        label: '金额',
        group: '',
        componentType: '',
        dataType: 'string',
        required: true,
        source: 'external',
      },
      {
        field: 'reason',
        label: '原因',
        group: '',
        componentType: '',
        dataType: 'string',
        required: false,
        source: 'external',
      },
    ])
  })
})
