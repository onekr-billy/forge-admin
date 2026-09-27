import { applyDatasetPagePart1 } from './useDatasetPage.part1.js'
import { applyDatasetPagePart2 } from './useDatasetPage.part2.js'
import { applyDatasetPagePart3 } from './useDatasetPage.part3.js'

export function useDatasetPage() {
  let api = applyDatasetPagePart1()
  api = applyDatasetPagePart2(api)
  api = applyDatasetPagePart3(api)
  const { __impl, mut, ...publicApi } = api
  return publicApi
}
