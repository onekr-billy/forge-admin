<template>
  <div class="workspace-summary-page">
    <section class="summary-head">
      <div>
        <h2>工作台首页</h2>
        <p>先看聚合，再进入具体列表处理。</p>
      </div>
      <NButton secondary :loading="loading" @click="loadSummary">
        <template #icon>
          <i class="i-material-symbols:refresh" />
        </template>
        刷新
      </NButton>
    </section>

    <NAlert v-if="loadError" class="summary-alert" type="warning" :show-icon="false">
      {{ loadError }}
    </NAlert>

    <NSpin :show="loading">
      <section class="summary-grid">
        <button
          v-for="item in metricItems"
          :key="item.key"
          class="summary-metric"
          type="button"
          @click="router.push(item.target)"
        >
          <span class="metric-icon" :class="item.tone">
            <i :class="item.icon" />
          </span>
          <span class="metric-copy">
            <strong>{{ item.value }}</strong>
            <span>{{ item.label }}</span>
            <small>{{ item.desc }}</small>
          </span>
          <i class="i-material-symbols:chevron-right metric-arrow" />
        </button>
      </section>
    </NSpin>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getWorkspaceSummary } from '@/api/workspace'

const props = defineProps({
  routeTargets: {
    type: Object,
    default: () => ({}),
  },
})
const router = useRouter()
const loading = ref(false)
const loadError = ref('')
const summary = ref({
  todoCount: 0,
  doneWeekCount: 0,
  startedRunningCount: 0,
  ccUnreadCount: 0,
})

const metricItems = computed(() => [
  {
    key: 'todo',
    label: '我的待办',
    desc: '需要我处理或签收',
    value: summary.value.todoCount,
    target: props.routeTargets.todo || '/workspace/todo',
    icon: 'i-material-symbols:pending-actions',
    tone: 'blue',
  },
  {
    key: 'done',
    label: '本周已办',
    desc: '本周完成的审批处理',
    value: summary.value.doneWeekCount,
    target: props.routeTargets.done || '/workspace/done',
    icon: 'i-material-symbols:task-alt-outline',
    tone: 'green',
  },
  {
    key: 'started',
    label: '发起中',
    desc: '我发起且仍在流转',
    value: summary.value.startedRunningCount,
    target: props.routeTargets.sent || '/workspace/started',
    icon: 'i-material-symbols:send-outline',
    tone: 'amber',
  },
  {
    key: 'cc',
    label: '未读抄送',
    desc: '抄送给我的未读消息',
    value: summary.value.ccUnreadCount,
    target: props.routeTargets.cc || '/workspace/cc',
    icon: 'i-material-symbols:alternate-email',
    tone: 'red',
  },
])

onMounted(loadSummary)

async function loadSummary() {
  loading.value = true
  loadError.value = ''
  try {
    const res = await getWorkspaceSummary()
    const data = res.data || {}
    summary.value = {
      todoCount: Number(data.todoCount || 0),
      doneWeekCount: Number(data.doneWeekCount || 0),
      startedRunningCount: Number(data.startedRunningCount || 0),
      ccUnreadCount: Number(data.ccUnreadCount || 0),
    }
  }
  catch (error) {
    loadError.value = error?.message || '工作台统计加载失败'
    summary.value = {
      todoCount: 0,
      doneWeekCount: 0,
      startedRunningCount: 0,
      ccUnreadCount: 0,
    }
  }
  finally {
    loading.value = false
  }
}
</script>

<style scoped>
.workspace-summary-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.summary-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 18px;
  border: 1px solid #e5eaf3;
  border-radius: 8px;
  background: #fff;
}

.summary-head h2 {
  margin: 0;
  color: #172033;
  font-size: 18px;
  font-weight: 700;
  line-height: 26px;
}

.summary-head p {
  margin: 4px 0 0;
  color: #667085;
  font-size: 13px;
}

.summary-alert {
  border-radius: 8px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.summary-metric {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr) 20px;
  gap: 12px;
  align-items: center;
  min-height: 118px;
  padding: 16px;
  border: 1px solid #e5eaf3;
  border-radius: 8px;
  background: #fff;
  color: #344256;
  text-align: left;
  cursor: pointer;
  transition:
    border-color 0.16s ease,
    box-shadow 0.16s ease,
    transform 0.16s ease;
}

.summary-metric:hover {
  border-color: #c9cdd4;
  box-shadow: 0 8px 22px rgba(31, 35, 41, 0.06);
  transform: translateY(-1px);
}

.metric-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  border-radius: 8px;
  font-size: 22px;
}

.metric-icon.blue {
  background: #f0f2f5;
  color: #4e5969;
}

.metric-icon.green {
  background: #e9f8ef;
  color: #15803d;
}

.metric-icon.amber {
  background: #fff6df;
  color: #b45309;
}

.metric-icon.red {
  background: #fff0f0;
  color: #dc2626;
}

.metric-copy {
  min-width: 0;
}

.metric-copy strong {
  display: block;
  color: #172033;
  font-size: 30px;
  font-weight: 750;
  line-height: 36px;
}

.metric-copy span,
.metric-copy small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.metric-copy span {
  margin-top: 4px;
  color: #344256;
  font-size: 14px;
  font-weight: 650;
}

.metric-copy small {
  margin-top: 2px;
  color: #667085;
  font-size: 12px;
}

.metric-arrow {
  color: #98a2b3;
  font-size: 20px;
}

@media (max-width: 1180px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .summary-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>
