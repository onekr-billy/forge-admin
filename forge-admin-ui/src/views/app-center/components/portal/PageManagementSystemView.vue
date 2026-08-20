<template>
  <section class="page-management-system-view">
    <div class="page-management-system-body">
      <WorkspaceSummary v-if="view === 'workbench'" />
      <WorkspaceTodo v-else-if="view === 'todo'" />
      <WorkspaceDone v-else-if="view === 'done'" />
      <WorkspaceStarted v-else-if="view === 'sent'" />
      <WorkspaceCc v-else-if="view === 'cc'" />
    </div>
  </section>
</template>

<script setup>
import WorkspaceCc from '@/views/workspace/cc.vue'
import WorkspaceDone from '@/views/workspace/done.vue'
import WorkspaceStarted from '@/views/workspace/started.vue'
import WorkspaceSummary from '@/views/workspace/summary.vue'
import WorkspaceTodo from '@/views/workspace/todo.vue'

defineProps({
  view: {
    type: String,
    default: 'workbench',
  },
  title: {
    type: String,
    default: '',
  },
})
</script>

<style scoped>
.page-management-system-view {
  display: flex;
  min-height: 100%;
  flex-direction: column;
  gap: 16px;
}

.page-management-system-body {
  min-height: 0;
  flex: 1;
  overflow: auto;
}
</style>
