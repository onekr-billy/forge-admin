<template>
  <AiCrudPage
    ref="crudRef"
    api="/api/message/template"
    :api-config="{
      list: 'get@/api/message/template/page',
      detail: 'get@/api/message/template/:id',
      add: 'post@/api/message/template',
      update: 'put@/api/message/template',
      delete: 'delete@/api/message/template/:id',
    }"
    :search-schema="searchSchema"
    :columns="tableColumns"
    :edit-schema="editSchema"
    row-key="id"
    add-button-text="新增模板"
    modal-width="1080px"
    edit-label-placement="left"
    edit-label-align="left"
    :edit-label-width="92"
    :edit-grid-cols="2"
    :load-detail-on-edit="true"
    :before-submit="handleBeforeSubmit"
  >
    <template #form-templateDesigner="{ formData }">
      <div class="template-designer">
        <aside class="template-variable-panel">
          <div class="variable-panel-head">
            <div>
              <div class="variable-panel-title">
                变量
              </div>
              <div class="variable-panel-count">
                {{ getVariablePanelSummary(formData) }}
              </div>
            </div>
            <n-input
              v-model:value="variableKeyword"
              size="small"
              clearable
              placeholder="搜索或输入变量名"
            />
          </div>

          <div class="template-target-switch">
            <button
              type="button"
              :class="{ active: activeTemplateField === 'titleTemplate' }"
              @click="setActiveTemplateField('titleTemplate')"
            >
              标题
            </button>
            <button
              type="button"
              :class="{ active: activeTemplateField === 'contentTemplate' }"
              @click="setActiveTemplateField('contentTemplate')"
            >
              内容
            </button>
          </div>

          <n-scrollbar class="variable-scrollbar">
            <button
              v-for="item in getVisibleTemplateVariables(formData)"
              :key="item.key"
              type="button"
              class="variable-row"
              :class="{ used: item.used, custom: item.source === 'current' }"
              @click="insertTemplateVariable(formData, item.key)"
            >
              <span class="variable-name">{{ item.label }}</span>
              <span class="variable-code">{{ formatPlaceholder(item.key) }}</span>
            </button>
            <button
              v-if="canInsertCustomVariable(formData)"
              type="button"
              class="variable-row custom"
              @click="addCustomVariable(formData)"
            >
              <span class="variable-name">添加自定义变量</span>
              <span class="variable-code">{{ formatPlaceholder(normalizeVariableKey(variableKeyword)) }}</span>
            </button>
            <div v-if="getVisibleTemplateVariables(formData).length === 0 && !canInsertCustomVariable(formData)" class="variable-empty">
              无匹配变量
            </div>
          </n-scrollbar>
        </aside>

        <section class="template-editor-panel">
          <div class="template-field-block">
            <div class="template-field-head">
              <span>标题模板</span>
              <span>{{ countTemplateVariables(formData.titleTemplate) }} 个变量</span>
            </div>
            <n-input
              :value="formData.titleTemplate"
              type="textarea"
              :rows="2"
              placeholder="请输入标题模板"
              @focus="rememberTemplateCursor('titleTemplate', $event)"
              @click="rememberTemplateCursor('titleTemplate', $event)"
              @keyup="rememberTemplateCursor('titleTemplate', $event)"
              @update:value="setTemplateField(formData, 'titleTemplate', $event)"
            />
          </div>

          <div class="template-field-block">
            <div class="template-field-head">
              <span>内容模板</span>
              <span>{{ countTemplateVariables(formData.contentTemplate) }} 个变量</span>
            </div>
            <n-input
              :value="formData.contentTemplate"
              type="textarea"
              :rows="7"
              placeholder="请输入内容模板"
              @focus="rememberTemplateCursor('contentTemplate', $event)"
              @click="rememberTemplateCursor('contentTemplate', $event)"
              @keyup="rememberTemplateCursor('contentTemplate', $event)"
              @update:value="setTemplateField(formData, 'contentTemplate', $event)"
            />
          </div>

          <div class="template-preview">
            <div class="template-preview-head">
              预览
            </div>
            <div class="template-preview-title">
              {{ renderTemplatePreview(formData.titleTemplate) || '标题预览' }}
            </div>
            <div class="template-preview-content" v-html="renderTemplatePreviewHtml(formData.contentTemplate) || '内容预览'" />
          </div>
        </section>
      </div>
    </template>
  </AiCrudPage>
</template>

