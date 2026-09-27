import { applyApplicationRuntimePart1 } from './useApplicationRuntime.part1.js'
import { applyApplicationRuntimePart2 } from './useApplicationRuntime.part2.js'
import { applyApplicationRuntimePart3 } from './useApplicationRuntime.part3.js'
import { applyApplicationRuntimePart4 } from './useApplicationRuntime.part4.js'
import { applyApplicationRuntimePart5 } from './useApplicationRuntime.part5.js'

export function useApplicationRuntime() {
  let api = applyApplicationRuntimePart1()
  api = applyApplicationRuntimePart2(api)
  api = applyApplicationRuntimePart3(api)
  api = applyApplicationRuntimePart4(api)
  api = applyApplicationRuntimePart5(api)
  const { __impl, mut, ...publicApi } = api
  return publicApi
}
