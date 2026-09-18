import { describe, expect, it } from 'vitest'
import { parseUserTaskConfig } from '../user-task-parser.js'
import { writeUserTaskConfig } from '../user-task-writer.js'
import { findElementsByLocalName, parseBpmnXml } from '../xml-utils.js'

function getTask(xml, id) {
  const doc = parseBpmnXml(xml)
  return findElementsByLocalName(doc, 'userTask').find(t => t.getAttribute('id') === id)
}

const SAMPLE_PERMS = [
  '<?xml version="1.0" encoding="UTF-8"?>',
  '<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn">',
  '  <bpmn:process id="P">',
  '    <bpmn:userTask id="T_full"',
  '                   flowable:allowApprove="true"',
  '                   flowable:allowReject="false"',
  '                   flowable:allowDelegate="false"',
  '                   flowable:allowReturn="true"',
  '                   flowable:allowTerminate="true"',
  '                   flowable:requireSignature="true"',
  '                   flowable:requireComment="false"/>',
  '    <bpmn:userTask id="T_default"/>',
  '  </bpmn:process>',
  '</bpmn:definitions>',
].join('\n')

const SAMPLE_FORM_PRIORITY = [
  '<?xml version="1.0" encoding="UTF-8"?>',
  '<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn">',
  '  <bpmn:process id="P">',
  '    <bpmn:userTask id="T_external"',
  '                   flowable:formUrl="/leave/approve"',
  '                   flowable:priority="80"',
  '                   flowable:dueDate="P3D"',
  '                   flowable:formFieldPermissions=\'[{"field":"amount","label":"金额","readable":true,"writable":false,"required":true}]\'/>',
  '    <bpmn:userTask id="T_overdue"',
  '                   flowable:dueDate="P1DT2H"',
  '                   flowable:overdueReminderEnabled="true"',
  '                   flowable:overdueReminderTemplateCode="FLOW_TASK_OVERDUE"',
  '                   flowable:overdueReminderChannels="WEB,EMAIL"',
  '                   flowable:overdueReminderRepeatMode="interval"',
  '                   flowable:overdueReminderIntervalMinutes="60"',
  '                   flowable:overdueReminderMaxTimes="3"/>',
  '    <bpmn:userTask id="T_dynamic" flowable:formKey="leaveForm"/>',
  '    <bpmn:userTask id="T_business"',
  '                   flowable:formMode="BUSINESS_CODE_FORM"',
  '                   flowable:formKey="sample_purchase_order_approval_form"',
  '                   flowable:formName="采购单审批表单"',
  '                   flowable:providerKey="samplePurchaseOrder"',
  '                   flowable:viewKey="approve"',
  '                   flowable:formRef=\'{"type":"BUSINESS_CODE_FORM","providerKey":"samplePurchaseOrder","formKey":"sample_purchase_order_approval_form"}\'/>',
  '    <bpmn:userTask id="T_no_form"/>',
  '  </bpmn:process>',
  '</bpmn:definitions>',
].join('\n')

describe('parseUserTaskConfig - 操作权限（7 个布尔字段）', () => {
  it('全显式配置时所有字段按值解析', () => {
    const cfg = parseUserTaskConfig(getTask(SAMPLE_PERMS, 'T_full'))
    expect(cfg.allowApprove).toBe(true)
    expect(cfg.allowReject).toBe(false)
    expect(cfg.allowDelegate).toBe(false)
    expect(cfg.allowReturn).toBe(true)
    expect(cfg.allowTerminate).toBe(true)
    expect(cfg.requireSignature).toBe(true)
    expect(cfg.requireComment).toBe(false)
  })

  it('未配置时使用默认值（与 NodePropertiesPanel 保持一致）', () => {
    const cfg = parseUserTaskConfig(getTask(SAMPLE_PERMS, 'T_default'))
    expect(cfg.allowApprove).toBe(true)
    expect(cfg.allowReject).toBe(true)
    expect(cfg.allowDelegate).toBe(true)
    expect(cfg.allowReturn).toBe(false)
    expect(cfg.allowTerminate).toBe(false)
    expect(cfg.requireSignature).toBe(false)
    expect(cfg.requireComment).toBe(true)
  })
})

