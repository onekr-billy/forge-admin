import { applyFlowModelPart1 } from './useFlowModel.part1.js'
import { applyFlowModelPart2 } from './useFlowModel.part2.js'

export function useFlowModel() {
  let api = applyFlowModelPart1()
  api = applyFlowModelPart2(api)
  const { __impl, mut, ...publicApi } = api
  return publicApi
}
