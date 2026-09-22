<template>
  <Transition name="mega-panel">
    <div v-if="expandedMenu" id="business-mega-panel" class="business-mega-panel" :aria-label="`${expandedMenu.label}子菜单`" :data-menu-key="expandedMenu.key">
      <div class="business-mega-inner">
        <aside class="mega-panel-aside">
          <div class="mega-panel-heading">
            <span class="mega-panel-icon" aria-hidden="true">
              <IconRenderer v-if="typeof expandedMenu.icon === 'string' && expandedMenu.icon" :icon="expandedMenu.icon" :size="22" />
              <component :is="expandedMenu.icon" v-else-if="expandedMenu.icon" />
              <i v-else class="i-lucide:layout-grid" />
            </span>
            <h2>{{ expandedMenu.label }}</h2>
          </div>
          <p class="mega-panel-description">
            {{ menuDescription }}
          </p>

          <label class="mega-menu-search">
            <i class="i-lucide:search" aria-hidden="true" />
            <input v-model.trim="searchQuery" type="search" :placeholder="`搜索${expandedMenu.label}`" :aria-label="`搜索${expandedMenu.label}菜单`" autocomplete="off">
            <button v-if="searchQuery" type="button" aria-label="清空菜单搜索" @click="searchQuery = ''">
              <i class="i-lucide:x" />
            </button>
          </label>

          <nav v-if="filteredSections.length" class="mega-section-nav" :aria-label="`${expandedMenu.label}二级菜单`">
            <button
              v-for="section in filteredSections" :key="section.key" type="button"
              :class="{ 'is-active': activeSection?.key === section.key, 'is-current': currentSectionKey === section.key }"
              :aria-current="currentSectionKey === section.key ? 'page' : undefined"
              @click="handleSection(section)" @mouseenter="previewSection(section)" @focus="previewSection(section)"
            >
              <span class="mega-section-nav__icon" aria-hidden="true">
                <IconRenderer v-if="typeof section.icon === 'string' && section.icon" :icon="section.icon" :size="15" />
                <component :is="section.icon" v-else-if="section.icon" />
                <i v-else class="i-lucide:folder" />
              </span>
              <span class="mega-section-nav__label">{{ section.title }}</span>
              <i v-if="section.hasChildren" class="i-lucide:chevron-right mega-section-nav__arrow" aria-hidden="true" />
            </button>
          </nav>

          <div v-if="filteredSections.length" class="mega-section-nav-summary">
            {{ filteredSections.length }} 个二级菜单 · {{ totalFeatureCount }} 个入口
          </div>
        </aside>

        <section class="mega-panel-main">
          <header class="mega-panel-toolbar">
            <div>
              <strong>{{ activeSection?.title || '全部功能' }}</strong>
              <span>{{ activeSection?.hasChildren ? `${activeFeatureCount} 个可访问入口` : '直接入口' }}</span>
            </div>
            <button class="mega-close" type="button" aria-label="关闭菜单" @click="store.closeMenus()">
              <i class="i-lucide:x" />
            </button>
          </header>

          <div v-if="activeSection" class="mega-links">
            <Transition name="mega-section-view" mode="out-in">
              <section v-if="activeSection.hasChildren" :key="activeSection.key" class="mega-section-content">
                <WorkbenchMenuBranch :items="activeSection.items" />
              </section>
              <section v-else :key="`direct-${activeSection.key}`" class="mega-direct-entry">
                <span class="mega-direct-entry__icon" aria-hidden="true">
                  <IconRenderer v-if="typeof activeSection.icon === 'string' && activeSection.icon" :icon="activeSection.icon" :size="24" />
                  <component :is="activeSection.icon" v-else-if="activeSection.icon" />
                  <i v-else class="i-lucide:arrow-up-right" />
                </span>
                <strong>{{ activeSection.title }}</strong>
                <span>该功能可直接进入，无下级菜单。</span>
                <button type="button" @click="navigate(activeSection.entry)">
                  进入功能
                </button>
              </section>
            </Transition>
          </div>

          <div v-else class="mega-search-empty" role="status">
            <i class="i-lucide:search-x" aria-hidden="true" />
            <strong>没有找到相关菜单</strong>
            <span>请换一个关键词，或清空搜索查看全部功能。</span>
            <button type="button" @click="searchQuery = ''">
              清空搜索
            </button>
          </div>
        </section>
      </div>
    </div>
  </Transition>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import IconRenderer from '@/components/IconRenderer.vue'
import { useBusinessWorkbenchStore } from '@/stores/layout/businessWorkbenchStore'
import { countMenuLeaves, filterMegaSections } from '../menu-model'
import { useWorkbenchNavigation } from '../useWorkbenchNavigation'
import WorkbenchMenuBranch from './WorkbenchMenuBranch.vue'

const store = useBusinessWorkbenchStore()
const { activeKey, expandedMenu, navigate, sections, trail } = useWorkbenchNavigation()
const searchQuery = ref('')
const selectedSectionKey = ref(null)
const filteredSections = computed(() => filterMegaSections(sections.value, searchQuery.value))
const menuDescription = computed(() => `集中查看和使用${expandedMenu.value?.label || '当前模块'}下的授权能力。`)
const currentSectionKey = computed(() => trail.value[1]?.key)
const activeSection = computed(() => {
  const visible = filteredSections.value
  return visible.find(section => section.hasChildren && section.key === selectedSectionKey.value)
    || visible.find(section => section.hasChildren && section.key === currentSectionKey.value)
    || visible.find(section => section.hasChildren)
    || visible.find(section => section.key === activeKey.value)
    || visible[0]
})
const activeFeatureCount = computed(() => activeSection.value?.hasChildren ? countMenuLeaves(activeSection.value.items) : 1)
const totalFeatureCount = computed(() => filteredSections.value.reduce((total, section) => total + (section.hasChildren ? countMenuLeaves(section.items) : 1), 0))

function previewSection(section) {
  if (section.hasChildren)
    selectedSectionKey.value = section.key
}

function handleSection(section) {
  if (section.hasChildren)
    selectedSectionKey.value = section.key
  else
    navigate(section.entry)
}

watch(() => store.activeMenu, () => {
  searchQuery.value = ''
  selectedSectionKey.value = null
})
watch(searchQuery, () => {
  selectedSectionKey.value = null
})
</script>
