import { CreateComponentType, CreateComponentGroupType } from '@/packages/index.d'
import { BaseEvent, ComponentActionType, EventLife } from '@/enums/eventEnum'
import { switchPreviewPage } from '@/views/preview/utils/storage'
import { useChartEditStore } from '@/store/modules/chartEditStore/chartEditStore'
import { resolveDrillParams } from '@/utils/reportDrill'

// 组件事件处理 hook
export const useLifeHandler = (
  chartConfig: CreateComponentType | CreateComponentGroupType,
  pageContext?: Record<string, any>
) => {
  if (!chartConfig.events) return {}
  const chartEditStore = useChartEditStore()
  const runtimePageContext = pageContext || chartEditStore.getRuntimePageContext

  // 处理基础事件
  const baseEvent: { [key: string]: any } = {}
  for (const key of Object.values(BaseEvent)) {
    const fnStr: string | undefined = (chartConfig.events.baseEvent as any)[key]
    const actions = (chartConfig.events.actions || []).filter(action => action.trigger === key)
    if (fnStr || actions.length) {
      baseEvent[key] = generateBaseFunc(fnStr, actions, chartConfig, runtimePageContext)
    }
  }

  // 生成生命周期事件
  const events = chartConfig.events.advancedEvents || {}
  const lifeEvents = {
    [EventLife.VNODE_BEFORE_MOUNT](e: any) {
      const fnStr = (events[EventLife.VNODE_BEFORE_MOUNT] || '').trim()
      rejectLegacyEventScript(fnStr)
    },
    [EventLife.VNODE_MOUNTED](e: any) {
      const fnStr = (events[EventLife.VNODE_MOUNTED] || '').trim()
      rejectLegacyEventScript(fnStr)
    }
  }
  return { ...baseEvent, ...lifeEvents }
}

/**
 * 生成基础函数
 * @param fnStr 用户方法体代码
 * @param event 鼠标事件
 */
 export function generateBaseFunc(
  fnStr = '',
  actions: CreateComponentType['events']['actions'] = [],
  chartConfig?: CreateComponentType | CreateComponentGroupType,
  pageContext: Record<string, any> = {}
) {
  try {
    rejectLegacyEventScript(fnStr)

    return async (mouseEvent: MouseEvent) => {
      for (const action of actions || []) {
        if (action.type === ComponentActionType.GO_PAGE && action.targetPageId) {
          const drillParams = resolveDrillParams(action.params || [], { mouseEvent, component: chartConfig }, pageContext)
          await switchPreviewPage(action.targetPageId, { ...pageContext, ...drillParams }, action.transition)
        }
        if (action.type === ComponentActionType.OPEN_MODAL && action.targetPageId) {
          const chartEditStore = useChartEditStore()
          const drillParams = resolveDrillParams(action.params || [], { mouseEvent, component: chartConfig }, pageContext)
          chartEditStore.openModal(action.targetPageId, { ...pageContext, ...drillParams })
        }
        if (action.type === ComponentActionType.CLOSE_MODAL) {
          useChartEditStore().closeModal()
        }
      }
    }
  } catch (error) {
    console.error(error)
  }
}

/**
 * 生成高级函数
 * @param fnStr 用户方法体代码
 * @param e 执行生命周期的动态组件实例
 */
function rejectLegacyEventScript(fnStr?: string) {
  if (fnStr) {
    console.warn('报表自定义 JavaScript 事件已禁用，请迁移为结构化组件动作')
  }
}
