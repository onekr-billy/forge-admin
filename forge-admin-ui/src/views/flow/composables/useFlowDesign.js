import { applyFlowDesignPart1 } from './useFlowDesign.part1.js'
import { applyFlowDesignPart2 } from './useFlowDesign.part2.js'
import { applyFlowDesignPart3 } from './useFlowDesign.part3.js'

export function useFlowDesign(props, emit) {
  let api = applyFlowDesignPart1(props, emit)
  api = applyFlowDesignPart2(props, emit, api)
  api = applyFlowDesignPart3(props, emit, api)
  const { __impl, mut, props: _p, emit: _e, ...publicApi } = api
  return publicApi
}
