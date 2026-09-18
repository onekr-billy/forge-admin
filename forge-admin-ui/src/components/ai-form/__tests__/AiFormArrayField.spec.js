import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'
import AiFormArrayField from '../AiFormArrayField.vue'

const AiFormStub = defineComponent({
  name: 'AiForm',
  props: ['value', 'schema', 'fieldPermissions'],
  setup(props, { expose }) {
    expose({
      validate: vi.fn(() => props.value?.amount == null
        ? Promise.reject(new Error('金额必填'))
        : Promise.resolve(true)),
    })
    return {}
  },
  template: '<div class="nested-ai-form" />',
})

const NButtonStub = {
  name: 'NButton',
  props: ['disabled'],
  emits: ['click'],
  template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
}

function mountArray(options = {}) {
  return mount(AiFormArrayField, {
    props: {
      modelValue: options.value || [],
      field: {
        field: 'expenseItems',
        label: '费用明细',
        type: 'array',
        itemSchema: [
          { field: 'name', label: '名称', type: 'input', defaultValue: '' },
          { field: 'amount', label: '金额', type: 'number', required: true },
        ],
        arrayConfig: {
          allowCreate: true,
          allowUpdate: true,
          allowDelete: true,
          min: options.min || 0,
          max: 3,
        },
      },
      context: { aiFormComponent: AiFormStub, ...(options.context || {}) },
      disabled: options.disabled || false,
    },
    global: {
      stubs: {
        NButton: NButtonStub,
        NEmpty: true,
      },
    },
  })
}

describe('ai form array field', () => {
  it('adds and removes rows while preserving object-array values', async () => {
    const wrapper = mountArray({ value: [{ name: '差旅', amount: 100 }] })
    await flushPromises()

    const addButton = wrapper.findAll('button').find(button => button.text().includes('新增明细'))
    await addButton.trigger('click')
    expect(wrapper.emitted('update:modelValue').at(-1)[0]).toEqual([
      { name: '差旅', amount: 100 },
      { name: '', amount: null },
    ])

    const deleteButtons = wrapper.findAll('button').filter(button => button.text().includes('删除'))
    await deleteButtons[1].trigger('click')
    expect(wrapper.emitted('update:modelValue').at(-1)[0]).toEqual([{ name: '差旅', amount: 100 }])
  })

  it('hides row operations when an explicit node permission denies them', async () => {
    const wrapper = mountArray({
      value: [{ name: '差旅', amount: 100 }],
      context: {
        hasExplicitFieldPermissions: true,
        fieldPermissions: {
          version: 3,
          fields: [{ field: 'expenseItems', readable: true, writable: true }],
          arrays: [{ arrayKey: 'expenseItems', readable: true, allowCreate: false, allowUpdate: false, allowDelete: false }],
        },
      },
    })
    await flushPromises()

    expect(wrapper.text()).not.toContain('新增明细')
    expect(wrapper.text()).not.toContain('删除')
    expect(wrapper.findComponent({ name: 'AiForm' }).props('fieldPermissions')).toEqual([
      expect.objectContaining({ field: 'name', writable: false }),
      expect.objectContaining({ field: 'amount', writable: false }),
    ])
  })

  it('rejects validation when the minimum row count is not met', async () => {
    const wrapper = mountArray({ min: 1 })
    await expect(wrapper.vm.validate()).rejects.toThrow('至少需要 1 条明细')
  })

  it('applies item-level writable permissions independently', async () => {
    const wrapper = mountArray({
      value: [{ name: '差旅', amount: 100 }],
      context: {
        hasExplicitFieldPermissions: true,
        fieldPermissions: {
          version: 3,
          fields: [
            { scope: 'array', arrayKey: 'expenseItems', itemField: 'name', readable: true, writable: false },
            { scope: 'array', arrayKey: 'expenseItems', itemField: 'amount', readable: true, writable: true },
          ],
          arrays: [{ arrayKey: 'expenseItems', readable: true, allowUpdate: true }],
        },
      },
    })
    await flushPromises()

    expect(wrapper.findComponent({ name: 'AiForm' }).props('fieldPermissions')).toEqual([
      expect.objectContaining({ field: 'name', writable: false }),
      expect.objectContaining({ field: 'amount', writable: true }),
    ])
  })

  it('waits for nested row validation', async () => {
    const wrapper = mountArray({ value: [{ name: '差旅', amount: null }] })
    await flushPromises()

    await expect(wrapper.vm.validate()).rejects.toThrow('金额必填')
  })
})
