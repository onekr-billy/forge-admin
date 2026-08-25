<template>
  <div class="flow-page">
    <!-- 统计卡片 -->
    <FlowModelStats
      :total-count="totalCount"
      :designing-count="designingCount"
      :deployed-count="deployedCount"
      :suspended-count="suspendedCount"
      :disabled-count="disabledCount"
      :active-status="activeStatsStatus"
      @filter="handleFilter"
    />

    <!-- 页面头部 -->
    <div class="page-header">
      <div class="header-left">
        <div class="title-row">
          <div class="title-icon">
            <i class="i-material-symbols:device-hub" />
          </div>
          <h2 class="page-title">
            流程模型
          </h2>
        </div>
      </div>
      <div class="header-right">
        <n-input
          v-model:value="queryParams.modelName"
          placeholder="搜索模型名称或 Key"
          clearable
          class="search-input"
          @keyup.enter="handleSearch"
        >
          <template #prefix>
            <i class="i-material-symbols:search" />
          </template>
        </n-input>
        <NTreeSelect
          v-model:value="queryParams.category"
          placeholder="流程分类"
          clearable
          class="category-select"
          :options="categoryTreeOptions"
          :default-expand-all="true"
        />
        <n-select
          v-model:value="queryParams.status"
          placeholder="状态"
          clearable
          class="status-select"
          :options="statusOptions"
          @update:value="handleStatusSelect"
        />
        <div class="toolbar-actions">
          <n-button type="primary" @click="handleSearch">
            <template #icon>
              <i class="i-material-symbols:search" />
            </template>
            查询
          </n-button>
          <n-button @click="handleReset">
            <template #icon>
              <i class="i-material-symbols:restart-alt" />
            </template>
            清空
          </n-button>
          <n-button type="primary" @click="handleAdd">
            <template #icon>
              <i class="i-material-symbols:add" />
            </template>
            新增模型
          </n-button>
        </div>
      </div>
    </div>

    <!-- 模型列表 -->
    <n-spin :show="loading" class="model-list-spin">
      <div v-if="dataSource.length > 0" class="model-grid">
        <div
          v-for="item in dataSource"
          :key="item.id"
          class="model-card"
        >
          <div class="card-header">
            <div class="card-title-block">
              <div class="card-title-row">
                <div class="card-title-icon-box">
                  <i class="i-lucide:git-merge card-title-icon" />
                </div>
                <div class="card-title-main">
                  <div class="card-title">
                    {{ item.modelName }}
                  </div>
                  <div class="card-key">
                    {{ item.modelKey }}
                  </div>
                </div>
              </div>
            </div>
            <span class="status-tag" :class="statusClass(item.status)">
              {{ getLabel('flow_model_status', item.status) }}
            </span>
          </div>
          <div class="card-body">
            <div class="card-tags">
              <span class="designer-type-badge" :class="designerTypeClass(item.designerType)">
                {{ designerTypeLabel(item.designerType) }}
              </span>
              <span v-if="getCategoryDisplayName(item)" class="category-badge">
                {{ getCategoryDisplayName(item) }}
              </span>
            </div>
            <div class="card-binding" :class="{ empty: !item.businessBindings?.length }">
              <i class="i-lucide:link-2" />
              <span>{{ formatBusinessBindings(item) }}</span>
            </div>
            <div class="card-desc">
              {{ item.description || '暂无描述' }}
            </div>
          </div>
          <div class="card-footer">
            <div class="card-metadata">
              <div class="meta-item">
                <i class="i-lucide:calendar" />
                {{ formatDate(item.updateTime) || '未更新' }}
              </div>
              <div class="meta-item">
                <i class="i-lucide:git-commit" />
                v{{ item.version || 1 }}
              </div>
            </div>
            <div class="card-actions">
              <button
                type="button"
                class="card-action-link"
                @click.stop="handleDesign(item)"
              >
                编辑
              </button>
              <template v-if="item.status === 0 || item.status === 1">
                <span class="card-action-separator" />
                <button
                  v-if="item.status === 0"
                  type="button"
                  class="card-action-link"
                  @click.stop="handleDeploy(item)"
                >
                  发布
                </button>
                <button
                  v-else
                  type="button"
                  class="card-action-link"
                  @click.stop="handleViewInstances(item)"
                >
                  实例
                </button>
              </template>
              <span class="card-action-separator" />
              <n-dropdown
                trigger="click"
                :options="getActionOptions(item)"
                @select="key => handleActionSelect(key, item)"
              >
                <button type="button" class="card-more-action" aria-label="更多操作" @click.stop>
                  <i class="i-lucide:more-horizontal" />
                </button>
              </n-dropdown>
            </div>
          </div>
        </div>
      </div>

      <!-- 加载占位：首次加载无数据时撑开高度，保证 loading 可见 -->
      <div v-else-if="loading" class="model-list-loading" />

      <!-- 空状态 -->
      <n-empty
        v-else
        description="暂无流程模型，点击「新增模型」开始设计"
        class="empty-state"
      >
        <template #extra>
          <n-button type="primary" @click="handleAdd">
            <template #icon>
              <i class="i-material-symbols:add" />
            </template>
            新增模型
          </n-button>
        </template>
      </n-empty>
    </n-spin>

    <!-- 分页 -->
    <div v-if="pagination.itemCount > 0" class="pagination-wrapper">
      <n-pagination
        v-model:page="pagination.page"
        v-model:page-size="pagination.pageSize"
        :item-count="pagination.itemCount"
        :page-sizes="[12, 24, 48]"
        show-size-picker
        @update:page="fetchData"
        @update:page-size="handlePageSizeChange"
      />
    </div>

    <!-- 新增/编辑弹窗 -->
    <Teleport to="body">
      <NModal
        v-model:show="showModal"
        preset="card"
        :title="modalTitle"
        style="width: min(760px, calc(100vw - 32px))"
        :mask-closable="false"
      >
        <n-form
          ref="formRef"
          :model="formData"
          :rules="rules"
          label-placement="left"
          label-width="100"
        >
          <n-grid :cols="2" :x-gap="16">
            <n-form-item-gi label="流程模式" path="designerType" :span="2">
              <div class="designer-type-chooser" :class="{ disabled: isEdit }">
                <button
                  v-for="option in designerTypeOptions"
                  :key="option.value"
                  type="button"
                  class="designer-type-option"
                  :class="{ active: formData.designerType === option.value }"
                  :disabled="isEdit"
                  @click="formData.designerType = option.value"
                >
                  <span class="designer-type-icon">
                    <i :class="option.icon" />
                  </span>
                  <span class="designer-type-main">
                    <span class="designer-type-title">{{ option.label }}</span>
                    <span class="designer-type-desc">{{ option.desc }}</span>
                  </span>
                </button>
              </div>
            </n-form-item-gi>
            <n-form-item-gi label="模型名称" path="modelName" :span="2">
              <n-input v-model:value="formData.modelName" placeholder="请输入模型名称" />
            </n-form-item-gi>
            <n-form-item-gi label="模型Key" path="modelKey">
              <n-input
                v-model:value="formData.modelKey"
                placeholder="系统自动生成"
                disabled
              />
            </n-form-item-gi>
            <n-form-item-gi label="流程分类" path="category" :span="2">
              <NTreeSelect
                v-model:value="formData.category"
                placeholder="请选择分类"
                :options="categoryTreeOptions"
                :default-expand-all="true"
              />
            </n-form-item-gi>
            <n-form-item-gi label="待办跳转" path="todoDetailUrlTemplate" :span="2">
              <div class="w-full">
                <n-input
                  v-model:value="formData.todoDetailUrlTemplate"
                  placeholder="留空默认跳待办详情页；可填相对路径或完整链接"
                  clearable
                />
                <div class="text-12px mt-1 text-gray-400">
                  企业协同待办卡片点击后的跳转地址，支持占位符 {taskId}/{businessKey}/{processInstanceId}（自动替换并 URL 编码）。例：/#/pages/order-detail?bizKey={businessKey}
                </div>
              </div>
            </n-form-item-gi>
            <n-form-item-gi label="描述" path="description" :span="2">
              <n-input
                v-model:value="formData.description"
                type="textarea"
                placeholder="请输入描述"
                :rows="3"
              />
            </n-form-item-gi>
          </n-grid>
        </n-form>
        <template #footer>
          <n-space justify="end">
            <n-button @click="showModal = false">
              取消
            </n-button>
            <n-button type="primary" :loading="submitLoading" @click="handleSubmit">
              确定
            </n-button>
          </n-space>
        </template>
      </NModal>
    </Teleport>

    <Teleport to="body">
      <NModal
        v-model:show="showStartTestModal"
        preset="card"
        :title="startTestTitle"
        style="width: min(760px, calc(100vw - 32px))"
        content-style="max-height: calc(100vh - 180px); overflow: auto;"
        :mask-closable="false"
      >
        <div class="start-test-modal">
          <div class="start-test-summary">
            <div class="summary-icon">
              <i class="i-material-symbols:play-circle-outline" />
            </div>
            <div class="summary-main">
              <div class="summary-title">
                {{ currentStartModel?.modelName || '-' }}
              </div>
              <div class="summary-subtitle">
                {{ currentStartModel?.modelKey || '-' }}
              </div>
            </div>
            <span class="status-tag deployed">测试发起</span>
          </div>

          <n-alert v-if="startTestAlert" :type="startTestAlert.type" :show-icon="false" class="start-test-alert">
            {{ startTestAlert.text }}
          </n-alert>

          <FlowFormCreateRenderer
            v-if="showStartTestModal && startTestFormSchema.length"
            ref="startTestFormRef"
            v-model="startTestFormData"
            :schema="startTestFormSchema"
          />
          <n-empty v-else size="small" description="当前模型没有可渲染的动态表单，将以空变量发起测试流程" />
        </div>

        <template #footer>
          <n-space justify="end">
            <n-button @click="showStartTestModal = false">
              取消
            </n-button>
            <n-button secondary @click="handleViewStarted">
              我发起的
            </n-button>
            <n-button type="primary" :loading="startTestLoading" @click="handleSubmitStartTest">
              发起测试
            </n-button>
          </n-space>
        </template>
      </NModal>
    </Teleport>

    <Teleport to="body">
      <NModal
        v-model:show="showDesignModal"
        :mask-closable="false"
        :close-on-esc="false"
        display-directive="if"
        class="flow-design-modal"
        style="width: 100vw; height: 100vh; max-width: none; margin: 0;"
      >
        <div class="flow-design-modal-shell">
          <FlowDesignPage
            v-if="currentDesignModelId"
            embedded
            :model-id="currentDesignModelId"
            :business-object-code="currentDesignBinding?.objectCode || ''"
            :business-object-name="currentDesignBinding?.objectName || ''"
            :business-entry-route="currentDesignBinding?.entryRoute || ''"
            :code-app="isCodeAppBinding(currentDesignBinding)"
            @close="handleDesignModalClose"
            @saved="fetchData"
            @deployed="fetchData"
          />
        </div>
      </NModal>
    </Teleport>

    <VersionHistory
      v-if="showVersionHistory"
      :model-id="currentModelId"
      :current-version="currentModelVersion"
      @close="showVersionHistory = false"
      @refresh="fetchData"
    />
  </div>
