import { applyObjectDesignerPart1 } from './useObjectDesigner.part1.js'
import { applyObjectDesignerPart2 } from './useObjectDesigner.part2.js'

export function useObjectDesigner(props, emit, expose) {
  let api = applyObjectDesignerPart1(props, emit, expose)
  api = applyObjectDesignerPart2(props, emit, expose, api)
  const { __impl, mut, props: _p, emit: _e, ...publicApi } = api
  return publicApi
}
