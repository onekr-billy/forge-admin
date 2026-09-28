<template>
  <view class="home-skeleton" aria-busy="true" aria-label="首页加载中">
    <view class="home-skeleton__header">
      <view class="home-skeleton__avatar pulse" />
      <view class="home-skeleton__copy">
        <view class="home-skeleton__line home-skeleton__line--title pulse" />
        <view class="home-skeleton__line home-skeleton__line--short pulse" />
      </view>
      <view class="home-skeleton__button pulse" />
    </view>

    <view class="home-skeleton__dashboard">
      <view class="home-skeleton__panel home-skeleton__overview">
        <SkeletonHead />
        <view class="home-skeleton__metric-grid">
          <view v-for="index in 2" :key="`metric-${index}`" class="home-skeleton__metric pulse" />
        </view>
      </view>

      <view class="home-skeleton__panel home-skeleton__apps">
        <SkeletonHead />
        <view class="home-skeleton__app-grid">
          <view v-for="index in 4" :key="`app-${index}`" class="home-skeleton__app pulse" />
        </view>
      </view>

      <view class="home-skeleton__panel home-skeleton__feed">
        <SkeletonHead />
        <view v-for="index in 2" :key="`feed-${index}`" class="home-skeleton__feed-row">
          <view class="home-skeleton__feed-icon pulse" />
          <view class="home-skeleton__copy">
            <view class="home-skeleton__line pulse" />
            <view class="home-skeleton__line home-skeleton__line--short pulse" />
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import SkeletonHead from './SkeletonHead.vue'
</script>

<style lang="scss" scoped>
.home-skeleton { display: flex; flex-direction: column; gap: 12px; }
.home-skeleton__header, .home-skeleton__panel { border: 1rpx solid var(--border-light); border-radius: var(--radius-card); background: #fff; box-sizing: border-box; }
.home-skeleton__header { display: flex; min-height: 64px; align-items: center; gap: 10px; padding: 10px 14px; }
.home-skeleton__avatar { width: 40px; height: 40px; flex: 0 0 40px; border-radius: 50%; }
.home-skeleton__copy { min-width: 0; flex: 1; }
.home-skeleton__button { width: 44px; height: 44px; flex: 0 0 44px; border-radius: var(--radius-control); }
.home-skeleton__dashboard { display: flex; flex-direction: column; gap: 12px; }
.home-skeleton__panel { overflow: hidden; }
.home-skeleton__metric-grid, .home-skeleton__app-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; padding: 12px 14px; }
.home-skeleton__metric { min-height: 104px; border-radius: var(--radius-card); }
.home-skeleton__app { min-height: 64px; border-radius: var(--radius-control); }
.home-skeleton__feed-row { display: flex; min-height: 56px; align-items: center; gap: 10px; padding: 10px 14px; border-top: 1rpx solid var(--border-light); }
.home-skeleton__feed-icon { width: 32px; height: 32px; flex: 0 0 32px; border-radius: var(--radius-control); }
.home-skeleton__line { width: 72%; height: 10px; border-radius: 6px; }
.home-skeleton__line + .home-skeleton__line { margin-top: 8px; }
.home-skeleton__line--title { width: 48%; height: 13px; }
.home-skeleton__line--short { width: 34%; }
.pulse { background: #e2e8f0; animation: home-skeleton-pulse 1.1s ease-in-out infinite alternate; }
@keyframes home-skeleton-pulse { from { opacity: .58; } to { opacity: 1; } }
@media (min-width: 1024px) {
  .home-skeleton__dashboard { display: grid; grid-template-areas: "overview apps" "feed apps"; grid-template-columns: minmax(0, 1.4fr) minmax(320px, .9fr); align-items: start; }
  .home-skeleton__overview { grid-area: overview; }
  .home-skeleton__apps { grid-area: apps; }
  .home-skeleton__feed { grid-area: feed; }
}
</style>
