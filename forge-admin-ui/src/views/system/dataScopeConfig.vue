<template>
  <AiCrudPage
    ref="crudRef"
    api="/system/dataScopeConfig"
    :api-config="apiConfig"
    :search-schema="searchSchema"
    :columns="tableColumns"
    :edit-schema="editSchema"
    :before-render-form="beforeRenderForm"
    :before-submit="beforeSubmit"
    row-key="id"
    add-button-text="接入列表"
    :load-detail-on-edit="true"
    :edit-grid-cols="1"
    edit-label-placement="left"
    edit-label-align="left"
    edit-label-width="96px"
    form-open-mode="drawer"
    modal-width="680px"
    :hide-form-section-nav="true"
    :hide-selection="true"
    :scroll-x="960"
    :search-grid-cols="3"
    :search-max-visible-fields="3"
    :search-y-gap="8"
    search-label-width="84"
  >
    <template #form-resourceCode="{ value, updateValue, formData }">
      <NSelect
        :value="value"
        filterable
        tag
        clearable
        :options="pageOptions"
        placeholder="选择要限制可见范围的列表或页面"
        @update:value="(next) => handlePageChange(next, updateValue, formData)"
      />
    </template>

    <template #form-mapperMethod="{ value, updateValue }">
      <div class="mapper-field">
        <NInput
          :value="value"
          type="textarea"
          :autosize="{ minRows: 2, maxRows: 4 }"
          placeholder="完整方法名，必须与 XML 中 namespace.方法名 一致，例如：&#10;com.mdframe.forge.plugin.system.mapper.SysUserMapper.selectUserPage"
          @update:value="updateValue"
        />
        <div class="mapper-field__hint">
          拦截器按这一行精确匹配。填错或留空，列表过滤不会生效。
        </div>
        <div class="mapper-field__examples">
          <button
            v-for="item in mapperExamples"
            :key="item.value"
            type="button"
            class="mapper-example"
            @click="updateValue(item.value)"
          >
            <span>{{ item.label }}</span>
            <code>{{ shortMapper(item.value) }}</code>
          </button>
        </div>
      </div>
    </template>

    <template #form-scopeRules="{ formData }">
      <DataScopeRuleEditor :form-data="formData" @update="(field, value) => patchForm(formData, field, value)" />
    </template>
  </AiCrudPage>
</template>

<script setup>
import { NInput, NSelect } from 'naive-ui'
import { computed, h, onMounted, ref } from 'vue'
import { AiCrudPage } from '@/components/ai-form'
import SystemTableCell from '@/components/common/SystemTableCell.vue'
import DictTag from '@/components/DictTag.vue'
import { useDict } from '@/composables/useDict'
import { request } from '@/utils'
import { toNumberDictOptions } from '@/utils/dict-options'
import DataScopeRuleEditor from './components/DataScopeRuleEditor.vue'

defineOptions({ name: 'DataScopeConfig' })

const crudRef = ref(null)
const pageOptions = ref([])
const { dict } = useDict('sys_enable_disable')
const statusOptions = computed(() => toNumberDictOptions(dict.value.sys_enable_disable))

const apiConfig = {
  list: 'get@/system/dataScopeConfig/page',
  detail: 'post@/system/dataScopeConfig/getById',
  add: 'post@/system/dataScopeConfig/add',
  update: 'post@/system/dataScopeConfig/edit',
  delete: 'post@/system/dataScopeConfig/remove',
}

const mapperExamples = [
  { label: '用户分页', value: 'com.mdframe.forge.plugin.system.mapper.SysUserMapper.selectUserPage' },
  { label: '组织列表', value: 'com.mdframe.forge.plugin.system.mapper.SysOrgMapper.selectOrgList' },
  { label: '流程监控', value: 'com.mdframe.forge.starter.flow.mapper.FlowBusinessMapper.selectMonitorBusinessPage' },
]

const searchSchema = computed(() => [
  { field: 'resourceName', label: '列表名称', type: 'input', props: { placeholder: '搜索列表或页面名称' } },
  { field: 'resourceCode', label: '编码', type: 'input', props: { placeholder: '如 system:user:list' } },
  { field: 'enabled', label: '状态', type: 'select', props: { placeholder: '全部状态', options: statusOptions.value } },
])

