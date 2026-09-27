import { applyBusinessListDesignerPart1 } from './useBusinessListDesigner.part1.js'
import { applyBusinessListDesignerPart2 } from './useBusinessListDesigner.part2.js'

export function useBusinessListDesigner(props, emit, expose) {
  let api = applyBusinessListDesignerPart1(props, emit, expose)
  api = applyBusinessListDesignerPart2(props, emit, expose, api)
  api.__impl.hydrateInitialState?.()
  const { __impl, mut, props: _p, emit: _e, ...publicApi } = api
  return publicApi
}
