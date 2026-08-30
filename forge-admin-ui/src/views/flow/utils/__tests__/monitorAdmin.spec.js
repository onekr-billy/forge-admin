import { describe, expect, it } from 'vitest'
import {
  buildCurrentTaskOptions,
  buildMonitorFormQuery,
  canInterveneInstance,
  canMutateRunningInstance,
  compactParams,
  isSuspendedInstance,
} from '../monitorAdmin'

describe('monitorAdmin', () => {
  it('允许运行中、激活和挂起实例进入干预区', () => {
    expect(canInterveneInstance('running')).toBe(true)
    expect(canInterveneInstance('active')).toBe(true)
    expect(canInterveneInstance('suspended')).toBe(true)
    expect(canInterveneInstance('completed')).toBe(false)
    expect(canInterveneInstance('terminated')).toBe(false)
  })

  it('只有运行中实例允许回退和转派', () => {
    expect(canMutateRunningInstance('running')).toBe(true)
    expect(canMutateRunningInstance('active')).toBe(true)
    expect(canMutateRunningInstance('suspended')).toBe(false)
    expect(isSuspendedInstance('suspended')).toBe(true)
  })

  it('监控表单查询使用实例 ID 作为 processInstanceId，不把实例 ID 当成 taskId', () => {
    expect(buildMonitorFormQuery({
      id: 'proc-1',
      businessKey: 'order:88',
      processDefKey: 'sample_purchase_order',
    })).toEqual({
      businessKey: 'order:88',
      processInstanceId: 'proc-1',
      processDefKey: 'sample_purchase_order',
    })
  })

  it('compactParams 去掉空值', () => {
    expect(compactParams({ a: '1', b: '', c: null, d: undefined })).toEqual({ a: '1' })
  })

  it('当前任务选项保留字符串任务 ID', () => {
    expect(buildCurrentTaskOptions([
      { id: '1001', name: '部门负责人审批', assignee: '45' },
      { taskId: '1002', taskName: '会签', assigneeName: '张三' },
    ])).toEqual([
      { label: '部门负责人审批 · 45', value: '1001', task: { id: '1001', name: '部门负责人审批', assignee: '45' } },
      { label: '会签 · 张三', value: '1002', task: { taskId: '1002', taskName: '会签', assigneeName: '张三' } },
    ])
  })
})
