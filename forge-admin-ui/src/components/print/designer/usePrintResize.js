import { usePrintDrag } from './usePrintDrag'

export function usePrintResize(store) {
  return usePrintDrag(store, true)
}
