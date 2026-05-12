<template>
  <!-- 组件配置 -->
  <n-divider class="go-my-3" title-placement="left"></n-divider>
  
  <setting-item-box
    name="接口来源"
    class="go-mt-0"
  >
    <setting-item name="数据来源">
      <n-radio-group v-model:value="requestSourceValue" @update:value="handleRequestSourceChange">
        <n-radio-button value="internal">内部接口</n-radio-button>
        <n-radio-button value="external">外部接口</n-radio-button>
      </n-radio-group>
    </setting-item>
  </setting-item-box>

  <setting-item-box
    v-show="requestSourceValue === 'internal'"
    name="地址"
    :itemRightStyle="{
      gridTemplateColumns: '6fr 2fr'
    }"
    style="padding-right: 25px"
  >
    <setting-item name="请求方式 & URL 地址">
      <n-input-group>
        <n-select class="select-type-options" v-model:value="requestHttpType" :options="selectTypeOptions" />
        <n-input v-model:value.trim="requestUrl" :min="1" placeholder="请输入地址（去除前置URL）">
          <template #prefix>
            <n-text>{{ requestOriginUrl }}</n-text>
            <n-divider vertical />
          </template>
        </n-input>
      </n-input-group>
      <n-tooltip trigger="hover" v-if="isDev()">
        <template #trigger>
          <n-text class="dev-api-tip" depth="3">查看 mock 地址</n-text>
        </template>
        <ul class="go-pl-0">
          开发环境使用 mock 数据，请输入
          <li v-for="item in apiList" :key="item.value">
            <n-text type="info"> {{ item.value }} </n-text>
          </li>
        </ul>
      </n-tooltip>
    </setting-item>

    <setting-item name="更新间隔，为 0 只会初始化">
      <n-input-group>
        <n-input-number
          v-model:value.trim="requestInterval"
          class="select-time-number"
          min="0"
          :show-button="false"
          placeholder="默认使用全局数据"
        >
        </n-input-number>
        <n-select class="select-time-options" v-model:value="requestIntervalUnit" :options="selectTimeOptions" />
      </n-input-group>
    </setting-item>
  </setting-item-box>

  <div v-show="requestSourceValue === 'external'" class="external-request-config">
    <setting-item-box name="外部接口" class="go-mt-0">
      <setting-item name="外部系统">
        <n-select
          v-model:value="externalSystemId"
          :options="externalSystemOptions"
          :loading="externalSystemLoading"
          placeholder="请选择外部系统"
          filterable
          clearable
          @focus="loadExternalSystemOptions"
          @update:value="handleExternalSystemChange"
        />
      </setting-item>

      <setting-item name="选择接口">
        <n-select
          v-model:value="externalApiId"
          :options="externalApiOptions"
          :loading="externalApiLoading"
          :disabled="!externalSystemId"
          placeholder="请先选择外部系统"
          filterable
          remote
          clearable
          @focus="() => loadExternalApiOptions()"
          @search="handleExternalApiSearch"
          @update:value="handleExternalApiChange"
        />
      </setting-item>

      <setting-item name="更新间隔，为 0 只会初始化">
        <n-input-group>
          <n-input-number
            v-model:value.trim="requestInterval"
            class="select-time-number"
            min="0"
            :show-button="false"
            placeholder="默认使用全局数据"
          >
          </n-input-number>
          <n-select class="select-time-options" v-model:value="requestIntervalUnit" :options="selectTimeOptions" />
        </n-input-group>
      </setting-item>
    </setting-item-box>
  </div>

  <setting-item-box name="动态参数" class="go-mt-0">
    <setting-item name="参数绑定">
      <n-space align="center">
        <n-button size="small" @click="showDynamicParamModal = true">
          <template #icon>
            <n-icon><edit-icon /></n-icon>
          </template>
          配置参数
        </n-button>
        <n-text v-if="dynamicParamsText" depth="3">{{ dynamicParamsText }}</n-text>
      </n-space>
    </setting-item>
  </setting-item-box>

  <n-modal v-model:show="showDynamicParamModal" preset="dialog" title="配置动态请求参数" style="width: 860px;">
    <n-space vertical :size="12">
      <div
        v-for="(item, index) in dynamicRequestParams"
        :key="item.id"
        class="dynamic-param-row"
      >
        <n-switch v-model:value="item.enabled" size="small" />
        <n-select
          v-model:value="item.target"
          :options="dynamicTargetOptions"
          size="small"
        />
        <n-input
          v-model:value.trim="item.targetKey"
          size="small"
          placeholder="参数名"
        />
        <n-select
          v-model:value="item.source"
          :options="dynamicSourceOptions"
          size="small"
          @update:value="() => handleDynamicSourceChange(item)"
        />
        <n-select
          v-if="item.source === 'context'"
          v-model:value="item.sourceKey"
          :options="contextParamOptions"
          size="small"
          filterable
          placeholder="上下文字段"
        />
        <template v-else-if="item.source === 'component'">
          <n-select
            v-model:value="item.componentId"
            :options="filterComponentOptions"
            size="small"
            filterable
            placeholder="筛选组件"
          />
          <n-select
            v-model:value="item.componentField"
            :options="componentParamOptions"
            size="small"
            placeholder="组件值"
          />
        </template>
        <template v-else-if="item.source === 'preset'">
          <n-select
            v-model:value="item.presetType"
            :options="presetParamOptions"
            size="small"
            placeholder="预设类型"
          />
          <n-input-number
            v-model:value="item.offsetDays"
            size="small"
            :min="0"
            placeholder="T-N"
          />
        </template>
        <n-input
          v-else
          v-model:value="item.customValue"
          size="small"
          placeholder="固定值"
        />
        <n-input
          v-model:value="item.fallbackValue"
          size="small"
          placeholder="默认值"
        />
        <n-button size="small" type="error" text @click="removeDynamicParam(index)">删除</n-button>
      </div>
      <n-space>
        <n-button size="small" type="primary" dashed @click="addDynamicParam">新增参数</n-button>
        <n-button size="small" type="primary" secondary @click="addTnDateRangeParams">新增 T-N 时间范围</n-button>
      </n-space>
    </n-space>
    <template #action>
      <n-space justify="end">
        <n-button @click="showDynamicParamModal = false">取消</n-button>
        <n-button type="primary" @click="handleSaveDynamicParams">确定</n-button>
      </n-space>
    </template>
  </n-modal>
