<script setup>
import { NAlert, NButton, NCard, NEmpty, NPagination, NPopconfirm, NSpace, NSpin, NTable } from 'naive-ui'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as api from '@/api/print'
import DictTag from '@/components/DictTag.vue'
import { printSourceFromQuery } from '@/components/print/management/printRouteContext'
import PrintTemplateCreate from '@/components/print/management/PrintTemplateCreate.vue'
import { useUserStore } from '@/store'
import { usePrintTemplateStore } from '@/stores/print/printTemplateStore'

const route = useRoute()
const router = useRouter()
const store = usePrintTemplateStore()
const user = useUserStore()
const source = computed(() => printSourceFromQuery(route.query))
const appId = computed(() => /^[1-9]\d*$/.test(String(route.query.applicationId || '')) ? String(route.query.applicationId) : null)
const canManage = computed(() => (user.getDataPermission || []).some(p => p === '**' || p === 'print:template:manage'))
const creating = ref(false)
const busy = ref(false)
watch(appId, (value) => {
  store.items = []
  if (value)
    store.list(value)
}, { immediate: true })
onBeforeUnmount(() => {
  store.listGeneration++
  store.items = []
})
function design(row) {
  router.push({ path: '/print/designer', query: { templateId: String(row.id) } })
}
async function act(action) {
  if (busy.value)
    return
  busy.value = true
  store.error = ''
  try {
    await action()
    await store.list(appId.value, store.pageNum)
  }
  catch (error) {
    store.error = error.message || '操作失败'
  }
  finally {
    busy.value = false
  }
}
async function copy(row) {
  await act(async () => {
    const { data } = await api.copyPrintTemplate(row.id, { expectedRevision: row.draftRevision, templateCode: `print_${crypto.randomUUID().replaceAll('-', '')}`, templateName: `${row.templateName.slice(0, 95)} 副本` })
    design(data)
  })
}
</script>

<template>
  <NCard title="打印模板" :bordered="false">
    <template #header-extra>
      <NSpace>
        <NButton :disabled="!appId || busy" @click="store.list(appId, store.pageNum)">
          刷新
        </NButton><NButton v-if="canManage" type="primary" :disabled="!source || busy" @click="creating = true">
          新建模板
        </NButton>
      </NSpace>
    </template>
    <NAlert v-if="store.error" type="error">
      {{ store.error }}
    </NAlert>
    <NEmpty v-if="!appId" description="请从所属应用的表单进入打印模板管理" />
    <template v-else>
      <p v-if="!source">
        新建模板需要表单来源，请从应用中的具体表单进入。
      </p>
      <NSpin :show="store.listing">
        <NEmpty v-if="!store.items.length && !store.listing" description="暂无打印模板" />
        <NTable v-else size="small">
          <thead><tr><th>模板名称</th><th>来源</th><th>设计状态</th><th>启用状态</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="row in store.items" :key="row.id">
              <td>{{ row.templateName }}</td><td><DictTag dict-type="sys_print_source_type" :value="row.source.sourceType" /> · {{ row.source.objectCode }}</td>
              <td><DictTag dict-type="sys_print_design_status" :value="row.designStatus" /></td><td><DictTag dict-type="sys_normal_disable" :value="row.status" /></td>
              <td>
                <NSpace>
                  <NButton text type="primary" @click="design(row)">
                    {{ canManage ? '设计' : '查看' }}
                  </NButton>
                  <template v-if="canManage">
                    <NButton text type="primary" :disabled="busy" @click="copy(row)">
                      复制
                    </NButton>
                    <NButton text :type="Number(row.status) === 1 ? 'warning' : 'success'" :disabled="busy" @click="act(() => api.changePrintTemplateStatus(row.id, { expectedRevision: row.draftRevision, status: Number(row.status) === 1 ? 0 : 1 }))">
                      {{ Number(row.status) === 1 ? '停用' : '启用' }}
                    </NButton>
                    <NPopconfirm @positive-click="act(() => api.deletePrintTemplate(row.id, row.draftRevision))">
                      <template #trigger>
                        <NButton text type="error" :disabled="busy">
                          删除
                        </NButton>
                      </template>删除此打印模板？存在绑定或发布引用时无法删除。
                    </NPopconfirm>
                  </template>
                </NSpace>
              </td>
            </tr>
          </tbody>
        </NTable>
      </NSpin>
      <NPagination v-if="store.total > 20" :page="store.pageNum" :page-size="20" :item-count="store.total" :disabled="store.listing" style="margin-top: 16px" @update:page="page => store.list(appId, page)" />
    </template>
    <PrintTemplateCreate v-model:show="creating" :source="source" @created="design" />
  </NCard>
</template>
