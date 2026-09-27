import { applyFlowTodoPart1 } from './useFlowTodo.part1.js'
import { applyFlowTodoPart2 } from './useFlowTodo.part2.js'

export function useFlowTodo() {
  let api = applyFlowTodoPart1()
  api = applyFlowTodoPart2(api)
  const { __impl, mut, ...publicApi } = api
  return publicApi
}
