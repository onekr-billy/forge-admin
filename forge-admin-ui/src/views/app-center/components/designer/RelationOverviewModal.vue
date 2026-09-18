<template>
  <n-modal
    :show="show"
    preset="card"
    title="关系全景图"
    :bordered="false"
    class="relation-overview-modal"
    :style="{ width: 'min(1080px, calc(100vw - 32px))' }"
    @update:show="emit('update:show', $event)"
  >
    <template #header-extra>
      <n-tag size="small" :bordered="false">
        只读
      </n-tag>
    </template>

    <div v-if="loading" class="overview-loading">
      <n-spin size="medium" />
      <span>加载业务对象关系…</span>
    </div>

    <LowcodeErDiagram
      v-else-if="diagramModels.length"
      :title="objectName || objectCode || '业务对象关系'"
      :subtitle="diagramSubtitle"
      :models="diagramModels"
      :primary-model-code="objectCode"
      :editable="false"
      :download-file-name="`${objectCode || 'business-object'}-er.svg`"
    />

    <n-empty
      v-else
      description="暂无关联关系"
      size="large"
    >
      <template #extra>
        <span class="empty-hint">在表单设计器中添加关联子表，系统会自动建立对象关系。</span>
      </template>
    </n-empty>
  </n-modal>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { businessObjectDesigner, businessObjectList } from '@/api/business-app'
import LowcodeErDiagram from '@/components/lowcode-builder/model/LowcodeErDiagram.vue'

const props = defineProps({
  show: { type: Boolean, default: false },
  objectCode: { type: String, default: '' },
  objectName: { type: String, default: '' },
  fields: { type: Array, default: () => [] },
  relations: { type: Array, default: () => [] },
})

const emit = defineEmits(['update:show'])

const loading = ref(false)
const targetFieldsMap = ref({})
const businessObjectMap = ref({})

const diagramModels = computed(() => {
  const currentCode = props.objectCode || 'current_object'
  const models = [{
    modelCode: currentCode,
    modelName: props.objectName || currentCode,
    tableName: currentCode,
    modelSchema: {
      object: { code: currentCode, name: props.objectName || currentCode },
      tableName: currentCode,
      fields: normalizeFields(props.fields || []),
      relations: (props.relations || [])
        .filter(r => r.targetObjectCode)
        .map(toErRelation),
    },
  }]

  const targetCodes = new Set(
    (props.relations || []).map(r => r.targetObjectCode).filter(Boolean),
  )
  targetCodes.forEach((targetCode) => {
    const obj = businessObjectMap.value[targetCode] || {}
    const fields = targetFieldsMap.value[targetCode] || []
    models.push({
      modelCode: targetCode,
      modelName: obj.objectName || targetCode,
      tableName: obj.tableName || targetCode,
      modelSchema: {
        object: { code: targetCode, name: obj.objectName || targetCode },
        tableName: obj.tableName || targetCode,
        fields: normalizeFields(fields),
        relations: [],
      },
    })
  })

  return models
})

const diagramSubtitle = computed(() => {
  const relationCount = (props.relations || []).filter(r => r.targetObjectCode).length
  const objectCount = diagramModels.value.length
  return `${props.objectName || props.objectCode || '当前对象'}：${objectCount} 个对象，${relationCount} 条关系`
})

watch(() => props.show, async (show) => {
  if (!show)
    return
  await loadOverviewData()
})

async function loadOverviewData() {
  loading.value = true
  try {
    // Load business object list for name resolution
    const listRes = await businessObjectList({})
    const objects = unwrapList(listRes?.data)
    businessObjectMap.value = Object.fromEntries(
      objects.filter(o => o?.objectCode).map(o => [o.objectCode, o]),
    )

    // Load target object fields
    const targetCodes = new Set(
      (props.relations || []).map(r => r.targetObjectCode).filter(Boolean),
    )
    const loadPromises = [...targetCodes].map(async (code) => {
      const obj = businessObjectMap.value[code]
      if (!obj?.id)
        return
      try {
        const res = await businessObjectDesigner(obj.id)
        const fields = res.data?.fields || res.data?.modelSchema?.fields || []
        targetFieldsMap.value = { ...targetFieldsMap.value, [code]: fields }
      }
      catch {
        targetFieldsMap.value = { ...targetFieldsMap.value, [code]: [] }
      }
    })
    await Promise.all(loadPromises)
  }
  catch {
    // Silent fail - diagram will show with available data
  }
  finally {
    loading.value = false
  }
}

function normalizeFields(fields) {
  return fields
    .filter(f => f && !f.systemField)
    .map(f => ({
      field: f.field || f.fieldCode || f.sourceField || '',
      fieldCode: f.fieldCode || f.field || f.sourceField || '',
      fieldName: f.fieldName || f.label || f.rawLabel || f.field || '',
      fieldType: f.fieldType || f.type || 'VARCHAR',
      primaryKey: Boolean(f.primaryKey),
    }))
    .filter(f => f.fieldCode)
}

function toErRelation(relation = {}) {
  return {
    relationName: relation.relationName || '',
    relationType: relation.relationType || 'REFERENCE',
    sourceObjectCode: relation.sourceObjectCode || '',
    targetObjectCode: relation.targetObjectCode || '',
    sourceFieldCode: relation.sourceFieldCode || relation.sourceField || '',
    targetFieldCode: relation.targetFieldCode || relation.targetField || '',
  }
}

function unwrapList(value) {
  if (Array.isArray(value))
    return value
  if (Array.isArray(value?.records))
    return value.records
  if (Array.isArray(value?.list))
    return value.list
  return []
}
</script>

<style scoped>
.overview-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 40px 0;
  color: var(--text-tertiary, #86909c);
  font-size: 13px;
}

.empty-hint {
  font-size: 13px;
  color: var(--text-tertiary, #86909c);
}
</style>