const tableColumns = computed(() => [
  {
    prop: 'resourceName',
    label: '列表',
    minWidth: 240,
    render: row => h(SystemTableCell, {
      title: row.resourceName || '-',
      subtitle: row.resourceCode || '未设置编码',
      interactive: true,
      tooltip: `编辑数据范围：${row.resourceName || row.resourceCode || '-'}`,
      onActivate: () => crudRef.value?.showEdit(row),
    }),
  },
  {
    prop: 'mapperMethod',
    label: 'Mapper 方法',
    minWidth: 220,
    render: row => h(SystemTableCell, {
      title: shortMapper(row.mapperMethod),
      subtitle: row.tableAlias ? `表别名 ${row.tableAlias}` : '未设表别名',
      tooltip: row.mapperMethod || '未配置 Mapper，列表过滤不会生效',
    }),
  },
  {
    prop: 'scopeFields',
    label: '可见范围',
    minWidth: 240,
    render: renderScopeTags,
  },
  {
    prop: 'enabled',
    label: '状态',
    width: 88,
    render: row => h(DictTag, { options: statusOptions.value, value: row.enabled, size: 'small' }),
  },
  {
    prop: 'action',
    label: '操作',
    width: 140,
    fixed: 'right',
    actions: [
      { label: '编辑', key: 'edit', type: 'primary', onClick: row => crudRef.value?.showEdit(row) },
      { label: '删除', key: 'delete', type: 'error', onClick: handleDelete },
    ],
  },
])

const editSchema = computed(() => [
  { type: 'divider', label: '接入哪个列表', span: 1, props: { titlePlacement: 'left' } },
  {
    field: 'resourceCode',
    label: '列表/页面',
    type: 'slot',
    slotName: 'resourceCode',
    rules: [{ required: true, message: '请选择要接入的列表', trigger: 'change' }],
  },
  {
    field: 'resourceName',
    label: '显示名称',
    type: 'input',
    rules: [{ required: true, message: '请输入显示名称', trigger: 'blur' }],
    props: { placeholder: '如：用户管理' },
  },
  {
    field: 'enabled',
    label: '状态',
    type: 'radio',
    defaultValue: 1,
    props: { options: statusOptions.value },
  },
  { type: 'divider', label: '绑定后台 Mapper', span: 1, props: { titlePlacement: 'left', description: '拦截器按完整方法名精确匹配 XML SQL，和上面选的页面不会自动对应。' } },
  {
    field: 'mapperMethod',
    label: 'Mapper 方法',
    type: 'slot',
    slotName: 'mapperMethod',
    rules: [{ required: true, message: '请填写 Mapper 方法', trigger: 'blur' }],
  },
  {
    field: 'tableAlias',
    label: '表别名',
    type: 'input',
    defaultValue: 't',
    props: { placeholder: '如 t，SQL 无别名可留空' },
  },
  { type: 'divider', label: '按什么限制可见数据', span: 1, props: { titlePlacement: 'left' } },
  {
    field: 'scopeRules',
    label: '过滤规则',
    type: 'slot',
    slotName: 'scopeRules',
    showLabel: false,
  },
  { type: 'divider', label: '高级', span: 1, props: { titlePlacement: 'left', description: 'JOIN 用户表做区划过滤时才需要。' } },
  {
    field: 'userRegionColumn',
    label: '用户区划字段',
    type: 'input',
    props: { placeholder: '选填，JOIN 用户表时使用' },
  },
  {
    field: 'userTableAlias',
    label: '用户表别名',
    type: 'input',
    props: { placeholder: '选填，如 u' },
  },
  {
    field: 'remark',
    label: '备注',
    type: 'textarea',
    props: { rows: 2, placeholder: '补充说明，可选' },
  },
])

function renderScopeTags(row) {
  const values = []
  if (hasValue(row.userIdColumn))
    values.push('本人')
  if (hasValue(row.orgIdColumn))
    values.push('本组织')
  if (hasValue(row.regionCodeColumn))
    values.push('本区划')
  if (hasValue(row.tenantIdColumn))
    values.push('租户')
  if (Number(row.flowRelatedVisible) === 1)
    values.push('经手可见')

  if (!values.length)
    return h('span', { class: 'empty-text' }, '未配置过滤')

  return h(SystemTableCell, { values })
}

function hasValue(value) {
  return String(value || '').trim() !== ''
}

function shortMapper(mapperMethod) {
  if (!mapperMethod)
    return '未填写'
  const parts = String(mapperMethod).split('.')
  if (parts.length < 2)
    return mapperMethod
  return `${parts[parts.length - 2]}.${parts[parts.length - 1]}`
}

