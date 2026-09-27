import assert from 'node:assert/strict'
import test from 'node:test'
import { formCreateToMobileFields, hasDeclaredFormCreateRules } from '../form-create-mobile.js'
import { adaptBusinessTaskFields } from '../business-task-form-adapter.js'

test('nested form-create rules render as mobile fields with node permissions', () => {
  const rules = JSON.stringify({ rule: [
    { type: 'row', children: [
      { type: 'input', field: 'requestTitle', title: '申请标题', validate: [{ required: true }] },
      { type: 'select', field: 'level', title: '级别', options: [{ label: '高', value: 'high' }] },
    ] },
    { type: 'inputNumber', field: 'amount', title: '金额', value: 200 },
  ] })
  const fields = adaptBusinessTaskFields(formCreateToMobileFields(rules), [
    { field: 'requestTitle', readable: true, writable: true },
    { field: 'level', readable: true, writable: false },
    { field: 'amount', readable: false, writable: false },
  ])
  assert.deepEqual(fields.map(field => field.field), ['requestTitle', 'level'])
  assert.equal(fields[0].label, '申请标题')
  assert.equal(fields[0].required, true)
  assert.equal(fields[1].readonly, true)
  assert.deepEqual(fields[1].options, [{ label: '高', value: 'high' }])
})

test('table-form child columns remain one dynamic array field', () => {
  const fields = formCreateToMobileFields([{ type: 'tableForm', field: 'items', title: '明细', props: {
    columns: [{ label: '物料', rule: [{ type: 'input', field: 'material', title: '物料' }] }],
    addable: false,
  } }])
  assert.equal(fields.length, 1)
  assert.equal(fields[0].type, 'array')
  assert.deepEqual(fields[0].itemSchema.map(field => field.field), ['material'])
  assert.equal(fields[0].arrayConfig.allowCreate, false)
})

test('field bindings and non-array validation metadata do not drop dynamic controls', () => {
  const fields = formCreateToMobileFields([{ type: 'el-input', props: { fieldCode: 'memo' }, title: '备注', rules: { required: true } }])
  assert.equal(fields.length, 1)
  assert.equal(fields[0].field, 'memo')
  assert.equal(fields[0].type, 'input')
})

test('empty forms remain actionable but missing declared fields require a safe block', () => {
  assert.equal(hasDeclaredFormCreateRules('[]'), false)
  assert.equal(hasDeclaredFormCreateRules('{}'), false)
  assert.equal(hasDeclaredFormCreateRules('[{"type":"input","field":"name"}]'), true)
  assert.equal(hasDeclaredFormCreateRules('{invalid json'), true)
})
