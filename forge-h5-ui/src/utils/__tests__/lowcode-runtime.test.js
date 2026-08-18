import assert from 'node:assert/strict'
import test from 'node:test'
import {
  applyEventMappings,
  applyFieldLinkageChange,
  buildEventClearPatch,
  buildEventParams,
  buildDefaultData,
  ensureChildRows,
  normalizeScanContext,
  normalizeRuntimeFlowInteraction,
  normalizeDesignerField,
  normalizeField,
  normalizeMainFields,
  hasComposedRuntimePageSchema,
  filterFieldOptionsByLinkage,
  parseRuntimeConfig,
  hasActionPermission,
  resolveActionPermission,
  resolveBottomBarActions,
  resolveFieldControl,
  resolveFieldLinkageContext,
  resolveFieldLinkages,
  resolveChildRows,
  resolveChildTitle,
  resolvePageSectionChild,
  resolvePageSectionFields,
  isPageSectionReadonly,
  mergeFlowActionsIntoBottomBar,
  resolveVisiblePageSections,
  resolveRuntimeFormDesignerSchema,
  resolveRuntimePageZones,
  resolveRuntimeZoneFormDesignerSchema,
  syncChildRowAliases,
  shouldSkipFieldEvent,
} from '../lowcode-runtime.js'
import { scanBarcode } from '../barcode-scanner.js'

test('field value controls another field visibility and required state', () => {
  const field = normalizeField({
    field: 'cashAmount',
    type: 'money',
    required: true,
    props: {
      runtimeRules: [{
        enabled: true,
        mode: 'all',
        conditions: [{ source: 'formData', field: 'payMethod', operator: 'eq', value: 'CASH' }],
        effect: { visible: true, whenUnmatched: 'hidden' },
      }],
    },
  })

  assert.deepEqual(resolveFieldControl(field, { formData: { payMethod: 'STATIC_CODE' } }), {
    visible: false,
    readonly: false,
    required: true,
  })
  assert.equal(resolveFieldControl(field, { formData: { payMethod: 'CASH' } }).visible, true)
})

test('field event maps governed query parameters and response fields', () => {
  const rule = {
    resultMode: 'FIRST_ROW',
    paramMappings: [{ source: 'FORM_FIELD', field: 'barcode', param: 'barcode' }],
    resultMappings: [
      { from: 'productName', to: 'productName' },
      { from: 'price', to: 'price' },
    ],
  }

  assert.deepEqual(buildEventParams(rule, { barcode: '6901234567890' }), { barcode: '6901234567890' })
  assert.deepEqual(applyEventMappings(rule, { records: [{ productName: '测试商品', price: 1200 }] }).patch, {
    productName: '测试商品',
    price: 1200,
  })
})

test('field event skips blank sources and clears only governed targets', () => {
  const rule = {
    sourceField: 'mobile',
    skipWhenEmpty: true,
    resultMappings: [
      { from: 'memberId', to: 'memberId', whenMissing: 'CLEAR' },
      { from: 'memberLevel', to: 'memberLevel', whenMissing: 'KEEP' },
    ],
  }

  assert.equal(shouldSkipFieldEvent(rule, { mobile: '  ' }), true)
  assert.equal(shouldSkipFieldEvent(rule, { mobile: '13800000000' }), false)
  assert.deepEqual(buildEventClearPatch(rule), { memberId: '' })
})

test('field linkage clears governed targets and filters linked dictionary options', () => {
  const schema = {
    settings: {
      governance: {
        fieldLinkages: [{
          ruleId: 'province_city',
          type: 'linkedDict',
          sourceField: 'province',
          targetField: 'city',
          clearOnSourceChange: true,
        }],
      },
    },
  }
  const formData = { province: '150000', city: '150100' }
  const rules = resolveFieldLinkages(schema, {})
  const patch = applyFieldLinkageChange(rules, 'province', formData)
  const context = resolveFieldLinkageContext(rules, 'city', formData)
  const options = [
    { label: '呼和浩特', value: '150100', parentValue: '150000' },
    { label: '海淀区', value: '110108', parentValue: '110000' },
  ]

  assert.deepEqual(patch, { city: '' })
  assert.equal(formData.city, '')
  assert.equal(context.sourceValue, '150000')
  assert.deepEqual(filterFieldOptionsByLinkage(options, context).map(option => option.value), ['150100'])
})

