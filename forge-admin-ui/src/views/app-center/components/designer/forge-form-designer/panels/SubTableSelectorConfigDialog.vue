<template>
  <n-modal
    :show="show"
    preset="card"
    title="选择器设置"
    :style="{ width: 'min(640px, calc(100vw - 32px))' }"
    :mask-closable="false"
    @update:show="emit('update:show', $event)"
  >
    <!-- 选择方式 -->
    <div class="selector-section">
      <label class="section-label">选择方式</label>
      <n-radio-group :value="draftMultiple ? 'multi' : 'single'" size="small" @update:value="draftMultiple = $event === 'multi'">
        <n-radio-button value="single">
          单选
        </n-radio-button>
        <n-radio-button value="multi">
          多选
        </n-radio-button>
      </n-radio-group>
    </div>

    <!-- 展示列 -->
    <div class="selector-section">
      <div class="section-label-row">
        <label class="section-label">展示列</label>
        <n-checkbox
          :checked="allDisplaySelected"
          :indeterminate="someDisplaySelected"
          size="small"
          @update:checked="toggleAllDisplay"
        >
          全选
        </n-checkbox>
      </div>
      <n-spin :show="loading" size="small">
        <n-checkbox-group
          v-if="fieldOptions.length"
          v-model:value="draftDisplayFields"
          class="field-checkbox-grid"
        >
          <n-checkbox
            v-for="field in fieldOptions"
            :key="field.value"
            :value="field.value"
            size="small"
          >
            <span class="field-checkbox-copy">
              <strong>{{ field.label }}</strong>
              <small>{{ field.value }}</small>
            </span>
          </n-checkbox>
        </n-checkbox-group>
        <n-empty
          v-else
          size="small"
          description="未读取到字段"
        />
      </n-spin>
    </div>

    <!-- 筛选字段 -->
    <div class="selector-section">
      <div class="section-label-row">
        <label class="section-label">筛选字段</label>
        <a v-if="!draftFilters.length" class="filter-add-link" @click="addFilter()">+ 添加</a>
      </div>
      <div v-if="draftFilters.length" class="filter-list">
        <div v-for="(row, index) in draftFilters" :key="index" class="filter-card">
          <div class="filter-card-head">
            <n-select
              :value="row.fieldCode || null"
              :options="fieldOptions"
              size="small"
              filterable
              placeholder="选择筛选字段"
              class="filter-field-select"
              @update:value="updateFilterField(index, $event)"
            />
            <n-button size="tiny" quaternary type="error" @click="removeFilter(index)">
              删除
            </n-button>
          </div>
          <div class="filter-card-default">
            <span class="filter-default-label">默认值</span>
            <n-select
              :value="row.defaultType || 'none'"
              :options="defaultValueTypeOptions"
              size="small"
              class="filter-type-select"
              @update:value="updateFilter(index, { defaultType: $event, defaultValue: '', formField: '' })"
            />
            <n-input
              v-if="row.defaultType === 'fixed'"
              :value="row.defaultValue || ''"
              size="small"
              placeholder="输入默认值"
              class="filter-default-input"
              @update:value="updateFilter(index, { defaultValue: $event })"
            />
            <n-select
              v-else-if="row.defaultType === 'form'"
              :value="row.formField || null"
              :options="mainFieldOptions"
              size="small"
              filterable
              placeholder="选择主表字段"
              class="filter-default-input"
              @update:value="updateFilter(index, { formField: $event })"
            />
          </div>
        </div>
        <a class="filter-add-link" @click="addFilter()">+ 添加筛选字段</a>
      </div>
      <div v-else class="section-hint">
        配置选择器弹窗顶部的筛选条件，可带默认值联动主表字段
      </div>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <span class="footer-spacer" />
        <n-button size="small" @click="emit('update:show', false)">
          取消
        </n-button>
        <n-button size="small" type="primary" @click="confirm">
          确定
        </n-button>
      </div>
    </template>
  </n-modal>
</template>

<script setup>
import { computed, ref, watch } from 'vue'

