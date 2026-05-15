/**
 * 存储配置 Composable
 * 全局缓存默认存储配置，组件通过 computed 获取 fileSize/storageType 默认值
 *
 * 使用：
 * import { useStorageConfig } from '@/composables/useStorageConfig'
 * const { storageType, fileSize, loadConfig } = useStorageConfig()
 */
import { computed, ref } from 'vue'
import { getDefaultStorageConfig } from '@/api/config'

const CACHE_KEY = 'forge_storage_config'
let cached = null
let pending = null
const configRef = ref(null)

function readCache() {
  try {
    const raw = sessionStorage.getItem(CACHE_KEY)
    return raw ? JSON.parse(raw) : null
  }
  catch { return null }
}
function writeCache(data) {
  try { sessionStorage.setItem(CACHE_KEY, JSON.stringify(data)) }
  catch { /* noop */ }
}

export function useStorageConfig() {
  async function loadConfig(force = false) {
    if (!force) {
      if (configRef.value)
        return configRef.value
      if (cached) { configRef.value = cached; return cached }
      const fromCache = readCache()
      if (fromCache) { cached = fromCache; configRef.value = fromCache; return fromCache }
    }
    if (pending)
      return pending
    pending = (async () => {
      try {
        const res = await getDefaultStorageConfig()
        if (res?.code === 200 && res.data) {
          cached = res.data
          configRef.value = res.data
          writeCache(res.data)
          return res.data
        }
      }
      catch (e) {
        console.warn('[StorageConfig] 加载默认存储配置失败:', e)
      }
      finally { pending = null }
      return null
    })()
    return pending
  }

  // 返回 computed 引用，组件可以安全地读默认值
  const storageType = computed(() => configRef.value?.storageType ?? 'local')
  const fileSize = computed(() => configRef.value?.maxFileSize ?? 10)
  const allowedTypes = computed(() => {
    const types = configRef.value?.allowedTypes
    return types ? types.split(',').map(s => s.trim()).filter(Boolean) : []
  })

  return { config: configRef, loadConfig, storageType, fileSize, allowedTypes }
}
