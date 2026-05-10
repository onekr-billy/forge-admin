<template>
  <div class="external-system-page">
    <AiCrudPage
      ref="crudRef"
      api="/external/system"
      :api-config="{
        list: 'get@/external/system/page',
        detail: 'get@/external/system/:id',
        add: 'post@/external/system',
        update: 'put@/external/system',
        delete: 'delete@/external/system/:id',
      }"
      :load-detail-on-edit="true"
      :search-schema="searchSchema"
      :columns="tableColumns"
      :edit-schema="editSchema"
      row-key="id"
      add-button-text="新增外部系统"
    />
  </div>
</template>

<script setup lang="ts">
import { NTag } from 'naive-ui'
import { computed, h } from 'vue'
import { AiCrudPage } from '@/components/ai-form'
import { getExternalSystemList } from '@/api/external/system'

defineOptions({ name: 'ExternalSystem' })

const crudRef = ref(null)

const authTypeOptions = [
  { label: '无认证', value: 'None' },
  { label: 'Bearer Token', value: 'BearerToken' }
]

const statusOptions = [
  { label: '启用', value: '0' },
  { label: '停用', value: '1' }
]

const searchSchema = [
  {
    field: 'systemName',
    label: '系统名称',
    type: 'input',
    props: {
      placeholder: '请输入系统名称'
    }
  }
]

const tableColumns = [
  { title: 'ID', key: 'id', width: 80 },
  { title: '系统名称', key: 'systemName', width: 150 },
  { title: '系统编码', key: 'systemCode', width: 120 },
  { title: '基础URL', key: 'baseUrl', width: 250 },
  {
    title: '认证类型',
    key: 'authType',
    width: 120,
    render: (row: any) => {
      const map = { 'None': '无认证', 'BearerToken': 'Bearer Token' }
      return map[row.authType] || row.authType
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
    field: 'systemName',
    label: '系统名称',
    type: 'input',
    required: true,
    props: {
      placeholder: '请输入系统名称'
    }
  },
  {
    field: 'systemCode',
    label: '系统编码',
    type: 'input',
    props: {
      placeholder: '请输入系统编码'
    }
  },
  {
    field: 'baseUrl',
    label: '基础URL',
    type: 'input',
    required: true,
    props: {
      placeholder: '例如: https://api.example.com'
    }
  },
  {
    field: 'authType',
    label: '认证类型',
    type: 'select',
    required: true,
    props: {
      placeholder: '请选择认证类型',
      options: authTypeOptions
    }
  },
  {
    field: 'authConfig',
    label: '认证配置',
    type: 'textarea',
    show: (formData: any) => formData.authType === 'BearerToken',
    props: {
      placeholder: '请输入 JSON 格式认证配置，如: {"token":"xxx"}',
      rows: 3
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
</script>

<style lang="scss" scoped>
.external-system-page {
  padding: 16px;
}
</style>