<template>
  <header class="platform-heading">
    <div class="heading-copy">
      <h1>{{ title }}</h1>
      <p>{{ description }}</p>
    </div>
    <nav aria-label="开放平台导航">
      <RouterLink v-for="item in visibleLinks" :key="item.path" :to="item.path" :class="{ current: active === item.key }">
        {{ item.label }}
      </RouterLink>
    </nav>
  </header>
</template>

<script setup>
import { computed } from 'vue'
import { useUserStore } from '@/store'

defineProps({ title: String, description: String, active: String })
const user = useUserStore()
const visibleLinks = computed(() => [
  { key: 'catalog', label: '能力目录', path: '/open-platform/capability-catalog', permission: 'ai:capability:query' },
  { key: 'client', label: '接入系统', path: '/open-platform/capability-client', permission: 'ai:capability:client:query' },
  { key: 'invocation', label: '调用记录', path: '/open-platform/capability-invocation', permission: 'ai:capability:invocation:query' },
].filter(item => user.isAdmin || user.permissions?.includes('*:*:*') || user.permissions?.includes(item.permission)))
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
  gap: 4px;
  flex-shrink: 0;
}
nav a {
  padding: 6px 10px;
  border-radius: 4px;
  color: var(--text-secondary);
  font-size: 13px;
}
nav a:hover,
nav a.current {
  color: var(--primary-color);
  background: var(--primary-color-10, var(--bg-secondary));
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
