<template>
  <!-- 组件配置 -->
  <n-divider class="go-my-3" title-placement="left"></n-divider>
  
  <setting-item-box
    v-show="!isDatasetRequest"
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
    v-show="!isDatasetRequest && requestSourceValue === 'internal'"
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

  <div v-show="!isDatasetRequest && requestSourceValue === 'external'" class="external-request-config">
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

  <div v-show="isDatasetRequest" class="dataset-request-config">
    <setting-item-box name="数据库数据集" class="go-mt-0">
      <setting-item name="数据连接">
        <n-select
          v-model:value="datasetConnectionId"
          :options="datasetConnectionOptions"
          :loading="datasetConnectionLoading"
          placeholder="请选择数据连接"
          filterable
          clearable
          @focus="loadDatasetConnectionOptions"
          @update:value="handleDatasetConnectionChange"
        />
      </setting-item>

      <setting-item name="数据集">
        <n-select
          v-model:value="datasetId"
          :options="datasetOptions"
          :loading="datasetLoading"
          :disabled="!datasetConnectionId"
          placeholder="请先选择数据连接"
          filterable
          clearable
          @focus="() => loadDatasetOptions()"
          @update:value="handleDatasetChange"
        />
      </setting-item>

      <setting-item name="输出字段">
        <n-select
          v-model:value="datasetFields"
          :options="datasetFieldOptions"
          :loading="datasetMetadataLoading"
          :disabled="!datasetId"
          placeholder="默认全部可展示字段"
          multiple
          filterable
          clearable
        />
      </setting-item>

      <setting-item v-if="datasetParamOptions.length" name="可用查询条件">
        <n-space :size="6" wrap>
          <n-tag
            v-for="item in datasetParamOptions"
            :key="item.value"
            size="small"
            :bordered="false"
            type="info"
          >
            {{ item.label }}
          </n-tag>
        </n-space>
      </setting-item>

      <setting-item v-if="showNameValueMapping" name="字段映射">
        <n-input-group>
          <n-select
            v-model:value="datasetNameField"
            :options="datasetFieldOptions"
            :disabled="!datasetFieldOptions.length"
            placeholder="名称字段"
            filterable
            clearable
          />
          <n-select
            v-model:value="datasetValueField"
            :options="datasetFieldOptions"
            :disabled="!datasetFieldOptions.length"
            placeholder="数值字段"
            filterable
            clearable
          />
        </n-input-group>
      </setting-item>

      <setting-item v-if="showHeaderMapping" name="表头同步">
        <n-switch v-model:value="datasetSyncHeader">
          <template #checked>使用字段标签</template>
          <template #unchecked>保持组件配置</template>
        </n-switch>
      </setting-item>

      <setting-item name="分页与限制">
        <n-input-group>
          <n-input-number
            v-model:value="datasetPageSize"
            class="select-time-number"
            :min="1"
            :max="datasetMaxRows || 10000"
            :show-button="false"
            placeholder="每页条数"
          />
          <n-input-number
            v-model:value="datasetMaxRows"
            class="select-time-number"
            :min="1"
            :max="10000"
            :show-button="false"
            placeholder="最大行数"
          />
        </n-input-group>
      </setting-item>

      <setting-item name="更新间隔，为 0 只会初始化">
        <n-input-group>
          <n-input-number
            v-model:value.trim="requestInterval"
            class="select-time-number"
            min="0"
            :show-button="false"
            placeholder="默认使用全局数据"
          />
          <n-select class="select-time-options" v-model:value="requestIntervalUnit" :options="selectTimeOptions" />
        </n-input-group>
      </setting-item>
    </setting-item-box>
  </div>

  <setting-item-box name="查询条件绑定" class="go-mt-0">
    <setting-item name="绑定配置">
      <n-space align="center">
        <n-button size="small" @click="showDynamicParamModal = true">
          <template #icon>
            <n-icon><edit-icon /></n-icon>
          </template>
          配置绑定
        </n-button>
        <n-text v-if="dynamicParamsText" depth="3">{{ dynamicParamsText }}</n-text>
      </n-space>
    </setting-item>
  </setting-item-box>

  <n-modal v-model:show="showDynamicParamModal" preset="dialog" title="配置查询条件绑定" style="width: 860px;">
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
        <n-select
          v-if="showDatasetParamTarget(item)"
          v-model:value="item.targetKey"
          :options="datasetParamOptions"
          size="small"
          filterable
          clearable
          placeholder="选择查询条件"
        />
        <n-input
          v-else
          v-model:value.trim="item.targetKey"
          size="small"
          placeholder="目标参数名"
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
  getDataConnectionList,
  getDataDatasetList,
  getDataDatasetMetadata,
  DataConnectionOption,
  DataDatasetField,
  DataDatasetOption
} from '@/api/data/dataset'
import {
  contextParamOptions,
  componentParamOptions,
  presetParamOptions,
  createDynamicParamBinding
} from '@/utils/requestDynamicParams'
import type { DynamicRequestParamBinding } from '@/store/modules/chartEditStore/chartEditStore.d'
import { RequestDataTypeEnum } from '@/enums/httpEnum'