</template>

<script setup lang="ts">
import { PropType, toRefs, ref, computed, onMounted, watch } from 'vue'
import { SettingItemBox, SettingItem } from '@/components/Pages/ChartItemSetting'
import { useTargetData } from '@/views/chart/ContentConfigurations/components/hooks/useTargetData.hook'
import { selectTypeOptions, selectTimeOptions } from '@/views/chart/ContentConfigurations/components/ChartData/index.d'
import { RequestConfigType } from '@/store/modules/chartEditStore/chartEditStore.d'
import { isDev } from '@/utils'
import { icon } from '@/plugins'
import { useMessage } from 'naive-ui'
import {
  graphUrl,
  chartDataUrl,
  chartSingleDataUrl,
  rankListUrl,
  scrollBoardUrl,
  numberFloatUrl,
  numberIntUrl,
  textUrl,
  imageUrl,
  radarUrl,
  heatMapUrl,
  scatterBasicUrl,
  mapUrl,
  capsuleUrl,
  wordCloudUrl,
  treemapUrl,
  threeEarth01Url,
  sankeyUrl,
  vchartBarDataUrl
} from '@/api/mock'
import {
  getExternalApiDetailApi,
  getExternalApiPageApi,
  getExternalSystemListApi,
  ExternalSystemVO
} from '@/api/external/api'
import {
  contextParamOptions,
  componentParamOptions,
  presetParamOptions,
  createDynamicParamBinding
} from '@/utils/requestDynamicParams'
import type { DynamicRequestParamBinding } from '@/store/modules/chartEditStore/chartEditStore.d'

const props = defineProps({
  targetDataRequest: Object as PropType<RequestConfigType>
})

const message = useMessage()
const { EditIcon } = icon.ionicons5
const { chartEditStore } = useTargetData()
const { requestOriginUrl } = toRefs(chartEditStore.getRequestGlobalConfig)
const { requestInterval, requestIntervalUnit, requestHttpType, requestUrl } = toRefs(
  props.targetDataRequest as RequestConfigType
)

const requestSourceValue = ref<'internal' | 'external'>('internal')

const handleRequestSourceChange = (value: 'internal' | 'external') => {
  requestSourceValue.value = value
  const requestConfig = props.targetDataRequest as RequestConfigType
  requestConfig.requestSource = value
  if (value === 'external') {
    loadExternalSystemOptions()
  }
}

const externalSystemId = computed({
  get: () => (props.targetDataRequest as RequestConfigType).externalSystemId || null,
  set: (val) => {
    (props.targetDataRequest as RequestConfigType).externalSystemId = val
  }
})

const externalApiId = computed({
  get: () => (props.targetDataRequest as RequestConfigType).externalApiId || null,
  set: (val) => {
    (props.targetDataRequest as RequestConfigType).externalApiId = val
  }
})

