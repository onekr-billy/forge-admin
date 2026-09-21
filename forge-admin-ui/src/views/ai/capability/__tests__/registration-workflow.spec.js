import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, reactive, ref } from 'vue'
import { useCapabilityRegistration } from '../components/useCapabilityRegistration'

const api = vi.hoisted(() => Object.fromEntries([
  'getBusinessActionRegistrationSource',
  'getCapabilityVersionDraft',
  'getFlowActionRegistrationSource',
  'getSystemServiceRegistrationSources',
  'publishBusinessActionCapability',
  'publishFlowActionCapability',
  'publishSystemServiceCapability',
  'businessObjectList',
].map(name => [name, vi.fn()])))
vi.mock('@/api/ai/capability', () => api)
vi.mock('@/api/business-app', () => api)
vi.mock('vue-router', () => ({ useRouter: () => ({ resolve: () => ({ href: '/' }) }) }))
vi.mock('@/composables', () => ({ useDict: () => ({
  dict: ref({ ai_capability_flow_operation: ['SUBMIT', 'START', 'APPROVE', 'REJECT', 'WITHDRAW'].map(value => ({ value, label: value })) }),
  errors: ref({}),
  loading: ref(false),
  reload: vi.fn().mockResolvedValue(),
}) }))

const objects = ['a', 'b'].map((code, index) => ({ id: String(index + 1), suiteCode: 'law', objectCode: code, status: 1, designStatus: 'CHANGED', lastPublishVersion: 1 }))
const services = [
  { serviceCode: 'flow.process.start', serviceName: '独立流程', options: { models: [] } },
  { serviceCode: 'system.rest.invoke', serviceName: 'REST 接口', options: { registrationKind: 'REST', endpoints: [
    { id: 'abc123456789', name: '字典选项', method: 'GET', path: '/system/dict/data/type/{dictType}', available: true },
  ] } },
  { serviceCode: 'lowcode.form.create', serviceName: '表单填报', options: { registrationKind: 'FORM', forms: [
    { id: 'law/a', objectId: '1', suiteCode: 'law', objectCode: 'a', name: '登记', available: true, fields: [{ field: 'title', label: '标题', required: true }, { field: 'note', label: '说明' }] },
  ] } },
]
let wrapper
let state
let props
let emit
async function start(overrides = {}) {
  props = reactive({ show: true, allowedTypes: ['BUSINESS_ACTION', 'FLOW_ACTION', 'SYSTEM_SERVICE'], capability: null, initialContext: {}, ...overrides })
  emit = vi.fn()
  wrapper = mount(defineComponent({
    setup() {
      state = useCapabilityRegistration(props, emit)
      return () => null
    },
  }), { global: { plugins: [createPinia()] } })
  await flushPromises()
  state.formRef.value = { validate: vi.fn().mockResolvedValue() }
}
beforeEach(() => {
  vi.clearAllMocks()
  setActivePinia(createPinia())
  window.$message = { error: vi.fn(), success: vi.fn(), warning: vi.fn() }
  api.businessObjectList.mockResolvedValue({ data: objects })
  api.getSystemServiceRegistrationSources.mockResolvedValue({ data: services })
  api.publishSystemServiceCapability.mockResolvedValue({ code: 200, data: '88' })
  api.getFlowActionRegistrationSource.mockResolvedValue({ data: { flowModelKey: 'approval', startSupported: true, submissionSupported: true, submissionFields: [{ field: 'title', required: true }] } })
})
afterEach(() => wrapper?.unmount())

