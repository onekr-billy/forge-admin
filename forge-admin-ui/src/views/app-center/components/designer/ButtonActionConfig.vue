<template>
  <n-modal
    :show="show"
    preset="card"
    title="配置按钮行为"
    :bordered="false"
    class="button-action-config-modal"
    :style="{ width: 'min(620px, calc(100vw - 32px))' }"
    @update:show="emit('update:show', $event)"
  >
    <n-form label-placement="top">
      <n-form-item label="行为类型" required>
        <n-select
          :value="draft.behaviorType"
          :options="behaviorTypeOptions"
          @update:value="handleBehaviorTypeChange"
        />
      </n-form-item>

      <n-alert
        v-if="draft.behaviorType === 'submit'"
        type="info"
        :bordered="false"
      >
        点击按钮后提交并保存当前表单，无需额外配置。
      </n-alert>

      <n-form-item v-else-if="draft.behaviorType === 'navigate'" label="目标页面" required>
        <n-select
          v-model:value="draft.targetPageKey"
          :options="targetPageOptions"
          filterable
          clearable
          placeholder="请选择应用页面"
        />
      </n-form-item>

      <template v-else>
        <n-form-item
          :label="draft.behaviorType === 'process' ? '业务流程' : '包含动作节点的业务流程'"
          required
        >
          <n-select
            :value="draft.processCode"
            :options="processOptions"
            :loading="loadingProcesses"
            filterable
            clearable
            :placeholder="processPlaceholder"
            @update:value="handleProcessChange"
          >
            <template #action>
              <n-button
                text
                type="primary"
                :loading="creatingProcess"
                data-create-process
                @click="createProcessAndOpenCanvas"
              >
                + 新建业务流程
              </n-button>
            </template>
          </n-select>
        </n-form-item>
        <n-alert
          v-if="processLoadError"
          class="process-load-alert"
          type="warning"
          :bordered="false"
        >
          {{ processLoadError }}
        </n-alert>
        <n-form-item v-if="draft.behaviorType === 'process'" label="权限标识">
          <n-input
            v-model:value="draft.permissionCode"
            clearable
            placeholder="例如：order:submit"
          />
        </n-form-item>
      </template>
    </n-form>

    <template #footer>
      <n-space justify="end">
        <n-button @click="closeConfig">
          取消
        </n-button>
        <n-button type="primary" @click="confirmConfig">
          确认
        </n-button>
      </n-space>
    </template>
  </n-modal>
</template>