const showDynamicParamModal = ref(false)
const externalSystemLoading = ref(false)
const externalApiLoading = ref(false)
const externalApiKeyword = ref('')
const externalSystemOptions = ref<{ label: string; value: number; systemCode?: string }[]>([])
const externalApiOptions = ref<{ label: string; value: number; systemId: number; apiPath: string }[]>([])
const dynamicRequestParams = computed({
  get: () => {
    const requestConfig = props.targetDataRequest as RequestConfigType
    if (!requestConfig.dynamicRequestParams) {
      requestConfig.dynamicRequestParams = []
    }
    return requestConfig.dynamicRequestParams as DynamicRequestParamBinding[]
  },
  set: (val: DynamicRequestParamBinding[]) => {
    (props.targetDataRequest as RequestConfigType).dynamicRequestParams = val
  }
})
const dynamicParamsText = computed(() => {
  const count = dynamicRequestParams.value.filter(item => item.enabled && item.targetKey).length
  return count ? `已配置 ${count} 个动态参数` : ''
})
const dynamicTargetOptions = [
  { label: 'Query', value: 'Params' },
  { label: 'Header', value: 'Header' },
  { label: 'Body', value: 'Body' }
]
const dynamicSourceOptions = [
  { label: '登录人', value: 'context' },
  { label: '筛选组件', value: 'component' },
  { label: '预设条件', value: 'preset' },
  { label: '固定值', value: 'custom' }
]
const filterComponentOptions = computed(() => {
  return chartEditStore.getComponentList
    .filter(item => item.key?.startsWith('Inputs'))
    .map(item => ({
      label: `${item.chartConfig?.title || item.key} (${item.id})`,
      value: item.id
    }))
})

const apiList = [
  {
    value: `【图表】${chartDataUrl}`
  },
  {
    value: `【单数据图表】${chartSingleDataUrl}`
  },
  {
    value: `【文本】${textUrl}`
  },
  {
    value: `【0~100 整数】${numberIntUrl}`
  },
  {
    value: `【0~1小数】${numberFloatUrl}`
  },
  {
    value: `【图片地址】${imageUrl}`
  },
  {
    value: `【排名列表】${rankListUrl}`
  },
  {
    value: `【滚动表格】${scrollBoardUrl}`
  },
  {
    value: `【雷达】${radarUrl}`
  },
  {
    value: `【热力图】${heatMapUrl}`
  },
  {
    value: `【基础散点图】${scatterBasicUrl}`
  },
  {
    value: `【地图数据】${mapUrl}`
  },
  {
    value: `【胶囊柱图】${capsuleUrl}`
  },
  {
    value: `【词云】${wordCloudUrl}`
  },
  {
    value: `【树图】${treemapUrl}`
  },
  {
    value: `【三维地球】${threeEarth01Url}`
  },
{
    value: `【桑基图】${sankeyUrl}`
  },
  {
    value: `【关系图】${graphUrl}`
  },
  {
    value: `【VChart 柱状图】${vchartBarDataUrl}`
  }
]

const loadExternalSystemOptions = async () => {
  if (externalSystemLoading.value || externalSystemOptions.value.length) return
  externalSystemLoading.value = true
  try {
    const res = await getExternalSystemListApi()
    externalSystemOptions.value = (res.data || [])
      .filter((system: ExternalSystemVO) => system.systemStatus === undefined || system.systemStatus === 1)
      .map(system => ({
        label: `${system.systemName}${system.systemCode ? ` (${system.systemCode})` : ''}`,
        value: system.id,
        systemCode: system.systemCode
      }))
  } catch (error) {
    message.error('加载外部系统列表失败')
  } finally {
    externalSystemLoading.value = false
  }
}

const loadExternalApiOptions = async (keyword = externalApiKeyword.value) => {
  if (!externalSystemId.value) {
    externalApiOptions.value = []
    return
  }
  if (externalApiLoading.value) return
  externalApiLoading.value = true
  externalApiKeyword.value = keyword || ''
  try {
    const res = await getExternalApiPageApi({
      pageNum: 1,
      pageSize: 50,
      systemId: externalSystemId.value,
      apiName: externalApiKeyword.value || undefined,
      apiStatus: 1
    })
    externalApiOptions.value = (res.data?.records || [])
      .filter(api => api.apiStatus === undefined || api.apiStatus === 1)
      .map(api => ({
        label: `${api.apiName}${api.apiCode ? ` (${api.apiCode})` : ''} - ${api.apiMethod || 'GET'} ${api.apiPath}`,
        value: api.id as number,
        systemId: api.systemId,
        apiPath: api.apiPath
      }))
  } catch (error) {
    message.error('加载外部接口列表失败')
  } finally {
    externalApiLoading.value = false
  }
}

