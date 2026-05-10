import { RequestBodyEnum, RequestContentTypeEnum, RequestDataTypeEnum, SelectHttpTimeNameObj } from '@/enums/httpEnum'
import type { CreateComponentGroupType, CreateComponentType } from '@/packages/index.d'
import type {
  EditCanvasConfigType,
  RequestConfigType,
  RequestDataPondItemType,
  RequestGlobalConfigType,
  RequestParamsObjType
} from '@/store/modules/chartEditStore/chartEditStore.d'
import { JSONStringify } from '@/utils/utils'

type JsonSchema = {
  type?: string | string[]
  description?: string
  properties?: Record<string, JsonSchema>
  items?: JsonSchema
  required?: string[]
  additionalProperties?: boolean
  example?: unknown
}

export type ApiContractWarningType = 'empty-url' | 'empty-dataset' | 'filter' | 'missing-pond' | 'static-reference'

export type ApiContractWarning = {
  type: ApiContractWarningType
  message: string
}

export type ApiContractEndpoint = {
  id: string
  sourceType: 'component' | 'pond' | 'static'
  componentId: string
  componentName: string
  componentKey: string
  categoryName: string
  chartFrame?: string
  pondId?: string
  pondName?: string
  method?: string
  originUrl?: string
  requestUrl?: string
  fullUrl?: string
  contentType: string
  bodyType?: string
  intervalText: string
  headers: RequestParamsObjType
  queryParams: RequestParamsObjType
  body: unknown
  sql?: string
  dataset: unknown
  datasetSchema: JsonSchema
  filter?: string
  consumers?: string[]
  consumerSpecs?: {
    componentId: string
    componentName: string
    dataset: unknown
    datasetSchema: JsonSchema
    filter?: string
  }[]
  warnings: ApiContractWarning[]
}

export type ApiContractBuildOptions = {
  canvasConfig: EditCanvasConfigType
  requestGlobalConfig: RequestGlobalConfigType
  componentList: Array<CreateComponentType | CreateComponentGroupType>
  targetComponentIds?: string[]
  includeStatic?: boolean
}

export type ApiContractDocument = {
  meta: {
    projectName: string
    canvasSize: string
    exportedAt: string
  }
  globalRequest: {
    originUrl: string
    intervalText: string
    headers: RequestParamsObjType
    queryParams: RequestParamsObjType
    body: unknown
  }
  endpoints: ApiContractEndpoint[]
  warnings: ApiContractWarning[]
  markdown: string
  json: string
}

const EMPTY_TEXT = '未配置'

const isPlainObject = (value: unknown): value is Record<string, unknown> => {
  return Object.prototype.toString.call(value) === '[object Object]'
}

const isEmptyObject = (value: unknown) => {
  return isPlainObject(value) && Object.keys(value).length === 0
}

const safeStringify = (value: unknown) => {
  if (typeof value === 'undefined') return 'undefined'
  return JSONStringify(value)
}

