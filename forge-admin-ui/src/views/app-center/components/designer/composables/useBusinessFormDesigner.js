import { applyBusinessFormDesignerPart1 } from './useBusinessFormDesigner.part1.js'
import { applyBusinessFormDesignerPart2 } from './useBusinessFormDesigner.part2.js'

export function useBusinessFormDesigner(props, emit, expose) {
  let api = applyBusinessFormDesignerPart1(props, emit, expose)
  api = applyBusinessFormDesignerPart2(props, emit, expose, api)
  api.__impl.hydrateInitialState?.()
  const { __impl, mut, props: _p, emit: _e, ...publicApi } = api
  return publicApi
}
