import { describe, expect, it } from 'vitest'
import { isInputLikeFieldType, isNumberFieldType } from '../field-type-utils'

describe('field type utils', () => {
  it.each(['number', 'inputNumber', 'input-number', 'integer', 'money'])(
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
})
