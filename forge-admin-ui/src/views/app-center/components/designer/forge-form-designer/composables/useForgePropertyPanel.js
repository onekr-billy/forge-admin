import { useForgePropertyPanelPart1 } from './useForgePropertyPanel.part1'
import { useForgePropertyPanelPart2 } from './useForgePropertyPanel.part2'
import { useForgePropertyPanelPart3 } from './useForgePropertyPanel.part3'

export function useForgePropertyPanel(props, emit) {
  const d1 = useForgePropertyPanelPart1(props, emit)
  const d2 = useForgePropertyPanelPart2(props, emit, d1)
  const d3 = useForgePropertyPanelPart3(props, emit, d2)
  const { __impl, mut, ...publicApi } = d3
  return publicApi
}
