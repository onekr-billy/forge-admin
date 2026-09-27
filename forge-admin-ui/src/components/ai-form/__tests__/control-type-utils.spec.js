import { describe, expect, it } from 'vitest'
import {
  isWeakControlType,
  normalizeRuntimeControlType,
  resolveDocumentNodeControlType,
  resolveFieldControlType,
} from '../control-type-utils'
import { isOrgSelectLikeField, isUserSelectLikeField } from '../selection-label-fields'

describe('control-type-utils', () => {
  it.each([
    ['money', 'number'],
    ['inputNumber', 'number'],
    ['integer', 'number'],
    ['forgeUserSelect', 'userSelect'],
    ['userPicker', 'userSelect'],
    ['deptSelect', 'orgTreeSelect'],
    ['forgeOrgTreeSelect', 'orgTreeSelect'],
    ['forgeDictSelect', 'dictSelect'],
    ['upload', 'fileUpload'],
    ['switch', 'switch'],
    ['datetime', 'datetime'],
  ])('normalizes %s → %s', (input, expected) => {
    expect(normalizeRuntimeControlType(input)).toBe(expected)
  })

  it('prefers strong componentKey over weak type', () => {
    expect(resolveFieldControlType({ type: 'input', componentKey: 'switch' })).toBe('switch')
    expect(resolveFieldControlType({ type: 'input', componentKey: 'money' })).toBe('number')
    expect(resolveFieldControlType({ type: 'input', componentKey: 'forgeUserSelect' })).toBe('userSelect')
    expect(resolveFieldControlType({ type: 'input', componentKey: 'deptTreeSelect' })).toBe('orgTreeSelect')
    expect(resolveFieldControlType({ type: 'input', componentKey: 'upload' })).toBe('fileUpload')
  })

  it('resolves uiDocument nodes the same way', () => {
    expect(resolveDocumentNodeControlType({ type: 'input', componentKey: 'date' })).toBe('date')
    expect(isWeakControlType('input')).toBe(true)
    expect(isWeakControlType('switch')).toBe(false)
  })
})

describe('selection-label-fields componentKey aliases', () => {
  it('detects user/org from componentKey when type is weak', () => {
    expect(isUserSelectLikeField({ type: 'input', componentKey: 'forgeUserSelect' })).toBe(true)
    expect(isOrgSelectLikeField({ type: 'input', componentKey: 'deptSelect' })).toBe(true)
    expect(isUserSelectLikeField({ type: 'input' })).toBe(false)
  })
})
