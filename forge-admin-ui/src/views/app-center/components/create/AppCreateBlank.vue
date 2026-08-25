<template>
  <section class="blank-start">
    <div class="blank-start__mark">
      <n-icon><AddCircleOutline /></n-icon>
    </div>
    <div>
      <strong>从空白应用开始</strong>
      <p>只创建应用身份和工作台，后续再添加业务对象、页面、流程与自动化。</p>
    </div>
  </section>
</template>

<script setup>
import { AddCircleOutline } from '@vicons/ionicons5'

function validate() {
  return true
}

function getPayload() {
  return {}
}

defineExpose({ getPayload, validate })
</script>

<style scoped>
.blank-start {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px;
  border: 1px solid var(--n-border-color);
  border-radius: 8px;
  background: var(--n-color-embedded);
}

.blank-start__mark {
  display: grid;
  width: 46px;
  height: 46px;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 8px;
  color: var(--n-primary-color);
  background: color-mix(in srgb, var(--n-primary-color) 10%, transparent);
  font-size: 24px;
}

.blank-start strong {
  color: var(--n-text-color);
  font-size: 15px;
}

.blank-start p {
  margin: 5px 0 0;
  color: var(--n-text-color-3);
  font-size: 13px;
}
</style>
