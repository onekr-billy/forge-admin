import { applyKnowledgeListPart1 } from './useKnowledgeList.part1.js'

export function useKnowledgeList() {
  let api = applyKnowledgeListPart1()
  const { __impl, mut, ...publicApi } = api
  return publicApi
}
