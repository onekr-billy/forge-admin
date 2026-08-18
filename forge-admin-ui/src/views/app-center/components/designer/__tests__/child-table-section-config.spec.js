import { describe, expect, it } from 'vitest'
import {
  removeChildTableSectionConfig,
  resolveChildTableSectionEditConfig,
  upsertChildTableSectionConfig,
} from '../child-table-section-config'

describe('child table section config', () => {
  it('upserts the model ref, runtime child config and form section as one operation', () => {
    const source = {
      pageSchema: {
        layoutType: 'master-detail-crud',
        modelRefs: [{ modelCode: 'order', primary: true }],
        options: { keepExisting: true },
      },
      formDesignerSchema: {
        formKey: 'order_form',
        pageSections: [],
      },
    }
    const config = {
      relationKey: 'order_items',
      title: '订单明细',
      displayMode: 'inline_grid',
      modelCode: 'order_item',
      modelName: '订单明细',
      tableName: 'biz_order_item',
      relation: {
        relationType: 'DETAIL',
        targetObjectCode: 'order_item',
        sourceFieldCode: 'id',
        targetFieldCode: 'order_id',
      },
      fields: [
        { fieldCode: 'productName', fieldName: '商品名称', componentType: 'input' },
        { fieldCode: 'quantity', fieldName: '数量', componentType: 'number' },
      ],
    }

    const result = upsertChildTableSectionConfig(source, config)

    expect(result.pageSchema.layoutType).toBe('master-detail-crud')
    expect(result.pageSchema.options.keepExisting).toBe(true)
    expect(result.pageSchema.modelRefs).toHaveLength(2)
    expect(result.pageSchema.modelRefs[1]).toMatchObject({
      modelCode: 'order_item',
      modelName: '订单明细',
      tableName: 'biz_order_item',
      primary: false,
      props: {
        relationKey: 'order_items',
        saveMode: 'CASCADE',
        inlineCreateEnabled: true,
        inlineEditEnabled: true,
        showInDetail: true,
      },
    })
    expect(result.pageSchema.modelRefs[1].fields).toEqual([
      expect.objectContaining({ field: 'productName', fieldRef: 'order_item__productName' }),
      expect.objectContaining({ field: 'quantity', fieldRef: 'order_item__quantity' }),
    ])
    expect(result.pageSchema.modelRefs[1].relations[0]).toMatchObject({
      sourceObjectCode: 'order_item',
      targetObjectCode: 'order',
      sourceField: 'order_id',
      targetField: 'id',
    })

    const children = result.pageSchema.options.masterDetailConfig.children
    expect(children).toHaveLength(1)
    expect(children[0]).toMatchObject({
      key: 'order_items',
      relationKey: 'order_items',
      modelCode: 'order_item',
      tableName: 'biz_order_item',
      saveMode: 'CASCADE',
      showInEdit: true,
      showInDetail: true,
      rowActions: [],
      toolbarActions: [],
    })
    expect(children[0].fields.map(field => field.field)).toEqual(['productName', 'quantity'])

    expect(result.formDesignerSchema.pageSections).toEqual([
      expect.objectContaining({
        sectionId: 'child_order_items',
        sectionKey: 'child_order_items',
        sectionType: 'child_table',
        type: 'child_table',
        title: '订单明细',
        displayMode: 'inline_grid',
        relationKey: 'order_items',
      }),
    ])
  })

  it('updates the same relation without creating duplicates and preserves custom actions', () => {
    const first = upsertChildTableSectionConfig({ pageSchema: {}, formDesignerSchema: {} }, {
      relationKey: 'order_items',
      title: '明细',
      modelCode: 'order_item',
      tableName: 'biz_order_item',
      fields: [{ fieldCode: 'quantity', fieldName: '数量' }],
    })
    first.pageSchema.options.masterDetailConfig.children[0].rowActions = [{ actionCode: 'view' }]

    const result = upsertChildTableSectionConfig(first, {
      relationKey: 'order_items',
      title: '商品明细',
      displayMode: 'card_list',
      modelCode: 'order_item',
      tableName: 'biz_order_item',
      fields: [{ fieldCode: 'amount', fieldName: '金额' }],
    })

    expect(result.pageSchema.modelRefs).toHaveLength(1)
    expect(result.pageSchema.options.masterDetailConfig.children).toHaveLength(1)
    expect(result.pageSchema.options.masterDetailConfig.children[0].rowActions).toEqual([{ actionCode: 'view' }])
    expect(result.formDesignerSchema.pageSections).toHaveLength(1)
    expect(result.formDesignerSchema.pageSections[0]).toMatchObject({
      title: '商品明细',
      displayMode: 'card_list',
    })
  })

  it('keeps separate relations that point to the same child object', () => {
    const first = upsertChildTableSectionConfig({ pageSchema: {}, formDesignerSchema: {} }, {
      relationKey: 'sale_items',
      modelCode: 'order_item',
      fields: [{ fieldCode: 'quantity' }],
    })
    const result = upsertChildTableSectionConfig(first, {
      relationKey: 'return_items',
      modelCode: 'order_item',
      fields: [{ fieldCode: 'reason' }],
    })

    expect(result.pageSchema.modelRefs).toHaveLength(2)
    expect(result.pageSchema.options.masterDetailConfig.children).toHaveLength(2)
    expect(result.formDesignerSchema.pageSections).toHaveLength(2)
  })

  it('resolves the current section values for editing in the wizard', () => {
    const source = upsertChildTableSectionConfig({ pageSchema: {}, formDesignerSchema: {} }, {
      relationKey: 'order_items',
      title: '商品明细',
      displayMode: 'card_list',
      modelCode: 'order_item',
      modelName: '订单明细',
      tableName: 'biz_order_item',
      fields: [
        { fieldCode: 'productName', fieldName: '商品名称' },
        { fieldCode: 'quantity', fieldName: '数量' },
      ],
    })

    expect(resolveChildTableSectionEditConfig(
      source,
      source.formDesignerSchema.pageSections[0],
    )).toMatchObject({
      relationKey: 'order_items',
      title: '商品明细',
      displayMode: 'card_list',
      modelCode: 'order_item',
      modelName: '订单明细',
      tableName: 'biz_order_item',
      fieldCodes: ['productName', 'quantity'],
    })
  })

  it('removes all three child-table layers without touching another relation to the same object', () => {
    const first = upsertChildTableSectionConfig({ pageSchema: {}, formDesignerSchema: {} }, {
      relationKey: 'sale_items',
      title: '销售明细',
      modelCode: 'order_item',
      fields: [{ fieldCode: 'quantity' }],
    })
    const source = upsertChildTableSectionConfig(first, {
      relationKey: 'return_items',
      title: '退货明细',
      modelCode: 'order_item',
      fields: [{ fieldCode: 'reason' }],
    })

    const result = removeChildTableSectionConfig(source, { relationKey: 'sale_items' })

    expect(result.formDesignerSchema.pageSections.map(item => item.relationKey)).toEqual(['return_items'])
    expect(result.pageSchema.options.masterDetailConfig.children.map(item => item.relationKey)).toEqual(['return_items'])
    expect(result.pageSchema.modelRefs).toHaveLength(1)
    expect(result.pageSchema.modelRefs[0].props.relationKey).toBe('return_items')
  })

  it('keeps a legacy shared model ref while another child config still uses its model', () => {
    const source = {
      pageSchema: {
        modelRefs: [{ modelCode: 'order_item', primary: false }],
        options: {
          masterDetailConfig: {
            children: [
              { relationKey: 'sale_items', modelCode: 'order_item' },
              { relationKey: 'return_items', modelCode: 'order_item' },
            ],
          },
        },
      },
      formDesignerSchema: {
        pageSections: [
          { sectionId: 'sale', sectionType: 'child_table', relationKey: 'sale_items' },
          { sectionId: 'return', sectionType: 'child_table', relationKey: 'return_items' },
        ],
      },
    }

    const result = removeChildTableSectionConfig(source, { relationKey: 'sale_items' })

    expect(result.pageSchema.modelRefs).toHaveLength(1)
    expect(result.pageSchema.options.masterDetailConfig.children).toEqual([
      expect.objectContaining({ relationKey: 'return_items' }),
    ])
  })
})
