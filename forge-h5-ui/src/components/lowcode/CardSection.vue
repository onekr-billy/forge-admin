<template>
  <view v-if="visible" class="card-section">
    <view
      v-if="title || collapsible"
      class="card-section__head"
      :class="{ 'card-section__head--interactive': collapsible }"
      @click="toggle"
    >
      <text v-if="title" class="card-section__title">{{ title }}</text>
      <view v-if="collapsible" class="card-section__toggle">
        <text class="card-section__toggle-icon">{{ collapsed ? '▼' : '▲' }}</text>
      </view>
    </view>
    <view v-show="!collapsed" class="card-section__body">
      <slot />
    </view>
  </view>
</template>

<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  title: { type: String, default: '' },
  collapsible: { type: Boolean, default: false },
  collapsedByDefault: { type: Boolean, default: false },
  visible: { type: Boolean, default: true },
})

const collapsed = ref(props.collapsible && props.collapsedByDefault)

watch(() => props.collapsedByDefault, (value) => {
  collapsed.value = props.collapsible && value
})

function toggle() {
  if (props.collapsible)
    collapsed.value = !collapsed.value
}
</script>

<style lang="scss" scoped>
.card-section {
  margin-bottom: 24rpx;
  padding: 26rpx;
  border: 1rpx solid #e7edf5;
  border-radius: 18rpx;
  background: #fff;
  box-shadow: 0 10rpx 28rpx rgba(15, 23, 42, 0.04);
}

.card-section__head {
  display: flex;
  min-height: 40rpx;
  align-items: center;
  justify-content: space-between;
  gap: 18rpx;
  margin-bottom: 18rpx;
}

.card-section__head--interactive {
  cursor: pointer;
}

.card-section__title {
  min-width: 0;
  overflow: hidden;
  color: var(--text-strong, #1e293b);
  font-size: 30rpx;
  font-weight: 850;
  line-height: 1.3;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-section__toggle {
  display: flex;
  width: 48rpx;
  height: 48rpx;
  flex: 0 0 48rpx;
  align-items: center;
  justify-content: center;
  border-radius: 12rpx;
  background: #eff6ff;
}

.card-section__body {
  min-width: 0;
}

.card-section__head + .card-section__body:empty {
  display: none;
}
</style>
