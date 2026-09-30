<script setup>
import {
  NAlert,
  NButton,
  NDescriptions,
  NDescriptionsItem,
  NEmpty,
  NModal,
  NSpin,
  NTag,
} from 'naive-ui'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import MasterDetailWorkspace from '@/components/common/MasterDetailWorkspace.vue'
import { hasPrintPermission } from '@/components/print/management/printPermissions'
import PrintTemplateList from '@/components/print/management/PrintTemplateList.vue'
import { useUserStore } from '@/store'
import { usePrintCenterStore } from '@/stores/print/printCenterStore'
import PrintSourceForm from './PrintSourceForm.vue'
import PrintSourceList from './PrintSourceList.vue'

defineProps({
  section: {
    type: String,
    default: 'templates',
    validator: value => ['sources', 'templates', 'bindings'].includes(value),
  },
})
const route = useRoute()
const router = useRouter()
const store = usePrintCenterStore()
const user = useUserStore()
const editing = ref(false)
const creating = ref(false)
const deleting = ref(false)
const source = computed(() => store.selected)
const sourceIdentity = computed(() => store.sourceIdentity)
const typeLabel = computed(() => source.value?.sourceType === 'DATASET' ? '数据集' : '业务服务')
const statusLabel = computed(() => Number(source.value?.status) === 1 ? '已启用' : '已停用')
const canManageSource = computed(() => hasPrintPermission(user, 'print:source:manage'))

onMounted(() => store.load(route.query.businessSourceId))
onBeforeUnmount(() => store.clear())

watch(() => store.selectedId, (id) => {
  if (String(route.query.businessSourceId || '') === String(id || ''))
    return
  router.replace({
    query: { ...route.query, businessSourceId: id || undefined },
  })
})

watch(() => route.query.businessSourceId, (id) => {
  if (id && String(id) !== String(store.selectedId || ''))
    store.select(id)
})

async function removeSource() {
  try {
    await store.remove()
    deleting.value = false
  }
  catch (error) {
    store.error = error.message || '删除打印来源失败'
  }
  return false
}

async function toggleSource() {
  try {
    await store.toggle()
  }
  catch (error) {
    store.error = error.message || '更新来源状态失败'
  }
}
</script>

