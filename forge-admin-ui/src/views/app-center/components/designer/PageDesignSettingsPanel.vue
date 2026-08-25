<template>
  <div class="page-design-settings">
    <aside class="page-design-settings-nav">
      <button
        v-for="item in sections"
        :key="item.key"
        type="button"
        :class="{ active: activeSection === item.key }"
        @click="activeSection = item.key"
      >
        <n-icon><component :is="item.icon" /></n-icon>
        <span>{{ item.label }}</span>
      </button>
    </aside>
    <main class="page-design-settings-content">
      <section v-if="activeSection === 'basic'" class="page-design-settings-card">
        <header>
          <h2>基础信息</h2>
          <p>这些信息会显示在应用导航和工作台页面树上。</p>
        </header>
        <n-form label-placement="top">
          <n-form-item label="页面名称" required>
            <n-input
              :value="node.title"
              maxlength="40"
              show-count
              placeholder="请输入页面名称"
              @update:value="patch({ title: $event })"
            />
          </n-form-item>
          <n-form-item label="页面图标">
            <IconSelector :model-value="node.icon || ''" @update:model-value="patch({ icon: $event })" />
          </n-form-item>
        </n-form>
      </section>

      <section v-else-if="activeSection === 'display'" class="page-design-settings-card">
        <header>
          <h2>显示设置</h2>
          <p>控制这个页面是否出现在应用导航中。</p>
        </header>
        <n-form label-placement="top">
          <n-form-item label="在导航中显示">
            <div class="page-design-settings-switch">
              <n-switch :value="navigationVisible" @update:value="patch({ navigationVisible: $event })" />
              <span>{{ navigationVisible ? '运行时导航会展示此页面' : '运行时导航会隐藏此页面' }}</span>
            </div>
          </n-form-item>
        </n-form>
      </section>

      <section v-else class="page-design-settings-card">
        <header>
          <h2>页面信息</h2>
          <p>页面形态和绑定对象在创建时确定，可在这里核对。</p>
        </header>
        <dl class="page-design-settings-meta">
          <div>
            <dt>页面形态</dt>
            <dd>{{ pageShapeLabel }}</dd>
          </div>
          <div>
            <dt>页面类型</dt>
            <dd>{{ pageTypeLabel }}</dd>
          </div>
          <div>
            <dt>数据对象</dt>
            <dd>{{ objectLabel }}</dd>
          </div>
          <div>
            <dt>页面编码</dt>
            <dd>{{ node.id }}</dd>
          </div>
        </dl>
      </section>
    </main>
  </div>
</template>

<script setup>
import { ColorPaletteOutline, EyeOutline, InformationCircleOutline } from '@vicons/ionicons5'
import { computed, ref } from 'vue'
import IconSelector from '@/components/IconSelector.vue'
import { inAppPageTypes } from '../../in-app-builder/in-app-builder-schema'
import { PAGE_SHAPE_TYPES } from '../../in-app-builder/page-shape-design'

const props = defineProps({
  node: {
    type: Object,
    required: true,
  },
})

const emit = defineEmits(['update'])

const activeSection = ref('basic')
const sections = [
  { key: 'basic', label: '基础信息', icon: ColorPaletteOutline },
  { key: 'display', label: '显示设置', icon: EyeOutline },
  { key: 'info', label: '页面信息', icon: InformationCircleOutline },
]

const navigationVisible = computed(() => (props.node.navigationVisible ?? props.node.settings?.navigationVisible) !== false)

const pageShapeLabel = computed(() => {
  const value = props.node.pageTemplate || props.node.objectRef?.pageMode || ''
  return PAGE_SHAPE_TYPES.find(item => item.value === value)?.label
    || PAGE_SHAPE_TYPES.find(item => item.value === mapPageModeToShape(value))?.label
    || '未指定'
})

const pageTypeLabel = computed(() => {
  return inAppPageTypes.find(item => item.value === props.node.pageType)?.label || props.node.pageType || '未指定'
})

const objectLabel = computed(() => {
  const objectRef = props.node.objectRef
  if (!objectRef?.objectName && !objectRef?.objectCode)
    return '未绑定数据对象'
  return [objectRef.objectName, objectRef.objectCode].filter(Boolean).join(' · ')
})

function mapPageModeToShape(value) {
  if (value === 'crud')
    return 'list-form'
  return value
}

function patch(partial) {
  emit('update', partial)
}
</script>

<style scoped>
.page-design-settings {
  display: grid;
  grid-template-columns: 188px minmax(0, 1fr);
  gap: 20px;
  width: min(1120px, 100%);
  margin: 0 auto;
}

.page-design-settings-nav {
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-self: start;
  padding: 8px;
  border: 1px solid #e5e6eb;
  border-radius: 12px;
  background: #fff;
}

.page-design-settings-nav button {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 10px 12px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #4e5969;
  font-size: 13px;
  text-align: left;
  cursor: pointer;
}

.page-design-settings-nav button:hover {
  background: #f2f3f5;
  color: #1f2329;
}

.page-design-settings-nav button.active {
  background: rgba(22, 93, 255, 0.08);
  color: #165dff;
  font-weight: 600;
}

.page-design-settings-card {
  padding: 24px;
  border: 1px solid #e5e6eb;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 1px 3px rgb(31 35 41 / 6%);
}

.page-design-settings-card header {
  margin-bottom: 20px;
}

.page-design-settings-card h2 {
  margin: 0;
  color: #1d2129;
  font-size: 16px;
  font-weight: 650;
  line-height: 24px;
}

.page-design-settings-card p {
  margin: 6px 0 0;
  color: #86909c;
  font-size: 12px;
  line-height: 18px;
}

.page-design-settings-switch {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #4e5969;
  font-size: 13px;
}

.page-design-settings-meta {
  display: grid;
  gap: 16px;
  margin: 0;
}

.page-design-settings-meta div {
  display: grid;
  gap: 6px;
  padding-bottom: 16px;
  border-bottom: 1px solid #f2f3f5;
}

.page-design-settings-meta div:last-child {
  padding-bottom: 0;
  border-bottom: 0;
}

.page-design-settings-meta dt {
  color: #86909c;
  font-size: 12px;
}

.page-design-settings-meta dd {
  margin: 0;
  color: #1d2129;
  font-size: 14px;
  word-break: break-all;
}

@media (max-width: 768px) {
  .page-design-settings {
    grid-template-columns: 1fr;
  }

  .page-design-settings-nav {
    flex-direction: row;
    overflow-x: auto;
  }
}
</style>
