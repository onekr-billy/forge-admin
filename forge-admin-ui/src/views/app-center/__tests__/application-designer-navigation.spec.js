import { describe, expect, it } from 'vitest'
import {
  buildApplicationDesignerResourceGroups,
  findApplicationDesignerResource,
  normalizeApplicationDesignerSection,
  resolveApplicationDesignerObject,
  resolveObjectDesignerSectionConfig,
} from '../application-designer-navigation'
import {
  buildSeedTakeoverSummary,
  markSeedTakeoverAccepted,
  requiresSeedTakeoverConfirmation,
} from '../components/designer/seed-takeover'

describe('application designer navigation', () => {
  it('maps legacy section keys to resource groups', () => {
    expect(normalizeApplicationDesignerSection('page')).toBe('pages')
    expect(normalizeApplicationDesignerSection('events')).toBe('pages')
    expect(normalizeApplicationDesignerSection('actions')).toBe('automation')
    expect(normalizeApplicationDesignerSection('business-flow')).toBe('automation')
    expect(normalizeApplicationDesignerSection('data-model')).toBe('data')
    expect(normalizeApplicationDesignerSection('unknown')).toBe('pages')
    expect(resolveObjectDesignerSectionConfig('data-model')).toEqual({
      initialPanel: 'fields',
      navPanels: ['fields', 'relations'],
    })
    expect(resolveObjectDesignerSectionConfig('business-flow')).toBe(null)
  })

  it('builds explicit form, list, custom page and object resource nodes', () => {
    const objects = [
      { objectId: '1910000000000000001', objectCode: 'ORDER', objectName: '订单', objectRole: 'PRIMARY' },
    ]
    const groups = buildApplicationDesignerResourceGroups({
      objects,
      designersByObjectId: {
        '1910000000000000001': {
          formDesignerSchema: { components: [{ id: 'name' }] },
          viewSchema: { list: { enabled: true, fieldRefs: ['name'] } },
        },
      },
      pages: [{ id: 'home', title: '工作台首页', type: 'page' }],
    })

    expect(groups.map(group => group.key)).toEqual(['pages', 'data', 'automation', 'settings'])
    expect(groups[0].nodes).toEqual(expect.arrayContaining([
      expect.objectContaining({ key: 'page-form:1910000000000000001', label: '订单（表单页）', configured: true }),
      expect.objectContaining({ key: 'page-list:1910000000000000001', label: '订单（列表页）', configured: true }),
      expect.objectContaining({ key: 'page-custom:home', label: '工作台首页（自由编排）', pageId: 'home' }),
    ]))
    expect(findApplicationDesignerResource(groups, '', 'events')).toMatchObject({
      key: 'page-form:1910000000000000001',
      objectCode: 'ORDER',
    })
    expect(findApplicationDesignerResource(groups, '', 'page')).toMatchObject({ key: 'page-custom:home' })
    expect(findApplicationDesignerResource(groups, '', 'business-flow')).toMatchObject({
      key: 'automation-processes',
      kind: 'automation-processes',
    })
    expect(findApplicationDesignerResource(groups, '', 'actions')).toMatchObject({
      key: 'automation-processes',
      kind: 'automation-processes',
    })
    // 拆分前的 business-actions:<objectId> 旧书签落到业务流程列表。
    expect(findApplicationDesignerResource(groups, 'business-actions:1910000000000000001')).toMatchObject({
      key: 'automation-processes',
    })
    expect(findApplicationDesignerResource(groups, '', 'data-model')).toMatchObject({
      key: 'data-fields:1910000000000000001',
      objectCode: 'ORDER',
    })
    expect(findApplicationDesignerResource(groups, 'data:1910000000000000001')).toMatchObject({
      key: 'data-fields:1910000000000000001',
    })
    expect(groups[1].nodes.map(node => node.kind)).toEqual(['data-fields', 'data-relations'])
    expect(findApplicationDesignerResource(groups, '', 'settings')).toMatchObject({
      key: 'settings',
      kind: 'settings',
    })
  })

  it('restores the application-level enhancement resource and resolves the legacy bookmark', () => {
    const groups = buildApplicationDesignerResourceGroups({
      objects: [],
      extensions: [{ id: 'extension-1' }],
    })

    expect(groups.find(group => group.key === 'automation')?.nodes).toContainEqual(expect.objectContaining({
      key: 'automation-enhancements',
      kind: 'automation-enhancements',
      label: '动作增强（JS / CSS / Java）',
      configured: true,
    }))
    expect(findApplicationDesignerResource(groups, '', 'automation-enhancements')).toMatchObject({
      key: 'automation-enhancements',
    })
  })

  it('keeps the automation group as process list plus enhancements', () => {
    const groups = buildApplicationDesignerResourceGroups({
      objects: [
        { objectId: '1', objectCode: 'ORDER', objectName: '订单' },
        { objectId: '2', objectCode: 'ITEM', objectName: '明细' },
      ],
      designersByObjectId: {
        1: {
          triggerConfigured: true,
          designerOptions: { actions: [{ actionCode: 'submit' }] },
          documentConfig: { mainFlowSummary: { flowModelKey: 'order_approval' } },
        },
        2: {
          triggerConfigured: false,
          designerOptions: { actions: [] },
          documentConfig: { mainFlowSummary: {} },
        },
      },
    })
    const automation = groups.find(group => group.key === 'automation')

    // 对象级动作面板已并入业务流程画布，automation 组只保留流程列表与动作增强。
    expect(automation.nodes.map(node => node.kind)).toEqual(['automation-processes', 'automation-enhancements'])
    expect(automation.nodes.some(node => node.kind === 'business-actions')).toBe(false)
    expect(automation).toMatchObject({ configuredCount: 1, totalCount: 2 })
  })

  it('uses an explicit object, then the primary object, then the first object', () => {
    const objects = [
      { objectId: 1, objectCode: 'DETAIL', objectRole: 'SHARED' },
      { objectId: 2, objectCode: 'ORDER', objectRole: 'PRIMARY' },
    ]
    expect(resolveApplicationDesignerObject(objects, 1).objectCode).toBe('DETAIL')
    expect(resolveApplicationDesignerObject(objects, 99).objectCode).toBe('ORDER')
    expect(resolveApplicationDesignerObject([{ objectId: 3, objectCode: 'ONLY' }]).objectCode).toBe('ONLY')
  })

  it('recognizes legacy list zones without hiding disabled list resources', () => {
    const objects = [{ objectId: '1', objectCode: 'ORDER', objectName: '订单' }]
    const configured = buildApplicationDesignerResourceGroups({
      objects,
      designersByObjectId: { 1: { viewSchema: { zones: [{ zoneKey: 'table' }] } } },
    })
    const disabled = buildApplicationDesignerResourceGroups({
      objects,
      designersByObjectId: { 1: { viewSchema: { list: { enabled: false, columns: [{ fieldCode: 'name' }] } } } },
    })

    expect(configured[0].nodes.find(node => node.kind === 'page-list')).toMatchObject({ configured: true })
    expect(disabled[0].nodes.find(node => node.kind === 'page-list')).toMatchObject({ configured: false })
  })
})

