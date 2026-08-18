<template>
  <section class="ai-create">
    <n-alert type="info" :bordered="false">
      描述业务目标即可生成对象、字段和页面草稿。生成结果只在确认后写入应用设计态，不会自动发布或建表。
    </n-alert>
    <div class="ai-create__prompt">
      <n-input
        v-model:value="description"
        type="textarea"
        :autosize="{ minRows: 4, maxRows: 8 }"
        maxlength="2000"
        show-count
        placeholder="例如：做一个销售线索跟进系统，记录客户、联系人、跟进阶段和预计成交金额"
      />
      <n-button type="primary" secondary :loading="generating" @click="generatePlan">
        <template #icon>
          <n-icon><SparklesOutline /></n-icon>
        </template>
        {{ plan ? '重新生成方案' : '生成应用方案' }}
      </n-button>
    </div>

    <div v-if="plan" class="ai-plan">
      <header>
        <div>
          <strong>{{ plan.requirementSummary || plan.appDraft?.appName || 'AI 应用方案' }}</strong>
          <span>{{ generatedModels.length }} 个对象 · {{ generatedApps.length }} 个页面草稿 · {{ generatedProcesses.length }} 个流程草稿</span>
        </div>
        <n-tag :type="plan.fallback ? 'warning' : 'success'" :bordered="false">
          {{ plan.fallback ? '规则方案' : 'AI 方案' }}
        </n-tag>
      </header>
      <div class="ai-plan__models">
        <article v-for="model in generatedModels" :key="model.modelCode || model.modelName">
          <span class="ai-plan__model-icon"><n-icon><LayersOutline /></n-icon></span>
          <span>
            <strong>{{ model.modelName || model.modelCode }}</strong>
            <small>{{ model.modelCode }} · {{ model.modelSchema?.fields?.length || 0 }} 个字段</small>
          </span>
        </article>
      </div>
      <div v-if="generatedProcesses.length" class="ai-plan__processes">
        <strong>建议流程</strong>
        <span v-for="process in generatedProcesses" :key="process.processCode || process.processName">
          {{ process.processName }} · {{ process.subjectObjectCode }}
        </span>
      </div>
      <ul v-if="plan.generationNotes?.length">
        <li v-for="note in plan.generationNotes.slice(0, 4)" :key="note">
          {{ note }}
        </li>
      </ul>
    </div>
  </section>
</template>

<script setup>
import { LayersOutline, SparklesOutline } from '@vicons/ionicons5'
import { computed, ref } from 'vue'
import { lowcodeAiGenerateApp } from '@/api/lowcode-crud'

const emit = defineEmits(['suggestName'])
const description = ref('')
const generating = ref(false)
const plan = ref(null)
const generatedModels = computed(() => plan.value?.models?.length
  ? plan.value.models
  : plan.value?.modelDraft ? [plan.value.modelDraft] : [])
const generatedApps = computed(() => plan.value?.apps?.length
  ? plan.value.apps
  : plan.value?.appDraft ? [plan.value.appDraft] : [])
const generatedProcesses = computed(() => plan.value?.processSuggestions || [])

async function generatePlan() {
  const prompt = description.value.trim()
  if (prompt.length < 8) {
    window.$message?.warning('请至少用 8 个字描述业务场景')
    return
  }
  generating.value = true
  try {
    const response = await lowcodeAiGenerateApp({
      description: prompt,
      includeDomainModels: true,
    })
    plan.value = response.data || null
    if (!generatedModels.value.length)
      throw new Error('AI 方案没有返回可初始化的数据对象')
    const suggestedName = plan.value?.appDraft?.appName
      || plan.value?.requirementSummary
      || generatedApps.value[0]?.appName
    if (suggestedName)
      emit('suggestName', String(suggestedName).slice(0, 64))
  }
  catch (error) {
    plan.value = null
    window.$message?.error(error?.message || '生成应用方案失败')
  }
  finally {
    generating.value = false
  }
}

function validate() {
  if (!plan.value || !generatedModels.value.length)
    throw new Error('请先生成并确认应用方案')
  return true
}

function getPayload() {
  return { plan: plan.value }
}

defineExpose({ getPayload, validate })
</script>

<style scoped>
.ai-create {
  display: grid;
  gap: 14px;
}

.ai-create__prompt {
  display: grid;
  justify-items: end;
  gap: 10px;
}

.ai-plan {
  display: grid;
  gap: 12px;
  padding: 14px;
  border: 1px solid var(--n-border-color);
  border-radius: 8px;
  background: var(--n-color-embedded);
}

.ai-plan header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.ai-plan header > div {
  display: grid;
  gap: 3px;
}

.ai-plan header span,
.ai-plan article small {
  color: var(--n-text-color-3);
  font-size: 12px;
}

.ai-plan__models {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.ai-plan article {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 9px;
  padding: 9px 10px;
  border: 1px solid var(--n-border-color);
  border-radius: 6px;
  background: var(--n-color);
}

.ai-plan article > span:last-child {
  display: grid;
  min-width: 0;
  gap: 2px;
}

.ai-plan__model-icon {
  color: var(--n-primary-color);
  font-size: 18px;
}

.ai-plan ul {
  margin: 0;
  color: var(--n-text-color-2);
  font-size: 12px;
  line-height: 1.7;
  padding-left: 20px;
}

.ai-plan__processes {
  display: grid;
  gap: 4px;
  color: var(--n-text-color-2);
  font-size: 12px;
}

@media (max-width: 760px) {
  .ai-plan__models {
    grid-template-columns: 1fr;
  }
}
</style>