</template>

<script setup>
import { CopyOutline, CreateOutline, PauseCircleOutline, PlayCircleOutline, TimeOutline, TrashOutline } from '@vicons/ionicons5'
import { NIcon, NModal, NTreeSelect } from 'naive-ui'
import { computed, defineAsyncComponent, h, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { businessFlowModelBindings } from '@/api/business-app'
import flowApi from '@/api/flow'
import FlowModelStats from '@/components/flow/FlowModelStats.vue'
import { useDict } from '@/composables/useDict'
import DesignerAsyncLoader from '@/views/app-center/components/designer/DesignerAsyncLoader.vue'
import { buildFlowCategoryTreeOptions, resolveFlowCategoryLabel, resolveFlowCategoryValue } from './utils/categoryOptions'

const router = useRouter()

const { dict, getLabel } = useDict('flow_model_status', 'flow_process_form_type', 'flow_designer_type')

const FlowDesignAsyncLoader = {
  name: 'FlowDesignAsyncLoader',
  setup() {
    return () => h(DesignerAsyncLoader, {
      title: '正在打开流程设计器',
      description: '首次加载需要准备流程画布与属性面板资源',
      overlay: true,
    })
  },
}

const FlowFormRendererAsyncLoader = {
  name: 'FlowFormRendererAsyncLoader',
  setup() {
    return () => h(DesignerAsyncLoader, {
      title: '正在加载测试表单',
      description: '首次打开需要准备表单渲染资源',
      overlay: true,
    })
  },
}

const FlowModalAsyncLoader = {
  name: 'FlowModalAsyncLoader',
  setup() {
    return () => h(NModal, {
      show: true,
      preset: 'card',
      title: '正在加载',
      style: 'width: min(560px, calc(100vw - 32px))',
      maskClosable: false,
    }, {
      default: () => h(DesignerAsyncLoader, {
        title: '正在加载版本历史',
        description: '首次打开需要准备流程图查看资源',
        overlay: true,
      }),
    })
  },
}

const FlowDesignPage = defineAsyncComponent({
  loader: () => import('./design.vue'),
  loadingComponent: FlowDesignAsyncLoader,
  delay: 120,
  suspensible: false,
})
const FlowFormCreateRenderer = defineAsyncComponent({
  loader: () => import('@/components/form-create/FlowFormCreateRenderer.vue'),
  loadingComponent: FlowFormRendererAsyncLoader,
  delay: 120,
  suspensible: false,
})
const VersionHistory = defineAsyncComponent({
  loader: () => import('./version.vue'),
  loadingComponent: FlowModalAsyncLoader,
  delay: 120,
  suspensible: false,
})

const statusOptions = computed(() => toNumberOptions(dict.value.flow_model_status))
const categoryTreeOptions = ref([])
const designerTypePresentation = {
  approval: {
    icon: 'i-material-symbols:approval-delegation-outline',
    desc: '适合人员审批、条件分支、抄送和表单权限配置。',
  },
  business: {
    icon: 'i-material-symbols:account-tree-outline',
    desc: '适合完整 BPMN 工作流、服务任务、事件和复杂业务编排。',
  },
}
const designerTypeOptions = computed(() => (dict.value.flow_designer_type || []).map(item => ({
  ...item,
  ...designerTypePresentation[item.value],
})))

function statusClass(status) {
  const cls = { 0: 'designing', 1: 'deployed', 2: 'suspended', 3: 'disabled' }
  return cls[status] || 'default'
}

function toNumberOptions(options = []) {
  return options.map(item => ({
    ...item,
    value: Number(item.value),
  }))
}

function formatDate(d) {
  if (!d)
    return ''
  return d.slice(0, 10)
}

function normalizeDesignerType(value) {
  return value === 'business' ? 'business' : 'approval'
}

function designerTypeLabel(value) {
  const normalizedValue = normalizeDesignerType(value)
  return designerTypeOptions.value.find(item => item.value === normalizedValue)?.label || normalizedValue
}

function designerTypeClass(value) {
  return normalizeDesignerType(value) === 'business' ? 'business' : 'approval'
}

function getCategoryDisplayName(row) {
  return row?.categoryName || resolveFlowCategoryLabel(row?.category, categoryTreeOptions.value, '')
}

function isCodeAppBinding(binding) {
  const value = binding?.codeApp
  if (value === true || value === 1)
    return true
  return ['true', '1', 'yes', 'y'].includes(String(value || '').trim().toLowerCase())
}

function getActionOptions(row) {
  const renderIcon = (icon) => {
    return () => h(NIcon, null, { default: () => h(icon) })
  }
  const opts = [
    { label: '编辑信息', key: 'edit', icon: renderIcon(CreateOutline) },
    { label: '版本历史', key: 'versionHistory', icon: renderIcon(TimeOutline) },
    { label: '复制模型', key: 'copy', icon: renderIcon(CopyOutline) },
  ]
  if (row.status === 1) {
    opts.splice(1, 0, { label: '发起测试', key: 'startTest', icon: renderIcon(PlayCircleOutline) })
    opts.push({ label: '挂起', key: 'suspend', icon: renderIcon(PauseCircleOutline) })
  }
  if (row.status === 2) {
    opts.push({ label: '激活', key: 'activate', icon: renderIcon(PlayCircleOutline) })
  }
  opts.push({ type: 'divider', key: 'd1' })
  opts.push({ label: '删除', key: 'delete', icon: renderIcon(TrashOutline), props: { style: 'color: #d03050' } })
  return opts
}

function handleActionSelect(key, row) {
  const map = { edit: handleEdit, startTest: handleStartTest, copy: handleCopy, versionHistory: handleVersionHistory, suspend: handleSuspend, activate: handleActivate, delete: handleDelete }
  map[key]?.(row)
}

const queryParams = reactive({ modelName: '', category: null, status: null })
const activeStatsStatus = computed(() => queryParams.status ?? 'all')
const dataSource = ref([])
const loading = ref(false)
const pagination = reactive({ page: 1, pageSize: 12, itemCount: 0 })
const showVersionHistory = ref(false)
const currentModelId = ref('')
const currentModelVersion = ref(null)
const showDesignModal = ref(false)
const currentDesignModelId = ref('')
const currentDesignBinding = ref(null)
const showStartTestModal = ref(false)
const startTestLoading = ref(false)
const startTestFormRef = ref(null)
const startTestFormData = ref({})
const startTestFormSchema = ref([])
const currentStartModel = ref(null)
const startTestTitle = computed(() => `发起测试 - ${currentStartModel.value?.modelName || '流程模型'}`)
const startTestAlert = computed(() => {
  if (!currentStartModel.value)
    return null
  if (currentStartModel.value.status !== 1) {
    return { type: 'warning', text: '当前模型尚未部署，部署后才能发起测试流程。' }
  }
  if (currentStartModel.value.formType === 'external') {
    return { type: 'warning', text: '当前模型使用外置表单，测试工具不会渲染外置页面，将以空变量发起。' }
  }
  if (!startTestFormSchema.value.length) {
    return { type: 'info', text: '当前模型没有动态表单字段，发起后仅使用系统内置流程变量。' }
  }
  return { type: 'info', text: '这是流程模型测试入口，只用于验证流程流转；正式业务入口仍由低代码应用或业务页面承载。' }
})

const totalCount = ref(0)
const designingCount = ref(0)
const deployedCount = ref(0)
const suspendedCount = ref(0)
const disabledCount = ref(0)

async function fetchCategories() {
  try {
    const res = await flowApi.getCategoryTreeSelect(false)
    if (res.code === 200) {
      categoryTreeOptions.value = buildFlowCategoryTreeOptions(res.data || [])
    }
  }
  catch {
    console.error('加载分类失败')
  }
}

async function fetchData() {
  loading.value = true
  try {
    await Promise.all([
      fetchModelPage(),
      fetchModelStatistics(),
    ])
  }
  catch {
    console.error('加载模型列表失败')
  }
  finally {
    loading.value = false
  }
}

async function fetchModelPage() {
  const res = await flowApi.getModelPage({
    pageNum: pagination.page,
    pageSize: pagination.pageSize,
    ...queryParams,
  })
  if (res.code === 200) {
    const records = res.data?.records || []
    dataSource.value = await enrichModelBusinessBindings(records)
    pagination.itemCount = res.data?.total || 0
  }
}

async function fetchModelStatistics() {
  const res = await flowApi.getModelStatistics({
    modelName: queryParams.modelName,
    category: queryParams.category,
  })
  if (res.code === 200) {
    applyModelStatistics(res.data || {})
  }
}

function applyModelStatistics(data = {}) {
  totalCount.value = toCount(data.total)
  designingCount.value = toCount(data.designing)
  deployedCount.value = toCount(data.deployed)
  suspendedCount.value = toCount(data.suspended)
  disabledCount.value = toCount(data.disabled)
}

function toCount(value) {
  const count = Number(value)
  return Number.isFinite(count) ? count : 0
}

function handlePageSizeChange(v) {
  pagination.pageSize = v
  pagination.page = 1
  fetchData()
}

function handleSearch() {
  pagination.page = 1
  fetchData()
}

function handleStatusSelect() {
  pagination.page = 1
  fetchData()
}

function handleReset() {
  Object.assign(queryParams, { modelName: '', category: null, status: null })
  pagination.page = 1
  fetchData()
}

function handleFilter(status) {
  if (status === 'all') {
    queryParams.status = null
  }
  else {
    queryParams.status = status
  }
  pagination.page = 1
  fetchData()
}

const showModal = ref(false)
const modalTitle = ref('新增模型')
const isEdit = ref(false)
const submitLoading = ref(false)
const formRef = ref(null)
const formData = reactive({
  id: '',
  modelName: '',
  modelKey: '',
  category: '',
  flowType: '',
  designerType: 'approval',
  formType: 'dynamic',
  description: '',
  notifyType: 'redis',
  webhookUrl: '',
  todoDetailUrlTemplate: '',
  notifyConfig: null,
})
const rules = {
  modelName: { required: true, message: '请输入模型名称', trigger: 'blur' },
  category: { required: true, message: '请选择分类', trigger: 'change' },
  designerType: { required: true, message: '请选择流程模式', trigger: 'change' },
}

function handleAdd() {
  isEdit.value = false
  modalTitle.value = '新增模型'
  Object.assign(formData, { id: '', modelName: '', modelKey: generateModelKey(), category: '', flowType: '', designerType: 'approval', formType: 'dynamic', description: '', notifyType: 'redis', webhookUrl: '', todoDetailUrlTemplate: '', notifyConfig: null })
  showModal.value = true
}

function generateModelKey() {
  const suffix = `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 8)}`
  return `process_${suffix}`
}

function handleEdit(row) {
  isEdit.value = true
  modalTitle.value = '编辑模型'
  Object.assign(formData, row, {
    designerType: normalizeDesignerType(row.designerType),
    category: resolveFlowCategoryValue(row.category, categoryTreeOptions.value),
    notifyConfig: row.notifyConfig || null,
  })
  showModal.value = true
}

async function handleSubmit() {
  try {
    await formRef.value?.validate()
    submitLoading.value = true
    const api = isEdit.value ? flowApi.updateModel : flowApi.createModel
    const res = await api(formData)
    if (res.code === 200) {
      window.$message?.success(isEdit.value ? '编辑成功' : '新增成功')
      showModal.value = false
      fetchData()
    }
    else {
      window.$message?.error(res.message || '操作失败')
    }
  }
  catch {
    console.error('提交失败')
  }
  finally {
    submitLoading.value = false
  }
}

async function enrichModelBusinessBindings(records = []) {
  await Promise.all(records.map(async (row) => {
    if (!row?.modelKey) {
      row.businessBindings = []
      return
    }
    try {
      const res = await businessFlowModelBindings(row.modelKey)
      row.businessBindings = res.code === 200 && Array.isArray(res.data) ? res.data : []
    }
    catch (error) {
      console.warn('[FlowModel] 加载业务绑定失败:', row.modelKey, error?.message || error)
      row.businessBindings = []
    }
  }))
  return records
}

function formatBusinessBindings(row = {}) {
  const bindings = Array.isArray(row.businessBindings) ? row.businessBindings : []
  if (!bindings.length)
    return '未绑定业务应用'
  const names = bindings.map(item => item.objectName || item.objectCode).filter(Boolean)
  return names.length > 2 ? `${names.slice(0, 2).join('、')} 等 ${names.length} 个业务应用` : names.join('、')
}

async function handleDesign(row) {
  if (!Array.isArray(row.businessBindings)) {
    const enriched = await enrichModelBusinessBindings([row])
    row.businessBindings = enriched[0]?.businessBindings || []
  }
  currentDesignBinding.value = row.businessBindings?.[0] || null
  currentDesignModelId.value = row.id
  showDesignModal.value = true
}

function handleDesignModalClose() {
  showDesignModal.value = false
  currentDesignModelId.value = ''
  currentDesignBinding.value = null
  fetchData()
}

function handleViewInstances(row) {
  router.push({ path: '/flow/monitor', query: { modelKey: row.modelKey } })
}

async function handleStartTest(row) {
  if (row.status !== 1) {
    window.$message?.warning('请先部署流程模型后再发起测试')
    return
  }
  currentStartModel.value = row
  startTestFormData.value = {}
  startTestFormSchema.value = []
  showStartTestModal.value = true
  try {
    const res = await flowApi.getModelDetail(row.id)
    if (res.code === 200 && res.data) {
      currentStartModel.value = { ...row, ...res.data }
      startTestFormSchema.value = parseFormSchema(res.data.formJson)
    }
  }
  catch (error) {
    console.error('加载流程模型表单失败:', error)
    window.$message?.warning('加载流程模型表单失败，将以空变量发起')
  }
}

function parseFormSchema(formJson) {
  if (!formJson)
    return []
  if (Array.isArray(formJson))
    return formJson
  try {
    const parsed = JSON.parse(formJson)
    return Array.isArray(parsed) ? parsed : []
  }
  catch {
    return []
  }
}

async function handleSubmitStartTest() {
  if (!currentStartModel.value)
    return
  startTestLoading.value = true
  try {
    const variables = startTestFormSchema.value.length
      ? await startTestFormRef.value?.submit?.()
      : {}
    const now = Date.now()
    const businessKey = `FLOW_TEST:${currentStartModel.value.modelKey}:${now}`
    const title = `${currentStartModel.value.modelName || currentStartModel.value.modelKey}-测试发起`
    const res = await flowApi.startProcess(currentStartModel.value.modelKey, {
      businessKey,
      businessType: 'FLOW_MODEL_TEST',
      title,
      variables: variables || {},
      testStart: true,
    })
    if (res.code === 200) {
      window.$message?.success('测试流程已发起')
      showStartTestModal.value = false
      router.push({ path: '/flow/started', query: { title } })
    }
    else {
      window.$message?.error(res.message || '发起测试失败')
    }
  }
  catch (error) {
    console.error('发起测试失败:', error)
    window.$message?.error(error?.message || '发起测试失败')
  }
  finally {
    startTestLoading.value = false
  }
}

function handleViewStarted() {
  showStartTestModal.value = false
  router.push('/flow/started')
}

async function handleDeploy(row) {
  window.$dialog?.info({
    title: '确认部署',
    content: `确定要部署「${row.modelName}」吗？部署后流程将可以发起。`,
    positiveText: '确定',
    negativeText: '取消',
    onPositiveClick: async () => {
      const res = await flowApi.deployModel(row.id)
      if (res.code === 200) {
        window.$message?.success('部署成功')
        fetchData()
      }
      else {
        window.$message?.error(res.message || '部署失败')
      }
    },
  })
}

async function handleCopy(row) {
  window.$dialog?.info({
    title: '复制模型',
    content: `确定要复制「${row.modelName}」吗？`,
    positiveText: '确定',
    negativeText: '取消',
    onPositiveClick: async () => {
      const res = await flowApi.copyModel(row.id, `${row.modelName} - 副本`)
      if (res.code === 200) {
        window.$message?.success('复制成功')
        fetchData()
      }
      else {
        window.$message?.error(res.message || '复制失败')
      }
    },
  })
}

async function handleSuspend(row) {
  window.$dialog?.warning({
    title: '确认挂起',
    content: `挂起后，「${row.modelName}」相关的进行中流程实例将暂停，确定继续？`,
    positiveText: '确定',
    negativeText: '取消',
    onPositiveClick: async () => {
      const res = await flowApi.suspendModel(row.id)
      if (res.code === 200) {
        window.$message?.success('已挂起')
        fetchData()
      }
      else {
        window.$message?.error(res.message || '挂起失败')
      }
    },
  })
}

async function handleActivate(row) {
  const res = await flowApi.activateModel(row.id)
  if (res.code === 200) {
    window.$message?.success('已激活')
    fetchData()
  }
  else {
    window.$message?.error(res.message || '激活失败')
  }
}

async function handleDelete(row) {
  window.$dialog?.error({
    title: '确认删除',
    content: `删除「${row.modelName}」前会校验该模型下是否存在流程实例或历史数据；如存在数据，系统会拒绝删除。删除后不可恢复，确定继续？`,
    positiveText: '确定删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        const res = await flowApi.deleteModel(row.id)
        if (res.code === 200) {
          window.$message?.success('删除成功')
          fetchData()
        }
        else {
          window.$message?.error(res.message || '删除失败')
        }
      }
      catch (error) {
        window.$message?.error(error?.message || error?.response?.data?.message || '删除失败')
      }
    },
  })
}

