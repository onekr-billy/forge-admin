<template>
  <div class="go-project-items">
    <div class="items-header go-float-up">
      <div class="header-content">
        <div class="header-left">
          <h2 class="page-title go-text-neon">
            <span class="title-accent">//</span>
            {{ $t('project.all_project') }}
          </h2>
          <p class="page-subtitle">数据可视化项目管理</p>
        </div>
      </div>
    </div>
    <project-items-list></project-items-list>
  </div>
</template>

<script setup lang="ts">
import { ProjectItemsList } from './components/ProjectItemsList'
</script>

<style lang="scss" scoped>
@include go(project-items) {
  padding: 0 32px 32px;
  min-height: calc(100vh - 2px);
}

.items-header {
  padding: 28px 0 24px;

  .header-content {
    display: flex;
    align-items: flex-end;
    justify-content: space-between;
  }

  .page-title {
    font-size: 22px;
    font-weight: 700;
    margin: 0 0 4px;
    letter-spacing: 1px;

    .title-accent {
      color: $--color-accent;
      font-weight: 300;
      margin-right: 8px;
    }
  }

  .page-subtitle {
    margin: 0;
    font-size: 12px;
    @include fetch-color(3);
    letter-spacing: 0.5px;
  }
}
</style>
