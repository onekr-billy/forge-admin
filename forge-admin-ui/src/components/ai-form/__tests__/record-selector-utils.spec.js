import { describe, expect, it } from 'vitest'
import {
  applyRecordFieldMappings,
  normalizeSelectorMappings,
} from '../record-selector-utils'

describe('applyRecordFieldMappings', () => {
  it('maps source fields from option row onto target form fields', () => {
    expect(applyRecordFieldMappings(
      { id: '1', fieldInput: '指标A', code: 'M1' },
      [{ sourceField: 'fieldInput', targetField: 'fieldInput3' }],
    )).toEqual({ fieldInput3: '指标A' })
  })

  it('reads nested _raw when top-level column is missing', () => {
    expect(applyRecordFieldMappings(
      { id: '1', label: '显示名', _raw: { fieldInput: '来自原始行' } },
      [{ source: 'fieldInput', target: 'fieldInput3' }],
    )).toEqual({ fieldInput3: '来自原始行' })
  })

  it('resolves camelCase / snake_case source field aliases', () => {
    expect(applyRecordFieldMappings(
      { id: '1', field_input: '蛇形源值' },
      [{ sourceField: 'fieldInput', targetField: 'fieldInput3' }],
    )).toEqual({ fieldInput3: '蛇形源值' })
  })

  it('skips missing source fields instead of writing undefined', () => {
    expect(applyRecordFieldMappings(
      { id: '1', name: '有值' },
      [
        { sourceField: 'name', targetField: 'title' },
        { sourceField: 'missing', targetField: 'other' },
      ],
    )).toEqual({ title: '有值' })
  })
})

describe('normalizeSelectorMappings', () => {
  it('accepts sourceField/targetField and source/target aliases', () => {
    expect(normalizeSelectorMappings([
      { sourceField: 'a', targetField: 'b' },
      { source: 'c', target: 'd' },
      { sourceField: '', targetField: 'x' },
    ])).toEqual({ a: 'b', c: 'd' })
  })
})
