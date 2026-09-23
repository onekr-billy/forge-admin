<template>
  <header class="platform-heading">
    <div class="heading-copy">
      <h1>{{ title }}</h1>
      <p>{{ description }}</p>
    </div>
    <nav aria-label="开放平台导航">
      <RouterLink v-for="item in visibleLinks" :key="item.path" :to="item.path" :class="{ current: active === item.key }" :aria-current="active === item.key ? 'page' : undefined">
        {{ item.label }}
      </RouterLink>
    </nav>
  </header>
</template>

<script setup>
import { computed } from 'vue'
import { openPlatformPages } from '@/router/open-platform-routes'
import { useUserStore } from '@/store'

defineProps({ title: String, description: String, active: String })
const user = useUserStore()
const visibleLinks = computed(() => openPlatformPages.filter(item => item.key !== 'grant'
  && (user.isAdmin || user.permissions?.includes('*:*:*') || user.permissions?.includes(item.permission))))
</script>

<style scoped>
.platform-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--border-light);
  background: var(--bg-primary);
}
.heading-copy {
  min-width: 0;
}
h1 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}
p {
  margin: 5px 0 0;
  font-size: 12px;
  color: var(--text-tertiary);
}
nav {
  display: flex;
  gap: 2px;
  padding: 3px;
  border: 1px solid var(--border-light);
  border-radius: 4px;
  background: var(--bg-secondary);
  flex-shrink: 0;
}
nav a {
  padding: 6px 10px;
  border-radius: 4px;
  color: var(--text-secondary);
  font-size: 13px;
}
nav a:hover {
  color: var(--primary-color);
}
nav a.current {
  color: var(--primary-color);
  background: var(--bg-primary);
  box-shadow: 0 1px 3px rgb(0 0 0 / 6%);
  font-weight: 600;
}
@media (max-width: 760px) {
  .platform-heading {
    align-items: flex-start;
    flex-direction: column;
    gap: 8px;
  }
  nav {
    flex-wrap: wrap;
  }
}
</style>