<script setup>
import { useMessage } from 'naive-ui'
import { computed, reactive, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { businessApplicationDetailByCode, businessApplicationObjects } from '@/api/business-application'
import {
  businessProcessDetail,
  businessProcessPage,
  createBusinessProcess,
} from '@/api/business-process'
import {
  buildBottomActionConfig,
  normalizeButtonActionDraft,
} from './button-action-config'

const props = defineProps({
  show: {
    type: Boolean,
    default: false,
  },
  modelValue: {
    type: Object,
    default: () => ({}),
  },
  applicationCode: {
    type: String,
    default: '',
  },
  objectCode: {
    type: String,
    default: '',
  },
  pages: {
    type: Array,
    default: () => [],
  },
})

const emit = defineEmits(['update:show', 'confirm', 'createProcess'])
const message = useMessage()
const route = useRoute()
const router = useRouter()
const draft = reactive(normalizeButtonActionDraft(props.modelValue))
const processes = reactive([])
const processState = reactive({
  loading: false,
  creating: false,
  error: '',
  requestId: 0,
})
const loadingProcesses = computed(() => processState.loading)
const creatingProcess = computed(() => processState.creating)
const processLoadError = computed(() => processState.error)

const behaviorTypeOptions = [
  { label: '提交保存', value: 'submit' },
  { label: '跳转页面', value: 'navigate' },
  { label: '启动业务流程', value: 'process' },
  { label: '执行自定义动作', value: 'custom' },
]
const targetPageOptions = computed(() => appendCurrentOption(
  (props.pages || []).map(normalizePageOption).filter(Boolean),
  draft.targetPageKey,
  '已有页面配置',
))
const processOptions = computed(() => appendCurrentOption(
  processes.map(process => ({
    label: `${process.processName || process.processCode}（${process.processCode}）`,
    value: process.processCode,
    processId: stringValue(process.id),
  })).filter(option => option.value),
  draft.processCode,
  '已有流程配置',
))
const processPlaceholder = computed(() => {
  if (!props.applicationCode)
    return '当前页面缺少应用上下文'
  return loadingProcesses.value ? '正在读取已发布流程' : '请选择当前应用的已发布流程'
})

watch(() => props.show, (show) => {
  if (!show)
    return
  Object.assign(draft, normalizeButtonActionDraft(props.modelValue))
  processState.error = ''
  if (['process', 'custom'].includes(draft.behaviorType))
    loadProcesses()
})
watch(() => route.query.processRefresh, () => {
  if (props.show && ['process', 'custom'].includes(draft.behaviorType))
    loadProcesses()
})

function handleBehaviorTypeChange(value) {
  draft.behaviorType = value
  if (['process', 'custom'].includes(value) && !processes.length)
    loadProcesses()
}

async function loadProcesses() {
  if (!props.applicationCode) {
    processState.error = '请从应用工作台进入页面设计器后再选择业务流程。'
    return
  }
  const requestId = ++processState.requestId
  processState.loading = true
  processState.error = ''
  try {
    const applicationRes = await businessApplicationDetailByCode(props.applicationCode)
    const applicationId = stringValue(applicationRes?.data?.id)
    if (!applicationId)
      throw new Error('未找到当前业务应用')
    const processRes = await businessProcessPage({
      pageNum: 1,
      pageSize: 100,
      applicationId,
      status: 1,
      designStatus: 'PUBLISHED',
    })
    const refreshedProcess = await loadRefreshedProcess(applicationId)
    if (requestId !== processState.requestId)
      return
    processes.splice(0, processes.length, ...mergeProcesses(
      unwrapRecords(processRes?.data),
      refreshedProcess ? [refreshedProcess] : [],
    ))
  }
  catch (error) {
    if (requestId !== processState.requestId)
      return
    processes.splice(0, processes.length)
    processState.error = error?.message || '业务流程读取失败'
  }
  finally {
    if (requestId === processState.requestId)
      processState.loading = false
  }
}

function handleProcessChange(processCode) {
  draft.processCode = processCode || ''
  draft.processId = stringValue(processes.find(process => process.processCode === processCode)?.id)
}

async function createProcessAndOpenCanvas() {
  if (processState.creating)
    return
  if (!props.applicationCode) {
    message.warning('当前页面缺少应用上下文，请先返回应用工作台')
    return
  }
  processState.creating = true
  try {
    const applicationRes = await businessApplicationDetailByCode(props.applicationCode)
    const applicationId = stringValue(applicationRes?.data?.id)
    if (!applicationId)
      throw new Error('未找到当前业务应用')
    const objectsRes = await businessApplicationObjects(applicationId)
    const applicationObject = resolveApplicationObject(unwrapRecords(objectsRes?.data), props.objectCode)
    const subjectObjectId = stringValue(applicationObject?.objectId || applicationObject?.id)
    if (!subjectObjectId)
      throw new Error('当前页面对象尚未加入业务应用')
    const buttonLabel = String(props.modelValue?.label || '').trim() || '页面按钮'
    const processName = (buttonLabel.endsWith('流程') ? buttonLabel : `${buttonLabel}流程`).slice(0, 128)
    const response = await createBusinessProcess({
      applicationId,
      processName,
      processDescription: `由页面按钮“${buttonLabel}”创建`,
      subjectObjectId,
      status: 1,
    })
    const createdProcess = response?.data || {}
    const processId = stringValue(createdProcess.id)
    if (!processId)
      throw new Error('业务流程已创建，但未返回流程 ID')
    emit('createProcess', {
      applicationCode: props.applicationCode,
      objectCode: props.objectCode,
      process: createdProcess,
    })
    await router.push({
      name: 'BusinessProcessDesigner',
      params: { processId },
      query: {
        applicationCode: props.applicationCode,
        from: 'button',
        objectCode: props.objectCode || undefined,
        returnTo: route.fullPath,
      },
    })
  }
  catch (error) {
    message.error(error?.response?.data?.message || error?.message || '业务流程创建失败')
  }
  finally {
    processState.creating = false
  }
}

function confirmConfig() {
  if (draft.behaviorType === 'navigate' && !draft.targetPageKey) {
    message.warning('请选择目标页面')
    return
  }
  if (['process', 'custom'].includes(draft.behaviorType) && !draft.processCode) {
    message.warning('请选择业务流程')
    return
  }
  emit('confirm', buildBottomActionConfig(props.modelValue, draft))
  closeConfig()
}

function closeConfig() {
  emit('update:show', false)
}

function normalizePageOption(page = {}) {
  const value = stringValue(page.pageKey || page.id || page.key)
  if (!value)
    return null
  const title = page.title || page.pageName || page.name || value
  return { label: `${title}（${value}）`, value }
}

function appendCurrentOption(options = [], value = '', suffix = '') {
  if (!value || options.some(option => option.value === value))
    return options
  return [...options, { label: `${value}（${suffix}）`, value, disabled: true }]
}

function unwrapRecords(value) {
  if (Array.isArray(value))
    return value
  if (Array.isArray(value?.records))
    return value.records
  return []
}

async function loadRefreshedProcess(applicationId) {
  const processId = stringValue(route.query.processRefresh)
  if (!processId)
    return null
  try {
    const response = await businessProcessDetail(processId)
    const process = response?.data || null
    const sameApplication = stringValue(process?.applicationId) === applicationId
    const sameObject = !props.objectCode || process?.subjectObjectCode === props.objectCode
    return sameApplication && sameObject ? process : null
  }
  catch {
    return null
  }
}

function mergeProcesses(...groups) {
  const records = new Map()
  groups.flat().forEach((process) => {
    const key = stringValue(process?.id || process?.processCode)
    if (key)
      records.set(key, process)
  })
  return [...records.values()]
}

function resolveApplicationObject(objects = [], objectCode = '') {
  const normalizedCode = String(objectCode || '').trim()
  if (normalizedCode) {
    return objects.find(item => String(item?.objectCode || '').trim() === normalizedCode) || null
  }
  return objects.find(item => String(item?.objectRole || '').toUpperCase() === 'PRIMARY') || objects[0] || null
}

function stringValue(value) {
  return value == null ? '' : String(value)
}
</script>

<style scoped>
.process-load-alert {
  margin: -4px 0 14px;
}
</style>
