import { describe, expect, it } from 'vitest'
import {
  collectChildTableFieldOptions,
  collectChildTableRelationOptions,
  resolveChildRelationKey,
  resolveChildRelationTargetObjectCode,
} from '../option-source-picker'

describe('option-source-picker', () => {
  it('汇总画布子表、其它表单资产子表与对象明细关系', () => {
    const options = collectChildTableRelationOptions({
      subTableComponents: [{
        componentKey: 'subTable',
        label: '本页明细',
        props: {
          header: '订单明细',
          relationKey: 'order_items',
          targetObjectCode: 'order_item',
          columns: [{ fieldCode: 'skuName', fieldLabel: '商品' }],
        },
      }],
      formAssets: [{
        formKey: 'edit_form',
        formName: '编辑表单',
        schema: {
          components: [{
            componentKey: 'subTable',
            props: {
              header: '退货明细',
              relationKey: 'return_items',
              targetObjectCode: 'return_item',
              columns: [{ fieldCode: 'qty', fieldLabel: '数量' }],
            },
          }],
        },
      }],
      relations: [{
        relationType: 'DETAIL',
        relationName: '合同明细',
        relationKey: 'contract_items',
        targetObjectCode: 'contract_item',
        status: 1,
      }, {
        relationType: 'REFERENCE',
        relationName: '客户',
        relationKey: 'customer',
        targetObjectCode: 'customer',
      }],
    })

    expect(options.map(item => item.value)).toEqual([
      'order_items',
      'return_items',
      'contract_items',
    ])
    expect(options[0].label).toContain('订单明细')
    expect(options[1].label).toContain('编辑表单')
    expect(options[2].label).toContain('对象关系')
  })

  it('同源 relationKey 优先保留画布配置（含 columns）', () => {
    const options = collectChildTableRelationOptions({
      subTableComponents: [{
        componentKey: 'subTable',
        props: {
          relationKey: 'order_items',
          header: '画布明细',
          columns: [{ fieldCode: 'name', fieldLabel: '名称' }],
        },
      }],
      relations: [{
        relationType: 'CHILD_LIST',
        relationKey: 'order_items',
        relationName: '对象关系明细',
        targetObjectCode: 'order_item',
      }],
    })

    expect(options).toHaveLength(1)
    expect(options[0].source).toBe('canvas')
    expect(options[0].columns).toEqual([{ fieldCode: 'name', fieldLabel: '名称' }])
  })

  it('字段候选项优先 columns，否则回落目标对象字段', () => {
    const relationOptions = collectChildTableRelationOptions({
      subTableComponents: [{
        componentKey: 'subTable',
        props: {
          relationKey: 'order_items',
          columns: [{ fieldCode: 'skuName', fieldLabel: '商品' }],
        },
      }],
      relations: [{
        relationType: 'DETAIL',
        relationKey: 'contract_items',
        targetObjectCode: 'contract_item',
      }],
    })

    expect(collectChildTableFieldOptions({
      relationKey: 'order_items',
      relationOptions,
    })).toEqual([{ label: '商品（skuName）', value: 'skuName' }])

    expect(collectChildTableFieldOptions({
      relationKey: 'contract_items',
      relationOptions,
      targetFieldOptions: [{ label: '合同号', value: 'contractNo' }],
    })).toEqual([{ label: '合同号（contractNo）', value: 'contractNo' }])
  })

  it('解析关系键与目标对象编码', () => {
    const relation = {
      relationType: 'ONE_TO_MANY',
      relationConfig: JSON.stringify({
        relationKey: 'presale_items',
        targetObjectCode: 'presale_item',
      }),
    }
    expect(resolveChildRelationKey(relation)).toBe('presale_items')
    expect(resolveChildRelationTargetObjectCode('presale_items', [{
      value: 'presale_items',
      targetObjectCode: 'presale_item',
    }])).toBe('presale_item')
  })
})