function handleVersionHistory(row) {
  currentModelId.value = row.id
  currentModelVersion.value = row.version
  showVersionHistory.value = true
}

onMounted(() => {
  fetchCategories()
  fetchData()
})
</script>

<style scoped>
.flow-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 18px;
  background: #f5f7fb;
  width: 100%;
  min-width: 0;
  box-sizing: border-box;
  overflow-x: hidden;
}

.page-header {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 14px 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-shrink: 0;
  min-width: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 120px;
}

.title-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.title-icon {
  width: 30px;
  height: 30px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #475569;
  font-size: 17px;
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
  color: #111827;
  margin: 0;
  letter-spacing: 0;
}

.header-right {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  flex: 1;
  min-width: 0;
  flex-wrap: nowrap;
}

.search-input {
  width: 240px;
  max-width: 100%;
}

.category-select {
  width: 148px;
  max-width: 100%;
}

.status-select {
  width: 124px;
  max-width: 100%;
}

.toolbar-actions {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.model-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  align-content: start;
  gap: 10px;
  padding: 2px;
  min-width: 0;
}

.model-card {
  position: relative;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 2px;
  cursor: default;
  transition:
    box-shadow 180ms ease,
    border-color 180ms ease,
    transform 180ms ease;
  display: flex;
  flex-direction: column;
  min-height: 0;
  width: 100%;
  box-sizing: border-box;
  overflow: hidden;
}