test('scan context is length-bounded and contains only declared values', () => {
  assert.deepEqual(normalizeScanContext({ value: ' SKU-001 ', type: 'barCode', platform: 'H5', token: 'ignored' }), {
    value: 'SKU-001',
    type: 'barCode',
    platform: 'H5',
  })
  assert.equal(normalizeScanContext({ value: '' }), null)
})

test('enterprise WeChat scanner result is normalized', async () => {
  const navigatorDescriptor = Object.getOwnPropertyDescriptor(globalThis, 'navigator')
  const previousWx = globalThis.wx
  Object.defineProperty(globalThis, 'navigator', {
    configurable: true,
    value: { userAgent: 'wxwork/4.1' },
  })
  globalThis.wx = {
    scanQRCode(options) {
      options.success({ resultStr: 'CODE-128,6901234567890', scanType: 'barCode' })
    },
  }

  try {
    const result = await scanBarcode()
    assert.deepEqual(result, {
      value: 'CODE-128,6901234567890',
      type: 'barCode',
      platform: 'WECHAT_ENTERPRISE',
    })
  }
  finally {
    if (navigatorDescriptor)
      Object.defineProperty(globalThis, 'navigator', navigatorDescriptor)
    else
      delete globalThis.navigator
    if (previousWx === undefined)
      delete globalThis.wx
    else
      globalThis.wx = previousWx
  }
})

test('designer field default value initializes H5 form data', () => {
  const field = normalizeDesignerField({
    label: '收款方式',
    componentKey: 'dictSelect',
    props: { defaultValue: 'STATIC_CODE' },
    fieldBinding: { fieldCode: 'payMethod' },
  })

  assert.equal(field.defaultValue, 'STATIC_CODE')
  assert.deepEqual(buildDefaultData([field]), { payMethod: 'STATIC_CODE' })
})

test('presale payment method controls static-code and cash fields while system fields stay hidden', () => {
  const fields = normalizeMainFields({
    editSchema: [
      { field: 'salesUserId', label: '导购userid', type: 'input', formVisible: false, hidden: true },
      { field: 'payMethod', label: '收款方式', type: 'dictSelect', defaultValue: 'STATIC_CODE' },
      {
        field: 'staticPaymentNo',
        label: '静态码单号',
        type: 'input',
        runtimeRules: [{
          enabled: true,
          conditions: [{ source: 'formData', field: 'payMethod', operator: 'eq', value: 'STATIC_CODE' }],
          effect: { visible: true, whenUnmatched: 'hidden' },
        }],
      },
      {
        field: 'cashAmount',
        label: '现金金额',
        type: 'money',
        runtimeRules: [{
          enabled: true,
          conditions: [{ source: 'formData', field: 'payMethod', operator: 'eq', value: 'CASH' }],
          effect: { visible: true, whenUnmatched: 'hidden' },
        }],
      },
      { field: 'status', label: '状态', type: 'dictSelect', formVisible: false, hidden: true },
    ],
  })

  assert.deepEqual(fields.map(field => field.field), ['payMethod', 'staticPaymentNo', 'cashAmount'])
  assert.equal(resolveFieldControl(fields[1], { formData: { payMethod: 'STATIC_CODE' } }).visible, true)
  assert.equal(resolveFieldControl(fields[2], { formData: { payMethod: 'STATIC_CODE' } }).visible, false)
  assert.equal(resolveFieldControl(fields[1], { formData: { payMethod: 'CASH' } }).visible, false)
  assert.equal(resolveFieldControl(fields[2], { formData: { payMethod: 'CASH' } }).visible, true)
})

