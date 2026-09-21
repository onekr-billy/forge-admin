<template>
  <div v-if="store.selected" class="integration-toolbar selection">
    <div>
      <span class="integration-help">当前能力</span><div class="integration-title">
        {{ store.selected.capabilityName }} <small>v{{ store.selected.currentVersion }}</small>
      </div><div class="integration-code">
        {{ store.selected.capabilityCode }}
      </div>
    </div>
    <n-button size="small" :disabled="store.writing" @click="store.tab = 'capabilities'">
      切换能力
    </n-button>
  </div>
  <n-empty v-else description="先选择本应用的一项能力" class="integration-empty">
    <template #extra>
      <n-button @click="store.tab = 'capabilities'">
        前往对外能力
      </n-button>
    </template>
  </n-empty>
</template>

<script setup>
import { useApplicationIntegrationStore } from '@/stores/application/integrationStore'

const store = useApplicationIntegrationStore()
</script>

<style scoped>
.selection {
  padding: 16px;
  border: 1px solid var(--integration-border);
  border-radius: 8px;
  background: var(--integration-surface);
}
small {
  font-weight: 400;
  color: var(--integration-muted);
  margin-left: 8px;
}
</style>
