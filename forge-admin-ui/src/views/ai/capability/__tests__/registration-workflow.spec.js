import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, reactive, ref } from 'vue'
import { useCapabilityRegistrationStore } from '@/stores/capability/registrationStore'
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
const applicationApi = vi.hoisted(() => ({ publishApplicationCapability: vi.fn() }))
vi.mock('@/api/ai/capability', () => api)
vi.mock('@/api/business-app', () => api)
vi.mock('@/api/application-integration', () => applicationApi)
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
  applicationApi.publishApplicationCapability.mockResolvedValue({ code: 200, data: '88' })
  api.getFlowActionRegistrationSource.mockResolvedValue({ data: { flowModelKey: 'approval', startSupported: true, submissionSupported: true, submissionFields: [{ field: 'title', required: true }] } })
})
afterEach(() => wrapper?.unmount())

describe('scenario registration', () => {
  it('defaults application scenario to form filling and only shows matching services', async () => {
    await start()
    await state.chooseScenario('application')
    expect(state.form.systemServiceCode).toBe('lowcode.form.create')
    expect(api.getSystemServiceRegistrationSources).not.toHaveBeenCalled()
    useCapabilityRegistrationStore().applicationId = '11'
    await state.handleSystemPageSelect({ objectId: '1' })
    expect(api.getSystemServiceRegistrationSources).toHaveBeenCalledWith({
      serviceCode: 'lowcode.form.create',
      applicationId: '11',
      objectId: '1',
    })
    expect(state.systemServiceOptions.value.map(item => item.value)).toEqual(['lowcode.form.create'])
    await flushPromises()
    expect(state.form.requiredFields).toEqual(['title'])
    expect(state.form.allowedFields).toEqual(['title', 'note'])
    await state.nextStep()
    expect(state.step.value).toBe(3)
    await state.handleSubmit()
    expect(applicationApi.publishApplicationCapability).toHaveBeenCalledWith('11', 'system', expect.objectContaining({
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
  it('does not expose legacy object approval as a new registration route', async () => {
    await start({ allowedTypes: ['FLOW_ACTION'] })
    expect(api.businessObjectList).not.toHaveBeenCalled()
    expect(api.getSystemServiceRegistrationSources).not.toHaveBeenCalled()
    await state.chooseScenario('flow')
    expect(state.step.value).toBe(1)
    expect(state.form.sourceType).toBe('FLOW_ACTION')
  })
  it('does not overwrite a newer page selection with an old form source response', async () => {
    await start()
    await state.chooseScenario('application')
    useCapabilityRegistrationStore().applicationId = '11'
    let resolveFirst
    api.getSystemServiceRegistrationSources.mockImplementationOnce(() => new Promise((resolve) => {
      resolveFirst = resolve
    }))
    api.getSystemServiceRegistrationSources.mockResolvedValueOnce({ data: [{
      serviceCode: 'lowcode.form.create',
      serviceName: '表单填报',
      options: { registrationKind: 'FORM', forms: [{ id: 'law/b', objectId: '2', suiteCode: 'law', objectCode: 'b', available: true, fields: [{ field: 'name' }] }] },
    }] })
    const first = state.handleSystemPageSelect({ objectId: '1' })
    await state.handleSystemPageSelect({ objectId: '2' })
    resolveFirst({ data: services.filter(item => item.serviceCode === 'lowcode.form.create') })
    await first
    expect(state.form.objectId).toBe('2')
    expect(state.form.systemFormId).toBe('law/b')
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
  it('enters the loading step immediately and requests only the chosen service', async () => {
    await start()
    let complete
    api.getSystemServiceRegistrationSources.mockImplementationOnce(() => new Promise((resolve) => {
      complete = resolve
    }))
    const loading = state.chooseScenario('rest')
    expect(state.step.value).toBe(2)
    expect(state.sourceLoading.value).toBe(true)
    expect(state.systemKind.value).toBe('REST')
    expect(state.submitDisabled.value).toBe(true)
    expect(api.getSystemServiceRegistrationSources).toHaveBeenLastCalledWith({ serviceCode: 'system.rest.invoke' })
    complete({ data: services })
    await loading
    expect(state.sourceLoading.value).toBe(false)
  })
  it('does not overwrite a newer scenario or reopen a closed wizard on late completion', async () => {
    await start()
    let complete
    api.getSystemServiceRegistrationSources.mockImplementationOnce(() => new Promise((resolve) => {
      complete = resolve
    }))
    const old = state.chooseScenario('rest')
    await state.chooseScenario('application')
    complete({ data: services.filter(item => item.serviceCode === 'system.rest.invoke') })
    await old
    expect(state.form.systemServiceCode).toBe('lowcode.form.create')
    expect(state.sourceLoading.value).toBe(false)
    api.getSystemServiceRegistrationSources.mockImplementationOnce(() => new Promise((resolve) => {
      complete = resolve
    }))
    const closing = state.chooseScenario('rest')
    props.show = false
    await flushPromises()
    props.show = true
    await flushPromises()
    complete({ data: services })
    await closing
    expect(state.step.value).toBe(1)
    expect(state.systemServices.value).toEqual([])
    expect(state.sourceLoading.value).toBe(false)
  })
  it('shows failed loading and can retry without leaving the configuration step', async () => {
    await start()
    api.getSystemServiceRegistrationSources.mockRejectedValueOnce(new Error('来源查询失败'))
    await state.chooseScenario('rest')
    expect(state.step.value).toBe(2)
    expect(state.sourceLoading.value).toBe(false)
    expect(state.sourceError.value).toBe('来源查询失败')
    expect(state.submitDisabled.value).toBe(true)
    await state.loadSystemServices()
    expect(state.sourceError.value).toBe('')
    expect(state.form.systemServiceCode).toBe('system.rest.invoke')
  })
  it('defaults locked application registration to form filling without a scenario round-trip', async () => {
    await start({ initialContext: { lockApplication: true, applicationId: '9007199254740999', applicationCode: 'law', applicationName: '案件管理' } })
    expect(state.step.value).toBe(2)
    expect(state.scenario.value).toBe('application')
    expect(state.form.systemServiceCode).toBe('lowcode.form.create')
    expect(api.businessObjectList).not.toHaveBeenCalled()
    expect(api.getSystemServiceRegistrationSources).not.toHaveBeenCalled()
  })

  it('defaults flow registration to the application process and publishes its exact source', async () => {
    await start()
    await state.chooseScenario('flow')
    expect(state.form.sourceType).toBe('APPLICATION_PROCESS')
    useCapabilityRegistrationStore().applicationId = '9007199254740999'
    api.getSystemServiceRegistrationSources.mockResolvedValue({ data: [{ serviceCode: 'lowcode.business-process.start', options: { processes: [
      { processCode: 'case_approval', name: '合同审批', version: 3, available: true },
    ] } }] })
    await state.applicationProcess.selectPage({ objectId: '9007199254741999' })
    await flushPromises()
    expect(api.businessObjectList).not.toHaveBeenCalled()
    expect(api.getFlowActionRegistrationSource).not.toHaveBeenCalled()
    expect(state.submitDisabled.value).toBe(false)
    await state.handleSubmit()
    expect(applicationApi.publishApplicationCapability).toHaveBeenCalledWith('9007199254740999', 'system', expect.objectContaining({
      serviceCode: 'lowcode.business-process.start',
      parameters: { applicationId: '9007199254740999', objectId: '9007199254741999', processCode: 'case_approval' },
    }))
  })

  it('restores an application process upgrade without using the legacy workflow source', async () => {
    api.getCapabilityVersionDraft.mockResolvedValue({ data: {
      sourceType: 'SYSTEM_SERVICE',
      sourceKey: 'lowcode.business-process.start',
      capabilityCode: 'app.legal.start',
      suggestedVersion: '1.0.1',
      policySnapshot: { registrationParameters: { applicationId: '11', objectId: '5', processCode: 'case_approval' } },
    } })
    api.getSystemServiceRegistrationSources.mockResolvedValue({ data: [{ serviceCode: 'lowcode.business-process.start', options: { processes: [
      { processCode: 'case_approval', name: '合同审批', version: 3, available: true },
    ] } }] })
    await start({ capability: { id: '88', sourceKey: 'lowcode.business-process.start', currentVersion: '1.0.0' } })
    expect(state.form.sourceType).toBe('APPLICATION_PROCESS')
    expect(state.form.capabilityCode).toBe('app.legal.start')
    expect(state.applicationProcess.selected.value.processCode).toBe('case_approval')
    expect(state.submitDisabled.value).toBe(false)
    expect(api.getFlowActionRegistrationSource).not.toHaveBeenCalled()
  })
})
