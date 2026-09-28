import assert from 'node:assert/strict'
import test from 'node:test'
import { fieldComponentSpecs } from '../../../../forge-admin-ui/src/components/lowcode-builder/designer-core/spec/field-components.js'
import { layoutComponentSpecs } from '../../../../forge-admin-ui/src/components/lowcode-builder/designer-core/spec/layout-components.js'
import {
  MOBILE_FIELD_PROPERTY_SUPPORT,
  MOBILE_LAYOUT_PROPERTY_SUPPORT,
  formatMobileFieldValue,
  normalizeMobileFieldContract,
  validateMobileFieldValue,
} from '../mobile-field-contract.js'

test('mobile field contract classifies every property exposed by the PC designer', () => {
  const missing = []
  for (const spec of fieldComponentSpecs) {
    const coverage = MOBILE_FIELD_PROPERTY_SUPPORT[spec.type] || {}
    for (const property of Object.keys(spec.propsSchema?.properties || {})) {
      if (!coverage[property]) missing.push(`${spec.type}.${property}`)
    }
  }
  assert.deepEqual(missing, [])
})

test('mobile layout contract classifies every property exposed by the PC designer', () => {
  const missing = []
  for (const spec of layoutComponentSpecs) {
    const coverage = MOBILE_LAYOUT_PROPERTY_SUPPORT[spec.type] || {}
    for (const property of Object.keys(spec.propsSchema?.properties || {})) {
      if (!coverage[property]) missing.push(`${spec.type}.${property}`)
    }
  }
  assert.deepEqual(missing, [])
})

test('normalization preserves protocol props and aligns legacy aliases', () => {
  const field = normalizeMobileFieldContract({
    field: 'amount',
    componentKey: 'money',
    maxlength: 12,
    validation: { required: true, min: 1 },
    props: { precision: 2, currencySymbol: '¥', customFutureProp: 'kept' },
  })
  assert.equal(field.type, 'money')
  assert.equal(field.props.maxLength, 12)
  assert.equal(field.props.precision, 2)
  assert.equal(field.props.customFutureProp, 'kept')
  assert.equal(field.required, true)
  assert.equal(field.rules.some(rule => rule.min === 1), true)
})

test('strong PC control identity wins over stale weak input metadata', () => {
  assert.equal(normalizeMobileFieldContract({ componentKey: 'input', type: 'money' }).type, 'money')
  assert.equal(normalizeMobileFieldContract({ componentKey: 'recordSelector', type: 'input' }).type, 'recordSelector')
  assert.equal(normalizeMobileFieldContract({ componentKey: 'datePicker', type: 'daterange' }).type, 'daterange')
})

test('mobile validation matches required, length, numeric range and pattern semantics', () => {
  assert.equal(validateMobileFieldValue({ label: '名称', type: 'input', required: true }, ''), '请输入名称')
  assert.equal(validateMobileFieldValue({ label: '名称', type: 'input', validation: { minLength: 3 } }, 'ab'), '名称长度不能少于 3')
  assert.equal(validateMobileFieldValue({ label: '金额', type: 'money', validation: { min: 1 } }, 0), '金额不能小于 1')
  assert.equal(validateMobileFieldValue({ label: '编码', type: 'input', validation: { pattern: '^[A-Z]+$' } }, 'abc'), '编码格式不正确')
  assert.equal(validateMobileFieldValue({ label: '编码', type: 'input', validation: { pattern: '^[A-Z]+$' } }, 'ABC'), '')
})

test('readonly formatter honors selection, switch, money and text display props', () => {
  assert.equal(formatMobileFieldValue({ type: 'select' }, '1', [{ label: '启用', value: 1 }]), '启用')
  assert.equal(formatMobileFieldValue({ type: 'switch', props: { checkedValue: 'Y', checkedText: '开启' } }, 'Y'), '开启')
  assert.equal(formatMobileFieldValue({ type: 'money', props: { precision: 2, currencySymbol: '¥' } }, 12), '¥12.00')
  assert.equal(formatMobileFieldValue({ type: 'text', props: { template: '编号：{value}', suffix: '号' } }, 8), '编号：8号')
})
