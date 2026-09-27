import { applyLowcodeAppsPart1 } from './useLowcodeApps.part1.js'
import { applyLowcodeAppsPart2 } from './useLowcodeApps.part2.js'

export function useLowcodeApps() {
  let api = applyLowcodeAppsPart1()
  api = applyLowcodeAppsPart2(api)
  const { __impl, mut, ...publicApi } = api
  return publicApi
}
