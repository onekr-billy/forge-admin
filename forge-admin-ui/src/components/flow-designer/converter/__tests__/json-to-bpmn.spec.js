import { describe, expect, it } from 'vitest'
import { convertJsonToBpmn } from '../json-to-bpmn.js'
import { findElementsByLocalName, getAttr, getFlowableAttr, getRootProcess, parseBpmnXml } from '../xml-utils.js'

const DOLLAR = '$'

function baseJson() {
  return {
    processId: 'Process_1',
    processName: '请假流程',
    nodes: [
      { id: 'S', nodeType: 'start', name: '发起', config: { initiator: 'initiator', documentation: '请假发起' } },
      { id: 'T_appr', nodeType: 'approver', name: '部门经理审批', config: {
        taskType: 'assignee',
        assignee: 'custom',
        assigneeUserId: '2090384244139360257',
        assigneeExpr: '',
        assigneeUserName: '张三',
        allowApprove: true,
        allowReject: true,
        allowDelegate: true,
        allowReturn: true, // 非默认
        multiInstanceType: 'none',
      } },
      { id: 'E', nodeType: 'end', name: '结束', config: { endType: 'normal' } },
    ],
    edges: [
      { id: 'F1', source: 'S', target: 'T_appr', condition: '', isDefault: false },
      { id: 'F2', source: 'T_appr', target: 'E', condition: '', isDefault: false },
    ],
  }
}

