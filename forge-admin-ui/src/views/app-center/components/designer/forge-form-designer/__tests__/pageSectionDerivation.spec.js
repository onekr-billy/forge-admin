import { describe, expect, it } from 'vitest'
import { derivePageSectionsFromLayout } from '../pageSectionDerivation'

function fieldComponent(id, fieldCode) {
  return { id, componentKey: 'input', fieldBinding: { fieldCode } }
}

describe('derivePageSectionsFromLayout', () => {
  it('derives card sections from layout containers with their nested fields', () => {
    const sections = derivePageSectionsFromLayout([
      {
        id: 'card_1',
        componentKey: 'card',
        props: { header: '订单信息' },
        children: [fieldComponent('f1', 'orderNo'), fieldComponent('f2', 'amount')],
      },
      {
        id: 'collapse_1',
        componentKey: 'collapse',
        props: { header: '补充说明' },
        children: [fieldComponent('f3', 'remark')],
      },
    ])

    expect(sections).toHaveLength(2)
    expect(sections[0]).toMatchObject({ sectionId: 'card_1', sectionType: 'card', title: '订单信息', fields: ['orderNo', 'amount'] })
    expect(sections[1]).toMatchObject({ sectionId: 'collapse_1', sectionType: 'card', title: '补充说明', fields: ['remark'] })
  })

  it('derives child-table sections from subTable containers after card sections', () => {
    const sections = derivePageSectionsFromLayout([
      {
        id: 'card_1',
        componentKey: 'card',
        props: { header: '基本信息' },
        children: [fieldComponent('f1', 'orderNo')],
      },
      {
        id: 'sub_1',
        componentKey: 'subTable',
        props: { header: '订单明细', relationKey: 'orderItems', displayMode: 'card_list' },
      },
    ])

    expect(sections).toHaveLength(2)
    expect(sections[0]).toMatchObject({ sectionId: 'card_1', sectionType: 'card' })
    expect(sections[1]).toMatchObject({
      // sectionId 按 relationKey 锚定（与子表分区向导链路的命名规则一致），不依赖组件 id。
      sectionId: 'child_order_items',
      sectionType: 'child_table',
      title: '订单明细',
      relationKey: 'orderItems',
      displayMode: 'card_list',
    })
  })

  it('collects loose fields into the default basic-info section', () => {
    const sections = derivePageSectionsFromLayout([
      fieldComponent('f1', 'orderNo'),
      {
        id: 'sub_1',
        componentKey: 'subTable',
        props: { relationKey: 'orderItems' },
      },
    ])

    expect(sections).toHaveLength(2)
    expect(sections[0]).toMatchObject({ sectionId: 'section_default', sectionType: 'card', title: '基本信息', fields: ['orderNo'] })
    expect(sections[1]).toMatchObject({ sectionId: 'child_order_items', sectionType: 'child_table', displayMode: 'inline_grid' })
  })

  it('anchors child-table sectionId to legacy sections by relationKey', () => {
    const sections = derivePageSectionsFromLayout([
      {
        id: 'subtable_orderItems',
        componentKey: 'subTable',
        props: { relationKey: 'orderItems' },
      },
    ], [
      { sectionId: 'child_legacy_custom', sectionType: 'child_table', relationKey: 'orderItems', title: '存量子表' },
    ])

    expect(sections).toHaveLength(1)
    expect(sections[0]).toMatchObject({ sectionId: 'child_legacy_custom', relationKey: 'orderItems', title: '关联子表' })
  })

  it('falls back to component id for subTable containers without relationKey', () => {
    const sections = derivePageSectionsFromLayout([
      { id: 'sub_blank', componentKey: 'subTable', props: {} },
    ])

    expect(sections).toHaveLength(1)
    expect(sections[0]).toMatchObject({ sectionId: 'sub_blank', sectionType: 'child_table', relationKey: '' })
  })

  it('prefers explicit props.sectionId and inherits its legacy extension keys', () => {
    const sections = derivePageSectionsFromLayout([
      {
        id: 'subtable_pickup_return',
        componentKey: 'subTable',
        props: { sectionId: 'pickup_return', relationKey: 'presale_items', header: '提货 / 退货', displayMode: 'inline_grid' },
      },
    ], [
      { sectionId: 'pickup_return', sectionType: 'child_table', relationKey: 'presale_items', title: '提货 / 退货', displayMode: 'inline_grid', visibleInModes: ['edit', 'detail'] },
      { sectionId: 'presale_items', sectionType: 'child_table', relationKey: 'presale_items', title: '商品明细', visibleInModes: ['create'] },
    ])

    expect(sections).toHaveLength(1)
    expect(sections[0]).toMatchObject({ sectionId: 'pickup_return', relationKey: 'presale_items', title: '提货 / 退货' })
    expect(sections[0].visibleInModes).toEqual(['edit', 'detail'])
  })

  it('anchors dual subTable containers of the same relationKey to distinct legacy sections', () => {
    const sections = derivePageSectionsFromLayout([
      {
        id: 'subtable_presale_items',
        componentKey: 'subTable',
        props: { sectionId: 'presale_items', relationKey: 'presale_items', header: '商品明细', displayMode: 'inline_grid' },
      },
      {
        id: 'subtable_pickup_return',
        componentKey: 'subTable',
        props: { sectionId: 'pickup_return', relationKey: 'presale_items', header: '提货 / 退货', displayMode: 'inline_grid' },
      },
    ], [
      { sectionId: 'presale_items', sectionType: 'child_table', relationKey: 'presale_items', title: '商品明细', visibleInModes: ['create'] },
      { sectionId: 'pickup_return', sectionType: 'child_table', relationKey: 'presale_items', title: '提货 / 退货', visibleInModes: ['edit', 'detail'] },
    ])

    expect(sections).toHaveLength(2)
    expect(sections[0]).toMatchObject({ sectionId: 'presale_items', visibleInModes: ['create'] })
    expect(sections[1]).toMatchObject({ sectionId: 'pickup_return', visibleInModes: ['edit', 'detail'] })
  })

  it('keeps legacy sections untouched when the layout has no section containers', () => {
    const legacy = [
      { sectionId: 'legacy_1', sectionType: 'card', title: '存量分区', fields: ['orderNo'] },
    ]

    expect(derivePageSectionsFromLayout([fieldComponent('f1', 'orderNo')], legacy)).toBe(legacy)
    expect(derivePageSectionsFromLayout([], legacy)).toBe(legacy)
  })

  it('inherits legacy extension keys by sectionId while the layout wins on skeleton keys', () => {
    const sections = derivePageSectionsFromLayout([
      {
        id: 'card_1',
        componentKey: 'card',
        props: { header: '新标题' },
        children: [fieldComponent('f1', 'orderNo')],
      },
    ], [
      { sectionId: 'card_1', sectionType: 'child_table', title: '旧标题', fields: ['legacyField'], visibleInModes: ['create', 'edit'] },
    ])

    expect(sections).toHaveLength(1)
    expect(sections[0]).toMatchObject({ sectionId: 'card_1', sectionType: 'card', title: '新标题', fields: ['orderNo'], visibleInModes: ['create', 'edit'] })
  })

  it('treats nested containers as part of the outer section instead of separate sections', () => {
    const sections = derivePageSectionsFromLayout([
      {
        id: 'card_1',
        componentKey: 'card',
        props: { header: '外层分组' },
        children: [
          {
            id: 'collapse_1',
            componentKey: 'collapse',
            props: { header: '内层折叠' },
            children: [fieldComponent('f1', 'remark')],
          },
        ],
      },
    ])

    expect(sections).toHaveLength(1)
    expect(sections[0]).toMatchObject({ sectionId: 'card_1', fields: ['remark'] })
  })
})
