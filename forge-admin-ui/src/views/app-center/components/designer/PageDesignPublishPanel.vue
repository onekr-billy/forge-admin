<template>
  <div class="page-design-publish">
    <section class="page-design-publish-card">
      <header>
        <div>
          <h2>页面访问</h2>
          <p>保存后可预览草稿；电脑端和移动端正式地址要等应用发布后才会按当前页面生效。</p>
        </div>
        <n-space>
          <n-button secondary :loading="saving" :disabled="!dirty" @click="emit('save')">
            保存当前页面
          </n-button>
          <n-button secondary :disabled="!pageId" @click="emit('preview')">
            预览页面
          </n-button>
        </n-space>
      </header>
      <dl class="page-design-publish-meta">
        <div>
          <dt>当前页面</dt>
          <dd>{{ pageTitle || '未命名页面' }}</dd>
        </div>
        <div>
          <dt>页面编码</dt>
          <dd>{{ pageId || '-' }}</dd>
        </div>
        <div>
          <dt>草稿状态</dt>
          <dd>{{ dirty ? '有未保存修改' : '已保存到草稿' }}</dd>
        </div>
        <div>
          <dt>正式发布</dt>
          <dd>{{ published ? `已发布 v${application.lastPublishVersion}` : '尚未发布，正式地址暂不可用' }}</dd>
        </div>
      </dl>
    </section>

    <!-- 挂载位置配置 -->
    <section class="page-design-publish-card">
      <header>
        <div>
          <h2>挂载位置</h2>
          <p>选择此页面在菜单中出现的位置，应用发布后生效。</p>
        </div>
      </header>
      <div class="mount-config">
        <n-form label-placement="left" label-width="92" size="small">
          <n-form-item label="挂载位置">
            <n-radio-group :value="currentMountTarget" size="small" @update:value="onMountTargetChange">
              <n-radio-button value="ADMIN">
                管理端
              </n-radio-button>
              <n-radio-button value="MOBILE">
                移动端
              </n-radio-button>
              <n-radio-button value="BOTH">
                两端同时
              </n-radio-button>
            </n-radio-group>
          </n-form-item>
          <n-form-item label="菜单名称">
            <n-input
              :value="currentMenuName"
              placeholder="留空则使用页面名称"
              size="small"
              @update:value="onMenuNameChange"
            />
          </n-form-item>
          <n-form-item v-if="showAdminMenuConfig" label="管理端父级">
            <MenuParentSelect
              :value="currentMenuParentId"
              client-code="pc"
              @update:value="onMenuParentIdChange"
            />
          </n-form-item>
          <n-form-item v-if="showMobileMenuConfig" label="移动端父级">
            <MenuParentSelect
              :value="currentMobileMenuParentId"
              client-code="h5"
              placeholder="请选择移动端菜单目录"
              @update:value="onMobileMenuParentIdChange"
            />
          </n-form-item>
          <n-form-item v-if="showAdminMenuConfig || showMobileMenuConfig" label="菜单排序">
            <n-input-number
              :value="currentMenuSort"
              :min="0"
              size="small"
              style="width: 100%;"
              @update:value="onMenuSortChange"
            />
          </n-form-item>
        </n-form>
      </div>
    </section>

    <AppPublishAccess :application="application" :page-id="pageId" :config-key="configKey" :objects="objects" />

    <!-- 表单填报直链 -->
    <section v-if="formFillLinks.length" class="page-design-publish-card">
      <header>
        <div>
          <h2>表单填报直链</h2>
          <p>可直接分享此链接，打开后进入表单填报模式。</p>
        </div>
      </header>
      <div class="form-fill-links">
        <div v-for="link in formFillLinks" :key="link.label" class="form-fill-link-row">
          <span class="link-label">{{ link.label }}</span>
          <n-input-group>
            <n-input :value="link.url" readonly size="small" />
            <n-button size="small" secondary @click="copyLink(link.url, link.label)">
              复制
            </n-button>
          </n-input-group>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { useMessage } from 'naive-ui'
import { computed } from 'vue'
import MenuParentSelect from '@/components/lowcode-builder/shared/MenuParentSelect.vue'
import AppPublishAccess from '../publish/AppPublishAccess.vue'

const props = defineProps({
  application: { type: Object, required: true },
  node: { type: Object, default: null },
  pageId: { type: String, default: '' },
  pageTitle: { type: String, default: '' },
  dirty: { type: Boolean, default: false },
  saving: { type: Boolean, default: false },
  configKey: { type: String, default: '' },
  objects: { type: Array, default: () => [] },
})

const emit = defineEmits(['save', 'preview', 'update'])
const message = useMessage()

const published = computed(() => Boolean(props.application?.lastPublishVersion) && Number(props.application?.status) === 1)

const currentMountTarget = computed(() => {
  const node = props.node
  if (!node) {
    return 'ADMIN'
  }
  return (node.mountTarget ?? node.settings?.mountTarget ?? 'ADMIN').toUpperCase()
})

const currentMenuName = computed(() => {
  const node = props.node
  return node?.menuName ?? node?.settings?.menuName ?? ''
})