.model-card:hover {
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.08);
}

.card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 16px 12px;
  min-width: 0;
}

.card-title-block {
  min-width: 0;
}

.card-title-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  min-width: 0;
}

.card-title-icon-box {
  width: 32px;
  height: 32px;
  border-radius: 2px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: #eff6ff;
  border: 1px solid #dbeafe;
}

.card-title-icon {
  color: #2563eb;
  font-size: 16px;
}

.status-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 20px;
  padding: 0 6px;
  border-radius: 2px;
  font-size: 11px;
  font-weight: 600;
  line-height: 1;
  border: 1px solid transparent;
  white-space: nowrap;
  flex-shrink: 0;
  letter-spacing: 0.01em;
}

.status-tag.designing {
  background: #fffbeb;
  color: #92400e;
  border-color: #fde68a;
}

.status-tag.deployed {
  background: #ecfdf5;
  color: #047857;
  border-color: #bbf7d0;
}

.status-tag.suspended {
  background: #f8fafc;
  color: #475569;
  border-color: #e2e8f0;
}

.status-tag.disabled {
  background: #fef2f2;
  color: #b91c1c;
  border-color: #fecaca;
}

.card-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 0 16px 10px;
}

.card-title {
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  line-height: 20px;
  letter-spacing: 0;
}