<script setup>
import { computed, h, ref } from 'vue'
import { AiCrudPage } from '@/components/ai-form'
import DictTag from '@/components/DictTag.vue'
import { useDict } from '@/composables/useDict'

defineOptions({ name: 'MessageTemplate' })

const MESSAGE_TYPE_DICT = 'sys_message_type'
const MESSAGE_CHANNEL_DICT = 'sys_message_channel'
const ENABLE_DISABLE_DICT = 'sys_enable_disable'

const crudRef = ref(null)

const { dict } = useDict(MESSAGE_TYPE_DICT, MESSAGE_CHANNEL_DICT, ENABLE_DISABLE_DICT)

const messageTypeOptions = computed(() => dict.value[MESSAGE_TYPE_DICT] || [])
const channelOptions = computed(() => dict.value[MESSAGE_CHANNEL_DICT] || [])
const enabledOptions = computed(() => toNumberOptions(dict.value[ENABLE_DISABLE_DICT]))
const activeTemplateField = ref('contentTemplate')
const variableKeyword = ref('')
const templateCursorMap = ref({
  titleTemplate: null,
  contentTemplate: null,
})

const SYSTEM_BUILT_IN_VARIABLES = [
  { key: 'Title', label: '标题' },
  { key: 'CreatorUserName', label: '创建人' },
  { key: 'SendTime', label: '发送时间' },
  { key: 'userName', label: '接收人' },
  { key: 'content', label: '通知内容' },
  { key: 'taskName', label: '任务名称' },
  { key: 'taskTitle', label: '任务标题' },
  { key: 'deadline', label: '截止时间' },
  { key: 'flowName', label: '流程名称' },
  { key: 'processName', label: '流程名称' },
  { key: 'approver', label: '审批人' },
  { key: 'approveTime', label: '审批时间' },
  { key: 'code', label: '验证码' },
  { key: 'expireMinutes', label: '有效分钟' },
  { key: 'dueDate', label: '到期时间' },
  { key: 'overdueMinutes', label: '逾期分钟' },
  { key: 'jumpUrl', label: '跳转地址' },
]

const TEMPLATE_VARIABLE_CATALOG = {
  SYSTEM_NOTICE: [
    { key: 'userName', label: '接收人' },
    { key: 'content', label: '通知内容' },
  ],
  TASK_ASSIGN: [
    { key: 'userName', label: '接收人' },
    { key: 'taskName', label: '任务名称' },
    { key: 'deadline', label: '截止时间' },
  ],
  SMS_VERIFY_CODE: [
    { key: 'code', label: '验证码' },
    { key: 'expireMinutes', label: '有效分钟' },
  ],
  APPROVAL_PASS: [
    { key: 'userName', label: '接收人' },
    { key: 'flowName', label: '流程名称' },
    { key: 'approver', label: '审批人' },
    { key: 'approveTime', label: '审批时间' },
  ],
  FLOW_TASK_OVERDUE: [
    { key: 'taskId', label: '任务ID' },
    { key: 'taskName', label: '任务名称' },
    { key: 'taskTitle', label: '任务标题' },
    { key: 'processName', label: '流程名称' },
    { key: 'processInstanceId', label: '流程实例' },
    { key: 'startUserName', label: '发起人' },
    { key: 'dueDate', label: '截止时间' },
    { key: 'overdueMinutes', label: '逾期分钟' },
    { key: 'jumpUrl', label: '跳转地址' },
  ],
}

const TEMPLATE_SAMPLE_VALUES = {
  userName: '张三',
  content: '消息内容',
  Title: '系统通知',
  CreatorUserName: '管理员',
  SendTime: '2026-07-10 09:30',
  taskName: '合同审批',
  taskTitle: '合同审批',
  taskId: 'task_1024',
  deadline: '2026-07-10 18:00',
  flowName: '采购审批',
  processName: '采购审批',
  processInstanceId: 'proc_20260710',
  startUserName: '王五',
  approver: '李四',
  approveTime: '2026-07-10 10:20',
  code: '839201',
  expireMinutes: '5',
  dueDate: '2026-07-10 18:00:00',
  overdueMinutes: '35',
  jumpUrl: '/flow/todo?taskId=task_1024',
}

// 搜索表单配置
const searchSchema = computed(() => [
  {
    field: 'type',
    label: '消息类型',
    type: 'select',
    props: {
      placeholder: '请选择消息类型',
      options: messageTypeOptions.value,
    },
  },
  {
    field: 'keyword',
    label: '关键词',
    type: 'input',
    props: {
      placeholder: '请输入模板编码或名称',
    },
  },
])