const normalizeFileName = (name: string) => {
  return name
    .replace(/[\\/:*?"<>|]/g, '-')
    .replace(/\s+/g, '-')
    .replace(/-+/g, '-')
    .replace(/^-|-$/g, '')
}

export const buildApiContractFileName = (projectName = '大屏接口规范') => {
  const date = new Date()
  const pad = (value: number) => `${value}`.padStart(2, '0')
  const timestamp = `${date.getFullYear()}${pad(date.getMonth() + 1)}${pad(date.getDate())}-${pad(date.getHours())}${pad(date.getMinutes())}`
  return `${normalizeFileName(projectName || '大屏接口规范')}-${timestamp}`
}

const formatDateTime = (date: Date) => {
  const pad = (value: number) => `${value}`.padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

const getIntervalText = (requestConfig: Pick<RequestConfigType, 'requestInterval' | 'requestIntervalUnit'>, globalConfig: RequestGlobalConfigType) => {
  const interval = typeof requestConfig.requestInterval === 'number' ? requestConfig.requestInterval : globalConfig.requestInterval
  const unit = requestConfig.requestInterval ? requestConfig.requestIntervalUnit : globalConfig.requestIntervalUnit
  if (!interval) return '仅初始化'
  return `${interval} ${SelectHttpTimeNameObj[unit] || unit}`
}

const getRequestBody = (requestConfig: RequestConfigType) => {
  if (requestConfig.requestContentType === RequestContentTypeEnum.SQL) {
    return requestConfig.requestSQLContent
  }
  switch (requestConfig.requestParamsBodyType) {
    case RequestBodyEnum.JSON:
      return requestConfig.requestParams.Body.json || {}
    case RequestBodyEnum.XML:
      return requestConfig.requestParams.Body.xml || ''
    case RequestBodyEnum.FORM_DATA:
      return requestConfig.requestParams.Body['form-data'] || {}
    case RequestBodyEnum.X_WWW_FORM_URLENCODED:
      return requestConfig.requestParams.Body['x-www-form-urlencoded'] || {}
    case RequestBodyEnum.NONE:
    default:
      return {}
  }
}

const getRequestContentTypeText = (requestConfig: RequestConfigType) => {
  return requestConfig.requestContentType === RequestContentTypeEnum.SQL ? 'SQL 请求' : '普通请求'
}

const buildFullUrl = (originUrl?: string, requestUrl?: string) => {
  if (!originUrl && !requestUrl) return ''
  return `${originUrl || ''}${requestUrl || ''}`.trim()
}

const flattenComponents = (
  componentList: Array<CreateComponentType | CreateComponentGroupType>
): CreateComponentType[] => {
  const result: CreateComponentType[] = []
  componentList.forEach(component => {
    if (component.groupList?.length) {
      result.push(...flattenComponents(component.groupList))
      return
    }
    result.push(component)
  })
  return result
}

const mergeSchemas = (schemas: JsonSchema[]): JsonSchema => {
  const availableSchemas = schemas.filter(Boolean)
  if (!availableSchemas.length) return { type: 'null' }

  const firstType = JSONStringify(availableSchemas[0].type)
  const sameType = availableSchemas.every(schema => JSONStringify(schema.type) === firstType)
  if (!sameType) {
    return {
      type: Array.from(new Set(availableSchemas.flatMap(schema => (Array.isArray(schema.type) ? schema.type : [schema.type || 'unknown']))))
    }
  }

  if (availableSchemas[0].type === 'object') {
    const properties: Record<string, JsonSchema> = {}
    const required = new Set<string>()
    const allKeys = new Set<string>()
    availableSchemas.forEach(schema => {
      Object.keys(schema.properties || {}).forEach(key => allKeys.add(key))
    })
    allKeys.forEach(key => {
      const keySchemas = availableSchemas.map(schema => schema.properties?.[key]).filter(Boolean) as JsonSchema[]
      properties[key] = mergeSchemas(keySchemas)
      if (keySchemas.length === availableSchemas.length) required.add(key)
    })
    return {
      type: 'object',
      properties,
      required: Array.from(required),
      additionalProperties: true
    }
  }

  if (availableSchemas[0].type === 'array') {
    return {
      type: 'array',
      items: mergeSchemas(availableSchemas.map(schema => schema.items || { type: 'unknown' }))
    }
  }

  return availableSchemas[0]
}

export const inferJsonSchema = (value: unknown): JsonSchema => {
  if (value === null) return { type: 'null', example: null }
  if (Array.isArray(value)) {
    if (!value.length) return { type: 'array', items: { type: 'unknown' }, example: [] }
    return {
      type: 'array',
      items: mergeSchemas(value.slice(0, 20).map(item => inferJsonSchema(item))),
      example: value[0]
    }
  }
  if (isPlainObject(value)) {
    const properties: Record<string, JsonSchema> = {}
    Object.keys(value).forEach(key => {
      properties[key] = inferJsonSchema(value[key])
    })
    return {
      type: 'object',
      properties,
      required: Object.keys(value),
      additionalProperties: true,
      example: value
    }
  }
  return {
    type: typeof value,
    example: value
  }
}

const createEndpoint = (
  component: CreateComponentType,
  requestConfig: RequestConfigType,
  globalConfig: RequestGlobalConfigType,
  sourceType: ApiContractEndpoint['sourceType'],
  extra?: Pick<ApiContractEndpoint, 'pondId' | 'pondName'>
): ApiContractEndpoint => {
  const requestUrl = requestConfig.requestUrl || ''
  const dataset = component.option?.dataset
  const warnings: ApiContractWarning[] = []
  const componentName = component.chartConfig?.title || component.key || component.id
  if (!requestUrl && sourceType !== 'static') {
    warnings.push({
      type: 'empty-url',
      message: `${componentName} 未配置接口地址`
    })
  }
  if (typeof dataset === 'undefined' || dataset === null || (Array.isArray(dataset) && !dataset.length) || isEmptyObject(dataset)) {
    warnings.push({
      type: 'empty-dataset',
      message: `${componentName} 当前 dataset 为空，无法推导完整响应结构`
    })
  }
  if (component.filter) {
    warnings.push({
      type: 'filter',
      message: `${componentName} 配置了数据过滤器，后端返回值会先经过前端 filter 再赋值给 dataset`
    })
  }
  if (sourceType === 'static') {
    warnings.push({
      type: 'static-reference',
      message: `${componentName} 当前未使用接口取数，文档仅导出渲染数据结构，接口地址可后续自行选择或配置`
    })
  }

  return {
    id: `${sourceType}_${extra?.pondId || component.id}`,
    sourceType,
    componentId: component.id,
    componentName,
    componentKey: component.key,
    categoryName: component.chartConfig?.categoryName || component.chartConfig?.category || '',
    chartFrame: component.chartConfig?.chartFrame,
    pondId: extra?.pondId,
    pondName: extra?.pondName,
    method: requestConfig.requestHttpType?.toUpperCase(),
    originUrl: globalConfig.requestOriginUrl || '',
    requestUrl,
    fullUrl: buildFullUrl(globalConfig.requestOriginUrl, requestUrl),
    contentType: getRequestContentTypeText(requestConfig),
    bodyType: requestConfig.requestParamsBodyType,
    intervalText: getIntervalText(requestConfig, globalConfig),
    headers: {
      ...(globalConfig.requestParams?.Header || {}),
      ...(requestConfig.requestParams?.Header || {})
    },
    queryParams: requestConfig.requestParams?.Params || {},
    body: getRequestBody(requestConfig),
    sql: requestConfig.requestContentType === RequestContentTypeEnum.SQL ? requestConfig.requestSQLContent?.sql : undefined,
    dataset,
    datasetSchema: inferJsonSchema(dataset),
    filter: component.filter,
    consumers: [componentName],
    consumerSpecs: [
      {
        componentId: component.id,
        componentName,
        dataset,
        datasetSchema: inferJsonSchema(dataset),
        filter: component.filter
      }
    ],
    warnings
  }
}

const buildEndpoints = (options: ApiContractBuildOptions) => {
  const components = flattenComponents(options.componentList)
  const selectedComponents = options.targetComponentIds?.length
    ? components.filter(component => options.targetComponentIds?.includes(component.id))
    : components
  const endpoints: ApiContractEndpoint[] = []
  const pondEndpointMap = new Map<string, ApiContractEndpoint>()
  const warnings: ApiContractWarning[] = []

  selectedComponents.forEach(component => {
    const requestType = component.request?.requestDataType
    if (requestType === RequestDataTypeEnum.AJAX) {
      endpoints.push(createEndpoint(component, component.request, options.requestGlobalConfig, 'component'))
      return
    }

    if (requestType === RequestDataTypeEnum.Pond) {
      const pondId = component.request.requestDataPondId
      const pond = options.requestGlobalConfig.requestDataPond?.find((item: RequestDataPondItemType) => item.dataPondId === pondId)
      if (!pond) {
        const warning = {
          type: 'missing-pond' as const,
          message: `${component.chartConfig?.title || component.id} 选择的数据池不存在或已被删除`
        }
        warnings.push(warning)
        return
      }
      const existingEndpoint = pondEndpointMap.get(pond.dataPondId)
      if (existingEndpoint) {
        const componentName = component.chartConfig?.title || component.id
        existingEndpoint.consumers?.push(componentName)
        existingEndpoint.consumerSpecs?.push({
          componentId: component.id,
          componentName,
          dataset: component.option?.dataset,
          datasetSchema: inferJsonSchema(component.option?.dataset),
          filter: component.filter
        })
        if (component.filter) {
          existingEndpoint.warnings.push({
            type: 'filter',
            message: `${componentName} 配置了数据过滤器，和同数据池其他组件可能使用不同字段`
          })
        }
        return
      }
      const endpoint = createEndpoint(component, pond.dataPondRequestConfig, options.requestGlobalConfig, 'pond', {
        pondId: pond.dataPondId,
        pondName: pond.dataPondName
      })
      endpoint.id = `pond_${pond.dataPondId}`
      pondEndpointMap.set(pond.dataPondId, endpoint)
      endpoints.push(endpoint)
      return
    }

    if (options.includeStatic && requestType === RequestDataTypeEnum.STATIC) {
      endpoints.push(createEndpoint(component, component.request, options.requestGlobalConfig, 'static'))
    }
  })

  endpoints.forEach(endpoint => warnings.push(...endpoint.warnings))
  return { endpoints, warnings }
}

const markdownTableValue = (value?: unknown) => {
  if (typeof value === 'undefined' || value === null || value === '') return EMPTY_TEXT
  return `${value}`.replace(/\|/g, '\\|').replace(/\n/g, ' ')
}

const codeBlock = (content: unknown, lang = 'json') => {
  const value = typeof content === 'string' ? content : safeStringify(content)
  return `\`\`\`${lang}\n${value || '{}'}\n\`\`\``
}

const buildEndpointMarkdown = (endpoint: ApiContractEndpoint, index: number) => {
  const sourceName = endpoint.sourceType === 'pond' ? `数据池：${endpoint.pondName || endpoint.pondId}` : endpoint.componentName
  const lines: string[] = []
  lines.push(`### ${index + 1}. ${sourceName}`)
  lines.push('')
  lines.push('#### 组件信息')
  lines.push(`- 组件 ID：${endpoint.componentId}`)
  lines.push(`- 组件名称：${endpoint.componentName}`)
  lines.push(`- 组件类型：${endpoint.categoryName || EMPTY_TEXT} / ${endpoint.componentKey || EMPTY_TEXT}`)
  lines.push(`- 渲染字段：\`option.dataset\``)
  if (endpoint.sourceType === 'pond') {
    lines.push(`- 数据池 ID：${endpoint.pondId}`)
    lines.push(`- 使用组件：${endpoint.consumers?.join('、') || EMPTY_TEXT}`)
  }
  lines.push('')
  lines.push('#### 请求规范')
  if (endpoint.sourceType === 'static') {
    lines.push('当前组件未使用接口取数，以下请求配置仅作为占位参考。后端可根据业务自行设计 URL，并按响应规范返回数据。')
    lines.push('')
  }
  lines.push(`- Method：${endpoint.method || EMPTY_TEXT}`)
  lines.push(`- URL：${endpoint.fullUrl || EMPTY_TEXT}`)
  lines.push(`- 组件路径：${endpoint.requestUrl || EMPTY_TEXT}`)
  lines.push(`- 请求类型：${endpoint.contentType}`)
  lines.push(`- Body 类型：${endpoint.bodyType || EMPTY_TEXT}`)
  lines.push(`- 刷新间隔：${endpoint.intervalText}`)
  lines.push('')
  lines.push('Query Params：')
  lines.push(codeBlock(endpoint.queryParams))
  lines.push('')
  lines.push('Headers：')
  lines.push(codeBlock(endpoint.headers))
  lines.push('')
  lines.push('Request Body：')
  lines.push(codeBlock(endpoint.body, endpoint.bodyType === RequestBodyEnum.XML ? 'xml' : 'json'))
  lines.push('')
  lines.push('#### 响应规范')
  if (endpoint.sourceType === 'static') {
    lines.push('当前组件的静态数据结构如下。后续切换为接口取数时，后端接口返回成功后，前端会读取 `res.data`，并赋值给组件的 `option.dataset`。')
  } else {
    lines.push('后端接口返回成功后，前端会读取 `res.data`。默认情况下，`res.data` 应直接符合下面的 `dataset` 结构，并赋值给组件的 `option.dataset`。')
  }
  if (endpoint.filter) {
    lines.push('')
    lines.push('该组件配置了数据过滤器，实际流程为：`res.data -> filter(res.data, res) -> option.dataset`。后端返回结构可以不同，但必须能被过滤器转换成下面的 `dataset`。')
    lines.push('')
    lines.push('Filter：')
    lines.push(codeBlock(endpoint.filter, 'js'))
  }
  lines.push('')
  lines.push('响应示例：')
  lines.push(codeBlock(endpoint.dataset))
  lines.push('')
  lines.push('JSON Schema：')
  lines.push(codeBlock(endpoint.datasetSchema))
  if (endpoint.sourceType === 'pond' && endpoint.consumerSpecs && endpoint.consumerSpecs.length > 1) {
    lines.push('')
    lines.push('#### 各组件渲染数据要求')
    endpoint.consumerSpecs.forEach((consumer, consumerIndex) => {
      lines.push('')
      lines.push(`##### ${consumerIndex + 1}. ${consumer.componentName}`)
      lines.push(`- 组件 ID：${consumer.componentId}`)
      if (consumer.filter) {
        lines.push('- 该组件配置了 filter，接口返回值需能被转换成以下 dataset。')
      }
      lines.push('')
      lines.push('Dataset 示例：')
      lines.push(codeBlock(consumer.dataset))
      lines.push('')
      lines.push('Dataset Schema：')
      lines.push(codeBlock(consumer.datasetSchema))
      if (consumer.filter) {
        lines.push('')
        lines.push('Filter：')
        lines.push(codeBlock(consumer.filter, 'js'))
      }
    })
  }
  if (endpoint.warnings.length) {
    lines.push('')
    lines.push('#### 注意事项')
    endpoint.warnings.forEach(warning => {
      lines.push(`- ${warning.message}`)
    })
  }
  lines.push('')
  return lines.join('\n')
}

const buildMarkdown = (document: Omit<ApiContractDocument, 'markdown' | 'json'>) => {
  const lines: string[] = []
  lines.push(`# ${document.meta.projectName}接口规范文档`)
  lines.push('')
  lines.push('## 基本信息')
  lines.push(`- 大屏名称：${document.meta.projectName}`)
  lines.push(`- 画布尺寸：${document.meta.canvasSize}`)
  lines.push(`- 导出时间：${document.meta.exportedAt}`)
  lines.push('')
  lines.push('## 数据接入规则')
  lines.push('- 前端接口请求成功后读取 `res.data`。')
  lines.push('- 默认情况下，`res.data` 会作为组件的 `option.dataset` 渲染数据。')
  lines.push('- 如果组件配置了 `filter`，则使用 `filter(res.data, res)` 的结果作为 `option.dataset`。')
  lines.push('- 未配置接口取数的组件也会导出当前静态 `dataset`，用于后端开发时选择接口并实现对应返回结构。')
  lines.push('- 全局 Header 会与组件 Header 合并；组件 Header 优先级更高。')
  lines.push('')
  lines.push('## 全局请求配置')
  lines.push(`- 前置 URL：${document.globalRequest.originUrl || EMPTY_TEXT}`)
  lines.push(`- 默认刷新间隔：${document.globalRequest.intervalText}`)
  lines.push('')
  lines.push('全局 Headers：')
  lines.push(codeBlock(document.globalRequest.headers))
  lines.push('')
  lines.push('全局 Query Params（当前运行时不自动合并到组件请求，仅作配置参考）：')
  lines.push(codeBlock(document.globalRequest.queryParams))
  lines.push('')
  lines.push('## 接口总览')
  lines.push('')
  lines.push('| 序号 | 数据来源 | 组件/数据池 | Method | URL | 刷新间隔 | 状态 |')
  lines.push('|---|---|---|---|---|---|---|')
  if (!document.endpoints.length) {
    lines.push('| - | - | 当前范围内没有接口取数组件 | - | - | - | - |')
  } else {
    document.endpoints.forEach((endpoint, index) => {
      const status = endpoint.warnings.length ? `${endpoint.warnings.length} 条提示` : '正常'
      const sourceTypeMap = {
        component: '组件接口',
        pond: '数据池',
        static: '待选接口'
      }
      lines.push(
        `| ${index + 1} | ${sourceTypeMap[endpoint.sourceType]} | ${markdownTableValue(endpoint.pondName || endpoint.componentName)} | ${markdownTableValue(endpoint.method)} | ${markdownTableValue(endpoint.fullUrl)} | ${markdownTableValue(endpoint.intervalText)} | ${status} |`
      )
    })
  }
  if (document.warnings.length) {
    lines.push('')
    lines.push('## 导出提示')
    document.warnings.forEach(warning => {
      lines.push(`- ${warning.message}`)
    })
  }
  lines.push('')
  lines.push('## 接口明细')
  lines.push('')
  document.endpoints.forEach((endpoint, index) => {
    lines.push(buildEndpointMarkdown(endpoint, index))
  })
  return lines.join('\n')
}

export const buildApiContractDocument = (options: ApiContractBuildOptions): ApiContractDocument => {
  const { endpoints, warnings } = buildEndpoints(options)
  const meta = {
    projectName: options.canvasConfig.projectName || '未命名大屏',
    canvasSize: `${options.canvasConfig.width} x ${options.canvasConfig.height}`,
    exportedAt: formatDateTime(new Date())
  }
  const globalRequest = {
    originUrl: options.requestGlobalConfig.requestOriginUrl || '',
    intervalText: getIntervalText(options.requestGlobalConfig, options.requestGlobalConfig),
    headers: options.requestGlobalConfig.requestParams?.Header || {},
    queryParams: options.requestGlobalConfig.requestParams?.Params || {},
    body: options.requestGlobalConfig.requestParams?.Body || {}
  }
  const baseDocument = {
    meta,
    globalRequest,
    endpoints,
    warnings
  }
  const markdown = buildMarkdown(baseDocument)
  const json = JSONStringify(baseDocument)
  return {
    ...baseDocument,
    markdown,
    json
  }
}
