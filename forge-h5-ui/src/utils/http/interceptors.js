import { resolveResError } from './helpers'
import { stripNullRequestBody } from './empty-body'
import { useAuthStore } from '@/store'
import { cryptoConfig, decryptResponse, encryptRequest, matchPath, shouldEncrypt } from '@/utils/crypto'
import { initKeyExchange, resetKeyExchange } from '@/utils/crypto/key-exchange'

let refreshTokenPromise = null

function generateUUID() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (character) => {
    const random = Math.random() * 16 | 0
    const value = character === 'x' ? random : (random & 0x3 | 0x8)
    return value.toString(16)
  })
}

export function setupInterceptors(axiosInstance) {
  const SUCCESS_CODES = [0, 200]
  const AUTH_EXPIRED_CODES = ['-8', -8, 401, 11007, 11008]

  function isAuthExpired(code) {
    return AUTH_EXPIRED_CODES.includes(code)
  }

  function refreshAuthToken() {
    const authStore = useAuthStore()
    if (!authStore.accessToken) {
      return Promise.reject(new Error('NO_TOKEN'))
    }

    if (!refreshTokenPromise) {
      refreshTokenPromise = axiosInstance({
        url: '/auth/refreshToken',
        method: 'post',
        needTip: false,
        skipAuthRefresh: true,
      })
        .then((res) => {
          authStore.setToken(res?.data || {})
          return res
        })
        .finally(() => {
          refreshTokenPromise = null
        })
    }

    return refreshTokenPromise
  }

  function retryRequest(config) {
    return axiosInstance({
      ...config,
      _retry: true,
    })
  }

  function expireAuth(code, message, error) {
    useAuthStore().resetAuth()
    const finalMessage = resolveResError(code, message || '登录已过期，请重新登录', true)
    return {
      code,
      message: finalMessage || message || '登录已过期，请重新登录',
      error,
    }
  }

  async function handleAuthExpired(errorInfo, originalConfig) {
    const { code, message } = errorInfo || {}
    if (originalConfig?.skipAuthRefresh || originalConfig?._retry) {
      return Promise.reject(expireAuth(code, message, errorInfo?.error))
    }

    try {
      await refreshAuthToken()
      if (originalConfig) {
        return retryRequest(originalConfig)
      }
    }
    catch (refreshError) {
      return Promise.reject(expireAuth(code, message, refreshError))
    }

    return Promise.reject(errorInfo)
  }

  /**
   * 响应成功拦截器
   */
  function resResolve(response) {
    try {
      response = decryptResponse(response)
    }
    catch (error) {
      if (error.message === 'DECRYPT_ERROR') {
        resetKeyExchange()
        return Promise.reject({ code: 401, message: '安全会话已过期，请重新操作', error, skipErrorHandler: true })
      }
      return Promise.reject({ code: 500, message: '解密数据失败', error, skipErrorHandler: true })
    }

    const { data, status, config, statusText, headers } = response

    // 处理JSON响应
    if (headers['content-type']?.includes('json')) {
      // 检查新的响应格式 (respCode)
      if (data && data.respCode === '0000') {
        return Promise.resolve(data)
      }

      // 兼容旧的响应格式
      if (SUCCESS_CODES.includes(data?.code) || SUCCESS_CODES.includes(data?.respCode)) {
        return Promise.resolve(data)
      }

      // 业务错误响应
      const code = data?.code ?? data?.respCode ?? status
      const message = data?.message ?? data?.msg ?? data?.respDesc ?? statusText
      const needTip = config?.needTip !== false
      const errorInfo = {
        code,
        message,
        error: data ?? response,
        isBusinessError: true,
        needTip,
        config,
      }

      if (isAuthExpired(code)) {
        return handleAuthExpired(errorInfo, config)
      }

      return Promise.reject(errorInfo)
    }

    // 非JSON响应直接返回
    return Promise.resolve(data ?? response)
  }

  /**
   * 响应失败拦截器 - 统一错误处理入口
   */
  async function resReject(error) {
    // 1. 处理已经在 resResolve 中标记了 skipErrorHandler 的错误
    if (error?.skipErrorHandler) {
      return Promise.reject(error)
    }

    // 2. 处理网络错误（没有 response）
    if (!error || !error.response) {
      // 如果是业务错误（从 resResolve 传来的）
      if (error?.isBusinessError) {
        const { code, message, needTip = true } = error
        if (isAuthExpired(code)) {
          return handleAuthExpired(error, error.config)
        }
        const finalMessage = resolveResError(code, message, needTip)
        return Promise.reject({ code, message: finalMessage, error: error.error })
      }

      // 网络错误或其他错误
      const code = error?.code || 'NETWORK_ERROR'
      const message = error?.message || '网络连接失败，请检查您的网络'
      resolveResError(code, message, true)
      return Promise.reject({ code, message, error })
    }

    // 3. 处理HTTP错误响应
    const { data, status, config } = error.response
    const code = data?.code ?? data?.respCode ?? status
    const message = data?.message ?? data?.msg ?? data?.respDesc ?? error.message
    const needTip = config?.needTip !== false
    if (isAuthExpired(code)) {
      return handleAuthExpired({ code, message, needTip, error: error.response?.data || error.response }, config)
    }
    const finalMessage = resolveResError(code, message, needTip)
    return Promise.reject({
      code,
      message: finalMessage,
      error: error.response?.data || error.response,
    })
  }

  axiosInstance.interceptors.request.use(config => reqResolve(config, axiosInstance), reqReject)
  axiosInstance.interceptors.response.use(resResolve, resReject)
}

/**
 * 请求拦截器
 */
async function reqResolve(config, axiosInstance) {
  const authStore = useAuthStore()

  // 设置默认headers
  config.headers = config.headers || {}
  stripNullRequestBody(config)

  // 生成traceid: 时间戳+5位随机数
  const timestamp = Date.now()
  const random = Math.floor(10000 + Math.random() * 90000)
  config.headers.traceId = `${timestamp}${random}`

  if (config.needToken !== false && authStore.accessToken) {
    config.headers.Authorization = `${authStore.tokenType || 'Bearer'} ${authStore.accessToken}`
  }

  if (cryptoConfig.enabled && cryptoConfig.enableReplay && config.replay !== false) {
    const path = (config.url || '').split('?')[0]
    const excluded = cryptoConfig.replayExcludePaths.some(pattern => matchPath(path, pattern))
    const included = !cryptoConfig.replayIncludePaths.length
      || cryptoConfig.replayIncludePaths.some(pattern => matchPath(path, pattern))
    if (!excluded && included) {
      config.headers['X-Timestamp'] = Date.now().toString()
      config.headers['X-Nonce'] = generateUUID()
    }
  }

  // 即使请求体无需加密，也要为可能的加密响应准备会话密钥。
  if (shouldEncrypt(config.url)) {
    const exchanged = await initKeyExchange(axiosInstance, authStore.accessToken || '')
    if (!exchanged) {
      return Promise.reject({ code: 'CRYPTO_EXCHANGE_FAILED', message: '安全通道初始化失败', config })
    }
  }

  config = encryptRequest(config)

  return config
}

function reqReject(error) {
  return Promise.reject(error)
}
