<script setup lang="ts">
import { useTheme } from './composables/useTheme'

useTheme()
</script>

<template>
  <div class="app-shell">
    <RouterView />
  </div>
</template>
