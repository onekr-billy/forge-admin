import { applyExtensionEditorDrawerPart1 } from './useExtensionEditorDrawer.part1.js'

export function useExtensionEditorDrawer(props, emit) {
  let api = applyExtensionEditorDrawerPart1(props, emit)
  const { __impl, mut, props: _p, emit: _e, ...publicApi } = api
  return publicApi
}
