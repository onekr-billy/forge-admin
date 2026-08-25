<template>
  <section class="page-management-system-view">
    <div class="page-management-system-body">
      <WorkspaceSummary v-if="view === 'workbench'" :route-targets="navigationRoutes" />
      <WorkspaceTodo v-else-if="view === 'todo'" />
      <WorkspaceDone v-else-if="view === 'done'" />
      <WorkspaceStarted v-else-if="view === 'sent'" />
      <WorkspaceCc v-else-if="view === 'cc'" />
      <MessageList v-else-if="view === 'messages'" :todo-route="navigationRoutes.todo" />
    </div>
  </section>
</template>

<script setup>
import MessageList from '@/views/message/message-list.vue'
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
  navigationRoutes: {
    type: Object,
    default: () => ({}),
  },
})
</script>

<style scoped>
.page-management-system-view {
  display: flex;
  height: 100%;
  min-height: 100%;
  flex-direction: column;
  gap: 16px;
}

.page-management-system-body {
  height: 100%;
  min-height: 0;
  flex: 1;
  overflow: auto;
}
</style>