const appendExternalApiOption = (api: {
  id: number
  systemId: number
  apiName: string
  apiCode?: string
  apiMethod?: string
  apiPath: string
}) => {
  const exists = externalApiOptions.value.some(item => item.value === api.id)
  if (exists) return
  externalApiOptions.value.unshift({
    label: `${api.apiName}${api.apiCode ? ` (${api.apiCode})` : ''} - ${api.apiMethod || 'GET'} ${api.apiPath}`,
    value: api.id,
    systemId: api.systemId,
    apiPath: api.apiPath
  })
}

const loadSelectedExternalApi = async () => {
  if (!externalApiId.value) return
  try {
    const res = await getExternalApiDetailApi(externalApiId.value)
    const api = res.data
    if (!api) return
    externalSystemId.value = api.systemId || externalSystemId.value
    appendExternalApiOption(api)
  } catch (error) {
    // 旧配置回显失败不阻断已有 externalApiId 的请求执行
  }
}

const handleExternalSystemChange = (value: number | null) => {
  externalSystemId.value = value || null
  externalApiId.value = null
  externalApiOptions.value = []
  externalApiKeyword.value = ''
  if (value) {
    loadExternalApiOptions('')
  }
}

const handleExternalApiSearch = (keyword: string) => {
  loadExternalApiOptions(keyword)
}

const handleExternalApiChange = (value: number | null) => {
  externalApiId.value = value || null
  ;(props.targetDataRequest as RequestConfigType).externalRequestParams = {}
}

const addDynamicParam = () => {
  dynamicRequestParams.value.push(createDynamicParamBinding())
}

const removeDynamicParam = (index: number) => {
  dynamicRequestParams.value.splice(index, 1)
}

const addTnDateRangeParams = () => {
  const startParam = createDynamicParamBinding()
  startParam.target = 'Params'
  startParam.targetKey = 'startTime'
  startParam.source = 'preset'
  startParam.presetType = 'tn-day-start'
  startParam.offsetDays = 1
  startParam.dateFormat = 'YYYY-MM-DD HH:mm:ss'

  const endParam = createDynamicParamBinding()
  endParam.target = 'Params'
  endParam.targetKey = 'endTime'
  endParam.source = 'preset'
  endParam.presetType = 'tn-day-end'
  endParam.offsetDays = 1
  endParam.dateFormat = 'YYYY-MM-DD HH:mm:ss'

  dynamicRequestParams.value.push(startParam, endParam)
}

const handleDynamicSourceChange = (item: DynamicRequestParamBinding) => {
  if (item.source === 'context') {
    item.sourceKey = item.sourceKey || 'userId'
    item.componentId = undefined
    item.componentField = 'value'
    item.customValue = ''
    item.presetType = undefined
  } else if (item.source === 'component') {
    item.sourceKey = undefined
    item.componentField = item.componentField || 'value'
    item.customValue = ''
    item.presetType = undefined
  } else if (item.source === 'preset') {
    item.sourceKey = undefined
    item.componentId = undefined
    item.componentField = undefined
    item.customValue = ''
    item.presetType = item.presetType || 'tn-day-start'
    item.offsetDays = item.offsetDays ?? 1
    item.dateFormat = item.dateFormat || 'YYYY-MM-DD HH:mm:ss'
  } else {
    item.sourceKey = undefined
    item.componentId = undefined
    item.componentField = undefined
    item.presetType = undefined
  }
}

const handleSaveDynamicParams = () => {
  const invalidItem = dynamicRequestParams.value.find(item => item.enabled && !item.targetKey)
  if (invalidItem) {
    message.error('请填写动态参数名')
    return
  }
  showDynamicParamModal.value = false
  message.success('动态参数配置已保存')
}

onMounted(() => {
  const requestConfig = props.targetDataRequest as RequestConfigType
  requestSourceValue.value = requestConfig.requestSource || 'internal'
  requestConfig.requestSource = requestSourceValue.value
  if (requestSourceValue.value === 'external') {
    loadExternalSystemOptions()
    if (requestConfig.externalApiId) {
      loadSelectedExternalApi().then(() => loadExternalApiOptions())
    } else if (requestConfig.externalSystemId) {
      loadExternalApiOptions()
    }
  }
})

watch(
  () => props.targetDataRequest?.requestSource,
  value => {
    requestSourceValue.value = value || 'internal'
  }
)
</script>

<style lang="scss" scoped>
.select-time-number {
  width: 100%;
}
.select-time-options {
  width: 100px;
}
.select-type-options {
  width: 120px;
}
.dev-api-tip {
  display: inline-block;
  margin-top: 6px;
  cursor: pointer;
}
.external-request-config {
  :deep(.go-config-item-box) {
    padding-right: 25px;
  }
}
.dynamic-param-row {
  display: grid;
  grid-template-columns: 54px 90px 120px 100px minmax(130px, 1fr) minmax(100px, 0.75fr) minmax(90px, 0.65fr) 46px;
  gap: 8px;
  align-items: center;
}
</style>
