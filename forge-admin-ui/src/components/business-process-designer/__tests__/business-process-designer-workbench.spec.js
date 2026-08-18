import { mount, shallowMount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ActionAndApprovalNodeConfig from '../ActionAndApprovalNodeConfig.vue'
import { createBusinessProcessSchema } from '../business-process-schema.js'
import BusinessProcessDesigner from '../BusinessProcessDesigner.vue'
import BusinessProcessNodeConfigDrawer from '../BusinessProcessNodeConfigDrawer.vue'
import BusinessProcessNodeRenderer from '../BusinessProcessNodeRenderer.vue'
import StartNodeConfig from '../StartNodeConfig.vue'

const objectRef = {
  objectId: '1900000000000001001',
  objectCode: 'sample_purchase_order',
}

describe('business process node renderer', () => {
  it('renders business approval ports without BPMN element metadata', async () => {
    const node = {
      id: 'approval_purchase',
      type: 'APPROVAL',
      name: '采购审批',
      ports: ['APPROVED', 'REJECTED', 'CANCELED', 'FAILED'],
      config: { flowModelKey: 'sample_purchase_order_approval' },
    }
    const wrapper = mount(BusinessProcessNodeRenderer, {
      props: {
        node,
        position: { x: 120, y: 80, width: 288, height: 92 },
        selected: true,
      },
    })

    expect(wrapper.text()).toContain('采购审批')
    expect(wrapper.findAll('[data-business-port]')).toHaveLength(4)
    expect(wrapper.attributes('data-node-id')).toBe('approval_purchase')
    expect(wrapper.attributes('data-bpmn-element-id')).toBeUndefined()

    await wrapper.trigger('click')
    expect(wrapper.emitted('select')[0][0]).toEqual(node)
  })
})

describe('structured business node configuration', () => {
  it('switches start modes with a governed record source and no expression editor', async () => {
    const wrapper = mount(StartNodeConfig, {
      props: {
        type: 'START_MANUAL',
        config: { positions: ['ROW'], permission: 'ai:businessProcess:start' },
        fields: [{ fieldCode: 'status', fieldName: '状态' }],
      },
    })
    const triggerTypeSelect = wrapper.find('[data-start-type]')

    await triggerTypeSelect.setValue('START_EVENT')

    expect(wrapper.emitted('update:type')[0][0]).toBe('START_EVENT')
    expect(wrapper.emitted('update:recordIdSource')[0][0]).toBe('EVENT_RECORD')
    expect(wrapper.text()).not.toMatch(/JSON|SpEL|Java|SQL|Webhook/)
  })

  it('applies editable start templates while preserving the manual configuration path', async () => {
    const wrapper = mount(StartNodeConfig, {
      props: {
        type: 'START_MANUAL',
        config: {},
        fields: [{ fieldCode: 'dueDate', fieldName: '到期日期' }],
      },
    })

    expect(wrapper.findAll('[data-start-template]')).toHaveLength(4)
    await wrapper.find('[data-start-template="EVENT_STATUS_CHANGED"]').trigger('click')

    expect(wrapper.emitted('update:type').at(-1)[0]).toBe('START_EVENT')
    expect(wrapper.emitted('update:recordIdSource').at(-1)[0]).toBe('EVENT_RECORD')
    expect(wrapper.emitted('update:config').at(-1)[0]).toMatchObject({ eventType: 'STATUS_CHANGED' })

    await wrapper.setProps({ type: 'START_EVENT', config: { eventType: 'STATUS_CHANGED' } })
    await wrapper.find('select:not([data-start-type])').setValue('RECORD_UPDATED')
    expect(wrapper.emitted('update:config').at(-1)[0]).toMatchObject({ eventType: 'RECORD_UPDATED' })
  })

  it('applies action templates as governed config that remains editable', async () => {
    const wrapper = shallowMount(ActionAndApprovalNodeConfig, {
      props: {
        node: {
          id: 'action_update',
          type: 'ACTION',
          name: '更新采购单',
          ports: [],
          config: {},
        },
        objectCode: 'sample_purchase_order',
        businessActions: [{ actionCode: 'adjust_quantity', actionName: '调整库存数量' }],
      },
      global: {
        stubs: {
          NModal: { template: '<div><slot /></div>' },
        },
      },
    })

    expect(wrapper.findAll('[data-action-template]')).toHaveLength(4)
    await wrapper.find('[data-action-template="UPDATE_STATUS"]').trigger('click')
    expect(wrapper.emitted('update:config').at(-1)[0]).toMatchObject({
      actionType: 'UPDATE_RECORD',
      objectCode: 'sample_purchase_order',
      fieldMappings: [{ field: 'status', valueSource: 'CONSTANT', value: '' }],
    })

    await wrapper.find('[data-action-template="ADJUST_NUMBER"]').trigger('click')
    expect(wrapper.emitted('update:config').at(-1)[0]).toMatchObject({
      actionType: 'BUSINESS_ACTION',
      businessActionCode: '',
    })
  })

  it('opens the real flow designer for a selected deployed approval model', async () => {
    const wrapper = shallowMount(ActionAndApprovalNodeConfig, {
      props: {
        node: {
          id: 'approval_purchase',
          type: 'APPROVAL',
          name: '采购审批',
          ports: ['APPROVED', 'REJECTED', 'CANCELED', 'FAILED'],
          config: {
            flowModelKey: 'sample_purchase_order_approval',
            versionPolicy: 'PINNED_AT_APPLICATION_PUBLISH',
          },
        },
        objectCode: 'sample_purchase_order',
        flowModels: [
          {
            modelId: '1900000000000002001',
            modelKey: 'sample_purchase_order_approval',
            modelName: '采购审批流程',
            status: 1,
            deploymentId: 'deployment-1',
          },
          {
            modelId: '1900000000000002002',
            modelKey: 'unpublished_approval',
            modelName: '未发布审批流程',
            status: 0,
            deploymentId: null,
          },
        ],
      },
      global: {
        stubs: {
          FlowDesignPage: true,
          NModal: { template: '<div class="n-modal"><slot /></div>' },
        },
      },
    })

    await wrapper.find('.open-flow-designer-button').trigger('click')

    expect(wrapper.emitted('openFlowDesigner')[0][0]).toMatchObject({
      modelId: '1900000000000002001',
      modelKey: 'sample_purchase_order_approval',
    })
    expect(wrapper.text()).toContain('审批人、会签、驳回和字段权限在真实流程设计器中维护')
    expect(wrapper.text()).not.toContain('未发布审批流程')
    expect(wrapper.text()).not.toMatch(/SpEL|Java|SQL|Webhook/)
  })

  it('routes start and execution nodes to dedicated structured panels', async () => {
    const start = createBusinessProcessSchema({ processCode: 'purchase_submit', objectRef }).nodes[0]
    const wrapper = mount(BusinessProcessNodeConfigDrawer, {
      props: { visible: true, node: start },
      global: {
        stubs: {
          NButton: { template: '<button><slot /></button>' },
          NDrawer: { template: '<div><slot /></div>' },
          NDrawerContent: { template: '<div><slot /><slot name="header" /><slot name="footer" /></div>' },
        },
      },
    })
    expect(wrapper.findComponent(StartNodeConfig).exists()).toBe(true)

    await wrapper.setProps({
      node: {
        id: 'action_update',
        type: 'ACTION',
        name: '更新采购单',
        ports: [],
        config: { actionType: 'UPDATE_RECORD' },
      },
    })
    expect(wrapper.findComponent(ActionAndApprovalNodeConfig).exists()).toBe(true)
  })
})

describe('business process designer workbench', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('auto-saves dirty drafts, supports explicit validation and surfaces hash conflicts', async () => {
    const schema = createBusinessProcessSchema({ processCode: 'purchase_submit', objectRef })
    const wrapper = mount(BusinessProcessDesigner, {
      props: {
        schema,
        autoSaveDelay: 20,
        saveState: 'idle',
      },
      global: {
        stubs: {
          BusinessProcessNodeConfigDrawer: true,
        },
      },
    })

    await wrapper.find('[data-node-type="ACTION"]').trigger('click')
    await vi.advanceTimersByTimeAsync(25)

    expect(wrapper.emitted('dirtyChange').at(-1)[0]).toBe(true)
    expect(wrapper.emitted('save').at(-1)[1]).toMatchObject({ reason: 'auto' })

    await wrapper.find('[data-designer-action="validate"]').trigger('click')
    expect(wrapper.emitted('validate')).toHaveLength(1)

    await wrapper.setProps({ saveState: 'conflict' })
    expect(wrapper.text()).toContain('草稿已被其他人更新')
    expect(wrapper.find('[data-designer-action="reload"]').exists()).toBe(true)
  })

  it('warns the browser before leaving a dirty draft', async () => {
    const schema = createBusinessProcessSchema({ processCode: 'purchase_submit', objectRef })
    const wrapper = mount(BusinessProcessDesigner, {
      props: { schema, autoSaveDelay: 60000 },
      global: { stubs: { BusinessProcessNodeConfigDrawer: true } },
    })

    await wrapper.find('[data-node-type="ACTION"]').trigger('click')
    const event = new Event('beforeunload', { cancelable: true })
    window.dispatchEvent(event)

    expect(event.defaultPrevented).toBe(true)
    wrapper.unmount()
  })

  it('drags a palette node onto a concrete canvas insertion edge', async () => {
    const schema = createBusinessProcessSchema({ processCode: 'purchase_submit', objectRef })
    const wrapper = mount(BusinessProcessDesigner, {
      props: { schema, autoSaveDelay: 60000 },
      global: { stubs: { BusinessProcessNodeConfigDrawer: true } },
    })
    const transferStore = new Map()
    const dataTransfer = {
      effectAllowed: 'none',
      dropEffect: 'none',
      setData: (key, value) => transferStore.set(key, value),
      getData: key => transferStore.get(key) || '',
    }

    const paletteItem = wrapper.find('[data-node-type="ACTION"]')
    expect(paletteItem.attributes('draggable')).toBe('true')
    await paletteItem.trigger('dragstart', { dataTransfer })
    expect(dataTransfer.effectAllowed).toBe('copy')
    expect([...transferStore.values()]).toContain('ACTION')

    const canvas = wrapper.find('.business-process-canvas')
    await canvas.trigger('dragover', { clientX: 300, clientY: 220, dataTransfer })
    expect(wrapper.find('[data-business-insert-edge]').classes()).toContain('is-active-target')
    await canvas.trigger('drop', { clientX: 300, clientY: 220, dataTransfer })

    const latestSchema = wrapper.emitted('update:schema').at(-1)[0]
    expect(latestSchema.nodes.some(node => node.type === 'ACTION')).toBe(true)
    expect(wrapper.findComponent(BusinessProcessNodeConfigDrawer).props('visible')).toBe(true)
  })
})
