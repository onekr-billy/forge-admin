import { computed } from 'vue'
import { useMenu } from '@/composables/useMenu'
import { usePermissionStore } from '@/store'
import { useBusinessWorkbenchStore } from '@/stores/layout/businessWorkbenchStore'
import { buildMegaSections, buildWorkbenchMenus, findWorkbenchTrail } from './menu-model'

export function useWorkbenchNavigation() {
  const permissionStore = usePermissionStore()
  const store = useBusinessWorkbenchStore()
  const { activeKey, handleMenuSelect } = useMenu()
  const menus = computed(() => buildWorkbenchMenus(permissionStore.menus))
  const trail = computed(() => findWorkbenchTrail(menus.value, activeKey.value))
  const activeRootKey = computed(() => trail.value[0]?.key)
  const expandedMenu = computed(() => menus.value.find(item => item.key === store.activeMenu))
  const sections = computed(() => buildMegaSections(expandedMenu.value))

  function navigate(item) {
    handleMenuSelect(item.key, item.path)
    store.closeMenus()
  }

  function activateMenu(item) {
    if (item.children.length)
      store.activeMenu = store.activeMenu === item.key ? null : item.key
    else
      navigate(item)
  }

  return { menus, trail, activeKey, activeRootKey, expandedMenu, sections, navigate, activateMenu }
}
