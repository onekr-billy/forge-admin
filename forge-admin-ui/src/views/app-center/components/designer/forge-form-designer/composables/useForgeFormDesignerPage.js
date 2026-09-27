import { applyForgeFormDesignerPagePart1 } from './useForgeFormDesignerPage.part1.js'

export function useForgeFormDesignerPage(props, emit, expose) {
  let api = applyForgeFormDesignerPagePart1(props, emit, expose)
  const { __impl, mut, props: _p, emit: _e, ...publicApi } = api
  return publicApi
}
