<template>
  <nav class="portal-navigation" :class="[`is-${style}`, { collapsed }]" aria-label="应用页面导航">
    <template v-for="navItem in tree" :key="navItem.id">
      <section v-if="navItem.type === 'group'" class="portal-nav-group">
        <div class="portal-nav-group-title">
          <IconRenderer v-if="navItem.icon" :icon="navItem.icon" :size="16" />
          <span v-if="!collapsed || style === 'top'">{{ navItem.title }}</span>
        </div>
        <div class="portal-nav-group-items">
          <PortalNavigationItem
            v-for="child in navItem.children"
            :key="child.id"
            :item="child"
            :active="child.id === currentPageId"
            :collapsed="collapsed && style !== 'top'"
            @select="emit('select', $event)"
          />
        </div>
      </section>
      <PortalNavigationItem
        v-else
        :item="navItem"
        :active="navItem.id === currentPageId"
        :collapsed="collapsed && style !== 'top'"
        @select="emit('select', $event)"
      />
    </template>
  </nav>
</template>

<script setup>
import { computed, defineComponent, h } from 'vue'
import IconRenderer from '@/components/IconRenderer.vue'

const props = defineProps({
  nodes: { type: Array, default: () => [] },
  currentPageId: { type: String, default: '' },
  style: { type: String, default: 'side' },
  collapsed: { type: Boolean, default: false },
})

const emit = defineEmits(['select'])
const tree = computed(() => buildTree(props.nodes))

const PortalNavigationItem = defineComponent({
  name: 'PortalNavigationItem',
  props: {
    item: { type: Object, required: true },
    active: Boolean,
    collapsed: Boolean,
  },
  emits: ['select'],
  setup(itemProps, { emit: itemEmit }) {
    return () => h('button', {
      'type': 'button',
      'class': ['portal-nav-item', { active: itemProps.active }],
      'title': itemProps.collapsed ? itemProps.item.title : undefined,
      'aria-current': itemProps.active ? 'page' : undefined,
      'onClick': () => itemEmit('select', itemProps.item.id),
    }, [
      itemProps.item.icon
        ? h(IconRenderer, { icon: itemProps.item.icon, size: 17 })
        : h('span', { 'class': 'portal-nav-item-dot', 'aria-hidden': 'true' }),
      itemProps.collapsed ? null : h('span', itemProps.item.title),
    ])
  },
})

function buildTree(nodes) {
  const visible = (nodes || []).filter(node => (node.navigationVisible ?? node.settings?.navigationVisible) !== false)
  const byParent = new Map()
  visible.forEach((node) => {
    const key = node.parentId == null || node.parentId === '' ? '__root__' : String(node.parentId)
    if (!byParent.has(key))
      byParent.set(key, [])
    byParent.get(key).push(node)
  })
  const visit = (parentKey, visited = new Set()) => (byParent.get(parentKey) || [])
    .sort((left, right) => Number(left.sort || 0) - Number(right.sort || 0))
    .filter(node => !visited.has(String(node.id)))
    .map((node) => {
      const nextVisited = new Set(visited).add(String(node.id))
      return { ...node, children: visit(String(node.id), nextVisited) }
    })
    .filter(node => node.type === 'page' || node.children.length)
  return visit('__root__')
}
</script>

<style scoped>
.portal-navigation {
  display: flex;
  min-width: 0;
}

.portal-navigation.is-side,
.portal-navigation.is-collapsed {
  width: 232px;
  flex-direction: column;
  gap: 4px;
  padding: 14px 10px;
}

.portal-navigation.collapsed {
  width: 64px;
}

.portal-navigation.is-top {
  align-items: center;
  gap: 4px;
  overflow-x: auto;
  padding: 0 8px;
}

.portal-nav-group {
  display: contents;
}

.is-side .portal-nav-group,
.is-collapsed .portal-nav-group {
  display: block;
  margin-top: 8px;
}

.portal-nav-group-title {
  display: flex;
  height: 30px;
  align-items: center;
  gap: 8px;
  padding: 0 10px;
  color: var(--portal-text-muted, #8f959e);
  font-size: 12px;
  font-weight: 600;
}

.portal-nav-group-items {
  display: contents;
}

:deep(.portal-nav-item) {
  display: flex;
  height: 38px;
  min-width: 0;
  align-items: center;
  gap: 10px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--portal-text-muted, #4e5969);
  font: inherit;
  padding: 0 12px;
  text-align: left;
  white-space: nowrap;
  cursor: pointer;
}

.is-top :deep(.portal-nav-item) {
  height: 34px;
  padding: 0 10px;
}

.collapsed :deep(.portal-nav-item) {
  width: 42px;
  justify-content: center;
  padding: 0;
}

:deep(.portal-nav-item:hover) {
  background: color-mix(in srgb, var(--portal-primary, #3370ff) 7%, transparent);
  color: var(--portal-primary, #3370ff);
}

:deep(.portal-nav-item.active) {
  background: color-mix(in srgb, var(--portal-primary, #3370ff) 12%, transparent);
  color: var(--portal-primary, #3370ff);
  font-weight: 600;
}

:deep(.portal-nav-item-dot) {
  width: 5px;
  height: 5px;
  flex: 0 0 auto;
  border-radius: 999px;
  background: currentColor;
  opacity: 0.56;
}

@media (max-width: 768px) {
  .portal-navigation.is-side,
  .portal-navigation.is-collapsed,
  .portal-navigation.collapsed {
    width: 100%;
    flex-direction: row;
    gap: 4px;
    overflow-x: auto;
    padding: 8px 12px;
  }

  .portal-nav-group,
  .is-side .portal-nav-group,
  .is-collapsed .portal-nav-group {
    display: contents;
  }

  .portal-nav-group-title {
    display: none;
  }

  .collapsed :deep(.portal-nav-item) {
    width: auto;
    justify-content: flex-start;
    padding: 0 12px;
  }
}
</style>
