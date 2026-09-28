import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useFlowModel } from '../useFlowModel'

const mocks = vi.hoisted(() => ({
  getLabel: vi.fn(),
  push: vi.fn(),
}))

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    routes: [],
    useRouter: () => ({ push: mocks.push }),
  }
})

vi.mock('vue', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    onMounted: vi.fn(),
  }
})

vi.mock('@/composables/useDict', () => ({
  useDict: () => ({
    dict: {
      value: {
        flow_designer_type: [],
        flow_model_status: [],
        flow_process_form_type: [],
      },
    },
    getLabel: mocks.getLabel,
  }),
}))

describe('useFlowModel split contract', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('restores shared setup dependencies and delegates part two actions', () => {
    const state = useFlowModel()

    expect(state.FlowDesignAsyncLoader.name).toBe('FlowDesignAsyncLoader')
    expect(state.FlowDesignPage).toBeTruthy()
    expect(state.getLabel).toBe(mocks.getLabel)
    expect(state.handleVersionHistory).toBeTypeOf('function')

    state.handleActionSelect('versionHistory', { id: 'model-1', version: 3 })
    expect(state.currentModelId.value).toBe('model-1')
    expect(state.currentModelVersion.value).toBe(3)
    expect(state.showVersionHistory.value).toBe(true)
  })
})
