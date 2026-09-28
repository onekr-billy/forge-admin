import assert from 'node:assert/strict'
import test from 'node:test'
import {
  adaptBusinessTaskFields,
  adaptChildrenConfig,
  buildBusinessTaskFormData,
  buildDefaultPageSections,
  hasWritableBusinessTaskForm,
} from '../business-task-form-adapter.js'
import { resolveMobileComponent } from '../../components/lowcode/mobile-component-registry.js'

test('preserves range controls and writable metadata for H5', () => {
  const fields = adaptBusinessTaskFields([{
    field: 'period',
    label: '有效期',
    componentType: 'daterange',
    writable: true,
    readable: true,
    props: { startPlaceholder: '开始日期', endPlaceholder: '结束日期' },
  }])

  assert.equal(fields[0].field, 'period')
  assert.equal(fields[0].type, 'daterange')
  assert.equal(fields[0].readonly, false)
  assert.equal(fields[0].formVisible, true)
  assert.equal(fields[0].props.startPlaceholder, '开始日期')
  assert.equal(fields[0].props.endPlaceholder, '结束日期')
  assert.deepEqual(fields[0].itemPermissions, [])
})

test('BPMN readable and writable keys take precedence over legacy aliases', () => {
  const fields = adaptBusinessTaskFields([{
    field: 'amount',
    type: 'money',
    readable: true,
    writable: true,
    required: true,
  }], [{
    field: 'amount',
    readable: true,
    visible: false,
    writable: false,
    editable: true,
    required: true,
  }])

  assert.equal(fields.length, 1)
  assert.equal(fields[0].readonly, true)
  assert.equal(fields[0].required, false)
})

test('writable permission cannot override an explicit editable false field', () => {
  const fields = adaptBusinessTaskFields([{
    field: 'fieldInput',
    type: 'input',
    editable: false,
    props: { placeholder: '请填写输入框' },
  }], [{
    field: 'fieldInput',
    readable: true,
    writable: true,
    required: true,
  }])

  assert.equal(fields[0].writable, false)
  assert.equal(fields[0].editable, false)
  assert.equal(fields[0].readonly, true)
  assert.equal(fields[0].disabled, true)
  assert.equal(fields[0].required, false)
  assert.deepEqual(buildBusinessTaskFormData({
    fields,
    mainData: { fieldInput: '不应提交' },
  }), {})
})

test('accepts JSON permission payloads and removes unreadable fields', () => {
  const fields = adaptBusinessTaskFields([
    { field: 'visibleField', type: 'input', writable: true },
    { field: 'hiddenField', type: 'input', writable: true },
  ], JSON.stringify({ fields: [
    { fieldCode: 'visible_field', readable: true, writable: true },
    { fieldCode: 'hiddenField', readable: false, writable: false },
  ] }))

  assert.deepEqual(fields.map(field => field.field), ['visibleField'])
  assert.equal(fields[0].readonly, false)
})

test('unknown approval components stay blocked instead of degrading to input', () => {
  const [field] = adaptBusinessTaskFields([{
    field: 'futureField',
    componentType: 'future-ai-widget',
    readable: true,
    writable: true,
  }])

  assert.equal(field.type, 'future-ai-widget')
  assert.equal(resolveMobileComponent(field.type).capability, 'blocked')
})

test('applies relation-scoped child permissions to child fields', () => {
  const [child] = adaptChildrenConfig([{
    relationKey: 'items',
    fields: [
      { field: 'quantity', type: 'number', writable: true },
      { field: 'secretCost', type: 'money', writable: true },
    ],
  }], [
    { scope: 'child', relationKey: 'items', field: 'quantity', readable: true, writable: true, required: true },
    { scope: 'child', relationKey: 'items', field: 'secretCost', readable: false, writable: false },
  ])

  assert.deepEqual(child.fields.map(field => field.field), ['quantity'])
  assert.equal(child.fields[0].required, true)
  assert.equal(child.fields[0].readonly, false)
})