.card-key {
  margin-top: 4px;
  font-size: 11px;
  color: #6b7280;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-variant-numeric: tabular-nums;
  letter-spacing: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.card-tags {
  display: flex;
  align-items: center;
  gap: 6px;
  min-height: 0;
  min-width: 0;
  flex-wrap: wrap;
}

.designer-type-badge {
  display: inline-flex;
  align-items: center;
  height: 20px;
  padding: 0 6px;
  border: 1px solid #e2e8f0;
  border-radius: 2px;
  font-size: 11px;
  font-weight: 500;
  line-height: 1;
  color: #475569;
  background: #fff;
}

.designer-type-badge.approval {
  color: #1e3a8a;
  background: #f8fbff;
  border-color: #dbe7ff;
}

.designer-type-badge.business {
  color: #065f46;
  background: #f7fcfa;
  border-color: #d7eee4;
}

.category-badge {
  display: inline-flex;
  max-width: 132px;
  height: 20px;
  align-items: center;
  padding: 0 6px;
  border: 1px solid #e2e8f0;
  border-radius: 2px;
  background: #fff;
  color: #475569;
  font-size: 11px;
  font-weight: 500;
  line-height: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-binding {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-width: 0;
  max-width: 100%;
  width: fit-content;
  color: #6b7280;
  font-size: 12px;
  line-height: 18px;
  padding: 6px 10px;
  border-radius: 2px;
  background: #f9fafb;
  border: 1px solid #f1f5f9;
}

.card-binding.empty {
  color: #94a3b8;
}

.card-binding span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-desc {
  font-size: 12px;
  color: #6b7280;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  line-height: 18px;
  min-height: 18px;
  text-wrap: pretty;
}

.card-metadata {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  flex: 1;
}

.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  border-top: 1px solid #f1f5f9;
  background: #f9fafb;
  min-height: 0;
  min-width: 0;
  gap: 12px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: #64748b;
  white-space: nowrap;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
}

.card-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
  min-width: 0;
  flex-shrink: 0;
}

