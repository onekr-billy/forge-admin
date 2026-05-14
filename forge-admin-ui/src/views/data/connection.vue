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

    <n-modal
      v-model:show="tableModalVisible"
      preset="card"
      :title="tableModalTitle"
      style="width: 980px"
      :segmented="{ content: 'soft' }"
    >
      <n-space class="mb-3" justify="space-between">
        <n-input
          v-model:value="tableKeyword"
          clearable
          placeholder="按表名或注释搜索"
          style="width: 260px"
          @keyup.enter="loadConnectionTables"
        />
        <n-button type="primary" :loading="tableLoading" @click="loadConnectionTables">
          查询
        </n-button>
      </n-space>
      <n-data-table
        :columns="connectionTableColumns"
        :data="connectionTables"
        :loading="tableLoading"
        :pagination="{ pageSize: 10 }"
        size="small"
        striped
      />
    </n-modal>

    <n-modal
      v-model:show="fieldModalVisible"
      preset="card"
      :title="fieldModalTitle"
      style="width: 900px"
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
  </div>
</template>

<script setup>
import { NTag } from 'naive-ui'
import { computed, h, ref } from 'vue'
import { getDataConnectionFields, getDataConnectionTables } from '@/api/data/connection'
import { AiCrudPage } from '@/components/ai-form'
import { request } from '@/utils'

defineOptions({ name: 'DataConnection' })

const crudRef = ref(null)
const tableModalVisible = ref(false)
const tableModalTitle = ref('数据表')
const tableLoading = ref(false)
const tableKeyword = ref('')
const connectionTables = ref([])
const currentConnection = ref(null)
const fieldModalVisible = ref(false)
const fieldModalTitle = ref('字段列表')
const fieldLoading = ref(false)
const fieldRows = ref([])

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
    render: row => h(NTag, {
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

const connectionTableColumns = [
  { title: '表名', key: 'tableName', width: 220 },
  { title: '表类型', key: 'tableType', width: 120 },
  { title: '表注释', key: 'tableComment' },
  {
    title: '操作',
    key: 'actions',
    width: 100,
    render: row => h('a', {
      class: 'text-primary cursor-pointer hover:text-primary-hover',
      onClick: () => handleViewFields(row),
    }, '查看字段'),
  },
]

const fieldColumns = [
  { title: '字段名', key: 'columnName', width: 180 },
  { title: '字段类型', key: 'columnType', width: 160 },
  { title: '字段注释', key: 'columnComment' },
  {
    title: '可空',
    key: 'nullable',
    width: 80,
    render: row => row.nullable ? '是' : '否',
  },
  {
    title: '主键',
    key: 'primaryKey',
    width: 80,
    render: row => row.primaryKey ? '是' : '否',
  },
]

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
      }
      catch {
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
    }
    else {
      window.$message.error('连接失败', { key: 'testConn' })
    }
  }
  catch {
    window.$message.error('连接测试失败', { key: 'testConn' })
  }
}

async function handleViewTables(row) {
  currentConnection.value = row
  tableModalTitle.value = `数据表 - ${row.connectionName}`
  tableKeyword.value = ''
  tableModalVisible.value = true
  await loadConnectionTables()
}

async function loadConnectionTables() {
  if (!currentConnection.value?.id) {
    return
  }

  tableLoading.value = true
  try {
    const res = await getDataConnectionTables(currentConnection.value.id, tableKeyword.value || undefined)
    if (res.code === 200) {
      connectionTables.value = res.data || []
    }
    else {
      window.$message.error(res.msg || '加载数据表失败')
    }
  }
  catch {
    window.$message.error('加载数据表失败')
  }
  finally {
    tableLoading.value = false
  }
}

async function handleViewFields(row) {
  if (!currentConnection.value?.id) {
    return
  }

  fieldModalTitle.value = `字段列表 - ${row.tableName}`
  fieldModalVisible.value = true
  fieldLoading.value = true
  fieldRows.value = []

  try {
    const res = await getDataConnectionFields(currentConnection.value.id, row.tableName)
    if (res.code === 200) {
      fieldRows.value = res.data || []
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
</script>

<style scoped>
.data-connection-page {
  height: 100%;
}
</style>
