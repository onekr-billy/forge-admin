<template>
  <div class="external-api-page">
    <AiCrudPage
      ref="crudRef"
      api="/external/api"
      :api-config="{
        list: 'get@/external/api/page',
        detail: 'get@/external/api/:id',
        add: 'post@/external/api',
        update: 'put@/external/api',
        delete: 'delete@/external/api/:id',
      }"
      :load-detail-on-edit="true"
      :search-schema="searchSchema"
      :columns="tableColumns"
      :edit-schema="editSchema"
      row-key="id"
      add-button-text="新增外部接口"
    />
  </div>
</template>

<script setup lang="ts">
import { NTag } from 'naive-ui'
import { computed, h, ref, onMounted } from 'vue'
import { AiCrudPage } from '@/components/ai-form'
import { getExternalSystemList, ExternalSystem } from '@/api/external/system'

defineOptions({ name: 'ExternalApi' })

const crudRef = ref(null)
const systemOptions = ref<{ label: string; value: number }[]>([])

const methodOptions = [
  { label: 'GET', value: 'GET' },
  { label: 'POST', value: 'POST' },
  { label: 'PUT', value: 'PUT' },
  { label: 'DELETE', value: 'DELETE' },
  { label: 'PATCH', value: 'PATCH' }
]

const adapterTypeOptions = [
  { label: '无适配', value: 'None' },
  { label: 'JSON Path', value: 'JsonPath' },
  { label: '脚本转换', value: 'Script' }
]

const statusOptions = [
  { label: '启用', value: '0' },
  { label: '停用', value: '1' }
]

const searchSchema = [
  {
    field: 'apiName',
    label: '接口名称',
    type: 'input',
    props: {
      placeholder: '请输入接口名称'
    }
  }
]

const tableColumns = [
  { title: 'ID', key: 'id', width: 80 },
  { title: '接口名称', key: 'apiName', width: 150 },
  { title: '所属系统', key: 'systemName', width: 120 },
  { title: '接口路径', key: 'apiPath', width: 200 },
  {
    title: '请求方法',
    key: 'method',
    width: 80,
    render: (row: any) => {
      const colors = { 'GET': 'success', 'POST': 'info', 'PUT': 'warning', 'DELETE': 'error' }
      return h(NTag, { type: colors[row.method] || 'default', size: 'small' }, { default: () => row.method })
    }
  },
  {
    title: '适配类型',
    key: 'adapterType',
    width: 100,
    render: (row: any) => {
      const map = { 'None': '无适配', 'JsonPath': 'JSON Path', 'Script': '脚本' }
      return map[row.adapterType] || row.adapterType
    }
  },
  {
    title: '状态',
    key: 'status',
    width: 80,
    render: (row: any) => {
      return h(NTag, {
        type: row.status === '0' ? 'success' : 'error',
        size: 'small'
      }, { default: () => row.status === '0' ? '启用' : '停用' })
    }
  },
  { title: '创建时间', key: 'createTime', width: 180 }
]

const editSchema = computed(() => [
  {
    field: 'systemId',
    label: '所属系统',
    type: 'select',
    required: true,
    props: {
      placeholder: '请选择所属系统',
      options: systemOptions.value
    }
  },
  {
    field: 'apiName',
    label: '接口名称',
    type: 'input',
    required: true,
    props: {
      placeholder: '请输入接口名称'
    }
  },
  {
    field: 'apiCode',
    label: '接口编码',
    type: 'input',
    props: {
      placeholder: '请输入接口编码'
    }
  },
  {
    field: 'apiPath',
    label: '接口路径',
    type: 'input',
    required: true,
    props: {
      placeholder: '例如: /api/v1/data'
    }
  },
  {
    field: 'method',
    label: '请求方法',
    type: 'select',
    required: true,
    props: {
      placeholder: '请选择请求方法',
      options: methodOptions
    }
  },
  {
    field: 'adapterType',
    label: '适配类型',
    type: 'select',
    props: {
      placeholder: '请选择适配类型',
      options: adapterTypeOptions
    }
  },
  {
    field: 'adapterConfig',
    label: '适配配置',
    type: 'textarea',
    show: (formData: any) => formData.adapterType === 'JsonPath' || formData.adapterType === 'Script',
    props: {
      placeholder: '请输入适配配置',
      rows: 4
    }
  },
  {
    field: 'description',
    label: '描述',
    type: 'textarea',
    props: {
      placeholder: '请输入描述',
      rows: 2
    }
  },
  {
    field: 'status',
    label: '状态',
    type: 'select',
    props: {
      placeholder: '请选择状态',
      options: statusOptions
    }
  }
])

onMounted(async () => {
  try {
    const res = await getExternalSystemList()
    systemOptions.value = (res.data || []).map((sys: ExternalSystem) => ({
      label: sys.systemName,
      value: sys.id as number
    }))
  } catch (e) {
    console.error('加载系统列表失败', e)
  }
})
</script>

<style lang="scss" scoped>
.external-api-page {
  padding: 16px;
}
</style>