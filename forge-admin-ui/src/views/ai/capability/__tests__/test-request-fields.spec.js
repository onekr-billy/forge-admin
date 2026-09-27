import { describe, expect, it } from 'vitest'
import { editableRequestFields, requestFieldValue, requestObject, requestValidationError, updateRequestField } from '../components/capabilityTestRequest'

const field = (name, type = 'string', required = true) => ({ path: `$.${name}`, fieldCode: name.split('.').at(-1), fieldLabel: name, type, required })
describe('typed request editor', () => {
  it('preserves long string IDs and unrelated JSON fields', () => {
    const [id] = editableRequestFields({ requestFields: [field('recordId')] })
    const text = updateRequestField('{"recordId":"old","extra":{"value":1}}', id, '2100942720360046593')
    expect(requestObject(text)).toEqual({ recordId: '2100942720360046593', extra: { value: 1 } })
    expect(requestFieldValue(requestObject(text), id)).toBe('2100942720360046593')
  })
  it('supports nested scalar form data without flattening the payload', () => {
    const guide = { requestFields: [field('data', 'object'), field('data.title'), field('data.amount', 'number')] }
    const fields = editableRequestFields(guide)
    const text = updateRequestField('{"data":{"amount":2}}', fields[0], '测试')
    expect(requestObject(text)).toEqual({ data: { title: '测试', amount: 2 } })
    expect(requestValidationError(guide, text)).toBe('')
  })
  it('preserves false and zero and allows clearing optional values', () => {
    const enabled = field('enabled', 'boolean', false)
    const zero = updateRequestField('{"enabled":true}', field('amount', 'number'), 0)
    const no = updateRequestField(zero, enabled, false)
    expect(requestObject(no)).toEqual({ enabled: false, amount: 0 })
    expect(requestObject(updateRequestField(no, enabled, null))).toEqual({ amount: 0 })
  })
  it('falls back for arrays, unknown types, ambiguous keys or unsafe paths', () => {
    for (const item of [field('items', 'array'), field('x', 'unknown'), field('x.*'), field('__proto__.x')])
      expect(editableRequestFields({ requestFields: [item] })).toBeNull()
    expect(() => updateRequestField('{}', field('__proto__.x'), 'bad')).toThrow()
    expect({}.x).toBeUndefined()
  })
  it('does not silently overwrite an invalid parent object or invalid JSON', () => {
    expect(() => updateRequestField('{"data":"text"}', field('data.title'), 'new')).toThrow('父字段')
    expect(() => requestObject('[]')).toThrow('JSON 对象')
    expect(requestValidationError({}, '{')).toContain('JSON 格式')
  })
  it('validates required fields and primitive types without coercion', () => {
    expect(requestValidationError({ requestFields: [field('recordId')] }, '{}')).toContain('请填写')
    expect(requestValidationError({ requestFields: [field('recordId')] }, '{"recordId":1}')).toContain('string')
    expect(requestValidationError({ requestFields: [field('amount', 'integer')] }, '{"amount":1.5}')).toContain('整数')
    expect(requestValidationError({ requestFields: [field('ok', 'boolean')] }, '{"ok":false}')).toBe('')
  })
  it('does not require children of an absent optional object', () => {
    expect(requestValidationError({ requestFields: [field('data', 'object', false), field('data.title')] }, '{}')).toBe('')
    expect(requestValidationError({ requestFields: [field('data', 'object'), field('data.title')] }, '{}')).toContain('data')
  })
})
