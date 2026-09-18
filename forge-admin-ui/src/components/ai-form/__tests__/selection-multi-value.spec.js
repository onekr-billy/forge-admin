import { describe, expect, it } from 'vitest'
import {
  isFieldMultiple,
  parseSelectionValues,
  serializeSelectionLabels,
  serializeSelectionValues,
  supportsMultipleSelect,
} from '../selection-multi-value'

describe('selection multi value storage', () => {
  it('recognizes designer and runtime multiple flags', () => {
    expect(supportsMultipleSelect('recordSelector')).toBe(true)
    expect(supportsMultipleSelect('input')).toBe(false)
    expect(isFieldMultiple({ type: 'userSelect', multiple: true })).toBe(true)
    expect(isFieldMultiple({ type: 'select', props: { multiple: true } })).toBe(true)
    expect(isFieldMultiple({ type: 'recordSelector', props: { recordSelector: { multiple: true } } })).toBe(true)
    expect(isFieldMultiple({ type: 'select', props: { multiple: false } })).toBe(false)
    expect(isFieldMultiple({ type: 'fileUpload', multiple: true })).toBe(false)
  })

  it('serializes multi-select values as comma-separated text', () => {
    expect(serializeSelectionValues(['1001', '1002'], true)).toBe('1001,1002')
    expect(serializeSelectionValues('1001,1002', true)).toBe('1001,1002')
    expect(serializeSelectionValues([], true)).toBe('')
    expect(serializeSelectionValues(['1001'], false)).toBe('1001')
    expect(serializeSelectionValues('1001', false)).toBe('1001')
  })

  it('parses stored comma-separated values back to arrays for widgets', () => {
    expect(parseSelectionValues('1001, 1002', true)).toEqual(['1001', '1002'])
    expect(parseSelectionValues(['1001', '1002'], true)).toEqual(['1001', '1002'])
    expect(parseSelectionValues('', true)).toEqual([])
    expect(parseSelectionValues('1001,1002', false)).toBe('1001,1002')
  })

  it('serializes corresponding labels as comma-separated text', () => {
    expect(serializeSelectionLabels(['张三', '李四'])).toBe('张三,李四')
    expect(serializeSelectionLabels('张三,李四')).toBe('张三,李四')
    expect(serializeSelectionLabels([])).toBe('')
  })
})
