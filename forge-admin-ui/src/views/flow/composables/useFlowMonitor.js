import { applyFlowMonitorPart1 } from './useFlowMonitor.part1.js'
import { applyFlowMonitorPart2 } from './useFlowMonitor.part2.js'

export function useFlowMonitor() {
  let api = applyFlowMonitorPart1()
  api = applyFlowMonitorPart2(api)
  const { __impl, mut, ...publicApi } = api
  return publicApi
}
