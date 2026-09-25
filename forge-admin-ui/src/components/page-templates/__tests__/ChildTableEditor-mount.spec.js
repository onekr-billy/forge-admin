import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import ChildTableEditor from '../ChildTableEditor.vue'

vi.mock('vue-router', () => ({ useRoute: () => ({ query: {}, params: {}, path: '/' }) }))
vi.mock('@/api/lowcode-query-source', () => ({ executeLowcodeQuerySource: vi.fn() }))
vi.mock('@/components/ai-form/AiFormItem.vue', () => ({ default: { name: 'AiFormItem', props: ['field'], render: () => null } }))
vi.mock('@/components/ai-form/AiRecordSelectorModal.vue', () => ({ default: { render: () => null } }))
vi.mock('@/components/common/UserSelectPicker.vue', () => ({ default: { render: () => null } }))
vi.mock('@/utils/collaboration-runtime', () => ({ scan: vi.fn() }))

function mountEditor(fields) {
  return mount(ChildTableEditor, {
    props: {
      value: { order_item: [] },
      childrenConfig: [{ key: 'order_item', modelCode: 'order_item', label: '明细', fields }],
    },
    global: {
      stubs: {
        'n-tabs': { template: '<div><slot /></div>' },
        'n-tab-pane': { template: '<div><slot /></div>' },
        'n-space': { template: '<div><slot /></div>' },
        'n-button': { template: '<button><slot /></button>' },
        'n-empty': true,
        'n-input': true,
        'n-input-number': true,
        'n-select': true,
        'n-date-picker': true,
        'n-switch': true,
      },
    },
  })
}

describe('child table editor column visibility', () => {
  it('shows selection columns named xxxId but hides plain internal id columns', () => {
    const wrapper = mountEditor([
      { field: 'handlerId', label: '处理人', type: 'userSelect' },
      { field: 'deptId', label: '部门', type: 'orgTreeSelect' },
      { field: 'customerId', label: '客户', type: 'objectReference' },
      { field: 'sourceId', label: '来源ID', type: 'input' },
      { field: 'remark', label: '备注', type: 'input' },
    ])

    const headers = wrapper.findAll('th').map(th => th.text())
    expect(headers).toEqual(expect.arrayContaining(['处理人', '部门', '客户', '备注']))
    expect(headers).not.toContain('来源ID')
  })
})
