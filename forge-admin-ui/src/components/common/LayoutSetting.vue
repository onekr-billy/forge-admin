<template>
  <div>
    <n-tooltip trigger="hover" placement="left">
      <template #trigger>
        <button id="layout-setting" class="layout-setting-entry" type="button" @click="drawerVisible = true">
          <i class="layout-setting-icon i-lucide:panels-top-left" />
        </button>
      </template>
      布局与外观
    </n-tooltip>

    <n-drawer
      v-model:show="drawerVisible"
      placement="right"
      width="min(1040px, calc(100vw - 32px))"
      :trap-focus="false"
      class="appearance-drawer"
    >
      <n-drawer-content title="布局与外观" closable body-content-style="padding: 18px;">
        <div class="appearance-shell">
          <aside class="appearance-preview">
            <div class="preview-frame" :style="previewStyle">
              <div class="preview-header">
                <span class="preview-brand-dot" />
                <span class="preview-brand-line" />
                <div class="preview-top-menu">
                  <span />
                  <span class="is-active" />
                  <span />
                </div>
              </div>
              <div class="preview-body">
                <div class="preview-side">
                  <span />
                  <span class="is-active" />
                  <span />
                  <span />
                </div>
                <div class="preview-content">
                  <span class="preview-title" />
                  <span />
                  <span />
                  <span class="is-short" />
                </div>
              </div>
            </div>

            <div class="preview-meta">
              <strong>{{ activeLayout?.label || '当前布局' }}</strong>
              <span>{{ activeLayout?.description || '选择一个布局后立即预览。' }}</span>
            </div>

            <div class="preset-row">
              <button
                v-for="preset in colorPresets"
                :key="preset.name"
                class="preset-button"
                type="button"
                :title="preset.name"
                :style="{ '--preset-color': preset.color }"
                @click="applyPrimaryColor(preset.color)"
              />
            </div>
          </aside>

          <section class="appearance-panel">
            <n-tabs type="segment" animated>
              <n-tab-pane name="layout" tab="布局">
                <div class="layout-grid">
                  <button
                    v-for="item in layoutOptions"
                    :key="item.value"
                    class="layout-card"
                    :class="{ 'is-active': appStore.layout === item.value }"
                    type="button"
                    @click="appStore.setLayout(item.value)"
                  >
                    <div class="layout-mini" :class="`layout-mini--${item.preview}`">
                      <span class="layout-mini__side" />
                      <span class="layout-mini__top" />
                      <span class="layout-mini__content" />
                    </div>
                    <span class="layout-card__title">{{ item.label }}</span>
                    <small>{{ item.description }}</small>
                  </button>
                </div>
              </n-tab-pane>

              <n-tab-pane name="theme" tab="主题">
                <div class="setting-section">
                  <div class="setting-section__head">
                    <strong>基础外观</strong>
                    <span>控制主题主色、顶栏底色和通用文字反差。</span>
                  </div>
                  <div class="setting-grid">
                    <label class="setting-field">
                      <span>主题色</span>
                      <n-color-picker
                        :value="themeValue('primaryColor')"
                        :modes="['hex']"
                        @update:value="applyPrimaryColor"
                      />
                    </label>
                    <label class="setting-field">
                      <span>Header 背景</span>
                      <n-color-picker
                        :value="themeValue('header.backgroundColor')"
                        :modes="['hex']"
                        @update:value="value => updateThemeSection('header', { backgroundColor: value })"
                      />
                    </label>
                    <label class="setting-field">
                      <span>Header 文字</span>
                      <n-color-picker
                        :value="themeValue('header.textColor')"
                        :modes="['hex']"
                        @update:value="value => updateThemeSection('header', { textColor: value })"
                      />
                    </label>
                  </div>
                </div>

                <div class="setting-actions">
                  <n-button secondary @click="resetTheme">
                    恢复默认主题
                  </n-button>
                </div>
              </n-tab-pane>

              <n-tab-pane name="navigation" tab="导航色彩">
                <div class="setting-section">
                  <div class="setting-section__head">
                    <strong>左侧菜单</strong>
                    <span>影响简约、通用、全屏和顶部+侧面菜单的左侧导航。</span>
                  </div>
                  <div class="setting-grid">
                    <label class="setting-field">
                      <span>菜单背景</span>
                      <n-color-picker
                        :value="themeValue('sideMenu.backgroundColor')"
                        :modes="['hex']"
                        @update:value="value => updateThemeSection('sideMenu', { backgroundColor: value })"
                      />
                    </label>
                    <label class="setting-field">
                      <span>默认文字</span>
                      <n-color-picker
                        :value="themeValue('sideMenu.textColor')"
                        :modes="['hex']"
                        @update:value="value => updateThemeSection('sideMenu', { textColor: value })"
                      />
                    </label>
                    <label class="setting-field">
                      <span>选中文字</span>
                      <n-color-picker
                        :value="themeValue('sideMenu.textColorActive')"
                        :modes="['hex']"
                        @update:value="value => updateThemeSection('sideMenu', { textColorActive: value })"
                      />
                    </label>
                    <label class="setting-field">
                      <span>选中背景</span>
                      <n-color-picker
                        :value="themeValue('sideMenu.backgroundColorActive')"
                        :modes="['hex']"
                        @update:value="value => updateThemeSection('sideMenu', { backgroundColorActive: value })"
                      />
                    </label>
                    <label class="setting-field">
                      <span>父级提示文字</span>
                      <n-color-picker
                        :value="themeValue('sideMenu.parentTextColorActive')"
                        :modes="['hex']"
                        @update:value="value => updateThemeSection('sideMenu', { parentTextColorActive: value })"
                      />
                    </label>
                    <label class="setting-field">
                      <span>父级提示背景</span>
                      <n-color-picker
                        :value="themeValue('sideMenu.parentBackgroundColorActive')"
                        :modes="['hex']"
                        @update:value="value => updateThemeSection('sideMenu', { parentBackgroundColorActive: value })"
                      />
                    </label>
                    <label class="setting-field">
                      <span>悬停文字</span>
                      <n-color-picker
                        :value="themeValue('sideMenu.textColorHover')"
                        :modes="['hex']"
                        @update:value="value => updateThemeSection('sideMenu', { textColorHover: value })"
                      />
                    </label>
                    <label class="setting-field">
                      <span>悬停背景</span>
                      <n-color-picker
                        :value="themeValue('sideMenu.backgroundColorHover')"
                        :modes="['hex']"
                        @update:value="value => updateThemeSection('sideMenu', { backgroundColorHover: value })"
                      />
                    </label>
                  </div>
                </div>

                <div class="setting-section">
                  <div class="setting-section__head">
                    <strong>顶部菜单</strong>
                    <span>影响顶部菜单和顶部+侧面菜单的一级导航。</span>
                  </div>
                  <div class="setting-grid">
                    <label class="setting-field">
                      <span>默认文字</span>
                      <n-color-picker
                        :value="themeValue('topMenu.textColor')"
                        :modes="['hex']"
                        @update:value="value => updateThemeSection('topMenu', { textColor: value })"
                      />
                    </label>
                    <label class="setting-field">
                      <span>选中文字</span>
                      <n-color-picker
                        :value="themeValue('topMenu.textColorActive')"
                        :modes="['hex']"
                        @update:value="value => updateThemeSection('topMenu', { textColorActive: value })"
                      />
                    </label>
                  </div>
                </div>
              </n-tab-pane>
            </n-tabs>

            <p class="setting-note">
              此处用于即时预览和个人会话配置；租户管理中的外观配置会作为租户默认主题保存。
            </p>
          </section>
        </div>
      </n-drawer-content>
    </n-drawer>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { defaultThemeConfig } from '@/config/theme.config'