describe('convertJsonToBpmn - 主结构', () => {
  it('生成完整 BPMN 2.0 文档（definitions + process + diagram）', () => {
    const xml = convertJsonToBpmn(baseJson())
    expect(xml).toContain('<?xml version="1.0"')
    expect(xml).toContain('<bpmn:definitions')
    expect(xml).toContain('<bpmn:process id="Process_1"')
    expect(xml).toContain('<bpmndi:BPMNDiagram')
    expect(xml).toContain('<bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_1"')
  })

  it('流程级审批策略写入 process 扩展属性', () => {
    const json = baseJson()
    json.config = {
      allowSubmitterWithdraw: false,
      autoApprovalMode: 'firstOnly',
    }

    const doc = parseBpmnXml(convertJsonToBpmn(json))
    const proc = getRootProcess(doc)

    expect(getFlowableAttr(proc, 'allowSubmitterWithdraw')).toBe('false')
    expect(getFlowableAttr(proc, 'autoApprovalMode')).toBe('firstOnly')
  })

  it('节点全部出现', () => {
    const xml = convertJsonToBpmn(baseJson())
    const doc = parseBpmnXml(xml)
    expect(findElementsByLocalName(doc, 'startEvent').length).toBe(1)
    expect(findElementsByLocalName(doc, 'userTask').length).toBe(1)
    expect(findElementsByLocalName(doc, 'endEvent').length).toBe(1)
    expect(findElementsByLocalName(doc, 'sequenceFlow').length).toBe(2)
  })

  it('startEvent 固定发起人变量且不写入节点表单配置', () => {
    const json = baseJson()
    json.nodes[0].config = {
      initiator: 'customInitiator',
      formKey: 'legacyStartForm',
      formJson: '[]',
      formUrl: '/legacy/start',
    }
    const xml = convertJsonToBpmn(json)
    const doc = parseBpmnXml(xml)
    const start = findElementsByLocalName(doc, 'startEvent')[0]
    expect(getFlowableAttr(start, 'initiator')).toBe('initiator')
    expect(getFlowableAttr(start, 'formKey')).toBe(null)
    expect(getFlowableAttr(start, 'formJson')).toBe(null)
    expect(getFlowableAttr(start, 'formUrl')).toBe(null)
  })

  it('userTask 写入 flowable:assignee + 非默认权限', () => {
    const xml = convertJsonToBpmn(baseJson())
    const doc = parseBpmnXml(xml)
    const t = findElementsByLocalName(doc, 'userTask')[0]
    expect(getFlowableAttr(t, 'assignee')).toBe('2090384244139360257')
    expect(getFlowableAttr(t, 'assigneeType')).toBe('custom')
    expect(getFlowableAttr(t, 'assigneeName')).toBe('张三')
    expect(getFlowableAttr(t, 'allowReturn')).toBe('true')
    // 默认权限不出现
    expect(getFlowableAttr(t, 'allowApprove')).toBe(null)
  })

  it('userTask 写入表单字段权限配置', () => {
    const json = baseJson()
    json.nodes[1].config.formFieldPermissions = [
      { field: 'amount', label: '金额', readable: true, writable: false, required: true },
    ]

    const doc = parseBpmnXml(convertJsonToBpmn(json))
    const t = findElementsByLocalName(doc, 'userTask')[0]
    const permissions = JSON.parse(getFlowableAttr(t, 'formFieldPermissions'))

    expect(permissions).toEqual([
      {
        field: 'amount',
        fieldCode: 'amount',
        label: '金额',
        visible: true,
        editable: false,
        readable: true,
        writable: false,
        required: false,
      },
    ])
  })

  it('userTask 写入业务表单资产引用', () => {
    const json = baseJson()
    Object.assign(json.nodes[1].config, {
      formMode: 'BUSINESS_CODE_FORM',
      formKey: 'sample_purchase_order_approval_form',
      formName: '采购单审批表单',
      providerKey: 'samplePurchaseOrder',
      formUrl: '/business/purchase-order-test',
      viewKey: 'approve',
      formRef: {
        type: 'BUSINESS_CODE_FORM',
        objectCode: 'sample_purchase_order',
        providerKey: 'samplePurchaseOrder',
        formKey: 'sample_purchase_order_approval_form',
      },
    })

    const doc = parseBpmnXml(convertJsonToBpmn(json))
    const t = findElementsByLocalName(doc, 'userTask')[0]

    expect(getFlowableAttr(t, 'formMode')).toBe('BUSINESS_CODE_FORM')
    expect(getFlowableAttr(t, 'formKey')).toBe('sample_purchase_order_approval_form')
    expect(getFlowableAttr(t, 'formName')).toBe('采购单审批表单')
    expect(getFlowableAttr(t, 'providerKey')).toBe('samplePurchaseOrder')
    expect(getFlowableAttr(t, 'formUrl')).toBe('/business/purchase-order-test')
    expect(getFlowableAttr(t, 'viewKey')).toBe('approve')
    expect(JSON.parse(getFlowableAttr(t, 'formRef'))).toEqual({
      type: 'BUSINESS_CODE_FORM',
      objectCode: 'sample_purchase_order',
      providerKey: 'samplePurchaseOrder',
      formKey: 'sample_purchase_order_approval_form',
    })
  })

  it('carbonCopy 写入抄送人配置和默认平台抄送委托', () => {
    const json = baseJson()
    json.nodes.splice(2, 0, {
      id: 'CC1',
      nodeType: 'carbonCopy',
      name: '抄送总经理',
      config: {
        candidateUsers: ['1001', '1002'],
        candidateUserNames: ['张三', '李四'],
      },
    })
    json.edges = [
      { id: 'F1', source: 'S', target: 'T_appr', condition: '', isDefault: false },
      { id: 'F2', source: 'T_appr', target: 'CC1', condition: '', isDefault: false },
      { id: 'F3', source: 'CC1', target: 'E', condition: '', isDefault: false },
    ]

    const doc = parseBpmnXml(convertJsonToBpmn(json))
    const cc = findElementsByLocalName(doc, 'serviceTask')[0]

    expect(getFlowableAttr(cc, 'type')).toBe('cc')
    expect(getFlowableAttr(cc, 'ccReceiverType')).toBe('users')
    expect(getFlowableAttr(cc, 'candidateUsers')).toBe('1001,1002')
    expect(getFlowableAttr(cc, 'candidateUserNames')).toBe('张三,李四')
    expect(getFlowableAttr(cc, 'delegateExpression')).toBe(`${DOLLAR}{flowCcNodeDelegate}`)
  })

  it('carbonCopy 支持按角色和表达式配置接收人', () => {
    const json = baseJson()
    json.nodes.splice(2, 0, {
      id: 'CC1',
      nodeType: 'carbonCopy',
      name: '按角色抄送',
      config: {
        ccReceiverType: 'roles',
        candidateGroups: ['general_manager'],
        candidateGroupNames: ['总经理'],
      },
    }, {
      id: 'CC2',
      nodeType: 'carbonCopy',
      name: '按表达式抄送',
      config: {
        ccReceiverType: 'expression',
        ccExpressionTarget: 'users',
        ccExpression: `${DOLLAR}{flowSpelService.findUsersByRole('general_manager')}`,
      },
    })
    json.edges = [
      { id: 'F1', source: 'S', target: 'T_appr', condition: '', isDefault: false },
      { id: 'F2', source: 'T_appr', target: 'CC1', condition: '', isDefault: false },
      { id: 'F3', source: 'CC1', target: 'CC2', condition: '', isDefault: false },
      { id: 'F4', source: 'CC2', target: 'E', condition: '', isDefault: false },
    ]

    const doc = parseBpmnXml(convertJsonToBpmn(json))
    const [roleCc, exprCc] = findElementsByLocalName(doc, 'serviceTask')

    expect(getFlowableAttr(roleCc, 'ccReceiverType')).toBe('roles')
    expect(getFlowableAttr(roleCc, 'candidateGroups')).toBe('general_manager')
    expect(getFlowableAttr(roleCc, 'candidateGroupNames')).toBe('总经理')
    expect(getFlowableAttr(exprCc, 'ccReceiverType')).toBe('expression')
    expect(getFlowableAttr(exprCc, 'ccExpressionTarget')).toBe('users')
    expect(getFlowableAttr(exprCc, 'candidateUsers')).toBe(`${DOLLAR}{flowSpelService.findUsersByRole('general_manager')}`)
  })

  it('userTask 会签写入 Flowable collection 与 elementVariable', () => {
    const json = baseJson()
    Object.assign(json.nodes[1].config, {
      assignee: `${DOLLAR}{assignee}`,
      multiInstanceType: 'parallel',
      multiInstanceCollection: `${DOLLAR}{countersignUserList}`,
      multiInstanceElementVariable: 'assignee',
      completionCondition: 'all',
      passRate: 100,
    })

    const doc = parseBpmnXml(convertJsonToBpmn(json))
    const loop = findElementsByLocalName(doc, 'multiInstanceLoopCharacteristics')[0]

    expect(getFlowableAttr(loop, 'collection')).toBe(`${DOLLAR}{countersignUserList}`)
    expect(getFlowableAttr(loop, 'elementVariable')).toBe('assignee')
    expect(findElementsByLocalName(loop, 'loopCardinality')).toHaveLength(0)
  })

  it('userTask 会签缺少 collection 时写入 loopCardinality 兜底，避免 Flowable 部署校验失败', () => {
    const json = baseJson()
    Object.assign(json.nodes[1].config, {
      multiInstanceType: 'parallel',
      multiInstanceCollection: '',
      multiInstanceLoopCardinality: '',
      completionCondition: 'all',
      passRate: 100,
    })

    const doc = parseBpmnXml(convertJsonToBpmn(json))
    const loop = findElementsByLocalName(doc, 'multiInstanceLoopCharacteristics')[0]
    const cardinality = findElementsByLocalName(loop, 'loopCardinality')[0]

    expect(getFlowableAttr(loop, 'collection')).toBe(null)
    expect(cardinality.textContent).toBe(`${DOLLAR}{nrOfInstances}`)
  })

  it('userTask 写入处理时限和逾期提醒扩展属性', () => {
    const json = baseJson()
    Object.assign(json.nodes[1].config, {
      dueDateDays: 1,
      dueDateHours: 2,
      overdueReminderEnabled: true,
      overdueReminderTemplateCode: 'FLOW_TASK_OVERDUE',
      overdueReminderChannels: ['WEB', 'EMAIL'],
      overdueReminderRepeatMode: 'interval',
      overdueReminderIntervalMinutes: 60,
      overdueReminderMaxTimes: 3,
    })

    const doc = parseBpmnXml(convertJsonToBpmn(json))
    const t = findElementsByLocalName(doc, 'userTask')[0]

    expect(getFlowableAttr(t, 'dueDate')).toBe('P1DT2H')
    expect(getFlowableAttr(t, 'overdueReminderEnabled')).toBe('true')
    expect(getFlowableAttr(t, 'overdueReminderTemplateCode')).toBe('FLOW_TASK_OVERDUE')
    expect(getFlowableAttr(t, 'overdueReminderChannels')).toBe('WEB,EMAIL')
    expect(getFlowableAttr(t, 'overdueReminderRepeatMode')).toBe('interval')
    expect(getFlowableAttr(t, 'overdueReminderIntervalMinutes')).toBe('60')
    expect(getFlowableAttr(t, 'overdueReminderMaxTimes')).toBe('3')
  })

  it('每个 BPMNShape 都有真实 bpmnElement 引用', () => {
    const xml = convertJsonToBpmn(baseJson())
    const doc = parseBpmnXml(xml)
    const shapes = findElementsByLocalName(doc, 'BPMNShape')
    const ids = new Set(['S', 'T_appr', 'E'])
    expect(shapes.length).toBe(3)
    for (const s of shapes)
      expect(ids.has(getAttr(s, 'bpmnElement'))).toBe(true)
  })
})

