<template>
  <div class="managed-cache-page">
    <div class="workbench-toolbar">
      <div class="filter-group">
        <NInput
          v-model:value="filters.applicationCode"
          class="application-filter"
          clearable
          placeholder="应用编码"
          @keyup.enter="handleSearch"
        >
          <template #prefix>
            <i class="i-mdi:application-brackets-outline" />
          </template>
        </NInput>
        <NInput
          v-model:value="filters.cacheName"
          class="cache-filter"
          clearable
          placeholder="缓存名称"
          @keyup.enter="handleSearch"
        >
          <template #prefix>
            <i class="i-mdi:magnify" />
          </template>
        </NInput>
      </div>
      <div class="toolbar-actions">
        <NButton type="primary" @click="handleSearch">
          <template #icon>
            <i class="i-mdi:magnify" />
          </template>
          查询
        </NButton>
        <NButton @click="handleResetFilters">
          <template #icon>
            <i class="i-mdi:refresh" />
          </template>
          重置
        </NButton>
      </div>
    </div>

    <div class="table-shell">
      <NDataTable
        :columns="columns"
        :data="records"
        :loading="loading"
        :pagination="false"
        :row-key="row => `${row.applicationCode}::${row.cacheName}`"
        :scroll-x="1060"
        size="small"
      />
    </div>

    <div class="mobile-policy-list">
      <NSpin :show="loading">
        <NEmpty v-if="!loading && records.length === 0" description="暂无受管缓存" />
        <div
          v-for="row in records"
          :key="`${row.applicationCode}::${row.cacheName}`"
          class="mobile-policy-item"
        >
          <div class="mobile-item-header">
            <div class="mobile-cache-name">
              {{ row.cacheName }}
            </div>
            <div class="mobile-status-tags">
              <NTag :type="modeTagType(row.cacheMode)" size="small" :bordered="false">
                {{ modeLabels[row.cacheMode] || row.cacheMode }}
              </NTag>
              <NTag :type="row.enabled ? 'success' : 'default'" size="small" :bordered="false">
                {{ row.enabled ? '启用' : '停用' }}
              </NTag>
            </div>
          </div>
          <div class="mobile-description">
            {{ row.description || '未配置描述' }}
          </div>
          <div class="mobile-meta">
            {{ row.applicationCode }} · {{ scopeLabels[row.scope] || row.scope }}
          </div>
          <div class="mobile-details">
            <div>
              <span>本地 TTL</span>
              <strong>{{ formatDuration(row.localTtlSeconds) }}</strong>
            </div>
            <div>
              <span>Redis TTL</span>
              <strong>{{ formatDuration(row.redisTtlSeconds) }}</strong>
            </div>
            <div>
              <span>本地容量</span>
              <strong>{{ formatCount(row.localMaxSize) }}</strong>
            </div>
            <div>
              <span>空值</span>
              <strong>{{ row.cacheNull ? formatDuration(row.nullTtlSeconds) : '不缓存' }}</strong>
            </div>
            <div>
              <span>命中 / 未命中</span>
              <strong>{{ formatCount(row.hitCount) }} / {{ formatCount(row.missCount) }}</strong>
            </div>
            <div>
              <span>写入 / 失败</span>
              <strong>
                {{ formatCount(row.putCount) }} /
                <span :style="hasFailures(row) ? { color: themeVars.errorColor } : undefined">
                  {{ formatCount(row.failureCount) }}
                </span>
              </strong>
            </div>
          </div>
          <div class="mobile-item-footer">
            <NTag v-if="row.overridden" type="warning" size="small" :bordered="false">
              管理覆盖
            </NTag>
            <span v-else class="default-policy">代码默认</span>
            <div class="mobile-actions">
              <NButton text size="small" type="primary" @click="openEdit(row)">
                编辑
              </NButton>
              <NButton text size="small" type="warning" @click="confirmClear(row)">
                清空
              </NButton>
              <NButton text size="small" :disabled="!row.overridden" @click="confirmReset(row)">
                恢复默认
              </NButton>
            </div>
          </div>
        </div>
      </NSpin>
    </div>

    <div class="pagination-bar">
      <span class="total-count">共 {{ pagination.total }} 个缓存</span>
      <NPagination
        v-model:page="pagination.pageNum"
        v-model:page-size="pagination.pageSize"
        :item-count="pagination.total"
        :page-sizes="[10, 20, 50]"
        show-size-picker
        @update:page="loadPolicies"
        @update:page-size="handlePageSizeChange"
      />
    </div>

    <NModal
      v-model:show="editVisible"
      preset="card"
      title="缓存策略"
      :bordered="false"
      :mask-closable="!saving"
      :style="{ width: '660px', maxWidth: 'calc(100vw - 24px)' }"
    >
      <div class="policy-identity">
        <div class="policy-name">
          {{ editForm.cacheName }}
        </div>
        <div class="policy-application">
          {{ editForm.applicationCode }}
        </div>
      </div>
      <NForm
        :model="editForm"
        label-placement="top"
        class="policy-form"
      >
        <NFormItem label="缓存模式">
          <NRadioGroup v-model:value="editForm.cacheMode" size="small">
            <NRadioButton
              v-for="mode in editingAllowedModes"
              :key="mode"
              :value="mode"
            >
              {{ modeLabels[mode] || mode }}
            </NRadioButton>
          </NRadioGroup>
          <div class="allowed-mode-text">
            允许模式：{{ editingAllowedModes.map(mode => modeLabels[mode] || mode).join(' / ') }}
          </div>
        </NFormItem>
        <div class="form-grid">
          <NFormItem label="本地 TTL">
            <NInputNumber
              v-model:value="editForm.localTtlSeconds"
              :min="1"
              :precision="0"
              class="number-input"
            >
              <template #suffix>
                秒
              </template>
            </NInputNumber>
          </NFormItem>
          <NFormItem label="Redis TTL">
            <NInputNumber
              v-model:value="editForm.redisTtlSeconds"
              :min="1"
              :precision="0"
              class="number-input"
            >
              <template #suffix>
                秒
              </template>
            </NInputNumber>
          </NFormItem>
          <NFormItem label="本地容量">
            <NInputNumber
              v-model:value="editForm.localMaxSize"
              :min="1"
              :precision="0"
              class="number-input"
            />
          </NFormItem>
          <NFormItem label="空值 TTL">
            <NInputNumber
              v-model:value="editForm.nullTtlSeconds"
              :min="1"
              :precision="0"
              class="number-input"
            >
              <template #suffix>
                秒
              </template>
            </NInputNumber>
          </NFormItem>
        </div>
        <div class="switch-grid">
          <div class="switch-setting">
            <div>
              <div class="switch-label">
                启用缓存
              </div>
              <div class="switch-description">
                当前策略状态
              </div>
            </div>
            <NSwitch v-model:value="editForm.enabled" />
          </div>
          <div class="switch-setting">
            <div>
              <div class="switch-label">
                缓存空值
              </div>
              <div class="switch-description">
                空值 TTL {{ formatDuration(editForm.nullTtlSeconds) }}
              </div>
            </div>
            <NSwitch v-model:value="editForm.cacheNull" />
          </div>
        </div>
      </NForm>
      <template #footer>
        <div class="modal-footer">
          <NButton :disabled="saving" @click="editVisible = false">
            取消
          </NButton>
          <NButton type="primary" :loading="saving" @click="handleSave">
            保存
          </NButton>
        </div>
      </template>
    </NModal>
  </div>
