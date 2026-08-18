import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  buildFieldEventParams,
  createFieldEventRuntime,
  mapFieldEventResult,
  normalizeFieldEventRules,
} from '../field-event-runtime'

function buildRule(overrides = {}) {
  return {
    id: 'query_contact',
    name: '查询联系人',
    enabled: true,
    trigger: 'CHANGE',
    sourceField: 'mobile',
    sourceType: 'EXTERNAL_API',
    sourceKey: 'crm/contact_lookup',
    debounceMs: 300,
    skipWhenEmpty: true,
    clearTargetsOnTrigger: true,
    paramMappings: [
      { param: 'mobile', source: 'FORM_FIELD', field: 'mobile' },
    ],
    resultMode: 'ROOT',
    resultMappings: [
      { from: 'contact.name', to: 'contactName', whenMissing: 'CLEAR' },
    ],
    notFoundMessage: '未匹配到数据',
    errorMessage: '查询失败，请重试',
    errorMode: 'MESSAGE',
    ...overrides,
  }
}

describe('field event protocol', () => {
  it('normalizes a valid rule and strips undeclared properties', () => {
    const [rule] = normalizeFieldEventRules([
      buildRule({ arbitraryClientConfig: 'ignored' }),
    ], ['mobile', 'contactName'])

    expect(rule).toMatchObject({
      id: 'query_contact',
      trigger: 'CHANGE',
      sourceType: 'EXTERNAL_API',
      debounceMs: 300,
    })
    expect(rule).not.toHaveProperty('arbitraryClientConfig')
  })

  it('fails closed for unknown protocol values and dangerous nested keys', () => {
    const rules = normalizeFieldEventRules([
      buildRule({ id: 'bad_trigger', trigger: 'CLICK' }),
      buildRule({ id: 'bad_source', sourceType: 'REMOTE_URL' }),
      buildRule({ id: 'bad_param', paramMappings: [{ param: 'mobile', source: 'SCRIPT', field: 'mobile' }] }),
      buildRule({ id: 'bad_secret', extension: { authorization: 'secret' } }),
    ], ['mobile', 'contactName'])

    expect(rules).toEqual([])
  })

  it('builds only explicitly mapped form, context and route parameters', () => {
    const rule = buildRule({
      trigger: 'FORM_LOAD',
      sourceField: '',
      paramMappings: [
        { param: 'mobile', source: 'FORM_FIELD', field: 'mobile' },
        { param: 'operator', source: 'CONTEXT_PATH', path: 'currentUser.userId' },
        { param: 'channel', source: 'ROUTE_QUERY', path: 'channel' },
      ],
    })

    expect(buildFieldEventParams(rule, {
      formData: { mobile: '13800000000', hiddenSecret: 'never-forward' },
      context: { currentUser: { userId: 'u-1', tenantId: 9 } },
      routeQuery: { channel: 'wecom', token: 'never-forward' },
    })).toEqual({
      mobile: '13800000000',
      operator: 'u-1',
      channel: 'wecom',
    })
  })

  it('maps ROOT and FIRST_ROW results with CLEAR and KEEP semantics', () => {
    expect(mapFieldEventResult(buildRule(), {
      contact: { name: '张三' },
    })).toEqual({ found: true, patch: { contactName: '张三' } })

    const firstRowRule = buildRule({
      resultMode: 'FIRST_ROW',
      resultMappings: [
        { from: 'name', to: 'contactName', whenMissing: 'CLEAR' },
        { from: 'level', to: 'contactLevel', whenMissing: 'KEEP' },
      ],
    })
    expect(mapFieldEventResult(firstRowRule, {
      records: [{ name: '李四' }],
    })).toEqual({ found: true, patch: { contactName: '李四' } })
    expect(mapFieldEventResult(firstRowRule, { records: [] })).toEqual({
      found: false,
      patch: { contactName: undefined },
    })
  })

  it('blocks prototype traversal paths', () => {
    const rule = buildRule({
      paramMappings: [{ param: 'polluted', source: 'CONTEXT_PATH', path: '__proto__.polluted' }],
    })
    expect(normalizeFieldEventRules([rule], ['mobile', 'contactName'])).toEqual([])
  })

  it('passes only an explicit normalized scan context to declared parameters', async () => {
    const formData = { mobile: '' }
    const execute = vi.fn(async payload => ({ data: { data: { contact: { name: payload.params.code } } } }))
    const patches = []
    const runtime = createFieldEventRuntime({
      rules: [buildRule({
        id: 'scan_contact',
        trigger: 'SCAN_COMPLETE',
        paramMappings: [
          { param: 'code', source: 'CONTEXT_PATH', path: 'scan.value' },
          { param: 'mobile', source: 'FORM_FIELD', field: 'mobile' },
        ],
        skipWhenEmpty: false,
        resultMappings: [{ from: 'contact.name', to: 'contactName', whenMissing: 'CLEAR' }],
      })],
      fields: ['mobile', 'contactName'],
      execute,
      getFormData: () => formData,
      getContext: () => ({ currentUser: { userId: 'trusted' }, userId: 'spoofed' }),
      applyPatch: patch => patches.push(patch),
    })

    await runtime.dispatch('SCAN_COMPLETE', 'mobile', {
      scan: { value: 'SKU-001', type: 'barCode', platform: 'H5' },
      userId: 'spoofed',
    })

    expect(execute).toHaveBeenCalledWith(expect.objectContaining({
      params: { code: 'SKU-001', mobile: '' },
    }), expect.anything())
    expect(patches).toContainEqual({ contactName: 'SKU-001' })
  })

  it('passes the configured page window when the source is a dataset', async () => {
    const execute = vi.fn(async () => ({ data: { records: [{ name: '李四' }] } }))
    const runtime = createFieldEventRuntime({
      rules: [buildRule({
        sourceType: 'DATASET',
        sourceKey: 'member_dataset',
        pageNum: 2,
        pageSize: 15,
        maxRows: 15,
      })],
      fields: ['mobile', 'contactName'],
      execute,
      getFormData: () => ({ mobile: '13800000000' }),
    })

    await runtime.dispatch('CHANGE', 'mobile')

    expect(execute).toHaveBeenCalledWith(expect.objectContaining({
      sourceType: 'DATASET',
      pageNum: 2,
      pageSize: 15,
      maxRows: 15,
    }), expect.anything())
  })

  it('uses page size as the dataset limit when max rows is omitted', async () => {
    const execute = vi.fn(async () => ({ data: { records: [{ name: '李四' }] } }))
    const runtime = createFieldEventRuntime({
      rules: [buildRule({
        sourceType: 'DATASET',
        sourceKey: 'member_dataset',
        pageSize: 50,
        maxRows: undefined,
      })],
      fields: ['mobile', 'contactName'],
      execute,
      getFormData: () => ({ mobile: '13800000000' }),
    })

    await runtime.dispatch('CHANGE', 'mobile')

    expect(execute.mock.calls[0][0]).toMatchObject({
      pageSize: 50,
      maxRows: 50,
    })
  })
})

