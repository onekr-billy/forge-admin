<template>
  <header ref="header" class="business-workbench-header" @keydown.esc="closeAndFocus">
    <div class="workbench-brand">
      <TheLogo />
      <TheTitle />
    </div>
    <nav class="business-mega-navigation" aria-label="平台主导航">
      <button v-if="overflowing" type="button" class="menu-scroll" aria-label="向左查看更多菜单" :disabled="arrivedState.left" @click="scrollMenus(-1)">
        <i class="i-lucide:chevron-left" />
      </button>
      <div ref="track" class="business-mega-triggers" @keydown.right.prevent="moveFocus($event, 1)" @keydown.left.prevent="moveFocus($event, -1)">
        <button
          v-for="item in menus" :key="item.key" type="button" :data-menu-key="item.key"
          :class="{ active: store.activeMenu ? store.activeMenu === item.key : activeRootKey === item.key }"
          :aria-expanded="item.children.length ? store.activeMenu === item.key : undefined"
          :aria-controls="item.children.length ? 'business-mega-panel' : undefined"
          :aria-current="activeRootKey === item.key ? 'true' : undefined"
          @click="activateMenuFromHeader(item)" @mouseenter="hoverMenu(item)" @keydown.down.prevent="openAndFocus(item)"
        >
          {{ item.label }}
        </button>
        <span v-if="!menus.length" class="menu-empty">{{ permissionStore.menuDataLoaded ? '暂无可访问菜单' : '菜单加载中…' }}</span>
      </div>
      <button v-if="overflowing" type="button" class="menu-scroll" aria-label="向右查看更多菜单" :disabled="arrivedState.right" @click="scrollMenus(1)">
        <i class="i-lucide:chevron-right" />
      </button>
    </nav>
    <div class="business-header-tools">
      <MenuSearch />
      <ToggleTheme class="workbench-theme-toggle" />
      <MessageNotification />
      <LayoutSetting />
      <UserAvatar />
    </div>
    <WorkbenchMegaPanel />
  </header>
</template>

<script setup>
import { onClickOutside, useResizeObserver, useScroll } from '@vueuse/core'
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import LayoutSetting from '@/components/common/LayoutSetting.vue'
import TheLogo from '@/components/common/TheLogo.vue'
import TheTitle from '@/components/common/TheTitle.vue'
import ToggleTheme from '@/components/common/ToggleTheme.vue'
import MenuSearch from '@/layouts/components/MenuSearch.vue'
import MessageNotification from '@/layouts/components/MessageNotification.vue'
import UserAvatar from '@/layouts/components/UserAvatar.vue'
import { usePermissionStore } from '@/store'
import { useBusinessWorkbenchStore } from '@/stores/layout/businessWorkbenchStore'
import { useWorkbenchNavigation } from '../useWorkbenchNavigation'
import WorkbenchMegaPanel from './WorkbenchMegaPanel.vue'

const store = useBusinessWorkbenchStore()
const permissionStore = usePermissionStore()
const { menus, activeRootKey, activateMenu } = useWorkbenchNavigation()
const header = ref(null)
const track = ref(null)
const overflowing = ref(false)
let hoverTimer
const { arrivedState, measure } = useScroll(track)
onClickOutside(header, () => store.closeMenus())

function measureTrack() {
  overflowing.value = Boolean(track.value && track.value.scrollWidth > track.value.clientWidth + 2)
  measure()
}
useResizeObserver(track, measureTrack)
watch(menus, () => nextTick(measureTrack))
watch(activeRootKey, () => nextTick(() => {
  const button = [...(track.value?.querySelectorAll('button') || [])].find(node => node.dataset.menuKey === activeRootKey.value)
  button?.scrollIntoView({ block: 'nearest', inline: 'nearest' })
}))

function scrollMenus(direction) {
  track.value?.scrollBy({ left: direction * track.value.clientWidth * 0.7, behavior: 'smooth' })
}
function hoverMenu(item) {
  if (!window.matchMedia('(hover: hover)').matches)
    return
  window.clearTimeout(hoverTimer)
  hoverTimer = window.setTimeout(() => {
    store.activeMenu = item.children.length ? item.key : null
  }, 70)
}
function activateMenuFromHeader(item) {
  window.clearTimeout(hoverTimer)
  activateMenu(item)
}
async function openAndFocus(item) {
  if (!item.children.length)
    return
  store.activeMenu = item.key
  await nextTick()
  header.value?.querySelector('.mega-menu-search input')?.focus()
}
function closeAndFocus() {
  const key = store.activeMenu
  store.closeMenus()
  const button = [...(track.value?.querySelectorAll('button') || [])].find(node => node.dataset.menuKey === key)
  button?.focus()
}
function moveFocus(event, direction) {
  const buttons = [...(track.value?.querySelectorAll('button') || [])]
  const index = buttons.indexOf(event.target)
  if (index >= 0)
    buttons[(index + direction + buttons.length) % buttons.length]?.focus()
}
onBeforeUnmount(() => window.clearTimeout(hoverTimer))
</script>
