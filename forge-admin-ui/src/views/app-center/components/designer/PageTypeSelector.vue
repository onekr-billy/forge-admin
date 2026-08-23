<template>
  <n-modal
    :show="show"
    preset="card"
    class="page-type-selector-modal"
    title="创建页面"
    :bordered="false"
    :mask-closable="false"
    :style="{ width: 'min(880px, calc(100vw - 40px))' }"
    @update:show="emit('update:show', $event)"
  >
    <div class="page-type-selector-intro">
      <strong>先选择页面形态</strong>
      <span>数据页会同步创建可见的业务对象；自定义页面不绑定对象。</span>
    </div>

    <div class="page-type-grid" role="radiogroup" aria-label="页面形态">
      <button
        v-for="item in PAGE_SHAPE_TYPES"
        :key="item.value"
        type="button"
        class="page-type-card"
        :class="{ active: form.pageType === item.value }"
        role="radio"
        :aria-checked="form.pageType === item.value"
        @click="selectPageType(item.value)"
      >
        <span class="page-type-icon" :class="`kind-${item.value}`">
          <n-icon><component :is="pageTypeIcons[item.value]" /></n-icon>
        </span>
        <span class="page-type-copy">
          <strong>{{ item.label }}</strong>
          <small>{{ item.description }}</small>
        </span>
        <span class="page-type-check">✓</span>
      </button>
    </div>

    <n-form ref="formRef" :model="form" :rules="rules" label-placement="top" class="page-type-form">
      <n-form-item label="页面名称" path="pageName">
        <n-input v-model:value="form.pageName" maxlength="50" show-count placeholder="例如：客户管理" @update:value="handlePageNameChange" />
      </n-form-item>
      <div v-if="form.pageType !== 'custom'" class="page-object-fields">
        <n-form-item label="对象名称" path="objectName">
          <n-input v-model:value="form.objectName" maxlength="50" placeholder="页面中管理的数据对象名称" @update:value="handleObjectNameChange" />
        </n-form-item>
        <n-form-item label="对象编码" path="objectCode">
          <n-input v-model:value="form.objectCode" maxlength="48" placeholder="字母开头，可使用数字和下划线" @update:value="objectCodeEdited = true" />
          <template #feedback>
            自动生成后仍可编辑，保存后会显示在设计器顶部。
          </template>
        </n-form-item>
      </div>
    </n-form>

    <template #footer>
      <div class="page-type-footer">
        <n-button @click="emit('update:show', false)">
          取消
        </n-button>
        <n-button type="primary" @click="confirmSelection">
          进入设计器
        </n-button>
      </div>
    </template>
  </n-modal>
</template>

<script setup>
import { AppsOutline, CreateOutline, DocumentTextOutline, ListOutline } from '@vicons/ionicons5'
import { reactive, ref, watch } from 'vue'
import { PAGE_SHAPE_TYPES } from '../../in-app-builder/page-shape-design'
import { normalizeObjectCode } from './form-first/namingUtils'

const props = defineProps({
  show: {
    type: Boolean,
    default: false,
  },
  defaultParentId: {
    type: [String, Number],
    default: null,
  },
})

const emit = defineEmits(['update:show', 'confirm'])
const formRef = ref(null)
const objectCodeEdited = ref(false)
const objectNameEdited = ref(false)
const form = reactive(createDefaultForm())
const pageTypeIcons = {
  'form': DocumentTextOutline,
  'list': ListOutline,
  'list-form': AppsOutline,
  'custom': CreateOutline,
}
const rules = {
  pageName: { required: true, message: '请输入页面名称', trigger: ['input', 'blur'] },
  objectName: { required: true, message: '请输入对象名称', trigger: ['input', 'blur'] },
  objectCode: [
    { required: true, message: '请输入对象编码', trigger: ['input', 'blur'] },
    {
      validator: (_rule, value) => /^[a-z]\w{1,47}$/i.test(String(value || '')),
      message: '对象编码需以字母开头，仅含字母、数字和下划线（2-48 位）',
      trigger: ['input', 'blur'],
    },
  ],
}

