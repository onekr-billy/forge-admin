import { applyBusinessActionDesignerPart1 } from './useBusinessActionDesigner.part1.js'
import { applyBusinessActionDesignerPart2 } from './useBusinessActionDesigner.part2.js'

export function useBusinessActionDesigner(props, emit) {
  let api = applyBusinessActionDesignerPart1(props, emit)
  api = applyBusinessActionDesignerPart2(props, emit, api)
  const { __impl, mut, props: _p, emit: _e, ...publicApi } = api
  return publicApi
}
