<template>
  <section class="portal-page-renderer" :class="{ 'is-fill': fillHost }">
    <iframe
      v-if="externalUrl"
      class="portal-external-frame"
      :src="externalUrl"
      :title="node?.title || '外部页面'"
      sandbox="allow-forms allow-modals allow-popups allow-same-origin allow-scripts"
    />
    <div
      v-else-if="blocks.length"
      class="portal-page-flow"
      :class="{ 'is-fill': fillHost }"
      :style="fillHost ? undefined : { minHeight: `${pageHeight}px` }"
    >
      <section
        v-for="(block, index) in blocks"
        :key="block.id || `${block.blockType}-${index}`"
        class="portal-page-block"
        :class="{ 'is-fill': fillHost && blocks.length === 1 }"
        :style="resolveBlockShellStyle(block, index)"
      >
        <GridBlockRenderer
          :block="block"
          :fields="resolveBlockFields(block)"
          :runtime-crud-props="resolveRuntimeCrudProps(block)"
          :runtime-crud-loading="isRuntimeCrudLoading(block)"
          :data-source-configured="isDataSourceConfigured(block)"
          :runtime-interactive="true"
          :block-fields-resolver="resolveBlockFields"
          :runtime-crud-props-resolver="resolveRuntimeCrudProps"
          :runtime-crud-loading-resolver="isRuntimeCrudLoading"
          :data-source-configured-resolver="isDataSourceConfigured"
          :selected="false"
          :readonly="!configurable"
        />
      </section>
    </div>
    <PortalEmptyState
      v-else
      type="empty"
      title="页面尚未配置内容"
      description="请由应用管理员进入页面设计器完成编排并重新发布。"
      :show-back="false"
    />
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { crudConfigRender } from '@/api/ai'
import GridBlockRenderer from '@/components/lowcode-builder/page/GridBlockRenderer.vue'
import { buildRuntimeCrudProps } from '@/components/lowcode-builder/shared/runtime-crud-props'
import PortalEmptyState from './PortalEmptyState.vue'

const props = defineProps({
  node: { type: Object, default: null },
  page: { type: Object, default: null },
  objects: { type: Array, default: () => [] },
  entries: { type: Array, default: () => [] },
  configurable: { type: Boolean, default: false },
  designPreview: { type: Boolean, default: false },
  fillHost: { type: Boolean, default: false },
})

const emit = defineEmits(['configure-block'])

const runtimeCrudPropsByKey = ref({})
const loadingKeys = ref(new Set())
const unavailableKeys = ref(new Set())

const blocks = computed(() => {
  const layout = props.page?.layout || {}
  const items = Array.isArray(layout.gridLayout?.items)
    ? layout.gridLayout.items
    : Array.isArray(layout.items) ? layout.items.map(normalizeLegacyBlock) : []
  if (items.length)
    return items
  if (props.node?.pageType === 'object' && resolveObjectRef(props.node)) {
    return [{
      id: `portal-object-${props.node.id}`,
      blockType: 'AiCrudPage',
      props: {
        objectRef: resolveObjectRef(props.node),
        style: { widthMode: 'full', heightMode: 'full', pageFlowHeight: 640 },
      },
    }]
  }
  return []
})

const externalUrl = computed(() => {
  const raw = props.page?.externalUrl
    || props.page?.url
    || props.node?.externalUrl
    || resolveEntry(props.node?.entryRef)?.entryUrl
  if (!raw)
    return ''
  try {
    const url = new URL(String(raw), window.location.origin)
    return ['http:', 'https:'].includes(url.protocol) ? url.toString() : ''
  }
  catch {
    return ''
  }
})

const pageHeight = computed(() => blocks.value.reduce((bottom, block, index) => {
  const style = block.props?.style || {}
  const top = finiteNumber(style.pageFlowY, resolveDefaultBlockY(block, index))
  const height = finiteNumber(style.pageFlowHeight, resolveDefaultBlockHeight(block))
  return Math.max(bottom, top + height + 28)
}, 620))

watch(() => [props.node?.id, blocks.value], () => {
  runtimeCrudPropsByKey.value = {}
  loadingKeys.value = new Set()
  unavailableKeys.value = new Set()
  visitBlocks(blocks.value, preloadRuntimeCrudProps)
}, { immediate: true, deep: true })

function resolveObjectRef(source = {}) {
  const raw = source.objectRef || source.props?.objectRef || source.props?.runtimeObjectRef
  if (raw && typeof raw === 'object')
    return enrichObjectRef(raw)
  if (source === props.node && props.node?.objectRef)
    return enrichObjectRef(props.node.objectRef)
  return null
}

