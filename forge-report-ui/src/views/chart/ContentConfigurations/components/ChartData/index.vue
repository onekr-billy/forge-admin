<template>
  <div class="go-chart-configurations-data" v-if="targetData">
    <div class="data-mode-bar">
      <span class="data-mode-label">数据模式</span>
      <div class="data-mode-actions">
        <n-select v-model:value="targetData.request.requestDataType" :disabled="isNotData" :options="selectOptions" size="small" />
        <n-button size="small" secondary @click="exportCurrentApiContract">
          <template #icon>
            <n-icon :component="DocumentTextIcon" />
          </template>
          接口文档
        </n-button>
      </div>
    </div>
    <!-- 静态 -->
    <chart-data-static v-if="targetData.request.requestDataType === RequestDataTypeEnum.STATIC"></chart-data-static>
    <!-- 动态 -->
    <chart-data-ajax v-if="targetData.request.requestDataType === RequestDataTypeEnum.AJAX"></chart-data-ajax>
    <!-- 数据池 -->
    <chart-data-pond v-if="targetData.request.requestDataType === RequestDataTypeEnum.Pond"></chart-data-pond>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { downloadTextFile, loadAsyncComponent } from '@/utils'
import { buildApiContractDocument, buildApiContractFileName } from '@/utils/apiContractExport'
import { RequestDataTypeEnum } from '@/enums/httpEnum'
import { ChartFrameEnum } from '@/packages/index.d'
import { useTargetData } from '../hooks/useTargetData.hook'
import { SelectCreateDataType, SelectCreateDataEnum } from './index.d'
import { icon } from '@/plugins'

const ChartDataStatic = loadAsyncComponent(() => import('./components/ChartDataStatic/index.vue'))
const ChartDataAjax = loadAsyncComponent(() => import('./components/ChartDataAjax/index.vue'))
const ChartDataPond = loadAsyncComponent(() => import('./components/ChartDataPond/index.vue'))

const { DocumentTextIcon } = icon.ionicons5
const { targetData, chartEditStore } = useTargetData()

// 选项
const selectOptions: SelectCreateDataType[] = [
  {
    label: SelectCreateDataEnum.STATIC,
    value: RequestDataTypeEnum.STATIC
  },
  {
    label: SelectCreateDataEnum.AJAX,
    value: RequestDataTypeEnum.AJAX
  },
  {
    label: SelectCreateDataEnum.Pond,
    value: RequestDataTypeEnum.Pond
  }
]

// 无数据源
const isNotData = computed(() => {
  return (
    targetData.value.chartConfig?.chartFrame === ChartFrameEnum.STATIC ||
    typeof targetData.value?.option?.dataset === 'undefined'
  )
})

const exportCurrentApiContract = () => {
  if (!targetData.value) return
  const document = buildApiContractDocument({
    canvasConfig: chartEditStore.getEditCanvasConfig,
    requestGlobalConfig: chartEditStore.getRequestGlobalConfig,
    componentList: chartEditStore.getComponentList,
    targetComponentIds: [targetData.value.id],
    includeStatic: true
  })

  if (!document.endpoints.length) {
    window['$message'].warning('当前组件没有可导出的数据规范')
    return
  }

  const componentName = targetData.value.chartConfig?.title || targetData.value.id
  const fileName = buildApiContractFileName(`${document.meta.projectName}-${componentName}`)
  downloadTextFile(document.markdown, fileName, 'md')
  window['$message'].success('当前组件接口规范已导出')
}
</script>

<style lang="scss" scoped>
@include go('chart-configurations-data') {
  .data-mode-bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
    margin-bottom: 14px;
    padding: 10px 14px;
    border-radius: 10px;
    border: 1px solid rgba(var(--app-theme-rgb), 0.08);
    background: rgba(15, 23, 42, 0.16);

    .data-mode-label {
      font-size: 12px;
      font-weight: 600;
      letter-spacing: 0.5px;
      color: rgba(203, 213, 225, 0.7);
      white-space: nowrap;
    }

    :deep(.n-select) {
      min-width: 140px;
    }

    .data-mode-actions {
      display: flex;
      align-items: center;
      gap: 8px;
      min-width: 0;
    }

    :deep(.n-button) {
      flex-shrink: 0;
      border-radius: 7px;
    }
  }
}
</style>