import { useAppStore } from '@/store'

const appStore = useAppStore()
const drawerVisible = ref(false)

const layoutOptions = [
  { label: '业务工作台', value: 'business-workbench', preview: 'top', description: '顶部导航与分组菜单，适合多模块业务后台。' },
  { label: '简约', value: 'simple', preview: 'side', description: '侧栏更轻，适合常规后台。' },
  { label: '通用', value: 'normal', preview: 'side-top', description: '顶部工具栏 + 左侧菜单。' },
  { label: '顶部菜单', value: 'top-menu', preview: 'top', description: '一级导航集中在顶部。' },
  { label: '顶部+侧面', value: 'top-side-menu', preview: 'top-side', description: '顶部一级，侧边承载子菜单。' },
  { label: '全屏', value: 'full', preview: 'side', description: '内容区更铺开。' },
  { label: '沉浸式', value: 'immersive', preview: 'top', description: '抽屉菜单，弱化导航占位。' },
  { label: '便当盒', value: 'bento', preview: 'rail', description: '窄栏图标导航。' },
  { label: 'Nexus 浮岛', value: 'nexus', preview: 'floating', description: '浮岛式侧栏。' },
  { label: '空白', value: 'empty', preview: 'empty', description: '无框架承载特殊页面。' },
]

const colorPresets = [
  { name: 'Forge 蓝', color: '#4242F7' },
  { name: '企业红', color: '#d12723' },
  { name: '松石绿', color: '#0f9f8f' },
  { name: '靛青', color: '#2563eb' },
  { name: '石墨', color: '#334155' },
]