describe('parseUserTaskConfig - 表单 / 优先级 / dueDate', () => {
  it('formUrl 配置 → formType=external', () => {
    const cfg = parseUserTaskConfig(getTask(SAMPLE_FORM_PRIORITY, 'T_external'))
    expect(cfg.formType).toBe('external')
    expect(cfg.formUrl).toBe('/leave/approve')
  })

  it('formKey 配置 → formType=dynamic', () => {
    const cfg = parseUserTaskConfig(getTask(SAMPLE_FORM_PRIORITY, 'T_dynamic'))
    expect(cfg.formType).toBe('dynamic')
    expect(cfg.formKey).toBe('leaveForm')
  })

  it('解析业务表单资产引用', () => {
    const cfg = parseUserTaskConfig(getTask(SAMPLE_FORM_PRIORITY, 'T_business'))
    expect(cfg.formType).toBe('dynamic')
    expect(cfg.formMode).toBe('BUSINESS_CODE_FORM')
    expect(cfg.formKey).toBe('sample_purchase_order_approval_form')
    expect(cfg.formName).toBe('采购单审批表单')
    expect(cfg.providerKey).toBe('samplePurchaseOrder')
    expect(cfg.viewKey).toBe('approve')
    expect(cfg.formRef).toEqual({
      type: 'BUSINESS_CODE_FORM',
      providerKey: 'samplePurchaseOrder',
      formKey: 'sample_purchase_order_approval_form',
    })
  })

  it('无表单配置 → formType=none', () => {
    const cfg = parseUserTaskConfig(getTask(SAMPLE_FORM_PRIORITY, 'T_no_form'))
    expect(cfg.formType).toBe('none')
  })

  it('priority / dueDate（ISO 8601 P3D） 解析为整数', () => {
    const cfg = parseUserTaskConfig(getTask(SAMPLE_FORM_PRIORITY, 'T_external'))
    expect(cfg.priority).toBe(80)
    expect(cfg.dueDate).toBe(3)
    expect(cfg.dueDateDays).toBe(3)
    expect(cfg.dueDateHours).toBe(0)
  })

  it('解析天+小时处理时限和逾期提醒配置', () => {
    const cfg = parseUserTaskConfig(getTask(SAMPLE_FORM_PRIORITY, 'T_overdue'))
    expect(cfg.dueDate).toBe(1)
    expect(cfg.dueDateDays).toBe(1)
    expect(cfg.dueDateHours).toBe(2)
    expect(cfg.overdueReminderEnabled).toBe(true)
    expect(cfg.overdueReminderTemplateCode).toBe('FLOW_TASK_OVERDUE')
    expect(cfg.overdueReminderChannels).toEqual(['WEB', 'EMAIL'])
    expect(cfg.overdueReminderRepeatMode).toBe('interval')
    expect(cfg.overdueReminderIntervalMinutes).toBe(60)
    expect(cfg.overdueReminderMaxTimes).toBe(3)
  })

  it('解析表单字段权限配置', () => {
    const cfg = parseUserTaskConfig(getTask(SAMPLE_FORM_PRIORITY, 'T_external'))
    expect(cfg.formFieldPermissions).toEqual([
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

  it('未配置时使用默认值', () => {
    const cfg = parseUserTaskConfig(getTask(SAMPLE_FORM_PRIORITY, 'T_no_form'))
    expect(cfg.priority).toBe(50)
    expect(cfg.dueDate).toBe(0)
    expect(cfg.dueDateDays).toBe(0)
    expect(cfg.dueDateHours).toBe(0)
    expect(cfg.overdueReminderEnabled).toBe(false)
  })

  it('主子表 v2 权限能解析并写回，保留子表行操作权限', () => {
    const taskXml = [
      '<bpmn:userTask id="T_detail"',
      ' flowable:formMode="BUSINESS_OBJECT_FORM"',
      ' flowable:formFieldPermissions=\'{"version":2,"fields":[{"scope":"main","field":"amount","readable":true,"writable":true},{"scope":"child","childKey":"items","childField":"quantity","readable":true,"writable":true}],"children":[{"childKey":"items","readable":true,"allowCreate":true,"allowUpdate":true,"allowDelete":false}]}\'/>',
    ].join('')
    const xml = `<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn"><bpmn:process id="P">${taskXml}</bpmn:process></bpmn:definitions>`
    const parsed = parseUserTaskConfig(getTask(xml, 'T_detail'))

    expect(parsed.formFieldPermissions).toEqual(expect.arrayContaining([
      expect.objectContaining({ field: 'amount', writable: true }),
      expect.objectContaining({ scope: 'child', childKey: 'items', childField: 'quantity', writable: true }),
    ]))
    expect(parsed.formChildPermissions).toEqual([
      expect.objectContaining({ childKey: 'items', allowCreate: true, allowUpdate: true, allowDelete: false }),
    ])

    const written = writeUserTaskConfig(parsed)
    const roundTripXml = `<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn"><bpmn:process id="P"><bpmn:userTask id="T_detail" ${written.attrs}/></bpmn:process></bpmn:definitions>`
    const roundTrip = parseUserTaskConfig(getTask(roundTripXml, 'T_detail'))
    expect(roundTrip.formChildPermissions[0]).toMatchObject({
      childKey: 'items',
      allowCreate: true,
      allowUpdate: true,
      allowDelete: false,
    })
    expect(roundTrip.formFieldPermissions.find(item => item.scope === 'child')).toMatchObject({
      childKey: 'items',
      childField: 'quantity',
      writable: true,
    })
  })
})
