import { applyForgeFormCanvasNodePart1 } from './useForgeFormCanvasNode.part1.js'
import { applyForgeFormCanvasNodePart2 } from './useForgeFormCanvasNode.part2.js'

export function useForgeFormCanvasNode(props, emit) {
  let api = applyForgeFormCanvasNodePart1(props, emit)
  api = applyForgeFormCanvasNodePart2(props, emit, api)
  const { __impl, mut, props: _p, emit: _e, ...publicApi } = api
  return publicApi
}
