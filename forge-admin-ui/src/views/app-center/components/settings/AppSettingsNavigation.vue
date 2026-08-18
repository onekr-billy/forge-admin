<template>
  <section class="settings-section-card">
    <header>
      <h2>导航设置</h2>
      <p>导航风格随门户配置发布；页面顺序与页面设计器共用同一份页面树。</p>
    </header>
    <n-form label-placement="top">
      <n-form-item label="导航风格">
        <n-radio-group :value="modelValue.navigation?.style" @update:value="patchNavigation({ style: $event })">
          <n-radio-button value="side">
            左侧导航
          </n-radio-button>
          <n-radio-button value="top">
            顶部导航
          </n-radio-button>
          <n-radio-button value="collapsed">
            折叠侧栏
          </n-radio-button>
        </n-radio-group>
      </n-form-item>
      <n-space vertical :size="14">
        <n-checkbox :checked="modelValue.navigation?.showLogo" @update:checked="patchNavigation({ showLogo: $event })">
          显示应用 Logo
        </n-checkbox>
        <n-checkbox :checked="modelValue.navigation?.showName" @update:checked="patchNavigation({ showName: $event })">
          显示应用名称
        </n-checkbox>
        <n-checkbox :checked="modelValue.navigation?.collapsible" @update:checked="patchNavigation({ collapsible: $event })">
          允许用户收起导航
        </n-checkbox>
      </n-space>
      <n-divider title-placement="left">
        页面顺序
      </n-divider>
      <div v-if="orderedPages.length" class="settings-page-order">
        <div v-for="(page, index) in orderedPages" :key="page.id" class="settings-page-row">
          <span class="settings-page-index">{{ index + 1 }}</span>
          <div>
            <strong>{{ page.title }}</strong>
            <small>{{ page.id }}</small>
          </div>
          <n-space size="small">
            <n-button quaternary size="small" :disabled="index === 0" @click="move(index, -1)">
              上移
            </n-button>
            <n-button quaternary size="small" :disabled="index === orderedPages.length - 1" @click="move(index, 1)">
              下移
            </n-button>
          </n-space>
        </div>
      </div>
      <n-empty v-else size="small" description="当前应用还没有页面" />
    </n-form>
  </section>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  modelValue: { type: Object, required: true },
  pages: { type: Array, default: () => [] },
})
const emit = defineEmits(['update:modelValue'])

const orderedPages = computed(() => {
  const pageById = new Map(props.pages.filter(page => page.type === 'page').map(page => [String(page.id), page]))
  const requestedOrder = props.modelValue.navigation?.pageOrder || []
  const ordered = requestedOrder.map(id => pageById.get(String(id))).filter(Boolean)
  props.pages.filter(page => page.type === 'page' && !ordered.includes(page)).forEach(page => ordered.push(page))
  return ordered
})

function patchNavigation(value) {
  emit('update:modelValue', {
    ...props.modelValue,
    navigation: { ...(props.modelValue.navigation || {}), ...value },
  })
}

function move(index, direction) {
  const target = index + direction
  if (target < 0 || target >= orderedPages.value.length)
    return
  const next = [...orderedPages.value]
  ;[next[index], next[target]] = [next[target], next[index]]
  patchNavigation({ pageOrder: next.map(page => String(page.id)) })
}
</script>

<style scoped>
.settings-page-order {
  overflow: hidden;
  border: 1px solid var(--border-color, #e5e6eb);
  border-radius: 8px;
}

.settings-page-row {
  display: grid;
  min-height: 54px;
  grid-template-columns: 30px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  border-bottom: 1px solid var(--border-color, #e5e6eb);
  padding: 8px 12px;
}

.settings-page-row:last-child {
  border-bottom: 0;
}

.settings-page-index {
  display: grid;
  width: 24px;
  height: 24px;
  place-items: center;
  border-radius: 5px;
  background: var(--action-color, #f2f3f5);
  color: var(--text-color-3, #86909c);
  font-size: 12px;
}

.settings-page-row div {
  display: grid;
  min-width: 0;
  gap: 2px;
}

.settings-page-row small {
  color: var(--text-color-3, #86909c);
  font-size: 11px;
}
</style>
