import { applyProviderModelPart1 } from './useProviderModel.part1.js'

export function useProviderModel() {
  let api = applyProviderModelPart1()
  const { __impl, mut, ...publicApi } = api
  return publicApi
}