test('child rows and title support relation keys and Chinese relation labels', () => {
  const child = {
    key: 'operation_logs',
    relationKey: 'operation_logs',
    modelCode: 'ps_presale_operation_log',
    relationName: '操作日志',
  }
  const childData = {
    operation_logs: [{ id: 1 }],
  }

  assert.equal(resolveChildTitle(child), '操作日志')
  assert.deepEqual(resolveChildRows(child, childData), [{ id: 1 }])
  syncChildRowAliases(child, childData)
  assert.deepEqual(childData.ps_presale_operation_log, [{ id: 1 }])
  ensureChildRows({ modelCode: 'ps_presale_order_item', relationKey: 'presale_items' }, childData).push({ id: 2 })
  assert.deepEqual(childData.ps_presale_order_item, [{ id: 2 }])
})

test('page sections preserve configured field order and apply isolated overrides', () => {
  const fields = [
    { field: 'memberPhone', label: '会员手机号', type: 'input', props: { clearable: true } },
    { field: 'payMethod', label: '收款方式', type: 'dictSelect', props: { dictType: 'pay_method' } },
  ]
  const section = {
    fields: ['payMethod', 'missingField', 'memberPhone'],
    fieldOverrides: {
      payMethod: { componentKey: 'pillSelect', props: { clearable: false } },
    },
  }

  const resolved = resolvePageSectionFields(section, fields)

  assert.deepEqual(resolved.map(field => field.field), ['payMethod', 'memberPhone'])
  assert.equal(resolved[0].type, 'pillSelect')
  assert.deepEqual(resolved[0].props, { dictType: 'pay_method', clearable: false })
  assert.equal(fields[1].type, 'dictSelect')
})

test('page sections and bottom actions use current mode and governed conditions', () => {
  const sections = [
    { sectionId: 'main', sectionType: 'card' },
    { sectionId: 'logs', sectionType: 'child_table', visibleInModes: ['edit', 'detail'] },
  ]
  const bottomBar = {
    actions: [
      { type: 'reset', label: '清空' },
      { type: 'action', label: '提交', displayCondition: 'status == DRAFT' },
      { type: 'action', label: '非法条件保持兼容', displayCondition: 'status.includes(DRAFT)' },
      { type: 'cancel', label: '隐藏', visible: false },
    ],
  }

  assert.deepEqual(resolveVisiblePageSections(sections, 'create').map(section => section.sectionId), ['main'])
  assert.deepEqual(resolveVisiblePageSections(sections, 'detail').map(section => section.sectionId), ['main', 'logs'])
  assert.deepEqual(resolveBottomBarActions(bottomBar, { status: 'DRAFT' }).map(action => action.label), ['清空', '提交', '非法条件保持兼容'])
  assert.deepEqual(resolveBottomBarActions(bottomBar, { status: 'SUBMITTED' }).map(action => action.label), ['清空', '非法条件保持兼容'])
  assert.deepEqual(resolveBottomBarActions(bottomBar, { status: 'DRAFT' }, 'edit').map(action => action.label), ['提交', '非法条件保持兼容'])
})

test('button permissions support hide, disable and wildcard compatibility', () => {
  const hidden = { type: 'action', label: '提交', permissionKey: 'order:submit', permissionStrategy: 'hide' }
  const disabled = { type: 'action', label: '删除', permissionCode: 'order:delete', permissionStrategy: 'disable' }
  const allowed = { type: 'action', label: '查看', permissionKey: 'order:view' }

  assert.equal(hasActionPermission(hidden, ['order:submit']), true)
  assert.equal(hasActionPermission(hidden, ['order:*']), true)
  assert.equal(hasActionPermission(hidden, ['*:*:*']), true)
  assert.equal(hasActionPermission(hidden, ['**']), true)
  assert.equal(resolveActionPermission(hidden, []), null)
  assert.deepEqual(resolveActionPermission(disabled, []), { ...disabled, disabled: true, permissionDenied: true })
  assert.equal(resolveActionPermission(allowed, ['order:view']).permissionDenied, false)

  const actions = resolveBottomBarActions({ actions: [hidden, disabled, allowed] }, {}, 'edit', ['order:view'])
  assert.deepEqual(actions.map(action => [action.label, action.disabled]), [['删除', true], ['查看', false]])
})

