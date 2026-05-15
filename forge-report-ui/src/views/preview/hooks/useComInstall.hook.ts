import { onUnmounted, ref, watch } from 'vue'
import { ChartEditStorageType } from '../index.d'
import { CreateComponentType, CreateComponentGroupType } from '@/packages/index.d'
import { fetchChartComponent } from '@/packages/index'

export const useComInstall = (localStorageInfo: ChartEditStorageType) => {
  const show = ref(false)
  let stopWatch: (() => void) | undefined

  const intComponent = (target: CreateComponentType) => {
    if (!window['$vue']?.component) return
    if (!window['$vue'].component(target.chartConfig.chartKey)) {
      window['$vue'].component(target.chartConfig.chartKey, fetchChartComponent(target.chartConfig))
    }
  }

  const installComponentList = (componentList: Array<CreateComponentType | CreateComponentGroupType> = []) => {
    componentList.forEach((e: CreateComponentType | CreateComponentGroupType) => {
      if (e.isGroup) {
        (e as CreateComponentGroupType).groupList.forEach(groupItem => {
          intComponent(groupItem)
        })
      } else {
        intComponent(e as CreateComponentType)
      }
    })
  }

  const setupComponentWatch = () => {
    if (stopWatch) return
    stopWatch = watch(
      () => localStorageInfo.componentList,
      componentList => {
        installComponentList(componentList)
        show.value = true
      },
      {
        deep: true
      })
  }

  // 注册组件(一开始无法获取window['$vue'])
  const intervalTiming = setInterval(() => {
    if (window['$vue']?.component) {
      clearInterval(intervalTiming)
      installComponentList(localStorageInfo.componentList)
      setupComponentWatch()
      show.value = true
    }
  }, 200)

  onUnmounted(() => {
    clearInterval(intervalTiming)
    if (stopWatch) {
      stopWatch()
    }
  })

  return {
    show
  }
}
