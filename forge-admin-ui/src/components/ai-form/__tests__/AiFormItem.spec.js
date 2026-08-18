import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import AiFormItem from '../AiFormItem.vue'

vi.mock('vue-router', () => ({
  useRoute: () => ({
    query: {},
    params: {},
    path: '/',
    fullPath: '/',
    name: 'ai-form-item-test',
  }),
}))

const NFormItemStub = {
  name: 'NFormItem',
  template: '<div><slot name="label" /><slot /></div>',
}

const NInputNumberStub = {
  name: 'NInputNumber',
  props: ['value', 'min', 'max', 'step'],
  template: '<input class="n-input-number-stub">',
}

const naiveStubs = Object.fromEntries([
  'NInput',
  'NInputNumber',
  'NSelect',
  'NRadio',
  'NSpace',
  'NRadioGroup',
  'NRadioButton',
  'NCheckbox',
  'NCheckboxGroup',
  'NSwitch',
  'NDatePicker',
  'NTimePicker',
  'NButton',
  'NUpload',
  'NSlider',
  'NRate',
  'NColorPicker',
  'NCascader',
  'NTreeSelect',
  'NTransfer',
  'NInputGroup',
  'NIcon',
  'NTooltip',
].map(name => [name, true]))

describe('aiFormItem number field compatibility', () => {
  it('renders input-number as NInputNumber and forwards numeric constraints', () => {
    const wrapper = mount(AiFormItem, {
      props: {
        field: {
          field: 'sort',
          label: '排序',
          type: 'input-number',
          props: {
            min: 0,
            max: 100,
            step: 5,
          },
        },
        value: 10,
      },
      global: {
        stubs: {
          ...naiveStubs,
          NFormItem: NFormItemStub,
          NInputNumber: NInputNumberStub,
          AiRecordSelectorModal: true,
        },
      },
    })

    const numberInput = wrapper.findComponent(NInputNumberStub)
    expect(numberInput.exists()).toBe(true)
    expect(numberInput.props()).toMatchObject({
      value: 10,
      min: 0,
      max: 100,
      step: 5,
    })
    expect(wrapper.find('.n-input-number-stub').exists()).toBe(true)
  })
})

describe('aiFormItem managed field events', () => {
  function mountEventField(context) {
    return mount(AiFormItem, {
      props: {
        field: { field: 'mobile', label: '手机号', type: 'input' },
        value: '13800000000',
        formData: { mobile: '13800000000' },
        context,
      },
      global: {
        stubs: {
          ...naiveStubs,
          NFormItem: NFormItemStub,
          NButton: {
            template: '<button><slot /></button>',
          },
          AiRecordSelectorModal: true,
        },
      },
    })
  }

  it('dispatches BLUR and SCAN_COMPLETE through the shared field event entry', async () => {
    const dispatchFieldEvent = vi.fn()
    const wrapper = mountEventField({
      hasFieldEvent: trigger => ['BLUR', 'SCAN_COMPLETE'].includes(trigger),
      dispatchFieldEvent,
      getFieldEventState: () => ({ status: 'idle', loading: false, message: '' }),
    })

    await wrapper.find('.ai-form-control').trigger('focusout')
    await wrapper.find('.ai-form-control').trigger('keyup', { key: 'Enter' })

    expect(dispatchFieldEvent).toHaveBeenNthCalledWith(1, 'BLUR', 'mobile')
    expect(dispatchFieldEvent).toHaveBeenNthCalledWith(2, 'SCAN_COMPLETE', 'mobile')
  })

  it('renders a compact MANUAL action and controlled field feedback', async () => {
    const dispatchFieldEvent = vi.fn()
    const wrapper = mountEventField({
      hasFieldEvent: trigger => trigger === 'MANUAL',
      dispatchFieldEvent,
      getFieldEventState: () => ({ status: 'not_found', loading: false, message: '未匹配到数据' }),
    })

    expect(wrapper.text()).toContain('查询')
    expect(wrapper.text()).toContain('未匹配到数据')
    await wrapper.find('.ai-form-field-event__action').trigger('click')
    expect(dispatchFieldEvent).toHaveBeenCalledWith('MANUAL', 'mobile')
  })

  it('writes an injected scan result and dispatches SCAN_COMPLETE context', async () => {
    const dispatchFieldEvent = vi.fn()
    const scanField = vi.fn(async () => ({ value: 'SKU-001', type: 'barCode', platform: 'H5' }))
    const wrapper = mountEventField({
      hasFieldEvent: trigger => trigger === 'SCAN_COMPLETE',
      scanField,
      dispatchFieldEvent,
      getFieldEventState: () => ({ status: 'idle', loading: false, message: '' }),
    })

    const button = wrapper.find('.ai-form-field-event__action')
    expect(button.text()).toContain('扫码')
    await button.trigger('click')

    expect(scanField).toHaveBeenCalledWith(expect.objectContaining({ field: 'mobile' }))
    expect(wrapper.emitted('update:value')).toEqual([['SKU-001']])
    expect(dispatchFieldEvent).toHaveBeenCalledWith('SCAN_COMPLETE', 'mobile', {
      scan: { value: 'SKU-001', type: 'barCode', platform: 'H5' },
    })
  })

  it('renders the barcode scanner field and writes the scanned value', async () => {
    const scanField = vi.fn(async field => ({
      value: '6901234567890',
      type: 'EAN_13',
      platform: 'H5',
      field: field.field,
    }))
    const dispatchFieldEvent = vi.fn()
    const wrapper = mount(AiFormItem, {
      props: {
        field: {
          field: 'barcode',
          label: '商品条码',
          type: 'barcodeScanner',
          props: { allowManualInput: true, timeoutMs: 5000 },
        },
        value: '',
        formData: { barcode: '' },
        context: {
          scanField,
          dispatchFieldEvent,
          hasFieldEvent: () => false,
          getFieldEventState: () => ({ status: 'idle', loading: false, message: '' }),
        },
      },
      global: {
        stubs: {
          ...naiveStubs,
          NFormItem: NFormItemStub,
          NButton: {
            template: '<button class="barcode-scan-button"><slot /></button>',
          },
          AiRecordSelectorModal: true,
        },
      },
    })

    expect(wrapper.find('.ai-form-barcode-scanner-field').exists()).toBe(true)
    await wrapper.find('.barcode-scan-button').trigger('click')

    expect(scanField).toHaveBeenCalledWith(expect.objectContaining({
      field: 'barcode',
      type: 'barcodeScanner',
    }))
    expect(wrapper.emitted('update:value')).toEqual([['6901234567890']])
    expect(dispatchFieldEvent).toHaveBeenCalledWith('SCAN_COMPLETE', 'barcode', {
      scan: expect.objectContaining({ value: '6901234567890', platform: 'H5' }),
    })
  })
})
