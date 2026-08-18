import { shallowMount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { defineComponent, nextTick } from 'vue'
import PageSectionEditor from '../PageSectionEditor.vue'

const DraggableStub = defineComponent({
  name: 'Draggable',
  props: {
    modelValue: {
      type: Array,
      default: () => [],
    },
  },
  emits: ['update:modelValue'],
  template: '<div class="draggable-stub" />',
})

describe('page section editor', () => {
  it('persists section and bottom-action order emitted by drag sorting', async () => {
    const sections = [
      { sectionId: 'member', sectionType: 'card', title: '会员信息', fields: ['memberName'] },
      { sectionId: 'payment', sectionType: 'card', title: '收款信息', fields: ['payMethod'] },
    ]
    const actions = [
      { actionId: 'reset', type: 'reset', label: '清空' },
      { actionId: 'submit', type: 'action', actionCode: 'submit_presale', label: '提交' },
    ]
    const wrapper = shallowMount(PageSectionEditor, {
      props: {
        modelValue: { pageSections: sections, bottomBar: { actions } },
        fields: [
          { fieldCode: 'memberName', fieldName: '会员名称' },
          { fieldCode: 'payMethod', fieldName: '收款方式' },
        ],
        actions: [{ actionCode: 'submit_presale', actionName: '提交预售单' }],
      },
      global: {
        config: {
          warnHandler: () => {},
        },
        stubs: {
          draggable: DraggableStub,
          // 底栏编辑已抽取为子组件，这里渲染真实组件以覆盖按钮拖拽排序链路。
          BottomBarEditor: false,
        },
      },
    })

    const draggables = wrapper.findAllComponents(DraggableStub)
    expect(draggables).toHaveLength(3)

    draggables[0].vm.$emit('update:modelValue', [...sections].reverse())
    draggables[2].vm.$emit('update:modelValue', [...actions].reverse())
    await nextTick()

    const protocol = wrapper.vm.getValue()
    expect(protocol.pageSections.map(section => section.sectionId)).toEqual(['payment', 'member'])
    expect(protocol.bottomBar.actions.map(action => action.actionId)).toEqual(['submit', 'reset'])
    wrapper.unmount()
  })
})
