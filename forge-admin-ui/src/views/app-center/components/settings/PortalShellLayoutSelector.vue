<template>
  <div class="portal-shell-layout-selector">
    <div
      v-for="group in groups"
      :key="group.key"
      class="layout-group"
    >
      <div class="layout-group-header">
        <span class="layout-group-title">{{ group.title }}</span>
        <span class="layout-group-desc">{{ group.description }}</span>
      </div>
      <div class="layout-group-grid">
        <button
          v-for="layout in group.options"
          :key="layout.value"
          type="button"
          class="layout-option"
          :class="{ active: modelValue === layout.value }"
          @click="$emit('update:modelValue', layout.value)"
        >
          <span class="layout-preview-card">
            <img :src="layout.preview" :alt="layout.label" class="layout-img">
          </span>
          <span class="layout-option-main">
            <span class="layout-option-title">
              {{ layout.label }}
              <span v-if="layout.recommended" class="layout-option-badge">推荐</span>
            </span>
            <span class="layout-option-desc">{{ layout.description }}</span>
          </span>
          <span
            v-if="modelValue === layout.value"
            class="layout-option-check"
            aria-hidden="true"
          >✓</span>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { buildPortalShellLayoutGroups } from '../portal/portal-shell-layouts'

defineProps({
  modelValue: {
    type: String,
    default: 'normal',
  },
})

defineEmits(['update:modelValue'])

const groups = computed(() => buildPortalShellLayoutGroups())
</script>

<style scoped>
.portal-shell-layout-selector {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.layout-group {
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  background: #f8fafc;
}

.layout-group-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.layout-group-title {
  color: #0f172a;
  font-size: 13px;
  font-weight: 700;
}

.layout-group-desc {
  min-width: 0;
  color: #64748b;
  font-size: 12px;
  text-align: right;
}

.layout-group-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.layout-option {
  position: relative;
  min-height: 82px;
  display: grid;
  grid-template-columns: 78px minmax(0, 1fr);
  align-items: center;
  gap: 10px;
  padding: 10px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  color: #334155;
  background: linear-gradient(180deg, #ffffff, #f8fafc);
  cursor: pointer;
  text-align: left;
  transition:
    border-color 0.18s ease,
    background-color 0.18s ease,
    box-shadow 0.18s ease,
    transform 0.18s ease;
}

.layout-option:hover {
  border-color: #93c5fd;
  background: #fff;
  box-shadow: 0 8px 18px rgb(37 99 235 / 8%);
  transform: translateY(-1px);
}

.layout-option.active {
  border-color: #2563eb;
  background: linear-gradient(180deg, #eff6ff, #ffffff);
  box-shadow: inset 0 0 0 1px rgb(37 99 235 / 12%);
}

.layout-preview-card {
  height: 54px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
}

.layout-img {
  width: 64px;
  height: 40px;
  object-fit: contain;
}

.layout-option-main {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.layout-option-title {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #0f172a;
  font-size: 13px;
  font-weight: 650;
}

.layout-option-badge {
  padding: 1px 6px;
  border-radius: 999px;
  background: #dbeafe;
  color: #1d4ed8;
  font-size: 11px;
  font-weight: 600;
}

.layout-option-desc {
  color: #64748b;
  font-size: 12px;
  line-height: 1.4;
}

.layout-option-check {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 18px;
  height: 18px;
  display: grid;
  place-items: center;
  border-radius: 999px;
  background: #2563eb;
  color: #fff;
  font-size: 11px;
  font-weight: 700;
}

@media (max-width: 720px) {
  .layout-group-grid {
    grid-template-columns: 1fr;
  }

  .layout-group-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .layout-group-desc {
    text-align: left;
  }
}
</style>