const showAdminMenuConfig = computed(() => {
  const mt = currentMountTarget.value
  return mt === 'ADMIN' || mt === 'BOTH'
})
const showMobileMenuConfig = computed(() => {
  const mt = currentMountTarget.value
  return mt === 'MOBILE' || mt === 'BOTH'
})

const currentMenuParentId = computed(() => {
  const node = props.node
  const value = node?.menuParentId ?? node?.settings?.menuParentId
  return value != null ? String(value) : null
})

const currentMobileMenuParentId = computed(() => {
  const node = props.node
  const value = node?.mobileMenuParentId ?? node?.settings?.mobileMenuParentId
  return value != null ? String(value) : null
})

const currentMenuSort = computed(() => {
  const node = props.node
  const value = node?.menuSort ?? node?.settings?.menuSort
  return typeof value === 'number' ? value : Number(value) || 0
})

const formFillLinks = computed(() => {
  const origin = window.location.origin
  const basePath = String(import.meta.env.BASE_URL || '').replace(/\/$/, '')
  const slug = props.application?.portalSlug || props.application?.applicationCode
  const appId = props.application?.id
  const mt = currentMountTarget.value
  const links = []

  if (mt === 'ADMIN' || mt === 'BOTH') {
    if (props.pageId && slug) {
      links.push({
        label: '管理端 · 表单填报',
        url: `${origin}${basePath}/app/${encodeURIComponent(slug)}?pageId=${encodeURIComponent(props.pageId)}&runtimeOpenMode=CREATE_FORM&mode=create`,
      })
    }
    if (props.configKey) {
      links.push({
        label: '低代码 · 表单填报',
        url: `${origin}${basePath}/ai/crud-page/${props.configKey}?appId=${appId}&runtimeOpenMode=CREATE_FORM&mode=create`,
      })
    }
  }

  if ((mt === 'MOBILE' || mt === 'BOTH') && props.configKey) {
    const h5Base = props.application?.portalConfig?.distribution?.h5BaseUrl || 'http://localhost:3009'
    links.push({
      label: '移动端 · 表单填报',
      url: `${h5Base.replace(/\/$/, '')}/#/pages/lowcode-runtime?configKey=${props.configKey}&appId=${appId}&mode=create`,
    })
  }

  return links
})

function onMountTargetChange(value) {
  // Selecting a client target is itself an explicit request to expose the
  // page in that client's menu.  Keep the visibility flag in the same patch;
  // otherwise the server quite correctly filters the node out at publish.
  emit('update', { mountTarget: value, systemMenuVisible: true })
}

function onMenuNameChange(value) {
  emit('update', { menuName: value })
}

function onMenuParentIdChange(value) {
  // 选择管理端父级就是一次明确的菜单挂载操作。若只保存父级 ID 而
  // 保留旧的 systemMenuVisible=false，发布时页面会被过滤掉并下线历史菜单。
  emit('update', { menuParentId: value, systemMenuVisible: true })
}

function onMobileMenuParentIdChange(value) {
  // 移动端使用独立的父级资源树，同样需要显式打开菜单挂载标记。
  emit('update', { mobileMenuParentId: value, systemMenuVisible: true })
}

function onMenuSortChange(value) {
  emit('update', { menuSort: value })
}

async function copyLink(url, label) {
  try {
    await navigator.clipboard.writeText(url)
    message.success(`${label}链接已复制`)
  }
  catch {
    message.error('复制失败，请手动复制')
  }
}
</script>

<style scoped>
.page-design-publish {
  display: grid;
  gap: 16px;
  width: min(1120px, 100%);
  margin: 0 auto;
}

.page-design-publish-card {
  padding: 24px;
  border: 1px solid #e5e6eb;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 1px 3px rgb(31 35 41 / 6%);
}

.page-design-publish-card header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
}

.page-design-publish-card h2 {
  margin: 0;
  color: #1d2129;
  font-size: 16px;
  font-weight: 650;
}

.page-design-publish-card p {
  margin: 6px 0 0;
  color: #86909c;
  font-size: 12px;
  line-height: 18px;
}

.page-design-publish-meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  margin: 0;
}

.page-design-publish-meta div {
  display: grid;
  gap: 6px;
}

.page-design-publish-meta dt {
  color: #86909c;
  font-size: 12px;
}

.page-design-publish-meta dd {
  margin: 0;
  color: #1d2129;
  font-size: 14px;
  word-break: break-all;
}

.mount-config {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}

.form-fill-links {
  display: grid;
  gap: 10px;
}

.form-fill-link-row {
  display: grid;
  grid-template-columns: 140px minmax(0, 1fr);
  gap: 10px;
  align-items: center;
}

.link-label {
  color: #4e5969;
  font-size: 13px;
  white-space: nowrap;
}

@media (max-width: 768px) {
  .page-design-publish-card header,
  .page-design-publish-meta {
    grid-template-columns: 1fr;
  }

  .page-design-publish-card header {
    display: grid;
  }

  .form-fill-link-row {
    grid-template-columns: 1fr;
  }
}
</style>
