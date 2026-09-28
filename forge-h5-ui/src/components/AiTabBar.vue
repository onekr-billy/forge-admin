<template>
  <view class="ai-tabbar-host">
    <view class="ai-tabbar">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        class="ai-tabbar__item"
        :class="{ 'is-active': currentKey === tab.key }"
        @click="handleTabClick(tab)"
      >
        <view class="ai-tabbar__icon-wrap">
          <view class="ai-tabbar__icon" :style="iconMask(tab.icon, currentKey === tab.key ? '#3b82f6' : '#94a3b8')" />
        </view>
        <text class="ai-tabbar__label">{{ tab.label }}</text>
      </button>
    </view>
  </view>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { resolveStaticUrl } from '@/utils/assets'

const props = defineProps({
  active: {
    type: String,
    default: '',
  },
})

const tabs = [
  {
    key: 'home',
    label: '首页',
    path: '/pages/index/index',
    icon: '/static/icons/ai-icon/home.svg',
  },
  {
    key: 'todo',
    label: '待办',
    path: '/pages/todo',
    icon: '/static/icons/ai-icon/check-square.svg',
  },
  {
    key: 'mine',
    label: '我的',
    path: '/pages/mine/index',
    icon: '/static/icons/ai-icon/user.svg',
  },
]

const currentKey = computed(() => {
  if (props.active) {
    return props.active
  }
  const pages = getCurrentPages()
  const route = pages[pages.length - 1]?.route || ''
  const matched = tabs.find(tab => route === tab.path.replace(/^\//, ''))
  return matched?.key || 'home'
})

onMounted(() => {
  hideNativeTabBar()
})

onShow(() => {
  hideNativeTabBar()
})

function hideNativeTabBar() {
  if (typeof uni === 'undefined' || typeof uni.hideTabBar !== 'function') {
    return
  }
  uni.hideTabBar({
    animation: false,
    fail: () => {},
  })
}

function iconMask(icon, color) {
  const url = resolveStaticUrl(icon)
  return {
    backgroundColor: color,
    WebkitMask: `url(${url}) center / contain no-repeat`,
    mask: `url(${url}) center / contain no-repeat`,
  }
}

function handleTabClick(tab) {
  if (tab.key === currentKey.value) {
    return
  }
  uni.switchTab({ url: tab.path })
}
</script>

<style lang="scss" scoped>
.ai-tabbar-host {
  position: fixed;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 80;
  display: flex;
  justify-content: stretch;
  padding: 0;
  pointer-events: none;
}

.ai-tabbar {
  display: flex;
  width: 100%;
  min-height: calc(64px + env(safe-area-inset-bottom));
  padding: 6px 24px env(safe-area-inset-bottom);
  border-top: 1px solid var(--border-light);
  background: #fff;
  box-shadow: 0 -8px 24px -18px rgba(15, 23, 42, .18);
  pointer-events: auto;
}

.ai-tabbar__item {
  position: relative;
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 48px;
  margin: 0;
  padding: 0;
  border: 0;
  background: transparent;
  line-height: 1;
  transition: color 0.15s ease, background 0.15s ease;
}

.ai-tabbar__item::after {
  display: none;
}

.ai-tabbar__item:active {
  background: var(--surface-muted);
}

.ai-tabbar__icon-wrap { position: relative; width: 22px; height: 22px; }

.ai-tabbar__icon {
  position: relative;
  z-index: 1;
  width: 22px;
  height: 22px;
  transition: background-color 0.15s ease;
}

.ai-tabbar__label {
  position: relative;
  z-index: 1;
  margin-top: 4px;
  color: var(--text-muted);
  font-size: 10px;
  font-weight: 700;
}

.ai-tabbar__item.is-active .ai-tabbar__label {
  color: var(--primary-color);
  font-weight: 500;
}
</style>
