import { applyBusinessFieldPropertyPanelPart1 } from './useBusinessFieldPropertyPanel.part1.js'
import { applyBusinessFieldPropertyPanelPart2 } from './useBusinessFieldPropertyPanel.part2.js'

export function useBusinessFieldPropertyPanel(props, emit, expose) {
  let api = applyBusinessFieldPropertyPanelPart1(props, emit, expose)
  api = applyBusinessFieldPropertyPanelPart2(props, emit, expose, api)
  const { __impl, mut, props: _p, emit: _e, ...publicApi } = api
  return publicApi
}
