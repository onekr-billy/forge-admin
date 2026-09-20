import { describe, expect, it, vi } from 'vitest'
import { useFlowActionFeedback } from '../crud/composables/useFlowActionFeedback'

function createFeedback(overrides = {}) {
  const state = {
    dataSource: { value: [{ id: '101' }, { id: '101', __listRowKey: '101:child-2' }, { id: '102' }] },
    currentRow: { value: { id: '101' } },
    detailRuntime: { value: null },
    formData: { value: { id: '101', title: '采购单' } },
  }
  const dependencies = {
    ...state,
    resolveRowKeyValue: row => row?.id,
    resolveDocumentRuntime: row => row?._documentRuntime || null,
    resolveRuntimeObjectCode: () => 'purchase_order',
    refreshCurrentDetailRuntime: vi.fn().mockResolvedValue(undefined),
    loadList: vi.fn().mockResolvedValue(undefined),
    ...overrides,
  }
  return {
    dependencies,
    feedback: useFlowActionFeedback(dependencies),
    state,
  }
}

describe('flow action feedback', () => {
  it('updates every rendered row for the same main record and the open detail snapshot', () => {
    const { feedback, state } = createFeedback()

    feedback.applyDocumentRuntimeSnapshot(state.dataSource.value[0], {
      flowStatus: 'IN_PROCESS',
      processInstanceId: 'process-1',
    }, 'purchase_order')

    expect(state.dataSource.value[0]._documentRuntime.flowStatus).toBe('IN_PROCESS')
    expect(state.dataSource.value[1]._documentRuntime.flowStatus).toBe('IN_PROCESS')
    expect(state.dataSource.value[2]._documentRuntime).toBeUndefined()
    expect(state.currentRow.value._documentRuntime.processInstanceId).toBe('process-1')
    expect(state.detailRuntime.value.flowStatus).toBe('IN_PROCESS')
    expect(state.formData.value.title).toBe('采购单')
    expect(state.formData.value._runtimeObjectCode).toBe('purchase_order')
  })

  it('optimistically occupies only the started application process entry', () => {
    const { feedback, state } = createFeedback()

    feedback.applyApplicationProcessRuntimeSnapshot(state.dataSource.value[0], 'submit_approval', {
      status: 'RUNNING',
    })

    expect(state.dataSource.value[0]._documentRuntime.activeProcessCodes).toEqual(['submit_approval'])
    expect(state.dataSource.value[0]._documentRuntime.startedProcessCodes).toEqual(['submit_approval'])
  })

  it('runs detail and list reconciliation after scheduling and supports disposal', async () => {
    let scheduledTask
    const clearSchedule = vi.fn()
    const schedule = vi.fn((task) => {
      scheduledTask = task
      return 23
    })
    const { feedback, dependencies } = createFeedback({ schedule, clearSchedule })

    feedback.queueFlowRuntimeRefresh({ id: '101' })

    expect(dependencies.refreshCurrentDetailRuntime).not.toHaveBeenCalled()
    expect(dependencies.loadList).not.toHaveBeenCalled()

    await scheduledTask()

    expect(dependencies.refreshCurrentDetailRuntime).toHaveBeenCalledWith({ id: '101' })
    expect(dependencies.loadList).toHaveBeenCalledTimes(1)

    feedback.queueFlowRuntimeRefresh({ id: '102' })
    feedback.disposeFlowRuntimeRefresh()
    expect(clearSchedule).toHaveBeenCalledWith(23)
  })
})