test('page section resolves child only by configured relation key', () => {
  const children = [
    { key: 'items', relationKey: 'presale_items', modelCode: 'ps_presale_order_item' },
    { key: 'logs', relationKey: 'operation_logs', modelCode: 'ps_presale_operation_log' },
  ]

  assert.equal(resolvePageSectionChild({ relationKey: 'operation_logs' }, children)?.modelCode, 'ps_presale_operation_log')
  assert.equal(resolvePageSectionChild({ relationKey: 'missing' }, children), null)
})

test('flow interaction stays inert by default and applies node section policy when configured', () => {
  const empty = normalizeRuntimeFlowInteraction()
  assert.deepEqual(empty.approvalActions, [])
  assert.equal(empty.timeline.enabled, false)
  assert.deepEqual(mergeFlowActionsIntoBottomBar({ actions: [{ type: 'save', label: '保存' }] }, empty).actions, [
    { type: 'save', label: '保存' },
  ])

  const flow = normalizeRuntimeFlowInteraction({
    approvalActions: [{ actionId: 'approve', operation: 'approve', label: '同意' }],
    nodePermissions: [{ nodeKey: 'manager', visibleSectionIds: ['base'], readonlySectionIds: ['base'] }],
  })
  const sections = [{ sectionId: 'base' }, { sectionId: 'amount' }]

  assert.deepEqual(resolveVisiblePageSections(sections, 'detail', flow, 'manager').map(item => item.sectionId), ['base'])
  assert.equal(isPageSectionReadonly(sections[0], flow, 'manager'), true)
  assert.equal(mergeFlowActionsIntoBottomBar({}, flow).actions[0].type, 'flow_action')
})

test('pageSchema takes priority and extracts the mode-specific form zone', () => {
  const config = parseRuntimeConfig({
    options: {
      formDesignerSchema: { components: [{ label: '旧表单', fieldBinding: { fieldCode: 'legacy' } }] },
    },
    pageSchema: {
      zones: [
        {
          zoneId: 'main_form',
          zoneType: 'form',
          props: {
            formDesignerSchema: {
              components: [{ label: '新表单', fieldBinding: { fieldCode: 'current' } }],
            },
          },
        },
        { zoneId: 'actions', zoneType: 'actions', props: { actions: [{ type: 'save' }] } },
      ],
    },
  })

  assert.equal(hasComposedRuntimePageSchema(config), true)
  assert.deepEqual(resolveRuntimePageZones(config, 'create').map(zone => zone.zoneId), ['main_form', 'actions'])
  assert.deepEqual(normalizeMainFields(config, resolveRuntimeFormDesignerSchema(config, 'create')).map(field => field.field), ['current'])
})

test('legacy pageSchema edit zone remains compatible with the existing options fallback', () => {
  const config = parseRuntimeConfig({
    options: { formDesignerSchema: { components: [{ label: '旧表单', fieldBinding: { fieldCode: 'legacy' } }] } },
    pageSchema: {
      zones: [{ zoneKey: 'edit', componentKey: 'edit-form', props: {
        formDesignerSchema: { components: [{ label: '页面表单', fieldBinding: { fieldCode: 'page' } }] },
      } }],
    },
  })

  assert.equal(hasComposedRuntimePageSchema(config), false)
  assert.deepEqual(normalizeMainFields(config, resolveRuntimeFormDesignerSchema(config, 'edit')).map(field => field.field), ['page'])
})

test('runtime page normalization is idempotent and accepts serialized form schemas', () => {
  const config = parseRuntimeConfig({
    options: {
      pageSchema: JSON.stringify({
        zones: [{
          zoneId: 'main',
          zoneType: 'form',
          props: {
            formDesignerSchema: JSON.stringify({
              components: [{ label: '名称', fieldBinding: { fieldCode: 'name' } }],
            }),
          },
        }],
      }),
    },
  })
  const [zone] = resolveRuntimePageZones(config, 'create')

  assert.equal(hasComposedRuntimePageSchema(config), true)
  assert.equal(resolveRuntimeZoneFormDesignerSchema(zone).components[0].fieldBinding.fieldCode, 'name')
  assert.deepEqual(parseRuntimeConfig(null).pageSchema.zones, [])
})
