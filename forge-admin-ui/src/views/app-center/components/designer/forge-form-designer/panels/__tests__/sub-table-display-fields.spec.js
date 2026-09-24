import { describe, expect, it } from 'vitest'
import {
  buildSubTableDisplayFieldOptions,
  collectRequiredFieldCodes,
  ensureRequiredDisplayFieldCodes,
  isBusinessFieldRequired,
  toSubTableColumnDefs,
} from '../sub-table-display-fields'

describe('sub-table-display-fields', () => {
  it('detects required flags from field or validation', () => {
    expect(isBusinessFieldRequired({ required: true })).toBe(true)
    expect(isBusinessFieldRequired({ required: 1 })).toBe(true)
    expect(isBusinessFieldRequired({ validation: { required: true } })).toBe(true)
    expect(isBusinessFieldRequired({ required: false })).toBe(false)
  })

  it('puts required fields first and keeps labels', () => {
    const options = buildSubTableDisplayFieldOptions([
      { fieldCode: 'remark', fieldName: '备注', required: false },
      { fieldCode: 'qty', fieldName: '数量', required: true },
      { fieldCode: 'name', fieldName: '名称', required: true },
    ])
    expect(options.map(item => item.value)).toEqual(['qty', 'name', 'remark'])
    expect(options[0]).toMatchObject({ label: '数量', required: true })
  })

  it('always merges required codes into selected display fields', () => {
    const options = [
      { value: 'qty', required: true },
      { value: 'name', required: true },
      { value: 'remark', required: false },
    ]
    expect(ensureRequiredDisplayFieldCodes(['remark'], options)).toEqual(['qty', 'name', 'remark'])
    expect(ensureRequiredDisplayFieldCodes([], options)).toEqual(['qty', 'name'])
    expect(collectRequiredFieldCodes(options)).toEqual(['qty', 'name'])
  })

  it('builds column defs with required marker', () => {
    const cols = toSubTableColumnDefs(['qty', 'remark'], [
      { fieldCode: 'qty', fieldName: '数量', required: true },
      { fieldCode: 'remark', fieldName: '备注' },
    ])
    expect(cols).toEqual([
      { fieldCode: 'qty', fieldLabel: '数量', required: true },
      { fieldCode: 'remark', fieldLabel: '备注', required: false },
    ])
  })
})
