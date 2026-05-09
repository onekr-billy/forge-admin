import type { App } from 'vue'
import { FgSkeleton } from '@/components/FgSkeleton'
import { FgLoading } from '@/components/FgLoading'
import { SketchRule } from 'vue3-sketch-ruler'

/**
 * 全局注册自定义组件
 * @param app
 */
export function setupCustomComponents(app: App) {
  app.component('FgSkeleton', FgSkeleton)
  app.component('FgLoading', FgLoading)
  app.component('SketchRule', SketchRule)
}
