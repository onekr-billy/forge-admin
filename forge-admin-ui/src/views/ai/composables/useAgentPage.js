import { applyAgentPagePart1 } from './useAgentPage.part1.js'
import { applyAgentPagePart2 } from './useAgentPage.part2.js'

export function useAgentPage() {
  let api = applyAgentPagePart1()
  api = applyAgentPagePart2(api)
  const { __impl, mut, ...publicApi } = api
  return publicApi
}
