import { describe, expect, it } from 'vitest'
import { extractForgeSchemaFieldRefs } from '@/views/app-center/components/designer/form-first/forgeToFormCreate'
import {
  bindProvisionedFormData,
  collectFormDataProvisionTargets,
  mergePageFieldCatalogs,
} from '../page-form-data-provisioning'
import { normalizeObjectDesignerFieldCatalog } from '../page-form-object-promotion'

function formAsset(components = [persistentField()]) {
  return {
    id: 'form_customer',
    name: '客户登记表',
    formDesignerSchema: {
      formKey: 'customer_form',
      formName: '客户登记表',
      components,
    },
  }
}

function persistentField() {
  return {
    id: 'field_customer_name',
    componentKey: 'input',
    label: '客户名称',
    fieldBinding: {
      mode: 'field',
      fieldCode: 'customerName',
      columnName: 'customer_name',
      createIfMissing: true,
    },
  }
}

function builderWithBlocks(blocks, asset = formAsset()) {
  return {
    formAssets: [asset],
    pages: {
      page_1: {
        layout: {
          gridLayout: {
            items: blocks,
          },
        },
      },
    },
  }
}

describe('page form data provisioning', () => {
  it('creates one provisioning target when one form is used by multiple unbound CRUD blocks', () => {
    const builder = builderWithBlocks([
      { id: 'crud_1', blockType: 'AiCrudPage', props: { formAssetId: 'form_customer' } },
      { id: 'crud_2', blockType: 'AiCrudPage', props: { formAssetId: 'form_customer' } },
    ])

    const targets = collectFormDataProvisionTargets(builder, [])

    expect(targets).toHaveLength(1)
    expect(targets[0]).toEqual(expect.objectContaining({
      formAssetId: 'form_customer',
      formName: '客户登记表',
      unboundCrudCount: 2,
    }))
    expect(targets[0].request.fields).toEqual([
      expect.objectContaining({ fieldCode: 'customerName', columnName: 'customer_name' }),
    ])
  })

  it('does not prepare storage for forms without persistent fields', () => {
    const builder = builderWithBlocks([
      { id: 'crud_1', blockType: 'AiCrudPage', props: { formAssetId: 'form_customer' } },
    ], formAsset([{
      id: 'intro',
      componentKey: 'text',
      label: '说明',
      fieldBinding: { mode: 'virtual', fieldCode: '' },
    }]))

    expect(collectFormDataProvisionTargets(builder, [])).toEqual([])
  })

  it('keeps synchronizing an automatically managed form but ignores manually bound objects', () => {
    const builder = builderWithBlocks([
      {
        id: 'crud_1',
        blockType: 'AiCrudPage',
        props: {
          formAssetId: 'form_customer',
          objectRef: { objectId: '900000000000000001', objectCode: 'customer' },
        },
      },
    ])

    expect(collectFormDataProvisionTargets(builder, [])).toEqual([])
    expect(collectFormDataProvisionTargets(builder, [{
      objectId: '900000000000000001',
      options: JSON.stringify({ managedBy: 'PAGE_FORM', sourceFormAssetId: 'form_customer' }),
    }])).toHaveLength(1)
  })

  it('binds every matching root and nested CRUD block while preserving 19 digit ids as strings', () => {
    const builder = builderWithBlocks([
      { id: 'crud_root', blockType: 'AiCrudPage', props: { formAssetId: 'form_customer' } },
      {
        id: 'tabs',
        blockType: 'tabs',
        props: {
          tabs: [{
            key: 'tab_1',
            children: [{ id: 'crud_nested', blockType: 'AiCrudPage', props: { formAssetId: 'form_customer' } }],
          }],
        },
      },
    ])

    const result = bindProvisionedFormData(builder, 'form_customer', {
      objectId: '900000000000000001',
      objectCode: 'crm_customer_form',
      objectName: '客户登记表',
      configKey: 'crm_customer_form',
    })
    const root = result.schema.pages.page_1.layout.gridLayout.items[0]
    const nested = result.schema.pages.page_1.layout.gridLayout.items[1].props.tabs[0].children[0]

    expect(result.changed).toBe(true)
    expect(root.props.objectRef.objectId).toBe('900000000000000001')
    expect(nested.props.objectRef.objectId).toBe('900000000000000001')
    expect(root.props.listApi).toBe('get@/ai/crud/crm_customer_form/page')
    expect(root.props.previewLiveData).toBe(true)
    expect(root.props.previewMode).toBe('realList')
    expect(root.props.managedPreviewInitialized).toBe(true)
  })

  it('upgrades an existing managed binding once but preserves a later manual preview choice', () => {
    const provisioned = {
      objectId: '900000000000000001',
      objectCode: 'crm_customer_form',
      objectName: '客户登记表',
      configKey: 'crm_customer_form',
    }
    const legacy = builderWithBlocks([{
      id: 'crud_1',
      blockType: 'AiCrudPage',
      props: {
        formAssetId: 'form_customer',
        objectRef: { objectId: '900000000000000001', objectCode: 'crm_customer_form' },
        previewLiveData: false,
        previewMode: 'mock',
      },
    }])

    const upgraded = bindProvisionedFormData(legacy, 'form_customer', provisioned)
    const upgradedBlock = upgraded.schema.pages.page_1.layout.gridLayout.items[0]
    expect(upgraded.changed).toBe(true)
    expect(upgradedBlock.props.previewLiveData).toBe(true)
    expect(upgradedBlock.props.managedPreviewInitialized).toBe(true)

    upgradedBlock.props.previewLiveData = false
    upgradedBlock.props.previewMode = 'mock'
    const preserved = bindProvisionedFormData(upgraded.schema, 'form_customer', provisioned)
    expect(preserved.schema.pages.page_1.layout.gridLayout.items[0].props.previewLiveData).toBe(false)
    expect(preserved.schema.pages.page_1.layout.gridLayout.items[0].props.previewMode).toBe('mock')
  })

  it('repairs a stale preview initialization marker when the managed object binding is missing', () => {
    const builder = builderWithBlocks([{
      id: 'crud_1',
      blockType: 'AiCrudPage',
      props: {
        formAssetId: 'form_customer',
        managedPreviewInitialized: true,
        previewLiveData: false,
        previewMode: 'mock',
      },
    }])

    const result = bindProvisionedFormData(builder, 'form_customer', {
      objectId: '900000000000000001',
      objectCode: 'crm_customer_form',
      objectName: '客户登记表',
      configKey: 'crm_customer_form',
    })
    const block = result.schema.pages.page_1.layout.gridLayout.items[0]

    expect(result.changed).toBe(true)
    expect(block.props.objectRef.objectId).toBe('900000000000000001')
    expect(block.props.previewLiveData).toBe(true)
    expect(block.props.previewMode).toBe('realList')
  })

  it('keeps form fields when the asynchronous runtime catalog temporarily only contains id', () => {
    expect(mergePageFieldCatalogs(
      [
        { field: 'customerName', label: '客户名称', componentType: 'input' },
        { field: 'orderNo', label: '订单号', componentType: 'input' },
      ],
      [{ field: 'id', label: 'ID', dataType: 'bigint', systemField: true }],
    ).map(field => field.field)).toEqual(['customerName', 'orderNo', 'id'])
  })

  it('cannot populate unused field assets when the shelf is derived only from the current form canvas', () => {
    const canvas = {
      components: [{
        componentKey: 'input',
        fieldBinding: { mode: 'field', fieldCode: 'customerName' },
      }],
    }
    const formOnlyFields = normalizeObjectDesignerFieldCatalog([
      { fieldCode: 'customerName', fieldName: '客户名称' },
    ])
    const used = new Set(extractForgeSchemaFieldRefs(canvas))
    expect(formOnlyFields.filter(field => !used.has(field.field)).map(field => field.field)).toEqual([])
  })

  it('keeps object field assets unused after they leave the form canvas', () => {
    const objectFields = normalizeObjectDesignerFieldCatalog([
      { fieldCode: 'customerName', fieldName: '客户名称' },
      { fieldCode: 'phone', fieldName: '电话' },
    ])
    const canvasWithName = {
      components: [{
        componentKey: 'input',
        fieldBinding: { mode: 'field', fieldCode: 'customerName' },
      }],
    }
    const formFieldsOnCanvas = objectFields.filter(field => field.field === 'customerName')
    const shelf = mergePageFieldCatalogs(formFieldsOnCanvas, objectFields)
    const used = new Set(extractForgeSchemaFieldRefs(canvasWithName))
    expect(shelf.filter(field => !used.has(field.field)).map(field => field.field)).toEqual(['phone'])

    const shelfAfterDelete = mergePageFieldCatalogs([], objectFields)
    const usedAfterDelete = new Set(extractForgeSchemaFieldRefs({ components: [] }))
    expect(shelfAfterDelete.filter(field => !usedAfterDelete.has(field.field)).map(field => field.field)).toEqual(['customerName', 'phone'])
  })

  it('does not overwrite a CRUD block whose page is explicitly bound to an existing object', () => {
    const builder = {
      formAssets: [formAsset()],
      nodes: [
        {
          id: 'page_manual',
          type: 'page',
          objectRef: { objectId: '800000000000000001', objectCode: 'existing_customer' },
        },
        { id: 'page_auto', type: 'page' },
      ],
      pages: {
        page_manual: {
          layout: {
            gridLayout: {
              items: [{ id: 'crud_manual', blockType: 'AiCrudPage', props: { formAssetId: 'form_customer' } }],
            },
          },
        },
        page_auto: {
          layout: {
            gridLayout: {
              items: [{ id: 'crud_auto', blockType: 'AiCrudPage', props: { formAssetId: 'form_customer' } }],
            },
          },
        },
      },
    }

    const targets = collectFormDataProvisionTargets(builder, [])
    const result = bindProvisionedFormData(builder, 'form_customer', {
      objectId: '900000000000000001',
      objectCode: 'crm_customer_form',
      objectName: '客户登记表',
      configKey: 'crm_customer_form',
    })

    expect(targets).toHaveLength(1)
    expect(targets[0].unboundCrudCount).toBe(1)
    expect(result.schema.pages.page_manual.layout.gridLayout.items[0].props.objectRef).toBeUndefined()
    expect(result.schema.nodes[0].objectRef.objectId).toBe('800000000000000001')
    expect(result.schema.pages.page_auto.layout.gridLayout.items[0].props.objectRef.objectId)
      .toBe('900000000000000001')
  })
})