const props = defineProps({
  targetDataRequest: Object as PropType<RequestConfigType>
})

interface DatasetParamOption {
  label: string
  value: string
  dataType?: string
  operator?: string
  fieldName?: string
}

const message = useMessage()
const { EditIcon } = icon.ionicons5
const { chartEditStore, targetData } = useTargetData()
const { requestOriginUrl } = toRefs(chartEditStore.getRequestGlobalConfig)
const { requestInterval, requestIntervalUnit, requestHttpType, requestUrl } = toRefs(
  props.targetDataRequest as RequestConfigType
)

const requestSourceValue = ref<'internal' | 'external'>('internal')
const isDatasetRequest = computed(() => {
  return (props.targetDataRequest as RequestConfigType)?.requestDataType === RequestDataTypeEnum.DATASET
})

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
const datasetConnectionLoading = ref(false)
const datasetLoading = ref(false)
const datasetMetadataLoading = ref(false)
const datasetConnectionOptions = ref<{ label: string; value: number; connectionCode?: string }[]>([])
const datasetOptions = ref<{ label: string; value: number; datasetCode?: string; connectionId?: number }[]>([])
const datasetFieldOptions = ref<{ label: string; value: string; dataType?: string; fieldRole?: string }[]>([])
const datasetParamOptions = ref<DatasetParamOption[]>([])
const showNameValueMapping = computed(() => targetData.value?.key === 'TableList')
const showHeaderMapping = computed(() => targetData.value?.key === 'TableScrollBoard')
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
  return count ? `已配置 ${count} 个条件绑定` : ''
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

const datasetConnectionId = computed({
  get: () => (props.targetDataRequest as RequestConfigType).datasetConnectionId || null,
  set: (val) => {
    (props.targetDataRequest as RequestConfigType).datasetConnectionId = val
  }
})

const datasetId = computed({
  get: () => (props.targetDataRequest as RequestConfigType).datasetId || null,
  set: (val) => {
    (props.targetDataRequest as RequestConfigType).datasetId = val
  }
})

const datasetFields = computed({
  get: () => (props.targetDataRequest as RequestConfigType).datasetFields || [],
  set: (val: string[]) => {
    const requestConfig = props.targetDataRequest as RequestConfigType
    requestConfig.datasetFields = val
    ensureDatasetMapping().outputFields = val || []
  }
})

const ensureDatasetMapping = () => {
  const requestConfig = props.targetDataRequest as RequestConfigType
  if (!requestConfig.datasetMapping) {
    requestConfig.datasetMapping = {
      mode: 'auto',
      fieldMap: {},
      outputFields: [],
      syncHeader: true
    }
  }
  requestConfig.datasetMapping.fieldMap = requestConfig.datasetMapping.fieldMap || {}
  requestConfig.datasetMapping.outputFields = requestConfig.datasetMapping.outputFields || []
  if (requestConfig.datasetMapping.syncHeader === undefined) {
    requestConfig.datasetMapping.syncHeader = true
  }
  return requestConfig.datasetMapping
}

const datasetNameField = computed({
  get: () => ensureDatasetMapping().fieldMap?.name || null,
  set: (val: string | null) => {
    ensureDatasetMapping().fieldMap!.name = val || ''
  }
})

const datasetValueField = computed({
  get: () => ensureDatasetMapping().fieldMap?.value || null,
  set: (val: string | null) => {
    ensureDatasetMapping().fieldMap!.value = val || ''
  }
})

const datasetSyncHeader = computed({
  get: () => ensureDatasetMapping().syncHeader !== false,
  set: (val: boolean) => {
    ensureDatasetMapping().syncHeader = val
  }
})

