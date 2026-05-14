<template>
  <div class="data-dataset-page">
    <AiCrudPage
      ref="crudRef"
      :api-config="{
        list: 'get@/data/dataset/page',
        detail: 'get@/data/dataset/:id',
        add: 'post@/data/dataset',
        update: 'put@/data/dataset',
        delete: 'delete@/data/dataset/:id',
      }"
      :search-schema="searchSchema"
      :columns="tableColumns"
      :edit-schema="editSchema"
      :before-render-form="beforeRenderForm"
      :before-submit="beforeSubmit"
      row-key="id"
      :edit-grid-cols="12"
      edit-label-placement="top"
      edit-form-class="data-dataset-edit-form"
      modal-type="modal"
      modal-width="min(1320px, calc(100vw - 32px))"
      add-button-text="新增数据集"
    >
      <template #form-sqlText="{ value, updateValue }">
        <SqlEditor
          :value="value"
          placeholder="SELECT id, name FROM table_name WHERE status = 1"
          @update:value="updateValue"
        />
      </template>

      <template #form-sqlPreviewAction="{ formData }">
        <div class="sql-preview-action">
          <n-button
            type="primary"
            secondary
            :loading="sqlPreviewLoading"
            @click="handlePreviewSql(formData)"
          >
            预览SQL
          </n-button>
          <n-text depth="3">
            仅执行并展示前10条数据，用于校验SQL语句
          </n-text>
        </div>
      </template>

      <template #form-paramSchemaJson="{ value, updateValue, formData }">
        <DatasetParamSchemaEditor
          :model-value="value || []"
          :dataset-type="formData.datasetType"
          :connection-id="formData.connectionId"
          :table-name="formData.tableName"
          :sql-text="formData.sqlText"
          @update:model-value="updateValue"
        />
      </template>
    </AiCrudPage>

    <n-modal
      v-model:show="fieldModalVisible"
      preset="card"
      :title="fieldModalTitle"
      style="width: 960px"
      :segmented="{ content: 'soft' }"
    >
      <n-data-table
        :columns="fieldColumns"
        :data="fieldRows"
        :loading="fieldLoading"
        :pagination="{ pageSize: 10 }"
        size="small"
        striped
      />
    </n-modal>

    <n-modal
      v-model:show="sqlPreviewVisible"
      preset="card"
      title="SQL预览结果"
      style="width: 1000px"
      :segmented="{ content: 'soft' }"
    >
      <n-data-table
        :columns="sqlPreviewColumns"
        :data="sqlPreviewRows"
        :loading="sqlPreviewLoading"
        :pagination="{ pageSize: 10 }"
        :scroll-x="sqlPreviewScrollX"
        size="small"
        striped
      />
    </n-modal>
  </div>
</template>

<script setup>
import { NTag } from 'naive-ui'
import { computed, h, ref } from 'vue'
import { getDataConnectionList, getDataConnectionTables } from '@/api/data/connection'
import { AiCrudPage } from '@/components/ai-form'
import DatasetParamSchemaEditor from '@/components/data/DatasetParamSchemaEditor.vue'
import SqlEditor from '@/components/SqlEditor.vue'
import { request } from '@/utils'

defineOptions({ name: 'DataDataset' })

const crudRef = ref(null)
const connectionOptions = ref([])
const tableOptions = ref([])
const tableLoading = ref(false)
const loadedTableConnectionId = ref(null)
const loadingTableConnectionId = ref(null)
const fieldModalVisible = ref(false)
const fieldLoading = ref(false)
const fieldModalTitle = ref('字段列表')
const fieldRows = ref([])
const sqlPreviewVisible = ref(false)
const sqlPreviewLoading = ref(false)
const sqlPreviewColumns = ref([])
const sqlPreviewRows = ref([])
const sqlPreviewScrollX = ref(0)

const datasetTypeOptions = [
  { label: '单表数据集', value: 'TABLE' },
  { label: 'SQL数据集', value: 'SQL' },
]

const statusOptions = [
  { label: '启用', value: 1 },
  { label: '禁用', value: 0 },
]

const supportedParamOperators = ['=', '!=', '>', '>=', '<', '<=', 'LIKE']

loadConnectionOptions()

async function loadConnectionOptions() {
  try {
    const res = await getDataConnectionList()
    if (res.code === 200 && res.data) {
      connectionOptions.value = res.data.map(c => ({
        label: c.connectionName,
        value: c.id,
      }))
    }
  }
  catch (e) {
    console.error('Failed to load connections', e)
  }
}

