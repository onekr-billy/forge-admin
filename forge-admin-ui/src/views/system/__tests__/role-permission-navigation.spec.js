import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { h } from 'vue'
import {
  buildPermissionNavigationTree,
  buildWorkspaceModules,
  filterPermissionNavigationTree,
  findPermissionNavigationMatch,
  findPermissionNavigationNode,
} from '@/views/system/components/role-permission-model'
import RolePermissionNavigation from '@/views/system/components/RolePermissionNavigation.vue'

const CheckboxStub = {
  name: 'NCheckbox',
  props: {
    checked: Boolean,
    indeterminate: Boolean,
    ariaLabel: String,
  },
  emits: ['update:checked'],
  template: `
    <button
      type="button"
      class="checkbox-stub"
      :data-checked="checked"
      :data-indeterminate="indeterminate"
      :aria-label="ariaLabel"
      @click="$emit('update:checked', !checked)"
    />
  `,
}

const TreeStub = {
  name: 'NTree',
  props: {
    data: { type: Array, default: () => [] },
    expandedKeys: { type: Array, default: () => [] },
    renderLabel: { type: Function, required: true },
  },
  emits: ['update:selected-keys', 'update:expanded-keys'],
  setup(props) {
    return () => h('div', {
      'class': 'tree-stub',
      'data-expanded-keys': JSON.stringify(props.expandedKeys),
    }, props.data.map(option => h('div', { class: 'tree-node-stub' }, [
      props.renderLabel({ option }),
    ])))
  },
}

function mountNavigation(overrides = {}) {
  return mount(RolePermissionNavigation, {
    props: {
      navigationTree: [{
        key: 'directory:system',
        label: '系统管理',
        nodeType: 'directory',
        resourceIds: ['1', '2', '3'],
        children: [{
          key: 'directory:user',
          label: '用户目录',
          nodeType: 'directory',
          resourceIds: ['1', '2'],
          children: [{
            key: 'page-navigation:page:user',
            label: '用户管理',
            nodeType: 'page',
            resourceIds: ['1', '2'],
          }],
        }, {
          key: 'page-navigation:page:org',
          label: '组织管理',
          nodeType: 'page',
          resourceIds: ['3'],
        }],
      }],
      checkedKeys: [],
      globalBatchOptions: [],
      ...overrides,
    },
    global: {
      stubs: {
        NCheckbox: CheckboxStub,
        NDropdown: { template: '<div><slot /></div>' },
        NEmpty: { template: '<div class="empty-stub" />' },
        NInput: { inheritAttrs: false, template: '<input />' },
        NButton: { template: '<button type="button"><slot name="icon" /><slot /></button>' },
        NSkeleton: { template: '<span />' },
        NTree: TreeStub,
      },
    },
  })
}

