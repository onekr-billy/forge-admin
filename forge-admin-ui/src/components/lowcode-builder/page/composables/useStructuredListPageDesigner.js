import { applyStructuredListPageDesignerPart1 } from './useStructuredListPageDesigner.part1.js'

export function useStructuredListPageDesigner(props, emit) {
  let api = applyStructuredListPageDesignerPart1(props, emit)
  const { __impl, mut, props: _p, emit: _e, ...publicApi } = api
  return publicApi
}
