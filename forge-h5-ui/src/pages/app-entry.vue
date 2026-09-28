<template>
  <AiLayoutPage :title="title" subtitle="已从全部应用打开">
    <AiFeedbackHost />
    <view class="app-entry">
      <view class="app-entry__icon">
        <AiIcon icon="/static/icons/ai-icon/layout.svg" color="#3b82f6" size="lg" />
      </view>
      <text class="app-entry__title">{{ title }}</text>
      <text class="app-entry__desc">该功能已由后台菜单授权。移动端页面完成配置后，将自动从这里进入。</text>
      <AiButton block @click="goHome">返回首页</AiButton>
    </view>
  </AiLayoutPage>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import AiButton from '@/components/AiButton.vue'
import AiFeedbackHost from '@/components/feedback/AiFeedbackHost.vue'
import AiIcon from '@/components/AiIcon.vue'
import AiLayoutPage from '@/components/AiLayoutPage.vue'

const title = ref('应用功能')

onLoad((query = {}) => {
  title.value = String(query.title || '应用功能')
  uni.setNavigationBarTitle({ title: title.value })
  const configKey = String(query.configKey || query.runtimeConfigKey || query.pageConfigKey || '').trim()
  const path = String(query.path || '').trim()
  if (configKey || /(?:crud-page|crud)\//.test(path)) {
    const params = Object.entries({
      configKey: configKey || resolveConfigKey(path),
      title: title.value,
      ...(query.mode ? { mode: query.mode } : {}),
      ...(query.recordId ? { recordId: query.recordId } : {}),
      ...(query.applicationId ? { applicationId: query.applicationId } : {}),
      ...(query.appId ? { appId: query.appId } : {}),
      ...(query.pageId ? { pageId: query.pageId } : {}),
      ...(query.pageCode ? { pageCode: query.pageCode } : {}),
    }).map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`).join('&')
    uni.redirectTo({ url: `/pages/lowcode-runtime?${params}` })
  }
})

function resolveConfigKey(path) {
  return decodeURIComponent(String(path || '').match(/(?:crud-page|crud|lowcode)\/([^/?]+)/)?.[1] || '')
}

function goHome() {
  uni.switchTab({ url: '/pages/index/index' })
}
</script>

<style lang="scss" scoped>
.app-entry { display: flex; width: 100%; max-width: 640px; min-height: 460rpx; flex-direction: column; align-items: center; justify-content: center; margin: 0 auto; padding: 24px 20px; border: 1px solid var(--border-light); border-radius: var(--radius-card); text-align: center; background: #fff; box-shadow: var(--shadow-soft); box-sizing: border-box; }
.app-entry__icon { display: flex; width: 56px; height: 56px; align-items: center; justify-content: center; border: 0; border-radius: 18px; background: linear-gradient(135deg, #eff6ff, #eef2ff); }
.app-entry__title { display: block; margin-top: 18px; color: var(--text-strong); font-size: 18px; font-weight: 700; }
.app-entry__desc { display: block; max-width: 520rpx; margin: 16rpx 0 32rpx; color: var(--text-secondary); font-size: 26rpx; line-height: 1.5; }
</style>