function enrichObjectRef(objectRef) {
  const targetId = String(objectRef?.objectId ?? objectRef?.id ?? '')
  const targetCode = String(objectRef?.objectCode || '')
  const object = props.objects.find(item => (
    (targetId && String(item.objectId ?? item.id ?? '') === targetId)
    || (targetCode && String(item.objectCode || '') === targetCode)
  ))
  return {
    ...(object || {}),
    ...(objectRef || {}),
    objectId: objectRef?.objectId ?? objectRef?.id ?? object?.objectId ?? object?.id,
    objectCode: objectRef?.objectCode || object?.objectCode || '',
    configKey: objectRef?.configKey || object?.configKey || '',
  }
}

function resolveObjectKey(objectRef) {
  return String(objectRef?.objectId ?? objectRef?.id ?? objectRef?.objectCode ?? objectRef?.configKey ?? '')
}

function preloadRuntimeCrudProps(block) {
  const objectRef = resolveObjectRef(block) || (block === blocks.value[0] ? resolveObjectRef(props.node || {}) : null)
  const key = resolveObjectKey(objectRef)
  if (!key || runtimeCrudPropsByKey.value[key] || loadingKeys.value.has(key) || unavailableKeys.value.has(key))
    return
  const configKey = String(objectRef?.configKey || '').trim()
  if (!configKey) {
    unavailableKeys.value = new Set([...unavailableKeys.value, key])
    return
  }
  loadingKeys.value = new Set([...loadingKeys.value, key])
  void loadRuntimeCrudProps(configKey, objectRef, key)
}

async function loadRuntimeCrudProps(configKey, objectRef, key) {
  try {
    let designPreview = props.designPreview
    let config = null
    try {
      config = (await crudConfigRender(configKey, designPreview, { needTip: false })).data
    }
    catch (error) {
      if (!designPreview)
        throw error
      designPreview = false
      config = (await crudConfigRender(configKey, false, { needTip: false })).data
    }
    if (!config || typeof config !== 'object')
      throw new Error('业务对象运行配置为空')
    runtimeCrudPropsByKey.value = {
      ...runtimeCrudPropsByKey.value,
      [key]: {
        ...buildRuntimeCrudProps(config, { designPreview }),
        title: config.title || objectRef.objectName || '',
      },
    }
  }
  catch (error) {
    unavailableKeys.value = new Set([...unavailableKeys.value, key])
    console.warn('[application-portal] 加载业务对象运行配置失败', error?.message || error)
  }
  finally {
    const next = new Set(loadingKeys.value)
    next.delete(key)
    loadingKeys.value = next
  }
}

function resolveRuntimeCrudProps(block) {
  const objectRef = resolveObjectRef(block) || resolveObjectRef(props.node || {})
  const key = resolveObjectKey(objectRef)
  if (key && !runtimeCrudPropsByKey.value[key] && !loadingKeys.value.has(key) && !unavailableKeys.value.has(key))
    preloadRuntimeCrudProps(block)
  return key ? runtimeCrudPropsByKey.value[key] || null : null
}

function isRuntimeCrudLoading(block) {
  return loadingKeys.value.has(resolveObjectKey(resolveObjectRef(block) || resolveObjectRef(props.node || {})))
}

function isDataSourceConfigured(block) {
  return Boolean(resolveObjectKey(resolveObjectRef(block) || resolveObjectRef(props.node || {})))
}

function resolveBlockFields(block) {
  const runtimeFields = resolveRuntimeCrudProps(block)?.fieldCatalog
  if (Array.isArray(runtimeFields) && runtimeFields.length)
    return runtimeFields
  if (Array.isArray(block.fields))
    return block.fields
  if (Array.isArray(block.props?.fields))
    return block.props.fields
  return []
}

function resolveBlockShellStyle(block, index) {
  if (props.fillHost && blocks.value.length === 1) {
    return {
      position: 'relative',
      inset: 'auto',
      width: '100%',
      height: '100%',
      textAlign: block.props?.style?.textAlign || block.props?.textAlign || block.props?.align || 'left',
    }
  }
  const style = block.props?.style || {}
  const widthMode = style.widthMode || 'full'
  const heightMode = style.heightMode || 'fixed'
  const customX = finiteNumber(style.pageFlowX, 24)
  const customY = finiteNumber(style.pageFlowY, resolveDefaultBlockY(block, index))
  const customHeight = finiteNumber(style.pageFlowHeight, readLength(style.height) || resolveDefaultBlockHeight(block))
  const position = {
    position: 'absolute',
    left: `${customX}px`,
    top: `${customY}px`,
    height: heightMode === 'auto' ? 'auto' : `${customHeight}px`,
    textAlign: style.textAlign || block.props?.textAlign || block.props?.align || 'left',
  }
  if (heightMode === 'full') {
    position.height = 'auto'
    position.bottom = '24px'
  }
  if (widthMode === 'full')
    return { ...position, left: '24px', width: 'calc(100% - 48px)' }
  const rawWidth = String(style.pageFlowWidth || '').trim()
  const frameWidth = readLength(style.width)
  if (widthMode === 'auto')
    return { ...position, width: rawWidth || `min(${Math.max(280, Math.min(560, frameWidth || 520))}px, calc(100% - 48px))` }
  if (widthMode === 'fixed' && frameWidth > 0)
    return { ...position, width: rawWidth || `min(${frameWidth}px, calc(100% - 48px))` }
  return { ...position, width: rawWidth || 'calc(100% - 48px)' }
}

