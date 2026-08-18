import { describe, expect, it, vi } from 'vitest'
import {
  buildBusinessActionExecutePayload,
  buildBusinessActionInitialData,
  buildBusinessActionInputFormSchema,
  buildChildRowActionContext,
  createBusinessActionIdempotencyKey,
  resolveBusinessActionAttempt,
  unwrapBusinessActionResult,
} from '../business-action-runtime'

describe('business action runtime protocol', () => {
  it('converts inputSchema into a managed AiForm schema', () => {
    expect(buildBusinessActionInputFormSchema([
      { name: 'quantity', label: '本次数量', type: 'integer', required: true, min: 1, max: 99 },
      { name: 'pickupDate', label: '提货日期', type: 'date' },
      { name: 'cashAmount', label: '现金金额', type: 'money', scale: 2 },
      { name: 'channel', label: '渠道', type: 'select', options: [{ label: '门店', value: 'STORE' }] },
    ])).toEqual([
      expect.objectContaining({ field: 'quantity', type: 'input-number', required: true, min: 1, max: 99, precision: 0 }),
      expect.objectContaining({ field: 'pickupDate', type: 'date' }),
      expect.objectContaining({ field: 'cashAmount', type: 'input-number', min: 0, precision: 2, step: 0.01 }),
      expect.objectContaining({ field: 'channel', type: 'select', options: [{ label: '门店', value: 'STORE' }] }),
    ])
  })

  it('uses only declared defaults when inputSchema exists', () => {
    expect(buildBusinessActionInitialData({
      inputSchema: [
        { name: 'quantity', defaultValue: 1 },
        { name: 'memberName' },
      ],
      defaultValues: {
        quantity: 2,
        memberName: 'row.member.name',
        hiddenSecret: 'never-forward',
      },
    }, { member: { name: '张三' } })).toEqual({ quantity: 2, memberName: '张三' })
    expect(buildBusinessActionInitialData({
      inputSchema: [],
      defaultValues: { hiddenSecret: 'never-forward' },
    })).toEqual({})
  })

  it('generates safe keys and reuses one key only for the same payload', () => {
    const generated = createBusinessActionIdempotencyKey({ randomUUID: () => '123e4567-e89b-12d3-a456-426614174000' })
    expect(generated).toBe('ui:123e4567-e89b-12d3-a456-426614174000')
    expect(generated).toMatch(/^[\w.:-]{8,128}$/)

    const keyFactory = vi.fn()
      .mockReturnValueOnce('ui:first-attempt')
      .mockReturnValueOnce('ui:second-attempt')
    const first = resolveBusinessActionAttempt({}, { quantity: 1 }, keyFactory)
    const retry = resolveBusinessActionAttempt(first, { quantity: 1 }, keyFactory)
    const corrected = resolveBusinessActionAttempt(retry, { quantity: 2 }, keyFactory)

    expect(retry.idempotencyKey).toBe('ui:first-attempt')
    expect(corrected.idempotencyKey).toBe('ui:second-attempt')
    expect(keyFactory).toHaveBeenCalledTimes(2)
  })

  it('builds a request without forwarding the browser row', () => {
    const payload = buildBusinessActionExecutePayload({
      action: { actionCode: 'confirm' },
      objectCode: 'presale_order',
      recordId: 101,
      formData: { quantity: 2 },
      routeQuery: { scene: 'wecom' },
      idempotencyKey: 'ui:confirm-101',
    })

    expect(payload).toMatchObject({
      objectCode: 'presale_order',
      recordId: '101',
      actionCode: 'confirm',
      formData: { quantity: 2 },
      context: { routeQuery: { scene: 'wecom' } },
      idempotencyKey: 'ui:confirm-101',
    })
    expect(payload.context).not.toHaveProperty('row')
  })

  it('builds a child-row request from identifiers without forwarding parent or child records', () => {
    const executionContext = buildChildRowActionContext({
      child: { relationKey: 'order_item' },
      parentRecord: { id: 1001, status: 'DRAFT' },
      childRecord: { id: 2001, amount: 99 },
    })
    expect(executionContext).toEqual({
      parentRecordId: 1001,
      childRecordId: 2001,
      relationKey: 'order_item',
      persisted: true,
    })

    const payload = buildBusinessActionExecutePayload({
      action: { actionCode: 'confirm_detail' },
      objectCode: 'order',
      recordId: executionContext.childRecordId,
      parentRecordId: executionContext.parentRecordId,
      childRecordId: executionContext.childRecordId,
      relationKey: executionContext.relationKey,
      idempotencyKey: 'ui:confirm-detail-2001',
    })

    expect(payload).toMatchObject({
      objectCode: 'order',
      recordId: '2001',
      parentRecordId: '1001',
      childRecordId: '2001',
      relationKey: 'order_item',
    })
    expect(payload).not.toHaveProperty('parentRecord')
    expect(payload).not.toHaveProperty('childRecord')
  })

  it('marks unsaved child rows as non-executable', () => {
    expect(buildChildRowActionContext({
      child: { key: 'order_item' },
      parentRecord: { id: 1001 },
      childRecord: { __rowKey: 'draft-row' },
    }).persisted).toBe(false)
  })

  it('fails closed when a replay request returns a failed business status', () => {
    expect(() => unwrapBusinessActionResult({
      data: {
        executeStatus: 'FAILED',
        message: '状态已变化',
      },
    })).toThrowError('状态已变化')

    expect(unwrapBusinessActionResult({
      data: {
        executeStatus: 'SUCCESS',
        message: '提交成功',
      },
    })).toMatchObject({ executeStatus: 'SUCCESS', message: '提交成功' })
  })
})