</template>

<script setup>
import { NButton, NSpace, NTag, useThemeVars } from 'naive-ui'
import { h, onMounted, reactive, ref } from 'vue'
import { request } from '@/utils'
import { normalizeManagedCachePolicy, validateManagedCachePolicy } from './managed-cache-policy'

const modeLabels = {
  LOCAL: '本地',
  REDIS: 'Redis',
  MULTI: '多级',
}

const scopeLabels = {
  GLOBAL: '全局',
  TENANT: '租户',
  TENANT_USER: '租户 + 用户',
  TENANT_USER_ORG: '租户 + 用户 + 组织',
}

const loading = ref(false)
const saving = ref(false)
const themeVars = useThemeVars()
const records = ref([])
const editVisible = ref(false)
const editingAllowedModes = ref([])
const filters = reactive({ applicationCode: '', cacheName: '' })
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const editForm = reactive(normalizeManagedCachePolicy())

const columns = [
  {
    title: '缓存',
    key: 'cacheName',
    width: 300,
    render: row => h('div', { class: 'cache-identity' }, [
      h('div', { class: 'cache-name-row' }, [
        h('span', { class: 'cache-name', title: row.source || '' }, row.cacheName),
        row.overridden
          ? h(NTag, { size: 'tiny', type: 'warning', bordered: false }, { default: () => '管理覆盖' })
          : null,
      ]),
      h('div', { class: 'cache-description', title: row.description || '' }, row.description || '未配置描述'),
      h('div', { class: 'cache-meta' }, [
        h('span', row.applicationCode),
        h('span', { class: 'meta-divider' }, '·'),
        h('span', scopeLabels[row.scope] || row.scope),
      ]),
    ]),
  },
  {
    title: '运行策略',
    key: 'policy',
    width: 190,
    render: row => h('div', { class: 'policy-cell' }, [
      h('div', { class: 'policy-tags' }, [
        h(NTag, { size: 'small', type: modeTagType(row.cacheMode), bordered: false }, {
          default: () => modeLabels[row.cacheMode] || row.cacheMode,
        }),
        h(NTag, { size: 'small', type: row.enabled ? 'success' : 'default', bordered: false }, {
          default: () => row.enabled ? '启用' : '停用',
        }),
      ]),
      h('div', { class: 'allowed-modes' }, `允许：${formatAllowedModes(row.allowedModes)}`),
    ]),
  },
  {
    title: '过期策略',
    key: 'ttl',
    width: 170,
    render: row => h('div', { class: 'detail-pairs' }, [
      detailPair('本地 L1', formatDuration(row.localTtlSeconds)),
      detailPair('Redis L2', formatDuration(row.redisTtlSeconds)),
    ]),
  },
  {
    title: '容量 / 空值',
    key: 'capacity',
    width: 160,
    render: row => h('div', { class: 'detail-pairs' }, [
      detailPair('本地容量', formatCount(row.localMaxSize)),
      detailPair('空值', row.cacheNull ? formatDuration(row.nullTtlSeconds) : '不缓存'),
    ]),
  },
  {
    title: '运行统计',
    key: 'stats',
    width: 145,
    render: row => h('div', { class: 'detail-pairs statistics' }, [
      detailPair('命中', formatCount(row.hitCount)),
      detailPair('未命中', formatCount(row.missCount)),
      detailPair('写入', formatCount(row.putCount)),
      detailPair('失败', formatCount(row.failureCount), hasFailures(row)),
    ]),
  },
  {
    title: '操作',
    key: 'actions',
    width: 180,
    render: row => h(NSpace, { size: [12, 8], wrap: true }, {
      default: () => [
        actionButton('编辑', 'primary', () => openEdit(row)),
        actionButton('清空', 'warning', () => confirmClear(row)),
        actionButton('恢复默认', 'default', () => confirmReset(row), !row.overridden),
      ],
    }),
  },
]