<template>
  <div class="print-center-page">
    <MasterDetailWorkspace :aside-width="250">
      <template #aside>
        <PrintSourceList :can-manage="canManageSource && section === 'sources'" @create="creating = true" />
      </template>

      <div class="print-center-main">
        <NAlert v-if="store.error" type="error" closable @close="store.error = ''">
          {{ store.error }}
        </NAlert>
        <NSpin :show="store.detailLoading" class="print-center-main__spin">
          <template v-if="source">
            <!-- 当前来源 -->
            <header class="print-center-head">
              <div class="print-center-head__identity">
                <span class="print-center-head__icon" aria-hidden="true">
                  <i :class="source.sourceType === 'DATASET' ? 'i-lucide:database' : 'i-lucide:braces'" />
                </span>
                <div>
                  <div class="print-center-head__title">
                    <strong>{{ source.sourceName }}</strong>
                    <NTag size="small" :type="Number(source.status) === 1 ? 'success' : 'default'">
                      {{ statusLabel }}
                    </NTag>
                  </div>
                  <span>{{ source.sourceCode }} · {{ typeLabel }}</span>
                </div>
              </div>
              <div v-if="canManageSource && section === 'sources'" class="print-center-head__actions">
                <NButton size="small" @click="editing = true">
                  来源设置
                </NButton>
                <NButton size="small" :loading="store.saving" @click="toggleSource">
                  {{ Number(source.status) === 1 ? '停用' : '启用' }}
                </NButton>
                <NButton size="small" quaternary type="error" @click="deleting = true">
                  删除
                </NButton>
              </div>
            </header>

            <!-- 独立菜单分别承载来源、模板和场景绑定，避免在一个页面继续套业务页签。 -->
            <div v-if="section === 'sources'" class="source-detail-pane">
              <NDescriptions label-placement="left" :column="1" bordered size="small">
                <NDescriptionsItem label="来源编码">
                  {{ source.sourceCode }}
                </NDescriptionsItem>
                <NDescriptionsItem label="对象编码">
                  {{ source.objectCode }}
                </NDescriptionsItem>
                <NDescriptionsItem label="来源类型">
                  {{ typeLabel }}
                </NDescriptionsItem>
                <NDescriptionsItem :label="source.sourceType === 'DATASET' ? '数据集 ID' : 'Provider 编码'">
                  {{ source.sourceType === 'DATASET' ? source.datasetId : source.providerCode }}
                </NDescriptionsItem>
                <NDescriptionsItem label="参数协议">
                  <pre>{{ source.parameterSchemaJson || '{}' }}</pre>
                </NDescriptionsItem>
                <NDescriptionsItem label="数据映射">
                  <pre>{{ source.mappingJson || '{}' }}</pre>
                </NDescriptionsItem>
              </NDescriptions>
            </div>
            <div v-else class="print-center-pane">
              <PrintTemplateList
                :key="`${source.id}:${section}`"
                :business-source-id="String(source.id)"
                :source="sourceIdentity"
                :sources="store.sources"
                :show-bindings="section === 'bindings'"
                :allow-create="section === 'templates'"
                lock-source
              />
            </div>
          </template>
          <NEmpty
            v-else
            :description="section === 'sources' ? '从左侧选择业务来源，或新增一个来源' : '请先到业务数据源菜单新增来源'"
          />
        </NSpin>
      </div>
    </MasterDetailWorkspace>

    <PrintSourceForm v-model:show="creating" />
    <PrintSourceForm v-model:show="editing" :source="source" />
    <NModal
      v-model:show="deleting"
      preset="dialog"
      title="删除打印来源"
      positive-text="删除"
      negative-text="取消"
      :loading="store.saving"
      @positive-click="removeSource"
    >
      仅未关联模板的来源可以删除。已经投入使用时，建议先停用。
    </NModal>
  </div>
</template>

<style scoped>
.print-center-page {
  height: 100%;
  min-height: 0;
}

.print-center-main,
.print-center-main__spin {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.print-center-main__spin :deep(.n-spin-content) {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.print-center-main > .n-alert {
  margin: 10px 12px 0;
}

.print-center-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--border-light, #e5e7eb);
}

.print-center-head__identity,
.print-center-head__title,
.print-center-head__actions {
  display: flex;
  align-items: center;
}

.print-center-head__identity {
  min-width: 0;
  gap: 10px;
}

.print-center-head__identity > div {
  min-width: 0;
}

.print-center-head__icon {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  flex: 0 0 auto;
  border: 1px solid var(--border-light, #e5e7eb);
  border-radius: 3px;
  color: var(--primary-color, #1677ff);
  background: var(--gray-100, #f6f8fb);
  font-size: 16px;
}

.print-center-head__title {
  gap: 8px;
}

.print-center-head__title strong {
  overflow: hidden;
  color: var(--text-primary, #1d2129);
  font-size: 15px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.print-center-head__identity > div > span {
  display: block;
  margin-top: 2px;
  color: var(--text-tertiary, #86909c);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 11px;
}

.print-center-head__actions {
  flex: 0 0 auto;
  gap: 6px;
}

.print-center-pane,
.source-detail-pane {
  height: 100%;
  min-height: 0;
  overflow: auto;
  scrollbar-gutter: stable;
}

.print-center-pane {
  padding: 12px 16px 16px;
}

.source-detail-pane {
  max-width: 820px;
  padding: 16px;
}

.source-detail-pane pre {
  max-height: 180px;
  margin: 0;
  overflow: auto;
  color: var(--text-secondary, #4e5969);
  font:
    12px/1.6 ui-monospace,
    SFMono-Regular,
    Menlo,
    monospace;
  white-space: pre-wrap;
  word-break: break-word;
}

@media (max-width: 720px) {
  .print-center-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .print-center-head__actions {
    width: 100%;
    flex-wrap: wrap;
  }
}
</style>
