import { applyCrudGeneratorPart1 } from './useCrudGenerator.part1.js'

export function useCrudGenerator() {
  let api = applyCrudGeneratorPart1()
  const { __impl, mut, ...publicApi } = api
  return publicApi
}