const searchSchema = computed(() => [
  {
    field: 'datasetName',
    label: '数据集名称',
    type: 'input',
    props: { placeholder: '请输入数据集名称' },
  },
  {
    field: 'connectionId',
    label: '数据连接',
    type: 'select',
    props: { placeholder: '请选择数据连接', options: connectionOptions.value, clearable: true },
  },
  {
    field: 'datasetType',
    label: '数据集类型',
    type: 'select',
    props: { placeholder: '请选择数据集类型', options: datasetTypeOptions, clearable: true },
  },
  {
    field: 'status',
    label: '状态',
    type: 'select',
    props: { placeholder: '请选择状态', options: statusOptions, clearable: true },
  },
])

const tableColumns = computed(() => [
  { prop: 'datasetName', label: '数据集名称', width: 150 },
  { prop: 'datasetCode', label: '数据集编码', width: 120 },
  {
    prop: 'connectionId',
    label: '数据连接',
    width: 150,
    render: row => row.connectionName || getConnectionName(row.connectionId),
  },
  {
    prop: 'datasetType',
    label: '数据集类型',
    width: 100,
    render: row => h(NTag, {
      type: row.datasetType === 'TABLE' ? 'info' : 'warning',
      size: 'small',
    }, { default: () => row.datasetType === 'TABLE' ? '单表' : 'SQL' }),
  },
  { prop: 'tableName', label: '表名', width: 150 },
  {
    prop: 'status',
    label: '状态',
    width: 80,
    render: row => h(NTag, {
      type: row.status === 1 ? 'success' : 'error',
      size: 'small',
    }, { default: () => row.status === 1 ? '启用' : '禁用' }),
  },
  { prop: 'maxRows', label: '最大行数', width: 80 },
  { prop: 'createTime', label: '创建时间', width: 160 },
  {
    prop: 'action',
    label: '操作',
    width: 180,
    fixed: 'right',
    actions: [
      { label: '编辑', key: 'edit', type: 'primary', onClick: handleEdit },
      { label: '查看字段', key: 'fields', type: 'info', onClick: handleViewFields },
      { label: '同步字段', key: 'sync', type: 'info', onClick: handleSyncFields },
      { label: '删除', key: 'delete', type: 'error', onClick: handleDelete },
    ],
  },
])

const fieldColumns = [
  { title: '字段名', key: 'fieldName', width: 160 },
  { title: '显示名', key: 'fieldLabel', width: 160 },
  { title: '来源列', key: 'sourceColumn', width: 140 },
  { title: '数据库类型', key: 'dbType', width: 120 },
  { title: '标准类型', key: 'dataType', width: 100 },
  {
    title: '字段角色',
    key: 'fieldRole',
    width: 100,
    render: row => row.fieldRole === 'MEASURE' ? '指标' : '维度',
  },
  {
    title: '可筛选',
    key: 'queryEnabled',
    width: 80,
    render: row => row.queryEnabled === 1 ? '是' : '否',
  },
  {
    title: '可展示',
    key: 'displayEnabled',
    width: 80,
    render: row => row.displayEnabled === 1 ? '是' : '否',
  },
]

function getConnectionName(connectionId) {
  const connection = connectionOptions.value.find(item => item.value === connectionId)
  return connection?.label || connectionId || '-'
}

