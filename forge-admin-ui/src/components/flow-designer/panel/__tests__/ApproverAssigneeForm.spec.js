import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { defineComponent, ref } from 'vue'
import ApproverAssigneeForm from '../ApproverAssigneeForm.vue'

const DOLLAR = '$'

const STUBS = {
  'n-form-item': { template: '<div><slot /></div>' },
  'n-select': true,
  'UserSelectPicker': {
    props: ['modelValue', 'labelValue'],
    emits: ['select'],
    template: `
      <button
        class="assignee-user-picker"
        type="button"
        @click="$emit('select', { id: '2090384244139360257', realName: '审批用户' })"
      >
        选择用户
      </button>
    `,
  },
}

function mountAssigneeForm(initialConfig = {}) {
  const Parent = defineComponent({
    components: { ApproverAssigneeForm },
    setup() {
      const config = ref({
        taskType: 'assignee',
        assignee: 'custom',
        assigneeUserId: '',
        assigneeExpr: '',
        assigneeUserName: '',
        ...initialConfig,
      })
      function updateConfig(patch) {
        config.value = { ...config.value, ...patch }
      }
      return { config, updateConfig }
    },
    template: `
      <ApproverAssigneeForm
        :config="config"
        @update:config="updateConfig"
      />
    `,
  })

  return mount(Parent, {
    global: { stubs: STUBS },
  })
}

describe('approverAssigneeForm', () => {
  it('指定人员保留雪花 ID 字符串且不再生成用户变量表达式', async () => {
    const wrapper = mountAssigneeForm({
      assigneeExpr: `${DOLLAR}{user_45}`,
      assigneeUserName: '历史用户',
    })

    await wrapper.find('.assignee-user-picker').trigger('click')

    expect(wrapper.vm.config).toMatchObject({
      assignee: 'custom',
      assigneeUserId: '2090384244139360257',
      assigneeExpr: '',
      assigneeUserName: '审批用户',
    })

    wrapper.unmount()
  })
})
