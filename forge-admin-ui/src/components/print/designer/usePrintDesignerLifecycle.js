import { onBeforeUnmount, onMounted } from 'vue'

export function usePrintDesignerLifecycle(store, confirmDiscard) {
  function beforeUnload(event) {
    if (store.dirty || store.saving) {
      event.preventDefault()
      event.returnValue = ''
    }
  }
  function canLeave() {
    return !(store.dirty || store.saving) || confirmDiscard('有未保存的打印模板修改，确定放弃吗？')
  }
  onMounted(() => window.addEventListener('beforeunload', beforeUnload))
  onBeforeUnmount(() => {
    window.removeEventListener('beforeunload', beforeUnload)
    store.cancelGesture()
    store.previewOpen = false
    store.generation++
  })
  return { canLeave }
}
