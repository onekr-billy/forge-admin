<template>
  <n-modal
    :show="show"
    preset="card"
    :title="title"
    :style="{ width: 'min(520px, calc(100vw - 32px))' }"
    :mask-closable="false"
    @update:show="emit('update:show', $event)"
  >
    <div class="picker-toolbar">
      <n-checkbox
        :checked="allSelected"
        :indeterminate="someSelected"
        size="small"
        @update:checked="toggleAll"
      >
        全选
      </n-checkbox>
      <span class="picker-count">已选 {{ draft.length }} / {{ options.length }} 项</span>
    </div>
    <n-spin :show="loading" size="small">
      <n-checkbox-group
        v-if="options.length"
        v-model:value="draft"
        class="field-checkbox-grid"
      >
        <n-checkbox
          v-for="field in options"
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
  title: {
    type: String,
    default: '选择字段',
  },
  /** 可选字段列表 [{ label, value }] */
  options: {
    type: Array,
    default: () => [],
  },
  /** 已选字段编码 */
  modelValue: {
    type: Array,
    default: () => [],
  },
  loading: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['update:show', 'confirm'])

const draft = ref([])

watch(() => props.show, (visible) => {
  if (visible)
    draft.value = [...props.modelValue]
})

const allSelected = computed(() =>
  Boolean(props.options.length) && draft.value.length === props.options.length,
)
const someSelected = computed(() =>
  draft.value.length > 0 && draft.value.length < props.options.length,
)

function toggleAll(checked) {
  draft.value = checked ? props.options.map(o => o.value).filter(Boolean) : []
}

function confirm() {
  emit('confirm', [...draft.value])
  emit('update:show', false)
}
</script>

<style scoped>
.picker-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.picker-count {
  font-size: 12px;
  color: var(--text-tertiary, #86909c);
}

.field-checkbox-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px 10px;
  max-height: 320px;
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
