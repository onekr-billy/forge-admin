<template>
  <div class="data-connection-page">
    <AiCrudPage
      ref="crudRef"
      :api-config="{
        list: 'get@/data/connection/page',
        detail: 'get@/data/connection/:id',
        add: 'post@/data/connection',
        update: 'put@/data/connection',
        delete: 'delete@/data/connection/:id',
      }"
      :search-schema="searchSchema"
      :columns="tableColumns"
      :edit-schema="editSchema"
      :before-submit="beforeSubmit"
      row-key="id"
      :edit-grid-cols="2"
      modal-width="800px"
      add-button-text="新增数据连接"
    />
  </div>
</template>

<script setup>
import { NTag } from 'naive-ui'
import { computed, h, ref } from 'vue'
import { AiCrudPage } from '@/components/ai-form'
import { request } from '@/utils'

defineOptions({ name: 'DataConnection' })

const crudRef = ref(null)

const dbTypeOptions = [
  { label: 'MySQL', value: 'MYSQL' },
  { label: 'Oracle', value: 'ORACLE' },
  { label: 'PostgreSQL', value: 'POSTGRESQL' },
  { label: 'SQLServer', value: 'SQLSERVER' },
]

const driverClassMap = {
  MYSQL: 'com.mysql.cj.jdbc.Driver',
  ORACLE: 'oracle.jdbc.OracleDriver',
  POSTGRESQL: 'org.postgresql.Driver',
  SQLSERVER: 'com.microsoft.sqlserver.jdbc.SQLServerDriver',
}

const statusOptions = [
  { label: '启用', value: 1 },
  { label: '禁用', value: 0 },
]

const searchSchema = [
  {
    field: 'connectionName',
    label: '连接名称',
    type: 'input',
    props: { placeholder: '请输入连接名称' },
  },
  {
    field: 'dbType',
    label: '数据库类型',
    type: 'select',
    props: { placeholder: '请选择数据库类型', options: dbTypeOptions },
  },
  {
    field: 'status',
    label: '状态',
    type: 'select',
    props: { placeholder: '请选择状态', options: statusOptions, clearable: true },
  },
]

const tableColumns = computed(() => [
  { prop: 'connectionName', label: '连接名称', width: 150 },
  { prop: 'connectionCode', label: '连接编码', width: 120 },
  { prop: 'dbType', label: '数据库类型', width: 100 },
  { prop: 'schemaName', label: '模式名', width: 100 },
  { prop: 'username', label: '用户名', width: 100 },
  {
    prop: 'status',
    label: '状态',
    width: 80,
    render: (row) => h(NTag, {
      type: row.status === 1 ? 'success' : 'error',
      size: 'small',
    }, { default: () => row.status === 1 ? '启用' : '禁用' }),
  },
  { prop: 'createTime', label: '创建时间', width: 160 },
  {
    prop: 'action',
    label: '操作',
    width: 200,
    fixed: 'right',
    actions: [
      { label: '编辑', key: 'edit', type: 'primary', onClick: handleEdit },
      { label: '测试连接', key: 'test', type: 'info', onClick: handleTest },
      { label: '查看表', key: 'tables', type: 'info', onClick: handleViewTables },
      { label: '删除', key: 'delete', type: 'error', onClick: handleDelete },
    ],
  },
])

const editSchema = [
  {
    field: 'connectionCode',
    label: '连接编码',
    type: 'input',
    rules: [{ required: true, message: '请输入连接编码', trigger: 'blur' }],
    props: { placeholder: '请输入连接编码' },
  },
  {
    field: 'connectionName',
    label: '连接名称',
    type: 'input',
    rules: [{ required: true, message: '请输入连接名称', trigger: 'blur' }],
    props: { placeholder: '请输入连接名称' },
  },
  {
    field: 'dbType',
    label: '数据库类型',
    type: 'select',
    defaultValue: 'MYSQL',
    rules: [{ required: true, message: '请选择数据库类型', trigger: 'change' }],
    props: {
      placeholder: '请选择数据库类型',
      options: dbTypeOptions,
      onUpdateValue: (value, formData) => {
        if (driverClassMap[value]) {
          formData.driverClassName = driverClassMap[value]
        }
      },
    },
  },
  {
    field: 'driverClassName',
    label: '驱动类名',
    type: 'input',
    defaultValue: 'com.mysql.cj.jdbc.Driver',
    rules: [{ required: true, message: '请输入驱动类名', trigger: 'blur' }],
    props: { placeholder: '请输入驱动类名' },
  },
  {
    field: 'jdbcUrl',
    label: 'JDBC连接地址',
    type: 'input',
    span: 2,
    rules: [{ required: true, message: '请输入JDBC连接地址', trigger: 'blur' }],
    props: { placeholder: 'jdbc:mysql://localhost:3306/database' },
  },
  {
    field: 'username',
    label: '用户名',
    type: 'input',
    rules: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
    props: { placeholder: '请输入用户名' },
  },
  {
    field: 'password',
    label: '密码',
    type: 'input',
    rules: [{ required: true, message: '请输入密码', trigger: 'blur' }],
    props: { type: 'password', placeholder: '编辑时留空则不修改', showPasswordOn: 'click' },
  },
  {
    field: 'schemaName',
    label: '模式名',
    type: 'input',
    props: { placeholder: '数据库名/模式名' },
  },
  {
    field: 'testSql',
    label: '测试SQL',
    type: 'input',
    defaultValue: 'SELECT 1',
    props: { placeholder: '请输入测试SQL' },
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
]

function beforeSubmit(formData) {
  if (!formData.password) {
    delete formData.password
  }
  return formData
}

function handleEdit(row) {
  const editData = { ...row, password: '' }
  crudRef.value?.showEdit(editData)
}

function handleDelete(row) {
  window.$dialog.warning({
    title: '确认删除',
    content: `确定要删除数据连接"${row.connectionName}"吗？`,
    positiveText: '确定',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        const res = await request.delete(`/data/connection/${row.id}`)
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

async function handleTest(row) {
  try {
    window.$message.loading('正在测试连接...', { duration: 0, key: 'testConn' })
    const res = await request.post(`/data/connection/${row.id}/test`)
    if (res.code === 200 && res.data) {
      window.$message.success('连接成功', { key: 'testConn' })
    } else {
      window.$message.error('连接失败', { key: 'testConn' })
    }
  } catch (error) {
    window.$message.error('连接测试失败', { key: 'testConn' })
  }
}

function handleViewTables(row) {
  window.$message.info('请创建数据集时选择数据连接查看表')
}
</script>

<style scoped>
.data-connection-page {
  height: 100%;
}
</style>