describe('convertJsonToBpmn - 边界场景', () => {
  it('空 flowJson 返回最小可解析定义', () => {
    const xml = convertJsonToBpmn(null)
    const doc = parseBpmnXml(xml)
    expect(getRootProcess(doc)).toBeTruthy()
  })

  it('terminate 结束节点写入 terminateEventDefinition', () => {
    const json = baseJson()
    json.nodes[2].config.endType = 'terminate'
    const xml = convertJsonToBpmn(json)
    expect(xml).toContain('<bpmn:terminateEventDefinition')
  })

  it('advanced 节点 rawXml 原样写出', () => {
    const json = {
      processId: 'P',
      nodes: [
        { id: 'S', nodeType: 'start', name: '', config: {} },
        { id: 'X', nodeType: 'advanced', name: '', config: {}, rawXml: '<bpmn:intermediateCatchEvent id="X"/>' },
        { id: 'E', nodeType: 'end', name: '', config: {} },
      ],
      edges: [
        { id: 'F1', source: 'S', target: 'X' },
        { id: 'F2', source: 'X', target: 'E' },
      ],
    }
    const xml = convertJsonToBpmn(json)
    expect(xml).toContain('<bpmn:intermediateCatchEvent id="X"/>')
  })

  it('默认分支有条件时不写出 conditionExpression，避免 Flowable 部署失败', () => {
    const json = {
      processId: 'P',
      nodes: [
        { id: 'S', nodeType: 'start', name: '', config: {} },
        { id: 'GW', nodeType: 'condition', name: '', config: { defaultFlowId: 'F_default' } },
        { id: 'T1', nodeType: 'approver', name: '', config: {} },
        { id: 'T2', nodeType: 'approver', name: '', config: {} },
      ],
      edges: [
        { id: 'F1', source: 'S', target: 'GW' },
        { id: 'F_normal', source: 'GW', target: 'T1', condition: `${DOLLAR}{amount > 1000}`, isDefault: false },
        { id: 'F_default', source: 'GW', target: 'T2', condition: `${DOLLAR}{amount <= 1000}`, isDefault: true },
      ],
    }

    const xml = convertJsonToBpmn(json)
    const doc = parseBpmnXml(xml)
    const gw = findElementsByLocalName(doc, 'exclusiveGateway').find(node => getAttr(node, 'id') === 'GW')
    const normalFlow = findElementsByLocalName(doc, 'sequenceFlow').find(edge => getAttr(edge, 'id') === 'F_normal')
    const defaultFlow = findElementsByLocalName(doc, 'sequenceFlow').find(edge => getAttr(edge, 'id') === 'F_default')

    expect(getAttr(gw, 'default')).toBe('F_default')
    expect(normalFlow.textContent).toContain(`${DOLLAR}{amount > 1000}`)
    expect(findElementsByLocalName(defaultFlow, 'conditionExpression')).toHaveLength(0)
  })
})
