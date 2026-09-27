import { applyCrudPageViewPart1 } from './useCrudPageView.part1.js'
import { applyCrudPageViewPart2 } from './useCrudPageView.part2.js'

export function useCrudPageView(props, emit) {
  let api = applyCrudPageViewPart1(props, emit)
  api = applyCrudPageViewPart2(props, emit, api)
  const { __impl, mut, props: _p, emit: _e, ...publicApi } = api
  return publicApi
}
