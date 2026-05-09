<template>
  <div class="go-project-template-market">
    <div class="market-content">
      <div class="market-icon">&#9670;</div>
      <h2 class="market-title go-text-neon">Template Market</h2>
      <p class="market-desc">模板市场正在建设中，敬请期待</p>
    </div>
  </div>
</template>

<script setup lang="ts"></script>

<style lang="scss" scoped>
@include go('project-template-market') {
  height: calc(100vh - 2px);
  @extend .go-flex-center;
}

.market-content {
  text-align: center;

  .market-icon {
    font-size: 48px;
    color: $--color-primary;
    text-shadow: 0 0 20px rgba(var(--app-theme-rgb), 0.5);
    margin-bottom: 16px;
  }

  .market-title {
    font-size: 24px;
    font-weight: 700;
    margin: 0 0 8px;
    letter-spacing: 2px;
  }

  .market-desc {
    font-size: 13px;
    @include fetch-color(3);
    margin: 0;
    letter-spacing: 1px;
  }
}
</style>