function patchForm(formData, field, value) {
  formData[field] = value
}

function handlePageChange(next, updateValue, formData) {
  updateValue(next)
  const matched = pageOptions.value.find(item => item.value === next)
  if (matched?.resourceName)
    formData.resourceName = matched.resourceName
}

function beforeRenderForm(row) {
  if (!row) {
    return {
      enabled: 1,
      tableAlias: 't',
      userIdColumn: 'create_by',
      orgIdColumn: 'org_id',
      tenantIdColumn: 'tenant_id',
      flowRelatedVisible: 0,
      recordIdColumn: 'id',
    }
  }
  return {
    ...row,
    enabled: Number(row.enabled) === 0 ? 0 : 1,
    flowRelatedVisible: Number(row.flowRelatedVisible) === 1 ? 1 : 0,
    recordIdColumn: row.recordIdColumn || 'id',
  }
}

function beforeSubmit(formData) {
  if (!hasValue(formData.userIdColumn) && !hasValue(formData.orgIdColumn)) {
    window.$message?.error('至少开启「只能看自己的数据」或「只能看本组织的数据」')
    return false
  }
  if (!hasValue(formData.mapperMethod)) {
    window.$message?.error('请填写查询方法，否则列表过滤不会生效')
    return false
  }
  const payload = { ...formData }
  delete payload.scopeRules
  if (Number(payload.flowRelatedVisible) !== 1) {
    payload.flowRelatedVisible = 0
    payload.flowBusinessType = undefined
    payload.recordIdColumn = undefined
  }
  Object.keys(payload).forEach((key) => {
    if (typeof payload[key] === 'string')
      payload[key] = payload[key].trim()
  })
  return payload
}

function handleDelete(row) {
  window.$dialog.warning({
    title: '确认删除',
    content: `确定不再限制「${row.resourceName || row.resourceCode || row.id}」的可见范围吗？`,
    positiveText: '确定',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        const res = await request.post('/system/dataScopeConfig/remove', null, {
          params: { id: row.id },
        })
        if (res.code === 200) {
          window.$message.success('已删除')
          crudRef.value?.refresh()
        }
      }
      catch {
        window.$message.error('删除失败')
      }
    },
  })
}

function flattenPages(nodes = [], parentName = '', result = []) {
  nodes.forEach((node) => {
    const type = Number(node.resourceType)
    const name = node.resourceName || node.perms || node.path || ''
    const label = parentName && type !== 2 ? `${parentName} / ${name}` : name
    if ((type === 2 || type === 3 || type === 4) && node.perms) {
      result.push({
        label,
        value: node.perms,
        resourceName: node.resourceName || name,
      })
    }
    if (Array.isArray(node.children) && node.children.length)
      flattenPages(node.children, type === 1 || type === 2 ? name : parentName, result)
  })
  return result
}

async function loadPageOptions() {
  try {
    const res = await request.get('/system/resource/tree')
    const list = Array.isArray(res) ? res : (Array.isArray(res?.data) ? res.data : [])
    const seen = new Set()
    pageOptions.value = flattenPages(list).filter((item) => {
      if (!item.value || seen.has(item.value))
        return false
      seen.add(item.value)
      return true
    })
  }
  catch (error) {
    console.error('加载列表选项失败', error)
  }
}

onMounted(() => {
  loadPageOptions()
})
</script>

<style scoped>
.empty-text {
  color: var(--text-tertiary);
  font-size: 12px;
}

.mapper-field {
  display: grid;
  gap: 8px;
  width: 100%;
  min-width: 0;
}

.mapper-field :deep(textarea) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  line-height: 18px;
}

.mapper-field__hint {
  color: var(--text-tertiary);
  font-size: 12px;
  line-height: 18px;
}

.mapper-field__examples {
  display: grid;
  gap: 6px;
}

.mapper-example {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
  min-width: 0;
  padding: 6px 8px;
  border: 1px solid var(--border-light);
  border-radius: 4px;
  background: var(--bg-primary);
  text-align: left;
  cursor: pointer;
}

.mapper-example:hover {
  border-color: var(--primary-color);
}

.mapper-example span {
  flex-shrink: 0;
  color: var(--text-secondary);
  font-size: 12px;
}

.mapper-example code {
  min-width: 0;
  overflow: hidden;
  color: var(--text-tertiary);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