const showDatasetParamTarget = (item: DynamicRequestParamBinding) => {
  return isDatasetRequest.value && item.target === 'Params' && datasetParamOptions.value.length > 0
}

const datasetPageSize = computed({
  get: () => (props.targetDataRequest as RequestConfigType).datasetPageSize || 50,
  set: (val) => {
    (props.targetDataRequest as RequestConfigType).datasetPageSize = val || 50
  }
})

const datasetMaxRows = computed({
  get: () => (props.targetDataRequest as RequestConfigType).datasetMaxRows || 1000,
  set: (val) => {
    (props.targetDataRequest as RequestConfigType).datasetMaxRows = val || 1000
  }
})

const resolveDefaultNameField = () => {
  return (
    datasetFieldOptions.value.find(item => item.fieldRole === 'DIMENSION')?.value ||
    datasetFieldOptions.value.find(item => ['STRING', 'DATE', 'DATETIME'].includes(item.dataType || ''))?.value ||
    datasetFieldOptions.value[0]?.value ||
    ''
  )
}

const resolveDefaultValueField = (nameField: string) => {
  return (
    datasetFieldOptions.value.find(item => item.fieldRole === 'MEASURE')?.value ||
    datasetFieldOptions.value.find(item => ['NUMBER', 'INTEGER', 'DECIMAL'].includes(item.dataType || ''))?.value ||
    datasetFieldOptions.value.find(item => item.value !== nameField)?.value ||
    datasetFieldOptions.value[1]?.value ||
    nameField ||
    ''
  )
}

const syncDefaultDatasetMapping = () => {
  const mapping = ensureDatasetMapping()
  mapping.outputFields = datasetFields.value || []
  if (showNameValueMapping.value && datasetFieldOptions.value.length) {
    const nameField = mapping.fieldMap?.name || resolveDefaultNameField()
    mapping.fieldMap!.name = nameField
    mapping.fieldMap!.value = mapping.fieldMap?.value || resolveDefaultValueField(nameField)
  }
}

const parseDatasetParamOptions = (paramSchemaJson?: string) => {
  if (!paramSchemaJson) return []
  try {
    const parsed = JSON.parse(paramSchemaJson)
    if (!Array.isArray(parsed)) return []
    return parsed
      .filter(item => item?.paramName)
      .map(item => ({
        label: `${item.label || item.paramName}${item.required ? '（必填）' : ''}`,
        value: item.paramName,
        dataType: item.dataType,
        operator: item.operator,
        fieldName: item.fieldName,
        required: item.required,
      }))
  }
  catch (error) {
    return []
  }
}

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

const loadDatasetConnectionOptions = async () => {
  if (datasetConnectionLoading.value || datasetConnectionOptions.value.length) return
  datasetConnectionLoading.value = true
  try {
    const res = await getDataConnectionList()
    datasetConnectionOptions.value = (res.data || [])
      .filter((connection: DataConnectionOption) => connection.status === undefined || connection.status === 1)
      .map(connection => ({
        label: `${connection.connectionName}${connection.connectionCode ? ` (${connection.connectionCode})` : ''}`,
        value: connection.id,
        connectionCode: connection.connectionCode
      }))
  } catch (error) {
    message.error('加载数据连接失败')
  } finally {
    datasetConnectionLoading.value = false
  }
}

const loadDatasetOptions = async () => {
  if (!datasetConnectionId.value) {
    datasetOptions.value = []
    return
  }
  if (datasetLoading.value) return
  datasetLoading.value = true
  try {
    const res = await getDataDatasetList(datasetConnectionId.value)
    datasetOptions.value = (res.data || [])
      .filter((dataset: DataDatasetOption) => dataset.status === undefined || dataset.status === 1)
      .map(dataset => ({
        label: `${dataset.datasetName}${dataset.datasetCode ? ` (${dataset.datasetCode})` : ''}`,
        value: dataset.id,
        datasetCode: dataset.datasetCode,
        connectionId: dataset.connectionId
      }))
  } catch (error) {
    message.error('加载数据集失败')
  } finally {
    datasetLoading.value = false
  }
}

