import { flushPromises, shallowMount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import BusinessFlowBindingPanel from '../components/designer/BusinessFlowBindingPanel.vue'
import TriggerPage from '../trigger.vue'

const businessApiMocks = vi.hoisted(() => ({
  businessFlowBinding: vi.fn(),
  businessFlowFormAssets: vi.fn(),
  businessFlowVariables: vi.fn(),
  businessObjectActions: vi.fn(),
  businessObjectFields: vi.fn(),
  businessObjectList: vi.fn(),
  businessTriggerLogs: vi.fn(),
  businessTriggerPage: vi.fn(),
  businessTriggerScenarioTemplates: vi.fn(),
  createBusinessTrigger: vi.fn(),
  deleteBusinessTrigger: vi.fn(),
  saveBusinessFlowBinding: vi.fn(),
  updateBusinessTrigger: vi.fn(),
  updateBusinessTriggerStatus: vi.fn(),
}))

const flowApiMocks = vi.hoisted(() => ({
  getModelList: vi.fn(),
}))

const routeState = vi.hoisted(() => ({
  params: {},
  query: { applicationCode: 'PURCHASE_APP' },
}))

const routerMocks = vi.hoisted(() => ({
  push: vi.fn(),
}))

vi.mock('@/api/business-app', () => businessApiMocks)
vi.mock('@/api/flow', () => ({ default: flowApiMocks }))
vi.mock('@/composables/useDict', () => ({
  useDict: () => ({ dict: { value: {} } }),
}))
vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    useRoute: () => routeState,
    useRouter: () => routerMocks,
  }
})
vi.mock('naive-ui', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    useMessage: () => ({ success: vi.fn(), error: vi.fn(), warning: vi.fn() }),
  }
})

const navigationStubs = {
  NAlert: { template: '<div class="n-alert"><slot /><slot name="action" /></div>' },
  NButton: { template: '<button v-bind="$attrs"><slot /></button>' },
  NModal: { props: ['show'], template: '<div v-if="show"><slot /><slot name="footer" /></div>' },
  NCollapse: { template: '<div><slot /></div>' },
  NCollapseItem: { template: '<div><slot name="header" /><slot /></div>' },
  NDataTable: true,
  NDrawer: { template: '<div><slot /></div>' },
  NDrawerContent: { template: '<div><slot /></div>' },
  NEmpty: true,
  NForm: { template: '<form><slot /></form>' },
  NFormItem: { template: '<div><slot /></div>' },
  NFormItemGi: { template: '<div><slot /></div>' },
  NGrid: { template: '<div><slot /></div>' },
  NIcon: { template: '<i><slot /></i>' },
  NInput: true,
  NInputNumber: true,
  NPagination: true,
  NSelect: true,
  NSpace: { template: '<div><slot /></div>' },
  NSpin: { template: '<div><slot /></div>' },
  NTag: { template: '<span><slot /></span>' },
}

describe('legacy process configuration entries', () => {
  beforeEach(() => {
    Object.values(businessApiMocks).forEach(mock => mock.mockReset())
    businessApiMocks.businessObjectList.mockResolvedValue({ data: [] })
    businessApiMocks.businessTriggerScenarioTemplates.mockResolvedValue({ data: [] })
    businessApiMocks.businessTriggerPage.mockResolvedValue({ data: { records: [], total: 0 } })
    businessApiMocks.businessFlowBinding.mockResolvedValue({ data: {} })
    businessApiMocks.businessFlowFormAssets.mockResolvedValue({ data: { formAssets: [], warnings: [] } })
    businessApiMocks.businessFlowVariables.mockResolvedValue({ data: [] })
    businessApiMocks.businessObjectActions.mockResolvedValue({ data: [] })
    flowApiMocks.getModelList.mockReset().mockResolvedValue({ data: [] })
    routerMocks.push.mockReset()
  })

  it('keeps the trigger entry read-only and routes to the application process workspace', async () => {
    const wrapper = shallowMount(TriggerPage, {
      global: { stubs: navigationStubs },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('此处仅支持查看历史配置')
    expect(wrapper.text()).not.toContain('新增触发器')

    wrapper.findComponent('[data-open-process-workspace]').vm.$emit('click')
    expect(routerMocks.push).toHaveBeenCalledWith({
      name: 'BusinessApplicationWorkspace',
      params: { applicationCode: 'PURCHASE_APP' },
      query: { section: 'automation' },
    })
  })

  it('keeps the legacy flow binding panel read-only with the same migration route', async () => {
    const wrapper = shallowMount(BusinessFlowBindingPanel, {
      props: {
        objectCode: 'sample_purchase_order',
        applicationCode: 'PURCHASE_APP',
      },
      global: { stubs: navigationStubs },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('此处仅支持查看历史配置')
    expect(wrapper.text()).not.toContain('保存流程')
    expect(wrapper.find('.flow-body').classes()).toContain('is-read-only')

    wrapper.findComponent('[data-open-process-workspace]').vm.$emit('click')
    expect(routerMocks.push).toHaveBeenCalledWith({
      name: 'BusinessApplicationWorkspace',
      params: { applicationCode: 'PURCHASE_APP' },
      query: { section: 'automation' },
    })
  })
})
