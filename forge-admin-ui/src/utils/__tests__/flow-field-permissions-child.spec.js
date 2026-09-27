import { describe, expect, it } from 'vitest'
import { appendChildTableCatalogFields, applyChildTableFieldPermissions, normalizeFlowFieldCatalog, resolvePermissionFieldCatalog } from '../flow-field-permissions'

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

  it('子表 allowUpdate 不能把未授权字段一刀切开写', () => {
    const [child] = applyChildTableFieldPermissions(
      [{
        modelCode: 'cgou_business_object_hl92',
        allowUpdate: true,
        fields: [
          { field: 'fieldInput', writable: false, readonly: true, disabled: true },
          { field: 'fieldInput2', writable: true, readonly: false, disabled: false },
        ],
      }],
      [
        { scope: 'child', childKey: 'business_object_hl92', childField: 'fieldInput', writable: false },
        { scope: 'child', childKey: 'business_object_hl92', childField: 'fieldInput2', writable: true },
      ],
    )

    expect(child.fields[0]).toMatchObject({ field: 'fieldInput', writable: false, readonly: true, disabled: true })
    expect(child.fields[1]).toMatchObject({ field: 'fieldInput2', writable: true, readonly: false, disabled: false })
  })

  it('未命中字段权限时保留后端只读标记，不因 allowUpdate 放开', () => {
    const [child] = applyChildTableFieldPermissions(
      [{
        modelCode: 'cgou_business_object_hl92',
        allowUpdate: true,
        fields: [{ field: 'fieldInput', writable: false, readonly: true, disabled: true }],
      }],
      [],
    )

    expect(child.fields[0]).toMatchObject({ writable: false, readonly: true, disabled: true })
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

describe('resolvePermissionFieldCatalog', () => {
  it('权限目录优先用设计器子表，丢掉发布态已删除子表', () => {
    const catalog = resolvePermissionFieldCatalog(
      [
        { field: 'title', label: '标题' },
        { scope: 'child', childKey: 'removed_child', childField: 'name', label: '已删' },
        { scope: 'child', childKey: 'order_item', childField: 'old_qty', label: '旧数量' },
      ],
      {
        formDesignerSchema: {
          components: [
            {
              componentKey: 'subTable',
              props: {
                modelCode: 'order_item',
                header: '订单明细',
                columns: [{ fieldCode: 'qty', fieldLabel: '数量' }],
              },
            },
          ],
        },
      },
    )

    expect(catalog.find(item => item.field === 'title')).toBeTruthy()
    expect(catalog.find(item => item.childKey === 'removed_child')).toBeFalsy()
    expect(catalog).toEqual(expect.arrayContaining([
      expect.objectContaining({ scope: 'child', childKey: 'order_item', childField: 'qty' }),
    ]))
    expect(catalog.find(item => item.childField === 'old_qty')).toBeFalsy()
  })

  it('设计器已无子表时不再回落发布态子表目录', () => {
    const catalog = resolvePermissionFieldCatalog(
      [
        { field: 'title', label: '标题' },
        { scope: 'child', childKey: 'removed_child', childField: 'name', label: '已删' },
      ],
      { formDesignerSchema: { components: [{ componentKey: 'input', props: { field: 'title' } }] } },
    )

    expect(catalog).toEqual([{ field: 'title', label: '标题' }])
  })
})
