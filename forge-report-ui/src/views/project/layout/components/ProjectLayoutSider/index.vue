<template>
  <n-layout-sider
    class="go-project-sider"
    collapse-mode="width"
    show-trigger="bar"
    :collapsed="collapsed"
    :native-scrollbar="false"
    :collapsed-width="getAsideCollapsedWidth"
    :width="asideWidth"
    @collapse="collapsed = true"
    @expand="collapsed = false"
  >
    <div class="sider-inner" :class="{ collapsed }">
      <div class="sider-brand" :class="{ collapsed }">
        <div class="brand-content">
          <span class="brand-icon">&#9670;</span>
          <span v-show="!collapsed" class="brand-text">FORGE</span>
          <span v-show="!collapsed" class="brand-cursor">_</span>
        </div>
      </div>

      <div class="sider-create" :class="{ collapsed }">
        <project-layout-create :collapsed="collapsed"></project-layout-create>
      </div>

      <div class="sider-nav">
        <div v-for="section in navSections" :key="section.key" class="nav-section">
          <div v-if="section.label && !collapsed" class="nav-label">{{ section.label }}</div>
          <div class="nav-items">
            <router-link
              v-for="item in section.children"
              :key="item.key"
              :to="item.to"
              class="nav-item"
              :class="{ active: routeName === item.key, collapsed }"
              :title="collapsed ? item.text : ''"
            >
              <span v-show="!collapsed" class="nav-bullet"></span>
              <n-icon size="18" class="nav-icon"><component :is="item.icon"></component></n-icon>
              <span v-show="!collapsed" class="nav-text">{{ item.text }}</span>
              <span v-show="!collapsed && routeName === item.key" class="nav-active-bar"></span>
            </router-link>
          </div>
        </div>
      </div>

      <div class="sider-toolbar" :class="{ collapsed }">
        <div class="sider-user" :class="{ collapsed }">
          <fg-user-info></fg-user-info>
        </div>

        <div class="tool-divider" v-show="!collapsed"></div>

        <div class="tool-buttons" :class="{ collapsed }">
          <n-tooltip placement="right" trigger="hover">
            <template #trigger>
              <div class="tool-btn" @click="toggleTheme">
                <n-icon size="17"><MoonIcon v-if="designStore.darkTheme" /><SunnyIcon v-else /></n-icon>
              </div>
            </template>
            <span>{{ designStore.darkTheme ? '暗色模式' : '亮色模式' }}</span>
          </n-tooltip>
          <n-tooltip placement="right" trigger="hover">
            <template #trigger>
              <n-dropdown trigger="click" :options="langOptions" @select="handleLangSelect" :show-arrow="true">
                <div class="tool-btn"><n-icon size="17"><LanguageIcon /></n-icon></div>
              </n-dropdown>
            </template>
            <span>语言切换</span>
          </n-tooltip>
          <n-tooltip placement="right" trigger="hover">
            <template #trigger>
              <div class="tool-btn-color"><theme-color-select></theme-color-select></div>
            </template>
            <span>主题颜色</span>
          </n-tooltip>
        </div>

        <div class="tool-divider" v-show="!collapsed"></div>

        <div class="tool-status" :class="{ collapsed }">
          <span class="status-dot"></span>
          <span v-show="!collapsed" class="status-text">SYSTEM ONLINE</span>
        </div>
      </div>
    </div>
  </n-layout-sider>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, toRefs } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ProjectLayoutCreate } from '../ProjectLayoutCreate/index'
import { asideWidth } from '@/settings/designSetting'
import { useSettingStore } from '@/store/modules/settingStore/settingStore'
import { useDesignStore } from '@/store/modules/designStore/designStore'
import { useLangStore } from '@/store/modules/langStore/langStore'
import { ThemeColorSelect } from '@/components/Pages/ThemeColorSelect'
import { FgUserInfo } from '@/components/FgUserInfo'
import { setHtmlTheme } from '@/utils'
import { PageEnum } from '@/enums/pageEnum'
import { LangEnum } from '@/enums/styleEnum'
import { langList } from '@/i18n/index'
import { icon } from '@/plugins'

const { MoonIcon, SunnyIcon, LanguageIcon, TvOutlineIcon, SparklesIcon, ImagesIcon,FolderOpenIcon } = icon.ionicons5
const { StoreIcon, ObjectStorageIcon } = icon.carbon

const { locale } = useI18n()
const route = useRoute()
const designStore = useDesignStore()
const langStore = useLangStore()
const { getAsideCollapsedWidth } = toRefs(useSettingStore())
const collapsed = ref<boolean>(false)
const t = window['$t']
const routeName = computed(() => route.name)

