import { webcrypto } from 'node:crypto'
import { flushPromises, mount, shallowMount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, reactive } from 'vue'
import { useCapabilityOnlineTestStore } from '@/stores/capability/onlineTestStore'
import CapabilityTestIdentity from '../components/CapabilityTestIdentity.vue'
import { useCapabilityOnlineTest } from '../components/useCapabilityOnlineTest'

vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))
vi.mock('../capabilityCredentialSession', () => ({ getCapabilityCredential: () => null }))
let wrapper, helper, props, state
function deferred() {
  let resolve
  const promise = new Promise(done => resolve = done)
  return { resolve, promise }
}
const response = (body, status = 200) => ({ ok: status < 300, status, statusText: 'test', headers: new Headers(), text: async () => JSON.stringify(body) })
const guide = (overrides = {}) => ({ ready: true, availableAuthModes: ['OAUTH'], clientId: '123', capabilityCode: 'app.start', behavior: 'ACTION', invokeUrl: 'https://example.test/openapi/v1/capabilities/app.start/invoke', tokenUrl: 'https://example.test/oauth2/token', openapiResource: 'resource', requestExample: { recordId: '2100942720360046593' }, requestFields: [{ path: '$.recordId', fieldCode: 'recordId', type: 'string', required: true }], ...overrides })
async function start(overrides = {}) {
  props = reactive({ guide: guide(overrides) })
  wrapper = mount(defineComponent({ setup() {
    helper = useCapabilityOnlineTest(props)
    state = useCapabilityOnlineTestStore()
    return () => h('div')
  } }), { global: { plugins: [createPinia()] } })
  await nextTick()
  helper.credential.value = 'TEST-ONLY-SECRET'
}
beforeEach(() => {
  vi.clearAllMocks()
  window.$message = { success: vi.fn(), error: vi.fn() }
  window.$dialog = { warning: vi.fn(() => ({ destroy: vi.fn() })) }
  vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(response({ access_token: 'TEST-ONLY-TOKEN' })).mockResolvedValue(response({ data: { runId: 'run1' }, requestId: 'req1' })))
})
afterEach(() => {
  wrapper?.unmount()
  vi.unstubAllGlobals()
})
describe('online test guided execution', () => {
  it('uses plain radios for identity sources without segmented-button dividers', () => {
    const pinia = createPinia()
    const identity = useCapabilityOnlineTestStore(pinia)
    identity.guide = { userAssertionEnabled: true }
    const view = shallowMount(CapabilityTestIdentity, {
      props: {
        authOptions: [],
        credentialLabel: 'Secret',
        credentialPlaceholder: 'Secret',
        requiresSubjectToken: true,
      },
      global: {
        plugins: [pinia],
        stubs: {
          NRadioGroup: { template: '<div class="radio-group"><slot /></div>' },
          NRadio: { props: ['value'], template: '<label class="plain-radio"><slot /></label>' },
          NButton: true,
          NSelect: true,
          NInput: true,
        },
      },
    })
    expect(view.findAll('.plain-radio')).toHaveLength(2)
    expect(view.find('n-radio-button-stub').exists()).toBe(false)
    view.unmount()
  })
  it('validates identity before progressing and never calls automatically', async () => {
    await start()
    helper.credential.value = ''
    helper.continueToRequest()
    expect(state.stage).toBe('identity')
    expect(state.inputError).toContain('Secret')
    helper.credential.value = 'TEST-ONLY-SECRET'
    helper.continueToRequest()
    expect(state.stage).toBe('request')
    expect(fetch).not.toHaveBeenCalled()
  })
  it('requires confirmation for writes and handles duplicate clicks once', async () => {
    await start()
    helper.handleTest()
    helper.handleTest()
    expect(fetch).not.toHaveBeenCalled()
    expect(window.$dialog.warning).toHaveBeenCalledTimes(1)
    const confirmation = window.$dialog.warning.mock.calls[0][0]
    await Promise.all([confirmation.onPositiveClick(), confirmation.onPositiveClick()])
    await flushPromises()
    expect(fetch).toHaveBeenCalledTimes(2)
    expect(fetch.mock.calls[1][1].headers['Idempotency-Key']).toBeTruthy()
    expect(state.stage).toBe('result')
    expect(state.testReport.success).toBe(true)
    expect(JSON.stringify(state.testReport)).not.toContain('TEST-ONLY-SECRET')
    expect(JSON.stringify(state.testReport)).not.toContain('TEST-ONLY-TOKEN')
  })
  it('cancels without leaving a lock or accepting the old confirmation', async () => {
    await start()
    helper.handleTest()
    const confirmation = window.$dialog.warning.mock.calls[0][0]
    expect(confirmation.onNegativeClick()).toBeUndefined()
    expect(state.confirming).toBe(false)
    await confirmation.onPositiveClick()
    expect(fetch).not.toHaveBeenCalled()
  })
  it('does not execute unready, malformed or missing-field requests', async () => {
    await start({ ready: false })
    helper.handleTest()
    props.guide = guide()
    await nextTick()
    helper.credential.value = 'test'
    state.requestBody = '{}'
    helper.handleTest()
    expect(state.inputError).toContain('recordId')
    state.requestBody = '[]'
    helper.handleTest()
    expect(state.inputError).toContain('JSON 对象')
    expect(fetch).not.toHaveBeenCalled()
  })
  it('retains USER delegation requirements', async () => {
    await start({ tokenExchangeRequired: true })
    helper.continueToRequest()
    expect(state.inputError).toContain('OIDC')
    expect(state.stage).toBe('identity')
    state.subjectToken = 'TEST-ONLY-OIDC'
    helper.continueToRequest()
    expect(state.stage).toBe('request')
  })
  it('does not erase the client secret when changing only the user identity method', async () => {
    await start({ tokenExchangeRequired: true, userAssertionEnabled: true, userAssertionKeyId: 'test-key' })
    state.subjectTokenMode = 'OIDC'
    await nextTick()
    expect(state.credential).toBe('TEST-ONLY-SECRET')
    expect(state.subjectToken).toBe('')
  })
  it('prevents a late token response from invoking after close and clears secrets', async () => {
    await start({ behavior: 'READ_ONLY' })
    const token = deferred()
    fetch.mockReset().mockReturnValue(token.promise)
    const execution = helper.handleTest()
    wrapper.unmount()
    wrapper = null
    token.resolve(response({ access_token: 'late' }))
    await execution
    expect(fetch).toHaveBeenCalledTimes(1)
    expect(state.credential).toBe('')
    expect(state.testReport).toBeNull()
  })
  it('isolates an old token from a newly selected client and concurrent run', async () => {
    await start({ behavior: 'READ_ONLY' })
    const old = deferred()
    fetch.mockReset().mockReturnValueOnce(old.promise).mockResolvedValueOnce(response({ access_token: 'new' })).mockResolvedValue(response({ requestId: 'new-result' }))
    const earlier = helper.handleTest()
    props.guide = guide({ behavior: 'READ_ONLY', clientId: 'new' })
    await nextTick()
    state.credential = 'new-secret'
    const latest = helper.handleTest()
    old.resolve(response({ access_token: 'old' }))
    await Promise.all([earlier, latest])
    expect(fetch).toHaveBeenCalledTimes(3)
    expect(fetch.mock.calls[2][1].headers.Authorization).toBe('Bearer new')
    expect(state.testReport.clientId).toBe('new')
  })
  it('does not invoke after token failure and shows a recoverable result', async () => {
    await start({ behavior: 'READ_ONLY' })
    fetch.mockReset().mockResolvedValue(response({ error: 'invalid_client' }, 401))
    await helper.handleTest()
    expect(fetch).toHaveBeenCalledTimes(1)
    expect(state.testReport.tokenExchange.response.status).toBe(401)
    expect(state.testReport.invocation).toBeNull()
    expect(state.stage).toBe('result')
  })
  it('does not retry network errors and sanitizes credential-like business fields', async () => {
    await start({ behavior: 'READ_ONLY' })
    fetch.mockReset().mockRejectedValue(new TypeError('Failed to fetch'))
    await helper.handleTest()
    await flushPromises()
    expect(fetch).toHaveBeenCalledTimes(1)
    expect(state.testReport.success).toBe(false)
    expect(helper.redactSensitive({ data: { privateKey: 'private', password: 'pw' }, recordId: 'id' })).toEqual({ data: { privateKey: '<REDACTED>', password: '<REDACTED>' }, recordId: 'id' })
  })
  it('keeps HMAC signing and safe reports without requesting a token', async () => {
    vi.stubGlobal('crypto', webcrypto)
    await start({ availableAuthModes: ['HMAC'], behavior: 'READ_ONLY' })
    fetch.mockReset().mockResolvedValue(response({ requestId: 'hmac-result' }))
    await helper.handleTest()
    expect(fetch).toHaveBeenCalledTimes(1)
    expect(fetch.mock.calls[0][1].headers['X-Forge-Signature']).toMatch(/^[a-f0-9]{64}$/)
    expect(fetch.mock.calls[0][1].headers.Authorization).toBeUndefined()
    expect(state.testReport.invocation.request.headers['X-Forge-Signature']).toBe('<REDACTED>')
    expect(state.testReport.success).toBe(true)
  })
  it('rejects confirmation after the request body has changed without leaving controls locked', async () => {
    await start()
    helper.handleTest()
    const confirmation = window.$dialog.warning.mock.calls[0][0]
    state.requestBody = '{"recordId":"different"}'
    confirmation.onPositiveClick()
    await flushPromises()
    expect(fetch).not.toHaveBeenCalled()
    expect(state.confirming).toBe(false)
  })
})
