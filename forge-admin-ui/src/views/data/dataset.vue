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
      :before-submit="beforeSubmit"
      row-key="id"
      :edit-grid-cols="2"
      modal-width="900px"
      add-button-text="新增数据集"
    />
  </div>
</template>

<script setup>
import { NTag } from 'naive-ui'
import { computed, h, ref } from 'vue'
import { AiCrudPage } from '@/components/ai-form'
import { request } from '@/utils'
import { getDataConnectionList } from '@/api/data/connection'

defineOptions({ name: 'DataDataset' })

const crudRef = ref(null)
const connectionOptions = ref([])

const datasetTypeOptions = [
  { label: '单表数据集', value: 'TABLE' },
  { label: 'SQL数据集', value: 'SQL' },
]

const statusOptions = [
  { label: '启用', value: 1 },
  { label: '禁用', value: 0 },
]

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
  } catch (e) {
    console.error('Failed to load connections', e)
  }
}

const searchSchema = [
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
    props: { placeholder: '请选择数据连接', options: connectionOptions, clearable: true },
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
]

const tableColumns = computed(() => [
  { prop: 'datasetName', label: '数据集名称', width: 150 },
  { prop: 'datasetCode', label: '数据集编码', width: 120 },
  { prop: 'connectionId', label: '数据连接', width: 120 },
  {
    prop: 'datasetType',
    label: '数据集类型',
    width: 100,
    render: (row) => h(NTag, {
      type: row.datasetType === 'TABLE' ? 'info' : 'warning',
      size: 'small',
    }, { default: () => row.datasetType === 'TABLE' ? '单表' : 'SQL' }),
  },
  { prop: 'tableName', label: '表名', width: 150 },
  {
    prop: 'status',
    label: '状态',
    width: 80,
    render: (row) => h(NTag, {
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
      { label: '同步字段', key: 'sync', type: 'info', onClick: handleSyncFields },
      { label: '删除', key: 'delete', type: 'error', onClick: handleDelete },
    ],
  },
])

const editSchema = computed(() => [
  {
    field: 'datasetCode',
    label: '数据集编码',
    type: 'input',
    rules: [{ required: true, message: '请输入数据集编码', trigger: 'blur' }],
    props: { placeholder: '请输入数据集编码' },
  },
  {
    field: 'datasetName',
    label: '数据集名称',
    type: 'input',
    rules: [{ required: true, message: '请输入数据集名称', trigger: 'blur' }],
    props: { placeholder: '请输入数据集名称' },
  },
  {
    field: 'connectionId',
    label: '数据连接',
    type: 'select',
    rules: [{ required: true, message: '请选择数据连接', trigger: 'change' }],
    props: { placeholder: '请选择数据连接', options: connectionOptions.value },
  },
  {
    field: 'datasetType',
    label: '数据集类型',
    type: 'radio',
    defaultValue: 'TABLE',
    rules: [{ required: true }],
    props: { options: datasetTypeOptions },
  },
  {
    field: 'tableName',
    label: '表名',
    type: 'input',
    rules: [{ required: true, message: '请输入表名', trigger: 'blur' }],
    props: { placeholder: '请输入表名', disabled: false },
    visible: (formData) => formData.datasetType === 'TABLE',
  },
  {
    field: 'sqlText',
    label: '查询SQL',
    type: 'textarea',
    span: 2,
    rules: [{ required: true, message: '请输入查询SQL', trigger: 'blur' }],
    props: { placeholder: 'SELECT ... FROM ...', rows: 6 },
    visible: (formData) => formData.datasetType === 'SQL',
  },
  {
    field: 'maxRows',
    label: '最大返回行数',
    type: 'number',
    defaultValue: 1000,
    props: { placeholder: '请输入最大返回行数', min: 1, max: 10000 },
  },
  {
    field: 'timeoutSeconds',
    label: '查询超时(秒)',
    type: 'number',
    defaultValue: 15,
    props: { placeholder: '请输入超时时间', min: 1, max: 300 },
  },
  {
    field: 'status',
    label: '状态',
    type: 'radio',
    defaultValue: 1,
    props: { options: statusOptions },
  },
  {
    field: 'description',
    label: '描述',
    type: 'textarea',
    span: 2,
    props: { placeholder: '请输入描述', rows: 3 },
  },
])

function beforeSubmit(formData) {
  if (formData.datasetType === 'TABLE') {
    formData.sqlText = null
  } else {
    formData.tableName = null
  }
  return formData
}

function handleEdit(row) {
  crudRef.value?.showEdit(row)
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
      } catch (error) {
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
    } else {
      window.$message.error(res.msg || '同步失败', { key: 'syncFields' })
    }
  } catch (error) {
    window.$message.error('同步字段失败', { key: 'syncFields' })
  }
}
</script>

<style scoped>
.data-dataset-page {
  height: 100%;
}
</style>