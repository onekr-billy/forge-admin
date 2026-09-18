<template>
  <section class="panel-item sub-table-panel">
    <!-- 子表列表 -->
    <div v-if="subTables.length" class="sub-table-list">
      <div
        v-for="item in subTableCards"
        :key="item.id"
        class="subtable-card"
      >
        <div class="subtable-card-header">
          <div class="subtable-card-title">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none">
              <path d="M3 3h18v18H3V3zm2 2v14h14V5H5zm2 2h10v2H7V7zm0 4h10v2H7v-2zm0 4h6v2H7v-2z" fill="currentColor" />
            </svg>
            <span>{{ item.header || '关联子表' }}</span>
            <n-tag size="tiny" :bordered="false" :type="item.hasRelation ? 'success' : 'warning'">
              {{ item.hasRelation ? '已关联' : '待配置' }}
            </n-tag>
          </div>
          <div class="subtable-card-actions">
            <n-button size="tiny" quaternary @click="editSubTable(item)">
              编辑
            </n-button>
            <n-button size="tiny" quaternary @click="locateOnCanvas(item.id)">
              定位
            </n-button>
            <n-button size="tiny" quaternary type="error" @click="removeSubTable(item)">
              删除
            </n-button>
          </div>
        </div>

        <div class="subtable-card-body">
          <div class="subtable-meta-row">
            <span class="meta-label">目标对象</span>
            <span class="meta-value">{{ item.modelCode || '—' }}</span>
          </div>
          <div class="subtable-meta-row">
            <span class="meta-label">展示方式</span>
            <span class="meta-value">{{ displayModeLabel(item.displayMode) }}</span>
          </div>
          <div v-if="item.fieldCount > 0" class="subtable-meta-row">
            <span class="meta-label">可见字段</span>
            <span class="meta-value">{{ item.fieldCount }} 个</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 空状态 -->
    <div v-else class="sub-table-empty">
      <svg width="32" height="32" viewBox="0 0 24 24" fill="none">
        <path d="M3 3h18v18H3V3zm2 2v14h14V5H5zm2 4h10v2H7V9zm0 4h6v2H7v-2z" fill="currentColor" opacity="0.3" />
      </svg>
      <div class="empty-text">
        <strong>暂无关联子表</strong>
        <span>添加子表后可在主表单中同时录入主表与子表数据，如采购订单 + 明细行。</span>
      </div>
    </div>

    <!-- 添加按钮 -->
    <n-button
      size="small"
      type="primary"
      secondary
      block
      class="add-subtable-btn"
      @click="openAddDialog"
    >
      + 添加关联子表
    </n-button>

    <!-- 配置弹窗 -->
    <SubTableConfigDialog
      v-model:show="dialogVisible"
      :relations="relations"
      :object-code="objectCode"
      :edit-config="editingConfig"
      @confirm="handleDialogConfirm"
    />
  </section>
</template>

<script setup>
import { computed, inject, ref } from 'vue'
import { useFormDesignerStore } from '@/store'
import {
  resolveChildTableRelationKey,
  safeKey,
} from '../../child-table-section-config'
import SubTableConfigDialog from './SubTableConfigDialog.vue'

/**
 * 主子表配置面板（一站式改造）
 *
 * - 通过 provide/inject 桥接 BusinessFormDesigner 的 pageSchema 同步
 * - 添加/编辑走 SubTableConfigDialog 弹窗
 * - 删除同时清理三处配置（组件、modelRef、masterDetailConfig）
 */
const designerStore = useFormDesignerStore()
const pageSchemaBridge = inject('subTableConfigBridge', null)

const dialogVisible = ref(false)
const editingConfig = ref(null)
let editingComponentId = ''

const subTables = computed(() => designerStore.subTableComponents)
const relations = computed(() => designerStore.relations || [])
const objectCode = computed(() => designerStore.objectCode || '')

const subTableCards = computed(() => {
  return subTables.value.map((component) => {
    const props = component.props || {}
    const relationKey = props.relationKey || ''
    const matched = relations.value.find(r =>
      resolveChildTableRelationKey(r) === relationKey
      || r.targetObjectCode === relationKey,
    )
    const fieldCount = Array.isArray(props.columns) ? props.columns.length : 0
    return {
      id: component.id,
      header: props.header || component.label || '关联子表',
      relationKey,
      modelCode: props.modelCode || matched?.targetObjectCode || relationKey,
      displayMode: props.displayMode || 'inline_grid',
      fieldCount,
      hasRelation: Boolean(matched || props.modelCode),
    }
  })
})

