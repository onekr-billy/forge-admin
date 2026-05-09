<template>
  <div class="go-chart-configurations-setting" v-if="targetData">
    <div class="setting-section">
      <div class="section-title">
        <span>基础</span>
        <em>名称与几何属性</em>
      </div>
      <name-setting :chartConfig="targetData.chartConfig"></name-setting>
      <size-setting :isGroup="targetData.isGroup" :chartAttr="targetData.attr"></size-setting>
      <position-setting :chartAttr="targetData.attr" :canvasConfig="chartEditStore.getEditCanvasConfig" />
    </div>

    <div class="setting-section">
      <div class="section-title">
        <span>外观</span>
        <em>滤镜、透明度与变换</em>
      </div>
      <styles-setting :isGroup="targetData.isGroup" :chartStyles="targetData.styles"></styles-setting>
    </div>

    <div class="setting-section config-section">
      <div class="section-title">
        <span>组件配置</span>
        <em>{{ targetData.chartConfig.title }}</em>
      </div>
      <component :is="targetData.chartConfig.conKey" :optionData="targetData.option"></component>
    </div>
  </div>
</template>

<script setup lang="ts">
import { NameSetting, PositionSetting, SizeSetting, StylesSetting } from '@/components/Pages/ChartItemSetting'
import { useTargetData } from '../hooks/useTargetData.hook'
const { targetData, chartEditStore } = useTargetData()
</script>

<style lang="scss" scoped>
@include go('chart-configurations-setting') {
  padding-bottom: 18px;

  .setting-section {
    margin-bottom: 14px;
    padding: 14px 12px 8px;
    border-radius: 12px;
    border: 1px solid rgba(var(--app-theme-rgb), 0.09);
    background:
      linear-gradient(180deg, rgba(var(--app-theme-rgb), 0.04), transparent 60%),
      rgba(2, 6, 23, 0.18);
  }

  .config-section {
    padding-bottom: 14px;
  }

  .section-title {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
    gap: 8px;
    margin-bottom: 4px;
    padding: 0 4px 8px;
    border-bottom: 1px solid rgba(var(--app-theme-rgb), 0.08);

    span {
      color: var(--app-theme, $--color-primary);
      font-size: 12px;
      font-weight: 700;
      letter-spacing: 0.8px;
      text-shadow: 0 0 8px rgba(var(--app-theme-rgb), 0.18);
    }

    em {
      min-width: 0;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      font-style: normal;
      font-size: 10px;
      @include fetch-color(4);
    }
  }

  :deep(.go-config-item-box) {
    margin: 8px 0;
  }

  :deep(.go-config-item-box .item-left) {
    font-size: 11px;
    letter-spacing: 0.4px;
    @include fetch-color(3);
  }

  :deep(.n-collapse-item) {
    border-radius: 10px;
    overflow: hidden;
    margin-bottom: 8px;
    border: 1px solid rgba(var(--app-theme-rgb), 0.07);
    background: rgba(15, 23, 42, 0.18);
  }

  :deep(.n-collapse-item__header) {
    font-size: 13px;
    font-weight: 600;
    color: rgba(226, 232, 240, 0.84);
  }
}
</style>