const activeLayout = computed(() => layoutOptions.find(item => item.value === appStore.layout))

const previewStyle = computed(() => ({
  '--preview-primary': themeValue('primaryColor'),
  '--preview-header': themeValue('header.backgroundColor'),
  '--preview-header-text': themeValue('header.textColor'),
  '--preview-side': themeValue('sideMenu.backgroundColor'),
  '--preview-side-text': themeValue('sideMenu.textColor'),
  '--preview-side-active': themeValue('sideMenu.backgroundColorActive'),
  '--preview-side-active-text': themeValue('sideMenu.textColorActive'),
  '--preview-top-active': themeValue('topMenu.textColorActive'),
}))

function cloneConfig(config) {
  return JSON.parse(JSON.stringify(config))
}

function themeValue(path) {
  return path.split('.').reduce((target, key) => target?.[key], appStore.themeConfig) || path.split('.').reduce((target, key) => target?.[key], defaultThemeConfig)
}

function updateThemeSection(section, patch) {
  appStore.setThemeConfig({
    ...cloneConfig(appStore.themeConfig || defaultThemeConfig),
    [section]: {
      ...(appStore.themeConfig?.[section] || defaultThemeConfig[section]),
      ...patch,
    },
  })
}

function applyPrimaryColor(color) {
  if (!color)
    return
  appStore.setThemeConfig({
    ...cloneConfig(appStore.themeConfig || defaultThemeConfig),
    primaryColor: color,
    header: {
      ...(appStore.themeConfig?.header || defaultThemeConfig.header),
      backgroundColor: color,
    },
  })
}

function resetTheme() {
  appStore.setThemeConfig(cloneConfig(defaultThemeConfig))
  window.$message?.success('已恢复默认主题')
}
</script>

<style scoped>
.layout-setting-entry {
  width: 36px;
  height: 36px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 8px;
  color: #fff;
  background: var(--primary-gradient);
  box-shadow: var(--button-primary-shadow);
  cursor: pointer;
}

.layout-setting-icon {
  display: inline-block;
  width: 20px;
  height: 20px;
  transition: color var(--transition-fast);
}

.appearance-shell {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 18px;
}

:deep(.appearance-drawer .n-drawer-body-content-wrapper) {
  overflow-x: hidden;
}

.appearance-preview {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 14px;
  border: 1px solid var(--border-light);
  border-radius: 8px;
  background: var(--bg-secondary);
}

.preview-frame {
  overflow: hidden;
  height: 176px;
  border: 1px solid var(--border-default);
  border-radius: 8px;
  background: #fff;
  box-shadow: var(--shadow-sm);
}

.preview-header {
  height: 38px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 10px;
  color: var(--preview-header-text);
  background: var(--preview-header);
}

.preview-brand-dot {
  width: 14px;
  height: 14px;
  border-radius: 4px;
  background: currentColor;
  opacity: 0.9;
}

.preview-brand-line {
  width: 54px;
  height: 6px;
  border-radius: 999px;
  background: currentColor;
  opacity: 0.82;
}

.preview-top-menu {
  display: flex;
  gap: 8px;
  margin-left: auto;
}

.preview-top-menu span {
  width: 26px;
  height: 5px;
  border-radius: 999px;
  background: currentColor;
  opacity: 0.5;
}

.preview-top-menu span.is-active {
  width: 38px;
  opacity: 1;
  box-shadow: 0 8px 0 -6px var(--preview-top-active);
}

.preview-body {
  height: calc(100% - 38px);
  display: flex;
}

