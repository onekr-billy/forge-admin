import { describe, expect, it } from 'vitest'
import { appendChildTableCatalogFields, applyChildTableFieldPermissions, normalizeFlowFieldCatalog } from '../flow-field-permissions'

describe('appendChildTableCatalogFields', () => {
  it('把明细表 columns 补进流程模型表单权限目录', () => {
    const catalog = appendChildTableCatalogFields(
      [{ field: 'title', label: '标题' }],
      {
        formDesignerSchema: {
          components: [
            {
              componentKey: 'row',
              children: [
                {
                  componentKey: 'subTable',
                  label: '明细',
                  props: {
                    modelCode: 'order_item',
                    relationKey: 'order_items',
                    header: '订单明细',
                    columns: [
                      { fieldCode: 'qty', fieldLabel: '数量' },
                      { fieldCode: 'amount', fieldLabel: '金额' },
                    ],
                  },
                },
              ],
            },
          ],
        },
      },
    )

    const normalized = normalizeFlowFieldCatalog(catalog)
    expect(normalized).toEqual(expect.arrayContaining([
      expect.objectContaining({ scope: 'main', field: 'title' }),
      expect.objectContaining({
        scope: 'child',
        childKey: 'order_item',
        childField: 'qty',
        label: '数量',
      }),
      expect.objectContaining({
        scope: 'child',
        childKey: 'order_item',
        childField: 'amount',
      }),
    ]))
    expect(normalized.filter(item => item.scope === 'child')).toHaveLength(2)
  })

  it('同一张子表的关系键和对象编码只保留一套字段', () => {
    const normalized = normalizeFlowFieldCatalog([
      {
        scope: 'child',
        childKey: 'detail_ujpc',
        childField: 'fieldInput',
        childLabel: '测试子表',
        label: '输入框',
      },
      {
        scope: 'child',
        childKey: 'cgou_detail_ujpc',
        childField: 'fieldInput',
        childLabel: 'cgou_detail_ujpc',
        label: '输入框',
      },
      {
        scope: 'child',
        childKey: 'cgou_detail_ujpc',
        childField: 'fieldNumber',
        label: '数字',
      },
    ])

    const children = normalized.filter(item => item.scope === 'child')
    expect(children).toHaveLength(2)
    expect(children.every(item => item.childKey === 'cgou_detail_ujpc')).toBe(true)
    expect(children.every(item => item.childLabel === '测试子表')).toBe(true)
  })

  it('已勾选可编辑的子表字段会打开已有行', () => {
    const [child] = applyChildTableFieldPermissions(
      [{
        modelCode: 'cgou_detail_ujpc',
        allowUpdate: false,
        fields: [{ field: 'fieldInput', writable: false, readonly: true }],
      }],
      [{ scope: 'child', childKey: 'detail_ujpc', childField: 'fieldInput', writable: true }],
    )

    expect(child.allowUpdate).toBe(true)
    expect(child.fields[0]).toMatchObject({ writable: true, readonly: false, disabled: false })
  })

  it('节点原始权限能打开已被标成只读的子表字段', () => {
    const raw = JSON.stringify({
      version: 2,
      fields: [
        { field: 'fieldInput', writable: true },
        { scope: 'child', childKey: 'cgou_detail_ujpc', childField: 'fieldInput', writable: true },
      ],
    })
    const [child] = applyChildTableFieldPermissions(
      [{
        modelCode: 'cgou_detail_ujpc',
        allowUpdate: true,
        fields: [{
          field: 'fieldInput',
          writable: false,
          readonly: true,
          disabled: true,
          props: { readonly: true, disabled: true },
        }],
      }],
      [[{ field: 'fieldInput', writable: true, scope: 'main' }], raw],
    )

    expect(child.fields[0].writable).toBe(true)
    expect(child.fields[0].props.readonly).toBeUndefined()
    expect(child.fields[0].props.disabled).toBeUndefined()
  })
})
