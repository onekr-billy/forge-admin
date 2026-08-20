import { mount, shallowMount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ActionAndApprovalNodeConfig from '../ActionAndApprovalNodeConfig.vue'

vi.mock('@/api/flow', () => ({
  default: {
    getModelDetail: vi.fn().mockResolvedValue({ data: { bpmnXml: '<definitions />' } }),
    createModel: vi.fn().mockResolvedValue({ data: { id: 'model-new', modelKey: 'created_approval' } }),
  },
}))
import flowApi from '@/api/flow'
import { createBusinessProcessSchema } from '../business-process-schema.js'
import BusinessProcessDesigner from '../BusinessProcessDesigner.vue'
import BusinessProcessNodeConfigDrawer from '../BusinessProcessNodeConfigDrawer.vue'
import BusinessProcessNodeRenderer from '../BusinessProcessNodeRenderer.vue'
import StartNodeConfig from '../StartNodeConfig.vue'

const objectRef = {
  objectId: '1900000000000001001',
  objectCode: 'sample_purchase_order',
}

const FORM_ASSET_STUBS = {
  FlowDesignPage: true,
  DingFlowViewer: true,
  TemplateVariableEditor: { template: '<div class="title-editor" />' },
  NModal: { template: '<div class="n-modal"><slot /></div>' },
  NSelect: { template: '<div class="n-select" />' },
  NButton: { template: '<button v-bind="$attrs" @click="$emit(\'click\')"><slot /></button>' },
  NTag: { template: '<span><slot /></span>' },
  NEmpty: { template: '<div class="n-empty"><slot /></div>' },
  'n-modal': { template: '<div class="n-modal"><slot /></div>' },
  'n-select': { template: '<div class="n-select" />' },
  'n-button': { template: '<button v-bind="$attrs" @click="$emit(\'click\')"><slot /></button>' },
  'n-tag': { template: '<span><slot /></span>' },
  'n-empty': { template: '<div class="n-empty"><slot /></div>' },
  'n-input': { template: '<input />' },
  'n-collapse': { template: '<div><slot /></div>' },
  'n-collapse-item': { template: '<div><slot /></div>' },
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
          DingFlowViewer: true,
          TemplateVariableEditor: { template: '<div class="title-editor" />' },
          NModal: { template: '<div class="n-modal"><slot /></div>' },
          NSelect: { template: '<div class="n-select" />' },
          NButton: { template: '<button v-bind="$attrs" @click="$emit(\'click\')"><slot /></button>' },
          NTag: { template: '<span><slot /></span>' },
          NEmpty: { template: '<div><slot /></div>' },
        },
      },
    })

    await wrapper.find('.open-flow-designer-button').trigger('click')

    expect(wrapper.emitted('openFlowDesigner')[0][0]).toMatchObject({
      modelId: '1900000000000002001',
      modelKey: 'sample_purchase_order_approval',
    })
    expect(wrapper.text()).toContain('审批人、会签、驳回和字段权限在真实流程设计器中维护')
    expect(wrapper.text()).toContain('在本页设计')
    expect(wrapper.text()).not.toMatch(/SpEL|Java|SQL|Webhook/)
  })

  it('defaults the current object form as the approval task form', async () => {
    const wrapper = mount(ActionAndApprovalNodeConfig, {
      props: {
        node: {
          id: 'approval_purchase',
          type: 'APPROVAL',
          name: '采购审批',
          ports: ['APPROVED', 'REJECTED', 'CANCELED', 'FAILED'],
          config: {},
        },
        objectCode: 'sample_purchase_order',
        objectName: '采购单',
        formAssets: [
          {
            formKey: 'purchase_form',
            formName: '采购申请单',
            formMode: 'BUSINESS_OBJECT_FORM',
          },
        ],
        fields: [{ fieldCode: 'name', fieldName: '名称' }],
      },
      global: { stubs: FORM_ASSET_STUBS },
    })

    const latest = wrapper.emitted('update:config')?.at(-1)?.[0]
    expect(latest).toMatchObject({
      formAsset: {
        formKey: 'purchase_form',
        formName: '采购申请单',
      },
    })
    expect(wrapper.text()).toContain('采购申请单')
    expect(wrapper.findAll('.asset-card')).toHaveLength(1)
    expect(wrapper.find('.asset-card').classes()).toContain('selected')
    wrapper.unmount()
  })

  it('synthesizes the object form when the catalog is empty', async () => {
    const wrapper = mount(ActionAndApprovalNodeConfig, {
      props: {
        node: {
          id: 'approval_empty_catalog',
          type: 'APPROVAL',
          name: '审批',
          ports: ['APPROVED', 'REJECTED', 'CANCELED', 'FAILED'],
          config: {},
        },
        objectCode: 'sample_purchase_order',
        objectName: '采购单',
        formAssets: [],
      },
      global: { stubs: FORM_ASSET_STUBS },
    })

    expect(wrapper.emitted('update:config')?.at(-1)?.[0]).toMatchObject({
      formAsset: {
        formKey: 'sample_purchase_order',
        formName: '采购单',
      },
    })
    expect(wrapper.text()).toContain('采购单')
    wrapper.unmount()
  })

  it('creates a business-form approval model bound to the object form', async () => {
    const wrapper = mount(ActionAndApprovalNodeConfig, {
      props: {
        node: {
          id: 'approval_purchase',
          type: 'APPROVAL',
          name: '采购审批',
          ports: ['APPROVED', 'REJECTED', 'CANCELED', 'FAILED'],
          config: {},
        },
        objectCode: 'sample_purchase_order',
        objectName: '采购单',
        formAssets: [{ formKey: 'purchase_form', formName: '采购申请单', formMode: 'BUSINESS_OBJECT_FORM' }],
      },
      global: { stubs: FORM_ASSET_STUBS },
    })

    const createButton = wrapper.findAll('button').find(button => button.text().includes('新建并设计'))
    await createButton.trigger('click')
    await Promise.resolve()
    await Promise.resolve()

    expect(flowApi.createModel).toHaveBeenCalledWith(expect.objectContaining({
      formType: 'business',
    }))
    expect(flowApi.createModel.mock.calls.at(-1)[0].formJson).toContain('purchase_form')
    wrapper.unmount()
  })

  it('persists the default form onto the node without a confirm click', async () => {
    const wrapper = mount(BusinessProcessNodeConfigDrawer, {
      props: {
        visible: true,
        node: {
          id: 'approval_purchase',
          type: 'APPROVAL',
          name: '采购审批',
          ports: ['APPROVED', 'REJECTED', 'CANCELED', 'FAILED'],
          config: {},
        },
        objectCode: 'sample_purchase_order',
        objectName: '采购单',
        formAssets: [{ formKey: 'purchase_form', formName: '采购申请单' }],
      },
      global: { stubs: FORM_ASSET_STUBS },
    })

    await Promise.resolve()
    expect(wrapper.emitted('save')?.at(-1)?.[0].config.formAsset).toMatchObject({
      formKey: 'purchase_form',
      formName: '采购申请单',
    })
    wrapper.unmount()
  })

  it('routes start and execution nodes to dedicated structured panels', async () => {
    const start = createBusinessProcessSchema({ processCode: 'purchase_submit', objectRef }).nodes[0]
    const wrapper = mount(BusinessProcessNodeConfigDrawer, {
      props: { visible: true, node: start },
      global: {
        stubs: {
          NButton: { template: '<button><slot /></button>' },
          NModal: { template: '<div class="node-config-modal-stub"><slot /></div>' },
          Modal: { template: '<div class="node-config-modal-stub"><slot /></div>' },
          'n-button': { template: '<button><slot /></button>' },
          'n-modal': { template: '<div class="node-config-modal-stub"><slot /></div>' },
        },
      },
    })
    expect(wrapper.find('[data-node-config="panel"]').exists()).toBe(true)
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

  it('binds the object form when inserting an approval node', async () => {
    const schema = createBusinessProcessSchema({ processCode: 'purchase_submit', objectRef })
    const wrapper = mount(BusinessProcessDesigner, {
      props: {
        schema,
        autoSaveDelay: 60000,
        objectName: '采购单',
        formAssets: [{ formKey: 'purchase_form', formName: '采购申请单' }],
      },
      global: { stubs: { BusinessProcessNodeConfigDrawer: true } },
    })

    await wrapper.find('[data-node-type="APPROVAL"]').trigger('click')

    const latestSchema = wrapper.emitted('update:schema').at(-1)[0]
    const approval = latestSchema.nodes.find(node => node.type === 'APPROVAL')
    expect(approval.config.formAsset).toMatchObject({
      formKey: 'purchase_form',
      formName: '采购申请单',
    })
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
