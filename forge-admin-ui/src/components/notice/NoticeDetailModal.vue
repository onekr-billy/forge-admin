<template>
  <NModal
    v-model:show="noticeStore.showDetail"
    preset="card"
    :title="noticeStore.currentNotice?.noticeTitle || '公告详情'"
    style="width: min(820px, calc(100vw - 24px))"
    :segmented="{ content: 'soft' }"
  >
    <NSpin :show="noticeStore.detailLoading">
      <NAlert v-if="noticeStore.detailError" type="error" :show-icon="false">
        {{ noticeStore.detailError }}
      </NAlert>
      <article v-else-if="noticeStore.currentNotice" class="notice-detail">
        <div class="notice-meta">
          <DictTag dict-type="sys_notice_type" :value="noticeStore.currentNotice.noticeType" />
          <span>发布人：{{ noticeStore.currentNotice.publisherName || '-' }}</span>
          <time>{{ noticeStore.currentNotice.publishTime }}</time>
        </div>
        <NAlert v-if="noticeStore.readError" type="warning" class="read-warning" :show-icon="false">
          {{ noticeStore.readError }}
          <NButton text type="primary" @click="noticeStore.confirmRead">
            重试
          </NButton>
        </NAlert>
        <div class="notice-body" v-html="safeContent" />
        <section v-if="noticeStore.currentNotice.attachments?.length" class="notice-attachments">
          <h3>附件</h3>
          <NButton
            v-for="file in noticeStore.currentNotice.attachments"
            :key="file.fileId"
            text
            type="primary"
            class="attachment-link"
            @click="downloadAttachment(file)"
          >
            <i class="i-material-symbols:attach-file" />
            {{ file.fileName }}
          </NButton>
        </section>
      </article>
      <div v-else class="detail-placeholder">
        正在加载公告…
      </div>
    </NSpin>
  </NModal>
</template>

<script setup>
import { NAlert, NButton, NModal, NSpin } from 'naive-ui'
import { computed } from 'vue'
import DictTag from '@/components/DictTag.vue'
import { useNoticeStore } from '@/stores/system/noticeStore'
import { downloadFile } from '@/utils/file'
import { sanitizeHtml } from '@/utils/sanitize-html'

const noticeStore = useNoticeStore()
const safeContent = computed(() => sanitizeHtml(noticeStore.currentNotice?.noticeContent))

async function downloadAttachment(file) {
  try {
    await downloadFile(file.fileId, file.fileName)
  }
  catch (error) {
    console.error('下载公告附件失败:', error)
    window.$message?.error('下载附件失败，请重试')
  }
}
</script>

<style scoped>
.notice-detail {
  max-height: 72vh;
  overflow: auto;
  color: var(--text-primary);
}
.notice-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 16px;
  color: var(--text-tertiary);
  font-size: 12px;
}
.notice-body {
  margin-top: 20px;
  line-height: 1.8;
  overflow-wrap: anywhere;
  overflow-x: auto;
}
.notice-body :deep(img) {
  max-width: 100%;
  height: auto;
}
.notice-attachments {
  border-top: 1px solid var(--border-light);
  margin-top: 20px;
  padding-top: 12px;
}
.notice-attachments h3 {
  font-size: 14px;
  margin: 0 0 8px;
}
.attachment-link {
  display: flex;
  margin: 8px 0;
  white-space: normal;
  text-align: left;
  overflow-wrap: anywhere;
}
.read-warning {
  margin-top: 12px;
}
.detail-placeholder {
  padding: 48px 0;
  text-align: center;
  color: var(--text-tertiary);
}
</style>
