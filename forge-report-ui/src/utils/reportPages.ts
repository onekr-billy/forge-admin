import cloneDeep from 'lodash/cloneDeep'
import { defaultTheme, globalThemeJson } from '@/settings/chartThemes/index'
import { requestInterval, previewScaleType, requestIntervalUnit } from '@/settings/designSetting'
import { getUUID } from '@/utils/utils'
import { requestConfig as defaultRequestConfig } from '@/packages/public/publicConfig'
import type { CreateComponentGroupType, CreateComponentType } from '@/packages/index.d'
import { ChartEditStoreEnum } from '@/store/modules/chartEditStore/chartEditStore.d'
import type {
  ChartEditStorage,
  EditCanvasConfigType,
  ReportCanvasPage,
  ReportMultiPageStorage,
  RequestGlobalConfigType
} from '@/store/modules/chartEditStore/chartEditStore.d'

const DEFAULT_PAGE_NAME = '首页'

export function isMultiPageStorage(storage: unknown): storage is ReportMultiPageStorage {
  const candidate = storage as Partial<ReportMultiPageStorage>
  return candidate?.version === 2 && Array.isArray(candidate.pages)
}

export function isLegacyChartStorage(storage: unknown): storage is ChartEditStorage {
  const candidate = storage as Partial<ChartEditStorage>
  return !!candidate && Array.isArray(candidate.componentList)
}

export function createDefaultEditCanvasConfig(projectName?: string): EditCanvasConfigType {
  return {
    projectName,
    width: 1920,
    height: 1080,
    filterShow: false,
    hueRotate: 0,
    saturate: 1,
    contrast: 1,
    brightness: 1,
    opacity: 1,
    rotateZ: 0,
    rotateX: 0,
    rotateY: 0,
    skewX: 0,
    skewY: 0,
    blendMode: 'normal',
    background: undefined,
    backgroundImage: undefined,
    selectColor: true,
    chartThemeColor: defaultTheme || 'dark',
    chartCustomThemeColorInfo: undefined,
    chartThemeSetting: globalThemeJson,
    vChartThemeName: 'vScreenVolcanoBlue',
    previewScaleType
  }
}

export function createDefaultRequestGlobalConfig(): RequestGlobalConfigType {
  return {
    requestDataPond: [],
    requestOriginUrl: '',
    requestInterval,
    requestIntervalUnit,
    requestParams: {
      Body: {
        'form-data': {},
        'x-www-form-urlencoded': {},
        json: '',
        xml: ''
      },
      Header: {},
      Params: {}
    }
  }
}

export function createDefaultPage(name = DEFAULT_PAGE_NAME): ReportCanvasPage {
  return {
    id: getUUID(),
    name,
    sort: 1,
    editCanvasConfig: createDefaultEditCanvasConfig(name),
    requestGlobalConfig: createDefaultRequestGlobalConfig(),
    componentList: []
  }
}

export function createDefaultProjectStorage(projectName = '新项目'): ReportMultiPageStorage {
  const page = createDefaultPage(DEFAULT_PAGE_NAME)
  page.editCanvasConfig.projectName = projectName

  return {
    version: 2,
    homePageId: page.id,
    activePageId: page.id,
    pageTransition: 'fade',
    pages: [page],
    sharedRequestGlobalConfig: {}
  }
}

export function normalizeComponentPageActions<T extends CreateComponentType | CreateComponentGroupType | any>(component: T): T {
  if (!component || !component.events) return component

  component.events.baseEvent = component.events.baseEvent || {}
  component.events.advancedEvents = component.events.advancedEvents || {}
  component.events.interactEvents = component.events.interactEvents || []
  component.events.actions = component.events.actions || []

  if (component.isGroup && Array.isArray(component.groupList)) {
    component.groupList.forEach((item: CreateComponentType) => normalizeComponentPageActions(item))
  }

  return component
}

export function normalizeChartStorage(storage: Partial<ChartEditStorage> = {}, fallbackName = DEFAULT_PAGE_NAME): ChartEditStorage {
  return {
    [ChartEditStoreEnum.EDIT_CANVAS_CONFIG]: {
      ...createDefaultEditCanvasConfig(fallbackName),
      ...(storage.editCanvasConfig || {})
    },
    [ChartEditStoreEnum.REQUEST_GLOBAL_CONFIG]: {
      ...createDefaultRequestGlobalConfig(),
      ...(storage.requestGlobalConfig || {})
    },
    [ChartEditStoreEnum.COMPONENT_LIST]: (storage.componentList || []).map(item =>
      normalizeComponentPageActions(cloneDeep(item))
    )
  }
}