test('preserves approval remote selector metadata', () => {
  const [field] = adaptBusinessTaskFields([{
    field: 'customerId',
    componentType: 'recordSelector',
    readable: true,
    writable: true,
    multiple: true,
    labelValueField: 'customerName',
    props: {
      recordSelector: { objectCode: 'customer', displayFields: ['name:客户'] },
      fieldMappings: [{ sourceField: 'creditCode', targetField: 'customerCreditCode' }],
    },
  }])

  assert.equal(field.type, 'recordSelector')
  assert.equal(field.multiple, true)
  assert.equal(field.labelValueField, 'customerName')
  assert.equal(field.props.recordSelector.objectCode, 'customer')
  assert.deepEqual(field.fieldMappings, [{ sourceField: 'creditCode', targetField: 'customerCreditCode' }])
})

test('builds default approval sections for main and child forms', () => {
  const children = adaptChildrenConfig([{
    relationKey: 'items',
    title: '采购明细',
    fields: [{ field: 'quantity', type: 'number', writable: true }],
  }])
  const sections = buildDefaultPageSections([{ field: 'title' }], children)

  assert.deepEqual(sections.map(section => section.sectionType), ['card', 'child_table'])
  assert.equal(sections[1].relationKey, 'items')
  assert.equal(sections[1].title, '采购明细')

  const configured = buildDefaultPageSections([{ field: 'title' }], children, [{
    sectionId: 'configured-main',
    sectionType: 'card',
    fields: ['title'],
  }])
  assert.deepEqual(configured.map(section => section.sectionId), ['configured-main', 'child:items'])
})

test('binds uiDocument child sections to relation configs by section id', () => {
  const children = adaptChildrenConfig([{
    key: 'cgou_business_object_hl92',
    modelCode: 'cgou_business_object_hl92',
    relationKey: 'business_object_hl92',
    displayMode: 'inline_grid',
    fields: [{ field: 'fieldInput', readable: true, writable: true }],
  }])
  const sections = buildDefaultPageSections([{ field: 'title' }], children, [
    { sectionId: 'section_default', sectionType: 'card', fields: ['title'] },
    { sectionId: 'child_business_object_hl92', sectionType: 'child_table', title: '指标汇总', fields: [] },
  ])

  assert.equal(sections.length, 2)
  assert.equal(sections[1].relationKey, 'business_object_hl92')
  assert.equal(sections[1].displayMode, 'inline_grid')
})

test('detects child-only writable approval forms', () => {
  const children = adaptChildrenConfig([{
    relationKey: 'items',
    allowCreate: true,
    fields: [{ field: 'quantity', type: 'number', writable: false }],
  }])

  assert.equal(hasWritableBusinessTaskForm([], children), true)
  assert.equal(children[0].approvalPermissionControlled, true)
  assert.equal(children[0].saveMode, 'merge')
})

test('builds permission-filtered master-detail payload and preserves Long ids', () => {
  const fields = adaptBusinessTaskFields([
    { field: 'title', type: 'input', writable: true },
    { field: 'auditCode', type: 'input', writable: false },
  ])
  const children = adaptChildrenConfig([{
    relationKey: 'items',
    modelCode: 'order_item',
    allowCreate: true,
    allowUpdate: true,
    allowDelete: true,
    fields: [
      { field: 'quantity', type: 'number', writable: true },
      { field: 'secretCost', type: 'money', writable: false },
    ],
  }])
  const data = buildBusinessTaskFormData({
    formType: 'business-object',
    fields,
    children,
    mainData: { title: '采购申请', auditCode: 'readonly-value' },
    childData: {
      order_item: [
        { id: '9223372036854775001', quantity: 2, secretCost: 99 },
        { id: '9223372036854775002', _deleted: true, secretCost: 88 },
        { quantity: 3, secretCost: 77 },
      ],
    },
  })

  assert.deepEqual(data, {
    main: { title: '采购申请' },
    children: {
      order_item: [
        { id: '9223372036854775001', quantity: 2 },
        { id: '9223372036854775002', _deleted: true },
        { quantity: 3 },
      ],
    },
  })
})

test('does not submit unauthorized persisted child updates', () => {
  const children = adaptChildrenConfig([{
    relationKey: 'items',
    allowCreate: true,
    allowUpdate: false,
    allowDelete: true,
    fields: [{ field: 'quantity', type: 'number', writable: true }],
  }])
  const data = buildBusinessTaskFormData({
    formType: 'business-object',
    children,
    childData: {
      items: [
        { id: '1001', quantity: 5 },
        { id: '1002', _deleted: true },
        { quantity: 6 },
      ],
    },
  })

  assert.deepEqual(data.children.items, [
    { id: '1002', _deleted: true },
    { quantity: 6 },
  ])
})
