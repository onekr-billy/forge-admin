<template>
  <div class="input-area">
    <div class="input-col">
      <div class="composer">
        <div class="input-attachments">
          <ImageUpload
            ref="imageUploadRef"
            v-model="attach"
            value-type="array"
            business-type="ai-chat"
            :limit="4"
            :multiple="true"
            :show-tip="false"
            :disabled="isStreaming"
          />
        </div>
        <NInput
          v-model:value="text"
          type="textarea"
          :bordered="false"
          :autosize="{ minRows: 1, maxRows: 6 }"
          placeholder="输入消息…"
          :disabled="isStreaming"
          @keydown.enter.exact="onEnter"
        />
        <div class="composer-bar">
          <div class="composer-tools">
            <NTooltip>
              <template #trigger>
                <NButton
                  quaternary
                  circle
                  size="small"
                  class="tool-btn"
                  :disabled="isStreaming"
                  @click="triggerAttach"
                >
                  <template #icon>
                    <NIcon><AttachOutline /></NIcon>
                  </template>
                </NButton>
              </template>
              添加图片
            </NTooltip>
            <span class="composer-hint">Enter 发送 · Shift+Enter 换行</span>
          </div>
          <NButton
            circle
            :type="isStreaming ? 'error' : 'primary'"
            class="send-btn"
            :disabled="!isStreaming && !text.trim()"
            @click="isStreaming ? emit('stop') : emit('send')"
          >
            <template #icon>
              <NIcon>
                <StopCircleOutline v-if="isStreaming" />
                <SendOutline v-else />
              </NIcon>
            </template>
          </NButton>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { AttachOutline, SendOutline, StopCircleOutline } from '@vicons/ionicons5'
import { NButton, NIcon, NInput, NTooltip } from 'naive-ui'
import { computed, ref } from 'vue'
import ImageUpload from '@/components/image-upload/index.vue'

defineOptions({ name: 'ChatInput' })

const props = defineProps({
  // 输入文本（v-model）
  modelValue: { type: String, default: '' },
  // 附件 fileId 列表（v-model:attachments）
  attachments: { type: Array, default: () => [] },
  isStreaming: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue', 'update:attachments', 'send', 'stop'])

const text = computed({
  get: () => props.modelValue,
  set: v => emit('update:modelValue', v),
})
const attach = computed({
  get: () => props.attachments,
  set: v => emit('update:attachments', v),
})

const imageUploadRef = ref(null)

// 回形针按钮触发隐藏的上传输入（内置「+」块已隐藏）
function triggerAttach() {
  imageUploadRef.value?.triggerUpload?.()
}

function onEnter(e) {
  if (e?.shiftKey)
    return
  e?.preventDefault()
  emit('send')
}

// 清空上传组件内部状态（发送后父组件调用；仅重置 v-model 不足以清 UI 态）
function clearAttachments() {
  imageUploadRef.value?.clear?.()
}

defineExpose({ clearAttachments })
</script>

<style scoped>
.input-area {
  padding: 12px 24px 18px;
  background: var(--surface);
  border-top: 1px solid var(--border);
}

.input-col {
  max-width: var(--chat-col);
  margin: 0 auto;
}

/* 现代圆角输入器：无边框输入 + 底部操作条 */
.composer {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 8px 10px 8px 14px;
  background: var(--surface);
  border: 1px solid var(--border-strong);
  border-radius: 18px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
  transition:
    border-color 0.15s ease,
    box-shadow 0.15s ease;
}

.composer:focus-within {
  border-color: var(--primary);
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.12);
}

/* 附件缩略图行：无图时自然塌陷；隐藏内置大「+」块与文件名，缩略图紧凑圆角 */
.input-attachments :deep(.upload-trigger) {
  display: none;
}

.input-attachments :deep(.image-list) {
  gap: 8px;
}

.input-attachments :deep(.image-item) {
  width: 64px;
  height: 64px;
  margin: 2px 0 6px;
  border-radius: 10px;
}

.input-attachments :deep(.image-name) {
  display: none;
}

.input-attachments :deep(.image-actions) {
  gap: 8px;
}

.input-attachments :deep(.action-icon) {
  font-size: 16px;
}

.composer :deep(.n-input) {
  background: transparent;
}

.composer :deep(.n-input .n-input__textarea-el) {
  font-size: 15px;
  line-height: 1.7;
}

.composer-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.composer-tools {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.tool-btn {
  color: var(--text-muted);
  flex-shrink: 0;
}

.tool-btn:hover {
  color: var(--primary);
}

.composer-hint {
  font-size: 12px;
  color: var(--text-muted);
  user-select: none;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.send-btn {
  flex-shrink: 0;
}
</style>