// 表格列配置
const tableColumns = computed(() => [
  {
    prop: 'templateCode',
    label: '模板编码',
    width: 150,
  },
  {
    prop: 'templateName',
    label: '模板名称',
    width: 150,
  },
  {
    prop: 'type',
    label: '消息类型',
    width: 100,
    render: (row) => {
      return h(DictTag, { dictType: MESSAGE_TYPE_DICT, value: row.type, size: 'small' })
    },
  },
  {
    prop: 'titleTemplate',
    label: '标题模板',
    width: 200,
  },
  {
    prop: 'defaultChannel',
    label: '默认渠道',
    width: 100,
    render: (row) => {
      return h(DictTag, { dictType: MESSAGE_CHANNEL_DICT, value: row.defaultChannel, size: 'small' })
    },
  },
  {
    prop: 'enabled',
    label: '状态',
    width: 80,
    render: (row) => {
      return h(DictTag, { dictType: ENABLE_DISABLE_DICT, value: row.enabled, size: 'small' })
    },
  },
  {
    prop: 'createTime',
    label: '创建时间',
    width: 180,
  },
  {
    prop: 'action',
    label: '操作',
    width: 120,
    fixed: 'right',
    actions: [
      { label: '编辑', key: 'edit', onClick: handleEdit },
      { label: '删除', key: 'delete', type: 'error', onClick: handleDelete },
    ],
  },
])

// 编辑表单配置
const editSchema = computed(() => [
  {
    type: 'divider',
    label: '基础信息',
    props: {
      titlePlacement: 'left',
    },
    span: 2,
  },
  {
    field: 'templateCode',
    label: '模板编码',
    type: 'input',
    rules: [{ required: true, message: '请输入模板编码', trigger: 'blur' }],
    props: {
      placeholder: '请输入模板编码，全局唯一',
    },
    editDisabled: true,
  },
  {
    field: 'templateName',
    label: '模板名称',
    type: 'input',
    rules: [{ required: true, message: '请输入模板名称', trigger: 'blur' }],
    props: {
      placeholder: '请输入模板名称',
    },
  },
  {
    field: 'type',
    label: '消息类型',
    type: 'select',
    defaultValue: 'SYSTEM',
    rules: [{ required: true, message: '请选择消息类型', trigger: 'change' }],
    props: {
      options: messageTypeOptions.value,
    },
  },
  {
    field: 'defaultChannel',
    label: '默认渠道',
    type: 'select',
    defaultValue: 'WEB',
    props: {
      options: channelOptions.value,
    },
  },
  {
    type: 'divider',
    label: '模板内容',
    props: {
      titlePlacement: 'left',
    },
    span: 2,
  },
  {
    field: 'templateDesigner',
    label: '',
    type: 'slot',
    span: 2,
    showLabel: false,
    showFeedback: false,
  },
  {
    field: 'enabled',
    label: '是否启用',
    type: 'radio',
    defaultValue: 1,
    props: {
      options: enabledOptions.value,
    },
  },
  {
    field: 'remark',
    label: '备注说明',
    type: 'textarea',
    span: 2,
    props: {
      placeholder: '请输入备注说明',
      rows: 3,
    },
  },
])

function toNumberOptions(options = []) {
  return options.map(item => ({
    ...item,
    value: Number(item.value),
  }))
}

function handleBeforeSubmit(data = {}) {
  const submitData = {
    ...data,
    titleTemplate: String(data.titleTemplate || '').trim(),
    contentTemplate: String(data.contentTemplate || '').trim(),
  }
  delete submitData.templateDesigner

  if (!submitData.contentTemplate) {
    window.$message?.warning('请输入内容模板')
    return false
  }
  if (hasUnsupportedAtVariables(submitData.titleTemplate, submitData.contentTemplate)) {
    window.$message?.warning(['请使用 ', '$', '{变量}', ' 或 {变量}，@变量 后端不会替换'].join(''))
    return false
  }
  return submitData
}

function resolveTemplateVariables(formData = {}) {
  const usedKeys = new Set(extractTemplateVariables(formData.titleTemplate, formData.contentTemplate))
  const catalogVariables = getTemplateCatalogVariables(formData.templateCode)
  const variableMap = new Map()

  SYSTEM_BUILT_IN_VARIABLES.forEach((item) => {
    variableMap.set(item.key, {
      ...item,
      used: usedKeys.has(item.key),
      source: 'system',
    })
  })

  catalogVariables.forEach((item) => {
    variableMap.set(item.key, {
      ...item,
      used: usedKeys.has(item.key),
      source: 'backend',
    })
  })

  usedKeys.forEach((key) => {
    if (!variableMap.has(key)) {
      variableMap.set(key, {
        key,
        label: key,
        used: true,
        source: 'current',
      })
    }
  })

  return Array.from(variableMap.values())
}