describe('seed configuration takeover', () => {
  it('requires one explicit confirmation and summarizes governed changes', () => {
    const designer = {
      formDesignerSchema: { components: [{ id: 'mobile' }], pageSections: [{ sectionId: 'member' }] },
      designerOptions: { seedKey: 'presale-registration-v1', actions: [{ actionCode: 'submit' }] },
    }
    const draft = {
      formDesignerSchema: {
        components: [{ id: 'mobile' }, { id: 'pay', children: [{ id: 'amount' }] }],
        pageSections: [{ sectionId: 'member' }, { sectionId: 'payment' }],
      },
      designerOptions: { ...designer.designerOptions, actions: [{}, {}] },
    }

    expect(requiresSeedTakeoverConfirmation(designer)).toBe(true)
    expect(buildSeedTakeoverSummary(designer, draft)).toBe('表单组件 1 -> 3，页面分区 1 -> 2，业务动作 1 -> 2')

    const accepted = markSeedTakeoverAccepted(designer.designerOptions, '2026-08-14T00:00:00.000Z')
    expect(accepted.seedKey).toBe('presale-registration-v1')
    expect(accepted.seedTakeover).toEqual({ accepted: true, acceptedAt: '2026-08-14T00:00:00.000Z' })
    expect(requiresSeedTakeoverConfirmation({ designerOptions: accepted })).toBe(false)
  })
})
