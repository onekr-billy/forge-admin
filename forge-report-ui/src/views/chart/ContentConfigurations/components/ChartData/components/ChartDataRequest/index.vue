<template>
  <n-modal
    class="go-chart-data-request"
    v-model:show="modelShowRef"
    :mask-closable="false"
    :closeOnEsc="true"
    :onEsc="onEsc"
  >
    <n-card :bordered="false" role="dialog" size="small" aria-modal="true" style="width: 1000px; height: 800px">
      <template #header></template>
      <template #header-extra> </template>
      <n-scrollbar style="max-height: 718px">
        <div class="go-pr-3">
          <n-space vertical>
            <request-global-config></request-global-config>
            <request-target-config :target-data-request="targetData?.request"></request-target-config>
          </n-space>
        </div>
      </n-scrollbar>
      <!-- 底部 -->
      <template #action>
        <n-space justify="space-between">
          <div>
            <n-text>「 {{ chartConfig.categoryName }} 」</n-text>
            <n-text>—— </n-text>
            <n-tag type="primary" :bordered="false" style="border-radius: 5px">
              {{ requestContentTypeObj[requestContentType] }}
            </n-tag>
          </div>
          <div>
            <n-button class="go-mr-3" @click="closeHandle">取消</n-button>
            <n-button type="primary" @click="closeAndSendHandle"> {{ saveBtnText || '保存 & 发送请求' }}</n-button>
          </div>
        </n-space>
      </template>
    </n-card>
  </n-modal>
</template>

<script script lang="ts" setup>
import { ref, toRefs, PropType, watch } from 'vue'
import { RequestContentTypeEnum } from '@/enums/httpEnum'
import { useTargetData } from '../../../hooks/useTargetData.hook'
import { RequestGlobalConfig } from './components/RequestGlobalConfig'
import { RequestTargetConfig } from './components/RequestTargetConfig'
import { CreateComponentType, CreateComponentGroupType } from '@/packages/index.d'

const props = defineProps({
  modelShow: Boolean,
  targetData: Object as PropType<CreateComponentType>,
  saveBtnText: String || null
})
const emit = defineEmits(['update:modelShow', 'sendHandle'])

// 解构基础配置
const { chartConfig } = toRefs(props.targetData as CreateComponentType)
const { requestContentType } = toRefs((props.targetData as CreateComponentType).request)
const modelShowRef = ref(false)
const requestContentTypeObj = {
  [RequestContentTypeEnum.DEFAULT]: '普通请求',
  [RequestContentTypeEnum.SQL]: 'SQL 请求'
}

watch(
  () => props.modelShow,
  newValue => {
    modelShowRef.value = newValue
  },
  {
    immediate: true
  }
)

const closeHandle = () => {
  emit('update:modelShow', false)
}

const closeAndSendHandle = () => {
  emit('update:modelShow', false)
  emit('sendHandle')
}

const onEsc = () => {
  closeHandle()
}
</script>

<style lang="scss" scoped>
@include go('chart-data-request') {
  :deep(.n-card) {
    border-radius: 16px;
    border: 1px solid rgba(var(--app-theme-rgb), 0.12);
    background:
      linear-gradient(180deg, rgba(var(--app-theme-rgb), 0.06), transparent 40%),
      rgba(10, 14, 23, 0.94);
    backdrop-filter: blur(24px);
    -webkit-backdrop-filter: blur(24px);
    box-shadow:
      0 0 80px rgba(var(--app-theme-rgb), 0.08),
      0 24px 60px rgba(0, 0, 0, 0.5);
  }

  :deep(.n-card__content) {
    padding: 0 20px;
  }

  :deep(.n-card__action) {
    padding: 12px 20px 16px;
    border-top: 1px solid rgba(var(--app-theme-rgb), 0.08);
  }

  :deep(.n-button) {
    border-radius: 8px;
    transition: all 0.22s ease;
  }

  :deep(.n-button--primary-type) {
    box-shadow: 0 0 18px rgba(var(--app-theme-rgb), 0.2);
  }

  :deep(.n-tag) {
    border-radius: 6px;
    padding: 2px 10px;
  }

  :deep(.n-collapse-item) {
    border-radius: 10px;
    overflow: hidden;
    margin-bottom: 8px;
    border: 1px solid rgba(var(--app-theme-rgb), 0.08);
    background: rgba(15, 23, 42, 0.16);
  }

  :deep(.n-collapse-item__header) {
    padding: 10px 14px;
    font-size: 13px;
    font-weight: 600;
    color: rgba(226, 232, 240, 0.86);
  }

  :deep(.n-input),
  :deep(.n-base-selection) {
    border-radius: 8px;
  }

  :deep(.go-config-item-box) {
    margin: 10px 0;
  }
}
</style>