function getVisibleTemplateVariables(formData = {}) {
  const keyword = String(variableKeyword.value || '').trim().toLowerCase()
  const variables = resolveTemplateVariables(formData)
  if (!keyword)
    return variables

  return variables.filter((item) => {
    return item.key.toLowerCase().includes(keyword) || item.label.toLowerCase().includes(keyword)
  })
}

function getVariablePanelSummary(formData = {}) {
  const variables = resolveTemplateVariables(formData)
  const customCount = variables.filter(item => item.source === 'current').length
  return customCount > 0
    ? `系统 ${SYSTEM_BUILT_IN_VARIABLES.length} / 自定义 ${customCount}`
    : `系统变量 ${SYSTEM_BUILT_IN_VARIABLES.length} 个`
}

function getTemplateCatalogVariables(templateCode) {
  const code = String(templateCode || '').trim()
  return TEMPLATE_VARIABLE_CATALOG[code] || []
}

function extractTemplateVariables(...contents) {
  const result = []
  const pattern = /\$\{([a-z_]\w*)\}|\{([a-z_]\w*)\}/gi
  contents.forEach((content) => {
    String(content || '').replace(pattern, (_match, dollarKey, braceKey) => {
      const key = dollarKey || braceKey
      if (key && !result.includes(key))
        result.push(key)
      return _match
    })
  })
  return result
}

function countTemplateVariables(content) {
  return extractTemplateVariables(content).length
}

function normalizeVariableKey(value) {
  return String(value || '').trim().replace(/^\$\{|\}$/g, '').replace(/^@/, '')
}

function canInsertCustomVariable(formData = {}) {
  const key = normalizeVariableKey(variableKeyword.value)
  if (!/^[a-z_]\w*$/i.test(key))
    return false
  return !resolveTemplateVariables(formData).some(item => item.key === key)
}

function formatPlaceholder(key) {
  return key ? `\${${key}}` : ''
}

function setActiveTemplateField(field) {
  activeTemplateField.value = field
}

function setTemplateField(formData, field, value) {
  setActiveTemplateField(field)
  formData[field] = value
}

function rememberTemplateCursor(field, event) {
  setActiveTemplateField(field)
  const target = event?.target
  if (!target || typeof target.selectionStart !== 'number')
    return
  templateCursorMap.value = {
    ...templateCursorMap.value,
    [field]: target.selectionStart,
  }
}

function insertTemplateVariable(formData, key) {
  const field = activeTemplateField.value || 'contentTemplate'
  const placeholder = formatPlaceholder(key)
  const current = String(formData[field] || '')
  const cursor = templateCursorMap.value[field]
  const insertIndex = typeof cursor === 'number' ? cursor : current.length

  formData[field] = `${current.slice(0, insertIndex)}${placeholder}${current.slice(insertIndex)}`
  templateCursorMap.value = {
    ...templateCursorMap.value,
    [field]: insertIndex + placeholder.length,
  }
}

function addCustomVariable(formData) {
  insertTemplateVariable(formData, normalizeVariableKey(variableKeyword.value))
  variableKeyword.value = ''
}

function renderTemplatePreview(content) {
  return String(content || '').replace(/\$\{([a-z_]\w*)\}|\{([a-z_]\w*)\}/gi, (_match, dollarKey, braceKey) => {
    const key = dollarKey || braceKey
    return TEMPLATE_SAMPLE_VALUES[key] || `${key}示例`
  })
}

/**
 * 清理预览 HTML，移除可执行/危险标签与事件，避免 v-html 引入 XSS。
 * 保留 div/span/class/style 等卡片常用标签，仅拦截 script/iframe 等与 on* 事件、javascript: 协议。
 */
