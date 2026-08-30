import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import FlowReadonlyFormPanel from '../FlowReadonlyFormPanel.vue'

const flowApiMocks = vi.hoisted(() => ({
  getProcessFormInfo: vi.fn(),
}))

const businessAppMocks = vi.hoisted(() => ({
  businessTaskFormReadonlyContext: vi.fn(),
}))

vi.mock('@/api/flow', () => ({
  default: flowApiMocks,
}))

vi.mock('@/api/business-app', () => businessAppMocks)

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
}))

vi.mock('@/components/ai-form', () => ({
  AiForm: defineComponent({ name: 'AiForm', setup: () => () => h('div', 'ai-form') }),
}))

vi.mock('@/components/ai-form/adapters/formCreate', () => ({
  formCreateToAiSchema: value => (Array.isArray(value) ? value : []),
}))

vi.mock('@/components/common/FlowBusinessForm.vue', () => ({
  default: defineComponent({ name: 'FlowBusinessForm', setup: () => () => h('div', 'external-form') }),
}))

vi.mock('@/components/page-templates/ChildTableEditor.vue', () => ({
  default: defineComponent({ name: 'ChildTableEditor', setup: () => () => h('div', 'child-table') }),
}))

vi.mock('@/utils/field-permissions', () => ({
  pickFirstNonEmptyFieldPermissions: () => [],
}))

describe('flowReadonlyFormPanel', () => {
  it('有流程实例时调用只读表单接口而不是变量接口', async () => {
    flowApiMocks.getProcessFormInfo.mockResolvedValue({
      code: 200,
      data: {
        formType: 'dynamic',
        formJson: [{ field: 'title', title: '标题' }],
        variables: { title: '采购申请' },
      },
    })
    businessAppMocks.businessTaskFormReadonlyContext.mockResolvedValue({
      code: 200,
      data: null,
    })

    const wrapper = mount(FlowReadonlyFormPanel, {
      props: {
        row: {
          processInstanceId: 'proc-1',
          businessKey: 'order:88',
          processDefKey: 'sample_purchase_order',
        },
        source: 'flowMonitor',
      },
      global: {
        stubs: {
          NSpin: true,
          NEmpty: { template: '<div class="empty">{{ description }}</div>', props: ['description'] },
          NAlert: true,
          NButton: true,
        },
      },
    })

    await flushPromises()

    expect(flowApiMocks.getProcessFormInfo).toHaveBeenCalledWith({
      processInstanceId: 'proc-1',
      businessKey: 'order:88',
      processDefKey: 'sample_purchase_order',
    })
    expect(wrapper.text()).toContain('节点动态表单')
    wrapper.unmount()
  })

  it('缺少查询条件时不发请求并展示空状态', async () => {
    flowApiMocks.getProcessFormInfo.mockReset()
    const wrapper = mount(FlowReadonlyFormPanel, {
      props: { row: {} },
      global: {
        stubs: {
          NSpin: true,
          NEmpty: { template: '<div class="empty">{{ description }}</div>', props: ['description'] },
          NAlert: true,
          NButton: true,
        },
      },
    })

    await flushPromises()

    expect(flowApiMocks.getProcessFormInfo).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('暂无可展示的表单内容')
    wrapper.unmount()
  })
})
