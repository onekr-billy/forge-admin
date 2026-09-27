import { applyLoginPagePart1 } from './useLoginPage.part1.js'

export function useLoginPage() {
  let api = applyLoginPagePart1()
  const { __impl, mut, ...publicApi } = api
  return publicApi
}
