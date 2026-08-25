import { describe, expect, it } from 'vitest'
import {
  buildBusinessObjectDesignerPayloadFromFormAsset,
  normalizeObjectDesignerFieldCatalog,
  syncFormBoundFieldRefs,
} from '../page-form-object-promotion'

describe('page form object promotion', () => {
  it('converts bound page form fields into a business object designer payload', () => {
    const payload = buildBusinessObjectDesignerPayloadFromFormAsset({
      id: 'form_customer',
      name: '客户登记表',
      formDesignerSchema: {
        formKey: 'customer_form',
        formName: '客户登记表',
        components: [{
          id: 'field_customer_name',
          componentKey: 'input',
          label: '客户名称',
          fieldBinding: {
            mode: 'field',
            fieldCode: 'customerName',
            columnName: 'customer_name',
            createIfMissing: true,
          },
          validation: { required: true },
          visibility: { hidden: false, readonly: false },
          props: { placeholder: '请输入客户名称' },
        }],
      },
    })

    expect(payload.formDesignerSchema).toEqual(expect.objectContaining({
      formKey: 'customer_form',
      formName: '客户登记表',
    }))
    expect(payload.fields).toEqual([
      expect.objectContaining({
        fieldName: '客户名称',
        fieldCode: 'customerName',
        columnName: 'customer_name',
        required: true,
        componentType: 'input',
        listVisible: true,
        formVisible: true,
      }),
    ])
  })

  it('ignores virtual components because they have no database field', () => {
    const payload = buildBusinessObjectDesignerPayloadFromFormAsset({
      id: 'form_intro',
      name: '说明表单',
      formDesignerSchema: {
        formKey: 'intro_form',
        components: [{
          id: 'intro_text',
          componentKey: 'text',
          label: '填写说明',
          fieldBinding: { mode: 'virtual', fieldCode: '' },
        }],
      },
    })

    expect(payload.fields).toEqual([])
  })

  it('normalizes unpublished object designer fields for the page field drawer', () => {
    expect(normalizeObjectDesignerFieldCatalog([{
      fieldCode: 'customerName',
      fieldName: '客户名称',
      listVisible: true,
      formVisible: true,
    }])).toEqual([
      expect.objectContaining({
        field: 'customerName',
        fieldCode: 'customerName',
        sourceField: 'customerName',
        label: '客户名称',
        fieldStatus: 'ENABLED',
      }),
    ])
  })

  it('drops deleted form fields from list and search refs instead of merging them', () => {
    expect(syncFormBoundFieldRefs({
      formFieldCodes: ['fieldSlider', 'fieldRate'],
      searchFieldRefs: ['fieldSlider', 'fieldNumber', 'fieldRate'],
    })).toEqual({
      fieldRefs: ['fieldSlider', 'fieldRate'],
      searchFieldRefs: ['fieldSlider', 'fieldRate'],
    })
  })
})
