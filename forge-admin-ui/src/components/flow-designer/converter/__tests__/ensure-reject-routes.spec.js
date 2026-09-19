import { describe, expect, it } from 'vitest'
import { convertJsonToBpmn } from '../json-to-bpmn.js'
import { ensureRejectRoutes, REJECT_STRATEGY } from '../ensure-reject-routes.js'

const DOLLAR = '$'

function linearJson() {
  return {
    processId: 'Process_1',
    processName: '线性审批',
    config: { rejectStrategy: REJECT_STRATEGY.TO_INITIATOR_MODIFY },
    nodes: [
      { id: 'S', name: '开始', nodeType: 'start', config: {} },
      { id: 'A1', name: '部门审批', nodeType: 'approver', config: { taskType: 'assignee', assignee: `${DOLLAR}{deptManager}` } },
      { id: 'E', name: '结束', nodeType: 'end', config: {} },
    ],
    edges: [
      { id: 'F1', source: 'S', target: 'A1' },
      { id: 'F2', source: 'A1', target: 'E' },
    ],
  }
}

describe('ensureRejectRoutes', () => {
  it('为线性审批自动补发起人修改回路', () => {
    const next = ensureRejectRoutes(linearJson(), REJECT_STRATEGY.TO_INITIATOR_MODIFY)
    expect(next.nodes.some(node => node.config?.initiatorModify)).toBe(true)
    expect(next.nodes.some(node => node.id.startsWith('Forge_RejectGW_'))).toBe(true)
    expect(next.edges.some(edge => /approvalResult\s*==\s*'reject'/.test(edge.condition || ''))).toBe(true)
    const modify = next.nodes.find(node => node.config?.initiatorModify)
    expect(next.edges.some(edge => edge.source === modify.id && edge.target === 'A1')).toBe(true)
  })

  it('MANUAL 策略不改图', () => {
    const source = linearJson()
    const next = ensureRejectRoutes(source, REJECT_STRATEGY.MANUAL)
    expect(next).toBe(source)
  })

  it('已有驳回分支时不重复注入', () => {
    const json = linearJson()
    json.nodes.push({ id: 'GW1', name: '分支', nodeType: 'condition', config: { defaultFlowId: 'F3' } })
    json.nodes.push({
      id: 'MOD',
      name: '发起人修改',
      nodeType: 'approver',
      config: { initiatorModify: true, taskType: 'assignee', assignee: `${DOLLAR}{initiator}` },
    })
    json.edges = [
      { id: 'F1', source: 'S', target: 'A1' },
      { id: 'F2', source: 'A1', target: 'GW1' },
      { id: 'F3', source: 'GW1', target: 'E', isDefault: true },
      { id: 'F4', source: 'GW1', target: 'MOD', condition: `${DOLLAR}{approvalResult == 'reject'}`, approvalResult: 'reject' },
      { id: 'F5', source: 'MOD', target: 'A1' },
    ]
    const next = ensureRejectRoutes(json, REJECT_STRATEGY.TO_INITIATOR_MODIFY)
    expect(next.nodes.filter(node => node.id.startsWith('Forge_RejectGW_'))).toHaveLength(0)
  })

  it('convertJsonToBpmn 写出 rejectStrategy 与发起人修改节点', () => {
    const xml = convertJsonToBpmn(linearJson())
    expect(xml).toContain('flowable:rejectStrategy="TO_INITIATOR_MODIFY"')
    expect(xml).toContain('Forge_InitiatorModify')
    expect(xml).toContain('flowable:initiatorModify="true"')
    expect(xml).toContain("approvalResult == 'reject'")
  })
})
