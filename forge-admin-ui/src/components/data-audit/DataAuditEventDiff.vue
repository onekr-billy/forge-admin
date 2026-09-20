<template>
  <div class="data-audit-diff">
    <n-spin :show="loading">
      <n-empty v-if="!loading && !fields.length" :description="emptyText" />
      <div v-else class="diff-content">
        <section v-for="group in fieldGroups" :key="group.key" class="diff-section">
          <div class="diff-section-title">
            <strong>{{ group.label }}</strong>
            <span>{{ group.total }} 项</span>
          </div>
          <div class="diff-table">
            <div class="diff-head">
              <span>字段</span>
              <span>{{ headings.before }}</span>
              <span>{{ headings.after }}</span>
            </div>
            <div v-for="field in group.fields" :key="field.id" class="diff-row">
              <div class="diff-field">
                <strong>{{ field.fieldLabel || field.fieldCode }}</strong>
                <span>{{ fieldIdentityText(field, group.key) }}</span>
                <DictTag
                  v-if="field.sourceType"
                  :options="sourceOptions"
                  :value="field.sourceType"
                  size="small"
                />
              </div>
              <div class="diff-value diff-value-before">
                {{ displayText(field.before) }}
              </div>
              <div class="diff-value diff-value-after">
                {{ displayText(field.after) }}
                <n-button
                  v-if="field.canReveal"
                  text
                  type="primary"
                  size="tiny"
                  @click="$emit('reveal', field)"
                >
                  查看原值
                </n-button>
              </div>
            </div>
          </div>
        </section>
        <div v-if="fields.length > previewLimit" class="diff-more">
          <n-button text type="primary" size="tiny" @click="$emit('toggle')">
            {{ expanded ? '收起字段变化' : `展开全部 ${fields.length} 项变化` }}
          </n-button>
        </div>
      </div>
    </n-spin>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import DictTag from '@/components/DictTag.vue'
import { useDict } from '@/composables'
import {
  auditDiffHeadings,
  DEFAULT_AUDIT_DIFF_LIMIT,
  groupAuditFields,
} from './data-audit-display'

const props = defineProps({
  fields: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  emptyText: { type: String, default: '没有可见的字段变化' },
  eventType: { type: String, default: 'UPDATE' },
  expanded: { type: Boolean, default: false },
  previewLimit: { type: Number, default: DEFAULT_AUDIT_DIFF_LIMIT },
})

defineEmits(['reveal', 'toggle'])

const { dict } = useDict('sys_data_audit_source_type')
const sourceOptions = computed(() => dict.value.sys_data_audit_source_type || [])
const headings = computed(() => auditDiffHeadings(props.eventType))
const fieldGroups = computed(() => groupAuditFields(props.fields, props.expanded, props.previewLimit))

function locationText(field) {
  if (field.fieldType === 'CHILD_SUMMARY' || field.fieldCode === '__childRows')
    return field.relationKey ? `子表 ${field.relationKey}` : '子表'
  if (field.relationKey)
    return `子表 ${field.relationKey} / 行 ${field.targetRecordId}`
  return '主表'
}

function fieldIdentityText(field, groupKey) {
  if (groupKey === 'main')
    return field.fieldCode || field.columnName || '主表字段'
  return locationText(field)
}

function displayText(view) {
  if (!view)
    return '-'
  if (view.omitted || view.state === 'OMITTED')
    return '仅记录变更'
  if (view.protectedValue && view.state === 'VALUE')
    return view.display || '已脱敏'
  if (view.state === 'ABSENT')
    return '不存在'
  if (view.state === 'NULL')
    return '空值'
  if (view.display)
    return view.display
  if (view.value === undefined || view.value === null)
    return '-'
  return String(view.value)
}
</script>

<style scoped>
.data-audit-diff {
  min-height: 120px;
}

.diff-content,
.diff-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.diff-section + .diff-section {
  margin-top: 4px;
}

.diff-section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--text-primary, #111827);
  font-size: 13px;
}

.diff-section-title span {
  color: var(--text-tertiary, #64748b);
  font-size: 12px;
}

.diff-table {
  border: 1px solid var(--border-light, #e5e7eb);
  border-radius: 6px;
  overflow: hidden;
}

.diff-head,
.diff-row {
  display: grid;
  grid-template-columns: minmax(160px, 1.1fr) 1fr 1fr;
  gap: 8px;
  padding: 8px 10px;
  font-size: 13px;
}

.diff-head {
  background: var(--gray-100, #f6f8fb);
  color: var(--text-tertiary, #64748b);
  font-weight: 600;
}

.diff-row + .diff-row {
  border-top: 1px solid var(--border-light, #e5e7eb);
}

.diff-field {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.diff-field strong {
  color: var(--text-primary, #111827);
}

.diff-field span {
  color: var(--text-tertiary, #64748b);
  font-size: 12px;
}

.diff-value {
  padding: 5px 7px;
  border-radius: 4px;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--text-primary, #111827);
}

.diff-value-before {
  background: var(--gray-50, #fafbfc);
  color: var(--text-secondary, #475569);
}

.diff-value-after {
  background: color-mix(in srgb, var(--primary-color, #165dff) 7%, transparent);
}

.diff-more {
  display: flex;
  justify-content: center;
  padding: 2px 10px 0;
}
</style>
