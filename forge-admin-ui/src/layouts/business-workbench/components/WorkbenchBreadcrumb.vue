<template>
  <nav class="business-breadcrumb" aria-label="面包屑">
    <template v-if="isHomeOnly">
      <strong aria-current="page">首页</strong>
    </template>
    <template v-else>
      <RouterLink to="/home">
        首页
      </RouterLink>
      <template v-for="(item, index) in visibleTrail" :key="item.key">
        <span>/</span>
        <strong v-if="index === visibleTrail.length - 1 && !detailTitle" aria-current="page">{{ item.label }}</strong>
        <button v-else type="button" @click="navigate(item)">
          {{ item.label }}
        </button>
      </template>
      <template v-if="detailTitle || !visibleTrail.length">
        <span>/</span><strong aria-current="page">{{ detailTitle || route.meta.title || '工作台' }}</strong>
      </template>
    </template>
  </nav>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { isSameMenuPath } from '@/utils/menu-utils'
import { useWorkbenchNavigation } from '../useWorkbenchNavigation'

const route = useRoute()
const { trail, navigate } = useWorkbenchNavigation()

function isHomeMenuItem(item) {
  return isSameMenuPath(item?.path, '/home') || String(item?.label || '').trim() === '首页'
}

/** 固定前缀已是「首页」，trail 里再出现首页会重复 */
const visibleTrail = computed(() => trail.value.filter(item => !isHomeMenuItem(item)))
const isHomeRoute = computed(() => isSameMenuPath(route.path, '/home') || route.path === '/')
/** 当前就在首页时只显示一个「首页」，不再拼「工作台/首页」 */
const isHomeOnly = computed(() => isHomeRoute.value)
const detailTitle = computed(() => {
  if (isHomeRoute.value || !trail.value.length)
    return ''
  const leaf = trail.value.at(-1)
  if (isSameMenuPath(leaf.path, route.path))
    return ''
  return route.meta.title || ''
})
</script>
