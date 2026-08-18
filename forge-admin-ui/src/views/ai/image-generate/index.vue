<template>
  <div class="image-generate-page">
    <div class="page-header">
      <div class="page-title">
        图片生成
      </div>
      <div class="page-subtitle">
        AI 文生图，输入提示词生成图片
      </div>
    </div>

    <n-card class="generate-card">
      <n-form label-placement="top">
        <n-form-item label="提示词">
          <n-input
            v-model:value="form.prompt"
            type="textarea"
            :rows="3"
            placeholder="描述你想要生成的图片内容..."
          />
        </n-form-item>
        <n-grid :cols="2" :x-gap="16">
          <n-form-item label="负面提示词">
            <n-input
              v-model:value="form.negativePrompt"
              placeholder="不希望出现的内容（可选）"
            />
          </n-form-item>
          <n-form-item label="尺寸">
            <n-select
              v-model:value="form.size"
              :options="sizeOptions"
            />
          </n-form-item>
        </n-grid>
        <n-form-item label="模型">
          <n-select
            v-model:value="form.modelId"
            :options="modelOptions"
            placeholder="选择图片生成模型"
            filterable
          />
        </n-form-item>
        <n-button
          type="primary"
          size="large"
          :loading="generating"
          @click="handleGenerate"
        >
          生成图片
        </n-button>
      </n-form>
    </n-card>

    <!-- 生成结果 -->
    <n-card v-if="currentResult" class="result-card" title="生成结果">
      <div v-if="currentResult.status === 'pending' || currentResult.status === 'generating'" class="result-loading">
        <n-spin size="large" />
        <p>图片生成中，请稍候...</p>
      </div>
      <div v-else-if="currentResult.status === 'success'" class="result-image">
        <AuthImage :file-id="currentResult.resultFileId" style="max-width: 100%; max-height: 500px;" />
        <n-button secondary style="margin-top: 12px;" @click="handleGenerate">
          重新生成
        </n-button>
      </div>
      <div v-else-if="currentResult.status === 'failed'" class="result-error">
        <n-result status="error" :title="currentResult.errorMsg || '生成失败'" />
      </div>
    </n-card>

    <!-- 历史记录 -->
    <n-card class="history-card" title="生成历史">
      <n-data-table
        :columns="historyColumns"
        :data="historyList"
        :pagination="historyPagination"
        :loading="historyLoading"
        remote
        @update:page="loadHistory"
      />
    </n-card>
  </div>
</template>

<script setup>
import { useMessage } from 'naive-ui'
import { onMounted, ref } from 'vue'
import { imageGenerate, imageGenerateGetResult, imageGeneratePage, modelListByProvider } from '@/api/ai'
import AuthImage from '@/components/common/AuthImage.vue'

const message = useMessage()

const form = ref({
  prompt: '',
  negativePrompt: '',
  size: '1024x1024',
  modelId: null,
})

const sizeOptions = [
  { label: '1024 × 1024', value: '1024x1024' },
  { label: '512 × 512', value: '512x512' },
  { label: '1792 × 1024', value: '1792x1024' },
  { label: '1024 × 1792', value: '1024x1792' },
]

const modelOptions = ref([])
const generating = ref(false)
const currentResult = ref(null)
const historyList = ref([])
const historyLoading = ref(false)
const historyPagination = ref({ page: 1, pageSize: 10, itemCount: 0 })

const historyColumns = [
  { title: '提示词', key: 'prompt', ellipsis: { tooltip: true } },
  { title: '尺寸', key: 'size', width: 120 },
  {
    title: '状态',
    key: 'status',
    width: 100,
    render: (row) => {
      const map = { pending: '待生成', generating: '生成中', success: '成功', failed: '失败' }
      return map[row.status] || row.status
    },
  },
  { title: '创建时间', key: 'createTime', width: 180 },
]

onMounted(() => {
  loadModels()
  loadHistory()
})

async function loadModels() {
  try {
    // 加载图片生成类型的模型（简化：加载所有模型供选择）
    const res = await modelListByProvider()
    if (res.data) {
      modelOptions.value = (res.data || [])
        .filter(m => m.modelType === 'image_generation')
        .map(m => ({ label: m.modelName || m.modelId, value: m.id }))
    }
  }
  catch { /* ignore */ }
}

async function loadHistory(page) {
  historyLoading.value = true
  try {
    const p = page || historyPagination.value.page
    const res = await imageGeneratePage({
      pageNum: p,
      pageSize: historyPagination.value.pageSize,
    })
    if (res.data) {
      historyList.value = res.data.records || []
      historyPagination.value.itemCount = res.data.total || 0
      historyPagination.value.page = p
    }
  }
  finally {
    historyLoading.value = false
  }
}

async function handleGenerate() {
  if (!form.value.prompt) {
    message.warning('请输入提示词')
    return
  }
  if (!form.value.modelId) {
    message.warning('请选择模型')
    return
  }

  generating.value = true
  currentResult.value = null
  try {
    const res = await imageGenerate({
      prompt: form.value.prompt,
      negativePrompt: form.value.negativePrompt || undefined,
      size: form.value.size,
      modelId: form.value.modelId,
    })
    if (res.data) {
      currentResult.value = { id: res.data, status: 'pending' }
      // 轮询结果
      pollResult(res.data)
    }
  }
  catch (e) {
    message.error(`生成请求失败: ${e.message || '未知错误'}`)
  }
  finally {
    generating.value = false
  }
}

function pollResult(recordId) {
  const timer = setInterval(async () => {
    try {
      const res = await imageGenerateGetResult(recordId)
      if (res.data) {
        currentResult.value = res.data
        if (res.data.status === 'success' || res.data.status === 'failed') {
          clearInterval(timer)
          loadHistory()
        }
      }
    }
    catch {
      clearInterval(timer)
    }
  }, 2000)
}
</script>

<style scoped>
.image-generate-page {
  padding: 20px;
}
.page-header {
  margin-bottom: 20px;
}
.page-title {
  font-size: 20px;
  font-weight: 600;
}
.page-subtitle {
  color: var(--text-color-3);
  font-size: 14px;
  margin-top: 4px;
}
.generate-card,
.result-card,
.history-card {
  margin-bottom: 16px;
}
.result-loading {
  text-align: center;
  padding: 40px;
}
.result-image {
  text-align: center;
}
</style>
