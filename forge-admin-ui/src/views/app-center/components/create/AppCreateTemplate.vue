<template>
  <section class="template-create">
    <div class="template-create__toolbar">
      <n-input v-model:value="keyword" clearable placeholder="搜索模板名称或场景">
        <template #prefix>
          <n-icon><SearchOutline /></n-icon>
        </template>
      </n-input>
      <n-radio-group v-model:value="deliveryMode" size="small">
        <n-radio-button value="ONLINE">
          在线使用
        </n-radio-button>
        <n-radio-button value="SOURCE">
          生成源码
        </n-radio-button>
      </n-radio-group>
    </div>

    <div class="template-grid">
      <button
        v-for="template in templates"
        :key="template.key"
        type="button"
        class="template-card"
        :class="{ active: selectedKey === template.key }"
        @click="selectedKey = template.key"
      >
        <span class="template-card__icon">
          <IconRenderer :icon="template.icon" :size="22" />
        </span>
        <span class="template-card__content">
          <span class="template-card__title">
            <strong>{{ template.name }}</strong>
            <n-tag size="small" :bordered="false">{{ template.category }}</n-tag>
          </span>
          <span class="template-card__description">{{ template.description }}</span>
          <small>{{ template.useCount }} 次启用 · {{ layoutLabel(template.templateCode) }}</small>
        </span>
        <n-icon v-if="selectedKey === template.key" class="template-card__check">
          <CheckmarkCircle />
        </n-icon>
      </button>
    </div>

    <n-empty v-if="!templates.length" description="没有匹配的应用模板" />
    <n-alert v-if="deliveryMode === 'SOURCE'" type="info" :bordered="false">
      系统会先生成可运行的在线应用，再打开统一代码预览与下载面板，确保源码与低代码协议一致。
    </n-alert>
  </section>
</template>

<script setup>
import { CheckmarkCircle, SearchOutline } from '@vicons/ionicons5'
import { computed, ref, watch } from 'vue'
import IconRenderer from '@/components/IconRenderer.vue'
import {
  APPLICATION_TEMPLATE_CATALOG,
  buildTemplateInitializePayload,
  filterApplicationTemplates,
  findApplicationTemplate,
} from './app-template-catalog'

const props = defineProps({
  templateKey: { type: String, default: '' },
  initialDeliveryMode: { type: String, default: 'ONLINE' },
})

const keyword = ref('')
const selectedKey = ref(props.templateKey || APPLICATION_TEMPLATE_CATALOG[0]?.key || '')
const deliveryMode = ref(props.initialDeliveryMode === 'SOURCE' ? 'SOURCE' : 'ONLINE')
const templates = computed(() => filterApplicationTemplates(keyword.value, 'official'))

watch(() => props.templateKey, (value) => {
  if (value)
    selectedKey.value = value
})

function layoutLabel(templateCode) {
  if (templateCode === 'TREE_TABLE')
    return '左树右表'
  if (templateCode === 'MASTER_DETAIL')
    return '主子表'
  return '单表 CRUD'
}

function validate() {
  if (!findApplicationTemplate(selectedKey.value))
    throw new Error('请选择应用模板')
  return true
}

function getPayload() {
  const template = findApplicationTemplate(selectedKey.value)
  return {
    template,
    initialization: buildTemplateInitializePayload(template),
    deliveryMode: deliveryMode.value,
  }
}

defineExpose({ getPayload, validate })
</script>

<style scoped>
.template-create {
  display: grid;
  gap: 14px;
}

.template-create__toolbar {
  display: grid;
  grid-template-columns: minmax(240px, 1fr) auto;
  gap: 12px;
}

.template-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.template-card {
  position: relative;
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr);
  gap: 12px;
  min-width: 0;
  padding: 14px;
  border: 1px solid var(--n-border-color);
  border-radius: 8px;
  background: var(--n-color);
  color: inherit;
  text-align: left;
  cursor: pointer;
}

.template-card:hover,
.template-card.active {
  border-color: var(--n-primary-color);
}

.template-card.active {
  background: color-mix(in srgb, var(--n-primary-color) 4%, var(--n-color));
}

.template-card__icon {
  display: grid;
  width: 42px;
  height: 42px;
  place-items: center;
  border-radius: 8px;
  color: var(--n-primary-color);
  background: color-mix(in srgb, var(--n-primary-color) 9%, transparent);
}

.template-card__content {
  display: grid;
  min-width: 0;
  gap: 6px;
}

.template-card__title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.template-card__description {
  color: var(--n-text-color-2);
  font-size: 13px;
  line-height: 1.5;
}

.template-card small {
  color: var(--n-text-color-3);
}

.template-card__check {
  position: absolute;
  top: 9px;
  right: 9px;
  color: var(--n-primary-color);
  font-size: 18px;
}

@media (max-width: 760px) {
  .template-create__toolbar,
  .template-grid {
    grid-template-columns: 1fr;
  }
}
</style>