.preview-side {
  width: 72px;
  display: flex;
  flex-direction: column;
  gap: 9px;
  padding: 12px 8px;
  background: var(--preview-side);
}

.preview-side span {
  height: 9px;
  border-radius: 999px;
  background: var(--preview-side-text);
  opacity: 0.42;
}

.preview-side span.is-active {
  height: 18px;
  background: var(--preview-side-active);
  box-shadow: inset 3px 0 0 var(--preview-side-active-text);
  opacity: 1;
}

.preview-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px;
  background: linear-gradient(180deg, #f8fafc, #eef2f7);
}

.preview-content span {
  height: 8px;
  border-radius: 999px;
  background: #cbd5e1;
}

.preview-content .preview-title {
  width: 48%;
  height: 14px;
  background: var(--preview-primary);
}

.preview-content .is-short {
  width: 66%;
}

.preview-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.preview-meta strong {
  color: var(--text-primary);
  font-size: 15px;
}

.preview-meta span,
.setting-section__head span,
.setting-note,
.layout-card small {
  color: var(--text-tertiary);
  font-size: 12px;
  line-height: 1.5;
}

.preset-row {
  display: flex;
  gap: 8px;
}

.preset-button {
  width: 26px;
  height: 26px;
  border: 2px solid #fff;
  border-radius: 999px;
  background: var(--preset-color);
  box-shadow: 0 0 0 1px var(--border-default);
  cursor: pointer;
}

.appearance-panel {
  min-width: 0;
}

.layout-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.layout-card {
  min-height: 128px;
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 8px;
  padding: 12px;
  border: 1px solid var(--border-light);
  border-radius: 8px;
  background: var(--bg-primary);
  text-align: left;
  cursor: pointer;
  transition:
    border-color var(--transition-fast),
    box-shadow var(--transition-fast),
    transform var(--transition-fast);
}

.layout-card:hover,
.layout-card.is-active {
  border-color: color-mix(in srgb, var(--primary-color) 42%, var(--border-light));
  box-shadow: 0 8px 20px color-mix(in srgb, var(--primary-color) 12%, transparent);
  transform: translateY(-1px);
}

.layout-card__title {
  color: var(--text-primary);
  font-weight: 600;
}

.layout-mini {
  position: relative;
  height: 54px;
  overflow: hidden;
  border-radius: 6px;
  background: #eef2f7;
}

.layout-mini__side,
.layout-mini__top,
.layout-mini__content {
  position: absolute;
  border-radius: 4px;
  background: #cbd5e1;
}

.layout-mini__side {
  left: 6px;
  top: 6px;
  bottom: 6px;
  width: 16px;
  background: var(--side-menu-bg-color);
}

.layout-mini__top {
  left: 28px;
  right: 6px;
  top: 6px;
  height: 9px;
  background: var(--layout-header-bg-color);
}

.layout-mini__content {
  left: 28px;
  right: 6px;
  top: 20px;
  bottom: 6px;
}

.layout-mini--top .layout-mini__side,
.layout-mini--empty .layout-mini__side {
  display: none;
}

.layout-mini--top .layout-mini__top,
.layout-mini--empty .layout-mini__content {
  left: 6px;
}

.layout-mini--top .layout-mini__content {
  left: 6px;
}

.layout-mini--rail .layout-mini__side {
  width: 10px;
}

.layout-mini--rail .layout-mini__top,
.layout-mini--rail .layout-mini__content {
  left: 22px;
}

.layout-mini--floating .layout-mini__side {
  top: 10px;
  bottom: 10px;
  border-radius: 999px;
}

.setting-section {
  padding: 14px 0;
  border-bottom: 1px solid var(--border-light);
}

.setting-section:first-child {
  padding-top: 4px;
}

.setting-section__head {
  display: flex;
  flex-direction: column;
  gap: 3px;
  margin-bottom: 12px;
}

.setting-section__head strong {
  color: var(--text-primary);
}

.setting-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.setting-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 600;
}

.setting-actions {
  display: flex;
  justify-content: flex-end;
  padding-top: 12px;
}

.setting-note {
  margin: 14px 0 0;
}

@media (max-width: 860px) {
  .appearance-shell {
    grid-template-columns: 1fr;
  }

  .layout-grid,
  .setting-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 560px) {
  .layout-grid,
  .setting-grid {
    grid-template-columns: 1fr;
  }
}
</style>
