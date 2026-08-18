<template>
  <section class="publish-section-card">
    <header>
      <div><h2>组织内访问</h2><p>只有已启用且拥有页面权限的组织成员可以访问。</p></div>
      <n-tag :type="application.lastPublishVersion && application.status === 1 ? 'success' : 'warning'" :bordered="false">
        {{ application.lastPublishVersion && application.status === 1 ? '可访问' : '暂不可用' }}
      </n-tag>
    </header>
    <div class="publish-access-grid">
      <div class="publish-access-body">
        <n-qr-code :value="portalUrl" :size="120" :color="themeColor" />
        <div class="publish-access-copy">
          <span>桌面与通用门户</span>
          <strong>{{ portalUrl }}</strong>
          <p>页面级权限由发布快照过滤，数据范围继续由 Forge DataScope 控制。</p>
          <n-space>
            <n-button secondary @click="copyLink(portalUrl, '门户')">
              复制链接
            </n-button>
            <n-button type="primary" :disabled="!portalAvailable" @click="openLink(portalUrl)">
              打开门户
            </n-button>
          </n-space>
        </div>
      </div>
      <div v-if="h5Enabled" class="publish-access-body is-h5">
        <n-qr-code :value="h5Url" :size="120" :color="themeColor" />
        <div class="publish-access-copy">
          <span>响应式 H5 入口</span>
          <strong>{{ h5Url }}</strong>
          <p>扫码在移动浏览器打开；导航会折叠为横向页签，复杂页面按移动端流式排列。</p>
          <n-space>
            <n-button secondary @click="copyLink(h5Url, 'H5')">
              复制 H5 链接
            </n-button>
            <n-button type="primary" :disabled="!portalAvailable" @click="openLink(h5Url)">
              打开 H5
            </n-button>
          </n-space>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup>
import { useMessage } from 'naive-ui'
import { computed } from 'vue'
import { normalizePortalConfig } from '../portal/portal-config'

const props = defineProps({ application: { type: Object, required: true } })
const message = useMessage()
const portalConfig = computed(() => normalizePortalConfig(props.application.portalConfig))
const themeColor = computed(() => portalConfig.value.themeColor || '#3370ff')
const portalUrl = computed(() => `${window.location.origin}${import.meta.env.BASE_URL.replace(/\/$/, '')}/app/${encodeURIComponent(props.application.portalSlug || props.application.applicationCode)}`)
const h5Url = computed(() => `${portalUrl.value}?display=h5`)
const h5Enabled = computed(() => portalConfig.value.distribution.h5Enabled !== false)
const portalAvailable = computed(() => Boolean(props.application.lastPublishVersion) && Number(props.application.status) === 1)

async function copyLink(value, label) {
  await navigator.clipboard.writeText(value)
  message.success(`${label}访问链接已复制`)
}

function openLink(value) {
  window.open(value, '_blank', 'noopener,noreferrer')
}
</script>

<style scoped>
.publish-access-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.publish-access-body {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 14px;
  border: 1px solid var(--n-border-color, #e5e7eb);
  border-radius: 8px;
  background: var(--n-color-embedded, #f7f8fa);
}

.publish-access-body.is-h5 {
  background: color-mix(in srgb, v-bind(themeColor) 4%, var(--n-color, #fff));
}

.publish-access-copy {
  display: grid;
  min-width: 0;
  gap: 7px;
}

.publish-access-copy span,
.publish-access-copy p {
  margin: 0;
  color: var(--text-color-3, #86909c);
  font-size: 12px;
}

.publish-access-copy strong {
  overflow: hidden;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 1080px) {
  .publish-access-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 620px) {
  .publish-access-body {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
