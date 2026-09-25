import { describe, expect, it } from 'vitest'
import {
  coerceNumberFieldValue,
  isInputLikeFieldType,
  isNumberFieldType,
  isNumberLikeField,
  normalizeNumberFieldType,
  resolveNumberFieldType,
} from '../field-type-utils'

describe('field type utils', () => {
  it.each(['number', 'inputNumber', 'input-number', 'integer', 'money', 'decimal'])(
    'recognizes %s as a number field type',
    (type) => {
      expect(isNumberFieldType(type)).toBe(true)
    },
  )

  it('does not treat regular input types as numbers', () => {
    expect(isNumberFieldType('input')).toBe(false)
    expect(isNumberFieldType('')).toBe(false)
    expect(isNumberFieldType(undefined)).toBe(false)
  })

  it.each(['input', 'textarea', 'number', 'inputNumber', 'input-number', 'integer', 'money'])(
    'recognizes %s as an input-like field type',
    (type) => {
      expect(isInputLikeFieldType(type)).toBe(true)
    },
  )

  it('resolves money from componentKey when type is weak input', () => {
    expect(resolveNumberFieldType({ type: 'input', componentKey: 'money' })).toBe('money')
    expect(isNumberLikeField({ type: 'input', componentKey: 'money' })).toBe(true)
    expect(normalizeNumberFieldType('money')).toBe('number')
  })

  it('coerces string / blank money values for n-input-number', () => {
    expect(coerceNumberFieldValue('128.50')).toBe(128.5)
    expect(coerceNumberFieldValue('')).toBeNull()
    expect(coerceNumberFieldValue(null)).toBeNull()
    expect(coerceNumberFieldValue(99)).toBe(99)
    expect(coerceNumberFieldValue('abc')).toBeNull()
  })
})
