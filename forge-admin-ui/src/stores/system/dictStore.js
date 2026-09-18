import { defineStore } from 'pinia'
import { markRaw, shallowReactive } from 'vue'

// 字典结果由所有消费组件共享；请求 Promise 不参与响应式追踪或持久化。
export const useDictStore = defineStore('system-dict', () => {
  const dictCache = shallowReactive(new Map())
  const dictPendingCache = markRaw(new Map())

  return { dictCache, dictPendingCache }
})
