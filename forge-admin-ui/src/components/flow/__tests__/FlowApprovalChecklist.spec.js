import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import FlowApprovalChecklist from '../FlowApprovalChecklist.vue'

const STUBS = {
  NCheckbox: {
    props: ['checked', 'disabled'],
    emits: ['update:checked'],
    template: '<input type="checkbox" :checked="checked" :disabled="disabled" @change="$emit(\'update:checked\', $event.target.checked)" />',
  },
}

describe('flowApprovalChecklist', () => {
  it('必审要点未勾选时不完成', () => {
    const wrapper = mount(FlowApprovalChecklist, {
      props: {
        responsibilityDescription: '核对金额',
        approvalPoints: [
          { id: 'p1', content: '金额', required: true, sort: 1 },
          { id: 'p2', content: '附件', required: false, sort: 2 },
        ],
        modelValue: { p1: false, p2: true },
      },
      global: { stubs: STUBS },
    })

    expect(wrapper.text()).toContain('审批职责')
    expect(wrapper.text()).toContain('必审 0/1')
    expect(wrapper.vm.requiredComplete).toBe(false)
    wrapper.unmount()
  })

  it('勾选必审要点后完成', () => {
    const wrapper = mount(FlowApprovalChecklist, {
      props: {
        approvalPoints: [{ id: 'p1', content: '金额', required: true, sort: 1 }],
        modelValue: { p1: true },
      },
      global: { stubs: STUBS },
    })

    expect(wrapper.text()).toContain('必审 1/1')
    expect(wrapper.vm.requiredComplete).toBe(true)
    wrapper.unmount()
  })
})
