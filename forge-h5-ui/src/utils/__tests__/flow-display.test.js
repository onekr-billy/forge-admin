import assert from 'node:assert/strict'
import test from 'node:test'
import { flowStatusTone, formatFlowAction, formatFlowDateTime, formatFlowStatus } from '../flow-display.js'

test('flow node statuses and history actions use Chinese labels', () => {
  assert.equal(formatFlowStatus('pending'), '待处理')
  assert.equal(formatFlowStatus('RUNNING'), '处理中')
  assert.equal(formatFlowStatus('completed'), '已完成')
  assert.equal(formatFlowStatus('unknown_stage'), '状态待确认')
  assert.equal(formatFlowStatus('用户自定义状态'), '用户自定义状态')
  assert.equal(formatFlowAction('start'), '提交申请')
  assert.equal(formatFlowAction('approve'), '已通过')
  assert.equal(formatFlowAction('reject'), '已驳回')
  assert.equal(formatFlowAction('return'), '已退回')
  assert.equal(formatFlowAction('delegate'), '已转办')
  assert.equal(formatFlowAction('claim'), '已签收')
  assert.equal(formatFlowAction('unexpected_action'), '其他操作')
  assert.equal(flowStatusTone('completed'), 'completed')
  assert.equal(flowStatusTone('running'), 'running')
})

test('flow times normalize local, array and zoned values without a raw T', () => {
  assert.equal(formatFlowDateTime('2026-09-25T19:41:46'), '2026-09-25 19:41:46')
  assert.equal(formatFlowDateTime('2026-09-25 19:41'), '2026-09-25 19:41:00')
  assert.equal(formatFlowDateTime([2026, 9, 25, 19, 41, 46]), '2026-09-25 19:41:46')
  assert.match(formatFlowDateTime('2026-09-25T11:41:46Z'), /^2026-09-\d{2} \d{2}:41:46$/)
  assert.equal(formatFlowDateTime(null), '-')
})
