<template>
  <n-modal v-model:show="modelShowRef" @afterLeave="closeHandle">
    <div class="go-system-info">
      <div class="info-header">
        <div class="header-left">
          <span class="header-diamond">&#9670;</span>
          <span class="header-title">关于</span>
        </div>
        <n-icon size="20" class="header-close" @click="closeHandle">
          <close-icon></close-icon>
        </n-icon>
      </div>

      <div class="info-body">
        <div class="info-brand">
          <span class="brand-name go-text-neon">FORGE ADMIN</span>
          <span class="brand-ver">v1.3.2</span>
        </div>

        <div class="info-divider"></div>

        <div class="info-grid">
          <div class="info-item">
            <span class="info-label">技术架构</span>
            <span class="info-value">Vue 3 + Naive UI</span>
          </div>
          <div class="info-item">
            <span class="info-label">后端框架</span>
            <span class="info-value">Spring Boot 3.2</span>
          </div>
          <div class="info-item">
            <span class="info-label">数据可视化</span>
            <span class="info-value">ECharts / VChart</span>
          </div>
        </div>

        <div class="info-divider"></div>

        <div class="info-status">
          <span class="status-dot"></span>
          <span class="status-text">SYSTEM OPERATIONAL</span>
        </div>
      </div>
    </div>
  </n-modal>
</template>

<script lang="ts" setup>
import { ref, watch } from 'vue'
import { icon } from '@/plugins'

const props = defineProps({
  modelShow: Boolean
})

const emit = defineEmits(['update:modelShow'])
const { CloseIcon } = icon.ionicons5
const modelShowRef = ref(false)

watch(() => props.modelShow, (newValue) => {
  modelShowRef.value = newValue
})

const closeHandle = () => {
  emit('update:modelShow', false)
}
</script>

<style lang="scss" scoped>
.go-system-info {
  width: 420px;
  border-radius: $--border-radius-lg;
  overflow: hidden;
  @include fetch-bg-color('background-color1');
  border: 1px solid rgba(var(--app-theme-rgb), 0.08);
  backdrop-filter: blur(16px);
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.6);
}

.info-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 22px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.04);

  .header-left {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .header-diamond {
    font-size: 16px;
    color: $--color-primary;
    text-shadow: 0 0 8px rgba(var(--app-theme-rgb), 0.5);
  }

  .header-title {
    font-size: 16px;
    font-weight: 700;
    @include fetch-color();
    letter-spacing: 2px;
  }

  .header-close {
    @include fetch-color(3);
    cursor: pointer;
    transition: all 0.2s;
    &:hover { color: $--color-red; }
  }
}

.info-body {
  padding: 24px 22px;
}

.info-brand {
  display: flex;
  align-items: baseline;
  justify-content: center;
  gap: 12px;
  margin-bottom: 24px;

  .brand-name {
    font-size: 20px;
    font-weight: 800;
    letter-spacing: 4px;
  }

  .brand-ver {
    font-size: 12px;
    @include fetch-color(3);
    font-family: 'Courier New', monospace;
  }
}

.info-divider {
  height: 1px;
  margin: 16px 0;
  background: linear-gradient(90deg, transparent, rgba(var(--app-theme-rgb), 0.1), transparent);
}

.info-grid {
  display: flex;
  flex-direction: column;
  gap: 14px;

  .info-item {
    display: flex;
    justify-content: space-between;
    align-items: center;

    .info-label {
      font-size: 12px;
      @include fetch-color(4);
      text-transform: uppercase;
      letter-spacing: 1px;
    }

    .info-value {
      font-size: 13px;
      @include fetch-color(1);
      font-weight: 500;
    }
  }
}

.info-status {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding-top: 8px;

  .status-dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: $--color-success;
    box-shadow: 0 0 8px rgba(46, 213, 115, 0.6);
    animation: statusPulse 2s ease-in-out infinite;
  }

  .status-text {
    font-size: 10px;
    letter-spacing: 2px;
    @include fetch-color(4);
    font-family: 'Courier New', monospace;
  }
}

@keyframes statusPulse {
  0%, 100% { box-shadow: 0 0 6px rgba(46, 213, 115, 0.6); }
  50% { box-shadow: 0 0 12px rgba(46, 213, 115, 0.9); }
}
</style>
