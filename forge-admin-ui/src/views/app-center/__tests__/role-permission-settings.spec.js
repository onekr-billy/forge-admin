import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import RolePermissionSettings from '@/views/system/components/RolePermissionSettings.vue'

const CheckboxStub = {
  name: 'NCheckbox',
  props: {
    checked: Boolean,
    disabled: Boolean,
  },
  emits: ['update:checked'],
  template: `
    <button
      type="button"
      class="checkbox-stub"
      :disabled="disabled"
      @click="$emit('update:checked', !checked)"
    >
      <slot />
    </button>
  `,
}

function mountSettings(overrides = {}) {
  return mount(RolePermissionSettings, {
    props: {
      checkedKeys: [],
      permissionModules: [{
        key: 'module:order',
        name: '订单',
        pages: [{
          key: 'page:order',
          name: '订单权限',
          accessItem: {
            key: 'access:11',
            resourceIds: ['11'],
          },
          actionItems: [{
            key: 'button:edit',
            label: '编辑',
            sectionKey: 'button',
            resourceIds: ['21'],
            sources: [{ type: 3, kind: 'button', label: '按钮' }],
          }],
          showDataScopePanel: false,
          showFunctionPanel: true,
        }],
      }],
      dataScopeSettings: { defaultDataScope: 5, modules: [] },
      dataScopeOptions: [{ label: '仅本人数据', value: 5 }],
      defaultScopeEditable: false,
      linkPageAndActions: false,
      ...overrides,
    },
    global: {
      stubs: {
        NCheckbox: CheckboxStub,
        NButton: { template: '<button type="button"><slot name="icon" /><slot /></button>' },
        NDropdown: { template: '<div><slot /></div>' },
        NEmpty: { template: '<div class="empty-stub" />' },
        NInput: {
          inheritAttrs: false,
          template: '<input />',
        },
        NSkeleton: { template: '<span />' },
        NSwitch: { template: '<button type="button" />' },
      },
    },
  })
}

describe('role permission settings application mode', () => {
  it('keeps actions unchanged when page entry is selected', async () => {
    const wrapper = mountSettings()
    await flushPromises()

    await wrapper.find('.page-card-title .checkbox-stub').trigger('click')

    const updates = wrapper.emitted('update:checkedKeys') || []
    expect(updates.at(-1)?.[0]).toEqual(['11'])
    expect(updates.at(-1)?.[0]).not.toContain('21')
  })

  it('keeps page entry independent when an action is selected', async () => {
    const wrapper = mountSettings()
    await flushPromises()

    await wrapper.find('.function-grid .checkbox-stub').trigger('click')

    const updates = wrapper.emitted('update:checkedKeys') || []
    expect(updates.at(-1)?.[0]).toEqual(['21'])
    expect(updates.at(-1)?.[0]).not.toContain('11')
  })

  it('renders the role default data scope as read-only', async () => {
    const wrapper = mountSettings()
    await flushPromises()

    expect(wrapper.find('.default-scope-button.is-readonly').exists()).toBe(true)
    expect(wrapper.text()).toContain('角色默认：')
    expect(wrapper.text()).toContain('仅本人数据')
  })
})