describe('field event runtime concurrency', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('debounces CHANGE and executes only the last form value', async () => {
    let formData = { mobile: '1' }
    const execute = vi.fn(async payload => ({ data: { data: { contact: { name: payload.params.mobile } } } }))
    const patches = []
    const runtime = createFieldEventRuntime({
      rules: [buildRule()],
      fields: ['mobile', 'contactName'],
      execute,
      getFormData: () => formData,
      applyPatch: patch => patches.push(patch),
    })

    const first = runtime.dispatch('CHANGE', 'mobile')
    formData = { mobile: '2' }
    const second = runtime.dispatch('CHANGE', 'mobile')
    await vi.advanceTimersByTimeAsync(300)
    await Promise.all([first, second])

    expect(execute).toHaveBeenCalledTimes(1)
    expect(execute.mock.calls[0][0].params).toEqual({ mobile: '2' })
    expect(patches).toContainEqual({ contactName: '2' })
  })

  it('aborts the previous request and ignores its late response', async () => {
    const resolvers = []
    const signals = []
    let formData = { mobile: 'first' }
    const execute = vi.fn((payload, options) => {
      signals.push(options.signal)
      return new Promise(resolve => resolvers.push({ payload, resolve }))
    })
    const patches = []
    const states = []
    const runtime = createFieldEventRuntime({
      rules: [buildRule({ debounceMs: 0 })],
      fields: ['mobile', 'contactName'],
      execute,
      getFormData: () => formData,
      applyPatch: patch => patches.push(patch),
      onStateChange: state => states.push(state),
    })

    const first = runtime.dispatch('CHANGE', 'mobile')
    await Promise.resolve()
    formData = { mobile: 'second' }
    const second = runtime.dispatch('CHANGE', 'mobile')
    await Promise.resolve()

    expect(signals[0].aborted).toBe(true)
    resolvers[1].resolve({ data: { data: { contact: { name: '新结果' } } } })
    await second
    resolvers[0].resolve({ data: { data: { contact: { name: '旧结果' } } } })
    await first

    expect(patches).toContainEqual({ contactName: '新结果' })
    expect(patches).not.toContainEqual({ contactName: '旧结果' })
    expect(states.at(-1)).toMatchObject({ status: 'success', ruleId: 'query_contact' })
  })

  it('skips empty input, clears targets and disposes pending work', async () => {
    const execute = vi.fn()
    const patches = []
    const runtime = createFieldEventRuntime({
      rules: [buildRule()],
      fields: ['mobile', 'contactName'],
      execute,
      getFormData: () => ({ mobile: '' }),
      applyPatch: patch => patches.push(patch),
    })

    await runtime.dispatch('CHANGE', 'mobile')
    expect(execute).not.toHaveBeenCalled()
    expect(patches).toEqual([{ contactName: undefined }])

    runtime.dispose()
    await vi.runAllTimersAsync()
    expect(execute).not.toHaveBeenCalled()
  })
})
