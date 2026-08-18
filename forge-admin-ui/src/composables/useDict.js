/**
 * 字典数据管理 Composable
 * 用于加载和缓存字典数据
 *
 * 使用示例：
 * import { useDict } from '@/composables/useDict'
 *
 * // 在 setup 中使用
 * const { dict } = useDict('case_status', 'matter_type')
 *
 * // 访问字典数据
 * dict.case_status // [{ label: '待处理', value: '1', ... }, ...]
 */

import { onMounted, ref } from 'vue'
import { request } from '@/utils'

// 全局字典缓存
const dictCache = new Map()
const dictPendingCache = new Map()

/**
 * 加载字典数据
 * @param {string} dictType - 字典类型
 * @returns {Promise<Array>} 字典数据列表
 */
async function loadDictData(dictType) {
  try {
    const encodedDictType = encodeURIComponent(dictType)
    const res = await request.get(`/system/dict/data/type/${encodedDictType}`, {
      needTip: false,
    })

    if (res.code === 200) {
      // 转换为标准格式：{ label, value, ... }
      const dictList = (res.data || []).map(item => ({
        label: item.dictLabel,
        value: item.dictValue,
        dictCode: item.dictCode,
        dictSort: item.dictSort,
        parentDictCode: item.parentDictCode,
        linkedDictType: item.linkedDictType,
        linkedDictValue: item.linkedDictValue,
        cssClass: item.cssClass,
        listClass: item.listClass || 'default', // 默认为 default
        isDefault: item.isDefault,
        status: item.dictStatus !== undefined ? item.dictStatus : item.status, // 兼容不同字段名
        remark: item.remark,
        raw: item, // 保留原始数据
      }))

      // 按排序字段排序
      dictList.sort((a, b) => (a.dictSort || 0) - (b.dictSort || 0))

      return dictList
    }

    throw new Error(res?.message || `字典 ${dictType} 响应无效`)
  }
  catch (error) {
    console.error(`加载字典 ${dictType} 失败:`, error)
    throw error
  }
}

/**
 * 获取原始字典请求。失败时保持 rejected，供 useDict 逐项处理；
 * 只有成功响应（包括合法空字典）才会进入全局缓存。
 * @param {string} dictType - 字典类型
 * @param {boolean} forceReload - 是否强制重新加载
 * @returns {Promise<Array>} 字典数据列表
 */
function getDictRequest(dictType, forceReload = false) {
  if (!forceReload && dictCache.has(dictType))
    return Promise.resolve(dictCache.get(dictType))

  // 强制刷新也复用正在进行的请求，避免同一字典并发重复加载。
  if (dictPendingCache.has(dictType))
    return dictPendingCache.get(dictType)

  const pending = loadDictData(dictType)
    .then((data) => {
      dictCache.set(dictType, data)
      return data
    })
    .finally(() => {
      if (dictPendingCache.get(dictType) === pending)
        dictPendingCache.delete(dictType)
    })
  dictPendingCache.set(dictType, pending)
  return pending
}

/**
 * 获取字典数据（带缓存）
 * @param {string} dictType - 字典类型
 * @param {boolean} forceReload - 是否强制重新加载
 * @returns {Promise<Array>} 字典数据列表
 */
async function getDictData(dictType, forceReload = false) {
  try {
    return await getDictRequest(dictType, forceReload)
  }
  catch {
    // 保持公共函数原有的安全返回契约，但失败结果不会污染全局缓存。
    return []
  }
}

/**
 * 清除字典缓存
 * @param {string} dictType - 字典类型，不传则清除所有
 */
function clearDictCache(dictType) {
  if (dictType) {
    dictCache.delete(dictType)
    dictPendingCache.delete(dictType)
  }
  else {
    dictCache.clear()
    dictPendingCache.clear()
  }
}

/**
 * 字典 Composable
 * @param  {...string} dictTypes - 字典类型列表
 * @returns {object} { dict, loading, errors, reload }
 */
export function useDict(...dictTypes) {
  const dict = ref({})
  const loading = ref(false)
  const errors = ref({})

  function applySettledResults(types, results) {
    const nextErrors = { ...errors.value }
    const failedTypes = []

    types.forEach((type, index) => {
      const result = results[index]
      if (result.status === 'fulfilled') {
        dict.value[type] = result.value
        delete nextErrors[type]
        return
      }

      failedTypes.push(type)
      nextErrors[type] = result.reason?.message || `字典 ${type} 加载失败`
    })

    errors.value = nextErrors
    return failedTypes
  }

  async function loadDictTypes(types, forceReload = false, retryOnce = false) {
    const results = await Promise.allSettled(
      types.map(type => getDictRequest(type, forceReload)),
    )
    const failedTypes = applySettledResults(types, results)

    if (retryOnce && failedTypes.length > 0) {
      const retryResults = await Promise.allSettled(
        failedTypes.map(type => getDictRequest(type, true)),
      )
      applySettledResults(failedTypes, retryResults)
    }
  }

  /**
   * 加载所有字典
   */
  async function loadAllDicts() {
    if (dictTypes.length === 0)
      return

    loading.value = true

    try {
      await loadDictTypes(dictTypes, false, true)
    }
    catch (error) {
      console.error('加载字典失败:', error)
    }
    finally {
      loading.value = false
    }
  }

  /**
   * 重新加载字典
   * @param  {...string} types - 要重新加载的字典类型，不传则重新加载所有
   */
  async function reload(...types) {
    const typesToReload = types.length > 0 ? types : dictTypes

    loading.value = true

    try {
      await loadDictTypes(typesToReload, true)
    }
    catch (error) {
      console.error('重新加载字典失败:', error)
    }
    finally {
      loading.value = false
    }
  }

  /**
   * 根据字典值获取标签
   * @param {string} dictType - 字典类型
   * @param {string | number} value - 字典值
   * @returns {string} 字典标签
   */
  function getLabel(dictType, value) {
    const dictList = dict.value[dictType] || []
    const item = dictList.find(d => String(d.value) === String(value))
    return item ? item.label : value
  }

  /**
   * 根据字典值获取字典项
   * @param {string} dictType - 字典类型
   * @param {string | number} value - 字典值
   * @returns {object | null} 字典项
   */
  function getDict(dictType, value) {
    const dictList = dict.value[dictType] || []
    return dictList.find(d => String(d.value) === String(value)) || null
  }

  // 组件挂载时加载字典
  onMounted(() => {
    loadAllDicts()
  })

  return {
    dict,
    loading,
    errors,
    reload,
    getLabel,
    getDict,
  }
}

/**
 * 导出工具函数
 */
export {
  clearDictCache,
  getDictData,
}