watch(() => [props.show, props.defaultParentId], ([visible]) => {
  if (!visible)
    return
  Object.assign(form, createDefaultForm())
  objectCodeEdited.value = false
  objectNameEdited.value = false
})

function createDefaultForm() {
  return {
    pageType: 'form',
    pageName: '',
    objectName: '',
    objectCode: '',
    parentId: props.defaultParentId == null || props.defaultParentId === ''
      ? null
      : String(props.defaultParentId),
  }
}

function selectPageType(pageType) {
  form.pageType = pageType
  formRef.value?.restoreValidation?.()
}

function handlePageNameChange(value) {
  if (!objectNameEdited.value)
    form.objectName = value
  if (!objectCodeEdited.value)
    form.objectCode = normalizeObjectCode('', value)
}

function handleObjectNameChange(value) {
  objectNameEdited.value = true
  if (!objectCodeEdited.value)
    form.objectCode = normalizeObjectCode('', value)
}

async function confirmSelection() {
  if (form.pageType !== 'custom')
    await formRef.value?.validate()
  else if (!String(form.pageName || '').trim())
    return formRef.value?.validate()
  emit('confirm', {
    ...form,
    pageName: String(form.pageName || '').trim(),
    objectName: String(form.objectName || '').trim(),
    objectCode: form.pageType === 'custom' ? '' : normalizeObjectCode(form.objectCode, form.objectName),
  })
}
</script>

<style scoped>
.page-type-selector-intro {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 18px;
}

.page-type-selector-intro strong {
  color: var(--n-text-color, #1f2329);
  font-size: 16px;
}

.page-type-selector-intro span {
  color: var(--n-text-color-2, #646a73);
  font-size: 13px;
}

.page-type-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.page-type-card {
  position: relative;
  display: flex;
  min-height: 142px;
  flex-direction: column;
  gap: 12px;
  padding: 18px;
  border: 1px solid var(--n-border-color, #dee0e3);
  border-radius: 12px;
  background: var(--n-color, #fff);
  text-align: left;
  transition:
    border-color 0.2s,
    box-shadow 0.2s,
    transform 0.2s;
}

.page-type-card:hover,
.page-type-card.active {
  border-color: var(--n-primary-color, #3370ff);
  box-shadow: 0 6px 18px color-mix(in srgb, var(--n-primary-color, #3370ff) 16%, transparent);
  transform: translateY(-1px);
}

.page-type-icon {
  display: grid;
  width: 40px;
  height: 40px;
  place-items: center;
  border-radius: 10px;
  background: color-mix(in srgb, var(--n-primary-color, #3370ff) 10%, transparent);
  color: var(--n-primary-color, #3370ff);
  font-size: 22px;
}

.page-type-icon.kind-list-form {
  background: #f3efff;
  color: #8b5cf6;
}

.page-type-icon.kind-custom {
  background: #e6fffb;
  color: #0f9f8f;
}

.page-type-copy {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.page-type-copy strong {
  color: var(--n-text-color, #1f2329);
  font-size: 15px;
}

.page-type-copy small {
  color: var(--n-text-color-3, #8f959e);
  font-size: 12px;
  line-height: 1.55;
}

.page-type-check {
  position: absolute;
  top: 10px;
  right: 12px;
  color: var(--n-primary-color, #3370ff);
  opacity: 0;
}

.page-type-card.active .page-type-check {
  opacity: 1;
}

.page-type-form {
  margin-top: 20px;
}

.page-object-fields {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.page-type-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

@media (max-width: 760px) {
  .page-type-grid,
  .page-object-fields {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 520px) {
  .page-type-grid,
  .page-object-fields {
    grid-template-columns: 1fr;
  }
}
</style>