const props = defineProps({
  show: {
    type: Boolean,
    default: false,
  },
  /** 子表对象字段选项 [{ label, value }] */
  fieldOptions: {
    type: Array,
    default: () => [],
  },
  /** 主表字段选项（默认值联动来源） */
  mainFieldOptions: {
    type: Array,
    default: () => [],
  },
  multiple: {
    type: Boolean,
    default: true,
  },
  displayFields: {
    type: Array,
    default: () => [],
  },
  filterFields: {
    type: Array,
    default: () => [],
  },
  loading: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['update:show', 'confirm'])

const defaultValueTypeOptions = [
  { label: '不设默认', value: 'none' },
  { label: '固定值', value: 'fixed' },
  { label: '主表字段', value: 'form' },
]

const draftMultiple = ref(true)
const draftDisplayFields = ref([])
const draftFilters = ref([])

watch(() => props.show, (visible) => {
  if (visible) {
    draftMultiple.value = props.multiple !== false
    draftDisplayFields.value = [...props.displayFields]
    draftFilters.value = (props.filterFields || []).map(row => ({ ...row }))
  }
})

const allDisplaySelected = computed(() =>
  Boolean(props.fieldOptions.length) && draftDisplayFields.value.length === props.fieldOptions.length,
)
const someDisplaySelected = computed(() =>
  draftDisplayFields.value.length > 0 && draftDisplayFields.value.length < props.fieldOptions.length,
)

function toggleAllDisplay(checked) {
  draftDisplayFields.value = checked ? props.fieldOptions.map(o => o.value).filter(Boolean) : []
}

function addFilter() {
  draftFilters.value.push({ fieldCode: '', fieldLabel: '', defaultType: 'none', defaultValue: '', formField: '' })
}

function updateFilterField(index, fieldCode) {
  const field = props.fieldOptions.find(f => f.value === fieldCode)
  updateFilter(index, { fieldCode, fieldLabel: field ? field.label : fieldCode })
}

function updateFilter(index, patch) {
  draftFilters.value = draftFilters.value.map((row, i) => (i === index ? { ...row, ...patch } : row))
}

function removeFilter(index) {
  draftFilters.value = draftFilters.value.filter((_, i) => i !== index)
}

function confirm() {
  emit('confirm', {
    multiple: draftMultiple.value,
    displayFields: [...draftDisplayFields.value],
    filterFields: draftFilters.value.map(row => ({ ...row })),
  })
  emit('update:show', false)
}
</script>

<style scoped>
.selector-section {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 16px;
}

.selector-section:last-of-type {
  margin-bottom: 0;
}

.section-label {
  font-size: 12px;
  font-weight: 500;
  color: var(--text-secondary, #4b5563);
  line-height: 1.4;
}

.section-label-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.section-hint {
  font-size: 11px;
  color: var(--text-tertiary, #86909c);
  line-height: 1.5;
}

.field-checkbox-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px 10px;
  max-height: 220px;
  overflow-y: auto;
  padding: 6px 8px;
  border: 1px solid #ececf0;
  border-radius: 6px;
  background: #fff;
}

.field-checkbox-copy {
  display: inline-flex;
  flex-direction: column;
  gap: 1px;
  margin-left: 2px;
  min-width: 0;
}

.field-checkbox-copy strong {
  color: var(--text-primary, #1f2937);
  font-size: 12px;
  font-weight: 500;
  line-height: 1.4;
}

.field-checkbox-copy small {
  color: var(--text-tertiary, #86909c);
  font-size: 10px;
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.filter-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.filter-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 8px;
  border: 1px solid #ececf0;
  border-radius: 6px;
  background: #fff;
}

.filter-card-head {
  display: flex;
  align-items: center;
  gap: 6px;
}

.filter-field-select {
  flex: 1;
  min-width: 0;
}

.filter-card-default {
  display: flex;
  align-items: center;
  gap: 6px;
  padding-left: 2px;
}

.filter-default-label {
  flex-shrink: 0;
  font-size: 11px;
  color: var(--text-tertiary, #86909c);
}

.filter-type-select {
  width: 96px;
  flex-shrink: 0;
}

.filter-default-input {
  flex: 1;
  min-width: 0;
}

.filter-add-link {
  font-size: 12px;
  color: var(--primary-color, #2080f0);
  cursor: pointer;
  user-select: none;
  line-height: 1.6;
}

.filter-add-link:hover {
  opacity: 0.8;
}

.dialog-footer {
  display: flex;
  align-items: center;
  gap: 8px;
}

.footer-spacer {
  flex: 1;
}

@media (max-width: 600px) {
  .field-checkbox-grid {
    grid-template-columns: 1fr;
  }
}
</style>