const editSchema = computed(() => [
  {
    field: '__sectionBasic',
    label: '基础信息',
    type: 'divider',
    span: 12,
    showFeedback: false,
    props: { class: 'dataset-form-divider' },
  },
  {
    field: 'datasetCode',
    label: '数据集编码',
    type: 'input',
    span: 3,
    rules: [{ required: true, message: '请输入数据集编码', trigger: 'blur' }],
    props: { placeholder: '请输入数据集编码' },
  },
  {
    field: 'datasetName',
    label: '数据集名称',
    type: 'input',
    span: 5,
    rules: [{ required: true, message: '请输入数据集名称', trigger: 'blur' }],
    props: { placeholder: '请输入数据集名称' },
  },
  {
    field: 'status',
    label: '状态',
    type: 'radio',
    span: 4,
    defaultValue: 1,
    props: { options: statusOptions },
  },
  {
    field: '__sectionSource',
    label: '数据来源',
    type: 'divider',
    span: 12,
    showFeedback: false,
    props: { class: 'dataset-form-divider' },
  },
  {
    field: 'connectionId',
    label: '数据连接',
    type: 'select',
    span: 4,
    props: { placeholder: '请选择数据连接', options: connectionOptions.value },
    onChange: ({ value, formData }) => handleConnectionChange(value, formData),
  },
  {
    field: 'datasetType',
    label: '数据集类型',
    type: 'radio',
    span: 3,
    defaultValue: 'TABLE',
    rules: [{ required: true }],
    props: { options: datasetTypeOptions },
    onChange: ({ value, formData }) => handleDatasetTypeChange(value, formData),
  },
  {
    field: 'tableName',
    label: '数据表',
    type: 'select',
    span: 5,
    props: {
      placeholder: '请先选择数据连接，再选择数据表',
      options: tableOptions.value,
      loading: tableLoading.value,
      filterable: true,
      clearable: true,
    },
    vIf: formData => formData.datasetType === 'TABLE',
  },
  {
    field: 'sqlText',
    label: '查询SQL',
    type: 'slot',
    slotName: 'sqlText',
    span: 12,
    rules: [{ required: true, message: '请输入查询SQL', trigger: 'blur' }],
    vIf: formData => formData.datasetType === 'SQL',
  },
  {
    field: 'sqlPreviewAction',
    label: '',
    type: 'slot',
    slotName: 'sqlPreviewAction',
    span: 12,
    vIf: formData => formData.datasetType === 'SQL',
    showFeedback: false,
  },
  {
    field: '__sectionParam',
    label: '查询条件定义',
    type: 'divider',
    span: 12,
    showFeedback: false,
    props: { class: 'dataset-form-divider' },
  },
  {
    field: 'paramSchemaJson',
    label: '查询条件',
    type: 'slot',
    slotName: 'paramSchemaJson',
    span: 12,
    showFeedback: false,
  },
  {
    field: '__sectionSetting',
    label: '执行设置',
    type: 'divider',
    span: 12,
    showFeedback: false,
    props: { class: 'dataset-form-divider' },
  },
  {
    field: 'maxRows',
    label: '最大返回行数',
    type: 'number',
    span: 3,
    defaultValue: 1000,
    props: { placeholder: '请输入最大返回行数', min: 1, max: 10000 },
  },
  {
    field: 'timeoutSeconds',
    label: '查询超时(秒)',
    type: 'number',
    span: 3,
    defaultValue: 15,
    props: { placeholder: '请输入超时时间', min: 1, max: 300 },
  },
  {
    field: 'description',
    label: '描述',
    type: 'textarea',
    span: 6,
    props: { placeholder: '请输入描述', rows: 3 },
  },
])

async function beforeRenderForm(formData) {
  const nextFormData = formData ? { ...formData } : {}
  nextFormData.paramSchemaJson = parseParamSchemaFormValue(nextFormData.paramSchemaJson)
  const connectionId = nextFormData.connectionId
  const datasetType = nextFormData.datasetType || 'TABLE'
  if (connectionId && datasetType === 'TABLE') {
    await loadTableOptions(connectionId)
  }
  else {
    resetTableOptions()
  }
  return nextFormData
}

async function handleConnectionChange(connectionId, formData) {
  formData.tableName = null
  if (formData.datasetType === 'TABLE') {
    await loadTableOptions(connectionId)
  }
}

async function handleDatasetTypeChange(datasetType, formData) {
  if (datasetType === 'TABLE') {
    formData.sqlText = null
    await loadTableOptions(formData.connectionId)
    return
  }

  formData.tableName = null
}

function resetTableOptions() {
  tableOptions.value = []
  loadedTableConnectionId.value = null
  loadingTableConnectionId.value = null
}

async function loadTableOptions(connectionId) {
  if (!connectionId) {
    resetTableOptions()
    return
  }
  if (loadedTableConnectionId.value === connectionId && tableOptions.value.length > 0) {
    return
  }
  if (tableLoading.value && loadingTableConnectionId.value === connectionId) {
    return
  }

  tableLoading.value = true
  loadingTableConnectionId.value = connectionId
  try {
    const res = await getDataConnectionTables(connectionId)
    if (res.code === 200 && Array.isArray(res.data)) {
      tableOptions.value = res.data.map(table => ({
        label: table.tableComment ? `${table.tableName}（${table.tableComment}）` : table.tableName,
        value: table.tableName,
      }))
      loadedTableConnectionId.value = connectionId
    }
    else {
      resetTableOptions()
    }
  }
  catch {
    resetTableOptions()
    window.$message?.error('加载数据表失败')
  }
  finally {
    tableLoading.value = false
    loadingTableConnectionId.value = null
  }
}