.card-action-link,
.card-more-action {
  border: 0;
  background: transparent;
  padding: 0;
  font: inherit;
  font-size: 12px;
  font-weight: 500;
  line-height: 1;
  color: #2563eb;
  cursor: pointer;
  transition:
    color 160ms ease,
    transform 160ms ease;
}

.card-action-link:hover,
.card-more-action:hover {
  color: #1d4ed8;
}

.card-action-link:active,
.card-more-action:active {
  transform: translateY(1px);
}

.card-action-separator {
  width: 1px;
  height: 12px;
  background: #d1d5db;
}

.card-more-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #9ca3af;
}

.card-more-action i {
  font-size: 14px;
}

.model-list-loading {
  min-height: 280px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  width: 100%;
}

.empty-state {
  padding: 48px 0;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #e5e7eb;
  width: 100%;
}

.pagination-wrapper {
  display: flex;
  justify-content: end;
  flex-shrink: 0;
  padding-top: 2px;
  max-width: 100%;
  overflow-x: auto;
}

.pagination-wrapper :deep(.n-pagination) {
  min-width: max-content;
}

.designer-type-chooser {
  width: 100%;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.designer-type-chooser.disabled {
  opacity: 0.72;
}

.designer-type-option {
  min-width: 0;
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px;
  border: 1px solid #dbe3ee;
  border-radius: 8px;
  background: #fff;
  color: #334155;
  text-align: left;
  cursor: pointer;
  transition:
    border-color 160ms ease,
    background 160ms ease,
    box-shadow 160ms ease;
}

.designer-type-option:hover:not(:disabled) {
  border-color: #93c5fd;
  background: #f8fbff;
}

.designer-type-option.active {
  border-color: #2563eb;
  background: #eff6ff;
  box-shadow: inset 0 0 0 1px rgba(37, 99, 235, 0.12);
}

.designer-type-option:disabled {
  cursor: not-allowed;
}

.designer-type-icon {
  width: 34px;
  height: 34px;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background: #f1f5f9;
  color: #2563eb;
}

.designer-type-icon i {
  font-size: 20px;
}

.designer-type-main {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.designer-type-title {
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
}

.designer-type-desc {
  font-size: 12px;
  line-height: 1.5;
  color: #64748b;
}

.start-test-modal {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-width: 0;
}

.start-test-summary {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border: 1px solid #dbe3ee;
  border-radius: 8px;
  background: #f8fafc;
}

.summary-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #2563eb;
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  font-size: 20px;
  flex-shrink: 0;
}

.summary-main {
  flex: 1;
  min-width: 0;
}

.summary-title {
  font-size: 15px;
  font-weight: 700;
  color: #0f172a;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.summary-subtitle {
  margin-top: 2px;
  font-size: 12px;
  color: #64748b;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.start-test-alert {
  border-radius: 8px;
}

.radio-group {
  margin-bottom: 8px;
}

.cursor-help {
  cursor: help;
  color: #64748b;
}

:deep(.n-spin-container),
:deep(.n-spin-content) {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.model-list-spin {
  display: flex;
  max-width: 100%;
  min-width: 0;
  overflow-x: auto;
}

.model-list-spin :deep(.n-spin-container) {
  flex: 1;
  min-width: 0;
}

:deep(.flow-design-modal) {
  width: 100vw !important;
  height: 100vh !important;
  max-width: none !important;
  margin: 0 !important;
  padding: 0 !important;
}

.flow-design-modal-shell {
  width: 100vw;
  height: 100vh;
  min-height: 0;
  display: flex;
  overflow: hidden;
  background: #f8fafc;
}

.flow-design-modal-shell :deep(.model-design-page) {
  flex: 1;
  width: 100%;
  height: 100%;
  min-height: 0;
}

@media (max-width: 1500px) {
  .model-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 1180px) {
  .page-header {
    align-items: stretch;
    flex-direction: column;
  }

  .model-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .header-right {
    justify-content: flex-start;
    flex-wrap: wrap;
  }

  .search-input {
    flex: 1 1 220px;
  }

  .category-select {
    flex: 0 1 160px;
  }

  .status-select {
    flex: 0 1 128px;
  }
}

@media (max-width: 760px) {
  .flow-design-modal {
    width: 100vw;
    height: 100vh;
  }

  .flow-page {
    padding: 12px;
  }

  .header-right {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    align-items: stretch;
  }

  .search-input,
  .category-select,
  .status-select {
    width: 100%;
    min-width: 0;
  }

  .toolbar-actions {
    grid-column: 1 / -1;
    justify-content: flex-end;
    width: 100%;
  }

  .model-grid {
    grid-template-columns: 1fr;
    min-width: 0;
  }

  .card-footer {
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
  }

  .card-actions {
    margin-left: auto;
    justify-content: flex-end;
    flex-wrap: wrap;
  }
}

@media (max-width: 480px) {
  .header-right {
    grid-template-columns: 1fr;
  }

  .toolbar-actions {
    justify-content: stretch;
  }

  .toolbar-actions :deep(.n-button) {
    flex: 1;
  }
}
</style>
