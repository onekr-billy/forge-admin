import { useAuthStore } from '@/store'
import loginApi from '@/views/login/api'
import { consumeWeComEntry, readWeComConnection, rememberWeComEntry } from './wecom-entry'

// 企业微信PC客户端工作台免登：仅在企业微信客户端内置浏览器（UA 含 wxwork）生效。
// 流程：无 code → 取授权地址跳转企微 OAuth2；带 code&state 回跳 → 换票据静默登录。
// 登录成功仅写入 token，后续 userInfo/菜单/密钥交换由路由守卫链路自动补齐。
// 免登开关与 connectionCode 由后端连接配置下发（sys_social_config.sso_workbench_enabled），不再前端写死。

async function getConnectionCode() {
  try {
    const explicit = readWeComConnection(window.location, window.sessionStorage)
    if (explicit)
      return explicit
    const res = await loginApi.getWecomSsoConnection('WECHAT_ENTERPRISE')
    if (res?.code === 200 && res.data?.enabled) {
      return res.data.connectionCode || ''
    }
  }
  catch (error) {
    console.warn('[wecom] 获取免登连接配置失败:', error)
  }
  return ''
}

function getUserClient() {
  return import.meta.env.VITE_USER_CLIENT || 'pc'
}

/**
 * 是否运行在企业微信客户端内（UA 含 wxwork）
 */
export function isWeComBrowser() {
  if (typeof window === 'undefined' || typeof navigator === 'undefined') {
    return false
  }
  return /wxwork/i.test(navigator.userAgent || '')
}

function readSearchParam(name) {
  if (typeof window === 'undefined') {
    return ''
  }
  const search = window.location.search || ''
  const matched = search.replace(/^\?/, '').split('&').find(item => item.startsWith(`${name}=`))
  return matched ? decodeURIComponent(matched.slice(name.length + 1)) : ''
}

// 回跳目标域名需在企微应用可信域名内；去掉 query/hash，保留 origin+pathname 作为授权回跳地址
function buildRedirectUri() {
  const { origin, pathname } = window.location
  return `${origin}${pathname}`
}

// 换票登录成功后清理 URL 上的 code&state，避免刷新时用已失效 code 重复回调
function clearCallbackParams() {
  if (typeof window === 'undefined' || typeof window.history?.replaceState !== 'function') {
    return
  }
  const { origin, pathname, hash } = window.location
  const entry = consumeWeComEntry(window.sessionStorage)
  window.history.replaceState(null, '', entry || `${origin}${pathname}${hash || ''}`)
}

/**
 * 企微PC客户端工作台免登编排。
 * 需在 pinia 就绪后、路由挂载前调用；返回状态供入口决定是否继续挂载。
 * - logged-in：已写入 token，继续挂载由守卫补拉用户信息
 * - redirecting：正在跳转企微授权页，调用方应停止挂载
 * - skip / error：回退常规登录页
 */
export async function runWeComAutoLogin() {
  const inWeCom = isWeComBrowser()
  if (!inWeCom) {
    return { status: 'skip', reason: 'not-wecom' }
  }
  const connectionCode = await getConnectionCode()
  // 联调诊断：打开控制台即可确认免登是否触发及跳过原因
  console.warn(`[wecom] 免登检测: inWeCom=${inWeCom}, hasConnectionCode=${!!connectionCode}, ua=${typeof navigator !== 'undefined' ? navigator.userAgent : ''}`)
  if (!connectionCode) {
    console.warn('[wecom] 未在连接配置中开启工作台免登，跳过企微免登')
    return { status: 'skip', reason: 'no-connection-code' }
  }

  const authStore = useAuthStore()
  const userClient = getUserClient()
  const code = readSearchParam('code')
  const state = readSearchParam('state')

  try {
    // 回调阶段：企微携带 code&state 回跳，换票据完成静默登录
    if (code && state) {
      clearCallbackParams()
      if (authStore.accessToken) {
        return { status: 'logged-in', reason: 'already' }
      }
      const callbackRes = await loginApi.wecomLoginCallback({ code, state, userClient })
      const ticket = callbackRes?.data
      if (callbackRes?.code !== 200 || !ticket?.socialTicket) {
        console.warn('[wecom] 企微免登换票失败:', callbackRes?.msg)
        return { status: 'skip', reason: 'callback-failed' }
      }

      const loginRes = await loginApi.login({
        authType: 'oauth2',
        socialTicket: ticket.socialTicket,
        connectionCode: ticket.connectionCode || connectionCode,
        tenantId: ticket.tenantId,
        userClient,
        appId: import.meta.env.VITE_APP_ID || undefined,
      })
      if (loginRes?.code !== 200) {
        console.warn('[wecom] 企微免登登录失败:', loginRes?.msg)
        return { status: 'skip', reason: 'login-failed' }
      }
      // 仅写 token，userInfo/菜单/密钥交换交由路由守卫补齐
      authStore.setToken(loginRes.data || {})
      return { status: 'logged-in' }
    }

    // 已登录无需再次授权
    if (authStore.accessToken) {
      return { status: 'skip', reason: 'already-login' }
    }

    // 授权阶段：取授权地址并跳转企微 OAuth2
    rememberWeComEntry(window.location, window.sessionStorage, connectionCode)
    const authRes = await loginApi.getWecomAuthorize({
      connectionCode,
      redirectUri: buildRedirectUri(),
      userClient,
    })
    const authUrl = authRes?.data?.authUrl
    if (authRes?.code !== 200 || !authUrl) {
      console.warn('[wecom] 获取企微授权地址失败:', authRes?.msg)
      return { status: 'skip', reason: 'authorize-failed' }
    }
    window.location.href = authUrl
    return { status: 'redirecting' }
  }
  catch (error) {
    console.error('[wecom] 企微免登异常:', error)
    return { status: 'error', error }
  }
}
