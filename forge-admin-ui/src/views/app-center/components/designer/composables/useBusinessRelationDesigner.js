import { applyBusinessRelationDesignerPart1 } from './useBusinessRelationDesigner.part1.js'
import { applyBusinessRelationDesignerPart2 } from './useBusinessRelationDesigner.part2.js'

export function useBusinessRelationDesigner(props, emit, expose) {
  let api = applyBusinessRelationDesignerPart1(props, emit, expose)
  api = applyBusinessRelationDesignerPart2(props, emit, expose, api)
  const { __impl, mut, props: _p, emit: _e, ...publicApi } = api
  return publicApi
}