onMounted(loadPolicies)

async function loadPolicies() {
  loading.value = true
  try {
    const response = await request.get('/system/cache/policy/page', {
      params: {
        pageNum: pagination.pageNum,
        pageSize: pagination.pageSize,
        applicationCode: filters.applicationCode || undefined,
        cacheName: filters.cacheName || undefined,
      },
    })
    if (response.code === 200) {
      records.value = response.data?.records || []
      pagination.total = Number(response.data?.total || 0)
    }
  }
  catch {
    window.$message.error('加载受管缓存策略失败')
  }
  finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.pageNum = 1
  loadPolicies()
}

function handleResetFilters() {
  filters.applicationCode = ''
  filters.cacheName = ''
  pagination.pageNum = 1
  loadPolicies()
}

function handlePageSizeChange(pageSize) {
  pagination.pageSize = pageSize
  pagination.pageNum = 1
  loadPolicies()
}

function openEdit(row) {
  Object.assign(editForm, normalizeManagedCachePolicy(row))
  editingAllowedModes.value = [...(row.allowedModes || [])]
  editVisible.value = true
}

async function handleSave() {
  const error = validateManagedCachePolicy(editForm, editingAllowedModes.value)
  if (error) {
    window.$message.warning(error)
    return false
  }
  saving.value = true
  try {
    const response = await request.post('/system/cache/policy/edit', { ...editForm })
    if (response.code === 200) {
      window.$message.success('缓存策略已更新')
      editVisible.value = false
      await loadPolicies()
      return true
    }
    return false
  }
  catch {
    return false
  }
  finally {
    saving.value = false
  }
}