function beforeSubmit(formData) {
  delete formData.__sectionBasic
  delete formData.__sectionSource
  delete formData.__sectionParam
  delete formData.__sectionSetting
  delete formData.sqlPreviewAction

  if (!formData.connectionId) {
    window.$message?.error('请选择数据连接')
    return false
  }

  if (formData.datasetType === 'TABLE') {
    if (!formData.tableName) {
      window.$message?.error('请选择数据表')
      return false
    }
    formData.sqlText = null
  }
  else if (formData.datasetType === 'SQL') {
    if (!formData.sqlText) {
      window.$message?.error('请输入查询SQL')
      return false
    }
    formData.tableName = null
  }

  const normalizedSchema = normalizeParamSchema(formData.paramSchemaJson, formData.datasetType)
  if (normalizedSchema === null) {
    return false
  }

  formData.paramSchemaJson = normalizedSchema.length > 0
    ? JSON.stringify(normalizedSchema, null, 2)
    : null

  return formData
}

function parseParamSchemaFormValue(value) {
  if (!value) {
    return []
  }
  if (Array.isArray(value)) {
    return value
  }

  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) ? parsed : []
  }
  catch (error) {
    console.error('Failed to parse dataset param schema', error)
    window.$message?.error('查询参数定义格式异常，已按空配置处理')
    return []
  }
}

function normalizeParamSchema(rows, datasetType) {
  if (!rows) {
    return []
  }
  if (!Array.isArray(rows)) {
    window.$message?.error('查询条件配置格式不正确')
    return null
  }

  const normalizedRows = []
  const paramNames = new Set()

  for (const [index, row] of rows.entries()) {
    const paramName = typeof row?.paramName === 'string' ? row.paramName.trim() : ''
    const label = typeof row?.label === 'string' ? row.label.trim() : ''
    const dataType = typeof row?.dataType === 'string' && row.dataType
      ? row.dataType.trim().toUpperCase()
      : 'STRING'
    const operator = typeof row?.operator === 'string' && row.operator
      ? row.operator.trim().toUpperCase()
      : '='
    const fieldName = typeof row?.fieldName === 'string' ? row.fieldName.trim() : ''
    const defaultValue = row?.defaultValue === '' ? null : row?.defaultValue ?? null
    const required = row?.required === true
    const isEmptyRow = !paramName && !label && !fieldName && defaultValue === null && required === false

    if (isEmptyRow) {
      continue
    }
    if (!paramName) {
      window.$message?.error(`第${index + 1}行缺少条件参数名`)
      return null
    }
    if (paramNames.has(paramName)) {
      window.$message?.error(`条件参数名重复：${paramName}`)
      return null
    }
    if (!supportedParamOperators.includes(operator)) {
      window.$message?.error(`第${index + 1}行匹配方式不支持：${operator}`)
      return null
    }
    if (datasetType === 'TABLE' && !fieldName) {
      window.$message?.error(`第${index + 1}行还未选择数据表字段`)
      return null
    }
    if (datasetType === 'SQL' && fieldName) {
      window.$message?.error(`第${index + 1}行不需要配置数据表字段`)
      return null
    }

    paramNames.add(paramName)
    normalizedRows.push({
      paramName,
      label: label || null,
      dataType,
      required,
      defaultValue,
      operator,
      fieldName: fieldName || null,
    })
  }

  return normalizedRows
}

function handleEdit(row) {
  crudRef.value?.showEdit(row)
}

