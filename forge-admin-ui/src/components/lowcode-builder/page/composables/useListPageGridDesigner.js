import { useListPageGridDesignerPart1 } from './useListPageGridDesigner.part1'
import { useListPageGridDesignerPart2 } from './useListPageGridDesigner.part2'
import { useListPageGridDesignerPart3 } from './useListPageGridDesigner.part3'
import { useListPageGridDesignerPart4 } from './useListPageGridDesigner.part4'
import { useListPageGridDesignerPart5 } from './useListPageGridDesigner.part5'

export function useListPageGridDesigner(props, emit, expose) {
  const d1 = useListPageGridDesignerPart1(props, emit)
  const d2 = useListPageGridDesignerPart2(props, emit, d1)
  const d3 = useListPageGridDesignerPart3(props, emit, d2)
  const d4 = useListPageGridDesignerPart4(props, emit, d3)
  const d5 = useListPageGridDesignerPart5(props, emit, d4, expose)
  d5.__impl?.hydrateInitialState?.()
  const { __impl, hydrateInitialState, mut, ...publicApi } = d5
  return publicApi
}
