import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import {
  isAuditReasonRequired,
  isAuditReasonRequiredError,
  isDataAuditHistoryAvailable,
  readDataAuditMeta,
  resolveDataAuditRemovePlan,
  shouldShowDataAuditHistory,
  stripDataAuditMeta,
  applyDataAuditRemove,
} from '../data-audit-submit'

describe('data-audit-submit', () => {
  it('reads meta from payload or main', () => {
    expect(readDataAuditMeta({ _dataAudit: { enabled: true, revision: 2 } })).toEqual({
      enabled: true,
      revision: 2,
    })
    expect(readDataAuditMeta({ main: { _dataAudit: { enabled: true } } }).enabled).toBe(true)
  })

  it('strips reserved audit keys from business payload', () => {
    const stripped = stripDataAuditMeta({
      name: '订单',
      _dataAudit: { revision: 1 },
      auditContext: { actorId: '1' },
      main: { title: 'A', _dataAudit: { revision: 1 } },
    })
    expect(stripped.name).toBe('订单')
    expect(stripped._dataAudit).toBeUndefined()
    expect(stripped.auditContext).toBeUndefined()
    expect(stripped.main.title).toBe('A')
    expect(stripped.main._dataAudit).toBeUndefined()
  })

  it('shows configured history for enabled objects and stopped objects with history', () => {
    expect(shouldShowDataAuditHistory({ configured: true, enabled: true, showInDetail: true })).toBe(true)
    expect(shouldShowDataAuditHistory({ configured: true, enabled: false, historyAvailable: true, showInDetail: true })).toBe(true)
    expect(shouldShowDataAuditHistory({ configured: true, enabled: false, historyAvailable: false, showInDetail: true })).toBe(false)
    expect(shouldShowDataAuditHistory({ configured: true, enabled: true, showInDetail: false }, true)).toBe(false)
  })

  it('keeps legacy page visibility when an object has no object-level policy', () => {
    expect(shouldShowDataAuditHistory(null, true)).toBe(true)
    expect(shouldShowDataAuditHistory(null, false)).toBe(false)
    expect(isDataAuditHistoryAvailable({ revision: 3 })).toBe(true)
  })

  it('treats missing reasonRequired as required when audit enabled', () => {
    expect(isAuditReasonRequired({ enabled: true })).toBe(true)
    expect(isAuditReasonRequired({ enabled: true, reasonRequired: true })).toBe(true)
    expect(isAuditReasonRequired({ enabled: true, reasonRequired: false })).toBe(false)
    expect(isAuditReasonRequired({ enabled: false, reasonRequired: true })).toBe(false)
  })

  it('detects audit reason required errors', () => {
    expect(isAuditReasonRequiredError({ message: '请填写删除原因' })).toBe(true)
    expect(isAuditReasonRequiredError({ data: { errorCode: 'AUDIT_REASON_REQUIRED' } })).toBe(true)
    expect(isAuditReasonRequiredError({ message: '无权限' })).toBe(false)
  })

  describe('applyDataAuditRemove', () => {
    beforeEach(() => {
      window.$dialog = { warning: vi.fn(), create: vi.fn() }
      window.$message = { warning: vi.fn() }
    })

    afterEach(() => {
      delete window.$dialog
      delete window.$message
    })

    it('returns null when list rows have no enabled audit meta', async () => {
      const requestRemove = vi.fn()
      const result = await applyDataAuditRemove({
        configKey: 'demo',
        ids: ['1'],
        rows: [{ id: '1' }],
        requestRemove,
      })
      expect(result).toBeNull()
      expect(requestRemove).not.toHaveBeenCalled()
    })

    it('hydrates meta from detail when list rows lack audit fields', async () => {
      const fetchRecordMeta = vi.fn().mockResolvedValue({
        enabled: true,
        reasonRequired: true,
        revision: 6,
      })
      const plan = await resolveDataAuditRemovePlan({
        configKey: 'demo',
        ids: ['1'],
        rows: [{ id: '1' }],
        fetchRecordMeta,
      })
      expect(fetchRecordMeta).toHaveBeenCalledTimes(1)
      expect(plan.mode).toBe('audit')
      expect(plan.reasonRequired).toBe(true)
      // 抽样补齐后删除不校验 revision，统一为 0
      expect(plan.expectedRevisions).toEqual([0])
    })

    it('samples only one detail when hydrating a batch without list audit meta', async () => {
      const fetchRecordMeta = vi.fn().mockResolvedValue({
        enabled: true,
        reasonRequired: true,
        revision: 3,
      })
      const plan = await resolveDataAuditRemovePlan({
        configKey: 'demo',
        ids: ['1', '2', '3'],
        rows: [{ id: '1' }, { id: '2' }, { id: '3' }],
        fetchRecordMeta,
      })
      expect(fetchRecordMeta).toHaveBeenCalledTimes(1)
      expect(plan.mode).toBe('audit')
      expect(plan.ids).toEqual(['1', '2', '3'])
      expect(plan.expectedRevisions).toEqual([0, 0, 0])
    })

    it('prompts delete reason via warning dialog and posts /remove', async () => {
      window.$dialog.warning = vi.fn(({ onPositiveClick, content }) => {
        const vnode = content()
        vnode.props['onUpdate:value']('清理测试数据')
        onPositiveClick()
      })
      const requestRemove = vi.fn().mockResolvedValue({ data: 1 })
      const result = await applyDataAuditRemove({
        configKey: 'demo',
        ids: [101],
        rows: [{ id: 101, _dataAudit: { enabled: true, reasonRequired: true, revision: 4 } }],
        requestRemove,
      })
      expect(window.$dialog.warning).toHaveBeenCalledWith(expect.objectContaining({
        title: '请填写删除原因',
      }))
      expect(requestRemove).toHaveBeenCalledWith('demo', {
        ids: ['101'],
        expectedRevisions: [4],
        reason: '清理测试数据',
      })
      expect(result).toEqual({ data: 1 })
    })

    it('skips prompt when audit enabled but reason not required', async () => {
      const requestRemove = vi.fn().mockResolvedValue({ data: 1 })
      await applyDataAuditRemove({
        configKey: 'demo',
        ids: ['9'],
        rows: [{ id: '9', _dataAudit: { enabled: true, reasonRequired: false, revision: 1 } }],
        requestRemove,
      })
      expect(window.$dialog.warning).not.toHaveBeenCalled()
      expect(requestRemove).toHaveBeenCalledWith('demo', {
        ids: ['9'],
        expectedRevisions: [1],
        reason: '',
      })
    })

    it('uses preset reason without opening dialog when skipPrompt', async () => {
      const requestRemove = vi.fn().mockResolvedValue({ data: 1 })
      await applyDataAuditRemove({
        configKey: 'demo',
        ids: ['9'],
        rows: [{ id: '9', _dataAudit: { enabled: true, reasonRequired: true, revision: 2 } }],
        requestRemove,
        reason: '预先填写的原因',
        skipPrompt: true,
      })
      expect(window.$dialog.warning).not.toHaveBeenCalled()
      expect(requestRemove).toHaveBeenCalledWith('demo', {
        ids: ['9'],
        expectedRevisions: [2],
        reason: '预先填写的原因',
      })
    })
  })
})
