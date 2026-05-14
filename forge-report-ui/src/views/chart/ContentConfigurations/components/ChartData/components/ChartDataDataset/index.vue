<template>
  <div class="go-chart-data-dataset">
    <n-card class="n-card-shallow">
      <setting-item-box name="数据集名称" :alone="true">
        <n-input size="small" :placeholder="targetData.request.datasetName || '暂无'" :disabled="true">
          <template #prefix>
            <n-icon :component="AlbumsIcon" />
          </template>
        </n-input>
      </setting-item-box>

      <setting-item-box name="输出字段" :alone="true">
        <n-input size="small" :placeholder="fieldsText" :disabled="true">
          <template #prefix>
            <n-icon :component="ListIcon" />
          </template>
        </n-input>
      </setting-item-box>

      <setting-item-box name="限制行数" :alone="true">
        <n-input size="small" :placeholder="`${targetData.request.datasetMaxRows || 1000}`" :disabled="true">
          <template #prefix>
            <n-icon :component="SettingsSharpIcon" />
          </template>
        </n-input>
      </setting-item-box>

      <div class="edit-text" @click="requestModelHandle">
        <div class="go-absolute-center">
          <n-button type="primary" secondary>编辑配置</n-button>
        </div>
      </div>
    </n-card>

    <setting-item-box :alone="true">
      <template #name>
        测试
        <n-tooltip trigger="hover">
          <template #trigger>
            <n-icon size="21" :depth="3">
              <help-outline-icon />
            </n-icon>
          </template>
          调用数据集运行时接口，结果赋值给 dataset 字段
        </n-tooltip>
      </template>
      <n-button type="primary" ghost @click="sendHandle">
        <template #icon>
          <n-icon>
            <flash-icon />
          </n-icon>
        </template>
        查询数据集
      </n-button>
    </setting-item-box>

    <chart-data-matching-and-show :show="showMatching && !loading" :ajax="true" />
    <fg-skeleton :load="loading" :repeat="3" />

    <chart-data-request
      v-model:modelShow="requestShow"
      :targetData="targetData"
      @sendHandle="sendHandle"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, toRaw, watchEffect } from 'vue'
import { icon } from '@/plugins'
import { customizeHttp } from '@/api/http'
import { SettingItemBox } from '@/components/Pages/ChartItemSetting'
import { ChartDataRequest } from '../ChartDataRequest'
import { ChartDataMatchingAndShow } from '../ChartDataMatchingAndShow'
import { useTargetData } from '../../../hooks/useTargetData.hook'
import { adaptDatasetForComponent, applyDatasetAdapterResult, newFunctionHandle } from '@/utils'

const { HelpOutlineIcon, FlashIcon, AlbumsIcon, ListIcon, SettingsSharpIcon } = icon.ionicons5
const { targetData, chartEditStore } = useTargetData()

const loading = ref(false)
const requestShow = ref(false)
const showMatching = ref(false)

let firstFocus = 0
let lastFilter: any = undefined

const fieldsText = computed(() => {
  const fields = targetData.value?.request?.datasetFields || []
  return fields.length ? fields.join(', ') : '默认全部可展示字段'
})

const requestModelHandle = () => {
  requestShow.value = true
}

const sendHandle = async () => {
  if (!targetData.value?.request?.datasetId) {
    window['$message'].warning('请选择数据集')
    requestShow.value = true
    return
  }

  loading.value = true
  try {
    const res = await customizeHttp(
      toRaw(targetData.value.request),
      toRaw(chartEditStore.getRequestGlobalConfig),
      toRaw(chartEditStore.getComponentList)
    )
    loading.value = false

    if (res) {
      const nextDataset = newFunctionHandle(res.data, res, targetData.value.filter)
      applyDatasetAdapterResult(targetData.value, adaptDatasetForComponent(nextDataset, targetData.value))
      showMatching.value = true
      return
    }

    window['$message'].warning('没有拿到返回值，请检查数据集配置')
  } catch (error) {
    console.error(error)
    loading.value = false
    window['$message'].warning('数据集查询异常，请检查参数')
  }
}

watchEffect(() => {
  const filter = targetData.value?.filter
  if (lastFilter !== filter && firstFocus) {
    lastFilter = filter
    sendHandle()
  }
  firstFocus++
})

onBeforeUnmount(() => {
  lastFilter = null
})
</script>

<style scoped lang="scss">
@include go('chart-data-dataset') {
  .n-card-shallow {
    position: relative;
    &.n-card {
      border-radius: 12px;
      border: 1px solid rgba(var(--app-theme-rgb), 0.12);
      background:
        linear-gradient(180deg, rgba(var(--app-theme-rgb), 0.04), transparent 50%),
        rgba(15, 23, 42, 0.22);
      @include deep() {
        .n-card__content {
          padding: 14px 12px;
        }
      }
    }
    .edit-text {
      position: absolute;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      cursor: pointer;
      opacity: 0;
      transition: all 0.28s ease;
      border-radius: 12px;
      background: rgba(8, 13, 22, 0.76);
      backdrop-filter: blur(6px);
      -webkit-backdrop-filter: blur(6px);
      display: flex;
      align-items: center;
      justify-content: center;
    }
    &:hover {
      border-color: rgba(var(--app-theme-rgb), 0.28);
      .edit-text {
        opacity: 1;
      }
    }
  }

  :deep(.go-config-item-box) {
    margin: 8px 0;
  }
}
</style>