function sanitizePreviewHtml(html) {
  return String(html || '')
    .replace(/<\s*(script|iframe|object|embed|link|meta|style)\b[^>]*>[\s\S]*?<\s*\/\s*\1\s*>/gi, '')
    .replace(/<\s*(script|iframe|object|embed|link|meta|style)\b[^>]*>/gi, '')
    .replace(/\son[a-z]+\s*=\s*("[^"]*"|'[^']*'|[^\s>]+)/gi, '')
    .replace(/(href|src)\s*=\s*("\s*javascript:[^"]*"|'\s*javascript:[^']*')/gi, '$1="#"')
}

function renderTemplatePreviewHtml(content) {
  return sanitizePreviewHtml(renderTemplatePreview(content))
}

function hasUnsupportedAtVariables(...contents) {
  return contents.some(content => /@[a-z_]\w*/i.test(String(content || '')))
}

// 编辑
function handleEdit(row) {
  crudRef.value?.handleEdit(row)
}

// 删除
function handleDelete(row) {
  crudRef.value?.handleDelete(row)
}
</script>

<style scoped>
:deep(.n-form-item-label) {
  font-weight: 500;
}

:deep(.n-modal .n-card__content) {
  padding-top: 18px;
}

.template-designer {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  gap: 16px;
  width: 100%;
}

.template-variable-panel,
.template-editor-panel {
  min-width: 0;
}

.template-variable-panel {
  border: 1px solid var(--n-border-color);
  border-radius: 8px;
  background: #fafafa;
  padding: 12px;
}

.variable-panel-head {
  display: grid;
  gap: 10px;
}

.variable-panel-title {
  color: #1f2937;
  font-size: 14px;
  font-weight: 600;
  line-height: 20px;
}

.variable-panel-count {
  margin-top: 2px;
  color: #6b7280;
  font-size: 12px;
  line-height: 18px;
}

.template-target-switch {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 4px;
  margin: 12px 0;
  padding: 3px;
  border-radius: 6px;
  background: #eef1f5;
}

.template-target-switch button {
  height: 28px;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: #4b5563;
  cursor: pointer;
  font-size: 12px;
}

.template-target-switch button.active {
  background: #fff;
  color: #1769e0;
  font-weight: 600;
  box-shadow: 0 1px 2px rgb(15 23 42 / 8%);
}

.variable-scrollbar {
  max-height: 318px;
}

.variable-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  width: 100%;
  min-height: 34px;
  margin-bottom: 6px;
  padding: 7px 8px;
  border: 1px solid transparent;
  border-radius: 6px;
  background: #fff;
  color: #374151;
  cursor: pointer;
  text-align: left;
  transition:
    border-color 0.18s ease,
    background-color 0.18s ease;
}

.variable-row:hover,
.variable-row.used {
  border-color: #9cc5ff;
  background: #f4f8ff;
}

.variable-row.custom {
  border-style: dashed;
}

.variable-name {
  overflow: hidden;
  font-size: 13px;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.variable-code {
  color: #6b7280;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', monospace;
  font-size: 12px;
  line-height: 18px;
}

.variable-empty {
  padding: 28px 0;
  color: #9ca3af;
  font-size: 13px;
  text-align: center;
}

.template-editor-panel {
  display: grid;
  gap: 12px;
}

.template-field-block {
  min-width: 0;
}

.template-field-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
  color: #1f2937;
  font-size: 13px;
  font-weight: 600;
}

.template-field-head span:last-child {
  color: #8a93a3;
  font-size: 12px;
  font-weight: 400;
}

.template-preview {
  border: 1px solid var(--n-border-color);
  border-radius: 8px;
  background: #fff;
  padding: 12px;
}

.template-preview-head {
  margin-bottom: 8px;
  color: #1f2937;
  font-size: 13px;
  font-weight: 600;
}

.template-preview-title {
  overflow: hidden;
  margin-bottom: 8px;
  color: #111827;
  font-size: 14px;
  font-weight: 600;
  line-height: 20px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.template-preview-content {
  min-height: 44px;
  color: #4b5563;
  font-size: 13px;
  line-height: 20px;
  white-space: pre-wrap;
  word-break: break-word;
}

/* 企业卡片（textcard）常用样式类，v-html 注入内容为非 scoped，需用 :deep 穿透 */
.template-preview-content :deep(div) {
  margin: 2px 0;
}

.template-preview-content :deep(.gray) {
  color: #9ca3af;
}

.template-preview-content :deep(.normal) {
  color: #4b5563;
}

.template-preview-content :deep(.highlight) {
  color: #2563eb;
}

.template-preview-content :deep(a) {
  color: #2563eb;
  text-decoration: none;
}

@media (max-width: 900px) {
  .template-designer {
    grid-template-columns: 1fr;
  }

  .variable-scrollbar {
    max-height: 220px;
  }
}
</style>
