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

    <AppPublishAccess :application="application" :page-id="pageId" :config-key="configKey" :objects="objects" />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import AppPublishAccess from '../publish/AppPublishAccess.vue'

const props = defineProps({
  application: { type: Object, required: true },
  pageId: { type: String, default: '' },
  pageTitle: { type: String, default: '' },
  dirty: { type: Boolean, default: false },
  saving: { type: Boolean, default: false },
  configKey: { type: String, default: '' },
  objects: { type: Array, default: () => [] },
})

const emit = defineEmits(['save', 'preview'])

const published = computed(() => Boolean(props.application?.lastPublishVersion) && Number(props.application?.status) === 1)
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

@media (max-width: 768px) {
  .page-design-publish-card header,
  .page-design-publish-meta {
    grid-template-columns: 1fr;
  }

  .page-design-publish-card header {
    display: grid;
  }
}
</style>