function resolveDefaultBlockHeight(block = {}) {
  if (block.blockType === 'page-title')
    return 176
  if (['divider', 'custom-html'].includes(block.blockType))
    return 88
  if (['stats-strip', 'info-panel', 'AiForm'].includes(block.blockType))
    return 128
  if (['AiCrudPage', 'AiTable', 'data-table', 'search-form', 'toolbar'].includes(block.blockType))
    return 420
  return 116
}

function resolveDefaultBlockY(block, index) {
  return blocks.value.slice(0, Math.max(0, index)).reduce(
    (top, item) => top + finiteNumber(item.props?.style?.pageFlowHeight, resolveDefaultBlockHeight(item)) + 16,
    20,
  )
}

function normalizeLegacyBlock(item, index) {
  const legacyTypes = {
    'intro': 'page-title',
    'metric-card': 'stats-strip',
    'business-list': 'AiCrudPage',
    'business-form': 'AiForm',
    'todo': 'info-panel',
    'chart': 'stats-strip',
    'text': 'custom-html',
    'image': 'info-panel',
    'columns': 'grid-layout',
  }
  return {
    ...item,
    id: item.id || `legacy-${index + 1}`,
    blockType: item.blockType || legacyTypes[item.type] || item.type || 'custom-html',
    props: { ...(item.props || {}), ...(item.content ? { content: item.content } : {}) },
  }
}

function resolveEntry(entryRef) {
  const entryId = String(entryRef?.entryId ?? entryRef?.id ?? '')
  return props.entries.find(item => String(item.id ?? item.entryId ?? '') === entryId) || null
}

function visitBlocks(source, visitor) {
  ;(source || []).forEach((block) => {
    visitor(block)
    const childCollections = [block.children, block.props?.children, block.props?.items]
    childCollections.filter(Array.isArray).forEach(children => visitBlocks(children, visitor))
  })
}

function finiteNumber(value, fallback) {
  const number = Number(value)
  return Number.isFinite(number) && number >= 0 ? number : fallback
}

function readLength(value) {
  const number = Number.parseFloat(String(value || ''))
  return Number.isFinite(number) ? number : 0
}
</script>

<style scoped>
.portal-page-renderer {
  position: relative;
  min-width: 0;
  min-height: 100%;
}

.portal-page-renderer.is-fill {
  display: flex;
  height: 100%;
  min-height: 0;
  flex: 1;
  flex-direction: column;
}

.portal-page-flow {
  position: relative;
  width: 100%;
  min-width: 0;
  overflow: hidden;
}

.portal-page-flow.is-fill {
  display: flex;
  height: 100%;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  overflow: hidden;
}

.portal-page-block.is-fill {
  flex: 1;
  min-height: 0;
}

.portal-page-block {
  min-width: 0;
  border-radius: 6px;
}

.portal-page-block :deep(.grid-block) {
  height: 100% !important;
  min-height: 0;
}

.portal-external-frame {
  width: 100%;
  min-height: calc(100vh - 96px);
  border: 0;
  background: #fff;
}

@media (max-width: 768px) {
  .portal-page-flow {
    display: grid;
    gap: 12px;
    min-height: 0 !important;
    padding: 12px;
  }

  .portal-page-block {
    position: relative !important;
    inset: auto !important;
    width: 100% !important;
    height: auto !important;
    min-height: 120px;
    overflow-x: auto;
  }

  .portal-page-block:has(:deep(.block-AiCrudPage)),
  .portal-page-block:has(:deep(.block-AiTable)),
  .portal-page-block:has(:deep(.block-data-table)) {
    min-height: 440px;
  }

  .portal-page-block :deep(.n-data-table),
  .portal-page-block :deep(.n-form) {
    max-width: 100%;
  }

  .portal-page-block :deep(.n-data-table-base-table) {
    min-width: 680px;
  }
}
</style>
