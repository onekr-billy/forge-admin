<template>
  <view class="message-detail-page">
    <AiFeedbackHost />
    <view v-if="loading" class="message-detail-card"><AiListSkeleton :rows="3" /></view>
    <view v-else-if="message" class="message-detail-card">
      <view class="message-detail-head">
        <text class="message-detail-type">{{ category }}</text>
        <text class="message-detail-time">{{ message.createTime || message.receiveTime || '' }}</text>
      </view>
      <text class="message-detail-title">{{ message.title || '消息通知' }}</text>
      <rich-text v-if="message.content" class="message-detail-body" :nodes="safeContent" />
      <text v-else class="message-detail-body">{{ message.description || '暂无正文内容' }}</text>
    </view>
    <AiEmpty v-else title="消息不存在" description="消息可能已经被删除" icon="inbox" />
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import AiEmpty from '@/components/AiEmpty.vue'
import AiFeedbackHost from '@/components/feedback/AiFeedbackHost.vue'
import AiListSkeleton from '@/components/AiListSkeleton.vue'
import api from '@/api'
import { sanitizeMessageHtml } from '@/utils/message-html'
import { toast } from '@/utils/notify'

const message = ref(null)
const loading = ref(true)
const category = computed(() => ({ SYSTEM: '系统消息', SMS: '短信通知', EMAIL: '邮件通知', CUSTOM: '通知' })[message.value?.type] || '消息通知')
const safeContent = computed(() => sanitizeMessageHtml(message.value?.content))

onLoad(async ({ id } = {}) => {
  if (!id) { loading.value = false; return }
  try {
    const response = await api.getMessageDetail(String(id))
    message.value = response?.data || null
    if (Number(message.value?.readFlag ?? message.value?.readStatus) === 0) {
      await api.markMessageRead(String(id))
      message.value.readFlag = 1
    }
  }
  catch (error) {
    console.error('加载消息失败:', error)
    toast('消息加载失败', { type: 'error' })
  }
  finally { loading.value = false }
})
</script>

<style lang="scss" scoped>
.message-detail-page { min-height: 100%; padding: 16px; background: var(--page-bg); }
.message-detail-card { width: 100%; max-width: 960px; margin: 0 auto; padding: 16px; border: 1px solid var(--border-color); border-radius: 6px; background: #fff; box-sizing: border-box; }
.message-detail-head { display: flex; justify-content: space-between; gap: 8px; color: var(--text-muted); font-size: 12px; }
.message-detail-type { color: var(--primary-color); }
.message-detail-time { text-align: right; }
.message-detail-title { display: block; margin-top: 12px; padding-bottom: 12px; border-bottom: 1px solid var(--border-light); color: var(--text-strong); font-size: 16px; font-weight: 500; line-height: 1.4; }
.message-detail-body { display: block; margin-top: 12px; color: var(--text-secondary); font-size: 14px; line-height: 1.7; overflow-wrap: anywhere; }
</style>
