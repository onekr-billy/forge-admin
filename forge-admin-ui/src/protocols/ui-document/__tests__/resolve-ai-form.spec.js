import { describe, expect, it } from 'vitest'
import {
  mapUiDocumentComponentsToAiFormSchema,
  resolveAiFormSchemaFromUiDocument,
  resolveBusinessTaskAiFormSchema,
} from '../index'

describe('resolveAiFormSchemaFromUiDocument', () => {
  it('无 context / 无 fields 时返回空数组', () => {
    expect(resolveAiFormSchemaFromUiDocument(null)).toEqual([])
    expect(resolveBusinessTaskAiFormSchema({})).toEqual([])
  })

  it('无 protocolVersion 时原样返回 fields', () => {
    const fields = [{ field: 'b' }, { field: 'a' }]
    expect(resolveAiFormSchemaFromUiDocument({ fields })).toEqual(fields)
  })

  it('按 sections 重排', () => {
    const fields = [
      { field: 'amount' },
      { field: 'title' },
      { field: 'extra' },
    ]
    const result = resolveAiFormSchemaFromUiDocument({
      protocolVersion: '1',
      uiDocument: {
        version: '1',
        sections: [{ fields: ['title', 'amount'] }],
        components: [],
      },
      fields,
    })
    expect(result.map(f => f.field)).toEqual(['title', 'amount', 'extra'])
  })

  it('优先 components 布局树，字段定义仍取自 fields', () => {
    const fields = [
      { field: 'amount', writable: false, type: 'number' },
      { field: 'title', writable: true, type: 'input' },
      { field: 'extra', writable: false },
    ]
    const result = resolveAiFormSchemaFromUiDocument({
      protocolVersion: '1',
      uiDocument: {
        version: '1',
        components: [
          {
            type: 'card',
            children: [
              { type: 'input', field: 'title', visible: true, editable: true },
              { type: 'input', field: 'amount', visible: true, editable: false },
            ],
          },
        ],
      },
      fields,
    })
    expect(result[0].nodeType).toBe('card')
    expect(result[0].children[0]).toMatchObject({ field: 'title', writable: true })
    expect(result[0].children[1]).toMatchObject({
      field: 'amount',
      readonly: true,
      disabled: true,
    })
    // 未入画布的 extra 不应再被追加到审批表单
    expect(result).toHaveLength(1)
    expect(result.flatMap(node => node.children || []).map(f => f.field)).toEqual(['title', 'amount'])
  })

  it('有组件树时不回灌 fields 里未入表单的字段', () => {
    const result = resolveAiFormSchemaFromUiDocument({
      protocolVersion: '1',
      uiDocument: {
        version: '1',
        components: [
          { type: 'input', field: 'title', editable: true },
        ],
      },
      fields: [
        { field: 'title', type: 'input' },
        { field: 'fieldSwitch', type: 'switch', required: true },
        { field: 'fieldInput5', type: 'input' },
      ],
    })
    expect(result.map(f => f.field)).toEqual(['title'])
  })

  it('叠回 uiDocument 节点 props / 组件类型，避免 optionSource、fieldMappings、userSelect 丢失', () => {
    const fields = [
      { field: 'ownerId', type: 'select', label: '负责人', props: { placeholder: '请选择' } },
      {
        field: 'metricId',
        type: 'select',
        label: '指标',
        props: { optionSource: { type: 'QUERY_SOURCE', sourceKey: 'old' } },
      },
    ]
    const result = resolveAiFormSchemaFromUiDocument({
      protocolVersion: '1',
      uiDocument: {
        version: '1',
        components: [
          {
            type: 'userSelect',
            field: 'ownerId',
            label: '负责人',
            props: { multiple: false },
          },
          {
            type: 'select',
            field: 'metricId',
            props: {
              optionSource: {
                type: 'QUERY_SOURCE',
                sourceType: 'BUSINESS_OBJECT',
                sourceKey: 'metric_detail',
                valueField: 'id',
                labelField: 'fieldInput',
              },
              fieldMappings: [{ sourceField: 'fieldInput', targetField: 'fieldInput3' }],
            },
          },
        ],
      },
      fields,
    })
    expect(result[0]).toMatchObject({
      field: 'ownerId',
      type: 'userSelect',
      props: { placeholder: '请选择', multiple: false },
    })
    expect(result[1]).toMatchObject({
      field: 'metricId',
      type: 'select',
      props: {
        optionSource: {
          sourceKey: 'metric_detail',
          labelField: 'fieldInput',
        },
        fieldMappings: [{ sourceField: 'fieldInput', targetField: 'fieldInput3' }],
      },
    })
  })

  it('设计器组件不在 fields 目录时仍按 uiDocument 渲染，不再静默丢字段', () => {
    const fields = [{ field: 'b' }, { field: 'a' }]
    const result = resolveAiFormSchemaFromUiDocument({
      protocolVersion: '1',
      uiDocument: {
        version: '1',
        sections: [{ fields: ['a', 'b'] }],
        components: [{ type: 'card', children: [{ type: 'switch', field: 'fieldSwitch', label: '开关', editable: true }] }],
      },
      fields,
    })
    expect(result[0].nodeType).toBe('card')
    expect(result[0].children[0]).toMatchObject({
      field: 'fieldSwitch',
      type: 'switch',
      label: '开关',
    })
    expect(result.map(f => f.field).filter(Boolean).concat(
      result.flatMap(f => (f.children || []).map(c => c.field)).filter(Boolean),
    )).toContain('fieldSwitch')
  })

  it('fields 为空时仍可用纯 uiDocument 组件树渲染', () => {
    const result = resolveAiFormSchemaFromUiDocument({
      protocolVersion: '1',
      uiDocument: {
        version: '1',
        components: [
          { type: 'input', field: 'newField', label: '新字段', editable: true },
        ],
      },
      fields: [],
    })
    expect(result).toMatchObject([{ field: 'newField', type: 'input', label: '新字段' }])
  })
})

describe('mapUiDocumentComponentsToAiFormSchema', () => {
  it('无命中字段且无组件字段时返回空数组', () => {
    expect(mapUiDocumentComponentsToAiFormSchema(
      [{ type: 'row', children: [] }],
      [{ field: 'a' }],
    )).toEqual([])
  })

  it('fields 缺失时从 uiDocument 节点合成字段定义', () => {
    const result = mapUiDocumentComponentsToAiFormSchema(
      [{ type: 'switch', field: 'fieldSwitch', label: '开关', editable: true }],
      [],
    )
    expect(result).toMatchObject([{ field: 'fieldSwitch', type: 'switch', label: '开关' }])
  })
})