const loadDatasetMetadata = async (id = datasetId.value) => {
  if (!id) {
    datasetFieldOptions.value = []
    datasetParamOptions.value = []
    return
  }
  datasetMetadataLoading.value = true
  try {
    const res = await getDataDatasetMetadata(id)
    const metadata = res.data
    const requestConfig = props.targetDataRequest as RequestConfigType
    requestConfig.datasetName = metadata?.datasetName || requestConfig.datasetName
    datasetParamOptions.value = parseDatasetParamOptions(metadata?.paramSchemaJson)
    datasetFieldOptions.value = (metadata?.fields || [])
      .filter((field: DataDatasetField) => field.displayEnabled === undefined || field.displayEnabled === 1)
      .map(field => ({
        label: `${field.fieldLabel || field.fieldName}${field.dataType ? ` (${field.dataType})` : ''}`,
        value: field.fieldName,
        dataType: field.dataType,
        fieldRole: field.fieldRole
      }))
    if (!requestConfig.datasetFields?.length) {
      requestConfig.datasetFields = datasetFieldOptions.value.map(item => item.value)
    }
    syncDefaultDatasetMapping()
  } catch (error) {
    message.error('加载数据集字段失败')
  } finally {
    datasetMetadataLoading.value = false
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

const handleDatasetConnectionChange = (value: number | null) => {
  datasetConnectionId.value = value || null
  datasetId.value = null
  datasetOptions.value = []
  datasetFieldOptions.value = []
  datasetParamOptions.value = []
  const requestConfig = props.targetDataRequest as RequestConfigType
  requestConfig.datasetName = ''
  requestConfig.datasetFields = []
  requestConfig.datasetMapping = {
    mode: 'auto',
    fieldMap: {},
    outputFields: [],
    syncHeader: true
  }
  if (value) {
    loadDatasetOptions()
  }
}

const handleDatasetChange = (value: number | null) => {
  datasetId.value = value || null
  const requestConfig = props.targetDataRequest as RequestConfigType
  const option = datasetOptions.value.find(item => item.value === value)
  requestConfig.datasetName = option?.label || ''
  requestConfig.datasetFields = []
  datasetParamOptions.value = []
  requestConfig.datasetMapping = {
    mode: 'auto',
    fieldMap: {},
    outputFields: [],
    syncHeader: true
  }
  datasetFieldOptions.value = []
  if (value) {
    loadDatasetMetadata(value)
  }
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
    message.error(isDatasetRequest.value ? '请选择要绑定的查询条件' : '请填写目标参数名')
    return
  }
  showDynamicParamModal.value = false
  message.success('查询条件绑定已保存')
}

onMounted(() => {
  const requestConfig = props.targetDataRequest as RequestConfigType
  requestSourceValue.value = requestConfig.requestSource || 'internal'
  requestConfig.requestSource = requestSourceValue.value
  if (requestConfig.requestDataType === RequestDataTypeEnum.DATASET) {
    ensureDatasetMapping()
    requestConfig.datasetPageNum = requestConfig.datasetPageNum || 1
    requestConfig.datasetPageSize = requestConfig.datasetPageSize || 50
    requestConfig.datasetMaxRows = requestConfig.datasetMaxRows || 1000
    requestConfig.datasetOutputMode = requestConfig.datasetOutputMode || 'ECHARTS_DATASET'
    loadDatasetConnectionOptions()
    if (requestConfig.datasetConnectionId) {
      loadDatasetOptions()
    }
    if (requestConfig.datasetId) {
      loadDatasetMetadata(requestConfig.datasetId)
    }
  }
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

watch(
  () => props.targetDataRequest?.requestDataType,
  value => {
    if (value === RequestDataTypeEnum.DATASET) {
      const requestConfig = props.targetDataRequest as RequestConfigType
      ensureDatasetMapping()
      requestConfig.datasetPageNum = requestConfig.datasetPageNum || 1
      requestConfig.datasetPageSize = requestConfig.datasetPageSize || 50
      requestConfig.datasetMaxRows = requestConfig.datasetMaxRows || 1000
      requestConfig.datasetOutputMode = requestConfig.datasetOutputMode || 'ECHARTS_DATASET'
      loadDatasetConnectionOptions()
      if (requestConfig.datasetConnectionId) {
        loadDatasetOptions()
      }
      if (requestConfig.datasetId) {
        loadDatasetMetadata(requestConfig.datasetId)
      }
    }
  },
  { immediate: true }
)

watch(
  () => targetData.value?.key,
  () => {
    if ((props.targetDataRequest as RequestConfigType)?.requestDataType === RequestDataTypeEnum.DATASET) {
      syncDefaultDatasetMapping()
    }
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
.dataset-request-config {
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
