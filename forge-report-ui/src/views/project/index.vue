<template>
  <div class="go-project">
    <div class="go-project-bg"></div>
    <n-layout has-sider position="absolute" class="go-project-layout">
      <project-layout-sider></project-layout-sider>
      <n-layout class="go-project-main">
        <n-layout
          class="go-project-content"
          position="absolute"
          :native-scrollbar="false"
        >
          <n-layout-content>
            <layout-transition-main>
              <router-view></router-view>
            </layout-transition-main>
          </n-layout-content>
        </n-layout>
      </n-layout>
    </n-layout>
  </div>
</template>

<script setup lang="ts">
import { ProjectLayoutSider } from './layout/components/ProjectLayoutSider'
import { LayoutTransitionMain } from '@/layout/components/LayoutTransitionMain/index'
</script>

<style lang="scss" scoped>
@include go(project) {
  position: relative;
  width: 100%;
  height: 100vh;
  overflow: hidden;
  @include fetch-bg-color('background-color');

  &-bg {
    position: absolute;
    inset: 0;
    pointer-events: none;
    z-index: 0;
    background-image:
      radial-gradient(circle at 1px 1px, rgba(var(--app-theme-rgb), 0.04) 1px, transparent 0);
    background-size: 40px 40px;
  }

  &-layout {
    position: relative;
    z-index: 1;
  }

  &-main {
    @include fetch-bg-color('background-color');
  }

  &-content {
    top: 0;
    bottom: 0;
  }
}
</style>
