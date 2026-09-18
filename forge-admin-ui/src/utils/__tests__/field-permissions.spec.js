import { describe, expect, it } from 'vitest'
import { normalizeArrayPermissions, normalizeFieldPermissions, pickFirstNonEmptyFieldPermissions } from '../field-permissions.js'

describe('field permissions', () => {
  it('优先读取 readable/writable，兼容 visible/editable 旧字段', () => {
    expect(normalizeFieldPermissions([
      {
        field: 'amount',
        visible: true,
        editable: true,
        readable: false,
        writable: false,
        required: true,
      },
    ])).toEqual([
      {
        field: 'amount',
        fieldCode: 'amount',
        visible: false,
        editable: false,
        readable: false,
        writable: false,
        required: false,
      },
    ])
  })

  it('支持后端 TaskFormInfo 返回的 JSON 字符串', () => {
    const permissions = normalizeFieldPermissions('[{"field":"amount","readable":false,"writable":false}]')
    expect(permissions[0]).toMatchObject({
      field: 'amount',
      visible: false,
      readable: false,
    })
  })

  it('只读场景强制不可编辑', () => {
    const permissions = normalizeFieldPermissions([
      { fieldCode: 'reason', readable: true, writable: true, required: true },
    ], { readOnly: true })
    expect(permissions[0]).toMatchObject({
      field: 'reason',
      visible: true,
      editable: false,
      writable: false,
      required: false,
    })
  })

  it('取第一个非空权限源，避免空数组挡住任务节点权限', () => {
    const permissions = pickFirstNonEmptyFieldPermissions([
      [],
      '',
      '[{"field":"amount","readable":false,"writable":false}]',
    ])
    expect(permissions).toEqual([
      {
        field: 'amount',
        fieldCode: 'amount',
        visible: false,
        editable: false,
        readable: false,
        writable: false,
        required: false,
      },
    ])
  })

  it('数组不可见时强制关闭全部行操作', () => {
    const permissions = normalizeArrayPermissions({
      arrays: [{ arrayKey: 'items', readable: false, allowCreate: true, allowUpdate: true, allowDelete: true }],
    })
    expect(permissions[0]).toMatchObject({
      readable: false,
      allowCreate: false,
      allowUpdate: false,
      allowDelete: false,
    })
  })
})
