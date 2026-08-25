<template>
  <div class="app-portal-layout">
    <slot />
  </div>
</template>

<style scoped>
.app-portal-layout {
  width: 100%;
  min-width: 0;
  min-height: 100vh;
  background: var(--portal-surface-muted, #f5f7fa);
  color: var(--portal-text, #1f2329);
}

@media (prefers-reduced-motion: reduce) {
  .app-portal-layout *,
  .app-portal-layout *::before,
  .app-portal-layout *::after {
    scroll-behavior: auto !important;
    transition-duration: 0.01ms !important;
  }
}
</style>
