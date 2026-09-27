import { applyExternalManagePart1 } from './useExternalManage.part1.ts'
import { applyExternalManagePart2 } from './useExternalManage.part2.ts'

export function useExternalManage(props, emit) {
  let api = applyExternalManagePart1(props, emit)
  api = applyExternalManagePart2(props, emit, api)
  const { __impl, mut, props: _p, emit: _e, ...publicApi } = api
  return publicApi
}