async function handlePreviewSql(formData) {
  if (!formData.connectionId) {
    window.$message?.error('请选择数据连接')
    return
  }
  if (!formData.sqlText) {
    window.$message?.error('请输入查询SQL')
    return
  }

  sqlPreviewVisible.value = true
  sqlPreviewLoading.value = true
  sqlPreviewColumns.value = []
  sqlPreviewRows.value = []
  sqlPreviewScrollX.value = 0

  try {
    const res = await request.post('/data/dataset/preview-sql', {
      connectionId: formData.connectionId,
      sqlText: formData.sqlText,
      maxRows: 10,
    })
    if (res.code === 200) {
      const columns = res.data?.columns || []
      sqlPreviewColumns.value = columns.map(column => ({
        title: column,
        key: column,
        width: 160,
        ellipsis: { tooltip: true },
        render: row => row[column] ?? '',
      }))
      sqlPreviewRows.value = res.data?.rows || []
      sqlPreviewScrollX.value = Math.max(columns.length * 160, 800)
      window.$message?.success(`SQL校验通过，预览${sqlPreviewRows.value.length}条数据`)
    }
    else {
      window.$message?.error(res.msg || 'SQL预览失败')
    }
  }
  catch (error) {
    window.$message?.error(error?.message || 'SQL预览失败')
  }
  finally {
    sqlPreviewLoading.value = false
  }
}

async function handleViewFields(row) {
  fieldModalTitle.value = `字段列表 - ${row.datasetName}`
  fieldModalVisible.value = true
  fieldLoading.value = true
  fieldRows.value = []

  try {
    const res = await request.get(`/data/dataset/${row.id}`)
    if (res.code === 200) {
      fieldRows.value = res.data?.fields || []
    }
    else {
      window.$message.error(res.msg || '加载字段失败')
    }
  }
  catch {
    window.$message.error('加载字段失败')
  }
  finally {
    fieldLoading.value = false
  }
}

function handleDelete(row) {
  window.$dialog.warning({
    title: '确认删除',
    content: `确定要删除数据集"${row.datasetName}"吗？`,
    positiveText: '确定',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        const res = await request.delete(`/data/dataset/${row.id}`)
        if (res.code === 200) {
          window.$message.success('删除成功')
          crudRef.value?.refresh()
        }
      }
      catch {
        window.$message.error('删除失败')
      }
    },
  })
}

async function handleSyncFields(row) {
  try {
    window.$message.loading('正在同步字段...', { duration: 0, key: 'syncFields' })
    const res = await request.post(`/data/dataset/${row.id}/sync-fields`)
    if (res.code === 200) {
      window.$message.success(`同步成功，共${res.data?.length || 0}个字段`, { key: 'syncFields' })
      fieldRows.value = res.data || []
    }
    else {
      window.$message.error(res.msg || '同步失败', { key: 'syncFields' })
    }
  }
  catch {
    window.$message.error('同步字段失败', { key: 'syncFields' })
  }
}
</script>

<style scoped>
.data-dataset-page {
  height: 100%;
}

.sql-preview-action {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 34px;
  padding: 2px 0 4px;
}

:global(.data-dataset-edit-form) {
  padding: 4px 2px 0;
}

:global(.data-dataset-edit-form .n-form-item) {
  margin-bottom: 8px;
  padding: 12px;
  border: 1px solid #e8edf5;
  border-radius: 8px;
  background: #fff;
  transition:
    border-color 0.18s ease,
    box-shadow 0.18s ease;
}

:global(.data-dataset-edit-form .n-form-item:hover) {
  border-color: #cbd8ea;
  box-shadow: 0 2px 8px rgb(15 23 42 / 4%);
}

:global(.data-dataset-edit-form .n-form-item-blank) {
  width: 100%;
}

:global(.data-dataset-edit-form .n-form-item-label) {
  min-height: 20px;
  margin-bottom: 7px;
  color: #475569;
  font-size: 12px;
  font-weight: 500;
  line-height: 1.3;
}

:global(.data-dataset-edit-form .dataset-form-divider) {
  margin: 12px 0 8px;
  color: #64748b;
}

:global(.data-dataset-edit-form .dataset-form-divider::before),
:global(.data-dataset-edit-form .dataset-form-divider::after) {
  border-top-color: #dbe3ef;
}

:global(.data-dataset-edit-form .dataset-form-divider .n-divider__title) {
  color: #1e293b;
  font-size: 14px;
  font-weight: 600;
}

:global(.data-dataset-edit-form .n-input),
:global(.data-dataset-edit-form .n-input-number),
:global(.data-dataset-edit-form .n-select) {
  width: 100%;
}

:global(.data-dataset-edit-form .sql-editor) {
  width: 100%;
}

:global(.data-dataset-edit-form textarea) {
  font-family: 'JetBrains Mono', 'SFMono-Regular', Consolas, monospace;
  line-height: 1.55;
}

:global(.data-dataset-edit-form .n-radio-group .n-space) {
  gap: 8px 18px !important;
}
</style>
