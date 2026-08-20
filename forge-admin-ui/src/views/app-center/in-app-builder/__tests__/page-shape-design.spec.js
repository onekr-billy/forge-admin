import { describe, expect, it } from 'vitest'
import { FIELD_COMPONENT_PALETTE_GROUPS } from '../../components/designer/form-first/fieldComponentCatalog'
import { buildBusinessObjectDesignerPayloadFromFormAsset } from '../page-form-object-promotion'
import {
  createPageShapeBuilder,
  normalizePageShapeSelection,
  PAGE_SHAPE_TYPES,
} from '../page-shape-design'

function emptyBuilder() {
  return {
    schemaVersion: 2,
    homePageId: null,
    nodes: [],
    pages: {},
    formAssets: [],
    flowInteraction: {},
  }
}

describe('page shape design draft', () => {
  it('exposes the four documented page shapes and the five minimum field components', () => {
    expect(PAGE_SHAPE_TYPES.map(item => item.value)).toEqual(['form', 'list', 'list-form', 'custom'])

    const componentKeys = FIELD_COMPONENT_PALETTE_GROUPS.flatMap(group => group.items.map(item => item.componentKey))
    expect(componentKeys).toEqual(expect.arrayContaining(['input', 'number', 'date', 'select', 'switch']))
  })

  it('normalizes an editable object code from the page name', () => {
    expect(normalizePageShapeSelection({ pageName: '客户管理', pageType: 'form' })).toMatchObject({
      pageName: '客户管理',
      objectName: '客户管理',
      objectCode: 'customer_management',
      pageType: 'form',
    })
    expect(normalizePageShapeSelection({
      pageName: '客户管理',
      objectCode: 'crm_customer',
      pageType: 'form',
    }).objectCode).toBe('crm_customer')
  })

  it('creates a form page, visible object reference, form asset and form-only CRUD block', () => {
    const result = createPageShapeBuilder(emptyBuilder(), {
      pageName: '客户登记',
      objectName: '客户登记',
      objectCode: 'customer_register',
      pageType: 'form',
    })

    expect(result.pageId).toBeTruthy()
    expect(result.formAssetId).toBeTruthy()
    expect(result.schema.nodes).toEqual([
      expect.objectContaining({
        id: result.pageId,
        title: '客户登记',
        pageType: 'object',
        objectRef: expect.objectContaining({
          objectCode: 'customer_register',
          objectName: '客户登记',
          pageMode: 'form',
        }),
      }),
    ])
    expect(result.schema.formAssets[0]).toMatchObject({
      id: result.formAssetId,
      name: '客户登记',
      formDesignerSchema: expect.objectContaining({ formName: '客户登记' }),
    })
    expect(result.schema.pages[result.pageId].layout.gridLayout.items[0]).toMatchObject({
      blockType: 'AiCrudPage',
      props: expect.objectContaining({
        formOnly: true,
        formAssetId: result.formAssetId,
        objectRef: expect.objectContaining({ objectCode: 'customer_register' }),
      }),
    })
  })

  it('uses an inline form workspace for list-form pages and no object for custom pages', () => {
    const listForm = createPageShapeBuilder(emptyBuilder(), {
      pageName: '客户管理',
      objectCode: 'customer',
      pageType: 'list-form',
    })
    expect(listForm.schema.pages[listForm.pageId].layout.gridLayout.items[0].props.formOpenMode).toBe('flat')

    const custom = createPageShapeBuilder(emptyBuilder(), {
      pageName: '数据看板',
      pageType: 'custom',
    })
    expect(custom.formAssetId).toBe('')
    expect(custom.schema.formAssets).toEqual([])
    expect(custom.schema.nodes[0]).toMatchObject({ pageType: 'content', objectRef: null })
  })

  it('keeps an existing object field when the form reuses it instead of creating it', () => {
    const result = buildBusinessObjectDesignerPayloadFromFormAsset({
      formDesignerSchema: {
        components: [{
          componentKey: 'input',
          label: '客户名称',
          fieldBinding: {
            mode: 'field',
            fieldCode: 'customerName',
            createIfMissing: false,
          },
        }],
      },
    }, [{
      fieldCode: 'customerName',
      fieldName: '客户名称',
      fieldType: 'TEXT',
      dataType: 'varchar',
      length: 128,
      precision: 0,
      systemField: false,
    }])

    expect(result.fields).toEqual(expect.arrayContaining([
      expect.objectContaining({ fieldCode: 'customerName', fieldType: 'TEXT' }),
    ]))
  })
})
