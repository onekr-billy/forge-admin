import { describe, expect, it } from 'vitest'
import {
  isDataAuditHistoryAvailable,
  readDataAuditMeta,
  shouldShowDataAuditHistory,
  stripDataAuditMeta,
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
})