const navSections = computed(() => [
  {
    key: 'main',
    label: t('project.my'),
    children: [
      { key: PageEnum.BASE_HOME_ITEMS_NAME, icon: TvOutlineIcon, text: t('project.all_project'), to: { name: PageEnum.BASE_HOME_ITEMS_NAME } },
      { key: PageEnum.BASE_HOME_TEMPLATE_NAME, icon: ObjectStorageIcon, text: t('project.my_template'), to: { name: PageEnum.BASE_HOME_TEMPLATE_NAME } },
      { key: PageEnum.BASE_HOME_MATERIALS_NAME, icon: ImagesIcon, text: '素材库', to: { name: PageEnum.BASE_HOME_MATERIALS_NAME } },
      { key: PageEnum.BASE_HOME_AI_PROVIDER_NAME, icon: SparklesIcon, text: 'AI 供应商', to: { name: PageEnum.BASE_HOME_AI_PROVIDER_NAME } },
      { key: PageEnum.BASE_HOME_DIRECTORY_NAME, icon: FolderOpenIcon, text: t('project.directory_manage'), to: { name: PageEnum.BASE_HOME_DIRECTORY_NAME } },
    ]
  },
  {
    key: 'market',
    label: t('project.template_market'),
    children: [
      { key: PageEnum.BASE_HOME_TEMPLATE_MARKET_NAME, icon: StoreIcon, text: t('project.template_market'), to: { name: PageEnum.BASE_HOME_TEMPLATE_MARKET_NAME } }
    ]
  }
])

const langOptions = langList.map((item: any) => ({ label: item.label, key: item.key }))

const handleLangSelect = (key: LangEnum) => { locale.value = key; langStore.changeLang(key) }
const toggleTheme = () => { designStore.changeTheme(); setHtmlTheme() }

const watchWidth = () => { collapsed.value = document.body.clientWidth <= 950 }

onMounted(() => { window.addEventListener('resize', watchWidth); watchWidth() })
onUnmounted(() => { window.removeEventListener('resize', watchWidth) })
</script>

<style lang="scss" scoped>
$siderHeight: 100vh;

@include go(project) {
  &-sider {
    @include fetch-bg-color('background-color1');
    position: relative;
    border-right: 1px solid rgba(var(--app-theme-rgb), 0.06);
    &::after {
      content: '';
      position: absolute;
      top: 0;
      right: 0;
      width: 1px;
      height: 100%;
      background: linear-gradient(180deg, transparent 5%, rgba(var(--app-theme-rgb), 0.25) 20%, rgba(167, 139, 250, 0.15) 50%, rgba(var(--app-theme-rgb), 0.1) 80%, transparent 95%);
      pointer-events: none;
    }
    @include deep() {
      .n-layout-sider-scroll-container { overflow: hidden; }
      .n-layout-toggle-bar {
        background: rgba(var(--app-theme-rgb), 0.06);
        border-left: 1px solid rgba(var(--app-theme-rgb), 0.15);
        &:hover { background: rgba(var(--app-theme-rgb), 0.12); }
        .n-layout-toggle-bar__top,
        .n-layout-toggle-bar__bottom {
          background: rgba(var(--app-theme-rgb), 0.4);
          box-shadow: 0 0 6px rgba(var(--app-theme-rgb), 0.3);
        }
      }
      .n-base-icon { color: $--color-primary; }
    }
  }
}

.sider-inner {
  display: flex;
  flex-direction: column;
  height: $siderHeight;
  overflow: hidden;
}

.sider-brand {
  padding: 20px 16px 12px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.03);
  &.collapsed { padding: 20px 0 12px; text-align: center; }
  .brand-content { display: flex; align-items: center; gap: 6px; padding-left: 4px; }
  &.collapsed .brand-content { justify-content: center; padding-left: 0; }
  .brand-icon { font-size: 18px; color: $--color-primary; text-shadow: 0 0 10px rgba(var(--app-theme-rgb), 0.5); flex-shrink: 0; }
  .brand-text { font-size: 15px; font-weight: 800; letter-spacing: 3px; @include fetch-color(); text-shadow: 0 0 10px rgba(var(--app-theme-rgb), 0.25); }
  .brand-cursor { color: $--color-primary; font-size: 13px; font-weight: 300; animation: cursorBlink 1s step-end infinite; }
}

@keyframes cursorBlink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