describe('scenario registration', () => {
  it('defaults application scenario to form filling and only shows matching services', async () => {
    await start()
    await state.chooseScenario('application')
    expect(state.form.systemServiceCode).toBe('lowcode.form.create')
    expect(state.systemServiceOptions.value.map(item => item.value)).toEqual(['lowcode.form.create'])
    state.form.systemFormId = 'law/a'
    state.handleSystemFormChange()
    await flushPromises()
    expect(state.form.requiredFields).toEqual(['title'])
    expect(state.form.allowedFields).toEqual(['title', 'note'])
    await state.nextStep()
    expect(state.step.value).toBe(3)
    await state.handleSubmit()
    expect(api.publishSystemServiceCapability).toHaveBeenCalledWith(expect.objectContaining({
      parameters: { suiteCode: 'law', objectCode: 'a', allowedFields: ['title', 'note'], requiredFields: ['title'] },
    }))
  })
  it('publishes a REST endpoint without requiring a workflow model', async () => {
    await start()
    await state.chooseScenario('rest')
    expect(state.submitDisabled.value).toBe(true)
    state.form.systemEndpointId = 'abc123456789'
    state.updateGeneratedCode()
    expect(state.submitDisabled.value).toBe(false)
    await state.nextStep()
    await state.handleSubmit()
    expect(api.publishSystemServiceCapability).toHaveBeenCalledWith(expect.objectContaining({
      serviceCode: 'system.rest.invoke',
      parameters: { endpointId: 'abc123456789' },
    }))
  })
  it('accepts the existing published snapshot even when the designer has changes', async () => {
    await start()
    expect(state.objects.value).toHaveLength(2)
  })
  it('does not overwrite a newer object selection with an old source response', async () => {
    await start()
    let resolveFirst
    api.getFlowActionRegistrationSource.mockImplementationOnce(() => new Promise((resolve) => {
      resolveFirst = resolve
    }))
    const first = state.handleObjectChange('1')
    await state.handleObjectChange('2')
    resolveFirst({ data: { flowModelKey: 'old', startSupported: true, submissionSupported: true } })
    await first
    expect(state.form.objectCode).toBe('b')
    expect(state.flowSource.value.flowModelKey).toBe('approval')
  })
  it('blocks duplicate publication while the first request is pending', async () => {
    await start()
    await state.chooseScenario('rest')
    state.form.systemEndpointId = 'abc123456789'
    state.updateGeneratedCode()
    let resolve
    api.publishSystemServiceCapability.mockImplementationOnce(() => new Promise((done) => {
      resolve = done
    }))
    const first = state.handleSubmit()
    await flushPromises()
    await state.handleSubmit()
    expect(api.publishSystemServiceCapability).toHaveBeenCalledTimes(1)
    resolve({ code: 200, data: '99' })
    await first
  })
  it('validates generated technical fields even when their collapsed UI is not mounted', async () => {
    await start()
    await state.chooseScenario('rest')
    state.form.systemEndpointId = 'abc123456789'
    state.form.capabilityCode = ''
    await state.nextStep()
    expect(state.step.value).toBe(2)
    expect(window.$message.error).toHaveBeenCalled()
  })
  it('blocks a second click while form validation is still pending', async () => {
    await start()
    await state.chooseScenario('rest')
    state.form.systemEndpointId = 'abc123456789'
    state.updateGeneratedCode()
    let validate
    state.formRef.value.validate.mockImplementationOnce(() => new Promise((done) => {
      validate = done
    }))
    const first = state.handleSubmit()
    await state.handleSubmit()
    expect(api.publishSystemServiceCapability).not.toHaveBeenCalled()
    validate()
    await first
    expect(api.publishSystemServiceCapability).toHaveBeenCalledTimes(1)
  })
  it('ignores an old draft after closing and reopening for a new registration', async () => {
    let oldDraft
    api.getCapabilityVersionDraft.mockImplementationOnce(() => new Promise((done) => {
      oldDraft = done
    }))
    await start({ capability: { id: '88', sourceKey: 'system.rest.invoke' } })
    props.show = false
    await flushPromises()
    props.capability = null
    props.show = true
    await flushPromises()
    oldDraft({ data: { sourceType: 'SYSTEM_SERVICE', capabilityCode: 'old.code', suggestedVersion: '2.0.0' } })
    await flushPromises()
    expect(state.form.capabilityCode).not.toBe('old.code')
    expect(state.draftLoading.value).toBe(false)
    expect(state.step.value).toBe(1)
    expect(state.sourceError.value).toBe('')
  })
  it('restores REST upgrade parameters without selecting a flow model', async () => {
    api.getCapabilityVersionDraft.mockResolvedValue({ data: {
      sourceType: 'SYSTEM_SERVICE',
      sourceKey: 'system.rest.invoke',
      capabilityCode: 'rest.dictionary',
      suggestedVersion: '1.0.1',
      policySnapshot: { registrationParameters: { endpointId: 'abc123456789' } },
    } })
    await start({ capability: { id: '88', sourceKey: 'system.rest.invoke', currentVersion: '1.0.0' } })
    expect(state.form.systemEndpointId).toBe('abc123456789')
    expect(state.scenario.value).toBe('rest')
    expect(state.sourceError.value).toBe('')
    expect(state.submitDisabled.value).toBe(false)
  })
})
