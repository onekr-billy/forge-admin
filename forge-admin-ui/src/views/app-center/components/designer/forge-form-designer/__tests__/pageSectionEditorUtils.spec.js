import { describe, expect, it } from 'vitest'
import {
  collectPageSectionWarnings,
  createBottomAction,
  createPageSection,
  isSupportedDisplayCondition,
  parseDisplayCondition,
  resolveVisibleModes,
  serializeDisplayCondition,
  updateVisibleModes,
} from '../pageSectionEditorUtils'

describe('pageSectionEditorUtils', () => {
  it('creates unique card and child-table sections with runtime defaults', () => {
    const first = createPageSection('card', [])
    const second = createPageSection('card', [first])
    const child = createPageSection('child_table', [first, second])

    expect(first).toMatchObject({ sectionId: 'content_section', sectionType: 'card', fields: [] })
    expect(second.sectionId).toBe('content_section_2')
    expect(child).toMatchObject({
      sectionId: 'child_section',
      sectionType: 'child_table',
      displayMode: 'inline_grid',
      inlineCreateEnabled: true,
      inlineEditEnabled: true,
      selectorEnabled: false,
      selectorMappings: [],
      selectorFilters: [],
    })
  })

  it('creates every supported bottom action type', () => {
    const save = createBottomAction('save')
    const action = createBottomAction('action', [save])
    const navigate = createBottomAction('navigate', [save, action])
    const process = createBottomAction('process', [save, action, navigate])

    expect(save).toMatchObject({ type: 'save', label: '保存', variant: 'primary' })
    expect(action).toMatchObject({ type: 'action', label: '执行动作', actionCode: '' })
    expect(action.actionId).toBe('bottom_action_2')
    expect(navigate).toMatchObject({ type: 'navigate', label: '跳转', actionCode: '' })
    expect(process).toMatchObject({ type: 'process', label: '发起流程', actionCode: '' })
  })

  it('validates process-backed button actions without treating process codes as legacy actions', () => {
    const warnings = collectPageSectionWarnings({
      bottomBar: {
        actions: [
          { type: 'process', label: '提交审批', actionCode: 'order_submit' },
          { type: 'action', actionType: 'BUSINESS_PROCESS_ACTION', label: '执行自动化', actionCode: 'order_auto' },
          { type: 'process', label: '未配置流程', actionCode: '' },
        ],
      },
      actions: [],
    })

    expect(warnings).toHaveLength(1)
    expect(warnings[0].message).toContain('尚未选择业务流程')
  })

  it('round-trips controlled display conditions used by the H5 runtime', () => {
    expect(parseDisplayCondition('status == DRAFT')).toEqual({ field: 'status', operator: '==', value: 'DRAFT' })
    expect(serializeDisplayCondition({ field: 'status', operator: '!=', value: 'CLOSED' })).toBe('status != CLOSED')
    expect(serializeDisplayCondition({ field: 'customerName', operator: '==', value: '张 三' })).toBe('customerName == "张 三"')
    expect(serializeDisplayCondition({ field: '', operator: '==', value: 'DRAFT' })).toBe('')
    expect(isSupportedDisplayCondition('status == DRAFT')).toBe(true)
    expect(isSupportedDisplayCondition('status.includes(DRAFT)')).toBe(false)
  })

  it('keeps at least one visible mode so an empty selection cannot become all modes', () => {
    expect(resolveVisibleModes({})).toEqual(['create', 'edit', 'detail'])
    expect(resolveVisibleModes({ visibleInModes: [] })).toEqual(['create', 'edit', 'detail'])
    expect(updateVisibleModes({ visibleInModes: ['detail'] }, [])).toEqual(['detail'])
    expect(updateVisibleModes({ visibleInModes: ['create', 'edit'] }, ['edit'])).toEqual(['edit'])
  })

  it('reports invalid references without removing them', () => {
    const pageSections = [
      { sectionId: 'main', sectionType: 'card', title: '基础信息', fields: ['name', 'removedField'] },
      { sectionId: 'items', sectionType: 'child_table', title: '商品明细', relationKey: 'removed_relation' },
    ]
    const bottomBar = {
      actions: [{ type: 'action', label: '提交', actionCode: 'removed_action', displayCondition: 'removedStatus == DRAFT' }],
    }

    const warnings = collectPageSectionWarnings({
      pageSections,
      bottomBar,
      fields: [{ fieldCode: 'name' }],
      relations: [],
      actions: [],
    })

    expect(pageSections[0].fields).toEqual(['name', 'removedField'])
    expect(warnings.map(item => item.message)).toEqual(expect.arrayContaining([
      expect.stringContaining('removedField'),
      expect.stringContaining('removed_relation'),
      expect.stringContaining('removed_action'),
      expect.stringContaining('removedStatus'),
    ]))
  })

  it('warns when an existing display condition cannot be edited by the controlled condition builder', () => {
    const warnings = collectPageSectionWarnings({
      bottomBar: {
        actions: [{ type: 'save', label: '保存', displayCondition: 'status.includes(DRAFT)' }],
      },
      fields: [{ fieldCode: 'status' }],
    })

    expect(warnings).toEqual(expect.arrayContaining([
      expect.objectContaining({
        key: 'action:0:condition:unsupported',
        message: expect.stringContaining('不支持的显示条件'),
      }),
    ]))
  })

  it('reports incomplete child selectors without rewriting their interaction config', () => {
    const pageSections = [{
      sectionId: 'items',
      sectionType: 'child_table',
      title: '商品明细',
      relationKey: 'items',
      selectorEnabled: true,
      selectorObjectCode: '',
      selectorMappings: [{ sourceField: 'skuCode', targetField: '' }],
      selectorFilters: [{ sourceField: 'storeId', targetParam: '' }],
    }]

    const warnings = collectPageSectionWarnings({
      pageSections,
      relations: [{ relationKey: 'items' }],
    })

    expect(warnings.map(item => item.message)).toEqual(expect.arrayContaining([
      expect.stringContaining('候选对象'),
      expect.stringContaining('字段映射'),
      expect.stringContaining('筛选条件'),
    ]))
    expect(pageSections[0].selectorMappings).toEqual([{ sourceField: 'skuCode', targetField: '' }])
  })
})
