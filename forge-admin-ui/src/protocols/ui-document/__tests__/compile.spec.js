import { describe, expect, it } from 'vitest'
import {
  compileUiDocument,
  compileUiDocumentFromDesigner,
  UI_DOCUMENT_PROTOCOL_VERSION,
  UI_DOCUMENT_UI_TYPES,
} from '../index'

describe('compileUiDocument', () => {
  it('编译 pageSections + components，并叠权限 editable', () => {
    const doc = compileUiDocument(
      {
        formKey: 'safety_form',
        pageSections: [
          {
            sectionId: 'base',
            sectionType: 'card',
            title: '基本信息',
            fields: ['status', 'wtno'],
          },
        ],
        components: [
          {
            id: 'c1',
            componentKey: 'input',
            label: '状态',
            fieldBinding: { fieldCode: 'status' },
            validation: { required: true },
            layout: { span: 12 },
          },
          {
            id: 'c2',
            componentKey: 'input',
            label: '流水号',
            fieldBinding: { fieldCode: 'wtno' },
          },
        ],
      },
      'safety_form',
      [
        { field: 'status', label: '状态' },
        { field: 'wtno', label: '流水号' },
      ],
      [
        { field: 'status', readable: true, writable: false },
        { field: 'wtno', readable: true, writable: true },
      ],
    )

    expect(doc.version).toBe(UI_DOCUMENT_PROTOCOL_VERSION)
    expect(doc.uiType).toBe(UI_DOCUMENT_UI_TYPES.BUSINESS_OBJECT)
    expect(doc.formKey).toBe('safety_form')
    expect(doc.sections[0]).toMatchObject({
      sectionId: 'base',
      fields: ['status', 'wtno'],
    })
    expect(doc.components[0]).toMatchObject({
      field: 'status',
      required: true,
      editable: false,
      span: 12,
    })
    expect(doc.components[1]).toMatchObject({
      field: 'wtno',
      editable: true,
    })
  })

  it('无 pageSections 时从 fields 生成默认分区', () => {
    const doc = compileUiDocument(
      {
        components: [
          { componentKey: 'input', fieldBinding: { fieldCode: 'title' }, label: '标题' },
        ],
      },
      'demo',
      [{ field: 'title', label: '标题' }],
      [],
    )
    expect(doc.sections[0].sectionId).toBe('main')
    expect(doc.sections[0].fields).toEqual(['title'])
    // 无权限 overlay 时低代码默认可写
    expect(doc.components[0].editable).toBe(true)
  })

  it('设计器 visibility.readonly 编译为 editable=false，且默认可写', () => {
    const doc = compileUiDocument(
      {
        components: [
          {
            componentKey: 'input',
            fieldBinding: { fieldCode: 'name' },
            visibility: { readonly: true },
          },
          {
            componentKey: 'input',
            fieldBinding: { fieldCode: 'title' },
          },
        ],
      },
      'demo',
      [{ field: 'name' }, { field: 'title' }],
      [],
    )
    expect(doc.components[0].editable).toBe(false)
    expect(doc.components[1].editable).toBe(true)
  })

  it('保留布局树 children', () => {
    const doc = compileUiDocument(
      {
        components: [
          {
            componentKey: 'card',
            label: '卡片',
            children: [
              { componentKey: 'input', fieldBinding: { fieldCode: 'a' } },
            ],
          },
        ],
      },
      'layout',
      [{ field: 'a' }],
    )
    expect(doc.components[0].type).toBe('card')
    expect(doc.components[0].children[0].field).toBe('a')
  })
})

describe('compileUiDocumentFromDesigner', () => {
  it('从多表单设计器选中默认表单编译', () => {
    const doc = compileUiDocumentFromDesigner({
      defaultFormKey: 'main',
      forms: [
        {
          formKey: 'main',
          schema: {
            components: [
              { componentKey: 'input', fieldBinding: { fieldCode: 'name' }, label: '名称' },
            ],
          },
        },
      ],
    }, {
      resolvedFields: [{ field: 'name', label: '名称' }],
    })
    expect(doc.uiType).toBe(UI_DOCUMENT_UI_TYPES.LOWCODE_FORM)
    expect(doc.formKey).toBe('main')
    expect(doc.components[0].field).toBe('name')
  })
})
