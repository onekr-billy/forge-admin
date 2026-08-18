import fs from 'node:fs'
import path from 'node:path'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  buildBottomActionConfig,
  normalizeButtonActionDraft,
} from '../button-action-config'
import ButtonActionConfig from '../ButtonActionConfig.vue'

const applicationApiMocks = vi.hoisted(() => ({
  businessApplicationDetailByCode: vi.fn(),
  businessApplicationObjects: vi.fn(),
}))
const processApiMocks = vi.hoisted(() => ({
  businessProcessDetail: vi.fn(),
  businessProcessPage: vi.fn(),
  createBusinessProcess: vi.fn(),
}))
const routerMocks = vi.hoisted(() => ({ push: vi.fn() }))
const routeState = vi.hoisted(() => ({
  fullPath: '/app-center/object/order/designer?panel=form&detailTab=sections&applicationCode=ORDER_APP',
  query: {},
}))
const messageMocks = vi.hoisted(() => ({ error: vi.fn(), warning: vi.fn() }))

vi.mock('@/api/business-application', () => applicationApiMocks)
vi.mock('@/api/business-process', () => processApiMocks)
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
  return { ...actual, useMessage: () => messageMocks }
})

describe('button action config', () => {
  beforeEach(() => {
    Object.values(applicationApiMocks).forEach(mock => mock.mockReset())
    Object.values(processApiMocks).forEach(mock => mock.mockReset())
    Object.values(routerMocks).forEach(mock => mock.mockReset())
    Object.values(messageMocks).forEach(mock => mock.mockReset())
    applicationApiMocks.businessApplicationDetailByCode.mockResolvedValue({ data: { id: '1001' } })
    applicationApiMocks.businessApplicationObjects.mockResolvedValue({
      data: [{ objectId: '2001', objectCode: 'order', objectName: '订单' }],
    })
    processApiMocks.businessProcessPage.mockResolvedValue({ data: { records: [] } })
    processApiMocks.businessProcessDetail.mockResolvedValue({ data: null })
    processApiMocks.createBusinessProcess.mockResolvedValue({
      data: { id: '3001', processCode: 'submit_order', processName: '提交订单流程' },
    })
  })

  it('loads only published processes from the current application and exposes process creation', () => {
    const source = fs.readFileSync(path.resolve(
      process.cwd(),
      'src/views/app-center/components/designer/ButtonActionConfig.vue',
    ), 'utf8')

    expect(source).toContain('businessApplicationDetailByCode(props.applicationCode)')
    expect(source).toContain('businessProcessPage({')
    expect(source).toContain('designStatus: \'PUBLISHED\'')
    expect(source).toContain('+ 新建业务流程')
    expect(source).toContain('createBusinessProcess({')
    expect(source).toContain('name: \'BusinessProcessDesigner\'')
  })

  it('creates a manual-start draft for the current object and opens its canvas directly', async () => {
    const wrapper = mount(ButtonActionConfig, {
      props: {
        show: true,
        modelValue: { type: 'process', label: '提交订单' },
        applicationCode: 'ORDER_APP',
        objectCode: 'order',
      },
      global: {
        stubs: {
          NModal: { props: ['show'], template: '<div v-if="show"><slot /><slot name="footer" /></div>' },
          NForm: { template: '<form><slot /></form>' },
          NFormItem: { template: '<div><slot /></div>' },
          NSelect: { template: '<div><slot name="action" /></div>' },
          NAlert: { template: '<div><slot /></div>' },
          NButton: { template: '<button v-bind="$attrs" @click="$emit(\'click\')"><slot /></button>' },
          NSpace: { template: '<div><slot /></div>' },
          NInput: true,
        },
      },
    })
    await flushPromises()

    await wrapper.find('[data-create-process]').trigger('click')
    await flushPromises()

    expect(applicationApiMocks.businessApplicationObjects).toHaveBeenCalledWith('1001')
    expect(processApiMocks.createBusinessProcess).toHaveBeenCalledWith({
      applicationId: '1001',
      processName: '提交订单流程',
      processDescription: '由页面按钮“提交订单”创建',
      subjectObjectId: '2001',
      status: 1,
    })
    expect(routerMocks.push).toHaveBeenCalledWith({
      name: 'BusinessProcessDesigner',
      params: { processId: '3001' },
      query: {
        applicationCode: 'ORDER_APP',
        from: 'button',
        objectCode: 'order',
        returnTo: routeState.fullPath,
      },
    })
  })

  it('maps every designer behavior to the bottom-bar runtime protocol', () => {
    expect(buildBottomActionConfig({ label: '保存' }, { behaviorType: 'submit' })).toMatchObject({
      label: '保存',
      type: 'save',
      actionCode: '',
    })
    expect(buildBottomActionConfig({}, {
      behaviorType: 'navigate',
      targetPageKey: 'page_order_detail',
    })).toMatchObject({
      type: 'navigate',
      actionType: 'NAVIGATE',
      actionCode: 'page_order_detail',
      targetPageKey: 'page_order_detail',
    })
    expect(buildBottomActionConfig({}, {
      behaviorType: 'process',
      processCode: 'order_submit',
      processId: '2088',
      permissionCode: 'order:submit',
    })).toMatchObject({
      type: 'process',
      actionType: 'START_PROCESS',
      actionCode: 'order_submit',
      processCode: 'order_submit',
      processId: '2088',
      permissionKey: 'order:submit',
      permissionCode: 'order:submit',
    })
    expect(buildBottomActionConfig({}, {
      behaviorType: 'custom',
      processCode: 'order_automation',
      processId: '2099',
    })).toMatchObject({
      type: 'action',
      actionType: 'BUSINESS_PROCESS_ACTION',
      actionCode: 'order_automation',
      processCode: 'order_automation',
      processId: '2099',
    })
  })

  it('round-trips existing process and navigation actions for editing', () => {
    expect(normalizeButtonActionDraft({
      type: 'process',
      actionCode: 'order_submit',
      processId: '2088',
      permissionKey: 'order:submit',
    })).toEqual({
      behaviorType: 'process',
      targetPageKey: '',
      processCode: 'order_submit',
      processId: '2088',
      permissionCode: 'order:submit',
    })
    expect(normalizeButtonActionDraft({
      type: 'navigate',
      actionCode: 'page_order_detail',
    })).toMatchObject({
      behaviorType: 'navigate',
      targetPageKey: 'page_order_detail',
    })
  })
})
