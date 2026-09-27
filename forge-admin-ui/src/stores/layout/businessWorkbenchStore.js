import { defineStore } from 'pinia'

export const useBusinessWorkbenchStore = defineStore('business-workbench', {
  state: () => ({ activeMenu: null }),
  actions: {
    closeMenus() {
      this.activeMenu = null
    },
  },
})
