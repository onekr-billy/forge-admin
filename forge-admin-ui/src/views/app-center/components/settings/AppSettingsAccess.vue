<template>
  <section class="settings-section-card">
    <header>
      <h2>访问地址</h2>
      <p>门户地址在当前租户内唯一，修改后需重新发布才会影响正式门户。</p>
    </header>
    <n-form label-placement="top">
      <n-form-item label="门户 slug" :validation-status="slugStatus" :feedback="slugFeedback">
        <n-input-group>
          <n-input-group-label>/app/</n-input-group-label>
          <n-input
            :value="modelValue.portalSlug"
            maxlength="50"
            placeholder="例如 crm-portal"
            @update:value="handleSlugInput"
            @blur="checkSlug"
          />
        </n-input-group>
      </n-form-item>
      <div class="access-link-grid">
        <div class="access-link-card">
          <n-qr-code :value="pcUrl" :size="108" :color="modelValue.themeColor || '#3370ff'" />
          <div>
            <span>电脑端</span>
            <strong>{{ pcUrl }}</strong>
            <p>在电脑浏览器打开完整应用门户。</p>
            <n-space>
              <n-button secondary @click="copyLink(pcUrl, '电脑端')">
                复制链接
              </n-button>
              <n-button type="primary" :disabled="!modelValue.portalSlug" @click="openLink(pcUrl)">
                打开电脑端
              </n-button>
            </n-space>
          </div>
        </div>
        <div class="access-link-card">
          <n-qr-code :value="h5Url" :size="108" :color="modelValue.themeColor || '#3370ff'" />
          <div>
            <span>移动端</span>
            <strong>{{ h5Url }}</strong>
            <p>扫码打开独立 H5 运行页，和旧访问入口同一套地址。</p>
            <n-space>
              <n-button secondary :disabled="!h5Url" @click="copyLink(h5Url, '移动端')">
                复制移动端链接
              </n-button>
              <n-button type="primary" :disabled="!h5Url" @click="openLink(h5Url)">
                打开移动端
              </n-button>
            </n-space>
          </div>
        </div>
      </div>
    </n-form>
  </section>
</template>

<script setup>
import { useMessage } from 'naive-ui'
import { computed, ref } from 'vue'
import { checkBusinessApplicationSlugAvailable } from '@/api/business-application'
import { buildPortalAccessUrls, RESERVED_PORTAL_SLUGS } from '../portal/portal-config'

const props = defineProps({
  modelValue: { type: Object, required: true },
  application: { type: Object, default: null },
  configKey: { type: String, default: '' },
  objects: { type: Array, default: () => [] },
})
const emit = defineEmits(['update:modelValue'])
const message = useMessage()
const slugStatus = ref(undefined)
const slugFeedback = ref('只允许 2-50 位字母、数字、中划线或下划线。')
let validationSequence = 0

const accessUrls = computed(() => buildPortalAccessUrls({
  origin: window.location.origin,
  basePath: import.meta.env.BASE_URL,
  slug: props.modelValue.portalSlug || props.modelValue.applicationCode,
  configKey: props.configKey,
  h5BaseUrl: props.modelValue.distribution?.h5BaseUrl,
  appId: props.modelValue.id,
  application: props.application || props.modelValue,
  objects: props.objects,
}))
const pcUrl = computed(() => accessUrls.value.pcUrl)
const h5Url = computed(() => accessUrls.value.h5Url)

function handleSlugInput(value) {
  slugStatus.value = undefined
  slugFeedback.value = '只允许 2-50 位字母、数字、中划线或下划线。'
  emit('update:modelValue', { ...props.modelValue, portalSlug: String(value || '').trim() })
}

async function checkSlug() {
  const slug = String(props.modelValue.portalSlug || '').trim()
  if (!/^[\w-]{2,50}$/.test(slug)) {
    slugStatus.value = 'error'
    slugFeedback.value = '门户地址格式不正确。'
    return false
  }
  if (RESERVED_PORTAL_SLUGS.includes(slug.toLowerCase())) {
    slugStatus.value = 'error'
    slugFeedback.value = '该地址是系统保留路径，请更换其它值。'
    return false
  }
  const sequence = ++validationSequence
  try {
    const response = await checkBusinessApplicationSlugAvailable(slug, props.modelValue.id)
    if (sequence !== validationSequence)
      return false
    const available = response.data === true
    slugStatus.value = available ? 'success' : 'error'
    slugFeedback.value = available ? '该访问地址可用。' : '该访问地址已被占用。'
    return available
  }
  catch (error) {
    if (sequence !== validationSequence)
      return false
    slugStatus.value = 'error'
    slugFeedback.value = error?.message || '地址校验失败。'
    return false
  }
}

async function copyLink(value, label) {
  await navigator.clipboard.writeText(value)
  message.success(`${label}访问链接已复制`)
}

function openLink(value) {
  window.open(value, '_blank', 'noopener,noreferrer')
}

defineExpose({ checkSlug })
</script>

<style scoped>
.access-link-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.access-link-card {
  display: flex;
  align-items: center;
  gap: 16px;
  border: 1px solid var(--border-color, #e5e6eb);
  border-radius: 8px;
  background: var(--card-color, #f7f8fa);
  padding: 16px;
}

.access-link-card > div {
  display: grid;
  min-width: 0;
  gap: 6px;
}

.access-link-card span,
.access-link-card p {
  margin: 0;
  color: var(--text-color-3, #86909c);
  font-size: 12px;
}

.access-link-card strong {
  overflow: hidden;
  color: var(--text-color-1, #1f2329);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 1080px) {
  .access-link-grid {
    grid-template-columns: 1fr;
  }
}
</style>
