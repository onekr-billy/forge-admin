import { describe, expect, it, vi } from 'vitest'
import {
  executeVisualRule,
  runRuntimeExtensions,
  selectRuntimeExtensions,
} from '../application-extension-runtime'

const base = {
  status: 'ENABLED',
  hookCode: 'BEFORE_SUBMIT',
  scopeType: 'OBJECT',
  objectId: '2001',
  sortOrder: 10,
  failurePolicy: 'BLOCK',
}

describe('published application extension runtime', () => {
  it('filters hook and scope while preserving configured order', () => {
    const selected = selectRuntimeExtensions([
      { ...base, id: '2', extensionCode: 'second', sortOrder: 20 },
      { ...base, id: '1', extensionCode: 'first', sortOrder: 10 },
      { ...base, id: '3', objectId: 'other' },
      { ...base, id: '4', status: 'DISABLED' },
    ], 'BEFORE_SUBMIT', { objectId: '2001' })

    expect(selected.map(item => item.id)).toEqual(['1', '2'])
  })

  it('evaluates visual conditions and returns only governed effects', () => {
    const effects = executeVisualRule({
      match: 'ALL',
      conditions: [
        { field: 'amount', operator: 'GE', value: 100 },
        { field: 'customerName', operator: 'NOT_EMPTY' },
      ],
      actions: [
        { actionType: 'SET_FIELD', field: 'priority', value: 'HIGH' },
        { actionType: 'SHOW_MESSAGE', message: '已标记高优先级' },
        { actionType: 'RAW_HTML', value: '<script />' },
      ],
    }, { amount: 120, customerName: '客户A' })

    expect(effects).toEqual([
      { type: 'SET_FIELD', field: 'priority', value: 'HIGH' },
      { type: 'SHOW_MESSAGE', message: '已标记高优先级', level: 'info' },
    ])
  })

  it('shows a message before submit when a numeric rating equals the configured value', async () => {
    const notify = vi.fn()
    await runRuntimeExtensions({
      extensions: [{
        ...base,
        extensionType: 'VISUAL_RULE',
        content: JSON.stringify({
          match: 'ALL',
          conditions: [{ field: 'fieldRate', operator: 'EQ', value: 2 }],
          actions: [{ actionType: 'SHOW_MESSAGE', message: '123' }],
        }),
      }],
      hookCode: 'BEFORE_SUBMIT',
      context: { objectId: '2001' },
      record: { fieldRate: 2 },
      fieldCatalog: [{ fieldCode: 'fieldRate', fieldType: 'RATE' }],
      notify,
    })

    expect(notify).toHaveBeenCalledWith('info', '123')
  })

  it('runs visual and sandbox extensions sequentially and applies effects', async () => {
    const notify = vi.fn()
    const sandboxExecute = vi.fn().mockResolvedValue({
      effects: [{ type: 'SET_FIELD', field: 'reviewed', value: true }],
    })
    const result = await runRuntimeExtensions({
      extensions: [
        { ...base, extensionType: 'VISUAL_RULE', content: '{"match":"ALL","conditions":[],"actions":[{"actionType":"SET_FIELD","field":"priority","value":"HIGH"}]}' },
        { ...base, extensionType: 'CLIENT_JS', sortOrder: 20, content: 'setField(\'reviewed\', true)' },
      ],
      hookCode: 'BEFORE_SUBMIT',
      context: { objectId: '2001' },
      record: { amount: 120 },
      fieldCatalog: [
        { fieldCode: 'priority' },
        { fieldCode: 'reviewed' },
      ],
      sandboxExecute,
      notify,
    })

    expect(result.record).toEqual({ amount: 120, priority: 'HIGH', reviewed: true })
    expect(sandboxExecute).toHaveBeenCalledWith(
      'setField(\'reviewed\', true)',
      expect.objectContaining({ record: { amount: 120, priority: 'HIGH' } }),
      expect.arrayContaining(['amount', 'priority', 'reviewed']),
      expect.arrayContaining(['priority', 'reviewed']),
    )
  })

  it('blocks pre-submit on BLOCK failures and only warns for WARN failures', async () => {
    const broken = { ...base, extensionType: 'CLIENT_JS', content: 'broken', extensionName: '校验增强' }
    await expect(runRuntimeExtensions({
      extensions: [broken],
      hookCode: 'BEFORE_SUBMIT',
      context: { objectId: '2001' },
      sandboxExecute: vi.fn().mockRejectedValue(new Error('脚本错误')),
    })).rejects.toThrow('校验增强执行失败')

    const notify = vi.fn()
    const result = await runRuntimeExtensions({
      extensions: [{ ...broken, failurePolicy: 'WARN' }],
      hookCode: 'BEFORE_SUBMIT',
      context: { objectId: '2001' },
      sandboxExecute: vi.fn().mockRejectedValue(new Error('脚本错误')),
      notify,
      record: { id: '1' },
    })
    expect(result.record).toEqual({ id: '1' })
    expect(notify).toHaveBeenCalledWith('warning', expect.stringContaining('脚本错误'))
  })

  it('rejects visual or server effects that write a readonly field', async () => {
    await expect(runRuntimeExtensions({
      extensions: [{
        ...base,
        extensionType: 'VISUAL_RULE',
        content: '{"conditions":[],"actions":[{"actionType":"SET_FIELD","field":"flowStatus","value":"APPROVED"}]}',
      }],
      hookCode: 'BEFORE_SUBMIT',
      context: { objectId: '2001' },
      fieldCatalog: [{ fieldCode: 'flowStatus', readonly: true }],
    })).rejects.toThrow('flowStatus 不存在或不可写')
  })
})