export function normalizeProjectStorage(storage: unknown, fallbackName = DEFAULT_PAGE_NAME): ReportMultiPageStorage {
  if (isMultiPageStorage(storage)) {
    const pages = storage.pages.length ? storage.pages : [createDefaultPage(fallbackName)]
    const normalizedPages = pages.map((page, index) => {
      const normalized = normalizeChartStorage(page, page.name || `${fallbackName}${index + 1}`)
      return {
        ...normalized,
        id: page.id || getUUID(),
        name: page.name || `${fallbackName}${index + 1}`,
        sort: page.sort || index + 1
      }
    })
    const homePageId = normalizedPages.some(page => page.id === storage.homePageId)
      ? storage.homePageId
      : normalizedPages[0].id
    const activePageId = normalizedPages.some(page => page.id === storage.activePageId)
      ? storage.activePageId
      : homePageId

    return {
      version: 2,
      homePageId,
      activePageId,
      pageTransition: storage.pageTransition || 'fade',
      pages: normalizedPages,
      sharedRequestGlobalConfig: storage.sharedRequestGlobalConfig || {}
    }
  }

  const legacyStorage = normalizeChartStorage(isLegacyChartStorage(storage) ? storage : {}, fallbackName)
  const page = {
    ...legacyStorage,
    id: getUUID(),
    name: legacyStorage.editCanvasConfig.projectName || fallbackName,
    sort: 1
  }

  return {
    version: 2,
    homePageId: page.id,
    activePageId: page.id,
    pageTransition: 'fade',
    pages: [page],
    sharedRequestGlobalConfig: {}
  }
}

export function extractPageStorage(project: ReportMultiPageStorage, pageId?: string): ChartEditStorage {
  const targetPage = project.pages.find(page => page.id === pageId)
    || project.pages.find(page => page.id === project.homePageId)
    || project.pages[0]
    || createDefaultPage()

  return normalizeChartStorage(targetPage, targetPage.name)
}

export function updatePageStorage(
  project: ReportMultiPageStorage,
  pageId: string,
  storage: ChartEditStorage
): ReportMultiPageStorage {
  const normalizedStorage = normalizeChartStorage(storage)
  return {
    ...project,
    pages: project.pages.map(page => {
      if (page.id !== pageId) return page
      return {
        ...page,
        ...normalizedStorage
      }
    })
  }
}

function cloneComponentWithNewId<T extends CreateComponentType | CreateComponentGroupType>(component: T): T {
  const cloned = normalizeComponentPageActions(cloneDeep(component))
  cloned.id = getUUID()
  cloned.request = cloneDeep(cloned.request || defaultRequestConfig)

  if (cloned.isGroup && Array.isArray((cloned as CreateComponentGroupType).groupList)) {
    const group = cloned as CreateComponentGroupType
    group.groupList = group.groupList.map(item => cloneComponentWithNewId(item) as CreateComponentType)
  }

  return cloned
}

export function clonePageWithNewIds(page: ReportCanvasPage, name?: string): ReportCanvasPage {
  const clonedPage = cloneDeep(page)
  const pageName = name || `${page.name || DEFAULT_PAGE_NAME} 副本`

  return {
    ...clonedPage,
    id: getUUID(),
    name: pageName,
    sort: page.sort + 1,
    editCanvasConfig: {
      ...createDefaultEditCanvasConfig(pageName),
      ...clonedPage.editCanvasConfig,
      projectName: pageName
    },
    requestGlobalConfig: {
      ...createDefaultRequestGlobalConfig(),
      ...clonedPage.requestGlobalConfig
    },
    componentList: (clonedPage.componentList || []).map(item => cloneComponentWithNewId(item))
  }
}

export function removeInvalidPageActions(
  project: ReportMultiPageStorage,
  removedPageId: string
): ReportMultiPageStorage {
  const cleanComponent = (component: CreateComponentType | CreateComponentGroupType) => {
    normalizeComponentPageActions(component)
    component.events.actions = (component.events.actions || []).filter(action => action.targetPageId !== removedPageId)

    if (component.isGroup && Array.isArray((component as CreateComponentGroupType).groupList)) {
      (component as CreateComponentGroupType).groupList.forEach(cleanComponent)
    }
  }

  const nextProject = cloneDeep(project)
  nextProject.pages.forEach(page => {
    page.componentList.forEach(cleanComponent)
  })

  return nextProject
}