describe('role permission navigation tree', () => {
  function resourceTree() {
    return [{
      id: 1,
      resourceName: '系统管理',
      resourceType: 1,
      children: [{
        id: 2,
        resourceName: '用户管理',
        resourceType: 2,
        visible: 1,
        menuStatus: 1,
        children: [{
          id: 3,
          resourceName: '用户查询',
          resourceType: 3,
          perms: 'system:user:list',
        }],
      }, {
        id: 4,
        resourceName: '隐藏详情',
        resourceType: 2,
        visible: 0,
        menuStatus: 1,
        children: [],
      }, {
        id: 5,
        resourceName: '组织目录',
        resourceType: 1,
        children: [{
          id: 6,
          resourceName: '组织管理',
          resourceType: 2,
          visible: 1,
          menuStatus: 1,
          children: [],
        }],
      }],
    }]
  }

  it('keeps nested directories and puts pages under their menu directory', () => {
    const modules = buildWorkspaceModules(resourceTree())
    const tree = buildPermissionNavigationTree(modules)

    expect(tree.map(node => node.label)).toEqual(['系统管理'])
    expect(tree[0].children.map(node => node.label)).toEqual(['用户管理', '隐藏详情', '组织目录'])
    expect(tree[0].children[2].children.map(node => node.label)).toEqual(['组织管理'])
  })

  it('marks hidden navigation pages without removing their permission resource', () => {
    const modules = buildWorkspaceModules(resourceTree())
    const tree = buildPermissionNavigationTree(modules)
    const hidden = findPermissionNavigationNode(tree, 'page-navigation:page:4')

    expect(hidden?.navigationHidden).toBe(true)
    expect(hidden?.resourceIds).toEqual([4])
  })

  it('keeps the directory context when searching for a nested page', () => {
    const modules = buildWorkspaceModules(resourceTree())
    const tree = buildPermissionNavigationTree(modules)
    const filtered = filterPermissionNavigationTree(tree, '组织管理')

    expect(filtered).toHaveLength(1)
    expect(filtered[0].label).toBe('系统管理')
    expect(filtered[0].children[0].label).toBe('组织目录')
    expect(filtered[0].children[0].children[0].label).toBe('组织管理')
  })

  it('selects the matching page when a search term identifies a page', () => {
    const modules = buildWorkspaceModules(resourceTree())
    const tree = buildPermissionNavigationTree(modules)

    expect(findPermissionNavigationMatch(tree, '组织管理')?.label).toBe('组织管理')
  })
})

describe('role permission navigation interactions', () => {
  it('checks every resource under a directory and supports partial state', async () => {
    const wrapper = mountNavigation({ checkedKeys: ['1'] })
    await flushPromises()

    const rootCheckbox = wrapper.find('.tree-node-stub .n-checkbox')
    expect(rootCheckbox.exists()).toBe(true)
    expect(rootCheckbox.attributes('aria-checked')).toBe('mixed')

    await rootCheckbox.trigger('click')
    const updates = wrapper.emitted('update:checked-keys') || []
    expect(updates.at(-1)?.[0]).toEqual(['1', '2', '3'])
  })

  it('expands and collapses all visible directories', async () => {
    const wrapper = mountNavigation()
    await flushPromises()

    await wrapper.get('[aria-label="展开全部目录"]').trigger('click')
    expect(wrapper.find('.tree-stub').attributes('data-expanded-keys')).toBe(
      JSON.stringify(['directory:system', 'directory:user']),
    )

    await wrapper.get('[aria-label="收起全部目录"]').trigger('click')
    expect(wrapper.find('.tree-stub').attributes('data-expanded-keys')).toBe('[]')
  })

  it('does not expand unrelated directories when selecting a first-level directory', async () => {
    const wrapper = mountNavigation({
      selectedKey: 'directory:system',
      navigationTree: [
        {
          key: 'directory:system',
          label: '系统管理',
          nodeType: 'directory',
          resourceIds: ['1'],
          children: [{
            key: 'page-navigation:page:user',
            label: '用户管理',
            nodeType: 'page',
            resourceIds: ['1'],
          }],
        },
        {
          key: 'directory:organization',
          label: '组织管理',
          nodeType: 'directory',
          resourceIds: ['2'],
          children: [{
            key: 'page-navigation:page:organization',
            label: '组织列表',
            nodeType: 'page',
            resourceIds: ['2'],
          }],
        },
      ],
    })
    await flushPromises()

    await wrapper.setProps({ selectedKey: 'directory:organization' })
    expect(wrapper.find('.tree-stub').attributes('data-expanded-keys')).toBe('[]')
  })

  it('emits global select and clear actions from the sidebar', async () => {
    const wrapper = mountNavigation()
    await flushPromises()

    await wrapper.get('[aria-label="全选权限"]').trigger('click')
    await wrapper.get('[aria-label="清空权限"]').trigger('click')

    expect(wrapper.emitted('globalAction')).toEqual([['global:all'], ['global:clear']])
  })
})
