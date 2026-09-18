import { describe, expect, it } from 'vitest'
import { buildLocalFormFieldCatalog } from '../form-field-catalog'

describe('buildLocalFormFieldCatalog', () => {
  it('递归解析 form-create children 中的业务字段', () => {
    const catalog = buildLocalFormFieldCatalog([
      {
        type: 'group',
        field: 'ref_container',
        title: '分组',
        children: [
          {
            type: 'inputNumber',
            field: 'amount',
            title: '总金额',
            validate: [{ required: true }],
          },
          {
            type: 'input',
            field: 'jtpNo',
            title: 'JTPO单号',
          },
        ],
      },
    ])

    expect(catalog).toEqual([
      {
        field: 'amount',
        label: '总金额',
        componentType: 'inputNumber',
        dataType: 'number',
        required: true,
        optionSource: '',
        source: 'model-inline',
      },
      {
        field: 'jtpNo',
        label: 'JTPO单号',
        componentType: 'input',
        dataType: 'string',
        required: false,
        optionSource: '',
        source: 'model-inline',
      },
    ])
  })

  it('支持从 _forge.fieldBinding 兜底解析字段编码', () => {
    const catalog = buildLocalFormFieldCatalog([
      {
        type: 'select',
        title: '审批类型',
        name: 'ref_Fabc',
        _forge: {
          fieldBinding: {
            fieldCode: 'approveType',
          },
        },
        props: {
          dictType: 'sys_approve_type',
        },
      },
    ])

    expect(catalog[0]).toMatchObject({
      field: 'approveType',
      label: '审批类型',
      dataType: 'enum',
      optionSource: 'sys_approve_type',
    })
  })

  it('忽略 form-create 自动生成的 ref_ 字段', () => {
    const catalog = buildLocalFormFieldCatalog([
      { type: 'input', field: 'ref_abc', title: '临时字段' },
      { type: 'input', field: 'title', title: '标题' },
    ])

    expect(catalog.map(item => item.field)).toEqual(['title'])
  })

  it('保留数组父字段并按 arrayKey 标记行字段', () => {
    const catalog = buildLocalFormFieldCatalog([
      {
        type: 'group',
        field: 'expenseItems',
        title: '费用明细',
        props: {
          rule: [
            { type: 'input', field: 'name', title: '名称' },
            { type: 'inputNumber', field: 'amount', title: '金额' },
          ],
        },
      },
      {
        type: 'group',
        field: 'otherItems',
        title: '其他明细',
        props: { rule: [{ type: 'input', field: 'name', title: '名称' }] },
      },
    ])

    expect(catalog).toEqual([
      expect.objectContaining({ field: 'expenseItems', dataType: 'array' }),
      expect.objectContaining({ scope: 'array', arrayKey: 'expenseItems', itemField: 'name' }),
      expect.objectContaining({ scope: 'array', arrayKey: 'expenseItems', itemField: 'amount' }),
      expect.objectContaining({ field: 'otherItems', dataType: 'array' }),
      expect.objectContaining({ scope: 'array', arrayKey: 'otherItems', itemField: 'name' }),
    ])
  })
})