.sider-create {
  padding: 10px 12px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.03);
  &.collapsed { padding: 10px 4px; display: flex; justify-content: center; }
}

.sider-nav {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 6px 0;
  .nav-section { margin-bottom: 2px; }
  .nav-label {
    padding: 8px 16px 2px;
    font-size: 10px;
    text-transform: uppercase;
    letter-spacing: 2px;
    @include fetch-color(3);
    font-weight: 700;
  }
  .nav-items { padding: 0 8px; }
  .nav-item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px 10px;
    border-radius: $--border-radius-sm;
    cursor: pointer;
    text-decoration: none;
    position: relative;
    margin: 1px 0;
    transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
    &.collapsed { justify-content: center; padding: 10px 0; }
    &:hover {
      background: rgba(var(--app-theme-rgb), 0.04);
      .nav-bullet { background: $--color-primary; box-shadow: 0 0 6px rgba(var(--app-theme-rgb), 0.5); }
      .nav-icon { color: $--color-primary; }
      .nav-text { @include fetch-color(); }
    }
    &.active {
      background: rgba(var(--app-theme-rgb), 0.06);
      .nav-bullet { opacity: 1; background: $--color-primary; box-shadow: 0 0 8px rgba(var(--app-theme-rgb), 0.6); }
      .nav-icon { color: $--color-primary; }
      .nav-text { color: $--color-primary; font-weight: 600; }
      .nav-active-bar {
        position: absolute;
        left: 0;
        top: 50%;
        transform: translateY(-50%);
        width: 3px;
        height: 16px;
        background: $--color-primary;
        border-radius: 0 2px 2px 0;
        box-shadow: 0 0 8px rgba(var(--app-theme-rgb), 0.5), 0 0 14px rgba(var(--app-theme-rgb), 0.2);
      }
    }
    .nav-bullet { width: 4px; height: 4px; border-radius: 50%; @include fetch-bg-color('background-color4'); opacity: 0.4; flex-shrink: 0; transition: all 0.2s ease; }
    .nav-icon { @include fetch-color(2); flex-shrink: 0; transition: all 0.2s ease; }
    .nav-text { font-size: 13px; @include fetch-color(2); white-space: nowrap; letter-spacing: 0.5px; transition: all 0.2s ease; }
  }
}

.sider-toolbar {
  flex-shrink: 0;
  padding: 0 8px 12px;
  &.collapsed { padding: 0 4px 12px; }
  .sider-user {
    display: flex;
    justify-content: center;
    padding: 8px 0 4px;
    &.collapsed { padding: 4px 0 2px; }
    @include deep() {
      .user-info-box { transform: scale(0.85); cursor: pointer; }
    }
  }
  .tool-divider {
    height: 1px;
    margin-bottom: 10px;
    background: linear-gradient(90deg, transparent, rgba(var(--app-theme-rgb), 0.12), transparent);
  }
  .tool-buttons {
    display: flex;
    justify-content: center;
    gap: 2px;
    &.collapsed { flex-direction: column; align-items: center; gap: 4px; }
    .tool-btn {
      width: 30px;
      height: 30px;
      display: flex;
      align-items: center;
      justify-content: center;
      border-radius: $--border-radius-sm;
      cursor: pointer;
      @include fetch-color(3);
      transition: all 0.2s ease;
      &:hover { color: $--color-primary; background: rgba(var(--app-theme-rgb), 0.08); }
    }
    .tool-btn-color {
      @include deep() {
        .n-button {
          width: 30px !important;
          height: 30px !important;
          padding: 0 !important;
          background: transparent !important;
          border: none !important;
    @include fetch-color(3);
          &:hover { color: $--color-primary; background: rgba(var(--app-theme-rgb), 0.08) !important; }
        }
      }
    }
  }
  .tool-status {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 6px;
    padding: 6px 0 2px;
    &.collapsed { padding: 4px 0 2px; }
    .status-dot {
      width: 5px;
      height: 5px;
      border-radius: 50%;
      background: $--color-success;
      box-shadow: 0 0 6px rgba(46, 213, 115, 0.6);
      animation: statusPulse 2s ease-in-out infinite;
    }
    .status-text { font-size: 9px; letter-spacing: 2px; @include fetch-color(4); font-family: 'Courier New', monospace; }
  }
}

@keyframes statusPulse {
  0%, 100% { box-shadow: 0 0 6px rgba(46, 213, 115, 0.6); }
  50% { box-shadow: 0 0 12px rgba(46, 213, 115, 0.9); }
}
</style>
