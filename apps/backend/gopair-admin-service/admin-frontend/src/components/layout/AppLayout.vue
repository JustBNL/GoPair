<script setup lang="ts">
import AppSidebar from './AppSidebar.vue'
import AppHeader  from './AppHeader.vue'
</script>

<template>
  <div class="app-layout">
    <AppSidebar class="app-layout__sidebar" />
    <div class="app-layout__main">
      <AppHeader class="app-layout__header" />
      <main class="app-layout__content">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<style scoped>
.app-layout {
  display: flex;
  min-height: 100vh;
  background-color: var(--color-bg);
}

.app-layout__sidebar {
  position: fixed;
  left: 0;
  top: 0;
  height: 100vh;
  width: var(--color-sidebar-width);
  z-index: 100;
}

.app-layout__main {
  margin-left: var(--color-sidebar-width);
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  transition: margin-left var(--transition-normal);
}

.app-layout__header {
  position: sticky;
  top: 0;
  z-index: 90;
  height: var(--color-header-height);
}

.app-layout__content {
  flex: 1;
  padding: var(--space-5);
  max-width: 1400px;
  width: 100%;
}
</style>
