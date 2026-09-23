<template>
  <n-result v-if="!store.has('ai:capability:invocation:query')" status="403" title="需要调用记录查看权限" />
  <template v-else>
    <ApplicationCapabilitySelection />
    <template v-if="store.selected">
      <div class="integration-toolbar">
        <n-input v-model:value="store.requestId" clearable placeholder="按请求 ID 查询" aria-label="请求 ID" class="request-search" @keyup.enter="search" @clear="search" /><n-button :loading="store.loading.logs" @click="search">
          查询记录
        </n-button>
      </div>
      <n-alert v-if="store.errors.logs" type="error" class="integration-error">
        {{ store.errors.logs }}
      </n-alert>
      <n-data-table :columns="columns" :data="store.logs" :loading="store.loading.logs" :scroll-x="850" :row-key="row => row.id">
        <template #empty>
          <n-empty description="暂无此能力的调用记录">
            <template #extra>
              授权后完成一次真实调用，记录会出现在这里。
            </template>
          </n-empty>
        </template>
      </n-data-table>
      <n-pagination v-if="store.logTotal > 10" v-model:page="store.logPage" :page-size="10" :item-count="store.logTotal" class="integration-pagination" @update:page="store.loadLogs" />
      <p class="integration-help">
        仅查看当前能力的审计记录。业务写操作不提供一键重试，避免重复填报或重复审批。
      </p>
    </template>
  </template>
</template>

<script setup>
import { useApplicationIntegrationStore } from '@/stores/application/integrationStore'
import ApplicationCapabilitySelection from './ApplicationCapabilitySelection.vue'

const store = useApplicationIntegrationStore()
const columns = [
  { title: '调用时间', key: 'createTime', width: 180 },
  { title: '接入系统', key: 'clientCode', width: 150 },
  { title: '请求 ID', key: 'requestId', width: 210, ellipsis: { tooltip: true } },
  { title: '结果码', key: 'resultCode', width: 130 },
  { title: '耗时', key: 'durationMs', width: 90, render: row => row.durationMs == null ? '—' : `${row.durationMs} ms` },
  { title: '失败原因', key: 'errorMessage', minWidth: 220, ellipsis: { tooltip: true }, render: row => row.errorMessage || '—' },
]
function search() {
  store.logPage = 1
  store.loadLogs()
}
</script>

<style scoped>
.request-search {
  width: min(100%, 340px);
}
</style>
