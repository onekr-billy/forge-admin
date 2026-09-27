import { applyNodePropertiesPanelPart1 } from './useNodePropertiesPanel.part1.js'
import { applyNodePropertiesPanelPart2 } from './useNodePropertiesPanel.part2.js'

export function useNodePropertiesPanel(props, emit) {
  let api = applyNodePropertiesPanelPart1(props, emit)
  api = applyNodePropertiesPanelPart2(props, emit, api)
  const { __impl, mut, props: _p, emit: _e, ...publicApi } = api
  return publicApi
}