function displayModeLabel(mode = '') {
  const map = { inline_grid: '行内表格', card_list: '卡片列表', bottom_sheet: '底部抽屉' }
  return map[mode] || mode || '行内表格'
}

function openAddDialog() {
  editingConfig.value = null
  editingComponentId = ''
  dialogVisible.value = true
}

function editSubTable(item) {
  editingComponentId = item.id
  // Build edit config from current state
  const component = subTables.value.find(c => c.id === item.id)
  if (!component)
    return
  const props = component.props || {}
  editingConfig.value = {
    relationKey: props.relationKey || '',
    title: props.header || component.label || '',
    displayMode: props.displayMode || 'inline_grid',
    modelCode: props.modelCode || '',
    fieldCodes: Array.isArray(props.columns) ? props.columns.map(c => c.fieldCode || c) : [],
    allowCreate: props.allowCreate !== false,
    allowSelectExisting: props.allowSelectExisting === true,
  }
  dialogVisible.value = true
}

function locateOnCanvas(componentId) {
  designerStore.selectComponent(componentId)
}

function removeSubTable(item) {
  const relationKey = item.relationKey
  // Remove from formDesignerSchema.components (via store)
  designerStore.removeComponent(item.id)

  // Also clean up pageSchema via bridge
  if (relationKey && pageSchemaBridge?.syncSubTableConfig) {
    pageSchemaBridge.syncSubTableConfig({
      action: 'remove',
      relationKey,
      componentId: item.id,
    })
  }
}

function handleDialogConfirm(config) {
  if (pageSchemaBridge?.syncSubTableConfig) {
    // Use bridge for full three-way sync
    pageSchemaBridge.syncSubTableConfig({
      action: editingComponentId ? 'update' : 'add',
      config,
      componentId: editingComponentId,
    })
  }
  else {
    // Fallback: only update formDesignerSchema component
    fallbackUpdateComponent(config)
  }
}

function fallbackUpdateComponent(config) {
  const relationKey = config.relationKey || config.modelCode || ''
  const header = config.title || '关联子表'
  const componentId = editingComponentId
    || `subtable_${safeKey(relationKey)}`

  const component = {
    id: componentId,
    componentKey: 'subTable',
    label: header,
    props: {
      header,
      relationKey,
      modelCode: config.modelCode || '',
      displayMode: config.displayMode || 'inline_grid',
      columns: config.fields.map(f => ({
        fieldCode: f.fieldCode || f.sourceField || f.field || '',
        fieldLabel: f.fieldName || f.label || f.fieldCode || '',
      })),
      allowCreate: config.allowCreate !== false,
      allowSelectExisting: config.allowSelectExisting === true,
    },
    layout: { span: designerStore.schema.layout?.gridColumns || 2, align: 'left' },
  }

  if (editingComponentId) {
    designerStore.updateComponent(editingComponentId, {
      label: header,
      props: component.props,
    })
  }
  else {
    designerStore.insertComponent(
      { parentId: '', index: designerStore.schema.components?.length || 0 },
      component,
    )
  }
}
</script>

<style scoped>
.sub-table-panel {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.sub-table-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.subtable-card {
  border: 1px solid var(--border-light, #e5e7eb);
  border-radius: 6px;
  background: var(--bg-primary, #fff);
  overflow: hidden;
}

.subtable-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 10px;
  border-bottom: 1px solid var(--border-light, #e5e7eb);
  background: var(--bg-secondary, #f9fafb);
}

.subtable-card-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary, #1f2937);
}

.subtable-card-title svg {
  flex-shrink: 0;
  color: var(--primary-color, #2080f0);
}

.subtable-card-actions {
  display: flex;
  gap: 2px;
}

.subtable-card-body {
  padding: 8px 10px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.subtable-meta-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  line-height: 1.5;
}

.meta-label {
  flex-shrink: 0;
  width: 52px;
  color: var(--text-tertiary, #86909c);
}

.meta-value {
  color: var(--text-secondary, #4b5563);
}

.sub-table-empty {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 16px 14px;
  border: 1px dashed var(--border-light, #e5e7eb);
  border-radius: 6px;
}

.sub-table-empty svg {
  flex-shrink: 0;
  color: var(--text-tertiary, #86909c);
  margin-top: 2px;
}

.empty-text {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.empty-text strong {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary, #1f2937);
}

.empty-text span {
  font-size: 12px;
  color: var(--text-tertiary, #86909c);
  line-height: 1.5;
}

.add-subtable-btn {
  margin-top: 2px;
}
</style>