function confirmClear(row) {
  window.$dialog.warning({
    title: '清空受管缓存',
    content: `确定清空 ${row.cacheName} 的全部缓存条目吗？`,
    positiveText: '清空',
    negativeText: '取消',
    onPositiveClick: async () => {
      const response = await request.post('/system/cache/policy/clear', null, {
        params: identityParams(row),
      })
      if (response.code === 200) {
        window.$message.success('缓存已清空')
        await loadPolicies()
      }
    },
  })
}

function confirmReset(row) {
  if (!row.overridden)
    return
  window.$dialog.warning({
    title: '恢复默认策略',
    content: `确定让 ${row.cacheName} 恢复代码声明的默认策略吗？`,
    positiveText: '恢复默认',
    negativeText: '取消',
    onPositiveClick: async () => {
      const response = await request.post('/system/cache/policy/reset', null, {
        params: identityParams(row),
      })
      if (response.code === 200) {
        window.$message.success('已恢复默认策略')
        await loadPolicies()
      }
    },
  })
}

function identityParams(row) {
  return {
    applicationCode: row.applicationCode,
    cacheName: row.cacheName,
  }
}

function actionButton(label, type, onClick, disabled = false) {
  return h(NButton, { text: true, size: 'small', type, disabled, onClick }, { default: () => label })
}

function detailPair(label, value, error = false) {
  return h('div', { class: 'detail-pair' }, [
    h('span', { class: 'detail-label' }, label),
    h('span', {
      class: 'detail-value',
      style: error ? { color: themeVars.value.errorColor } : undefined,
    }, value),
  ])
}

function hasFailures(row) {
  return Number(row.failureCount || 0) > 0
}

function formatAllowedModes(modes) {
  return (modes || []).map(mode => modeLabels[mode] || mode).join(' / ') || '-'
}

function formatCount(value) {
  return Number(value || 0).toLocaleString()
}

function modeTagType(mode) {
  if (mode === 'MULTI')
    return 'success'
  if (mode === 'REDIS')
    return 'info'
  return 'warning'
}

function formatDuration(seconds) {
  const value = Number(seconds || 0)
  if (value > 0 && value % 3600 === 0)
    return `${value / 3600}h`
  if (value > 0 && value % 60 === 0)
    return `${value / 60}m`
  return `${value}s`
}
</script>

<style scoped>
.managed-cache-page {
  min-width: 0;
  min-height: 520px;
  padding: 12px 16px 16px;
}

.workbench-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 12px;
}

.filter-group,
.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.filter-group {
  min-width: 0;
}

.application-filter {
  width: 230px;
}

.cache-filter {
  width: 280px;
}

.table-shell {
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--border-color);
  border-radius: 4px;
}

.mobile-policy-list {
  display: none;
}

.pagination-bar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 16px;
  min-width: 0;
  padding-top: 14px;
}

.total-count {
  color: var(--text-color-3);
  font-size: 13px;
  white-space: nowrap;
}

.policy-identity {
  padding-bottom: 16px;
  margin-bottom: 16px;
  border-bottom: 1px solid var(--border-color);
}

.policy-name {
  overflow-wrap: anywhere;
  color: var(--text-color-1);
  font-size: 16px;
  font-weight: 600;
}

.policy-application,
.allowed-mode-text,
.switch-description {
  margin-top: 4px;
  color: var(--text-color-3);
  font-size: 12px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 16px;
}

