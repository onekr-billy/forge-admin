import { describe, expect, it, vi } from 'vitest'
import { useRecordFormLoader } from '../crud/composables/useRecordFormLoader'

function deferred() {
  let resolve
  const promise = new Promise((done) => {
    resolve = done
  })
  return { promise, resolve }
}

function fixture(overrides = {}) {
  const dependencies = {
    loadDetailOnEdit: () => true,
    loadDetail: vi.fn().mockResolvedValue(true),
    callHook: vi.fn(async (_, row) => row),
    applyDetailData: vi.fn(),
    fetchRuntime: vi.fn().mockResolvedValue({ data: { flowStatus: 'IN_PROCESS' } }),
    resolveRuntimeObjectCode: () => 'order',
    resolveRowKeyValue: row => row?.id,
    detailRuntime: { value: null },
    detailRuntimeLoading: { value: false },
    showLoading: vi.fn(),
    closeLoading: vi.fn(),
    warn: vi.fn(),
    ...overrides,
  }
  return { ...dependencies, ...useRecordFormLoader(dependencies) }
}

describe('record form loading', () => {
  it('starts independent requests together and waits for both', async () => {
    const detail = deferred()
    const runtime = deferred()
    const state = fixture({ loadDetail: vi.fn(() => detail.promise), fetchRuntime: vi.fn(() => runtime.promise) })
    const loading = state.loadRecordForm({ id: '9001' })
    expect(state.loadDetail).toHaveBeenCalledOnce()
    expect(state.fetchRuntime).toHaveBeenCalledWith('order', '9001')
    expect(state.showLoading).toHaveBeenCalledOnce()
    detail.resolve(true)
    await Promise.resolve()
    expect(state.closeLoading).not.toHaveBeenCalled()
    runtime.resolve({ data: { flowStatus: 'CANCELED' } })
    expect(await loading).toBe(true)
    expect(state.detailRuntime.value.flowStatus).toBe('CANCELED')
    expect(state.closeLoading).toHaveBeenCalledOnce()
  })

  it('ignores stale runtime responses after switching records', async () => {
    const first = deferred()
    const second = deferred()
    const state = fixture({ fetchRuntime: vi.fn().mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise) })
    const firstLoad = state.loadDetailRuntime({ id: '1' })
    const secondLoad = state.loadDetailRuntime({ id: '2' })
    first.resolve({ data: { flowStatus: 'APPROVED' } })
    await firstLoad
    expect(state.detailRuntimeLoading.value).toBe(true)
    expect(state.detailRuntime.value).toBeNull()
    second.resolve({ data: { flowStatus: 'CANCELED' } })
    await secondLoad
    expect(state.detailRuntime.value.flowStatus).toBe('CANCELED')
    expect(state.detailRuntimeLoading.value).toBe(false)
  })

  it('does not open a stale form when detail fails and always releases loading', async () => {
    const state = fixture({ loadDetail: vi.fn().mockResolvedValue(false) })
    expect(await state.loadRecordForm({ id: '1' })).toBe(false)
    expect(state.closeLoading).toHaveBeenCalledOnce()
  })

  it('preserves the selected row snapshot on runtime failure, not the previous record', async () => {
    const state = fixture({ fetchRuntime: vi.fn().mockRejectedValue(new Error('offline')) })
    state.detailRuntime.value = { processInstanceId: 'previous' }
    await state.loadDetailRuntime({ id: '2' })
    expect(state.detailRuntime.value).toBeNull()
    expect(state.detailRuntimeLoading.value).toBe(false)
    expect(state.warn).toHaveBeenCalledOnce()
  })

  it('keeps the local detail hook path and skips missing record runtime', async () => {
    const state = fixture({ loadDetailOnEdit: () => false })
    expect(await state.loadRecordForm({ title: 'unsaved' })).toBe(true)
    expect(state.applyDetailData).toHaveBeenCalledWith({ title: 'unsaved' })
    expect(state.loadDetail).not.toHaveBeenCalled()
    expect(state.fetchRuntime).not.toHaveBeenCalled()
    expect(state.showLoading).not.toHaveBeenCalled()
  })
})
