<script setup>
/**
 * ApproverConfig — 审批人节点完整配置
 *
 * 内部用 NTabs 切换：
 *   - basic        审批办理（assignee / candidates / 会签）
 *   - multi        会签设置（multiInstance + completionCondition）
 *   - form         表单资产和字段权限（formKey / formFieldPermissions 列表）
 *   - permissions  审批操作权限（7 个布尔开关）
 *   - extensions   逾期提醒 / 任务监听器 / 执行监听器
 *
 * 字段 1:1 迁移自 NodePropertiesPanel.vue:1562-2200，但用更扁平的 emit('update:config') 通信。
 *
 * Props:
 *   - node      flowJson 节点
 *   - readonly
 *
 * Events:
 *   - update:config   增量 patch，外层 NodeConfigDrawer 合并到 draftNode.config
 */
import { computed, ref } from 'vue'
import { normalizeFieldPermissions } from '@/utils/field-permissions'
import BusinessFlowFormAssetSelect from '@/views/app-center/components/designer/BusinessFlowFormAssetSelect.vue'
import ApproverAssigneeForm from './ApproverAssigneeForm.vue'
import BasicConfig from './BasicConfig.vue'
import FormPermissionConfig from './FormPermissionConfig.vue'
import ListenerConfig from './ListenerConfig.vue'
import MultiInstanceConfig from './MultiInstanceConfig.vue'
import OverdueReminderConfig from './OverdueReminderConfig.vue'
import PermissionConfig from './PermissionConfig.vue'

const props = defineProps({
  node: { type: Object, required: true },
  formAssetOptions: { type: Array, default: () => [] },
  formFieldCatalog: { type: Array, default: () => [] },
  readonly: Boolean,
})

const emit = defineEmits(['update:config', 'update:node'])

const tab = ref('basic')

const config = computed(() => props.node?.config || {})
const normalizedFormAssetOptions = computed(() => (props.formAssetOptions || [])
  .map((item) => {
    const value = String(item?.value || item?.formKey || item?.key || '').trim()
    if (!value)
      return null
    return {
      ...item,
      value,
      formKey: item?.formKey || value,
      formName: item?.formName || item?.label || value,
      label: item?.label || `${item?.formName || value}（${value}）`,
      formMode: item?.formMode || item?.type || 'BUSINESS_OBJECT_FORM',
      providerKey: item?.providerKey || '',
      providerName: item?.providerName || '',
      objectName: item?.objectName || '',
      sourceType: item?.sourceType || item?.source || '',
      fieldCount: item?.fieldCount,
      fieldPreview: item?.fieldPreview || [],
      fieldCatalog: Array.isArray(item?.fieldCatalog) ? item.fieldCatalog : [],
    }
  })
  .filter(Boolean))
const selectedFormAsset = computed(() => {
  const formKey = String(config.value.formKey || '').trim()
  if (!formKey)
    return null
  return findFormAsset({
    formMode: config.value.formMode,
    formKey,
    providerKey: config.value.providerKey,
  }) || null
})
const activeFormFieldCatalog = computed(() => {
  if (selectedFormAsset.value?.fieldCatalog?.length)
    return selectedFormAsset.value.fieldCatalog
  return props.formFieldCatalog
})
const nodeFormForAssetSelect = computed(() => ({
  formMode: config.value.formMode
    || selectedFormAsset.value?.formMode
    || normalizedFormAssetOptions.value[0]?.formMode
    || 'BUSINESS_OBJECT_FORM',
  formKey: config.value.formKey || '',
  formName: config.value.formName || selectedFormAsset.value?.formName || '',
  providerKey: config.value.providerKey || selectedFormAsset.value?.providerKey || '',
  formUrl: config.value.formUrl || selectedFormAsset.value?.formUrl || '',
  viewKey: config.value.viewKey || selectedFormAsset.value?.viewKey || 'default',
  formRef: config.value.formRef || selectedFormAsset.value?.formRef || {},
}))

function patch(part) {
  emit('update:config', part)
}

function updateNode(node) {
  emit('update:node', node)
}

function handleFormAssetUpdate(partial = {}) {
  if (!partial.formKey) {
    patch({
      formType: 'none',
      formMode: '',
      formKey: '',
      formName: '',
      providerKey: '',
      formJson: '',
      formUrl: '',
      formRef: {},
      formFieldPermissions: [],
    })
    return
  }
  const asset = findFormAsset(partial)
  const formMode = partial.formMode || asset?.formMode || asset?.type || 'BUSINESS_OBJECT_FORM'
  patch({
    formType: 'dynamic',
    formMode,
    formKey: partial.formKey || '',
    formName: partial.formName || asset?.formName || partial.formKey || '',
    providerKey: partial.providerKey || asset?.providerKey || '',
    formJson: '',
    formUrl: partial.formUrl || asset?.formUrl || '',
    viewKey: partial.viewKey || asset?.viewKey || 'default',
    formRef: partial.formRef || asset?.formRef || buildFormRefFromAsset(asset, formMode),
    formFieldPermissions: buildFormFieldPermissionsForCatalog(
      config.value.formFieldPermissions,
      asset?.fieldCatalog,
    ),
  })
}

