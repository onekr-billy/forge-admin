import { describe, expect, it, vi } from 'vitest'
import { loadTaskFormBundle } from '../task-form-bundle'

describe('loadTaskFormBundle', () => {
  it('reuses the task form snapshot returned with the business context', async () => {
    const taskFormInfo = { taskId: 'task-1', allowApprove: true }
    const loadBusinessContext = vi.fn().mockResolvedValue({
      configured: true,
      formType: 'business-object',
      taskFormInfo,
    })
    const loadFlowFormInfo = vi.fn()

    const result = await loadTaskFormBundle({
      row: { taskId: 'task-1' },
      loadBusinessContext,
      loadFlowFormInfo,
      isConfiguredBusinessContext: context => context?.configured === true,
    })

    expect(result.formInfo).toBe(taskFormInfo)
    expect(loadBusinessContext).toHaveBeenCalledTimes(1)
    expect(loadFlowFormInfo).not.toHaveBeenCalled()
  })

  it('keeps the legacy direct-flow fallback and retries unresolved business context', async () => {
    const formInfo = { taskId: 'task-2', formKey: 'order-form' }
    const loadBusinessContext = vi.fn()
      .mockResolvedValueOnce({ configured: false })
      .mockResolvedValueOnce({ configured: true, formType: 'business-object' })
    const loadFlowFormInfo = vi.fn().mockResolvedValue(formInfo)

    const result = await loadTaskFormBundle({
      row: { taskId: 'task-2' },
      loadBusinessContext,
      loadFlowFormInfo,
      isConfiguredBusinessContext: context => context?.configured === true,
    })

    expect(result.formInfo).toBe(formInfo)
    expect(loadFlowFormInfo).toHaveBeenCalledTimes(1)
    expect(loadBusinessContext).toHaveBeenNthCalledWith(2, { taskId: 'task-2' }, formInfo)
  })
})