.number-input {
  width: 100%;
}

.switch-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.switch-setting {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-width: 0;
  padding: 12px;
  border: 1px solid var(--border-color);
  border-radius: 4px;
}

.switch-label {
  color: var(--text-color-1);
  font-weight: 500;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

:deep(.cache-identity) {
  min-width: 0;
  padding: 4px 0;
  line-height: 1.4;
}

:deep(.cache-name-row) {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

:deep(.cache-name) {
  overflow: hidden;
  color: var(--text-color-1);
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

:deep(.cache-description) {
  overflow: hidden;
  margin-top: 2px;
  color: var(--text-color-2);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

:deep(.cache-meta) {
  display: flex;
  gap: 5px;
  min-width: 0;
  margin-top: 3px;
  overflow: hidden;
  color: var(--text-color-3);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

:deep(.policy-cell),
:deep(.detail-pairs) {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

:deep(.policy-tags) {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

:deep(.allowed-modes) {
  overflow: hidden;
  color: var(--text-color-3);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

:deep(.detail-pair) {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: baseline;
  gap: 10px;
  min-width: 0;
}

:deep(.detail-label) {
  color: var(--text-color-3);
  font-size: 12px;
}

:deep(.detail-value) {
  overflow: hidden;
  color: var(--text-color-1);
  font-size: 13px;
  font-variant-numeric: tabular-nums;
  text-overflow: ellipsis;
  white-space: nowrap;
}

:deep(.statistics .detail-value) {
  font-weight: 500;
}

:deep(.n-data-table-th) {
  background-color: color-mix(in srgb, var(--card-color) 94%, var(--body-color));
}

:deep(.n-data-table-td) {
  vertical-align: middle;
}

@media (max-width: 720px) {
  .managed-cache-page {
    padding: 10px;
  }

  .workbench-toolbar,
  .filter-group {
    align-items: stretch;
    flex-direction: column;
  }

  .application-filter,
  .cache-filter {
    width: 100%;
  }

  .toolbar-actions {
    width: 100%;
  }

  .toolbar-actions > * {
    flex: 1;
  }

  .table-shell {
    display: none;
  }

  .mobile-policy-list {
    display: block;
    overflow: hidden;
    border: 1px solid var(--border-color);
    border-radius: 4px;
  }

  .mobile-policy-item {
    padding: 14px;
    border-bottom: 1px solid var(--border-color);
  }

  .mobile-policy-item:last-child {
    border-bottom: 0;
  }

  .mobile-item-header,
  .mobile-status-tags,
  .mobile-item-footer,
  .mobile-actions {
    display: flex;
    align-items: center;
  }

  .mobile-item-header,
  .mobile-item-footer {
    justify-content: space-between;
    gap: 10px;
  }

  .mobile-status-tags,
  .mobile-actions {
    flex-wrap: wrap;
    gap: 8px;
  }

  .mobile-cache-name {
    min-width: 0;
    overflow-wrap: anywhere;
    color: var(--text-color-1);
    font-weight: 600;
  }

  .mobile-description {
    margin-top: 6px;
    color: var(--text-color-2);
    font-size: 13px;
  }

  .mobile-meta,
  .default-policy {
    margin-top: 3px;
    color: var(--text-color-3);
    font-size: 12px;
  }

  .mobile-details {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px 16px;
    padding: 12px 0;
    margin: 12px 0;
    border-top: 1px solid var(--border-color);
    border-bottom: 1px solid var(--border-color);
  }

  .mobile-details > div {
    display: flex;
    flex-direction: column;
    min-width: 0;
  }

  .mobile-details span {
    color: var(--text-color-3);
    font-size: 12px;
  }

  .mobile-details strong {
    margin-top: 2px;
    overflow: hidden;
    color: var(--text-color-1);
    font-size: 13px;
    font-weight: 500;
    font-variant-numeric: tabular-nums;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .form-grid,
  .switch-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .pagination-bar {
    align-items: flex-start;
    flex-direction: column;
    overflow-x: auto;
  }
}
</style>
