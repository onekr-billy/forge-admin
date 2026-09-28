import { defineStore } from 'pinia'
import { defaultPrimaryColor } from '@/settings.js'
import { resolveTenantLogoUrl } from '@/utils/tenant-brand'

export const useAppStore = defineStore('app', {
  state: () => ({
    isDark: false,
    primaryColor: defaultPrimaryColor,
    brandConfig: null,
    keepAliveNames: [] // 需要缓存的页面名称列表
  }),
  actions: {
    setBrandConfig(config) {
      this.brandConfig = config || null
    },
    toggleDark() {
      this.isDark = !this.isDark
    },
    setPrimaryColor(color) {
      this.primaryColor = color
    },
    // 添加需要缓存的页面
    addKeepAliveName(name) {
      !this.keepAliveNames.includes(name) && this.keepAliveNames.push(name)
    },
    // 删除需要缓存的页面
    removeKeepAliveName(name) {
      this.keepAliveNames = this.keepAliveNames.filter((n) => n !== name)
    },
    // 设置需要缓存的页面列表
    setKeepAliveNames(names = []) {
      this.keepAliveNames = names
    }
  },
  getters: {
    brandName: state => state.brandConfig?.systemName || 'Forge 移动工作台',
    brandLogoUrl: state => resolveTenantLogoUrl(state.brandConfig),
  },
  persist: {
    key: `${import.meta.env.VITE_TENANT || 'default'}_app`,
    pick: ['primaryColor', 'keepAliveNames', 'brandConfig'],
    storage: sessionStorage,
  },
})
