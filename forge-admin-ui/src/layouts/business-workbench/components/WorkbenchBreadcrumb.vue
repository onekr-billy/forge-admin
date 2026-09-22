<template>
  <nav class="business-breadcrumb" aria-label="面包屑">
    <RouterLink to="/home">
      首页
    </RouterLink>
    <template v-for="(item, index) in trail" :key="item.key">
      <span>/</span>
      <strong v-if="index === trail.length - 1 && !detailTitle" aria-current="page">{{ item.label }}</strong>
      <button v-else type="button" @click="navigate(item)">
        {{ item.label }}
      </button>
    </template>
    <template v-if="detailTitle || !trail.length">
      <span>/</span><strong aria-current="page">{{ detailTitle || route.meta.title || '工作台' }}</strong>
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
const detailTitle = computed(() => trail.value.length && !isSameMenuPath(trail.value.at(-1).path, route.path) ? route.meta.title : '')
</script>
