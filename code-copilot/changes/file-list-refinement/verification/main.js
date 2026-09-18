import { createApp, h } from 'vue'
import { createRouter, createMemoryHistory } from 'vue-router'
import { setupStore, useUserStore, useAppStore } from '@/store'
import { request } from '@/utils/request'
import { setupNaiveDiscreteApi } from '@/utils/naiveTools'
import { cryptoConfig } from '@/utils/crypto/crypto-config'
import { applyThemeConfig, defaultThemeConfig } from '@/config/theme.config'
import tableScrollEnhance from '@/directives/modules/tableScrollEnhance'
import '@/styles/reset.css'
import '@/styles/design-tokens.css'
import '@/styles/global.css'
import '@/styles/theme.css'
import 'uno.css'
import Preview from './preview.vue'

// 独立验证入口：请求仅由浏览器脚本的模拟 API 处理，不连接真实后端。
cryptoConfig.enabled = false
cryptoConfig.enableReplay = false
request.defaults.baseURL = '/verification-api'
const app = createApp({ render: () => h(Preview) })
setupStore(app)
useUserStore().userInfo = { userId: '1', tenantId: '1', isAdmin: true }
useAppStore().isDark = false
applyThemeConfig(defaultThemeConfig, false)
setupNaiveDiscreteApi()
app.directive('table-scroll-enhance', tableScrollEnhance)
const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/system/file-list', component: Preview }] })
app.use(router)
await router.push('/system/file-list')
await router.isReady()
app.mount('#app')
