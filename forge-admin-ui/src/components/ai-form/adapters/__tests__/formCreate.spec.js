import { describe, expect, it } from 'vitest'
import { formCreateToAiSchema } from '../formCreate'

describe('formCreateToAiSchema array fields', () => {
  it('preserves group as one array field with nested item schema', () => {
    const schema = formCreateToAiSchema([
      {
        type: 'group',
        field: 'expenseItems',
        title: '费用明细',
        props: {
          rule: [
            { type: 'input', field: 'name', title: '名称' },
            { type: 'inputNumber', field: 'amount', title: '金额', validate: [{ required: true }] },
          ],
          min: 1,
          max: 5,
          button: true,
        },
      },
    ])

    expect(schema).toHaveLength(1)
    expect(schema[0]).toMatchObject({
      field: 'expenseItems',
      type: 'array',
      arrayConfig: { allowCreate: true, allowUpdate: true, allowDelete: true, min: 1, max: 5 },
    })
    expect(schema[0].itemSchema).toEqual([
      expect.objectContaining({ field: 'name', type: 'input' }),
      expect.objectContaining({ field: 'amount', type: 'number', required: true }),
    ])
  })

  it('converts tableForm columns without leaking row fields to the root', () => {
    const schema = formCreateToAiSchema([
      {
        type: 'tableForm',
        field: 'goods',
        title: '商品明细',
        props: {
          addable: false,
          deletable: true,
          columns: [
            { label: '商品', rule: [{ type: 'input', field: 'productName', title: '商品' }] },
            { label: '数量', rule: [{ type: 'inputNumber', field: 'quantity', title: '数量' }] },
          ],
        },
      },
      { type: 'input', field: 'remark', title: '备注' },
    ])

    expect(schema.map(item => item.field)).toEqual(['goods', 'remark'])
    expect(schema[0]).toMatchObject({
      type: 'array',
      arrayConfig: { displayMode: 'table', allowCreate: false, allowDelete: true },
    })
    expect(schema[0].itemSchema.map(item => item.field)).toEqual(['productName', 'quantity'])
  })

  it('keeps ref_ group containers as layouts and continues collecting normal children', () => {
    const schema = formCreateToAiSchema([
      {
        type: 'group',
        field: 'ref_container',
        children: [{ type: 'input', field: 'title', title: '标题' }],
      },
    ])

    expect(schema).toEqual([expect.objectContaining({ field: 'title', type: 'input' })])
  })
})
