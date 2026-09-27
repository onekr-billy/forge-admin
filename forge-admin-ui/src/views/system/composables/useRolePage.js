import { applyRolePagePart1 } from './useRolePage.part1.js'
import { applyRolePagePart2 } from './useRolePage.part2.js'

export function useRolePage() {
  let api = applyRolePagePart1()
  api = applyRolePagePart2(api)
  const { __impl, mut, ...publicApi } = api
  return publicApi
}