function findFormAsset(partial = {}) {
  const formKey = String(partial.formKey || '').trim()
  if (!formKey)
    return null
  const providerKey = String(partial.providerKey || '')
  const formMode = normalizeFormMode(partial.formMode || partial.formRef?.type || config.value.formMode)
  const exactAsset = normalizedFormAssetOptions.value.find((asset) => {
    return String(asset.formKey || asset.value || '') === formKey
      && normalizeFormMode(asset.formMode || asset.type) === formMode
      && String(asset.providerKey || '') === providerKey
  })
  if (exactAsset)
    return exactAsset
  const providerAsset = normalizedFormAssetOptions.value.find(asset =>
    String(asset.formKey || asset.value || '') === formKey
    && String(asset.providerKey || '') === providerKey,
  )
  if (providerAsset)
    return providerAsset
  return normalizedFormAssetOptions.value.find(asset => String(asset.formKey || asset.value || '') === formKey) || null
}

function buildFormFieldPermissionsForCatalog(currentPermissions, fieldCatalog = []) {
  const current = new Map()
  for (const permission of normalizeFieldPermissions(currentPermissions)) {
    if (permission.field)
      current.set(permission.field, permission)
  }
  const catalog = normalizeFieldCatalog(fieldCatalog)
  if (!catalog.length)
    return Array.from(current.values())
  return catalog.map((field) => {
    const saved = current.get(field.field)
    if (saved) {
      return {
        ...saved,
        label: field.label || saved.label || field.field,
      }
    }
    const required = field.required === true
    return {
      field: field.field,
      fieldCode: field.field,
      label: field.label || field.field,
      visible: true,
      editable: true,
      readable: true,
      writable: true,
      required,
    }
  })
}

function normalizeFieldCatalog(fieldCatalog = []) {
  const seen = new Set()
  return (Array.isArray(fieldCatalog) ? fieldCatalog : [])
    .map((item) => {
      const field = String(item?.field || item?.fieldCode || item?.fieldName || item?.name || item?.key || '').trim()
      if (!field || seen.has(field))
        return null
      seen.add(field)
      return {
        field,
        label: String(item?.label || item?.title || item?.fieldName || field).trim(),
        required: item?.required === true || item?.sourceRequired === true,
      }
    })
    .filter(Boolean)
}

function normalizeFormMode(value) {
  const normalized = String(value || 'BUSINESS_OBJECT_FORM').toUpperCase()
  if (normalized === 'BUSINESS_CODE_FORM' || normalized === 'EXTERNAL')
    return normalized
  return 'BUSINESS_OBJECT_FORM'
}

function buildFormRefFromAsset(asset, formMode) {
  if (!asset?.formKey)
    return {}
  return {
    type: formMode,
    formMode,
    objectCode: asset.objectCode || '',
    objectName: asset.objectName || '',
    applicationId: asset.applicationId || '',
    pageId: asset.pageId || '',
    pageCode: asset.pageCode || '',
    pageName: asset.pageName || '',
    pageType: asset.pageType || '',
    sourceFormKey: asset.sourceFormKey || '',
    formKey: asset.formKey,
    formName: asset.formName || asset.formKey,
    providerKey: asset.providerKey || '',
    formUrl: asset.formUrl || '',
    viewKey: asset.viewKey || 'default',
  }
}
</script>

<template>
  <div class="approver-config">
    <n-tabs v-model:value="tab" type="line" size="large" animated>
      <n-tab-pane name="basic" tab="审批办理">
        <BasicConfig
          :node="node"
          :readonly="readonly"
          @update:node="updateNode"
        />
        <ApproverAssigneeForm
          :config="config"
          :readonly="readonly"
          @update:config="patch"
        />
        <div class="config-section-block">
          <div class="config-section-title">
            多人审批方式
          </div>
          <MultiInstanceConfig
            :config="config"
            :readonly="readonly"
            @update:config="patch"
          />
        </div>
      </n-tab-pane>
      <n-tab-pane name="form" tab="表单权限">
        <div class="config-section-block">
          <div class="config-section-title">
            节点表单资产
          </div>
          <div class="node-form-asset-block">
            <BusinessFlowFormAssetSelect
              :node-form="nodeFormForAssetSelect"
              :form-assets="normalizedFormAssetOptions"
              :disabled="readonly"
              show-all-modes
              @update="handleFormAssetUpdate"
            />
            <n-tag size="small" :type="config.formKey ? 'success' : 'default'" :bordered="false">
              {{ config.formKey ? '已绑定' : '未绑定' }}
            </n-tag>
          </div>
          <div class="node-form-asset-hint">
            运行时按节点 formKey 加载表单资产，并按下方字段权限控制该节点可见、可编辑和必填字段。
          </div>
        </div>
        <FormPermissionConfig
          :config="config"
          :form-field-catalog="activeFormFieldCatalog"
          :readonly="readonly"
          @update:config="patch"
        />
      </n-tab-pane>
      <n-tab-pane name="permissions" tab="审批权限">
        <div class="config-section-block">
          <div class="config-section-title">
            审批操作权限
          </div>
          <PermissionConfig
            :config="config"
            :readonly="readonly"
            @update:config="patch"
          />
        </div>
      </n-tab-pane>
      <n-tab-pane name="extensions" tab="扩展配置">
        <OverdueReminderConfig
          :config="config"
          :readonly="readonly"
          @update:config="patch"
        />
        <div class="config-section-block">
          <div class="config-section-title">
            监听器
          </div>
          <ListenerConfig
            :config="config"
            :readonly="readonly"
            @update:config="patch"
          />
        </div>
      </n-tab-pane>
    </n-tabs>
  </div>
</template>

<style scoped>
.node-form-asset-block {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 10px;
}

.node-form-asset-hint {
  margin-top: 8px;
  color: #64748b;
  font-size: 12px;
  line-height: 18px;
}
</style>
