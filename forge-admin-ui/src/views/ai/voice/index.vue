<template>
  <div class="voice-settings-page">
    <div class="page-header">
      <div class="page-title">
        语音设置
      </div>
      <div class="page-subtitle">
        配置 ASR（语音识别）和 TTS（语音合成）模型
      </div>
    </div>

    <n-grid :cols="2" :x-gap="16">
      <!-- ASR 设置 -->
      <n-card title="语音识别（ASR）">
        <n-form label-placement="top">
          <n-form-item label="ASR 模型">
            <n-select
              v-model:value="asrModelId"
              :options="asrModelOptions"
              placeholder="选择语音识别模型"
              filterable
              clearable
            />
          </n-form-item>
          <n-form-item label="测试语音识别">
            <n-upload
              :max="1"
              accept="audio/*"
              :custom-request="handleAsrTest"
              :show-file-list="false"
            >
              <n-button :loading="asrTesting" secondary>
                上传音频测试
              </n-button>
            </n-upload>
          </n-form-item>
          <n-form-item v-if="asrResult" label="识别结果">
            <n-input :value="asrResult" type="textarea" :rows="3" readonly />
          </n-form-item>
        </n-form>
      </n-card>

      <!-- TTS 设置 -->
      <n-card title="语音合成（TTS）">
        <n-form label-placement="top">
          <n-form-item label="TTS 模型">
            <n-select
              v-model:value="ttsModelId"
              :options="ttsModelOptions"
              placeholder="选择语音合成模型"
              filterable
              clearable
            />
          </n-form-item>
          <n-form-item label="测试文本">
            <n-input
              v-model:value="ttsTestText"
              type="textarea"
              :rows="3"
              placeholder="输入测试文本..."
            />
          </n-form-item>
          <n-form-item>
            <n-button :loading="ttsTesting" secondary @click="handleTtsTest">
              合成语音
            </n-button>
          </n-form-item>
          <n-form-item v-if="ttsResultFileId" label="合成结果">
            <audio controls :src="getFileUrl(ttsResultFileId)" />
          </n-form-item>
        </n-form>
      </n-card>
    </n-grid>
  </div>
</template>

<script setup>
import { useMessage } from 'naive-ui'
import { onMounted, ref } from 'vue'
import { modelListByProvider, voiceAsr, voiceTts } from '@/api/ai'
import { getFileUrl } from '@/utils/file'

const message = useMessage()

const asrModelId = ref(null)
const ttsModelId = ref(null)
const asrModelOptions = ref([])
const ttsModelOptions = ref([])
const asrTesting = ref(false)
const ttsTesting = ref(false)
const asrResult = ref('')
const ttsTestText = ref('你好，这是一段语音合成测试。')
const ttsResultFileId = ref(null)

onMounted(() => {
  loadModels()
})

async function loadModels() {
  try {
    const res = await modelListByProvider()
    if (res.data) {
      const models = res.data || []
      asrModelOptions.value = models
        .filter(m => m.modelType === 'asr')
        .map(m => ({ label: m.modelName || m.modelId, value: m.id }))
      ttsModelOptions.value = models
        .filter(m => m.modelType === 'tts')
        .map(m => ({ label: m.modelName || m.modelId, value: m.id }))
    }
  }
  catch { /* ignore */ }
}

async function handleAsrTest({ file }) {
  if (!asrModelId.value) {
    message.warning('请先选择 ASR 模型')
    return
  }
  asrTesting.value = true
  asrResult.value = ''
  try {
    const formData = new FormData()
    formData.append('audio', file.file)
    formData.append('agentId', '0') // 测试用，实际需传 agentId
    const res = await voiceAsr(formData)
    if (res.data) {
      asrResult.value = res.data
      message.success('语音识别成功')
    }
  }
  catch (e) {
    message.error(`语音识别失败: ${e.message || '未知错误'}`)
  }
  finally {
    asrTesting.value = false
  }
}

async function handleTtsTest() {
  if (!ttsModelId.value) {
    message.warning('请先选择 TTS 模型')
    return
  }
  if (!ttsTestText.value) {
    message.warning('请输入测试文本')
    return
  }
  ttsTesting.value = true
  ttsResultFileId.value = null
  try {
    const res = await voiceTts({
      text: ttsTestText.value,
      agentId: 0, // 测试用
    })
    if (res.data) {
      ttsResultFileId.value = res.data
      message.success('语音合成成功')
    }
  }
  catch (e) {
    message.error(`语音合成失败: ${e.message || '未知错误'}`)
  }
  finally {
    ttsTesting.value = false
  }
}
</script>

<style scoped>
.voice-settings-page {
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
</style>
