<template>
  <section class="portal-empty-state" :class="`is-${type}`" role="status">
    <div class="portal-empty-icon" aria-hidden="true">
      <n-icon><component :is="stateIcon" /></n-icon>
    </div>
    <h1>{{ resolvedTitle }}</h1>
    <p>{{ resolvedDescription }}</p>
    <n-space v-if="$slots.actions || showBack" justify="center">
      <slot name="actions">
        <n-button v-if="showBack" type="primary" @click="$router.push('/app-center')">
          返回应用中心
        </n-button>
      </slot>
    </n-space>
  </section>
</template>

<script setup>
import { AlertCircleOutline, FileTrayOutline, LockClosedOutline } from '@vicons/ionicons5'
import { computed } from 'vue'

const props = defineProps({
  type: { type: String, default: 'empty' },
  title: { type: String, default: '' },
  description: { type: String, default: '' },
  showBack: { type: Boolean, default: true },
})

const copyByType = {
  unavailable: ['应用暂不可用', '应用可能尚未发布或已被停用，请联系应用管理员。'],
  forbidden: ['暂无访问权限', '当前账号没有可访问的应用页面，请联系应用管理员授权。'],
  error: ['应用加载失败', '暂时无法读取应用配置，请稍后重试。'],
  empty: ['暂无可用页面', '应用已发布，但当前版本尚未配置可访问页面。'],
}

const resolvedTitle = computed(() => props.title || copyByType[props.type]?.[0] || copyByType.empty[0])
const resolvedDescription = computed(() => props.description || copyByType[props.type]?.[1] || copyByType.empty[1])
const stateIcon = computed(() => ({
  unavailable: AlertCircleOutline,
  forbidden: LockClosedOutline,
  error: AlertCircleOutline,
  empty: FileTrayOutline,
})[props.type] || FileTrayOutline)
</script>

<style scoped>
.portal-empty-state {
  width: min(520px, calc(100% - 32px));
  margin: 0 auto;
  padding: 96px 24px;
  text-align: center;
}

.portal-empty-icon {
  display: grid;
  width: 56px;
  height: 56px;
  margin: 0 auto 18px;
  place-items: center;
  border-radius: 14px;
  background: color-mix(in srgb, var(--portal-primary, #3370ff) 10%, transparent);
  color: var(--portal-primary, #3370ff);
  font-size: 28px;
}

.portal-empty-state.is-forbidden .portal-empty-icon,
.portal-empty-state.is-unavailable .portal-empty-icon {
  background: color-mix(in srgb, #f59e0b 12%, transparent);
  color: #b7791f;
}

.portal-empty-state h1 {
  margin: 0;
  color: var(--portal-text, #1f2329);
  font-size: 22px;
  font-weight: 650;
}

.portal-empty-state p {
  margin: 10px 0 24px;
  color: var(--portal-text-muted, #646a73);
  font-size: 14px;
  line-height: 1.7;
}
</style>
