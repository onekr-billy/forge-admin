import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { defineComponent, ref } from 'vue'
import FormPermissionConfig from '../FormPermissionConfig.vue'

const STUBS = {
  'n-tag': { template: '<span class="n-tag"><slot /></span>' },
  'n-checkbox': {
    props: ['checked', 'disabled'],
    emits: ['update:checked'],
    template: '<input type="checkbox" :checked="checked" :disabled="disabled" v-bind="$attrs" @change="$emit(\'update:checked\', $event.target.checked)" />',
  },
}

const fields = [
  { field: 'amount', label: '金额', required: true, dataType: 'number' },
  { field: 'reason', label: '申请原因', dataType: 'string' },
]

function mountConfig(options = {}) {
  const Parent = defineComponent({
    components: { FormPermissionConfig },
    setup() {
      const config = ref({
        formFieldPermissions: options.permissions || [],
      })
      function updateConfig(patch) {
        config.value = { ...config.value, ...patch }
      }
      return {
        config,
        formFieldCatalog: options.formFieldCatalog ?? fields,
        readonly: options.readonly || false,
        updateConfig,
      }
    },
    template: `
      <FormPermissionConfig
        :config="config"
        :form-field-catalog="formFieldCatalog"
        :readonly="readonly"
        @update:config="updateConfig"
      />
    `,
  })

  return mount(Parent, {
    global: { stubs: STUBS },
  })
}

describe('formPermissionConfig', () => {
  it('按动态表单字段目录展示权限行', () => {
    const wrapper = mountConfig()

    expect(wrapper.text()).toContain('金额')
    expect(wrapper.text()).toContain('amount')
    expect(wrapper.text()).toContain('申请原因')
    expect(wrapper.text()).toContain('默认全量可写')

    wrapper.unmount()
  })

  it('切换可编辑后输出完整字段权限配置', async () => {
    const wrapper = mountConfig()
    const writableInputs = wrapper.findAll('[data-test="permission-writable"]')

    await writableInputs[0].setValue(false)

    expect(wrapper.vm.config.formFieldPermissions).toEqual([
      {
        field: 'amount',
        fieldCode: 'amount',
        label: '金额',
        visible: true,
        editable: false,
        readable: true,
        writable: false,
        required: false,
      },
      {
        field: 'reason',
        fieldCode: 'reason',
        label: '申请原因',
        visible: true,
        editable: true,
        readable: true,
        writable: true,
        required: false,
      },
    ])

    wrapper.unmount()
  })

  it('关闭可见时同步关闭可编辑和必填', async () => {
    const wrapper = mountConfig({
      permissions: [
        { field: 'amount', label: '金额', readable: true, writable: true, required: true },
      ],
    })
    const readableInputs = wrapper.findAll('[data-test="permission-readable"]')

    await readableInputs[0].setValue(false)

    expect(wrapper.vm.config.formFieldPermissions[0]).toEqual({
      field: 'amount',
      fieldCode: 'amount',
      label: '金额',
      visible: false,
      editable: false,
      readable: false,
      writable: false,
      required: false,
    })

    wrapper.unmount()
  })

  it('存在 visible/editable 旧字段时以 readable/writable 为准', async () => {
    const wrapper = mountConfig({
      permissions: [
        {
          field: 'amount',
          label: '金额',
          visible: true,
          editable: true,
          readable: false,
          writable: false,
          required: false,
        },
      ],
    })

    const readableInputs = wrapper.findAll('[data-test="permission-readable"]')
    expect(readableInputs[0].element.checked).toBe(false)

    await readableInputs[0].setValue(true)

    expect(wrapper.vm.config.formFieldPermissions[0]).toMatchObject({
      field: 'amount',
      visible: true,
      editable: false,
      readable: true,
      writable: false,
    })

    wrapper.unmount()
  })

  it('子表字段和行级新增/修改/删除权限输出 v2 配置', async () => {
    const wrapper = mountConfig({
      formFieldCatalog: [
        ...fields,
        { scope: 'child', childKey: 'items', childField: 'quantity', field: 'quantity', label: '数量' },
      ],
    })

    expect(wrapper.text()).toContain('items.quantity')
    await wrapper.find('[data-test="child-allow-create"]').setValue(true)
    await wrapper.find('[data-test="child-allow-update"]').setValue(true)
    await wrapper.find('[data-test="child-allow-delete"]').setValue(true)

    expect(wrapper.vm.config.formFieldPermissions).toMatchObject({
      version: 2,
      children: [{ childKey: 'items', allowCreate: true, allowUpdate: true, allowDelete: true }],
    })
    expect(wrapper.vm.config.formFieldPermissions.fields).toEqual(expect.arrayContaining([
      expect.objectContaining({ scope: 'child', childKey: 'items', childField: 'quantity' }),
    ]))

    wrapper.unmount()
  })
})
